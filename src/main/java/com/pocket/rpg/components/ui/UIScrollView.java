package com.pocket.rpg.components.ui;

import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import lombok.Getter;
import lombok.Setter;

/**
 * Vertical scroll view that manages content offset within a viewport.
 * <p>
 * Expected hierarchy (created by UIEntityFactory):
 * <pre>
 * ScrollView (this component + UIPanel + UITransform)
 * ├── Viewport (UIMask + UITransform — clips children)
 * │   └── Content (UITransform — holds scrollable items)
 * └── Scrollbar (UIScrollbar + UIImage + UITransform)
 *     └── Handle (UIImage + UITransform)
 * </pre>
 * <p>
 * UIScrollView finds the Viewport child (by looking for UIMask) and the
 * Content grandchild (first child of Viewport). It offsets Content's Y
 * position each frame based on {@code scrollOffset}. Clipping is handled
 * by UIMask on the Viewport — this component does not manage clipping.
 */
@ComponentMeta(category = "UI")
public class UIScrollView extends UIComponent implements UITransformDriver {

    public enum ScrollbarVisibility {
        ALWAYS, AUTO, NEVER
    }

    public enum ScrollbarPosition {
        LEFT, RIGHT
    }

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    @Getter @Setter
    private float scrollSensitivity = 20f;

    @Getter
    private ScrollbarVisibility showScrollbar = ScrollbarVisibility.AUTO;

    @Getter @Setter
    private ScrollbarPosition scrollbarPosition = ScrollbarPosition.RIGHT;

    // ========================================================================
    // RUNTIME STATE (not serialized)
    // ========================================================================

    @Getter
    private transient float scrollOffset = 0f;

    @Getter
    private transient float contentHeight = 0f;

    @Getter
    private transient float viewportHeight = 0f;

    /**
     * When true, the scrollbar GO is kept visible regardless of content,
     * allowing editor preview of handle positioning and track padding.
     */
    @Getter @Setter
    private transient boolean editorPreview = false;

    private transient GameObject cachedViewport;
    private transient GameObject cachedContent;
    private transient GameObject cachedScrollbarGo;
    private transient boolean hierarchyCacheDirty = true;

    @Override
    public void setGameObject(GameObject gameObject) {
        super.setGameObject(gameObject);
        hierarchyCacheDirty = true;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    public void setShowScrollbar(ScrollbarVisibility showScrollbar) {
        this.showScrollbar = showScrollbar;
        updateScrollbarVisibility();
    }

    /**
     * Single point of truth for scrollbar GO enabled state.
     * Called whenever showScrollbar, editorPreview, or metrics change.
     */
    public void updateScrollbarVisibility() {
        GameObject scrollbarGo = getScrollbarGameObject();
        if (scrollbarGo == null) return;
        scrollbarGo.setEnabled(isScrollbarVisible());
    }

    /**
     * Sets scroll offset in pixels, clamped to valid range.
     */
    public void setScrollOffset(float offset) {
        this.scrollOffset = clampOffset(offset);
    }

    /**
     * Scrolls by a delta in pixels (positive = scroll down).
     */
    public void scroll(float delta) {
        setScrollOffset(scrollOffset + delta);
    }

    /**
     * Gets normalized scroll position (0 = top, 1 = bottom).
     */
    public float getScrollNormalized() {
        float maxScroll = getMaxScrollOffset();
        if (maxScroll <= 0) return 0f;
        return scrollOffset / maxScroll;
    }

    /**
     * Sets normalized scroll position (0 = top, 1 = bottom).
     */
    public void setScrollNormalized(float normalized) {
        float maxScroll = getMaxScrollOffset();
        setScrollOffset(normalized * maxScroll);
    }

    /**
     * Maximum scroll offset (0 if content fits in viewport).
     */
    public float getMaxScrollOffset() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    /**
     * Whether content overflows the viewport (scrolling is possible).
     */
    public boolean canScroll() {
        return contentHeight > viewportHeight;
    }

    /**
     * Whether the scrollbar should be visible based on current state and visibility setting.
     */
    public boolean isScrollbarVisible() {
        return switch (showScrollbar) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> canScroll();
        };
    }

    // ========================================================================
    // UITransformDriver
    // ========================================================================

    @Override
    public TransformDriverInfo getChildDriverInfo(GameObject child) {
        // Viewport child (has UIMask) → width driven by ScrollView
        if (child != null && child.getComponent(UIMask.class) != null) {
            return new TransformDriverInfo(false, true, false, false, "ScrollView");
        }
        return null;
    }


    /**
     * Gets the scrollbar width (from the scrollbar's UITransform).
     */
    public float getScrollbarWidth() {
        GameObject scrollbarGo = getScrollbarGameObject();
        if (scrollbarGo == null) return 0;
        UITransform scrollbarTransform = scrollbarGo.getComponent(UITransform.class);
        if (scrollbarTransform == null) return 0;
        return scrollbarTransform.getEffectiveWidth();
    }

    // ========================================================================
    // CONTENT HEIGHT COMPUTATION
    // ========================================================================

    /**
     * Recomputes content height and viewport height from the hierarchy.
     * Called by the renderer before applying scroll offset.
     */
    public void updateMetrics() {
        GameObject viewport = getViewport();
        GameObject content = getContent();

        if (viewport == null || content == null) {
            updateScrollbarVisibility();
            return;
        }

        // Read viewport height for scroll calculations
        UITransform viewportTransform = viewport.getComponent(UITransform.class);
        if (viewportTransform != null) {
            viewportHeight = viewportTransform.getEffectiveHeight();
        }

        contentHeight = computeContentHeight(content);

        // Update scrollbar visibility (after computing metrics so canScroll() is accurate)
        updateScrollbarVisibility();

        // Tile viewport width: viewport = scrollView width - scrollbar width
        GameObject scrollbarGo = getScrollbarGameObject();
        UITransform ownTransform = getUITransform();
        if (viewportTransform != null && ownTransform != null) {
            float parentWidth = ownTransform.getEffectiveWidth();
            float scrollbarWidth = (scrollbarGo != null && scrollbarGo.isEnabled()) ? getScrollbarWidth() : 0;
            float viewportTargetWidth = parentWidth - scrollbarWidth;

            if (viewportTransform.getWidthMode() == UITransform.SizeMode.PERCENT) {
                float pct = parentWidth > 0 ? (viewportTargetWidth / parentWidth) * 100f : 100f;
                viewportTransform.setWidthPercent(pct);
            } else {
                viewportTransform.setWidth(viewportTargetWidth);
            }

            // Position scrollbar and viewport based on scrollbarPosition
            UITransform scrollbarTransform = scrollbarGo != null ? scrollbarGo.getComponent(UITransform.class) : null;
            if (scrollbarPosition == ScrollbarPosition.LEFT) {
                // Scrollbar on left: anchor/pivot at (0,0), viewport offset shifts right
                if (scrollbarTransform != null) {
                    scrollbarTransform.getAnchor().set(0, 0);
                    scrollbarTransform.getPivot().set(0, 0);
                }
                viewportTransform.setOffset(scrollbarWidth, viewportTransform.getOffset().y);
            } else {
                // Scrollbar on right (default): anchor/pivot at (1,0), viewport at x=0
                if (scrollbarTransform != null) {
                    scrollbarTransform.getAnchor().set(1, 0);
                    scrollbarTransform.getPivot().set(1, 0);
                }
                viewportTransform.setOffset(0, viewportTransform.getOffset().y);
            }
        }

        // Re-clamp offset in case content shrank
        scrollOffset = clampOffset(scrollOffset);
    }

    /**
     * Applies the current scroll offset to the Content's Y position.
     * Called by the renderer each frame.
     */
    public void applyScrollOffset() {
        GameObject content = getContent();
        if (content == null) return;

        UITransform contentTransform = content.getComponent(UITransform.class);
        if (contentTransform == null) return;

        contentTransform.setOffset(contentTransform.getOffset().x, -scrollOffset);
    }

    // ========================================================================
    // HIERARCHY LOOKUP
    // ========================================================================

    /**
     * Gets the Viewport child (the one with UIMask).
     */
    public GameObject getViewport() {
        if (hierarchyCacheDirty) {
            refreshHierarchyCache();
        }
        return cachedViewport;
    }

    /**
     * Gets the Content child (first child of Viewport).
     */
    public GameObject getContent() {
        if (hierarchyCacheDirty) {
            refreshHierarchyCache();
        }
        return cachedContent;
    }

    /**
     * Invalidates the hierarchy cache. Call when children change.
     */
    public void invalidateHierarchyCache() {
        hierarchyCacheDirty = true;
    }

    /**
     * Gets the Scrollbar sibling (child of ScrollView root with UIScrollbar).
     */
    public GameObject getScrollbarGameObject() {
        if (hierarchyCacheDirty) {
            refreshHierarchyCache();
        }
        return cachedScrollbarGo;
    }

    private void refreshHierarchyCache() {
        cachedViewport = null;
        cachedContent = null;
        cachedScrollbarGo = null;
        hierarchyCacheDirty = false;

        if (gameObject == null) return;

        // Find viewport (child with UIMask) and scrollbar (child with UIScrollbar)
        for (GameObject child : gameObject.getChildren()) {
            if (cachedViewport == null && child.getComponent(UIMask.class) != null) {
                cachedViewport = child;
            }
            if (cachedScrollbarGo == null && child.getComponent(UIScrollbar.class) != null) {
                cachedScrollbarGo = child;
            }
        }

        // Find content: first child of viewport
        if (cachedViewport != null && !cachedViewport.getChildren().isEmpty()) {
            cachedContent = cachedViewport.getChildren().get(0);
        }
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    /**
     * Computes content height from the maximum bottom edge of all children.
     */
    private float computeContentHeight(GameObject content) {
        // If content has a layout group, use its computed extent (includes all padding)
        LayoutGroup layout = content.getComponent(LayoutGroup.class);
        if (layout != null) {
            return layout.getContentExtentHeight();
        }

        // Fallback: measure from children directly
        float maxBottom = 0;
        for (GameObject child : content.getChildren()) {
            if (!child.isEnabled()) continue;

            UITransform ct = child.getComponent(UITransform.class);
            if (ct == null) continue;

            float childBottom = ct.getOffset().y + ct.getEffectiveHeight();
            maxBottom = Math.max(maxBottom, childBottom);
        }
        return maxBottom;
    }

    private float clampOffset(float offset) {
        return Math.max(0, Math.min(offset, getMaxScrollOffset()));
    }

}
