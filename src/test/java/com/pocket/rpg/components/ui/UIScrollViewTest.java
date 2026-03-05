package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UIScrollViewTest {

    private GameObject scrollViewGo;
    private UIScrollView scrollView;
    private GameObject viewportGo;
    private UITransform viewportTransform;
    private GameObject contentGo;
    private UITransform contentTransform;

    @BeforeEach
    void setUp() {
        // Build hierarchy: ScrollView -> Viewport (UIMask) -> Content
        scrollViewGo = new GameObject("ScrollView");
        UITransform rootTransform = new UITransform(200, 300);
        scrollViewGo.addComponent(rootTransform);
        scrollView = new UIScrollView();
        scrollViewGo.addComponent(scrollView);

        viewportGo = new GameObject("Viewport");
        viewportTransform = new UITransform(200, 300);
        viewportGo.addComponent(viewportTransform);
        viewportGo.addComponent(new UIMask());
        viewportGo.setParent(scrollViewGo);

        contentGo = new GameObject("Content");
        contentTransform = new UITransform(200, 0);
        contentGo.addComponent(contentTransform);
        contentGo.setParent(viewportGo);
    }

    private void addContentChild(float y, float height) {
        GameObject child = new GameObject("Item");
        UITransform ct = new UITransform(200, height);
        ct.setOffset(new Vector2f(0, y));
        child.addComponent(ct);
        child.setParent(contentGo);
    }

    // ========================================================================
    // Hierarchy Lookup
    // ========================================================================

    @Nested
    class HierarchyLookup {

        @Test
        void findsViewportByUIMask() {
            assertSame(viewportGo, scrollView.getViewport());
        }

        @Test
        void findsContentAsFirstChildOfViewport() {
            assertSame(contentGo, scrollView.getContent());
        }

        @Test
        void returnsNullViewportIfNoUIMaskChild() {
            UIScrollView orphan = new UIScrollView();
            GameObject go = new GameObject("Orphan");
            go.addComponent(new UITransform(100, 100));
            go.addComponent(orphan);

            assertNull(orphan.getViewport());
            assertNull(orphan.getContent());
        }
    }

    // ========================================================================
    // Scroll Offset Clamping
    // ========================================================================

    @Nested
    class ScrollOffsetClamping {

        @BeforeEach
        void addItems() {
            // 10 items, each 40px tall, starting at y=0,40,80,...
            for (int i = 0; i < 10; i++) {
                addContentChild(i * 40f, 40f);
            }
            scrollView.updateMetrics();
        }

        @Test
        void contentHeightComputedFromChildren() {
            // Last child at y=360, height=40 -> bottom = 400
            assertEquals(400f, scrollView.getContentHeight(), 0.01f);
        }

        @Test
        void viewportHeightFromTransform() {
            assertEquals(300f, scrollView.getViewportHeight(), 0.01f);
        }

        @Test
        void maxScrollOffset() {
            // contentHeight(400) - viewportHeight(300) = 100
            assertEquals(100f, scrollView.getMaxScrollOffset(), 0.01f);
        }

        @Test
        void cannotScrollNegative() {
            scrollView.setScrollOffset(-50f);
            assertEquals(0f, scrollView.getScrollOffset(), 0.01f);
        }

        @Test
        void cannotScrollPastContent() {
            scrollView.setScrollOffset(200f);
            assertEquals(100f, scrollView.getScrollOffset(), 0.01f);
        }

        @Test
        void scrollDeltaAccumulates() {
            scrollView.scroll(30f);
            assertEquals(30f, scrollView.getScrollOffset(), 0.01f);
            scrollView.scroll(50f);
            assertEquals(80f, scrollView.getScrollOffset(), 0.01f);
        }

        @Test
        void scrollClampedOnDelta() {
            scrollView.scroll(200f);
            assertEquals(100f, scrollView.getScrollOffset(), 0.01f);
        }
    }

    // ========================================================================
    // Normalized Scroll
    // ========================================================================

    @Nested
    class NormalizedScroll {

        @BeforeEach
        void addItems() {
            for (int i = 0; i < 10; i++) {
                addContentChild(i * 40f, 40f);
            }
            scrollView.updateMetrics();
        }

        @Test
        void normalizedAtTopIsZero() {
            scrollView.setScrollOffset(0);
            assertEquals(0f, scrollView.getScrollNormalized(), 0.01f);
        }

        @Test
        void normalizedAtBottomIsOne() {
            scrollView.setScrollOffset(100f);
            assertEquals(1f, scrollView.getScrollNormalized(), 0.01f);
        }

        @Test
        void normalizedAtMiddle() {
            scrollView.setScrollOffset(50f);
            assertEquals(0.5f, scrollView.getScrollNormalized(), 0.01f);
        }

        @Test
        void setNormalizedToHalf() {
            scrollView.setScrollNormalized(0.5f);
            assertEquals(50f, scrollView.getScrollOffset(), 0.01f);
        }

        @Test
        void setNormalizedClamped() {
            scrollView.setScrollNormalized(2f);
            assertEquals(100f, scrollView.getScrollOffset(), 0.01f);
        }
    }

    // ========================================================================
    // Content Fits in Viewport
    // ========================================================================

    @Nested
    class ContentFitsInViewport {

        @BeforeEach
        void addFewItems() {
            // 3 items * 40 = 120, viewport = 300 -> no scroll needed
            for (int i = 0; i < 3; i++) {
                addContentChild(i * 40f, 40f);
            }
            scrollView.updateMetrics();
        }

        @Test
        void cannotScroll() {
            assertFalse(scrollView.canScroll());
        }

        @Test
        void maxScrollOffsetIsZero() {
            assertEquals(0f, scrollView.getMaxScrollOffset(), 0.01f);
        }

        @Test
        void scrollOffsetStaysAtZero() {
            scrollView.scroll(100f);
            assertEquals(0f, scrollView.getScrollOffset(), 0.01f);
        }

        @Test
        void normalizedIsZero() {
            assertEquals(0f, scrollView.getScrollNormalized(), 0.01f);
        }
    }

    // ========================================================================
    // Scrollbar Visibility
    // ========================================================================

    @Nested
    class ScrollbarVisibility {

        private GameObject scrollbarGo;

        @BeforeEach
        void addScrollbar() {
            scrollbarGo = new GameObject("Scrollbar");
            scrollbarGo.addComponent(new UITransform(12, 300));
            scrollbarGo.addComponent(new UIScrollbar());
            scrollbarGo.setParent(scrollViewGo);
            scrollView.invalidateHierarchyCache();
        }

        @Test
        void autoShowsWhenContentOverflows() {
            for (int i = 0; i < 10; i++) {
                addContentChild(i * 40f, 40f);
            }
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.AUTO);
            scrollView.updateMetrics();

            assertTrue(scrollView.isScrollbarVisible());
            assertTrue(scrollbarGo.isEnabled());
        }

        @Test
        void autoHidesWhenContentFits() {
            addContentChild(0, 100);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.AUTO);
            scrollView.updateMetrics();

            assertFalse(scrollView.isScrollbarVisible());
            assertFalse(scrollbarGo.isEnabled());
        }

        @Test
        void alwaysShowsRegardless() {
            addContentChild(0, 100);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.ALWAYS);
            scrollView.updateMetrics();

            assertTrue(scrollView.isScrollbarVisible());
            assertTrue(scrollbarGo.isEnabled());
        }

        @Test
        void neverHidesRegardless() {
            for (int i = 0; i < 10; i++) {
                addContentChild(i * 40f, 40f);
            }
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.NEVER);
            scrollView.updateMetrics();

            assertFalse(scrollView.isScrollbarVisible());
            assertFalse(scrollbarGo.isEnabled());
        }

        @Test
        void setNeverDisablesScrollbarImmediately_noUpdateMetricsNeeded() {
            scrollbarGo.setEnabled(true);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.NEVER);

            assertFalse(scrollbarGo.isEnabled(), "Scrollbar GO should be disabled immediately by setter");
        }

        @Test
        void setAlwaysEnablesScrollbarImmediately_noUpdateMetricsNeeded() {
            scrollbarGo.setEnabled(false);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.ALWAYS);

            assertTrue(scrollbarGo.isEnabled(), "Scrollbar GO should be enabled immediately by setter");
        }

        @Test
        void autoStillWorksViaUpdateMetrics() {
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.AUTO);
            // No content -> can't scroll -> should hide
            scrollView.updateMetrics();
            assertFalse(scrollbarGo.isEnabled());

            // Add overflowing content -> can scroll -> should show
            for (int i = 0; i < 10; i++) {
                addContentChild(i * 40f, 40f);
            }
            scrollView.updateMetrics();
            assertTrue(scrollbarGo.isEnabled());
        }

    }

    // ========================================================================
    // Apply Scroll Offset
    // ========================================================================

    @Nested
    class ApplyOffset {

        @Test
        void appliesNegativeOffsetToContentY() {
            for (int i = 0; i < 10; i++) {
                addContentChild(i * 40f, 40f);
            }
            scrollView.updateMetrics();
            scrollView.setScrollOffset(50f);
            scrollView.applyScrollOffset();

            assertEquals(-50f, contentTransform.getOffset().y, 0.01f);
        }

        @Test
        void zeroOffsetAtTop() {
            scrollView.updateMetrics();
            scrollView.applyScrollOffset();

            assertEquals(0f, contentTransform.getOffset().y, 0.01f);
        }
    }

    // ========================================================================
    // Viewport/Scrollbar Tiling
    // ========================================================================

    @Nested
    class ViewportTiling {

        private GameObject scrollbarGo;
        private UITransform scrollbarTransform;

        @BeforeEach
        void addScrollbarAndContent() {
            scrollbarGo = new GameObject("Scrollbar");
            scrollbarTransform = new UITransform(12, 300);
            scrollbarGo.addComponent(scrollbarTransform);
            scrollbarGo.addComponent(new UIScrollbar());
            scrollbarGo.setParent(scrollViewGo);
            scrollView.invalidateHierarchyCache();

            // Add enough content to overflow
            for (int i = 0; i < 10; i++) {
                addContentChild(i * 40f, 40f);
            }
        }

        @Test
        void viewportWidthEqualsScrollViewMinusScrollbar_fixedMode() {
            viewportTransform.setWidthMode(UITransform.SizeMode.FIXED);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.ALWAYS);
            scrollView.updateMetrics();

            // ScrollView=200, Scrollbar=12, Viewport should be 188
            assertEquals(188f, viewportTransform.getWidth(), 0.01f);
        }

        @Test
        void viewportWidthPercentComputedCorrectly_percentMode() {
            viewportTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            viewportTransform.setWidthPercent(100);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.ALWAYS);
            scrollView.updateMetrics();

            // ScrollView=200, Scrollbar=12, viewport target=188
            // percent = (188/200)*100 = 94
            assertEquals(94f, viewportTransform.getWidthPercent(), 0.01f);
        }

        @Test
        void scrollbarHidden_viewportGetsFullWidth() {
            viewportTransform.setWidthMode(UITransform.SizeMode.FIXED);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.NEVER);
            scrollView.updateMetrics();

            assertEquals(200f, viewportTransform.getWidth(), 0.01f);
        }

        @Test
        void scrollbarShown_viewportShrinksByScrollbarWidth() {
            viewportTransform.setWidthMode(UITransform.SizeMode.FIXED);
            scrollbarTransform.setWidth(20);
            scrollView.setShowScrollbar(UIScrollView.ScrollbarVisibility.ALWAYS);
            scrollView.updateMetrics();

            assertEquals(180f, viewportTransform.getWidth(), 0.01f);
        }
    }

    // ========================================================================
    // Empty Content
    // ========================================================================

    @Nested
    class EmptyContent {

        @Test
        void emptyContentHeightIsZero() {
            scrollView.updateMetrics();
            assertEquals(0f, scrollView.getContentHeight(), 0.01f);
        }

        @Test
        void cannotScrollWithNoContent() {
            scrollView.updateMetrics();
            assertFalse(scrollView.canScroll());
        }
    }
}
