package com.pocket.rpg.core.application;

import com.pocket.rpg.audio.AudioConfig;
import com.pocket.rpg.audio.AudioContext;
import com.pocket.rpg.audio.DefaultAudioContext;
import com.pocket.rpg.audio.backend.OpenALAudioBackend;
import com.pocket.rpg.audio.music.MusicManager;
import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.EngineConfiguration;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.editor.scene.RuntimeSceneLoader;
import com.pocket.rpg.input.DefaultInputContext;
import com.pocket.rpg.input.InputBackend;
import com.pocket.rpg.input.InputContext;
import com.pocket.rpg.input.events.InputEventBus;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import com.pocket.rpg.platform.PlatformFactory;
import com.pocket.rpg.platform.glfw.GLFWPlatformFactory;
import com.pocket.rpg.rendering.postfx.PostEffectRegistry;
import com.pocket.rpg.rendering.targets.ScreenTarget;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.ErrorMode;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import com.pocket.rpg.time.DefaultTimeContext;
import com.pocket.rpg.time.Time;
import com.pocket.rpg.time.TimeContext;
import com.pocket.rpg.utils.LogUtils;

/**
 * Main application class for the game.
 * <p>
 * Creates the window and contexts, then delegates subsystem lifecycle
 * to {@link GameEngine}. All three context singletons (Time, Audio, Input)
 * are passed to GameEngine which manages their lifecycle.
 * <p>
 * Game Loop Order:
 * <ol>
 *   <li>Poll window events (keyboard, mouse, gamepad)</li>
 *   <li>Update UI input (sets mouse consumed flag)</li>
 *   <li>Update game logic via GameEngine (respects transition freeze)</li>
 *   <li>Render frame (pipeline handles everything)</li>
 *   <li>Swap buffers</li>
 *   <li>End frame (input + time)</li>
 * </ol>
 *
 * @see GameEngine
 */
public class GameApplication {

    // Platform (owned by GameApplication — not managed by engine)
    private PlatformFactory platformFactory;
    private AbstractWindow window;
    private InputEventBus inputEventBus;
    private ScreenTarget screenTarget;

    // Configuration
    private EngineConfiguration config;

    // Engine (owns all game subsystems + context singletons)
    private GameEngine engine;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    private void init() {
        // 1. Asset system + serializer (no GL needed)
        Assets.initialize();
        Assets.configure()
                .setAssetRoot("gameData/assets/")
                .setErrorMode(ErrorMode.USE_PLACEHOLDER)
                .apply();
        Serializer.init(Assets.getContext());
        System.out.println(LogUtils.buildBox("Application starting"));

        // 2. PostEffectRegistry — class scanning, no GL
        PostEffectRegistry.initialize();

        // 3. Load GL-safe configs BEFORE window creation
        GameConfig gameConfig = ConfigLoader.loadSingleConfig(ConfigLoader.ConfigType.GAME);
        InputConfig inputConfig = ConfigLoader.loadSingleConfig(ConfigLoader.ConfigType.INPUT);

        inputEventBus = new InputEventBus();

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

        // 8. Create contexts
        TimeContext timeContext = new DefaultTimeContext();
        AudioContext audioContext = createAudioContext();
        InputContext inputContext = createInputContext();

        // 9. Create engine (initializes context singletons + subsystems)
        engine = GameEngine.builder()
                .gameConfig(gameConfig)
                .renderingConfig(renderingConfig)
                .window(window)
                .platformFactory(platformFactory)
                .timeContext(timeContext)
                .audioContext(audioContext)
                .inputContext(inputContext)
                .build();
        engine.init();

        // 10. Wire resize listeners to engine subsystems
        inputEventBus.addResizeListener(engine.getViewportConfig()::setWindowSize);
        inputEventBus.addResizeListener((_, _) -> engine.getPipeline().resize());
        inputEventBus.addResizeListener((w, h) -> {
            var overlay = engine.getPipeline().getOverlayRenderer();
            if (overlay != null) overlay.setScreenSize(w, h);
        });

        // 11. Scene loading (game-specific)
        screenTarget = new ScreenTarget(engine.getViewportConfig());
        RuntimeSceneLoader sceneLoader = new RuntimeSceneLoader();
        engine.getSceneManager().setSceneLoader(sceneLoader, "gameData/scenes/");
        SaveManager.initialize(engine.getSceneManager());
        MusicManager.initialize(engine.getSceneManager(), Assets.getContext());

        // Load configurable start scene
        String startScene = gameConfig.getStartScene();
        if (startScene == null || startScene.isBlank()) {
            throw new IllegalStateException(
                    "No start scene configured in game.json (set 'startScene' field)");
        }
        engine.getSceneManager().loadScene(startScene);

        System.out.println("Application initialization complete");
    }

    private AudioContext createAudioContext() {
        OpenALAudioBackend backend = new OpenALAudioBackend();
        AudioConfig audioConfig = new AudioConfig();
        return new DefaultAudioContext(backend, audioConfig);
    }

    private InputContext createInputContext() {
        KeyListener keyListener = new KeyListener();
        MouseListener mouseListener = new MouseListener();
        GamepadListener gamepadListener = new GamepadListener();

        inputEventBus.addKeyListener(keyListener);
        inputEventBus.addMouseListener(mouseListener);
        inputEventBus.addGamepadListener(gamepadListener);

        return new DefaultInputContext(config.getInput(), keyListener, mouseListener, gamepadListener);
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
     */
    private void processFrame() {
        if (handleMinimizedWindow()) {
            return;
        }

        // 1. Poll events
        window.pollEvents();

        // 2. Update UI input (before game logic)
        updateUIInput();

        // 3. Update game logic
        try {
            engine.update();
        } catch (Exception e) {
            System.err.println("ERROR in game update: " + e.getMessage());
            e.printStackTrace();
        }

        // 4. Render frame
        try {
            engine.render(screenTarget);
        } catch (Exception e) {
            System.err.println("ERROR in rendering: " + e.getMessage());
            e.printStackTrace();
        }

        // 5. Swap buffers and end frame
        window.swapBuffers();
        engine.endFrame();
    }

    private void updateUIInput() {
        var screenMousePos = com.pocket.rpg.input.Input.getMousePosition();
        float gameMouseX = engine.getViewportConfig().windowToGameX(screenMousePos.x);
        float gameMouseY = engine.getViewportConfig().windowToGameY(screenMousePos.y);
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
