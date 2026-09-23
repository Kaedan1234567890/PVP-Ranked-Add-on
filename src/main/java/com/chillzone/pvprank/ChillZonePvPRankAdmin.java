package com.chillzone.pvprank;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
    private static int ticks;

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            builder.suggest(p.getGameProfile().name());
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        exclusions.load();
        try {
            bridge = new CombatRankBridge();
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
                .then(Commands.literal("resetall")
                    .then(Commands.literal("confirm").executes(ctx -> resetAll(ctx.getSource()))))
            );
        });

        // Combat-Ranked automatically assigns unranked players when they join.
        // Enforce /pvprank remove as a persistent exclusion once per second.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks < 20 || bridge == null) return;
            ticks = 0;
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
            if (changed) bridge.refreshOnlineNametags(server);
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

    private static int resetAll(CommandSourceStack source) {
        if (!ready(source)) return 0;
        try {
            bridge.clearAllRanks();
            exclusions.clear();
            bridge.refreshOnlineNametags(source.getServer());
            source.sendSuccess(() -> Component.literal("All PvP ranks were cleared. Players will receive ranks again through Combat-Ranked's normal system."), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            return fail(source, e);
        }
    }

    private static int fail(CommandSourceStack source, Exception e) {
        source.sendFailure(Component.literal("PvP rank change failed. Check the server console."));
        System.err.println("[ChillZonePvPRankAdmin] " + e);
        return 0;
    }
}
