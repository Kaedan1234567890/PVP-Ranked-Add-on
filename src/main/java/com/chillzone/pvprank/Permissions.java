package com.chillzone.pvprank;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class Permissions {
    private Permissions() {}

    static boolean canAdmin(CommandSourceStack source) {
        // Server console / command blocks are allowed. Players must be server operators.
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        return source.getServer().getPlayerList().isOp(player.getGameProfile());
    }
}
