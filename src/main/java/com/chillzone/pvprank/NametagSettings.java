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

/** Persists the owner's selected PvP nametag wording across restarts. */
final class NametagSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("chillzone-pvprank-admin")
            .resolve("settings.json");
    private static final Path TEMP_FILE = FILE.resolveSibling("settings.json.tmp");

    private static final class Config {
        boolean compactNametags = true;
    }

    private boolean compact = true;

    void load() {
        compact = true;
        if (!Files.exists(FILE)) return;

        try (Reader reader = Files.newBufferedReader(FILE)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config != null) compact = config.compactNametags;
        } catch (Exception e) {
            System.err.println("[ChillZonePvPRankAdmin] Could not load nametag settings; using compact mode: " + e.getMessage());
        }
    }

    boolean isCompact() {
        return compact;
    }

    void setCompact(boolean compact) {
        this.compact = compact;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Config config = new Config();
            config.compactNametags = compact;

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
            System.err.println("[ChillZonePvPRankAdmin] Could not save nametag settings: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(TEMP_FILE); } catch (IOException ignored) {}
        }
    }
}
