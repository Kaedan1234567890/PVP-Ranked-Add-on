package com.chillzone.pvprank;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

final class CombatRankBridge {
    private final Class<?> playerDataClass;
    private final Method getAllPlayersData;
    private final Method getOrCreatePlayerData;
    private final Method save;
    private final Method updatePlayerNametag;
    private final Field rankPosition;
    private final Field rank;
    private final Field playerName;

    CombatRankBridge() throws ReflectiveOperationException {
        Class<?> dataManager = Class.forName("com.combat.DataManager");
        playerDataClass = Class.forName("com.combat.PlayerData");
        Class<?> combatMod = Class.forName("com.combat.CombatMod");

        getAllPlayersData = dataManager.getMethod("getAllPlayersData");
        getOrCreatePlayerData = dataManager.getMethod("getOrCreatePlayerData", UUID.class);
        save = dataManager.getMethod("save");
        updatePlayerNametag = combatMod.getMethod("updatePlayerNametag", ServerPlayer.class);

        rankPosition = playerDataClass.getField("rankPosition");
        rank = playerDataClass.getField("rank");
        playerName = playerDataClass.getField("playerName");
    }

    @SuppressWarnings("unchecked")
    Map<UUID, Object> all() throws ReflectiveOperationException {
        return (Map<UUID, Object>) getAllPlayersData.invoke(null);
    }

    Object getOrCreate(UUID id) throws ReflectiveOperationException {
        return getOrCreatePlayerData.invoke(null, id);
    }

    int position(Object data) throws IllegalAccessException {
        return rankPosition.getInt(data);
    }

    String name(Object data) throws IllegalAccessException {
        Object value = playerName.get(data);
        return value == null ? "Unknown" : value.toString();
    }

    void setPosition(Object data, int pos) throws IllegalAccessException {
        rankPosition.setInt(data, pos);
        rank.set(data, pos < 1 ? "unranked" : "Rank #" + pos);
    }

    void setPlayerName(Object data, String name) throws IllegalAccessException {
        playerName.set(data, name);
    }

    void save() throws ReflectiveOperationException {
        save.invoke(null);
    }

    void refreshOnlineNametags(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                updatePlayerNametag.invoke(null, p);
            } catch (ReflectiveOperationException e) {
                System.err.println("[ChillZonePvPRankAdmin] Could not refresh nametag for " + p.getGameProfile().name());
            }
        }
    }

    List<Map.Entry<UUID, Object>> rankedEntries() throws ReflectiveOperationException {
        List<Map.Entry<UUID, Object>> list = new ArrayList<>();
        for (Map.Entry<UUID, Object> e : all().entrySet()) {
            try {
                if (position(e.getValue()) >= 1) list.add(e);
            } catch (IllegalAccessException ignored) {}
        }
        list.sort(Comparator.comparingInt(e -> {
            try { return position(e.getValue()); }
            catch (IllegalAccessException ex) { return Integer.MAX_VALUE; }
        }));
        return list;
    }

    void moveTo(UUID id, String currentName, int newPos) throws ReflectiveOperationException {
        Map<UUID, Object> all = all();
        Object target = getOrCreate(id);
        setPlayerName(target, currentName);
        int oldPos = position(target);

        if (oldPos == newPos) {
            setPosition(target, newPos);
            save();
            return;
        }

        for (Map.Entry<UUID, Object> e : all.entrySet()) {
            if (e.getKey().equals(id)) continue;
            Object data = e.getValue();
            int pos = position(data);
            if (pos < 1) continue;

            if (oldPos < 1) {
                if (pos >= newPos) setPosition(data, pos + 1);
            } else if (newPos < oldPos) {
                if (pos >= newPos && pos < oldPos) setPosition(data, pos + 1);
            } else {
                if (pos > oldPos && pos <= newPos) setPosition(data, pos - 1);
            }
        }

        setPosition(target, newPos);
        save();
    }

    int removeAndCompact(UUID id) throws ReflectiveOperationException {
        Object target = getOrCreate(id);
        int oldPos = position(target);
        if (oldPos < 1) {
            setPosition(target, -1);
            save();
            return -1;
        }

        setPosition(target, -1);
        for (Map.Entry<UUID, Object> e : all().entrySet()) {
            if (e.getKey().equals(id)) continue;
            Object data = e.getValue();
            int pos = position(data);
            if (pos > oldPos) setPosition(data, pos - 1);
        }
        save();
        return oldPos;
    }

    void swap(UUID a, String aName, UUID b, String bName) throws ReflectiveOperationException {
        Object da = getOrCreate(a);
        Object db = getOrCreate(b);
        setPlayerName(da, aName);
        setPlayerName(db, bName);
        int pa = position(da);
        int pb = position(db);
        setPosition(da, pb);
        setPosition(db, pa);
        save();
    }

    int moveToBottom(UUID id, String name) throws ReflectiveOperationException {
        removeAndCompact(id);
        int max = 0;
        for (Object data : all().values()) max = Math.max(max, position(data));
        int next = max + 1;
        Object target = getOrCreate(id);
        setPlayerName(target, name);
        setPosition(target, next);
        save();
        return next;
    }

    void clearAllRanks() throws ReflectiveOperationException {
        for (Object data : all().values()) setPosition(data, -1);
        save();
    }
}
