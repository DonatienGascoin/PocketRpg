package com.pocket.rpg.rendering.pipeline;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.rendering.targets.FramebufferTarget;
import com.pocket.rpg.rendering.targets.ScreenTarget;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.core.RenderTarget;
import com.pocket.rpg.rendering.core.Renderable;
import com.pocket.rpg.rendering.postfx.PostProcessor;
import lombok.Getter;
import org.joml.Vector4f;

import java.util.List;

/**
 * Unified entry point for all rendering operations.
 * <p>
 * This facade provides two APIs:
 * <ul>
 *   <li><b>All-in-one</b>: {@link #renderToScreen}, {@link #renderToFramebuffer} - for editor panels</li>
 *   <li><b>Granular</b>: {@link #beginFrame}, {@link #renderScene}, etc. - for game loop integration</li>
 * </ul>
 * <p>
 * Usage (all-in-one):
 * <pre>
 * renderer.renderToFramebuffer(fbo, RenderParams.builder()
 *     .renderables(renderables)
 *     .camera(camera)
 *     .renderScene(true)
 *     .renderUI(true)
 *     .renderPostFx(postFxToggle)
 *     .build());
 * </pre>
 * <p>
 * Usage (granular - game loop):
 * <pre>
 * renderer.beginFrame();
 * renderer.beginSceneCapture(postFxEnabled);
 * renderer.renderScene(renderables, camera);
 * renderer.endSceneCaptureAndApplyPostFx();
 * renderer.renderUI(canvases);
 * renderer.renderOverlay(transitionManager);
 * renderer.endFrame();
 * </pre>
 */
public class UnifiedRenderer {

    @Getter private final ViewportConfig viewportConfig;
    private final RenderingConfig renderingConfig;

    // Internal pipeline
    @Getter private final RenderPipeline pipeline;

    // Targets
    @Getter private final ScreenTarget screenTarget;

    @Getter private boolean initialized;

    public UnifiedRenderer(ViewportConfig viewportConfig, RenderingConfig renderingConfig) {
        this.viewportConfig = viewportConfig;
        this.renderingConfig = renderingConfig;

        this.pipeline = new RenderPipeline(viewportConfig, renderingConfig);
        this.screenTarget = new ScreenTarget(viewportConfig);
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initializes the renderer. Must be called before any render methods.
     */
    public void init() {
        if (initialized) return;

        pipeline.init();
        initialized = true;

        System.out.println("[UnifiedRenderer] Initialized");
    }

    /**
     * Initializes with an existing PostProcessor (for migration).
     *
     * @param postProcessor Existing post-processor to reuse
     */
    public void init(PostProcessor postProcessor) {
        if (initialized) return;

        pipeline.setPostProcessor(postProcessor);
        pipeline.init();
        initialized = true;

        System.out.println("[UnifiedRenderer] Initialized (with existing PostProcessor)");
    }

    // ========================================================================
    // ALL-IN-ONE API (for editor panels)
    // ========================================================================

    /**
     * Renders to the screen with the given parameters.
     *
     * @param params Rendering configuration
     */
    public void renderToScreen(RenderParams params) {
        if (!initialized) {
            System.err.println("[UnifiedRenderer] Not initialized");
            return;
        }
        pipeline.execute(screenTarget, params);
    }

    /**
     * Renders to an EditorFramebuffer with the given parameters.
     *
     * @param framebuffer Target framebuffer
     * @param params      Rendering configuration
     */
    public void renderToFramebuffer(EditorFramebuffer framebuffer, RenderParams params) {
        if (!initialized) {
            System.err.println("[UnifiedRenderer] Not initialized");
            return;
        }
        if (framebuffer == null) {
            System.err.println("[UnifiedRenderer] Framebuffer is null");
            return;
        }

        FramebufferTarget target = new FramebufferTarget(framebuffer);
        pipeline.execute(target, params);
    }

    /**
     * Renders to a RenderTarget with the given parameters.
     *
     * @param target Target to render to
     * @param params Rendering configuration
     */
    public void renderTo(RenderTarget target, RenderParams params) {
        if (!initialized) {
            System.err.println("[UnifiedRenderer] Not initialized");
            return;
        }
        if (target == null) {
            System.err.println("[UnifiedRenderer] Target is null");
            return;
        }
        pipeline.execute(target, params);
    }

    // ========================================================================
    // GRANULAR API (for game loop integration)
    // ========================================================================

    /**
     * Begins a frame. Renders to screen by default.
     */
    public void beginFrame() {
        beginFrame(screenTarget, renderingConfig.getClearColor());
    }

    /**
     * Begins a frame with custom clear color. Renders to screen.
     *
     * @param clearColor Clear color
     */
    public void beginFrame(Vector4f clearColor) {
        beginFrame(screenTarget, clearColor);
    }

    /**
     * Begins a frame to a specific target.
     *
     * @param target     Render target
     * @param clearColor Clear color
     */
    public void beginFrame(RenderTarget target, Vector4f clearColor) {
        if (!initialized) {
            System.err.println("[UnifiedRenderer] Not initialized");
            return;
        }
        pipeline.beginFrame(target, clearColor);
    }

    /**
     * Begins scene capture for post-processing.
     *
     * @param postFxEnabled Whether post-processing should be applied
     */
    public void beginSceneCapture(boolean postFxEnabled) {
        pipeline.beginSceneCapture(postFxEnabled);
    }

    /**
     * Renders scene content.
     *
     * @param renderables Objects to render
     * @param camera      Camera for view/projection
     */
    public void renderScene(Iterable<Renderable> renderables, RenderCamera camera) {
        pipeline.renderScene(renderables, camera);
    }

    /**
     * Ends scene capture and applies post-processing.
     */
    public void endSceneCaptureAndApplyPostFx() {
        pipeline.endSceneCaptureAndApplyPostFx();
    }

    /**
     * Renders UI canvases.
     *
     * @param canvases UI canvases to render
     */
    public void renderUI(List<UICanvas> canvases) {
        pipeline.renderUI(canvases);
    }

    /**
     * Renders transition overlay.
     *
     */
    public void renderOverlay() {
        pipeline.renderOverlay();
    }

    /**
     * Ends the frame.
     */
    public void endFrame() {
        pipeline.endFrame();
    }

    /**
     * Updates UI input state.
     * Call before game update to consume mouse input over UI.
     */
    public void updateUIInput() {
        pipeline.updateUIInput();
    }

    // ========================================================================
    // ACCESSORS
    // ========================================================================

    /**
     * @return The post-processor for configuration
     */
    public PostProcessor getPostProcessor() {
        return pipeline.getPostProcessor();
    }

    /**
     * @return true if any post-processing effects are active
     */
    public boolean hasPostProcessingEffects() {
        return pipeline.hasPostProcessingEffects();
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
        pipeline.resize();
    }

    /**
     * Releases all resources.
     */
    public void destroy() {
        pipeline.destroy();
        initialized = false;
        System.out.println("[UnifiedRenderer] Destroyed");
    }
}