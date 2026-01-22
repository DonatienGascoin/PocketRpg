package com.pocket.rpg.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Audio configuration loaded from gameData/config/audio.json.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioConfig {

    private static final String CONFIG_PATH = "gameData/config/audio.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Builder.Default
    private float masterVolume = 1.0f;

    @Builder.Default
    private float musicVolume = 0.8f;

    @Builder.Default
    private float sfxVolume = 1.0f;

    @Builder.Default
    private float voiceVolume = 1.0f;

    @Builder.Default
    private float ambientVolume = 0.7f;

    @Builder.Default
    private float uiVolume = 1.0f;

    @Builder.Default
    private int maxSimultaneousSounds = 32;

    @Builder.Default
    private float defaultRolloffFactor = 1.0f;

    @Builder.Default
    private float musicCrossfadeDuration = 2.0f;

    @Builder.Default
    private boolean enableReverb = false;

    /**
     * Load config from file, or create default if not exists.
     */
    public static AudioConfig load() {
        Path path = Paths.get(CONFIG_PATH);

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                AudioConfig config = GSON.fromJson(json, AudioConfig.class);
                System.out.println("Loaded audio config from: " + CONFIG_PATH);
                return config;
            } catch (IOException e) {
                System.err.println("Failed to load audio config: " + e.getMessage());
            }
        }

        // Create default config
        AudioConfig config = AudioConfig.builder().build();
        config.save();
        return config;
    }

    /**
     * Save config to file.
     */
    public void save() {
        Path path = Paths.get(CONFIG_PATH);

        try {
            // Ensure parent directory exists
            Files.createDirectories(path.getParent());

            String json = GSON.toJson(this);
            Files.writeString(path, json);
            System.out.println("Saved audio config to: " + CONFIG_PATH);
        } catch (IOException e) {
            System.err.println("Failed to save audio config: " + e.getMessage());
        }
    }
}
