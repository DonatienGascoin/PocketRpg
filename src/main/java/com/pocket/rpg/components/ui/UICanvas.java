package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import lombok.Setter;

/**
 * Marks a GameObject subtree as UI.
 * All UI components (UIImage, UIText, etc.) must have a UICanvas ancestor to render.
 * Owns a UITransform that reflects screen dimensions (managed automatically).
 */
@RequiredComponent(UITransform.class)
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

    /**
     * Updates the canvas-owned UITransform to reflect current screen dimensions.
     * Called each frame by UIRenderer before rendering the canvas subtree.
     */
    public void updateScreenSize(float width, float height) {
        UITransform transform = gameObject.getComponent(UITransform.class);
        if (transform == null) return;
        if (transform.getWidth() == width && transform.getHeight() == height) return;
        transform.setWidth(width);
        transform.setHeight(height);
        transform.setWidthMode(UITransform.SizeMode.FIXED);
        transform.setHeightMode(UITransform.SizeMode.FIXED);
        transform.getAnchor().set(0, 0);
        transform.getPivot().set(0, 0);
        transform.setOffset(0, 0);
        transform.markDirtyRecursive();
    }

    @Override
    public void render(UIRendererBackend backend) {
        // UICanvas doesn't render anything - it's just a marker
    }

    @Override
    public String toString() {
        return String.format("UICanvas[mode=%s, sortOrder=%d]", renderMode, sortOrder);
    }
}