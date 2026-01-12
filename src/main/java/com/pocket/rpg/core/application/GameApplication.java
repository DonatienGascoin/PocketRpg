package com.pocket.rpg.core.application;

import com.pocket.rpg.config.EngineConfiguration;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.platform.PlatformFactory;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.platform.glfw.GLFWPlatformFactory;
import com.pocket.rpg.input.DefaultInputContext;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.InputContext;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import com.pocket.rpg.rendering.postfx.PostProcessing;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.targets.ScreenTarget;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.ErrorMode;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.time.DefaultTimeContext;
import com.pocket.rpg.time.Time;
import com.pocket.rpg.time.TimeContext;
import com.pocket.rpg.ui.UIInputHandler;
import com.pocket.rpg.utils.LogUtils;
import com.pocket.rpg.utils.PerformanceMonitor;

/**
 * Main application class for the game.
 * <p>
 * <b>UNIFIED RENDERING NOTE:</b>
 * This class now uses {@link RenderPipeline} as the single entry point for all rendering.
 * The manual orchestration of scene → post-fx → UI → overlay is now handled internally
 * by the pipeline via {@link GameEngine#renderFrame()}.
 * <p>
 * Game Loop Order:
 * <ol>
 *   <li>Poll window events (keyboard, mouse, gamepad)</li>
 *   <li>Update UI input (sets mouse consumed flag)</li>
 *   <li>Update game logic (respects mouse consumption)</li>
 *   <li>Render frame (pipeline handles everything)</li>
 *   <li>Swap buffers</li>
 * </ol>
 *
 * @see RenderPipeline
 * @see GameEngine#renderFrame()
 */
public class GameApplication {

    private PlatformFactory platformFactory;

    private AbstractWindow window;
    private GameEngine engine;
    private ViewportConfig viewportConfig;

    // Unified rendering pipeline (owns all renderers)
    private RenderPipeline pipeline;
    private ScreenTarget screenTarget;

    // Post-processor (set on pipeline)
    private PostProcessor postProcessor;

    private InputEventBus inputEventBus;
    private PerformanceMonitor performanceMonitor;

    // UI Input Handler - processes mouse input for UI elements
    private UIInputHandler uiInputHandler;

    private EngineConfiguration config;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    private void init() {
        Assets.initialize();
        Assets.configure()
                .setAssetRoot("gameData/assets/")
                .setErrorMode(ErrorMode.USE_PLACEHOLDER)
                .apply();
        Serializer.init(Assets.getContext());
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
        createRenderPipeline();
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

        // Create post-processor (will be set on pipeline)
        postProcessor = platformFactory.createPostProcessor(config.getGame());
        postProcessor.init(window);
        PostProcessing.initialize(postProcessor);

        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(config.getRendering().isEnableStatistics());
    }

    /**
     * Creates the unified render pipeline.
     * Replaces separate renderer + uiRenderer + manual post-fx orchestration.
     */
    private void createRenderPipeline() {
        System.out.println("Creating render pipeline...");

        // Create screen target for rendering to default framebuffer
        screenTarget = new ScreenTarget(viewportConfig);

        // Create pipeline
        pipeline = new RenderPipeline(viewportConfig, config.getRendering());

        // Set post-processor (must be before init)
        pipeline.setPostProcessor(postProcessor);

        // Initialize pipeline (creates SceneRenderer, UIRenderer, OverlayRenderer)
        pipeline.init();

        // Register resize listener
        inputEventBus.addResizeListener((_, _) -> pipeline.resize());

        System.out.println("Render pipeline initialized");
    }

    private void createGameEngine() {
        System.out.println("Creating game engine...");

        engine = GameEngine.builder()
                .config(config)
                .window(window)
                .viewportConfig(viewportConfig)
                .inputEventBus(inputEventBus)
                .pipeline(pipeline)
                .screenTarget(screenTarget)
                .build();

        // Set the UI input handler
        engine.setUIInputHandler(uiInputHandler);

        engine.initialize();
    }

    // ========================================================================
    // MAIN LOOP
    // ========================================================================

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
     * <ol>
     *   <li>Handle minimized window (skip rendering)</li>
     *   <li>Poll events (populates Input state)</li>
     *   <li>Update UI input FIRST (sets mouse consumed flag)</li>
     *   <li>Update game logic (Input methods respect consumption)</li>
     *   <li>Render frame (pipeline handles scene → post-fx → UI → overlay)</li>
     *   <li>Swap buffers and end frame</li>
     * </ol>
     */
    private void processFrame() {
        if (handleMinimizedWindow()) {
            return;
        }

        // ============================================
        // 1. POLL EVENTS
        // ============================================
        window.pollEvents();

        // ============================================
        // 2. UPDATE UI INPUT (BEFORE GAME LOGIC)
        // ============================================
        updateUIInput();

        // ============================================
        // 3. UPDATE GAME LOGIC
        // ============================================
        try {
            engine.update(Time.deltaTime());
        } catch (Exception e) {
            System.err.println("ERROR in game update: " + e.getMessage());
            e.printStackTrace();
        }

        // ============================================
        // 4. RENDER FRAME (UNIFIED PIPELINE)
        // ============================================
        // Pipeline handles: Scene → Post-FX → UI → Overlay
        try {
            engine.renderFrame();
        } catch (Exception e) {
            System.err.println("ERROR in rendering: " + e.getMessage());
            e.printStackTrace();
        }

        // ============================================
        // 5. SWAP BUFFERS AND END FRAME
        // ============================================
        window.swapBuffers();

        // Clear per-frame input state
        Input.endFrame();

        // Update time for next frame
        Time.update();
        performanceMonitor.update();
    }

    /**
     * Updates UI input handling.
     */
    private void updateUIInput() {
        var screenMousePos = Input.getMousePosition();
        float gameMouseX = viewportConfig.windowToGameX(screenMousePos.x);
        float gameMouseY = viewportConfig.windowToGameY(screenMousePos.y);
        engine.updateUIInput(gameMouseX, gameMouseY);
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    private void destroy() {
        System.out.println("Destroying application...");

        if (engine != null) {
            engine.destroy();
        }

        Input.destroy();

        if (pipeline != null) {
            pipeline.destroy();
        }

        if (postProcessor != null) {
            postProcessor.destroy();
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
        inputEventBus.addResizeListener(viewportConfig::setWindowSize);
    }
}
