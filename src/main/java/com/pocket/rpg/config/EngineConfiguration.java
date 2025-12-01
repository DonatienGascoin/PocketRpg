package com.pocket.rpg.config;

import lombok.Getter;

@Getter
public class EngineConfiguration {

    private final GameConfig game;
    private final InputConfig input;
    private final RenderingConfig rendering;

    private EngineConfiguration(GameConfig game, InputConfig input, RenderingConfig rendering) {
        this.game = game;
        this.input = input;
        this.rendering = rendering;
    }

    /**
     * Load all configurations from files.
     */
    public static EngineConfiguration load() {
        ConfigLoader.loadAllConfigs();

        GameConfig game = ConfigLoader.getConfig(ConfigLoader.ConfigType.GAME);
        InputConfig input = ConfigLoader.getConfig(ConfigLoader.ConfigType.INPUT);
        RenderingConfig rendering = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);

        ConfigLoader.saveAllConfigs();

        return new EngineConfiguration(game, input, rendering);
    }

    public void saveToFile() {
        saveToFile(this);
    }

    public static void saveToFile(EngineConfiguration engineConfiguration) {
        ConfigLoader.saveConfigToFile(engineConfiguration.game, ConfigLoader.ConfigType.GAME);
        ConfigLoader.saveConfigToFile(engineConfiguration.input, ConfigLoader.ConfigType.INPUT);
        ConfigLoader.saveConfigToFile(engineConfiguration.rendering, ConfigLoader.ConfigType.RENDERING);
    }

    /**
     * Create from existing config objects (useful for testing).
     */
    public static EngineConfiguration from(GameConfig game, InputConfig input, RenderingConfig rendering) {
        return new EngineConfiguration(game, input, rendering);
    }
}
