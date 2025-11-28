package com.pocket.rpg.config;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigLoader {

    private static final String CONFIG_DIR = "gameData/config";
    private static final String GAME_CONFIG_FILE = CONFIG_DIR + "/game.json";
    private static final String INPUT_CONFIG_FILE = CONFIG_DIR + "/input.json";

    /**
     * Ensures the config directory exists.
     */
    private static void ensureConfigDirectory() {
        try {
            Path configPath = Paths.get(CONFIG_DIR);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                System.out.println("Created config directory: " + CONFIG_DIR);
            }
        } catch (IOException e) {
            System.err.println("Failed to create config directory: " + e.getMessage());
        }
    }

    /**
     * Loads window configuration from JSON file.
     * Returns default config if file doesn't exist or loading fails.
     *
     * @return validated window configuration
     */
    public static GameConfig loadGameConfig() {
        File file = new File(GAME_CONFIG_FILE);

        if (!file.exists()) {
            System.out.println("Game config not found, creating default: " + GAME_CONFIG_FILE);
            GameConfig defaultConfig = new GameConfig();
            saveWindowConfig(defaultConfig);
            return defaultConfig;
        }
        try {

            return FileUtils.readFileAndDeserialize(file, GameConfig.class);
        } catch (Exception e) {
            System.err.println("Failed to load window config: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Using default configuration");
            return new GameConfig();
        }
    }

    /**
     * Saves window configuration to JSON file.
     * Validates before saving.
     *
     * @param config configuration to save
     */
    public static void saveWindowConfig(GameConfig config) {
        ensureConfigDirectory();

        try {
            FileUtils.serializeAndWriteToFile(GAME_CONFIG_FILE, config);
            System.out.println("✓ Saved window config to: " + GAME_CONFIG_FILE);
        } catch (Exception e) {
            System.err.println("Failed to save window config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads input configuration from JSON file.
     * Returns default config if file doesn't exist or loading fails.
     *
     * @return validated window configuration
     */
    public static InputConfig loadInputConfig() {
        File file = new File(INPUT_CONFIG_FILE);

        if (!file.exists()) {
            System.out.println("Game config not found, creating default: " + INPUT_CONFIG_FILE);
            InputConfig defaultConfig = new InputConfig();
            saveInputConfig(defaultConfig);
            return defaultConfig;
        }
        try {

            return FileUtils.readFileAndDeserialize(file, InputConfig.class);
        } catch (Exception e) {
            System.err.println("Failed to load input config: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Using default configuration");
            return new InputConfig();
        }
    }

    /**
     * Saves input configuration to JSON file.
     * Validates before saving.
     *
     * @param config configuration to save
     */
    public static void saveInputConfig(InputConfig config) {
        ensureConfigDirectory();

        try {
            FileUtils.serializeAndWriteToFile(INPUT_CONFIG_FILE, config);
            System.out.println("✓ Saved window config to: " + INPUT_CONFIG_FILE);
        } catch (Exception e) {
            System.err.println("Failed to save window config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
