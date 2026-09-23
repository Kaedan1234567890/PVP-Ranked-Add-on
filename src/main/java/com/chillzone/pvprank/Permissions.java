package com.chillzone.pvprank;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class Permissions {
    private Permissions() {}

    static boolean canAdmin(CommandSourceStack source) {
        // Server console / non-player command sources are allowed.
        // Players must be Minecraft server operators (OPs).
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        return source.getServer().getPlayerList().isOp(player.nameAndId());
    }
}
