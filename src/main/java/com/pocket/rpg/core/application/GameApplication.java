package com.pocket.rpg.core.application;

import com.pocket.rpg.audio.music.MusicManager;
import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.EngineConfiguration;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.input.DefaultInputContext;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.InputContext;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import com.pocket.rpg.platform.PlatformFactory;
import com.pocket.rpg.platform.glfw.GLFWPlatformFactory;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.postfx.PostEffectRegistry;
import com.pocket.rpg.rendering.postfx.PostProcessing;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.targets.ScreenTarget;
import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.AudioConfig;
import com.pocket.rpg.audio.DefaultAudioContext;
import com.pocket.rpg.audio.backend.OpenALAudioBackend;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.ErrorMode;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import com.pocket.rpg.serialization.ComponentRegistry;
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
 * Uses {@link GameLoop} for update coordination and {@link RenderPipeline}
 * for unified rendering.
 * <p>
 * Game Loop Order:
 * <ol>
 *   <li>Poll window events (keyboard, mouse, gamepad)</li>
 *   <li>Update UI input (sets mouse consumed flag)</li>
 *   <li>Update game logic via GameLoop (respects transition freeze)</li>
 *   <li>Render frame (pipeline handles everything)</li>
 *   <li>Swap buffers</li>
 *   <li>End frame (input + time)</li>
 * </ol>
 *
 * @see GameLoop
 * @see RenderPipeline
 */
public class GameApplication {

    // Platform
    private PlatformFactory platformFactory;
    private AbstractWindow window;
    private ViewportConfig viewportConfig;
    private InputEventBus inputEventBus;

    // Rendering
    private RenderPipeline pipeline;
    private ScreenTarget screenTarget;
    private PostProcessor postProcessor;

    // Game systems
    private GameLoop gameLoop;
    private TransitionManager transitionManager;
    private UIInputHandler uiInputHandler;

    // Utilities
    private PerformanceMonitor performanceMonitor;
    private EngineConfiguration config;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    private void init() {
        // 1. Asset system + serializer + audio (no GL needed)
        Assets.initialize();
        Assets.configure()
                .setAssetRoot("gameData/assets/")
                .setErrorMode(ErrorMode.USE_PLACEHOLDER)
                .apply();
        Serializer.init(Assets.getContext());
        initAudio();
        System.out.println(LogUtils.buildBox("Application starting"));

        // 2. PostEffectRegistry — class scanning, no GL
        PostEffectRegistry.initialize();

        // 3. Load GL-safe configs BEFORE window creation
        GameConfig gameConfig = ConfigLoader.loadSingleConfig(ConfigLoader.ConfigType.GAME);
        InputConfig inputConfig = ConfigLoader.loadSingleConfig(ConfigLoader.ConfigType.INPUT);

        inputEventBus = new InputEventBus();
        TimeContext timeContext = new DefaultTimeContext();
        Time.initialize(timeContext);

        // 4. Create window — establishes GL context
        platformFactory = selectPlatform();
        System.out.println("Using platform: " + platformFactory.getPlatformName());

        InputBackend inputBackend = platformFactory.createInputBackend();
        window = platformFactory.createWindow(gameConfig, inputBackend, inputEventBus);
        window.init();

        // 5. ComponentRegistry — class scanning
        ComponentRegistry.initialize();

        // 6. Load rendering config AFTER GL context (has sprite references)
        RenderingConfig renderingConfig = ConfigLoader.loadSingleConfig(ConfigLoader.ConfigType.RENDERING);

        // 7. Build composite config
        config = EngineConfiguration.from(gameConfig, inputConfig, renderingConfig);

        // 8. Create remaining systems
        createViewportConfig();
        createPostProcessor();
        setupInputSystem();
        createUIInputHandler();
        createRenderPipeline();
        createGameSystems();

        System.out.println("Application initialization complete");
    }

    private void initAudio() {
        OpenALAudioBackend backend = new OpenALAudioBackend();
        AudioConfig audioConfig = new AudioConfig();
        DefaultAudioContext audioContext = new DefaultAudioContext(backend, audioConfig);
        Audio.initialize(audioContext);
        System.out.println("Audio system initialized");
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

    private void createViewportConfig() {
        System.out.println("Initializing viewport config...");
        viewportConfig = new ViewportConfig(config.getGame());
        inputEventBus.addResizeListener(viewportConfig::setWindowSize);
    }

    private void createPostProcessor() {
        System.out.println("Initializing post-processor...");

        postProcessor = platformFactory.createPostProcessor(
                config.getRendering(), config.getGame().getGameWidth(), config.getGame().getGameHeight());
        postProcessor.init(window);
        PostProcessing.initialize(postProcessor);

        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(config.getRendering().isEnableStatistics());
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

    private void createRenderPipeline() {
        System.out.println("Creating render pipeline...");

        screenTarget = new ScreenTarget(viewportConfig);

        pipeline = new RenderPipeline(viewportConfig, config.getRendering());
        pipeline.setPostProcessor(postProcessor);
        pipeline.init();

        inputEventBus.addResizeListener((_, _) -> pipeline.resize());

        System.out.println("Render pipeline initialized");
    }

    /**
     * Creates game systems: SceneManager, TransitionManager, GameLoop, SaveManager, MusicManager.
     */
    private void createGameSystems() {
        System.out.println("Creating game systems...");

        // Create SceneManager
        SceneManager sceneManager = new SceneManager(viewportConfig, config.getRendering());

        // Create RuntimeSceneLoader and configure SceneManager
        RuntimeSceneLoader sceneLoader = new RuntimeSceneLoader();
        sceneManager.setSceneLoader(sceneLoader, "gameData/scenes/");

        // Create TransitionManager (transitions now in RenderingConfig)
        RenderingConfig renderingConfig = config.getRendering();
        transitionManager = new TransitionManager(
                sceneManager,
                pipeline.getOverlayRenderer(),
                renderingConfig.getDefaultTransitionConfig(),
                renderingConfig.getTransitions(),
                renderingConfig.getDefaultTransitionName()
        );
        pipeline.setTransitionManager(transitionManager);

        // Register resize listener for overlay
        inputEventBus.addResizeListener((w, h) -> {
            if (pipeline.getOverlayRenderer() != null) {
                pipeline.getOverlayRenderer().setScreenSize(w, h);
            }
        });

        // Initialize SceneTransition static API
        SceneTransition.forceInitialize(transitionManager);

        // Create GameLoop
        gameLoop = new GameLoop(sceneManager, transitionManager);

        // Initialize SaveManager and MusicManager
        SaveManager.initialize(sceneManager);
        MusicManager.initialize(sceneManager, Assets.getContext());

        // Load configurable start scene
        String startScene = config.getGame().getStartScene();
        if (startScene == null || startScene.isBlank()) {
            throw new IllegalStateException(
                    "No start scene configured in game.json (set 'startScene' field)");
        }
        sceneManager.loadScene(startScene);

        System.out.println("Game systems initialized");
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
     *   <li>Update game logic via GameLoop (scene frozen during transitions)</li>
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
        // 3. UPDATE GAME LOGIC (GameLoop handles freeze)
        // ============================================
        try {
            gameLoop.update(Time.deltaTime());
        } catch (Exception e) {
            System.err.println("ERROR in game update: " + e.getMessage());
            e.printStackTrace();
        }

        // ============================================
        // 4. RENDER FRAME (UNIFIED PIPELINE)
        // ============================================
        try {
            renderFrame();
        } catch (Exception e) {
            System.err.println("ERROR in rendering: " + e.getMessage());
            e.printStackTrace();
        }

        // ============================================
        // 5. SWAP BUFFERS AND END FRAME
        // ============================================
        window.swapBuffers();
        Input.endFrame();
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

        Scene currentScene = gameLoop.getSceneManager().getCurrentScene();
        if (currentScene != null && uiInputHandler != null) {
            uiInputHandler.update(currentScene.getUICanvases(), gameMouseX, gameMouseY);
        }
    }

    /**
     * Renders a complete frame using the unified pipeline.
     */
    private void renderFrame() {
        Scene scene = gameLoop.getSceneManager().getCurrentScene();
        if (scene == null) return;

        RenderParams params = RenderParams.builder()
                .renderables(scene.getRenderers())
                .camera(scene.getCamera())
                .uiCanvases(scene.getUICanvases())
                .clearColor(config.getRendering().getClearColor())
                .renderScene(true)
                .renderUI(true)
                .renderPostFx(pipeline.hasPostProcessingEffects())
                .renderOverlay(true)
                .build();

        pipeline.execute(screenTarget, params);
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    private void destroy() {
        System.out.println("Destroying application...");

        if (gameLoop != null) {
            gameLoop.destroy();
        }

        Input.destroy();
        Audio.destroy();

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
}
