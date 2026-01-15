package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.config.RenderingConfig;
import imgui.ImDrawList;
import lombok.Setter;

/**
 * Handles auxiliary rendering for the UI Designer panel.
 * <p>
 * UI element rendering is now handled by RenderPipeline via UIRenderer
 * for consistency with GameViewPanel and runtime. This class only handles:
 * <ul>
 *   <li>World background texture display (when background mode is WORLD)</li>
 * </ul>
 * <p>
 * Overlays (selection, handles, anchors) are handled by UIDesignerGizmoDrawer.
 */
public class UIDesignerRenderer {

    private final UIDesignerState state;
    private final UIDesignerCoordinates coords;

    @Setter
    private int sceneTextureId = 0;

    public UIDesignerRenderer(UIDesignerState state, UIDesignerCoordinates coords, RenderingConfig renderingConfig) {
        this.state = state;
        this.coords = coords;
        // renderingConfig no longer needed - UI rendering moved to RenderPipeline
    }

    // ========================================================================
    // WORLD BACKGROUND
    // ========================================================================

    /**
     * Displays the world background texture (if enabled).
     * This shows the scene texture behind UI elements when background mode is WORLD.
     */
    public void drawWorldBackground(ImDrawList drawList) {
        if (state.getBackgroundMode() != UIDesignerState.BackgroundMode.WORLD) return;
        if (sceneTextureId == 0) return;

        float[] canvasBounds = coords.getCanvasScreenBounds();
        float left = state.getViewportX() + canvasBounds[0];
        float top = state.getViewportY() + canvasBounds[1];
        float right = state.getViewportX() + canvasBounds[2];
        float bottom = state.getViewportY() + canvasBounds[3];

        // Draw with flipped V coordinates (OpenGL texture origin is bottom-left)
        drawList.addImage(sceneTextureId, left, top, right, bottom, 0, 1, 1, 0);
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    public void destroy() {
        sceneTextureId = 0;
    }
}
