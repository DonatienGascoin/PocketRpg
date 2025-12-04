package com.pocket.rpg.ui;

import lombok.Getter;
import lombok.Setter;

/**
 * Marks a GameObject subtree as UI.
 * All UI components (UIImage, UIText, etc.) must have a UICanvas ancestor to render.
 */
public class UICanvas extends UIComponent {

    public enum RenderMode {
        SCREEN_SPACE_OVERLAY,  // Renders on top of everything, ignores camera
        SCREEN_SPACE_CAMERA,   // Renders at a plane distance from camera
        WORLD_SPACE            // Renders in world space (e.g., health bars above enemies)
    }

    @Getter @Setter
    private RenderMode renderMode = RenderMode.SCREEN_SPACE_OVERLAY;

    @Getter
    private int sortOrder = 0;  // Higher = renders on top

    @Getter @Setter
    private float planeDistance = 100f;  // For SCREEN_SPACE_CAMERA mode

    public UICanvas() {
    }

    public UICanvas(RenderMode renderMode) {
        this.renderMode = renderMode;
    }

    public UICanvas(RenderMode renderMode, int sortOrder) {
        this.renderMode = renderMode;
        this.sortOrder = sortOrder;
    }

    /**
     * Sets sort order and triggers re-sort in the scene's canvas list.
     */
    public void setSortOrder(int sortOrder) {
        if (this.sortOrder == sortOrder) return;
        this.sortOrder = sortOrder;

        // Notify scene to re-sort canvases
        if (gameObject != null && gameObject.getScene() != null) {
            gameObject.getScene().markCanvasSortDirty();
        }
    }

    @Override
    public void render(UIRendererBackend backend) {
        // UICanvas doesn't render anything - it's just a marker
    }

    @Override
    public float getWidth() {
        return 0;
    }

    @Override
    public float getHeight() {
        return 0;
    }

    @Override
    public String toString() {
        return String.format("UICanvas[mode=%s, sortOrder=%d]", renderMode, sortOrder);
    }
}