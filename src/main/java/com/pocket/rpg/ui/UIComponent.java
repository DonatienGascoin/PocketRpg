package com.pocket.rpg.ui;

import com.pocket.rpg.components.Component;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all UI components.
 * Provides common functionality: canvas lookup, raycast targeting.
 */
public abstract class UIComponent extends Component {

    @Getter @Setter
    protected boolean raycastTarget = true;

    // Cached canvas reference
    private UICanvas cachedCanvas;
    private boolean canvasCacheDirty = true;

    /**
     * Finds the UICanvas ancestor. Returns null if none found.
     */
    public UICanvas getCanvas() {
        if (canvasCacheDirty) {
            cachedCanvas = findCanvasInAncestors();
            canvasCacheDirty = false;
        }
        return cachedCanvas;
    }

    /**
     * Invalidate canvas cache (call when parent changes).
     */
    public void invalidateCanvasCache() {
        canvasCacheDirty = true;
    }

    private UICanvas findCanvasInAncestors() {
        if (gameObject == null) return null;

        // Check self first
        UICanvas canvas = gameObject.getComponent(UICanvas.class);
        if (canvas != null) return canvas;

        // Walk up parent chain
        var current = gameObject.getParent();
        while (current != null) {
            canvas = current.getComponent(UICanvas.class);
            if (canvas != null) return canvas;
            current = current.getParent();
        }

        return null;
    }

    @Override
    protected void onStart() {
        canvasCacheDirty = true;

        UICanvas canvas = getCanvas();
        if (canvas == null && !(this instanceof UICanvas)) {
            System.err.println("WARNING: " + getClass().getSimpleName() + " on '" +
                    gameObject.getName() + "' has no UICanvas ancestor - it won't render!");
        }
    }

    /**
     * Called by UIRenderer to render this component.
     * Subclasses implement their specific rendering logic.
     */
    public abstract void render(UIRendererBackend backend);

    /**
     * Get the width of this UI element.
     */
    public abstract float getWidth();

    /**
     * Get the height of this UI element.
     */
    public abstract float getHeight();
}