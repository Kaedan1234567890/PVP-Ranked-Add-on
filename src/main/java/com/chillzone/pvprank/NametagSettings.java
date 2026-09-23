package com.chillzone.pvprank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Persists PvP-rank settings across restarts. */
final class NametagSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-pvprank-admin")
            .resolve("settings.json");
    private static final Path TEMP_FILE = FILE.resolveSibling("settings.json.tmp");

    private static final class Config {
        boolean compactNametags = true;
        // Wrapper type is intentional: old 0.1.5 settings files do not contain
        // this field, so null means the backwards-compatible default: ON.
        Boolean rankingEnabled = null;
    }

    private boolean compact = true;
    private boolean rankingEnabled = true;

    void load() {
        compact = true;
        rankingEnabled = true;
        if (!Files.exists(FILE)) return;

        try (Reader reader = Files.newBufferedReader(FILE)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config != null) {
                compact = config.compactNametags;
                rankingEnabled = config.rankingEnabled == null || config.rankingEnabled;
            }
        } catch (Exception e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not load settings; using defaults: " + e.getMessage());
        }
    }

    boolean isCompact() {
        return compact;
    }

    void setCompact(boolean compact) {
        this.compact = compact;
        save();
    }

    boolean isRankingEnabled() {
        return rankingEnabled;
    }

    void setRankingEnabled(boolean enabled) {
        this.rankingEnabled = enabled;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Config config = new Config();
            config.compactNametags = compact;
            config.rankingEnabled = rankingEnabled;

            try (Writer writer = Files.newBufferedWriter(TEMP_FILE)) {
                GSON.toJson(config, writer);
            }

            try {
                Files.move(TEMP_FILE, FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(TEMP_FILE, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not save settings: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(TEMP_FILE); } catch (IOException ignored) {}
        }
    }
}
