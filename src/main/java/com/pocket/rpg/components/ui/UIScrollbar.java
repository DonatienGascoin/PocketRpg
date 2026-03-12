package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.Tooltip;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.ui.UIRendererBackend;
import lombok.Getter;
import lombok.Setter;


/**
 * Visual scrollbar that reflects and controls a UIScrollView's scroll position.
 * <p>
 * Expected hierarchy:
 * <pre>
 * Scrollbar (this component + UIImage track + UITransform)
 * └── Handle (UIImage + UITransform)
 * </pre>
 * <p>
 * The scrollbar finds its parent UIScrollView automatically (walks up the hierarchy).
 * Each frame, it updates the Handle child's position and size to reflect the
 * current scroll state.
 */
@ComponentMeta(category = "UI")
public class UIScrollbar extends UIComponent implements UITransformDriver {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    @Getter @Setter
    private float minHandleSize = 20f;

    @Getter @Setter
    private boolean fixedHandleSize = false;

    @Getter @Setter
    private float handleSize = 20f;

    @Getter @Setter
    @Tooltip("Inset from top and bottom of the track. Prevents the handle from reaching the edges.")
    private float trackPadding = 0f;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    private transient UIScrollView cachedScrollView;
    private transient boolean scrollViewCacheDirty = true;

    @Override
    public void setGameObject(GameObject gameObject) {
        super.setGameObject(gameObject);
        scrollViewCacheDirty = true;
    }

    /** True when the user is dragging the scrollbar handle. */
    @Getter
    private transient boolean dragging = false;

    /** Y position where drag started (in game coordinates). */
    private transient float dragStartMouseY;

    /** Scroll offset when drag started. */
    private transient float dragStartScrollOffset;

    /**
     * Editor preview normalized position (0-1). Used to position the handle
     * when no UIScrollView is present or content doesn't overflow.
     */
    @Getter @Setter
    private transient float previewNormalized = 0f;

    // ========================================================================
    // UITransformDriver
    // ========================================================================

    @Override
    public TransformDriverInfo getChildDriverInfo(GameObject child) {
        return TransformDriverInfo.entirelyDriven("Scrollbar");
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Gets the associated UIScrollView (walks up hierarchy to find it).
     */
    public UIScrollView getScrollView() {
        if (scrollViewCacheDirty) {
            cachedScrollView = findScrollView();
            scrollViewCacheDirty = false;
        }
        return cachedScrollView;
    }

    public void invalidateScrollViewCache() {
        scrollViewCacheDirty = true;
    }

    /**
     * Updates the handle child's position and size based on current scroll state.
     * Called by the renderer each frame.
     * <p>
     * When a parent UIScrollView exists and has scrollable content, handle size
     * is proportional to viewport/content ratio and position follows scroll offset.
     * Otherwise, uses a fixed handle size and {@link #previewNormalized} for positioning
     * (useful for editor preview).
     */
    public void updateHandle() {
        GameObject handleGo = getHandle();
        if (handleGo == null) return;

        UITransform handleTransform = handleGo.getComponent(UITransform.class);
        UITransform ownTransform = getUITransform();
        if (handleTransform == null || ownTransform == null) return;

        float trackHeight = ownTransform.getEffectiveHeight();
        if (trackHeight <= 0) return;

        // Determine handle size (based on full track, not affected by padding)
        float handleHeight;
        float normalized;

        UIScrollView scrollView = getScrollView();
        boolean connected = scrollView != null && scrollView.getContentHeight() > 0;
        boolean canScroll = connected && scrollView.canScroll();

        if (fixedHandleSize) {
            handleHeight = Math.min(handleSize, trackHeight);
        } else if (connected && canScroll) {
            float handleRatio = Math.min(1f, scrollView.getViewportHeight() / scrollView.getContentHeight());
            handleHeight = Math.max(minHandleSize, trackHeight * handleRatio);
        } else {
            handleHeight = Math.min(minHandleSize, trackHeight);
        }

        if (canScroll) {
            normalized = scrollView.getScrollNormalized();
        } else {
            normalized = previewNormalized;
        }

        // Handle position — padding only constrains travel range, not size
        float scrollableTrack = Math.max(0, trackHeight - 2 * trackPadding - handleHeight);
        float handleY = trackPadding + scrollableTrack * normalized;

        // Apply to handle transform
        handleTransform.setHeight(handleHeight);
        handleTransform.setAnchor(0, 0);
        handleTransform.setPivot(0, 0);
        handleTransform.setOffset(0, handleY);

        // Force full width
        handleTransform.setWidthMode(UITransform.SizeMode.PERCENT);
        handleTransform.setWidthPercent(100);
    }

    /**
     * Begins a drag operation.
     *
     * @param mouseY current mouse Y in game coordinates
     */
    public void beginDrag(float mouseY) {
        UIScrollView scrollView = getScrollView();
        if (scrollView == null) return;

        dragging = true;
        dragStartMouseY = mouseY;
        dragStartScrollOffset = scrollView.getScrollOffset();
    }

    /**
     * Updates during a drag operation.
     *
     * @param mouseY current mouse Y in game coordinates
     */
    public void updateDrag(float mouseY) {
        if (!dragging) return;

        UIScrollView scrollView = getScrollView();
        UITransform ownTransform = getUITransform();
        if (scrollView == null || ownTransform == null) return;

        float trackHeight = ownTransform.getEffectiveHeight();
        float contentH = scrollView.getContentHeight();
        float viewportH = scrollView.getViewportHeight();

        if (contentH <= 0 || trackHeight <= 0) return;

        float handleHeight;
        if (fixedHandleSize) {
            handleHeight = Math.min(handleSize, trackHeight);
        } else {
            float handleRatio = Math.min(1f, viewportH / contentH);
            handleHeight = Math.max(minHandleSize, trackHeight * handleRatio);
        }
        float scrollableTrack = Math.max(0, trackHeight - 2 * trackPadding - handleHeight);

        if (scrollableTrack <= 0) return;

        float mouseDelta = mouseY - dragStartMouseY;
        float scrollDelta = (mouseDelta / scrollableTrack) * scrollView.getMaxScrollOffset();
        scrollView.setScrollOffset(dragStartScrollOffset + scrollDelta);
    }

    /**
     * Ends a drag operation.
     */
    public void endDrag() {
        dragging = false;
    }

    /**
     * Jumps to a position based on click on the track.
     *
     * @param mouseY click Y in game coordinates
     */
    public void jumpToTrackPosition(float mouseY) {
        UIScrollView scrollView = getScrollView();
        UITransform ownTransform = getUITransform();
        if (scrollView == null || ownTransform == null) return;

        float trackTop = ownTransform.getScreenPosition().y;
        float trackHeight = ownTransform.getEffectiveHeight();
        if (trackHeight <= 0) return;

        float usableTrack = trackHeight - 2 * trackPadding;
        if (usableTrack <= 0) return;

        float normalized = (mouseY - trackTop - trackPadding) / usableTrack;
        normalized = Math.max(0, Math.min(1, normalized));
        scrollView.setScrollNormalized(normalized);
    }

    /**
     * Checks if a point (in game coordinates) is over the handle.
     */
    public boolean isPointOverHandle(float x, float y) {
        GameObject handleGo = getHandle();
        if (handleGo == null) return false;

        UITransform handleTransform = handleGo.getComponent(UITransform.class);
        UITransform ownTransform = getUITransform();
        if (handleTransform == null || ownTransform == null) return false;

        var ownPos = ownTransform.getScreenPosition();
        float handleX = ownPos.x;
        float handleY = ownPos.y + handleTransform.getOffset().y;
        float handleW = ownTransform.getEffectiveWidth();
        float handleH = handleTransform.getHeight();

        return x >= handleX && x < handleX + handleW
                && y >= handleY && y < handleY + handleH;
    }

    /**
     * Checks if a point is over the track area.
     */
    public boolean isPointOverTrack(float x, float y) {
        UITransform ownTransform = getUITransform();
        if (ownTransform == null) return false;

        var pos = ownTransform.getScreenPosition();
        float w = ownTransform.getEffectiveWidth();
        float h = ownTransform.getEffectiveHeight();

        return x >= pos.x && x < pos.x + w && y >= pos.y && y < pos.y + h;
    }

    // ========================================================================
    // HIERARCHY LOOKUP
    // ========================================================================

    /**
     * Gets the Handle child (first child of this scrollbar).
     */
    public GameObject getHandle() {
        if (gameObject == null || gameObject.getChildren().isEmpty()) return null;
        return gameObject.getChildren().get(0);
    }

    private UIScrollView findScrollView() {
        if (gameObject == null) return null;

        // Walk up to find UIScrollView on an ancestor
        var current = gameObject.getParent();
        while (current != null) {
            UIScrollView sv = current.getComponent(UIScrollView.class);
            if (sv != null) return sv;
            current = current.getParent();
        }
        return null;
    }

    @Override
    public void render(UIRendererBackend backend) {
        // UIScrollbar doesn't render itself — the UIImage on the track GO and handle GO do.
    }
}
