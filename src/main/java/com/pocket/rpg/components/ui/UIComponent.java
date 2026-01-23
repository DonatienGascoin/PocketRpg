package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Base class for all UI components.
 * Provides common functionality: canvas lookup, UITransform validation, raycast targeting.
 * <p>
 * REQUIRES: UITransform component on the same GameObject (except for UICanvas).
 */
@ComponentMeta(category = "UI")
public abstract class UIComponent extends Component {

    /**
     * Optional key for registering this component with UIManager.
     * If set, the component can be retrieved via UIManager.get(uiKey).
     * Registration happens during scene initialization, before onStart().
     */
    @Getter
    @Setter
    protected String uiKey;

    @Getter
    @Setter
    protected boolean raycastTarget = true;

    // Cached references (transient - not serialized)
    private transient UICanvas cachedCanvas;
    private transient boolean canvasCacheDirty = true;

    private transient UITransform cachedTransform;
    private transient boolean transformCacheDirty = true;

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

    /**
     * Override to invalidate caches when gameObject changes.
     * This is critical for GameViewPanel's wrapper GameObjects.
     */
    @Override
    public void setGameObject(com.pocket.rpg.core.GameObject gameObject) {
        super.setGameObject(gameObject);
        transformCacheDirty = true;
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

    // ========================================================================
    // RENDER BOUNDS HELPER
    // ========================================================================

    /**
     * Computed render bounds for a UI element.
     */
    public record RenderBounds(
        float x, float y,           // Top-left position
        float width, float height,  // Scaled dimensions
        float rotation,             // World rotation in degrees
        float pivotX, float pivotY  // Pivot ratio (0-1)
    ) {}

    /**
     * Computes the render bounds from UITransform.
     * Returns null if UITransform is missing.
     */
    protected RenderBounds computeRenderBounds() {
        UITransform transform = getUITransform();
        if (transform == null) return null;

        Vector2f pivotWorld = transform.getWorldPivotPosition2D();
        Vector2f scale = transform.getComputedWorldScale2D();
        float w = transform.getEffectiveWidth() * scale.x;
        float h = transform.getEffectiveHeight() * scale.y;
        float rotation = transform.getComputedWorldRotation2D();
        Vector2f pivot = transform.getEffectivePivot();

        float x = pivotWorld.x - pivot.x * w;
        float y = pivotWorld.y - pivot.y * h;

        return new RenderBounds(x, y, w, h, rotation, pivot.x, pivot.y);
    }
}