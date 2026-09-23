package com.chillzone.pvprank;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class ChillZonePvPRankAdmin implements ModInitializer {
    private static CombatRankBridge bridge;
    private static final ExclusionStore exclusions = new ExclusionStore();
    private static final RankStore rankStore = new RankStore();
    private static final NametagSettings nametagSettings = new NametagSettings();
    private static int housekeepingTicks;
    private static boolean startupRestoreFinished;

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            builder.suggest(p.getGameProfile().name());
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        exclusions.load();
        rankStore.load();
        nametagSettings.load();
        try {
            bridge = new CombatRankBridge();
            bridge.setCompactNametags(nametagSettings.isCompact());
            System.out.println("[ChillZonePvPRankAdmin] Connected to Combat-Ranked.");
        } catch (ReflectiveOperationException e) {
            System.err.println("[ChillZonePvPRankAdmin] Combat-Ranked API bridge failed: " + e);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("pvprank")
                .requires(Permissions::canAdmin)
                .then(Commands.literal("set")
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, 10))
                            .executes(ctx -> setRank(ctx.getSource(), StringArgumentType.getString(ctx, "player"), IntegerArgumentType.getInteger(ctx, "rank"))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                        .executes(ctx -> removeRank(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("swap")
                    .then(Commands.argument("player1", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                        .then(Commands.argument("player2", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                            .executes(ctx -> swapRanks(ctx.getSource(), StringArgumentType.getString(ctx, "player1"), StringArgumentType.getString(ctx, "player2"))))))
                .then(Commands.literal("reset")
                    .then(Commands.argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                        .executes(ctx -> resetRank(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("list").executes(ctx -> listRanks(ctx.getSource())))
                .then(Commands.literal("nametag")
                    .executes(ctx -> nametagStatus(ctx.getSource()))
                    .then(Commands.literal("status").executes(ctx -> nametagStatus(ctx.getSource())))
                    .then(Commands.literal("toggle").executes(ctx -> toggleNametagStyle(ctx.getSource())))
                    .then(Commands.literal("compact").executes(ctx -> setNametagStyle(ctx.getSource(), true)))
                    .then(Commands.literal("full").executes(ctx -> setNametagStyle(ctx.getSource(), false))))
                .then(Commands.literal("resetall")
                    .then(Commands.literal("confirm").executes(ctx -> resetAll(ctx.getSource()))))
            );
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (bridge == null) return;
            startupRestoreFinished = false;
            housekeepingTicks = 0;
            try {
                if (rankStore.hasSnapshot()) {
                    // The add-on's file is the restart authority. Restore the exact
                    // last-known Top 10 before ordinary joins can rebuild an empty list.
                    bridge.restoreRanks(rankStore.snapshot());
                    bridge.normalizeDisplayLabels();
                    rankStore.replace(bridge.snapshotRanks());
                    bridge.refreshOnlineNametags(server);
                    System.out.println("[ChillZonePvPRankAdmin] Restored saved PvP rankings after server start.");
                } else {
                    // First run of this version: adopt Combat-Ranked's current list as the baseline.
                    rankStore.replace(bridge.snapshotRanks());
                    System.out.println("[ChillZonePvPRankAdmin] Created initial PvP rank backup.");
                }
                startupRestoreFinished = true;
            } catch (ReflectiveOperationException e) {
                System.err.println("[ChillZonePvPRankAdmin] Could not restore saved PvP rankings: " + e.getMessage());
            }
        });

        // Make one final best-effort capture during a normal stop/restart. An
        // unexpected empty Combat-Ranked table is never allowed to erase a
        // non-empty last-known-good backup here.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (bridge == null || !startupRestoreFinished) return;
            try {
                syncAutomaticRankBackup();
            } catch (ReflectiveOperationException e) {
                System.err.println("[ChillZonePvPRankAdmin] Could not save PvP ranks during shutdown: " + e.getMessage());
            }
        });

        // Capture Combat-Ranked's order every server tick. RankStore only writes
        // when the list actually changes, so a normal PvP kill/swap is persisted
        // immediately without replacing Combat-Ranked's own kill logic.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (bridge == null || !startupRestoreFinished) return;

            try {
                syncAutomaticRankBackup();
            } catch (ReflectiveOperationException e) {
                System.err.println("[ChillZonePvPRankAdmin] Could not update PvP rank backup: " + e.getMessage());
            }

            // Slower housekeeping: persistent removals and nametag formatting.
            if (++housekeepingTicks < 20) return;
            housekeepingTicks = 0;

            boolean changed = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!exclusions.contains(p.getUUID())) continue;
                try {
                    Object data = bridge.getOrCreate(p.getUUID());
                    if (bridge.position(data) >= 1) {
                        bridge.removeAndCompact(p.getUUID());
                        changed = true;
                    }
                } catch (ReflectiveOperationException e) {
                    System.err.println("[ChillZonePvPRankAdmin] Failed to enforce removed rank: " + e.getMessage());
                }
            }
            try {
                if (bridge.normalizeDisplayLabels()) changed = true;
                // Apply the selected nametag style. Compact mode overrides Combat-Ranked's
                // native [Rank #N] prefix with [#N]; full mode leaves the native wording.
                bridge.enforceSelectedNametagStyle(server);
            } catch (ReflectiveOperationException e) {
                System.err.println("[ChillZonePvPRankAdmin] Could not normalize PvP rank labels: " + e.getMessage());
            }

            if (changed) {
                try {
                    // This can intentionally become empty if an admin removal caused it.
                    syncRankBackup();
                } catch (ReflectiveOperationException e) {
                    System.err.println("[ChillZonePvPRankAdmin] Could not save PvP ranks after housekeeping: " + e.getMessage());
                }
                bridge.refreshOnlineNametags(server);
            }
        });
    }

    private static ServerPlayer online(CommandSourceStack source, String name) {
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            if (p.getGameProfile().name().equalsIgnoreCase(name)) return p;
        }
        source.sendFailure(Component.literal("That player is not currently online."));
        return null;
    }

    private static boolean ready(CommandSourceStack source) {
        if (bridge != null) return true;
        source.sendFailure(Component.literal("Combat-Ranked is not available."));
        return false;
    }

    private static int setRank(CommandSourceStack source, String name, int rank) {
        if (!ready(source)) return 0;
        ServerPlayer p = online(source, name); if (p == null) return 0;
        try {
            exclusions.remove(p.getUUID());
            bridge.moveTo(p.getUUID(), p.getGameProfile().name(), rank);
            syncRankBackup();
            bridge.refreshOnlineNametags(source.getServer());
            source.sendSuccess(() -> Component.literal("Set " + p.getGameProfile().name() + " to PvP Rank #" + rank + "."), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static int removeRank(CommandSourceStack source, String name) {
        if (!ready(source)) return 0;
        ServerPlayer p = online(source, name); if (p == null) return 0;
        try {
            exclusions.add(p.getUUID());
            int old = bridge.removeAndCompact(p.getUUID());
            syncRankBackup();
            bridge.refreshOnlineNametags(source.getServer());
            String suffix = old < 1 ? " (already unranked)" : " (was #" + old + ")";
            source.sendSuccess(() -> Component.literal("Removed " + p.getGameProfile().name() + " from PvP rankings" + suffix + "."), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static int swapRanks(CommandSourceStack source, String aName, String bName) {
        if (!ready(source)) return 0;
        ServerPlayer a = online(source, aName); if (a == null) return 0;
        ServerPlayer b = online(source, bName); if (b == null) return 0;
        if (a.getUUID().equals(b.getUUID())) {
            source.sendFailure(Component.literal("Choose two different players."));
            return 0;
        }
        try {
            exclusions.remove(a.getUUID());
            exclusions.remove(b.getUUID());
            bridge.swap(a.getUUID(), a.getGameProfile().name(), b.getUUID(), b.getGameProfile().name());
            syncRankBackup();
            bridge.refreshOnlineNametags(source.getServer());
            source.sendSuccess(() -> Component.literal("Swapped PvP ranks for " + a.getGameProfile().name() + " and " + b.getGameProfile().name() + "."), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static int resetRank(CommandSourceStack source, String name) {
        if (!ready(source)) return 0;
        ServerPlayer p = online(source, name); if (p == null) return 0;
        try {
            exclusions.remove(p.getUUID());
            int newRank = bridge.moveToBottom(p.getUUID(), p.getGameProfile().name());
            syncRankBackup();
            bridge.refreshOnlineNametags(source.getServer());
            source.sendSuccess(() -> Component.literal("Reset " + p.getGameProfile().name() + " to the bottom of the PvP rankings (#" + newRank + ")."), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static int listRanks(CommandSourceStack source) {
        if (!ready(source)) return 0;
        try {
            List<Map.Entry<UUID, Object>> list = bridge.rankedEntries();
            source.sendSuccess(() -> Component.literal("--- PvP Rankings ---"), false);
            if (list.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No ranked players."), false);
                return 1;
            }
            for (Map.Entry<UUID, Object> e : list) {
                int pos = bridge.position(e.getValue());
                String name = bridge.name(e.getValue());
                source.sendSuccess(() -> Component.literal("#" + pos + " - " + name), false);
            }
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static int nametagStatus(CommandSourceStack source) {
        String style = nametagSettings.isCompact() ? "COMPACT ([#2])" : "FULL ([Rank #2])";
        source.sendSuccess(() -> Component.literal("PvP nametag style: " + style), false);
        return 1;
    }

    private static int toggleNametagStyle(CommandSourceStack source) {
        return setNametagStyle(source, !nametagSettings.isCompact());
    }

    private static int setNametagStyle(CommandSourceStack source, boolean compact) {
        if (!ready(source)) return 0;
        nametagSettings.setCompact(compact);
        bridge.setCompactNametags(compact);
        try {
            bridge.normalizeDisplayLabels();
            bridge.refreshOnlineNametags(source.getServer());
            String style = compact ? "compact ([#2])" : "full ([Rank #2])";
            source.sendSuccess(() -> Component.literal("PvP nametag style set to " + style + ". This setting is saved across restarts."), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static int resetAll(CommandSourceStack source) {
        if (!ready(source)) return 0;
        try {
            bridge.clearAllRanks();
            exclusions.clear();
            rankStore.clear();
            bridge.refreshOnlineNametags(source.getServer());
            source.sendSuccess(() -> Component.literal("All PvP ranks were cleared. Players will receive ranks again through Combat-Ranked's normal system."), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static void syncRankBackup() throws ReflectiveOperationException {
        rankStore.replace(bridge.snapshotRanks());
    }

    private static void syncAutomaticRankBackup() throws ReflectiveOperationException {
        List<RankStore.StoredRank> live = bridge.snapshotRanks();
        List<RankStore.StoredRank> saved = rankStore.snapshot();

        // Combat-Ranked can briefly appear empty while starting/stopping. Never
        // let that transient state erase a real saved Top 10. Intentional admin
        // clears use rankStore.clear()/syncRankBackup() directly and still work.
        if (live.isEmpty() && !saved.isEmpty()) return;
        rankStore.replace(live);
    }

    private static int fail(CommandSourceStack source, Exception e) {
        source.sendFailure(Component.literal("PvP rank change failed. Check the server console."));
        System.err.println("[ChillZonePvPRankAdmin] " + e);
        return 0;
    }
}
