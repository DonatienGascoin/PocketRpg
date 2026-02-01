package com.pocket.rpg.core.application;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.AudioContext;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputContext;
import com.pocket.rpg.platform.PlatformFactory;
import com.pocket.rpg.rendering.pipeline.RenderParams;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import com.pocket.rpg.rendering.postfx.PostProcessing;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.core.RenderTarget;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import com.pocket.rpg.time.Time;
import com.pocket.rpg.time.TimeContext;
import com.pocket.rpg.ui.UIInputHandler;
import com.pocket.rpg.utils.PerformanceMonitor;
import lombok.Builder;
import lombok.Getter;

/**
 * Shared game engine that owns all subsystem initialization and lifecycle.
 * <p>
 * Creates and wires: ViewportConfig, PostProcessor, RenderPipeline,
 * SceneManager, TransitionManager, GameLoop, UIInputHandler, PerformanceMonitor.
 * <p>
 * All three context singletons (Time, Audio, Input) are required. GameEngine
 * sets each singleton via {@code setContext()} in {@link #init()} and clears
 * them in {@link #destroy()}, calling context lifecycle methods directly.
 * <p>
 * GameEngine does NOT manage:
 * <ul>
 *   <li>The window or the main loop</li>
 *   <li>SaveManager or MusicManager</li>
 *   <li>Resize listeners — callers wire those to their own event systems</li>
 * </ul>
 *
 * @see GameApplication
 */
@Builder
public class GameEngine {

    // === Required (passed by caller) ===
    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;
    private final AbstractWindow window;
    private final PlatformFactory platformFactory;

    // === Required contexts ===
    // GameEngine always sets the singleton and manages lifecycle.
    private final TimeContext timeContext;
    private final AudioContext audioContext;
    private final InputContext inputContext;

    // === Created internally ===
    @Getter private ViewportConfig viewportConfig;
    @Getter private PostProcessor postProcessor;
    @Getter private RenderPipeline pipeline;
    @Getter private SceneManager sceneManager;
    @Getter private TransitionManager transitionManager;
    @Getter private GameLoop gameLoop;
    @Getter private UIInputHandler uiInputHandler;
    @Getter private PerformanceMonitor performanceMonitor;

    /**
     * Creates all subsystems and initializes context singletons.
     */
    public void init() {
        // Initialize context singletons via setContext + direct lifecycle calls
        Time.setContext(timeContext);
        timeContext.init();

        Audio.setContext(audioContext);
        audioContext.initialize();

        Input.setContext(inputContext);

        // 1. Viewport config
        viewportConfig = new ViewportConfig(gameConfig);

        // 2. Post-processor
        postProcessor = platformFactory.createPostProcessor(
                renderingConfig, gameConfig.getGameWidth(), gameConfig.getGameHeight());
        postProcessor.init(window);
        PostProcessing.initialize(postProcessor);

        // 3. Performance monitor
        performanceMonitor = new PerformanceMonitor();
        performanceMonitor.setEnabled(renderingConfig.isEnableStatistics());

        // 4. UI input handler
        uiInputHandler = new UIInputHandler(gameConfig);

        // 5. Render pipeline
        pipeline = new RenderPipeline(viewportConfig, renderingConfig);
        pipeline.setPostProcessor(postProcessor);
        pipeline.init();

        // 6. Scene manager
        sceneManager = new SceneManager(viewportConfig, renderingConfig);

        // 7. Transition manager
        transitionManager = new TransitionManager(
                sceneManager,
                pipeline.getOverlayRenderer(),
                renderingConfig.getDefaultTransitionConfig(),
                renderingConfig.getTransitions(),
                renderingConfig.getDefaultTransitionName()
        );
        pipeline.setTransitionManager(transitionManager);
        SceneTransition.forceInitialize(transitionManager);

        // 8. Game loop
        gameLoop = new GameLoop(sceneManager, transitionManager);
    }

    /**
     * Updates game logic. Reads {@code Time.deltaTime()} internally.
     */
    public void update() {
        gameLoop.update(Time.deltaTime());
    }

    /**
     * Renders the current scene to the given target.
     */
    public void render(RenderTarget target) {
        Scene scene = sceneManager.getCurrentScene();
        if (scene == null) return;

        RenderParams params = RenderParams.builder()
                .renderables(scene.getRenderers())
                .camera(scene.getCamera())
                .uiCanvases(scene.getUICanvases())
                .clearColor(renderingConfig.getClearColor())
                .renderScene(true)
                .renderUI(true)
                .renderPostFx(pipeline.hasPostProcessingEffects())
                .renderOverlay(true)
                .build();

        pipeline.execute(target, params);
    }

    /**
     * Updates UI input handler with the current scene's canvases.
     *
     * @param gameMouseX mouse X in game coordinates
     * @param gameMouseY mouse Y in game coordinates
     */
    public void updateUIInput(float gameMouseX, float gameMouseY) {
        Scene currentScene = sceneManager.getCurrentScene();
        if (currentScene != null && uiInputHandler != null) {
            uiInputHandler.update(currentScene.getUICanvases(), gameMouseX, gameMouseY);
        }
    }

    /**
     * Ends the frame: updates input, time, and performance monitor.
     */
    public void endFrame() {
        Input.endFrame();
        Time.update();
        performanceMonitor.update();
    }

    /**
     * Destroys all owned subsystems and context singletons.
     */
    public void destroy() {
        if (gameLoop != null) {
            gameLoop.destroy();
        }

        inputContext.destroy();
        Input.setContext(null);

        audioContext.destroy();
        Audio.setContext(null);

        if (pipeline != null) {
            pipeline.destroy();
        }

        if (postProcessor != null) {
            postProcessor.destroy();
        }
    }
}
