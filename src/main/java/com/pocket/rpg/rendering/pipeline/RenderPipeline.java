package com.pocket.rpg.rendering.pipeline;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.rendering.overlay.OverlayRenderer;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.core.RenderTarget;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import com.pocket.rpg.rendering.ui.UIRenderer;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector4f;

import java.util.List;

/**
 * Orchestrates the rendering pipeline stages: Scene → PostFx → UI → Overlay.
 * <p>
 * This class owns and manages all sub-renderers:
 * <ul>
 *   <li>{@link SceneRenderer} - Sprites, tilemaps, entities</li>
 *   <li>{@link UIRenderer} - Screen-space UI canvases</li>
 *   <li>{@link PostProcessor} - Post-processing effects (wrapped)</li>
 *   <li>{@link com.pocket.rpg.rendering.core.OverlayRenderer} - Transitions, fades (wrapped)</li>
 * </ul>
 * <p>
 * The pipeline can be executed in two ways:
 * <ul>
 *   <li>All-in-one: {@link #execute(RenderTarget, RenderParams)} - for editor panels</li>
 *   <li>Granular: individual stage methods - for game loop integration</li>
 * </ul>
 */
public class RenderPipeline {

    private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    // Sub-renderers (owned)
    @Getter private final SceneRenderer sceneRenderer;
    private final UIRenderer uiRenderer;

    @Setter
    @Getter
    private PostProcessor postProcessor;

    @Setter
    @Getter
    private com.pocket.rpg.rendering.core.OverlayRenderer overlayRenderer;

    @Setter
    @Getter
    private TransitionManager transitionManager;

    // State for granular API
    private RenderTarget currentTarget;
    private boolean postFxCaptureActive;

    /**
     * -- GETTER --
     *
     * @return true if init() has been called
     */
    @Getter private boolean initialized;

    public RenderPipeline(ViewportConfig viewportConfig, RenderingConfig renderingConfig) {
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;

        this.sceneRenderer = new SceneRenderer(renderingConfig);
        this.uiRenderer = new UIRenderer(viewportConfig);
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initializes all sub-renderers.
     * Must be called before any render methods.
     * <p>
     * Note: PostProcessor must be set externally via {@link #setPostProcessor}
     * before calling init(), as it requires AbstractWindow for initialization.
     */
    public void init() {
        if (initialized) return;

        int gameWidth = viewportConfig.getGameWidth();
        int gameHeight = viewportConfig.getGameHeight();

        sceneRenderer.init(gameWidth, gameHeight);
        uiRenderer.init();

        // PostProcessor must be set externally (requires window for init)
        if (postProcessor == null) {
            System.err.println("[RenderPipeline] WARNING: PostProcessor not set. Call setPostProcessor() before init().");
        }

        // Create overlay renderer with window dimensions
        if (overlayRenderer == null) {
            OverlayRenderer glOverlay = new OverlayRenderer();
            glOverlay.init();
            glOverlay.setScreenSize(viewportConfig.getWindowWidth(), viewportConfig.getWindowHeight());
            overlayRenderer = glOverlay;
        }

        initialized = true;
        System.out.println("[RenderPipeline] Initialized (game=" + gameWidth + "x" + gameHeight +
                ", window=" + viewportConfig.getWindowWidth() + "x" + viewportConfig.getWindowHeight() + ")");
    }

    // ========================================================================
    // ALL-IN-ONE EXECUTION (for editor panels)
    // ========================================================================

    /**
     * Executes a complete frame with configurable stages.
     * <p>
     * Pipeline order:
     * <ol>
     *   <li>Clear target</li>
     *   <li>Scene (with optional post-fx capture)</li>
     *   <li>Post-processing (if enabled and has effects)</li>
     *   <li>UI (after post-fx, never affected by effects)</li>
     *   <li>Overlay (transitions)</li>
     * </ol>
     *
     * @param target Render target (screen or framebuffer)
     * @param params Rendering configuration
     */
    public void execute(RenderTarget target, RenderParams params) {
        if (!initialized) {
            System.err.println("[RenderPipeline] Not initialized - call init() first");
            return;
        }

        // Stage 0: Bind and clear target
        target.bind();
        target.clear(params.getClearColor());

        // Stage 1: Scene
        if (params.isRenderScene() && params.getRenderables() != null && params.getCamera() != null) {
            boolean usePostFx = params.isRenderPostFx() && hasPostProcessingEffects();

            if (usePostFx) {
                // Render scene to post-processor's capture FBO
                postProcessor.beginCapture();
                sceneRenderer.render(params.getRenderables(), params.getCamera());
                // Apply effects and blit to screen (PostProcessor handles target internally)
                postProcessor.endCaptureAndApplyEffects();
            } else {
                // Render scene directly to target
                sceneRenderer.render(params.getRenderables(), params.getCamera());
            }
        }

        // Stage 2: UI (after post-fx, never blurred/bloomed)
        if (params.isRenderUI() && params.getUiCanvases() != null && !params.getUiCanvases().isEmpty()) {
            uiRenderer.render(params.getUiCanvases());
        }

        // Stage 3: Overlay (transitions)
        if (params.isRenderOverlay() && transitionManager != null) {
            renderTransition();
        }

        // Unbind target
        target.unbind();
    }

    // ========================================================================
    // GRANULAR EXECUTION (for game loop integration)
    // ========================================================================

    /**
     * Begins a frame. Binds and clears the target.
     *
     * @param target Render target for this frame
     */
    public void beginFrame(RenderTarget target) {
        beginFrame(target, renderingConfig.getClearColor());
    }

    /**
     * Begins a frame with custom clear color.
     *
     * @param target     Render target for this frame
     * @param clearColor Clear color
     */
    public void beginFrame(RenderTarget target, Vector4f clearColor) {
        if (!initialized) {
            System.err.println("[RenderPipeline] Not initialized - call init() first");
            return;
        }

        currentTarget = target;
        postFxCaptureActive = false;

        target.bind();
        target.clear(clearColor);
    }

    /**
     * Begins scene capture for post-processing.
     * If post-fx is enabled and has effects, redirects rendering to capture FBO.
     *
     * @param postFxEnabled Whether post-processing should be applied
     */
    public void beginSceneCapture(boolean postFxEnabled) {
        postFxCaptureActive = postFxEnabled && hasPostProcessingEffects();

        if (postFxCaptureActive) {
            postProcessor.beginCapture();
        }
    }

    /**
     * Renders scene content (sprites, tilemaps, entities).
     *
     * @param renderables Objects to render
     * @param camera      Camera for view/projection
     */
    public void renderScene(Iterable<Renderable> renderables, RenderCamera camera) {
        if (renderables != null && camera != null) {
            sceneRenderer.render(renderables, camera);
        }
    }

    /**
     * Ends scene capture and applies post-processing effects.
     * Blits result to screen.
     */
    public void endSceneCaptureAndApplyPostFx() {
        if (postFxCaptureActive) {
            postProcessor.endCaptureAndApplyEffects();
            postFxCaptureActive = false;
        }
    }

    /**
     * Renders UI canvases (after post-processing).
     *
     * @param canvases UI canvases to render
     */
    public void renderUI(List<UICanvas> canvases) {
        if (canvases != null && !canvases.isEmpty()) {
            uiRenderer.render(canvases);
        }
    }

    /**
     * Renders transition overlay.
     *
     */
    public void renderOverlay() {
        renderTransition();
    }

    /**
     * Ends the frame. Unbinds the target.
     */
    public void endFrame() {
        if (currentTarget != null) {
            currentTarget.unbind();
            currentTarget = null;
        }
    }

    /**
     * Updates UI input state.
     * Should be called before game update to consume mouse input over UI.
     */
    public void updateUIInput() {
        uiRenderer.processInput();
    }

    // ========================================================================
    // TRANSITION RENDERING
    // ========================================================================

    /**
     * Renders the current transition effect using the overlay renderer.
     */
    private void renderTransition() {
        if (transitionManager == null) return;
        if (!transitionManager.isTransitioning()) return;

        // TransitionManager has its own OverlayRenderer
        transitionManager.render();
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * @return true if post-processor is available and has effects enabled
     */
    public boolean hasPostProcessingEffects() {
        if (postProcessor == null) return false;
        if (!postProcessor.isEnabled()) return false;
        return !postProcessor.getEffects().isEmpty();
    }

    /**
     * @return The UI renderer instance
     */
    public UIRenderer getUIRenderer() {
        return uiRenderer;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Updates renderers after window resize.
     * Reads current window size from ViewportConfig.
     * <p>
     * Note: Call this after ViewportConfig.setWindowSize() has been updated.
     */
    public void resize() {
        int windowWidth = viewportConfig.getWindowWidth();
        int windowHeight = viewportConfig.getWindowHeight();

        if (windowWidth <= 0 || windowHeight <= 0) return;

        // Scene uses game resolution (fixed), but update projection anyway
        sceneRenderer.resize(viewportConfig.getGameWidth(), viewportConfig.getGameHeight());

        // Overlay uses window dimensions
        if (overlayRenderer != null) {
            overlayRenderer.setScreenSize(windowWidth, windowHeight);
        }
    }

    /**
     * Releases all resources.
     */
    public void destroy() {
        sceneRenderer.destroy();
        uiRenderer.destroy();

        // Don't destroy postProcessor - it's managed externally
        postProcessor = null;

        if (overlayRenderer != null) {
            overlayRenderer.destroy();
            overlayRenderer = null;
        }

        currentTarget = null;
        initialized = false;

        System.out.println("[RenderPipeline] Destroyed");
    }

}