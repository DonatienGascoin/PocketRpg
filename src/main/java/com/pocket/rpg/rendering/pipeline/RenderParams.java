package com.pocket.rpg.rendering.pipeline;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.rendering.core.RenderCamera;
import com.pocket.rpg.rendering.core.RenderTarget;
import com.pocket.rpg.rendering.core.Renderable;
import lombok.Builder;
import lombok.Getter;
import org.joml.Vector4f;

import java.util.List;

/**
 * Immutable configuration for a render pass.
 * <p>
 * Controls what gets rendered in a single frame:
 * <ul>
 *   <li>Scene content (renderables + camera)</li>
 *   <li>Post-processing effects</li>
 *   <li>UI canvases</li>
 *   <li>Overlay (transitions)</li>
 * </ul>
 * <p>
 * <b>NOTE:</b> TransitionManager is NOT a parameter here.
 * It is set on {@link RenderPipeline} via {@code setTransitionManager()}.
 * This class only controls WHAT to render, not HOW.
 *
 * @see RenderPipeline#execute(RenderTarget, RenderParams)
 */
@Getter
@Builder
public class RenderParams {

    // ========================================================================
    // SCENE RENDERING
    // ========================================================================

    /**
     * Objects to render (sprites, tilemaps, entities).
     * Required when renderScene is true.
     */
    private final Iterable<Renderable> renderables;

    /**
     * Camera providing view/projection matrices.
     * Required when renderScene is true.
     */
    private final RenderCamera camera;

    // ========================================================================
    // UI RENDERING
    // ========================================================================

    /**
     * UI canvases to render.
     * Used when renderUI is true.
     */
    private final List<UICanvas> uiCanvases;

    // ========================================================================
    // VISUAL SETTINGS
    // ========================================================================

    /**
     * Background clear color.
     *
     */
    @Builder.Default
    private final Vector4f clearColor = new Vector4f(0.1f, 0.1f, 0.1f, 1.0f);

    // ========================================================================
    // PIPELINE STAGE FLAGS
    // ========================================================================

    /**
     * Whether to render scene content.
     */
    @Builder.Default
    private final boolean renderScene = true;

    /**
     * Whether to apply post-processing effects.
     * Only effective if PostProcessor is configured on RenderPipeline.
     */
    @Builder.Default
    private final boolean renderPostFx = false;

    /**
     * Whether to render UI canvases.
     */
    @Builder.Default
    private final boolean renderUI = false;

    /**
     * Whether to render overlay (transitions).
     * Only effective if TransitionManager is set on RenderPipeline.
     */
    @Builder.Default
    private final boolean renderOverlay = false;

    // ========================================================================
    // CONVENIENCE BUILDERS
    // ========================================================================

    /**
     * Creates params for scene-only rendering (no UI, post-fx, or overlay).
     * Used for editor preview.
     */
    public static RenderParamsBuilder sceneOnly(Iterable<Renderable> renderables, RenderCamera camera) {
        return builder()
                .renderables(renderables)
                .camera(camera)
                .renderScene(true)
                .renderUI(false)
                .renderPostFx(false)
                .renderOverlay(false);
    }

    /**
     * Creates params for full pipeline rendering.
     * Used for play mode / standalone game.
     */
    public static RenderParamsBuilder fullPipeline(Iterable<Renderable> renderables, 
                                                    RenderCamera camera,
                                                    List<UICanvas> uiCanvases) {
        return builder()
                .renderables(renderables)
                .camera(camera)
                .uiCanvases(uiCanvases)
                .renderScene(true)
                .renderUI(true)
                .renderPostFx(true)
                .renderOverlay(true);
    }
}
