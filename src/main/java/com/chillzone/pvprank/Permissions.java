package com.chillzone.pvprank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class Permissions {
    static final String ADMIN = "chillzonepvprank.admin";

    private Permissions() {}

    static boolean canAdmin(CommandSourceStack source) {
        // Server console is always allowed.
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            return user != null && user.getCachedData().getPermissionData().checkPermission(ADMIN).asBoolean();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }
}
