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
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.time.DefaultTimeContext;
import com.pocket.rpg.time.Time;
import com.pocket.rpg.time.TimeContext;
import com.pocket.rpg.ui.UIInputHandler;
import com.pocket.rpg.ui.UIRenderer;
import com.pocket.rpg.utils.LogUtils;
import com.pocket.rpg.utils.PerformanceMonitor;

/**
 * Main application class for the game.
 * <p>
 * Game Loop Order:
 * 1. Poll window events (keyboard, mouse, gamepad)
 * 2. Update UI input (sets mouse consumed flag)
 * 3. Update game logic (respects mouse consumption)
 * 4. Render game world
 * 5. Apply post-processing
 * 6. Render UI (on top, unaffected by post-processing)
 * 7. Swap buffers
 */
public class GameApplication {

    private PlatformFactory platformFactory;

    private AbstractWindow window;
    private GameEngine engine;
    private ViewportConfig viewportConfig;

    private RenderInterface renderer;
    private UIRenderer uiRenderer;
    private PostProcessor postProcessor;
    private InputEventBus inputEventBus;
    private PerformanceMonitor performanceMonitor;

    // UI Input Handler - processes mouse input for UI elements
    private UIInputHandler uiInputHandler;

    private EngineConfiguration config;

    private void init() {
        Serializer.init();
        System.out.println(LogUtils.buildBox("Application starting"));

        config = EngineConfiguration.load();

        inputEventBus = new InputEventBus();
        TimeContext timeContext = new DefaultTimeContext();
        Time.initialize(timeContext);

        platformFactory = selectPlatform();
        System.out.println("Using platform: " + platformFactory.getPlatformName());

        createViewportConfig();
        createPlatformSystems();
        setupInputSystem();
        createUIInputHandler();
        createGameEngine();

        System.out.println("Application initialization complete");
    }

    private PlatformFactory selectPlatform() {
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

        InputContext realContext = new DefaultInputContext(config.getInput(), keyListener, mouseListener, gamepadListener);
        Input.initialize(realContext);
    }

    private void createUIInputHandler() {
        System.out.println("Creating UI input handler...");
        uiInputHandler = new UIInputHandler(config.getGame());
    }

    private void createPlatformSystems() {
        System.out.println("Initializing platform systems...");

        InputBackend inputBackend = platformFactory.createInputBackend();

        window = platformFactory.createWindow(config.getGame(), inputBackend, inputEventBus);
        window.init();

        renderer = platformFactory.createRenderer(viewportConfig, config.getRendering());
        renderer.init(config.getGame().getGameWidth(), config.getGame().getGameHeight());

        // Create UI renderer via platform factory
        uiRenderer = platformFactory.createUIRenderer();

        postProcessor = platformFactory.createPostProcessor(config.getGame());
        postProcessor.init(window);
        PostProcessing.initialize(postProcessor);

        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(config.getRendering().isEnableStatistics());
    }

    private void createGameEngine() {
        System.out.println("Creating game engine...");
        engine = GameEngine.builder()
                .config(config)
                .window(window)
                .renderer(renderer)
                .uiRenderer(uiRenderer)
                .inputEventBus(inputEventBus)
                .postProcessor(postProcessor)
                .viewportConfig(viewportConfig)
                .build();

        // Set the UI input handler
        engine.setUIInputHandler(uiInputHandler);

        engine.initialize();
    }

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

    public void loop() {
        System.out.println("Starting main loop...");

        while (!window.shouldClose()) {
            processFrame();
        }

        System.out.println("Exited main loop");
    }

    /**
     * Main game loop frame processing.
     * <p>
     * Order of operations:
     * 1. Handle minimized window (skip rendering)
     * 2. Poll events (populates Input state)
     * 3. Update UI input FIRST (sets mouse consumed flag)
     * 4. Update game logic (Input methods respect consumption)
     * 5. Render game world to framebuffer
     * 6. Apply post-processing effects
     * 7. Render UI (not affected by post-processing)
     * 8. Swap buffers and end frame
     */
    private void processFrame() {
        if (handleMinimizedWindow()) {
            return;
        }

        // ============================================
        // 1. POLL EVENTS
        // ============================================
        // This updates keyboard, mouse, gamepad state
        window.pollEvents();

        // ============================================
        // 2. UPDATE UI INPUT (BEFORE GAME LOGIC)
        // ============================================
        // This must happen BEFORE game update so that:
        // - UI can consume mouse input
        // - Game code using Input.getMouseButtonDown() automatically
        //   gets false if UI consumed the input
        updateUIInput();

        // ============================================
        // 3. BEGIN POST-PROCESSING CAPTURE
        // ============================================
        postProcessor.beginCapture();

        // ============================================
        // 4. UPDATE GAME LOGIC
        // ============================================
        // At this point, if UI consumed mouse input:
        // - Input.getMouseButtonDown() returns false
        // - Input.getMouseButton() returns false
        // - Input.getMouseButtonUp() returns false
        // Game code doesn't need to check isMouseConsumed()!
        try {
            engine.update(Time.deltaTime());
        } catch (Exception e) {
            System.err.println("ERROR in game update: " + e.getMessage());
            e.printStackTrace();
        }

        // ============================================
        // 5. RENDER GAME WORLD
        // ============================================
        try {
            engine.render();
        } catch (Exception e) {
            System.err.println("ERROR in game render: " + e.getMessage());
            e.printStackTrace();
        }

        // ============================================
        // 6. APPLY POST-PROCESSING
        // ============================================
        postProcessor.endCaptureAndApplyEffects();

        // ============================================
        // 7. RENDER UI (AFTER POST-PROCESSING)
        // ============================================
        // UI is rendered AFTER post-processing so it's not
        // affected by blur, bloom, color grading, etc.
        try {
            engine.renderUI();
        } catch (Exception e) {
            System.err.println("ERROR in UI rendering: " + e.getMessage());
            e.printStackTrace();
        }

        // ============================================
        // 8. SWAP BUFFERS AND END FRAME
        // ============================================
        window.swapBuffers();

        // Clear per-frame input state (pressed/released flags)
        Input.endFrame();

        // Update time for next frame
        Time.update();
        performanceMonitor.update();
    }

    /**
     * Updates UI input handling.
     * Converts screen mouse coordinates to game coordinates and
     * processes UI hover/click events.
     */
    private void updateUIInput() {
        // Get screen mouse position
        var screenMousePos = Input.getMousePosition();

        // Convert screen coordinates to game coordinates
        // This accounts for pillarbox/letterbox scaling
        float gameMouseX = viewportConfig.windowToGameX(screenMousePos.x);
        float gameMouseY = viewportConfig.windowToGameY(screenMousePos.y);

        // Update UI input - this may set Input.mouseConsumed = true
        engine.updateUIInput(gameMouseX, gameMouseY);
    }

    private void destroy() {
        System.out.println("Destroying application...");

        if (engine != null) {
            engine.destroy();
        }

        Input.destroy();

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

    private void createViewportConfig() {
        System.out.println("Initializing viewport config...");
        viewportConfig = new ViewportConfig(config.getGame());

        // Register for window resize events
        inputEventBus.addResizeListener(viewportConfig::setWindowSize);
    }
}