package com.pocket.rpg.config;

import com.pocket.rpg.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ConfigLoader {

    private static final String CONFIG_DIR = "gameData/config";

    public enum ConfigType {
        GAME,
        INPUT,
        RENDERING
    }

    private static final List<ConfigFile> configFiles = List.of(
            new ConfigFile(ConfigType.GAME, CONFIG_DIR + "/game.json", GameConfig.class, new GameConfig()),
            new ConfigFile(ConfigType.INPUT, CONFIG_DIR + "/input.json", InputConfig.class, new InputConfig()),
            new ConfigFile(ConfigType.RENDERING, CONFIG_DIR + "/rendering.json", RenderingConfig.class, new RenderingConfig())
    );

    /**
     * Load configuration of specified type.
     */
    public static <T> T loadConfig(ConfigType type) {
        for (ConfigFile configFile : configFiles) {
            if (configFile.type == type) {
                return (T) configFile.configInstance;
            }
        }
        throw new IllegalArgumentException("Unknown config type: " + type);
    }

    /**
     * Save all configurations to their respective JSON files.
     */
    public static void saveAllConfigs() {
        for (ConfigFile configFile : configFiles) {
            saveConfigFile(configFile.filePath, configFile.configInstance);
        }
    }

    /**
     * Load all configurations from their respective JSON files.
     */
    public static void loadAllConfigs() {
        for (ConfigFile configFile : configFiles) {
            configFile.configInstance = loadConfigFile(configFile.filePath, configFile.clazz, configFile.defaultConfig);
        }
    }

    /**
     * Load configuration from JSON file.
     */
    private static <T> T loadConfigFile(String filePath, Class<T> clazz, T defaultConfig) {
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println(clazz.getSimpleName() + " config not found, creating default: " + filePath);
            saveConfigFile(filePath, defaultConfig);
            return defaultConfig;
        }
        try {

            return FileUtils.readFileAndDeserialize(file, clazz);
        } catch (Exception e) {
            System.err.println("Failed to load " + defaultConfig.getClass().getSimpleName() + " config: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Using default configuration");
            return defaultConfig;
        }
    }

    /**
     * Save configuration to JSON file.
     */
    private static <T> void saveConfigFile(String filePath, T config) {
        ensureConfigDirectory();

        try {
            FileUtils.serializeAndWriteToFile(filePath, config);
            System.out.println("✓ Saved " + config.getClass().getSimpleName() + " config to: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to save " + config.getClass().getSimpleName() + " config: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
     * Configuration file representation.
     * @param <T> Type of the configuration
     */
    private static class ConfigFile<T> {
        public ConfigType type;
        public String filePath;
        public Class<?> clazz;
        public Object defaultConfig;

        public T configInstance;

        public ConfigFile(ConfigType type, String filePath, Class<T> clazz, Object defaultConfig) {
            this.type = type;
            this.filePath = filePath;
            this.clazz = clazz;
            this.defaultConfig = defaultConfig;
        }
    }
}
