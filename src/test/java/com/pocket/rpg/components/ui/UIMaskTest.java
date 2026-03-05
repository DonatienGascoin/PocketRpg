package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UIMaskTest {

    private GameObject maskGo;
    private UITransform maskTransform;
    private UIMask mask;

    @BeforeEach
    void setUp() {
        maskGo = new GameObject("MaskObject");
        maskTransform = new UITransform(200, 150);
        maskGo.addComponent(maskTransform);

        mask = new UIMask();
        maskGo.addComponent(mask);
    }

    // ========================================================================
    // Component Setup
    // ========================================================================

    @Nested
    class ComponentSetup {

        @Test
        void maskIsUIComponent() {
            assertInstanceOf(UIComponent.class, mask);
        }

        @Test
        void defaultShowMaskGraphicIsTrue() {
            UIMask freshMask = new UIMask();
            assertTrue(freshMask.isShowMaskGraphic());
        }

        @Test
        void canToggleShowMaskGraphic() {
            mask.setShowMaskGraphic(false);
            assertFalse(mask.isShowMaskGraphic());

            mask.setShowMaskGraphic(true);
            assertTrue(mask.isShowMaskGraphic());
        }

        @Test
        void maskHasUITransformReference() {
            assertNotNull(mask.getUITransform());
            assertSame(maskTransform, mask.getUITransform());
        }
    }

    // ========================================================================
    // Hierarchy with Mask
    // ========================================================================

    @Nested
    class HierarchyWithMask {

        @Test
        void childrenCanBeAddedUnderMaskedObject() {
            GameObject child = new GameObject("Child");
            UITransform childTransform = new UITransform(100, 50);
            child.addComponent(childTransform);
            child.setParent(maskGo);

            assertEquals(1, maskGo.getChildren().size());
            assertSame(maskGo, child.getParent());
        }

        @Test
        void nestedMasksCreateValidHierarchy() {
            // Outer mask
            GameObject innerMaskGo = new GameObject("InnerMask");
            UITransform innerTransform = new UITransform(100, 75);
            innerMaskGo.addComponent(innerTransform);
            UIMask innerMask = new UIMask();
            innerMaskGo.addComponent(innerMask);

            innerMaskGo.setParent(maskGo);

            // Child of inner mask
            GameObject leaf = new GameObject("Leaf");
            UITransform leafTransform = new UITransform(50, 30);
            leaf.addComponent(leafTransform);
            leaf.setParent(innerMaskGo);

            assertEquals(1, maskGo.getChildren().size());
            assertEquals(1, innerMaskGo.getChildren().size());
        }

        @Test
        void deepHierarchyUnderMask() {
            // Mask → Panel1 → Panel2 → Image (4 levels)
            GameObject panel1 = new GameObject("Panel1");
            panel1.addComponent(new UITransform(180, 130));
            panel1.setParent(maskGo);

            GameObject panel2 = new GameObject("Panel2");
            panel2.addComponent(new UITransform(160, 110));
            panel2.setParent(panel1);

            GameObject image = new GameObject("Image");
            image.addComponent(new UITransform(80, 80));
            image.setParent(panel2);

            assertEquals(1, maskGo.getChildren().size());
            assertEquals(1, panel1.getChildren().size());
            assertEquals(1, panel2.getChildren().size());
            assertEquals(0, image.getChildren().size());
        }
    }

    // ========================================================================
    // Scissor Rect Math (static helper tests)
    // ========================================================================

    @Nested
    class ScissorRectMath {

        @Test
        void rectsOverlap() {
            // Two overlapping rects
            assertFalse(isOutside(0, 0, 100, 100, 50, 50, 100, 100));
        }

        @Test
        void rectFullyInsideScissor() {
            assertFalse(isOutside(0, 0, 200, 200, 50, 50, 50, 50));
        }

        @Test
        void rectFullyOutsideRight() {
            assertTrue(isOutside(0, 0, 100, 100, 100, 0, 50, 50));
        }

        @Test
        void rectFullyOutsideLeft() {
            assertTrue(isOutside(50, 0, 100, 100, 0, 0, 50, 100));
        }

        @Test
        void rectFullyOutsideBelow() {
            assertTrue(isOutside(0, 0, 100, 100, 0, 100, 100, 50));
        }

        @Test
        void rectFullyOutsideAbove() {
            assertTrue(isOutside(0, 50, 100, 100, 0, 0, 100, 50));
        }

        @Test
        void rectTouchingEdgeIsOutside() {
            // Touching at edge (no overlap) counts as outside
            assertTrue(isOutside(0, 0, 100, 100, 100, 0, 100, 100));
        }

        @Test
        void partialOverlapIsNotOutside() {
            // Overlapping by 1 pixel
            assertFalse(isOutside(0, 0, 100, 100, 99, 0, 100, 100));
        }

        @Test
        void zeroSizeScissorEverythingOutside() {
            // Zero-size scissor at a point outside the child rect
            assertTrue(isOutside(200, 200, 0, 0, 0, 0, 100, 100));
        }

        @Test
        void zeroSizeChildIsOutsideIfNotInsideScissor() {
            assertTrue(isOutside(0, 0, 100, 100, 200, 200, 0, 0));
        }

        @Test
        void nestedScissorIntersection() {
            // Outer: (0,0) w=200,h=200, Inner: (50,50) w=100,h=100
            // Intersection should be (50,50) w=100,h=100
            float[] result = intersect(0, 0, 200, 200, 50, 50, 100, 100);
            assertEquals(50, result[0], 0.01f);
            assertEquals(50, result[1], 0.01f);
            assertEquals(100, result[2], 0.01f); // width
            assertEquals(100, result[3], 0.01f); // height
        }

        @Test
        void nestedScissorNoOverlap() {
            // Two non-overlapping rects
            float[] result = intersect(0, 0, 50, 50, 100, 100, 50, 50);
            assertEquals(0, result[2], 0.01f); // zero width
            assertEquals(0, result[3], 0.01f); // zero height
        }

        @Test
        void nestedScissorPartialOverlap() {
            // Outer: (0,0)-(100,100), Inner: (50,50)-(200,200)
            // Intersection: (50,50)-(100,100) = width 50, height 50
            float[] result = intersect(0, 0, 100, 100, 50, 50, 200, 200);
            assertEquals(50, result[0], 0.01f);
            assertEquals(50, result[1], 0.01f);
            assertEquals(50, result[2], 0.01f);
            assertEquals(50, result[3], 0.01f);
        }

        // Mirrors the UIRenderer.isOutsideScissor logic
        private boolean isOutside(float sx, float sy, float sw, float sh,
                                  float x, float y, float w, float h) {
            return (x >= sx + sw) || (x + w <= sx) || (y >= sy + sh) || (y + h <= sy);
        }

        // Mirrors the UIRenderer.pushScissor intersection logic
        private float[] intersect(float ax, float ay, float aw, float ah,
                                  float bx, float by, float bw, float bh) {
            float newX = Math.max(ax, bx);
            float newY = Math.max(ay, by);
            float newRight = Math.min(ax + aw, bx + bw);
            float newBottom = Math.min(ay + ah, by + bh);

            float width = Math.max(0, newRight - newX);
            float height = Math.max(0, newBottom - newY);

            return new float[]{newX, newY, width, height};
        }
    }

    // ========================================================================
    // Disabled Mask
    // ========================================================================

    @Nested
    class DisabledMask {

        @Test
        void disabledMaskReportsNotEnabled() {
            mask.setEnabled(false);
            assertFalse(mask.isEnabled());
        }

        @Test
        void enabledMaskReportsEnabled() {
            assertTrue(mask.isEnabled());
        }
    }
}
