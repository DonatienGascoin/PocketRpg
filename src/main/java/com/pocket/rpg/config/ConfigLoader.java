package com.pocket.rpg.config;

import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigLoader {

    private static final String GAME_CONFIG_DIR = "gameData/config";
    private static final String EDITOR_CONFIG_DIR = "editor/config";

    public static <T> void saveConfigToFile(T config, ConfigType configType) {
        configFiles.stream().filter(cf -> cf.type == configType)
                .findFirst()
                .ifPresentOrElse(cf -> saveConfigFile(cf.filePath, config),
                        () -> {
                            throw new IllegalArgumentException("Unknown config class: " + configType);
                        });
    }

    public enum ConfigType {
        GAME,
        INPUT,
        RENDERING,
        EDITOR
    }

    private static final List<ConfigFile> configFiles = new ArrayList<>(List.of(
            new ConfigFile(ConfigType.GAME, GAME_CONFIG_DIR + "/game.json", GameConfig.class, new GameConfig()),
            new ConfigFile(ConfigType.INPUT, GAME_CONFIG_DIR + "/input.json", InputConfig.class, new InputConfig()),
            new ConfigFile(ConfigType.RENDERING, GAME_CONFIG_DIR + "/rendering.json", RenderingConfig.class, new RenderingConfig()),
            new ConfigFile(ConfigType.EDITOR, EDITOR_CONFIG_DIR + "/editor.json", EditorConfig.class, EditorConfig.createDefault())
    ));

    /**
     * Load configuration of specified type.
     */
    public static <T> T getConfig(ConfigType type) {
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
        ensureParentDirectory(filePath);

        try {
            FileUtils.serializeAndWriteToFile(filePath, config);
            System.out.println("✓ Saved " + config.getClass().getSimpleName() + " config to: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to save " + config.getClass().getSimpleName() + " config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ensures the parent directory of a file path exists.
     */
    private static void ensureParentDirectory(String filePath) {
        try {
            Path parentPath = Paths.get(filePath).getParent();
            if (parentPath != null && !Files.exists(parentPath)) {
                Files.createDirectories(parentPath);
                System.out.println("Created config directory: " + parentPath);
            }
        } catch (IOException e) {
            System.err.println("Failed to create config directory: " + e.getMessage());
        }
    }

    /**
     * Configuration file representation.
     *
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
