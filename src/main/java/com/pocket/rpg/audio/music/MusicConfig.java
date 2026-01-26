package com.pocket.rpg.audio.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for scene-to-music mappings.
 * Loaded from gameData/config/music.json.
 * <p>
 * Stores asset paths as strings for JSON serialization.
 * Paths are resolved to AudioClip objects at runtime by MusicManager.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MusicConfig {

    private static final String CONFIG_PATH = "gameData/config/music.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Path to default music (played when no scene mapping exists).
     */
    private String defaultMusicPath;

    /**
     * Scene-to-music mappings.
     */
    private List<SceneMusicEntry> sceneMappings = new ArrayList<>();

    /**
     * A single scene-to-music mapping entry.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneMusicEntry {
        /**
         * Path to the scene file (e.g., "scenes/overworld.scene").
         */
        private String scenePath;

        /**
         * Paths to music tracks for this scene.
         * If multiple tracks, one is chosen randomly.
         */
        private List<String> trackPaths = new ArrayList<>();
    }

    /**
     * Load config from file, or create default if not exists.
     */
    public static MusicConfig load() {
        Path path = Paths.get(CONFIG_PATH);

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                MusicConfig config = GSON.fromJson(json, MusicConfig.class);
                if (config == null) {
                    config = new MusicConfig();
                }
                if (config.sceneMappings == null) {
                    config.sceneMappings = new ArrayList<>();
                }
                System.out.println("Loaded music config from: " + CONFIG_PATH);
                return config;
            } catch (IOException e) {
                System.err.println("Failed to load music config: " + e.getMessage());
            }
        }

        // Create default config
        MusicConfig config = new MusicConfig();
        config.save();
        return config;
    }

    /**
     * Save config to file.
     */
    public void save() {
        Path path = Paths.get(CONFIG_PATH);

        try {
            Files.createDirectories(path.getParent());
            String json = GSON.toJson(this);
            Files.writeString(path, json);
            System.out.println("Saved music config to: " + CONFIG_PATH);
        } catch (IOException e) {
            System.err.println("Failed to save music config: " + e.getMessage());
        }
    }

    /**
     * Find music entry for a scene by path.
     *
     * @param scenePath Path to the scene
     * @return Matching entry or null
     */
    public SceneMusicEntry findEntryForScene(String scenePath) {
        if (scenePath == null || sceneMappings == null) {
            return null;
        }

        for (SceneMusicEntry entry : sceneMappings) {
            if (scenePath.equals(entry.getScenePath())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Find music entry for a scene by name (without path/extension).
     *
     * @param sceneName Scene name
     * @return Matching entry or null
     */
    public SceneMusicEntry findEntryForSceneName(String sceneName) {
        if (sceneName == null || sceneMappings == null) {
            return null;
        }

        for (SceneMusicEntry entry : sceneMappings) {
            String entryPath = entry.getScenePath();
            if (entryPath == null) continue;

            // Extract scene name from path
            String entryName = entryPath;
            int lastSlash = entryName.lastIndexOf('/');
            if (lastSlash >= 0) {
                entryName = entryName.substring(lastSlash + 1);
            }
            // Remove extension
            int dotIndex = entryName.lastIndexOf('.');
            if (dotIndex >= 0) {
                entryName = entryName.substring(0, dotIndex);
            }

            if (sceneName.equals(entryName)) {
                return entry;
            }
        }
        return null;
    }
}
