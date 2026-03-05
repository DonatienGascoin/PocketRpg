package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UIScrollbarTest {

    private UIScrollView scrollView;
    private UIScrollbar scrollbar;
    private UITransform scrollbarTransform;
    private GameObject handleGo;
    private UITransform handleTransform;

    @BeforeEach
    void setUp() {
        // Build full hierarchy
        GameObject scrollViewGo = new GameObject("ScrollView");
        scrollViewGo.addComponent(new UITransform(200, 300));
        scrollView = new UIScrollView();
        scrollViewGo.addComponent(scrollView);

        // Viewport with mask
        GameObject viewportGo = new GameObject("Viewport");
        viewportGo.addComponent(new UITransform(188, 300));
        viewportGo.addComponent(new UIMask());
        viewportGo.setParent(scrollViewGo);

        // Content with items
        GameObject contentGo = new GameObject("Content");
        contentGo.addComponent(new UITransform(188, 0));
        contentGo.setParent(viewportGo);

        // Add 20 items of 40px each = 800px total content
        for (int i = 0; i < 20; i++) {
            GameObject item = new GameObject("Item" + i);
            UITransform itemT = new UITransform(188, 40);
            itemT.setOffset(0, i * 40f);
            item.addComponent(itemT);
            item.setParent(contentGo);
        }

        // Scrollbar
        GameObject scrollbarGo = new GameObject("Scrollbar");
        scrollbarTransform = new UITransform(12, 300);
        scrollbarGo.addComponent(scrollbarTransform);
        scrollbar = new UIScrollbar();
        scrollbarGo.addComponent(scrollbar);
        scrollbarGo.setParent(scrollViewGo);

        // Handle
        handleGo = new GameObject("Handle");
        handleTransform = new UITransform(12, 40);
        handleGo.addComponent(handleTransform);
        handleGo.setParent(scrollbarGo);

        // Initialize metrics
        scrollView.updateMetrics();
    }

    // ========================================================================
    // Scroll View Lookup
    // ========================================================================

    @Nested
    class ScrollViewLookup {

        @Test
        void findsScrollViewInParent() {
            assertSame(scrollView, scrollbar.getScrollView());
        }

        @Test
        void findsHandle() {
            assertSame(handleGo, scrollbar.getHandle());
        }
    }

    // ========================================================================
    // Handle Size
    // ========================================================================

    @Nested
    class HandleSize {

        @Test
        void handleSizeProportionalToViewportContent() {
            scrollbar.updateHandle();

            // viewport=300, content=800, track=300
            // handleRatio = 300/800 = 0.375
            // handleHeight = 300 * 0.375 = 112.5
            assertEquals(112.5f, handleTransform.getHeight(), 0.5f);
        }

        @Test
        void handleMinSize() {
            scrollbar.setMinHandleSize(150);
            scrollbar.updateHandle();

            // Computed would be 112.5, but min is 150
            assertEquals(150f, handleTransform.getHeight(), 0.5f);
        }

        @Test
        void handlePreviewSizeWhenContentFitsViewport() {
            // Remove existing content and add small content
            GameObject viewport = scrollView.getViewport();
            GameObject content = scrollView.getContent();
            // Clear children
            for (GameObject child : new java.util.ArrayList<>(content.getChildren())) {
                child.setParent(null);
            }
            // Add small content
            GameObject item = new GameObject("Small");
            UITransform itemT = new UITransform(188, 100);
            item.addComponent(itemT);
            item.setParent(content);

            scrollView.updateMetrics();
            scrollbar.updateHandle();

            // Content fits viewport -> standalone/preview mode
            // handleHeight = min(minHandleSize, trackHeight) = min(20, 300) = 20
            assertEquals(20f, handleTransform.getHeight(), 0.5f);
        }
    }

    // ========================================================================
    // Handle Position
    // ========================================================================

    @Nested
    class HandlePosition {

        @Test
        void handleAtTopWhenScrollIsZero() {
            scrollView.setScrollOffset(0);
            scrollbar.updateHandle();

            assertEquals(0f, handleTransform.getOffset().y, 0.5f);
        }

        @Test
        void handleAtBottomWhenScrollIsMax() {
            scrollView.setScrollOffset(scrollView.getMaxScrollOffset());
            scrollbar.updateHandle();

            // Track = 300, handle = 112.5, scrollable = 187.5
            // At max scroll -> handle at 187.5
            float expected = 300f - handleTransform.getHeight();
            assertEquals(expected, handleTransform.getOffset().y, 0.5f);
        }

        @Test
        void handleAtMiddleWhenScrollIsHalf() {
            scrollView.setScrollNormalized(0.5f);
            scrollbar.updateHandle();

            float scrollable = 300f - handleTransform.getHeight();
            assertEquals(scrollable * 0.5f, handleTransform.getOffset().y, 0.5f);
        }
    }

    // ========================================================================
    // Drag
    // ========================================================================

    @Nested
    class Drag {

        @Test
        void beginDragSetsFlag() {
            scrollbar.beginDrag(100f);
            assertTrue(scrollbar.isDragging());
        }

        @Test
        void endDragClearsFlag() {
            scrollbar.beginDrag(100f);
            scrollbar.endDrag();
            assertFalse(scrollbar.isDragging());
        }

        @Test
        void dragUpdatesScrollPosition() {
            scrollbar.updateHandle();
            float handleHeight = handleTransform.getHeight();
            float scrollableTrack = 300f - handleHeight;

            scrollbar.beginDrag(0f);
            // Drag to middle of scrollable track
            scrollbar.updateDrag(scrollableTrack / 2f);

            // Should be approximately half scroll
            assertEquals(scrollView.getMaxScrollOffset() / 2f,
                    scrollView.getScrollOffset(), 1f);
        }
    }

    // ========================================================================
    // Track Padding
    // ========================================================================

    @Nested
    class TrackPadding {

        @Test
        void handleAtTopWithPadding() {
            scrollbar.setTrackPadding(10f);
            scrollView.setScrollOffset(0);
            scrollbar.updateHandle();

            assertEquals(10f, handleTransform.getOffset().y, 0.5f);
        }

        @Test
        void handleAtBottomWithPadding() {
            scrollbar.setTrackPadding(10f);
            scrollView.setScrollOffset(scrollView.getMaxScrollOffset());
            scrollbar.updateHandle();

            // Handle bottom edge = trackHeight - padding
            float handleHeight = handleTransform.getHeight();
            float expectedY = 300f - 10f - handleHeight;
            assertEquals(expectedY, handleTransform.getOffset().y, 0.5f);
        }

        @Test
        void paddingDoesNotAffectHandleSize() {
            scrollbar.setTrackPadding(0f);
            scrollbar.updateHandle();
            float sizeWithoutPadding = handleTransform.getHeight();

            scrollbar.setTrackPadding(20f);
            scrollbar.updateHandle();
            float sizeWithPadding = handleTransform.getHeight();

            assertEquals(sizeWithoutPadding, sizeWithPadding, 0.01f);
        }

        @Test
        void zeroPadding_sameAsBefore() {
            scrollbar.setTrackPadding(0f);
            scrollView.setScrollOffset(0);
            scrollbar.updateHandle();
            assertEquals(0f, handleTransform.getOffset().y, 0.5f);

            scrollView.setScrollOffset(scrollView.getMaxScrollOffset());
            scrollbar.updateHandle();
            float expected = 300f - handleTransform.getHeight();
            assertEquals(expected, handleTransform.getOffset().y, 0.5f);
        }
    }

    // ========================================================================
    // Fixed Handle Size
    // ========================================================================

    @Nested
    class FixedHandleSize {

        @Test
        void fixedHandleSizeUsesHandleSizeField() {
            scrollbar.setFixedHandleSize(true);
            scrollbar.setHandleSize(50f);
            scrollbar.updateHandle();

            assertEquals(50f, handleTransform.getHeight(), 0.5f);
        }

        @Test
        void fixedHandleSizeClampedToTrackHeight() {
            scrollbar.setFixedHandleSize(true);
            scrollbar.setHandleSize(500f); // larger than track (300)
            scrollbar.updateHandle();

            assertEquals(300f, handleTransform.getHeight(), 0.5f);
        }

        @Test
        void dynamicModeUsesMinHandleSize() {
            scrollbar.setFixedHandleSize(false);
            scrollbar.setMinHandleSize(20f);
            scrollbar.updateHandle();

            // viewport=300, content=800, ratio=0.375, handle=112.5 > minHandle=20
            assertEquals(112.5f, handleTransform.getHeight(), 0.5f);
        }

        @Test
        void dynamicModeRespectsMinHandleSize() {
            scrollbar.setFixedHandleSize(false);
            scrollbar.setMinHandleSize(150f);
            scrollbar.updateHandle();

            // Computed 112.5 < min 150, so use 150
            assertEquals(150f, handleTransform.getHeight(), 0.5f);
        }

        @Test
        void fixedHandleSizeDefaultIsFalse() {
            UIScrollbar fresh = new UIScrollbar();
            assertFalse(fresh.isFixedHandleSize());
        }

        @Test
        void defaultHandleSize() {
            UIScrollbar fresh = new UIScrollbar();
            assertEquals(20f, fresh.getHandleSize(), 0.01f);
        }
    }

    // ========================================================================
    // Defaults
    // ========================================================================

    @Nested
    class Defaults {

        @Test
        void defaultMinHandleSize() {
            UIScrollbar fresh = new UIScrollbar();
            assertEquals(20f, fresh.getMinHandleSize(), 0.01f);
        }

        @Test
        void defaultNotDragging() {
            UIScrollbar fresh = new UIScrollbar();
            assertFalse(fresh.isDragging());
        }
    }
}
