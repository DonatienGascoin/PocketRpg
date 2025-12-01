package com.pocket.rpg.core;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.glfw.GLFWInputBackend;
import com.pocket.rpg.glfw.GLFWWindow;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.callbacks.DefaultInputCallback;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.rendering.renderers.OpenGLRenderer;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.utils.LogUtils;

/**
 * Main application class for the game.
 * Initializes and runs all core systems.
 */
public class GameApplication {
    // Platform systems
    private AbstractWindow window;
    private InputBackend inputBackend;
    private GameEngine engine;

    private RenderInterface renderer;
    //    private AudioInterface audio;
    private PostProcessor postProcessor;

    private DefaultInputCallback callbacks;

    // Configuration
    private GameConfig gameConfig;
    private InputConfig inputConfig;
    private RenderingConfig renderingConfig;

    /**
     * Initialize all systems.
     */
    private void init() {
        Serializer.init();
        System.out.println(LogUtils.buildBox("Application starting"));

        // Load configuration
        loadConfigurationFiles();

        createSystemDependantSystems();

        callbacks = new DefaultInputCallback();

        // Create window
        window = new GLFWWindow(gameConfig, inputBackend, callbacks);
        window.init();


        // Create game engine
        initGameEngine();

        System.out.println("Application initialization complete");
    }

    private void loadConfigurationFiles() {
        ConfigLoader.loadAllConfigs();

        gameConfig = ConfigLoader.loadConfig(ConfigLoader.ConfigType.GAME);
        inputConfig = ConfigLoader.loadConfig(ConfigLoader.ConfigType.INPUT);
        renderingConfig = ConfigLoader.loadConfig(ConfigLoader.ConfigType.RENDERING);
        ConfigLoader.saveAllConfigs();
    }

    /**
     * Initialize the main game engine.
     */
    private void initGameEngine() {
        System.out.println("Creating game engine...");
        engine = GameEngine.builder()
                .inputConfig(inputConfig)
                .gameConfig(gameConfig)
                .renderingConfig(renderingConfig)
                .window(window)
                .renderer(renderer)
                .inputCallbacks(callbacks)
                .inputConfig(inputConfig)
                .postProcessor(postProcessor)
                .build();

        engine.initialize();
    }

    /**
     * Create platform-dependent systems (window, renderer, audio, etc.)
     * Ex. GLFW for window and input, OpenGL for rendering.
     * Initialization during GameEngine init.
     */
    private void createSystemDependantSystems() {
        System.out.println("Initializing platform systems...");
        inputBackend = new GLFWInputBackend();
        renderer = new OpenGLRenderer(renderingConfig);
        //        audio = new NoOpAudioManager();
        postProcessor = new PostProcessor(gameConfig);
    }

    /**
     * Run the application.
     */
    public void run() {
        try {
            init();
            engine.loop();
            destroy();
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            destroy();
        }
    }

    /**
     * Clean up all systems.
     */
    private void destroy() {
        System.out.println("Destroying application...");

        if (engine != null) {
            engine.destroy();
        }

        System.out.println("Application destroyed");
    }
}
