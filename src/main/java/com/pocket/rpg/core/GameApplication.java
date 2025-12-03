package com.pocket.rpg.core;

import com.pocket.rpg.config.EngineConfiguration;
import com.pocket.rpg.glfw.GLFWPlatformFactory;
import com.pocket.rpg.input.DefaultInputContext;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.InputContext;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import com.pocket.rpg.postProcessing.PostProcessing;
import com.pocket.rpg.postProcessing.PostProcessor;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.time.DefaultTimeContext;
import com.pocket.rpg.time.Time;
import com.pocket.rpg.time.TimeContext;
import com.pocket.rpg.utils.LogUtils;
import com.pocket.rpg.utils.PerformanceMonitor;

/**
 * Main application class for the game.
 * Initializes and runs all core systems.
 */
public class GameApplication {

    private PlatformFactory platformFactory;

    // Platform systems
    private AbstractWindow window;
    private GameEngine engine;
    private CameraSystem cameraSystem;

    private RenderInterface renderer;
    //    private AudioInterface audio;
    private PostProcessor postProcessor;
    private InputEventBus inputEventBus;
    private PerformanceMonitor performanceMonitor;

    // Configuration
    private EngineConfiguration config;

    /**
     * Initialize all systems.
     */
    private void init() {
        Serializer.init();
        System.out.println(LogUtils.buildBox("Application starting"));

        // Load configuration
        config = EngineConfiguration.load();

        inputEventBus = new InputEventBus();
        TimeContext timeContext = new DefaultTimeContext();
        Time.initialize(timeContext);

        // Select platform
        platformFactory = selectPlatform();
        System.out.println("Using platform: " + platformFactory.getPlatformName());

        // Create platform systems
        createCameraSystem();
        createPlatformSystems();

        setupInputSystem();

        // Create game engine
        createGameEngine();

        System.out.println("Application initialization complete");
    }

    private PlatformFactory selectPlatform() {
        // Could read from config or environment variable
        String platform = System.getProperty("game.platform", "glfw");

        return switch (platform.toLowerCase()) {
            case "glfw" -> new GLFWPlatformFactory();
            default -> {
                System.out.println("Unknown platform: " + platform + ", using GLFW");
                yield new GLFWPlatformFactory();
            }
        };
    }

    private void setupInputSystem() {
        System.out.println("Setting up input system...");

        KeyListener keyListener = new KeyListener();
        MouseListener mouseListener = new MouseListener();
        GamepadListener gamepadListener = new GamepadListener();

        inputEventBus.addKeyListener(keyListener);
        inputEventBus.addMouseListener(mouseListener);
        inputEventBus.addGamepadListener(gamepadListener);

        // Create input context
        InputContext realContext = new DefaultInputContext(config.getInput(), keyListener, mouseListener, gamepadListener);

        // Initialize InputManager
        Input.initialize(realContext);
    }

    /**
     * Create platform-dependent systems (window, renderer, audio, etc.)
     * Ex. GLFW for window and input, OpenGL for rendering.
     * Initialization during GameEngine init.
     */
    private void createPlatformSystems() {
        System.out.println("Initializing platform systems...");

        InputBackend inputBackend = platformFactory.createInputBackend();

        // Create window
        window = platformFactory.createWindow(config.getGame(), inputBackend, inputEventBus);
        window.init();

        // Create renderer (now owns OverlayRenderer internally)
        renderer = platformFactory.createRenderer(cameraSystem, config.getRendering());
        renderer.init(config.getGame().getGameWidth(), config.getGame().getGameHeight());

        //        audio = new NoOpAudioManager();

        // Create post-processor
        postProcessor = platformFactory.createPostProcessor(config.getGame());
        postProcessor.init(window);
        PostProcessing.initialize(postProcessor);

        // Create performance monitor
        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(config.getRendering().isEnableStatistics());

        // OverlayRenderer is now created and owned by renderer
        // No longer created here!
    }

    /**
     * Initialize the main game engine.
     */
    private void createGameEngine() {
        System.out.println("Creating game engine...");
        engine = GameEngine.builder()
                .config(config)
                .window(window)
                .renderer(renderer)
                .inputEventBus(inputEventBus)
                .postProcessor(postProcessor)
                .cameraSystem(cameraSystem)
                // OverlayRenderer is now obtained from renderer, not passed directly
                .build();

        engine.initialize();
    }

    /**
     * Run the application.
     */
    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            destroy();
        }
    }


    /**
     * Main application loop.
     * Runs until the window is closed.
     */
    public void loop() {
        System.out.println("Starting main loop...");

        while (!window.shouldClose()) {
            processFrame();
        }

        System.out.println("Exited main loop");
    }

    private void processFrame() {
        // Handle minimized window
        if (handleMinimizedWindow()) {
            return;
        }

        // Begin post-processing capture
        postProcessor.beginCapture();

        // Update and render game
        try {
            engine.update(Time.deltaTime());
            engine.render();
        } catch (Exception e) {
            System.err.println("ERROR in game loop: " + e.getMessage());
            e.printStackTrace();
        }

        // Apply post-processing
        postProcessor.endCaptureAndApplyEffects();

        // Swap buffers and poll events
        window.swapBuffers();
        window.pollEvents();

        Input.endFrame();

        // Update time and performance, last things in loop
        Time.update();
        performanceMonitor.update();
    }

    /**
     * Clean up all systems.
     */
    private void destroy() {
        System.out.println("Destroying application...");

        // Destroy in reverse order of creation
        if (engine != null) {
            engine.destroy();
        }

        Input.destroy();

        // Destroy platform systems
        // OverlayRenderer is destroyed by renderer
        if (postProcessor != null) {
            postProcessor.destroy();
        }
        if (renderer != null) {
            renderer.destroy();
        }
        if (window != null) {
            window.destroy();
        }

        System.out.println("Application destroyed");
    }

    private boolean handleMinimizedWindow() {
        if (!window.isVisible()) {
            window.pollEvents();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
            Time.update();
            return true;
        }
        return false;
    }


    private void createCameraSystem() {
        System.out.println("Initializing camera system...");
        cameraSystem = new CameraSystem(config.getGame());
        inputEventBus.addResizeListener(cameraSystem::setViewportSize);
    }
}