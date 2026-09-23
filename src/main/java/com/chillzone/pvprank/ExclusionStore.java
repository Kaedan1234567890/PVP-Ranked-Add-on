package com.chillzone.pvprank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class ExclusionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Set<UUID>>(){}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-pvprank-admin")
            .resolve("excluded-players.json");
    private final Set<UUID> excluded = new HashSet<>();

    void load() {
        excluded.clear();
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                save();
                return;
            }
            try (Reader r = Files.newBufferedReader(FILE)) {
                Set<UUID> loaded = GSON.fromJson(r, TYPE);
                if (loaded != null) excluded.addAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not load exclusions: " + e.getMessage());
        }
    }

    void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(excluded, TYPE, w);
            }
        } catch (IOException e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not save exclusions: " + e.getMessage());
        }
    }

    boolean contains(UUID id) { return excluded.contains(id); }
    void add(UUID id) { if (excluded.add(id)) save(); }
    void remove(UUID id) { if (excluded.remove(id)) save(); }
    void clear() { excluded.clear(); save(); }
}
