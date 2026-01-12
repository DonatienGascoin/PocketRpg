package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all UI components.
 * Provides common functionality: canvas lookup, UITransform validation, raycast targeting.
 * <p>
 * REQUIRES: UITransform component on the same GameObject (except for UICanvas).
 */
public abstract class UIComponent extends Component {

    @Getter
    @Setter
    protected boolean raycastTarget = true;

    // Cached references
    private UICanvas cachedCanvas;
    private boolean canvasCacheDirty = true;

    private UITransform cachedTransform;
    private boolean transformCacheDirty = true;

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
     * Gets the UITransform on this GameObject.
     *
     * @throws IllegalStateException if UITransform is missing (except for UICanvas)
     */
    public UITransform getUITransform() {
        if (transformCacheDirty) {
            cachedTransform = gameObject.getComponent(UITransform.class);
            transformCacheDirty = false;
        }
        return cachedTransform;
    }

    /**
     * Invalidate canvas cache (call when parent changes).
     */
    public void invalidateCanvasCache() {
        canvasCacheDirty = true;
    }

    /**
     * Invalidate transform cache.
     */
    public void invalidateTransformCache() {
        transformCacheDirty = true;
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
        transformCacheDirty = true;

        // Validate canvas (except for UICanvas itself)
        if (!(this instanceof UICanvas)) {
            UICanvas canvas = getCanvas();
            if (canvas == null) {
                throw new IllegalStateException(
                        getClass().getSimpleName() + " on '" + gameObject.getName() +
                                "' has no UICanvas ancestor - UI components must be children of a Canvas!");
            }

            // Validate UITransform exists
            UITransform transform = getUITransform();
            if (transform == null) {
                throw new IllegalStateException(
                        getClass().getSimpleName() + " on '" + gameObject.getName() +
                                "' requires a UITransform component on the same GameObject!");
            }
        }
    }

    /**
     * Called by UIRenderer to render this component.
     * Subclasses implement their specific rendering logic.
     */
    public abstract void render(UIRendererBackend backend);

    /**
     * Get the width of this UI element from UITransform.
     */
    public float getWidth() {
        UITransform t = getUITransform();
        return t != null ? t.getWidth() : 0;
    }

    /**
     * Get the height of this UI element from UITransform.
     */
    public float getHeight() {
        UITransform t = getUITransform();
        return t != null ? t.getHeight() : 0;
    }
}