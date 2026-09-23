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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Independent persistent backup of Combat-Ranked's ranking table.
 *
 * This intentionally lives in this add-on's own config folder so a Combat-Ranked
 * restart/load issue cannot wipe the last known-good Chill Zone ranking list.
 */
final class RankStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<StoredRank>>(){}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-pvprank-admin")
            .resolve("saved-ranks.json");

    static final class StoredRank {
        UUID uuid;
        String name;
        int position;

        StoredRank(UUID uuid, String name, int position) {
            this.uuid = uuid;
            this.name = name;
            this.position = position;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StoredRank other)) return false;
            return position == other.position
                    && Objects.equals(uuid, other.uuid)
                    && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, name, position);
        }
    }

    private final List<StoredRank> ranks = new ArrayList<>();
    private boolean snapshotExists;

    void load() {
        ranks.clear();
        snapshotExists = false;
        if (!Files.exists(FILE)) return;

        try (Reader r = Files.newBufferedReader(FILE)) {
            List<StoredRank> loaded = GSON.fromJson(r, TYPE);
            if (loaded != null) {
                loaded.removeIf(entry -> entry == null || entry.uuid == null || entry.position < 1);
                loaded.sort(Comparator.comparingInt(entry -> entry.position));
                ranks.addAll(loaded);
            }
            // Only trust the snapshot after it was read successfully. A damaged
            // file must never cause an empty list to overwrite live rankings.
            snapshotExists = true;
        } catch (Exception e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not load saved PvP ranks; live ranks will be preserved: " + e.getMessage());
        }
    }

    boolean hasSnapshot() {
        return snapshotExists;
    }

    List<StoredRank> snapshot() {
        return copy(ranks);
    }

    void replace(List<StoredRank> updated) {
        List<StoredRank> normalized = copy(updated);
        normalized.removeIf(entry -> entry.uuid == null || entry.position < 1);
        normalized.sort(Comparator.comparingInt(entry -> entry.position));

        if (snapshotExists && ranks.equals(normalized)) return;

        ranks.clear();
        ranks.addAll(normalized);
        snapshotExists = true;
        save();
    }

    void clear() {
        ranks.clear();
        snapshotExists = true;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(ranks, TYPE, w);
            }
        } catch (IOException e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not save PvP rank backup: " + e.getMessage());
        }
    }

    private static List<StoredRank> copy(List<StoredRank> source) {
        List<StoredRank> result = new ArrayList<>();
        if (source == null) return result;
        for (StoredRank entry : source) {
            if (entry == null) continue;
            result.add(new StoredRank(entry.uuid, entry.name, entry.position));
        }
        return result;
    }
}
