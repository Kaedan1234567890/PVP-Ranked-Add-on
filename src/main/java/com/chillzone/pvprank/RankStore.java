package com.chillzone.pvprank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private static final Path TEMP_FILE = FILE.resolveSibling("saved-ranks.json.tmp");
    private static final int MAX_RANK = 10;

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
                ranks.addAll(normalize(loaded));
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
        List<StoredRank> normalized = normalize(updated);

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

            // Write the complete snapshot to a temporary file first. Only once
            // that succeeds do we replace the real save file, avoiding a
            // half-written ranking list if the server is stopped mid-write.
            try (Writer w = Files.newBufferedWriter(TEMP_FILE)) {
                GSON.toJson(ranks, TYPE, w);
            }

            try {
                Files.move(TEMP_FILE, FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(TEMP_FILE, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not save PvP rank backup: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(TEMP_FILE); } catch (IOException ignored) {}
        }
    }

    private static List<StoredRank> normalize(List<StoredRank> source) {
        List<StoredRank> result = copy(source);
        result.removeIf(entry -> entry.uuid == null || entry.position < 1 || entry.position > MAX_RANK);
        result.sort(Comparator.comparingInt(entry -> entry.position));

        // A valid Top 10 can contain each UUID and each position only once.
        Set<UUID> seenPlayers = new HashSet<>();
        Set<Integer> seenPositions = new HashSet<>();
        result.removeIf(entry -> !seenPlayers.add(entry.uuid) || !seenPositions.add(entry.position));
        return result;
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
