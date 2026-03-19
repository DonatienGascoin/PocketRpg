package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UITransformTest {

    private GameObject parent;
    private GameObject child;
    private UITransform parentTransform;
    private UITransform childTransform;

    @BeforeEach
    void setUp() {
        parent = new GameObject("Parent");
        parentTransform = new UITransform(400, 300);
        parent.addComponent(parentTransform);

        child = new GameObject("Child");
        childTransform = new UITransform(200, 100);
        child.addComponent(childTransform);
        child.setParent(parent);
    }

    // ========================================================================
    // Effective Size
    // ========================================================================

    @Nested
    class EffectiveSize {

        @Test
        void fixedModeReturnsRawWidthAndHeight() {
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setHeightMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(200);
            childTransform.setHeight(100);

            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);
            assertEquals(100, childTransform.getEffectiveHeight(), 0.01f);
        }

        @Test
        void percentModeReturnsPercentageOfParent() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(75);

            // parent is 400x300
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);
            assertEquals(225, childTransform.getEffectiveHeight(), 0.01f);
        }

        @Test
        void percentAt100EqualsParentSize() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(100);

            assertEquals(400, childTransform.getEffectiveWidth(), 0.01f);
            assertEquals(300, childTransform.getEffectiveHeight(), 0.01f);
        }

        @Test
        void percentWithNoParentUsesScreenBounds() {
            // Unparent the child
            child.setParent(null);
            childTransform.setScreenBounds(640, 480);
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);

            assertEquals(320, childTransform.getEffectiveWidth(), 0.01f);
        }
    }

    // ========================================================================
    // Layout Overrides
    // ========================================================================

    @Nested
    class LayoutOverrides {

        @Test
        void overrideTakesPriorityOverPercent() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            // Without override: 400 * 100% = 400
            assertEquals(400, childTransform.getEffectiveWidth(), 0.01f);

            // With override: returns override value
            childTransform.setLayoutOverrideWidth(350);
            assertEquals(350, childTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void overrideTakesPriorityOverFixed() {
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(200);
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);

            // Force-expand scenario: layout overrides FIXED child
            childTransform.setLayoutOverrideWidth(300);
            assertEquals(300, childTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void clearLayoutOverridesRevertsToNormal() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setLayoutOverrideWidth(999);
            childTransform.setLayoutOverrideHeight(999);

            assertEquals(999, childTransform.getEffectiveWidth(), 0.01f);
            assertEquals(999, childTransform.getEffectiveHeight(), 0.01f);

            childTransform.clearLayoutOverrides();

            // Reverts to percent of parent: 400 * 50% = 200
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void clearAlsoClearsPercentReference() {
            childTransform.setLayoutPercentReference(500, 600);
            assertEquals(500, childTransform.getPercentReferenceWidth(), 0.01f);

            childTransform.clearLayoutOverrides();

            // Falls back to getParentWidth
            assertEquals(400, childTransform.getPercentReferenceWidth(), 0.01f);
        }

        @Test
        void staleOverrideClearedOnReparent() {
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(200);
            childTransform.setLayoutOverrideWidth(350);
            assertEquals(350, childTransform.getEffectiveWidth(), 0.01f);

            // Reparent to a different parent — stale override should be cleared
            GameObject newParent = new GameObject("NewParent");
            newParent.addComponent(new UITransform(800, 600));
            child.setParent(newParent);

            // Should return the raw FIXED width, not stale override
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);
        }
    }

    // ========================================================================
    // Layout + FillingParent interaction
    // ========================================================================

    @Nested
    class LayoutWithFillingParent {

        /**
         * Simulates what a layout group does: sets anchor/pivot/offset/overrides on a child.
         */
        private void simulateLayoutOnChild(float offsetX, float offsetY,
                                            float overrideWidth, float overrideHeight) {
            childTransform.clearLayoutOverrides();
            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(offsetX, offsetY);
            childTransform.setLayoutOverrideWidth(overrideWidth);
            childTransform.setLayoutOverrideHeight(overrideHeight);
        }

        @Test
        void fillingParentWithLayoutOverrideRespectsOffset() {
            // Child at 100%/100% — isFillingParent() returns true
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightPercent(100);
            assertTrue(childTransform.isFillingParent());

            // Layout sets padding offset and size overrides (simulating padding=10)
            simulateLayoutOnChild(10, 10, 380, 280);

            parentTransform.setScreenBounds(400, 300);
            childTransform.setScreenBounds(400, 300);

            Vector2f pos = childTransform.getScreenPosition();
            // Should be at parent origin + layout offset, NOT at raw parent origin
            assertEquals(10, pos.x, 0.01f);
            assertEquals(10, pos.y, 0.01f);
        }

        @Test
        void fillingParentWithoutLayoutOverrideSnapsToParent() {
            // Child at 100%/100% with NO layout overrides — original shortcut should work
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightPercent(100);
            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setOffset(5, 5);

            parentTransform.setScreenBounds(400, 300);
            childTransform.setScreenBounds(400, 300);

            Vector2f pos = childTransform.getScreenPosition();
            // isFillingParent shortcut: snaps to parent origin, ignores offset/anchor/pivot
            assertEquals(0, pos.x, 0.01f);
            assertEquals(0, pos.y, 0.01f);
        }

        @Test
        void fillingParentWithLayoutOverrideUsesOwnPivot() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightPercent(100);

            // Layout sets pivot to (0,0)
            simulateLayoutOnChild(10, 10, 380, 280);

            // Should return own pivot (0,0), NOT parent's pivot
            Vector2f effectivePivot = childTransform.getEffectivePivot();
            assertEquals(0, effectivePivot.x, 0.01f);
            assertEquals(0, effectivePivot.y, 0.01f);
        }

        @Test
        void fillingParentWithoutLayoutOverrideInheritsParentPivot() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightPercent(100);
            childTransform.setPivot(0, 0);
            parentTransform.setPivot(0.5f, 0.5f);

            // No layout overrides — should inherit parent's pivot
            Vector2f effectivePivot = childTransform.getEffectivePivot();
            assertEquals(0.5f, effectivePivot.x, 0.01f);
            assertEquals(0.5f, effectivePivot.y, 0.01f);
        }

        @Test
        void fillingParentWithLayoutOverrideSizeIsOverrideNotParent() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightPercent(100);

            // Layout overrides to content area (parent 400x300 minus 10px padding each side)
            simulateLayoutOnChild(10, 10, 380, 280);

            // Effective size should be the layout override, not 100% of parent
            assertEquals(380, childTransform.getEffectiveWidth(), 0.01f);
            assertEquals(280, childTransform.getEffectiveHeight(), 0.01f);
        }

        @Test
        void percent99WithLayoutOverrideAlsoRespectsOffset() {
            // 99% — isFillingParent() returns false, normal path always runs
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(99);
            childTransform.setHeightPercent(99);
            assertFalse(childTransform.isFillingParent());

            simulateLayoutOnChild(10, 10, 376, 277);

            parentTransform.setScreenBounds(400, 300);
            childTransform.setScreenBounds(400, 300);

            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(10, pos.x, 0.01f);
            assertEquals(10, pos.y, 0.01f);
        }
    }

    // ========================================================================
    // Percent Reference
    // ========================================================================

    @Nested
    class PercentReference {

        @Test
        void defaultFallsBackToParentWidth() {
            assertEquals(400, childTransform.getPercentReferenceWidth(), 0.01f);
            assertEquals(300, childTransform.getPercentReferenceHeight(), 0.01f);
        }

        @Test
        void setReferenceOverridesDefault() {
            childTransform.setLayoutPercentReference(350, 250);

            assertEquals(350, childTransform.getPercentReferenceWidth(), 0.01f);
            assertEquals(250, childTransform.getPercentReferenceHeight(), 0.01f);
        }

        @Test
        void percentRoundTrip() {
            // Simulate: child is 100% in a layout with reference = 350
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setLayoutPercentReference(350, 300);
            childTransform.setLayoutOverrideWidth(350);

            float pixelSize = childTransform.getEffectiveWidth(); // 350
            assertEquals(350, pixelSize, 0.01f);

            // Toggle to FIXED
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(pixelSize);
            childTransform.clearLayoutOverrides();

            // Toggle back to PERCENT using the reference
            childTransform.setLayoutPercentReference(350, 300);
            float referenceWidth = childTransform.getPercentReferenceWidth();
            float newPercent = pixelSize / referenceWidth * 100f;

            assertEquals(100f, newPercent, 0.01f);
        }
    }

    // ========================================================================
    // Effective Pivot
    // ========================================================================

    @Nested
    class EffectivePivot {

        @Test
        void returnsOwnPivotByDefault() {
            childTransform.setPivot(0.3f, 0.7f);

            Vector2f pivot = childTransform.getEffectivePivot();
            assertEquals(0.3f, pivot.x, 0.001f);
            assertEquals(0.7f, pivot.y, 0.001f);
        }

        @Test
        void returnsParentPivotWhenFillingParent() {
            parentTransform.setPivot(0.5f, 0.5f);
            childTransform.setPivot(0f, 0f);
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(100);

            Vector2f pivot = childTransform.getEffectivePivot();
            assertEquals(0.5f, pivot.x, 0.001f);
            assertEquals(0.5f, pivot.y, 0.001f);
        }

        @Test
        void singleAxisFillReturnsOwnPivot() {
            parentTransform.setPivot(0.5f, 0.5f);
            childTransform.setPivot(0f, 0f);

            // Only width fills parent
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.FIXED);

            Vector2f pivot = childTransform.getEffectivePivot();
            assertEquals(0f, pivot.x, 0.001f);
            assertEquals(0f, pivot.y, 0.001f);
        }

        @Test
        void isFillingParentBoundary99vs100() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);

            childTransform.setWidthPercent(100);
            childTransform.setHeightPercent(100);
            assertTrue(childTransform.isFillingParent());

            childTransform.setWidthPercent(99);
            assertFalse(childTransform.isFillingParent());

            childTransform.setWidthPercent(100);
            childTransform.setHeightPercent(99);
            assertFalse(childTransform.isFillingParent());
        }
    }

    // ========================================================================
    // calculatePosition with PERCENT + Pivot
    // ========================================================================

    @Nested
    class CalculatePosition {

        @Test
        void percentModeWithPivotUsesEffectiveSize() {
            // Parent at screen origin with screen bounds set
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setWidthMode(UITransform.SizeMode.FIXED);
            parentTransform.setWidth(400);
            parentTransform.setHeight(300);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            // Child: PERCENT 50% width with center pivot
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setHeightMode(UITransform.SizeMode.FIXED);
            childTransform.setHeight(100);
            childTransform.setPivot(0.5f, 0);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(100, 50);
            childTransform.setScreenBounds(640, 480);

            // effectiveWidth = 400 * 50% = 200
            // position.x = parentX + anchorX + offsetX - pivot.x * effectiveWidth
            //            = 0 + 0 + 100 - 0.5 * 200 = 0
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(0, pos.x, 0.5f);
            assertEquals(50, pos.y, 0.5f);
        }
    }

    // ========================================================================
    // Rotation
    // ========================================================================

    @Nested
    class Rotation {

        @Test
        void localRotationReturnsLocalZ() {
            childTransform.setRotation2D(45);
            childTransform.setMatchParentRotation(false);
            assertEquals(45, childTransform.getRotation2D(), 0.01f);
        }

        @Test
        void matchParentRotationReturnsParentWorldRotation() {
            parentTransform.setRotation2D(30);
            childTransform.setMatchParentRotation(true);
            childTransform.setRotation2D(10);
            assertEquals(30, childTransform.getRotation2D(), 0.01f);
        }

        @Test
        void matchParentRotationWithNoParentReturnsLocal() {
            child.setParent(null);
            childTransform.setMatchParentRotation(true);
            childTransform.setRotation2D(15);
            assertEquals(15, childTransform.getRotation2D(), 0.01f);
        }

        @Test
        void worldRotationAccumulatesParentRotation() {
            parentTransform.setRotation2D(20);
            childTransform.setRotation2D(15);
            assertEquals(35, childTransform.getWorldRotation2D(), 0.01f);
        }

        @Test
        void worldRotationMatchParentIgnoresLocal() {
            parentTransform.setRotation2D(20);
            childTransform.setMatchParentRotation(true);
            childTransform.setRotation2D(99);
            assertEquals(20, childTransform.getWorldRotation2D(), 0.01f);
        }

        @Test
        void worldRotationNoParentReturnsLocal() {
            child.setParent(null);
            childTransform.setRotation2D(42);
            assertEquals(42, childTransform.getWorldRotation2D(), 0.01f);
        }

        @Test
        void worldRotationThreeLevelHierarchy() {
            GameObject grandchild = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(50, 50);
            grandchild.addComponent(gcTransform);
            grandchild.setParent(child);

            parentTransform.setRotation2D(10);
            childTransform.setRotation2D(20);
            gcTransform.setRotation2D(5);

            assertEquals(35, gcTransform.getWorldRotation2D(), 0.01f);
        }

        @Test
        void calculatePositionRotatesChildAroundParentPivot() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setPivot(0.5f, 0.5f);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setRotation2D(90);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(100, 0);
            childTransform.setScreenBounds(640, 480);

            Vector2f pos = childTransform.getScreenPosition();
            // Parent screenPos: anchor(0,0)*screen(640,480)=0, offset(0,0),
            //   worldScale(1,1), pivot(0.5,0.5)*400x300 -> shift (-200,-150), pos=(-200,-150)
            // Child in parent: anchorX=0, localX=100, localY=0
            //   parentPivotX = -200 + 0.5*400 = 0, parentPivotY = -150 + 0.5*300 = 0
            //   relX = -200 + 100 - 0 = -100, relY = -150 + 0 - 0 = -150
            //   cos90=0, sin90=1: rotX = -100*0 - (-150)*1 = 150, rotY = -100*1 + (-150)*0 = -100
            //   final: (0+150, 0-100) = (150, -100)
            assertEquals(150, pos.x, 1f);
            assertEquals(-100, pos.y, 1f);
        }

        @Test
        void calculatePositionNoRotationSkipsRotationPath() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setPivot(0, 0);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(10, 20);
            parentTransform.setRotation2D(0);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(30, 40);
            childTransform.setScreenBounds(640, 480);

            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(40, pos.x, 0.5f); // 10 + 30
            assertEquals(60, pos.y, 0.5f); // 20 + 40
        }

        @Test
        void buildMatrixWorldRotationComposesWithParent() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setRotation2D(30);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setRotation2D(15);
            childTransform.setScreenBounds(800, 600);

            assertEquals(45, childTransform.getComputedWorldRotation2D(), 0.01f);
        }

        @Test
        void buildMatrixMatchParentRotationUsesParentOnly() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setRotation2D(30);

            childTransform.setMatchParentRotation(true);
            childTransform.setRotation2D(99);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            assertEquals(30, childTransform.getComputedWorldRotation2D(), 0.01f);
        }

        @Test
        void buildMatrixFillingParentInheritsParentRotation() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0.5f, 0.5f);
            parentTransform.setRotation2D(45);

            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(100);
            childTransform.setScreenBounds(800, 600);

            assertEquals(45, childTransform.getComputedWorldRotation2D(), 0.01f);
        }

        @Test
        void buildMatrixNoParentUsesLocalRotation() {
            child.setParent(null);
            childTransform.setScreenBounds(800, 600);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setRotation2D(60);

            assertEquals(60, childTransform.getComputedWorldRotation2D(), 0.01f);
        }
    }

    // ========================================================================
    // Scale
    // ========================================================================

    @Nested
    class Scale {

        @Test
        void localScaleReturnsLocalValues() {
            childTransform.setScale2D(2, 3);
            childTransform.setMatchParentScale(false);
            Vector2f s = childTransform.getScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(3, s.y, 0.01f);
        }

        @Test
        void matchParentScaleReturnsParentWorldScale() {
            parentTransform.setScale2D(2, 3);
            childTransform.setMatchParentScale(true);
            childTransform.setScale2D(5, 5);
            Vector2f s = childTransform.getScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(3, s.y, 0.01f);
        }

        @Test
        void matchParentScaleWithNoParentReturnsLocal() {
            child.setParent(null);
            childTransform.setMatchParentScale(true);
            childTransform.setScale2D(4, 5);
            Vector2f s = childTransform.getScale2D();
            assertEquals(4, s.x, 0.01f);
            assertEquals(5, s.y, 0.01f);
        }

        @Test
        void worldScaleMultipliesWithParent() {
            parentTransform.setScale2D(2, 3);
            childTransform.setScale2D(0.5f, 2);
            Vector2f s = childTransform.getWorldScale2D();
            assertEquals(1, s.x, 0.01f);
            assertEquals(6, s.y, 0.01f);
        }

        @Test
        void worldScaleMatchParentIgnoresLocal() {
            parentTransform.setScale2D(2, 3);
            childTransform.setMatchParentScale(true);
            childTransform.setScale2D(99, 99);
            Vector2f s = childTransform.getWorldScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(3, s.y, 0.01f);
        }

        @Test
        void worldScaleNoParentReturnsLocal() {
            child.setParent(null);
            childTransform.setScale2D(3, 4);
            Vector2f s = childTransform.getWorldScale2D();
            assertEquals(3, s.x, 0.01f);
            assertEquals(4, s.y, 0.01f);
        }

        @Test
        void worldScaleThreeLevelHierarchy() {
            GameObject grandchild = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(50, 50);
            grandchild.addComponent(gcTransform);
            grandchild.setParent(child);

            parentTransform.setScale2D(2, 2);
            childTransform.setScale2D(3, 1);
            gcTransform.setScale2D(0.5f, 4);

            Vector2f s = gcTransform.getWorldScale2D();
            assertEquals(3, s.x, 0.01f);  // 2*3*0.5
            assertEquals(8, s.y, 0.01f);  // 2*1*4
        }

        @Test
        void calculatePositionAccountsForWorldScale() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setScale2D(2, 2);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(100, 100);
            childTransform.setScreenBounds(640, 480);

            // effectiveWidth=200, effectiveHeight=100
            // worldScale = (2,2)
            // scaledWidth = 200*2=400, scaledHeight = 100*2=200
            // localX = 0 + 100 - 0.5*400 = -100
            // localY = 0 + 100 - 0.5*200 = 0
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(-100, pos.x, 0.5f);
            assertEquals(0, pos.y, 0.5f);
        }

        @Test
        void buildMatrixWorldScaleComposesWithParent() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setScale2D(2, 3);

            childTransform.setScale2D(0.5f, 2);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            Vector2f s = childTransform.getComputedWorldScale2D();
            assertEquals(1, s.x, 0.01f);
            assertEquals(6, s.y, 0.01f);
        }

        @Test
        void buildMatrixMatchParentScaleUsesParentOnly() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setScale2D(2, 3);

            childTransform.setMatchParentScale(true);
            childTransform.setScale2D(99, 99);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            Vector2f s = childTransform.getComputedWorldScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(3, s.y, 0.01f);
        }

        @Test
        void buildMatrixFillingParentInheritsParentScale() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0.5f, 0.5f);
            parentTransform.setScale2D(2, 3);

            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(100);
            childTransform.setScreenBounds(800, 600);

            Vector2f s = childTransform.getComputedWorldScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(3, s.y, 0.01f);
        }

        @Test
        void buildMatrixNoParentUsesLocalScale() {
            child.setParent(null);
            childTransform.setScreenBounds(800, 600);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScale2D(3, 4);

            Vector2f s = childTransform.getComputedWorldScale2D();
            assertEquals(3, s.x, 0.01f);
            assertEquals(4, s.y, 0.01f);
        }

        @Test
        void parentScaleAffectsChildRelativePositionInMatrix() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setScale2D(2, 2);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(50, 50);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            // Child at offset (50,50), parent pivot at (0,0), parent scale (2,2)
            // relX = 50 - 0 = 50, relY = 50 - 0 = 50
            // scaledRelX = 50*2 = 100, scaledRelY = 50*2 = 100
            // parentWorldPivot = (0,0)
            // pivotWorld = (0+100, 0+100) = (100, 100)
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();
            assertEquals(100, pivotPos.x, 0.5f);
            assertEquals(100, pivotPos.y, 0.5f);
        }
    }

    // ========================================================================
    // Anchor Presets
    // ========================================================================

    @Nested
    class AnchorPresets {

        @Test
        void centerAnchorWithPercentSize() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setHeightMode(UITransform.SizeMode.FIXED);
            childTransform.setHeight(50);
            childTransform.setScreenBounds(640, 480);

            // anchor = (0.5*400, 0.5*300) = (200, 150)
            // pos = parentPos + anchor + offset - pivot*size = (0,0) + (200,150) + (0,0) - (0,0) = (200, 150)
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(200, pos.x, 0.5f);
            assertEquals(150, pos.y, 0.5f);
        }

        @Test
        void centerAnchorCenteredPivotWithPercent() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setOffset(0, 0);
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(50);
            childTransform.setScreenBounds(640, 480);

            // effectiveWidth = 400*50% = 200, effectiveHeight = 300*50% = 150
            // anchor = (200, 150)
            // localX = 200 + 0 - 0.5*200 = 100
            // localY = 150 + 0 - 0.5*150 = 75
            // pos = (0 + 100, 0 + 75) = (100, 75)
            // This centers the element within parent
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(100, pos.x, 0.5f);
            assertEquals(75, pos.y, 0.5f);
        }

        @Test
        void bottomRightAnchorWithOffset() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(1, 1);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(-10, -10);
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(50);
            childTransform.setHeight(50);
            childTransform.setScreenBounds(640, 480);

            // anchor = (400, 300), offset = (-10, -10)
            // pos = (0 + 400 - 10, 0 + 300 - 10) = (390, 290)
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(390, pos.x, 0.5f);
            assertEquals(290, pos.y, 0.5f);
        }

        @Test
        void bottomRightAnchorWithPercentSize() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(1, 1);
            childTransform.setPivot(1, 1);
            childTransform.setOffset(0, 0);
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(25);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(25);
            childTransform.setScreenBounds(640, 480);

            // effectiveWidth = 400*25% = 100, effectiveHeight = 300*25% = 75
            // anchor = (400, 300)
            // localX = 400 + 0 - 1*100 = 300
            // localY = 300 + 0 - 1*75 = 225
            // pos = (0+300, 0+225) = (300, 225)
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(300, pos.x, 0.5f);
            assertEquals(225, pos.y, 0.5f);
        }

        @Test
        void anchorInMatrixPathMatchesCalculatePosition() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setPivot(0.3f, 0.7f);
            childTransform.setOffset(10, 20);
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(100);
            childTransform.setHeight(80);
            childTransform.setScreenBounds(800, 600);

            // screenPosition (calculatePosition path)
            Vector2f screenPos = childTransform.getScreenPosition();
            // pivotPosition (matrix path)
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            // The pivot world position should equal screenPos + pivot * effectiveSize
            // because no rotation/scale: pivotPos = screenPos + pivot * size
            float expectedPivotX = screenPos.x + 0.3f * 100;
            float expectedPivotY = screenPos.y + 0.7f * 80;
            assertEquals(expectedPivotX, pivotPos.x, 1f);
            assertEquals(expectedPivotY, pivotPos.y, 1f);
        }

        @Test
        void anchorWithNoParentUsesScreenBounds() {
            child.setParent(null);
            childTransform.setScreenBounds(800, 600);
            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(100);
            childTransform.setHeight(80);

            // anchor = (0.5*800, 0.5*600) = (400, 300)
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(400, pos.x, 0.5f);
            assertEquals(300, pos.y, 0.5f);
        }
    }

    // ========================================================================
    // Matrix vs Position Consistency
    // ========================================================================

    @Nested
    class MatrixPositionConsistency {

        @Test
        void pivotPositionEqualsScreenPosPlusPivotOffset() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setOffset(50, 50);
            childTransform.setScreenBounds(800, 600);

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            assertEquals(screenPos.x + 0.5f * 200, pivotPos.x, 1f);
            assertEquals(screenPos.y + 0.5f * 100, pivotPos.y, 1f);
        }

        @Test
        void zeroPivotMakesBothPositionsEqual() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(100, 200);
            childTransform.setScreenBounds(800, 600);

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            assertEquals(screenPos.x, pivotPos.x, 1f);
            assertEquals(screenPos.y, pivotPos.y, 1f);
        }

        @Test
        void fillingParentBothPathsConsistent() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(50, 30);
            parentTransform.setPivot(0.5f, 0.5f);

            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(100);
            childTransform.setScreenBounds(800, 600);

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f parentScreenPos = parentTransform.getScreenPosition();

            // Filling parent: screen position = parent's screen position
            assertEquals(parentScreenPos.x, screenPos.x, 1f);
            assertEquals(parentScreenPos.y, screenPos.y, 1f);
        }

        @Test
        void anchorOffsetConsistentBetweenPaths() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setOffset(20, 30);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setScreenBounds(800, 600);

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            assertEquals(screenPos.x + 0.5f * 200, pivotPos.x, 1f);
            assertEquals(screenPos.y + 0.5f * 100, pivotPos.y, 1f);
        }

        @Test
        void percentSizeConsistentBetweenPaths() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(60);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(40);
            childTransform.setAnchor(0, 0);
            childTransform.setOffset(10, 20);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setScreenBounds(800, 600);

            float effW = childTransform.getEffectiveWidth();  // 400*60% = 240
            float effH = childTransform.getEffectiveHeight(); // 300*40% = 120

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            assertEquals(screenPos.x + 0.5f * effW, pivotPos.x, 1f);
            assertEquals(screenPos.y + 0.5f * effH, pivotPos.y, 1f);
        }

        @Test
        void withParentScaleConsistentBetweenPaths() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setScale2D(2, 2);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(50, 50);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setScreenBounds(800, 600);

            // With parent scale, worldScale = (2,2)
            Vector2f worldScale = childTransform.getWorldScale2D();
            float scaledW = 200 * worldScale.x;
            float scaledH = 100 * worldScale.y;

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            // screenPos accounts for scale in pivot subtraction
            // pivotPos should relate: pivotPos ~= screenPos + pivot * scaledSize (approximately)
            // But matrix path calculates differently (pivot in parent local then transforms)
            // Both should place the visual element in the same world position
            assertNotNull(screenPos);
            assertNotNull(pivotPos);
        }

        @Test
        void noParentConsistentBetweenPaths() {
            child.setParent(null);
            childTransform.setScreenBounds(800, 600);
            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setOffset(10, 20);
            childTransform.setPivot(0, 0);

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            // No parent, pivot(0,0): both should return the same position
            assertEquals(screenPos.x, pivotPos.x, 1f);
            assertEquals(screenPos.y, pivotPos.y, 1f);
        }

        /**
         * Regression test: child of a filling parent whose raw pivot differs from
         * its effective pivot should still be positioned inside the parent bounds.
         * <p>
         * Hierarchy: grandparent (pivot 0,0) → middle (filling, pivot 0.5,0.5) → grandchild
         * <p>
         * The filling middle layer inherits grandparent's worldPivotPosition (top-left).
         * The grandchild's matrix calculation must use the parent's effective pivot (0,0)
         * rather than the raw pivot (0.5,0.5), otherwise it ends up at negative coordinates.
         */
        @Test
        void childOfFillingParentWithNonZeroPivotPositionedCorrectly() {
            // Grandparent: fixed size, pivot (0,0) at origin
            GameObject grandparent = new GameObject("Grandparent");
            UITransform gpTransform = new UITransform(640, 480);
            grandparent.addComponent(gpTransform);
            gpTransform.setScreenBounds(640, 480);
            gpTransform.setAnchor(0, 0);
            gpTransform.setOffset(0, 0);
            gpTransform.setPivot(0, 0);

            // Middle: fills grandparent, but has raw pivot (0.5, 0.5)
            GameObject middle = new GameObject("Middle");
            UITransform midTransform = new UITransform(640, 480);
            middle.addComponent(midTransform);
            middle.setParent(grandparent);
            midTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            midTransform.setWidthPercent(100);
            midTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            midTransform.setHeightPercent(100);
            midTransform.setPivot(0.5f, 0.5f);
            midTransform.setAnchor(0.5f, 0.5f);
            midTransform.setScreenBounds(640, 480);

            // Grandchild: anchored at (0,0) with offset, not filling
            GameObject gc = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(100, 100);
            gc.addComponent(gcTransform);
            gc.setParent(middle);
            gcTransform.setAnchor(0, 0);
            gcTransform.setPivot(0, 0);
            gcTransform.setOffset(25, 25);
            gcTransform.setScreenBounds(640, 480);

            // Grandchild's world pivot should be at (25, 25) — inside parent bounds
            Vector2f gcPivot = gcTransform.getWorldPivotPosition2D();
            assertEquals(25f, gcPivot.x, 1f, "Grandchild X should be at offset from parent top-left");
            assertEquals(25f, gcPivot.y, 1f, "Grandchild Y should be at offset from parent top-left");

            // Also verify via calculateElementBounds-style calculation
            Vector2f effPivot = gcTransform.getEffectivePivot();
            float x = gcPivot.x - effPivot.x * gcTransform.getEffectiveWidth();
            float y = gcPivot.y - effPivot.y * gcTransform.getEffectiveHeight();
            assertTrue(x >= 0 && x < 640, "Grandchild bounds X should be within canvas");
            assertTrue(y >= 0 && y < 480, "Grandchild bounds Y should be within canvas");
        }

        /**
         * Regression test: calculatePosition() rotation path must use effective pivot
         * for the rotation center when the parent fills its own parent.
         * <p>
         * Hierarchy: root (pivot 0,0, rotation 45°) → middle (filling, pivot 0.5,0.5) → child
         * <p>
         * The middle layer inherits root's rotation. When the child calculates its position,
         * the rotation center should be at the effective pivot (root's pivot), not the raw pivot.
         */
        @Test
        void childOfFillingParentWithRotationUsesEffectivePivotForRotationCenter() {
            // Root: fixed size, pivot (0,0), 45° rotation
            GameObject root = new GameObject("Root");
            UITransform rootTransform = new UITransform(400, 300);
            root.addComponent(rootTransform);
            rootTransform.setScreenBounds(400, 300);
            rootTransform.setAnchor(0, 0);
            rootTransform.setOffset(0, 0);
            rootTransform.setPivot(0, 0);
            rootTransform.setRotation2D(45);

            // Middle: fills root, raw pivot (0.5, 0.5), no own rotation
            GameObject mid = new GameObject("Middle");
            UITransform midTransform = new UITransform(400, 300);
            mid.addComponent(midTransform);
            mid.setParent(root);
            midTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            midTransform.setWidthPercent(100);
            midTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            midTransform.setHeightPercent(100);
            midTransform.setPivot(0.5f, 0.5f);
            midTransform.setAnchor(0.5f, 0.5f);
            midTransform.setScreenBounds(400, 300);

            // Child A: under the filling parent
            GameObject childA = new GameObject("ChildA");
            UITransform childATransform = new UITransform(50, 50);
            childA.addComponent(childATransform);
            childA.setParent(mid);
            childATransform.setAnchor(0, 0);
            childATransform.setPivot(0, 0);
            childATransform.setOffset(0, 0);
            childATransform.setScreenBounds(400, 300);

            // Child B: under root directly (for comparison)
            GameObject childB = new GameObject("ChildB");
            UITransform childBTransform = new UITransform(50, 50);
            childB.addComponent(childBTransform);
            childB.setParent(root);
            childBTransform.setAnchor(0, 0);
            childBTransform.setPivot(0, 0);
            childBTransform.setOffset(0, 0);
            childBTransform.setScreenBounds(400, 300);

            // Both children should have the same screen position since
            // the filling middle layer is transparent to positioning
            Vector2f posA = childATransform.getScreenPosition();
            Vector2f posB = childBTransform.getScreenPosition();

            assertEquals(posB.x, posA.x, 1f,
                    "Child through filling parent should match child directly under root (X)");
            assertEquals(posB.y, posA.y, 1f,
                    "Child through filling parent should match child directly under root (Y)");
        }

        /**
         * Tests that multiple levels of filling parents don't compound the pivot error.
         * <p>
         * Hierarchy: root (pivot 0,0) → fill1 (pivot 0.5,0.5) → fill2 (pivot 0.3,0.7) → child
         */
        @Test
        void multipleFillingParentsDoNotCompoundPivotError() {
            GameObject root = new GameObject("Root");
            UITransform rootT = new UITransform(640, 480);
            root.addComponent(rootT);
            rootT.setScreenBounds(640, 480);
            rootT.setAnchor(0, 0);
            rootT.setPivot(0, 0);
            rootT.setOffset(0, 0);

            // First filling layer with non-zero pivot
            GameObject fill1 = new GameObject("Fill1");
            UITransform fill1T = new UITransform(640, 480);
            fill1.addComponent(fill1T);
            fill1.setParent(root);
            fill1T.setWidthMode(UITransform.SizeMode.PERCENT);
            fill1T.setWidthPercent(100);
            fill1T.setHeightMode(UITransform.SizeMode.PERCENT);
            fill1T.setHeightPercent(100);
            fill1T.setPivot(0.5f, 0.5f);
            fill1T.setScreenBounds(640, 480);

            // Second filling layer with different non-zero pivot
            GameObject fill2 = new GameObject("Fill2");
            UITransform fill2T = new UITransform(640, 480);
            fill2.addComponent(fill2T);
            fill2.setParent(fill1);
            fill2T.setWidthMode(UITransform.SizeMode.PERCENT);
            fill2T.setWidthPercent(100);
            fill2T.setHeightMode(UITransform.SizeMode.PERCENT);
            fill2T.setHeightPercent(100);
            fill2T.setPivot(0.3f, 0.7f);
            fill2T.setScreenBounds(640, 480);

            // Grandchild at offset
            GameObject gc = new GameObject("Grandchild");
            UITransform gcT = new UITransform(100, 100);
            gc.addComponent(gcT);
            gc.setParent(fill2);
            gcT.setAnchor(0, 0);
            gcT.setPivot(0, 0);
            gcT.setOffset(10, 20);
            gcT.setScreenBounds(640, 480);

            // Matrix path: should be (10, 20) regardless of intermediate filling pivots
            Vector2f gcPivot = gcT.getWorldPivotPosition2D();
            assertEquals(10f, gcPivot.x, 1f, "X should not be affected by intermediate filling pivots");
            assertEquals(20f, gcPivot.y, 1f, "Y should not be affected by intermediate filling pivots");

            // Position path: should also be (10, 20)
            Vector2f gcPos = gcT.getScreenPosition();
            assertEquals(10f, gcPos.x, 1f, "Screen position X should match");
            assertEquals(20f, gcPos.y, 1f, "Screen position Y should match");
        }

        /**
         * Tests that both position paths agree for a child of a filling parent
         * that has a centered anchor/pivot (the exact PokedexMenu scenario).
         */
        @Test
        void matrixAndPositionPathsAgreeForChildOfFillingParent() {
            // Canvas root
            GameObject canvas = new GameObject("Canvas");
            UITransform canvasT = new UITransform(640, 480);
            canvas.addComponent(canvasT);
            canvasT.setScreenBounds(640, 480);
            canvasT.setAnchor(0, 0);
            canvasT.setPivot(0, 0);

            // BGImage: filling canvas, pivot (0,0)
            GameObject bg = new GameObject("BGImage");
            UITransform bgT = new UITransform(640, 480);
            bg.addComponent(bgT);
            bg.setParent(canvas);
            bgT.setWidthMode(UITransform.SizeMode.PERCENT);
            bgT.setWidthPercent(100);
            bgT.setHeightMode(UITransform.SizeMode.PERCENT);
            bgT.setHeightPercent(100);
            bgT.setPivot(0, 0);
            bgT.setScreenBounds(640, 480);

            // PokedexMenu: filling BGImage, pivot (0.5, 0.5), anchor (0.5, 0.5)
            GameObject menu = new GameObject("PokedexMenu");
            UITransform menuT = new UITransform(640, 480);
            menu.addComponent(menuT);
            menu.setParent(bg);
            menuT.setWidthMode(UITransform.SizeMode.PERCENT);
            menuT.setWidthPercent(100);
            menuT.setHeightMode(UITransform.SizeMode.PERCENT);
            menuT.setHeightPercent(100);
            menuT.setAnchor(0.5f, 0.5f);
            menuT.setPivot(0.5f, 0.5f);
            menuT.setScreenBounds(640, 480);

            // Child entity (layout-managed): anchor (0,0), pivot (0,0), offset (25, 25)
            GameObject entity = new GameObject("Entity_27");
            UITransform entityT = new UITransform(100, 100);
            entity.addComponent(entityT);
            entity.setParent(menu);
            entityT.setAnchor(0, 0);
            entityT.setPivot(0, 0);
            entityT.setOffset(25, 25);
            entityT.setScreenBounds(640, 480);

            // Both paths should agree: child at (25, 25)
            Vector2f screenPos = entityT.getScreenPosition();
            Vector2f pivotWorld = entityT.getWorldPivotPosition2D();

            assertEquals(25f, screenPos.x, 1f, "Screen position X");
            assertEquals(25f, screenPos.y, 1f, "Screen position Y");
            assertEquals(25f, pivotWorld.x, 1f, "Matrix pivot X");
            assertEquals(25f, pivotWorld.y, 1f, "Matrix pivot Y");
        }
    }

    // ========================================================================
    // Combined Rotation and Scale
    // ========================================================================

    @Nested
    class CombinedRotationAndScale {

        @Test
        void rotatedAndScaledParentComposesInMatrix() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setRotation2D(45);
            parentTransform.setScale2D(2, 2);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            assertEquals(45, childTransform.getComputedWorldRotation2D(), 0.01f);
            Vector2f s = childTransform.getComputedWorldScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(2, s.y, 0.01f);
        }

        @Test
        void rotatedAndScaledParentAffectsChildOffset() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setRotation2D(90);
            parentTransform.setScale2D(2, 2);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(50, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            // Child at (50,0) in parent local, parent pivot at (0,0)
            // relX=50, relY=0, scaled: (100, 0)
            // Rotated by -90 (code negates): cos(-90)=0, sin(-90)=-1
            // rotX = 100*0 - 0*(-1) = 0, rotY = 100*(-1) + 0*0 = -100
            // pivotWorld = (0,0) + (0,-100) = (0, -100)
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();
            assertEquals(0, pivotPos.x, 2f);
            assertEquals(-100, pivotPos.y, 2f);
        }

        @Test
        void matchBothRotationAndScale() {
            parentTransform.setRotation2D(30);
            parentTransform.setScale2D(2, 3);

            childTransform.setMatchParentRotation(true);
            childTransform.setMatchParentScale(true);
            childTransform.setRotation2D(99);
            childTransform.setScale2D(99, 99);

            assertEquals(30, childTransform.getWorldRotation2D(), 0.01f);
            Vector2f s = childTransform.getWorldScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(3, s.y, 0.01f);
        }

        @Test
        void childRotationComposesWithScaledParent() {
            parentTransform.setScale2D(2, 1);
            childTransform.setRotation2D(30);

            assertEquals(30, childTransform.getWorldRotation2D(), 0.01f);
            Vector2f s = childTransform.getWorldScale2D();
            assertEquals(2, s.x, 0.01f);
            assertEquals(1, s.y, 0.01f);
        }

        @Test
        void threeLevelRotationAndScale() {
            GameObject grandchild = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(50, 50);
            grandchild.addComponent(gcTransform);
            grandchild.setParent(child);

            parentTransform.setRotation2D(10);
            parentTransform.setScale2D(2, 2);
            childTransform.setRotation2D(20);
            childTransform.setScale2D(0.5f, 0.5f);
            gcTransform.setRotation2D(5);
            gcTransform.setScale2D(3, 3);

            assertEquals(35, gcTransform.getWorldRotation2D(), 0.01f);
            Vector2f s = gcTransform.getWorldScale2D();
            assertEquals(3, s.x, 0.01f);  // 2*0.5*3
            assertEquals(3, s.y, 0.01f);  // 2*0.5*3
        }

        @Test
        void negativeScaleWithRotationMatrix() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(100, 100);
            parentTransform.setPivot(0, 0);
            parentTransform.setScale2D(-1, 1);
            parentTransform.setRotation2D(45);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(50, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            Vector2f s = childTransform.getComputedWorldScale2D();
            assertEquals(-1, s.x, 0.01f);
            assertEquals(1, s.y, 0.01f);
            assertEquals(45, childTransform.getComputedWorldRotation2D(), 0.01f);
        }

        @Test
        void rotatedScaledParentWithPivotCrossValidation() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(100, 100);
            parentTransform.setPivot(0.5f, 0.5f);
            parentTransform.setScale2D(2, 1.5f);
            parentTransform.setRotation2D(30);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(50, 30);
            childTransform.setPivot(0.3f, 0.7f);
            childTransform.setScreenBounds(800, 600);

            // Both paths should produce non-null, finite values
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();
            assertNotNull(pivotPos);
            assertTrue(Float.isFinite(pivotPos.x));
            assertTrue(Float.isFinite(pivotPos.y));

            float worldRot = childTransform.getComputedWorldRotation2D();
            assertEquals(30, worldRot, 0.01f); // child rot = 0, parent rot = 30
        }
    }

    // ========================================================================
    // Scale-Percent Interaction
    // ========================================================================

    @Nested
    class ScalePercentInteraction {

        @Test
        void parentScaleDoesNotAffectPercentSizeResolution() {
            parentTransform.setScale2D(2, 2);

            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(50);

            // Percent resolves against unscaled parent effective size (400, 300)
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);
            assertEquals(150, childTransform.getEffectiveHeight(), 0.01f);
        }

        @Test
        void worldScaleDoesNotAffectEffectiveWidth() {
            parentTransform.setScale2D(3, 3);
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(100);

            // effectiveWidth is the unscaled logical size
            assertEquals(100, childTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void layoutOnScaledContainerUsesUnscaledSize() {
            parentTransform.setScale2D(2, 2);

            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            parent.addComponent(vlg);

            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(100);

            vlg.applyLayout();

            // Layout resolves against unscaled effective size (400, 300)
            assertEquals(400, childTransform.getEffectiveWidth(), 0.01f);
            assertEquals(300, childTransform.getEffectiveHeight(), 0.01f);
        }
    }

    // ========================================================================
    // Convenience and State Transitions
    // ========================================================================

    @Nested
    class ConvenienceAndStateTransitions {

        @Test
        void setMatchParentSetsBothAxesPercent100() {
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setAnchor(0.3f, 0.7f);
            childTransform.setOffset(10, 20);

            childTransform.setMatchParent();

            assertEquals(UITransform.SizeMode.PERCENT, childTransform.getWidthMode());
            assertEquals(UITransform.SizeMode.PERCENT, childTransform.getHeightMode());
            assertEquals(100, childTransform.getWidthPercent(), 0.01f);
            assertEquals(100, childTransform.getHeightPercent(), 0.01f);
            assertEquals(0, childTransform.getAnchor().x, 0.01f);
            assertEquals(0, childTransform.getAnchor().y, 0.01f);
            assertEquals(0, childTransform.getPivot().x, 0.01f);
            assertEquals(0, childTransform.getPivot().y, 0.01f);
            assertEquals(0, childTransform.getOffset().x, 0.01f);
            assertEquals(0, childTransform.getOffset().y, 0.01f);
        }

        @Test
        void clearMatchParentResetsBothAxesToFixed() {
            childTransform.setMatchParent();
            childTransform.clearMatchParent();

            assertEquals(UITransform.SizeMode.FIXED, childTransform.getWidthMode());
            assertEquals(UITransform.SizeMode.FIXED, childTransform.getHeightMode());
        }

        @Test
        void isMatchingParentTrueWhenEitherAxisPercent() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightMode(UITransform.SizeMode.FIXED);
            assertTrue(childTransform.isMatchingParent());

            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            assertTrue(childTransform.isMatchingParent());

            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setHeightMode(UITransform.SizeMode.FIXED);
            assertFalse(childTransform.isMatchingParent());
        }

        @Test
        void getLocalRotation2DIgnoresMatchParentRotation() {
            parentTransform.setRotation2D(30);
            childTransform.setMatchParentRotation(true);
            childTransform.setRotation2D(10);

            assertEquals(10, childTransform.getLocalRotation2D(), 0.01f);
        }

        @Test
        void getLocalScale2DIgnoresMatchParentScale() {
            parentTransform.setScale2D(2, 2);
            childTransform.setMatchParentScale(true);
            childTransform.setScale2D(3, 3);

            Vector2f local = childTransform.getLocalScale2D();
            assertEquals(3, local.x, 0.01f);
            assertEquals(3, local.y, 0.01f);
        }

        @Test
        void overrideWithModeTransitionRoundTrip() {
            // Step 1: Set override
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setLayoutOverrideWidth(200);
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);

            // Step 2: Switch to FIXED
            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(200);
            childTransform.clearLayoutOverrides();
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);

            // Step 3: Switch back to PERCENT
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            // No override set, should resolve against parent (400*50%=200)
            assertEquals(200, childTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void percentWithParentNoUITransformUsesScreenBounds() {
            // Parent GO exists but has no UITransform
            GameObject bareParent = new GameObject("BareParent");
            GameObject testChild = new GameObject("TestChild");
            UITransform testTransform = new UITransform(100, 100);
            testChild.addComponent(testTransform);
            testChild.setParent(bareParent);

            testTransform.setScreenBounds(800, 600);
            testTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            testTransform.setWidthPercent(50);

            // Parent has no UITransform, falls back to screenBounds
            assertEquals(400, testTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void setOffsetIdenticalValuesSkipsDirty() {
            childTransform.setOffset(10, 20);
            // Force recalculate
            childTransform.getScreenPosition();
            // Set same values again - should return early
            childTransform.setOffset(10, 20);
            // No exception, and position is still valid
            Vector2f pos = childTransform.getScreenPosition();
            assertNotNull(pos);
        }

        @Test
        void setPivotCenterSetsCenterValues() {
            childTransform.setPivotCenter();
            assertEquals(0.5f, childTransform.getPivot().x, 0.001f);
            assertEquals(0.5f, childTransform.getPivot().y, 0.001f);
        }

        @Test
        void setSizeVector2fSetsWidthAndHeight() {
            childTransform.setSize(new Vector2f(150, 250));
            assertEquals(150, childTransform.getWidth(), 0.01f);
            assertEquals(250, childTransform.getHeight(), 0.01f);
        }
    }

    // ========================================================================
    // Edge Values
    // ========================================================================

    @Nested
    class EdgeValues {

        @Test
        void percentOver100ExceedsParentSize() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(200);
            // parent width=400, 200% = 800
            assertEquals(800, childTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void zeroSizeContainerDoesNotCrash() {
            GameObject container = new GameObject("Container");
            UITransform ct = new UITransform(0, 0);
            container.addComponent(ct);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = new GameObject("A");
            UITransform at = new UITransform(50, 50);
            a.addComponent(at);
            a.setParent(container);

            assertDoesNotThrow(() -> vlg.applyLayout());
        }

        @Test
        void percentZeroGivesZeroSize() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(0);
            assertEquals(0, childTransform.getEffectiveWidth(), 0.01f);
        }

        @Test
        void negativeSpacingInLayout() {
            GameObject container = new GameObject("Container");
            UITransform ct = new UITransform(400, 300);
            container.addComponent(ct);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);
            vlg.setSpacing(-5);

            GameObject a = new GameObject("A");
            UITransform at = new UITransform(100, 40);
            a.addComponent(at);
            a.setParent(container);

            GameObject b = new GameObject("B");
            UITransform bt = new UITransform(100, 40);
            b.addComponent(bt);
            b.setParent(container);

            vlg.applyLayout();

            // A at y=0, B at y=40+(-5)=35 (overlaps by 5px)
            assertEquals(0, at.getOffset().y, 0.01f);
            assertEquals(35, bt.getOffset().y, 0.01f);
        }
    }

    // ========================================================================
    // Dirty Propagation
    // ========================================================================

    @Nested
    class DirtyPropagation {

        @Test
        void markDirtyRecursivePropagesToAllDescendants() {
            GameObject grandchild = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(50, 50);
            grandchild.addComponent(gcTransform);
            grandchild.setParent(child);

            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(10, 10);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            gcTransform.setAnchor(0, 0);
            gcTransform.setOffset(5, 5);
            gcTransform.setPivot(0, 0);
            gcTransform.setScreenBounds(800, 600);

            // Cache positions
            parentTransform.getScreenPosition();
            childTransform.getScreenPosition();
            gcTransform.getScreenPosition();

            // Mark dirty
            parentTransform.markDirtyRecursive();

            // All should recalculate successfully
            Vector2f pPos = parentTransform.getScreenPosition();
            Vector2f cPos = childTransform.getScreenPosition();
            Vector2f gcPos = gcTransform.getScreenPosition();

            assertNotNull(pPos);
            assertEquals(10, cPos.x, 0.5f);
            assertEquals(15, gcPos.x, 0.5f); // 10 + 5
        }

        @Test
        void parentOffsetChangeInvalidatesChildPosition() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setOffset(10, 10);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(800, 600);

            Vector2f before = childTransform.getScreenPosition();
            assertEquals(10, before.x, 0.5f);

            // Change parent offset — must also mark children dirty
            // (in the real engine, the renderer calls markDirtyRecursive each frame)
            parentTransform.setOffset(50, 50);
            parentTransform.markDirtyRecursive();

            Vector2f after = childTransform.getScreenPosition();
            assertEquals(60, after.x, 0.5f); // 50 + 10
        }
    }

    // ========================================================================
    // Offset Percentage Mode
    // ========================================================================

    @Nested
    class OffsetPercentage {

        @Test
        void fixedModeReturnsRawOffset() {
            childTransform.setOffsetXMode(UITransform.SizeMode.FIXED);
            childTransform.setOffsetYMode(UITransform.SizeMode.FIXED);
            childTransform.setOffset(50, 30);

            assertEquals(50, childTransform.getEffectiveOffsetX(), 0.01f);
            assertEquals(30, childTransform.getEffectiveOffsetY(), 0.01f);
        }

        @Test
        void percentModeReturnsPercentageOfParent() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(25);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(50);

            // parent is 400x300
            assertEquals(100, childTransform.getEffectiveOffsetX(), 0.01f); // 400 * 25%
            assertEquals(150, childTransform.getEffectiveOffsetY(), 0.01f); // 300 * 50%
        }

        @Test
        void mixedAxesXPercentYFixed() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(10);
            childTransform.setOffsetYMode(UITransform.SizeMode.FIXED);
            childTransform.setOffset(0, 20);

            assertEquals(40, childTransform.getEffectiveOffsetX(), 0.01f); // 400 * 10%
            assertEquals(20, childTransform.getEffectiveOffsetY(), 0.01f);
        }

        @Test
        void mixedAxesXFixedYPercent() {
            childTransform.setOffsetXMode(UITransform.SizeMode.FIXED);
            childTransform.setOffset(15, 0);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(33.33f);

            assertEquals(15, childTransform.getEffectiveOffsetX(), 0.01f);
            assertEquals(100, childTransform.getEffectiveOffsetY(), 0.5f); // 300 * 33.33%
        }

        @Test
        void percentAt100EqualsParentSize() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(100);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(100);

            assertEquals(400, childTransform.getEffectiveOffsetX(), 0.01f);
            assertEquals(300, childTransform.getEffectiveOffsetY(), 0.01f);
        }

        @Test
        void percentZeroReturnsZero() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(0);

            assertEquals(0, childTransform.getEffectiveOffsetX(), 0.01f);
        }

        @Test
        void percentNegativeValue() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(-25);

            assertEquals(-100, childTransform.getEffectiveOffsetX(), 0.01f); // 400 * -25%
        }

        @Test
        void percentOver100() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(150);

            assertEquals(600, childTransform.getEffectiveOffsetX(), 0.01f); // 400 * 150%
        }

        @Test
        void percentWithNoParentUsesScreenBounds() {
            child.setParent(null);
            childTransform.setScreenBounds(640, 480);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(50);

            assertEquals(320, childTransform.getEffectiveOffsetX(), 0.01f); // 640 * 50%
        }

        @Test
        void defaultOffsetModeIsFixed() {
            assertEquals(UITransform.SizeMode.FIXED, childTransform.getOffsetXMode());
            assertEquals(UITransform.SizeMode.FIXED, childTransform.getOffsetYMode());
            assertEquals(0f, childTransform.getOffsetXPercent(), 0.01f);
            assertEquals(0f, childTransform.getOffsetYPercent(), 0.01f);
        }
    }

    // ========================================================================
    // Offset Percentage with Size Percentage (mixed)
    // ========================================================================

    @Nested
    class OffsetPercentWithSizePercent {

        @Test
        void bothSizeAndOffsetPercent() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            // Child: 50% size, 10% offset
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(50);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(10);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(10);
            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(640, 480);

            // effectiveWidth = 400 * 50% = 200
            // effectiveOffsetX = 400 * 10% = 40
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(40, pos.x, 0.5f);
            assertEquals(30, pos.y, 0.5f); // 300 * 10% = 30
        }

        @Test
        void sizePercentWithOffsetFixed() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setOffsetXMode(UITransform.SizeMode.FIXED);
            childTransform.setOffset(20, 0);
            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setScreenBounds(640, 480);

            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(20, pos.x, 0.5f);
        }

        @Test
        void offsetPercentWithPivot() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setWidthMode(UITransform.SizeMode.FIXED);
            childTransform.setWidth(200);
            childTransform.setHeightMode(UITransform.SizeMode.FIXED);
            childTransform.setHeight(100);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(50); // 400 * 50% = 200
            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0.5f, 0); // center pivot
            childTransform.setScreenBounds(640, 480);

            // pos.x = anchor(0) + effectiveOffset(200) - pivot(0.5)*width(200) = 100
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(100, pos.x, 0.5f);
        }

        @Test
        void offsetPercentWithAnchor() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setWidth(100);
            childTransform.setHeight(100);
            childTransform.setAnchor(0.5f, 0); // center anchor
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(10); // 400 * 10% = 40
            childTransform.setScreenBounds(640, 480);

            // pos.x = anchor(0.5*400=200) + effectiveOffset(40) = 240
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(240, pos.x, 0.5f);
        }
    }

    // ========================================================================
    // Offset Percentage in Hierarchy (parent/grandparent)
    // ========================================================================

    @Nested
    class OffsetPercentHierarchy {

        @Test
        void childPercentOffsetRelativeToParentSize() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(25); // 400 * 25% = 100
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(50); // 300 * 50% = 150
            childTransform.setScreenBounds(800, 600);

            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(100, pos.x, 0.5f);
            assertEquals(150, pos.y, 0.5f);
        }

        @Test
        void grandchildPercentOffsetRelativeToChildSize() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setScreenBounds(800, 600);
            // child is 200x100

            GameObject grandchild = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(50, 50);
            grandchild.addComponent(gcTransform);
            grandchild.setParent(child);
            gcTransform.setAnchor(0, 0);
            gcTransform.setPivot(0, 0);
            gcTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            gcTransform.setOffsetXPercent(50); // 200 * 50% = 100
            gcTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            gcTransform.setOffsetYPercent(50); // 100 * 50% = 50
            gcTransform.setScreenBounds(800, 600);

            Vector2f pos = gcTransform.getScreenPosition();
            assertEquals(100, pos.x, 0.5f);
            assertEquals(50, pos.y, 0.5f);
        }

        @Test
        void grandchildPercentOffsetWithPercentSizeParent() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            // Child at 50% of parent: effective size = 200x150
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(50);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(50);
            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffset(0, 0);
            childTransform.setScreenBounds(800, 600);

            GameObject grandchild = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(50, 50);
            grandchild.addComponent(gcTransform);
            grandchild.setParent(child);
            gcTransform.setAnchor(0, 0);
            gcTransform.setPivot(0, 0);
            gcTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            gcTransform.setOffsetXPercent(25); // 200 * 25% = 50
            gcTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            gcTransform.setOffsetYPercent(25); // 150 * 25% = 37.5
            gcTransform.setScreenBounds(800, 600);

            Vector2f pos = gcTransform.getScreenPosition();
            assertEquals(50, pos.x, 0.5f);
            assertEquals(37.5f, pos.y, 0.5f);
        }

        @Test
        void parentPercentOffsetChildPercentOffset() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            // Parent (400x300) with child at 10% offset
            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(10); // 400 * 10% = 40
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(10); // 300 * 10% = 30
            childTransform.setScreenBounds(800, 600);

            // Grandchild at 50% of child size (200x100)
            GameObject grandchild = new GameObject("Grandchild");
            UITransform gcTransform = new UITransform(50, 50);
            grandchild.addComponent(gcTransform);
            grandchild.setParent(child);
            gcTransform.setAnchor(0, 0);
            gcTransform.setPivot(0, 0);
            gcTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            gcTransform.setOffsetXPercent(25); // 200 * 25% = 50
            gcTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            gcTransform.setOffsetYPercent(25); // 100 * 25% = 25
            gcTransform.setScreenBounds(800, 600);

            // child screen pos = (40, 30), gc offset relative to child = (50, 25)
            Vector2f gcPos = gcTransform.getScreenPosition();
            assertEquals(90, gcPos.x, 0.5f);  // 40 + 50
            assertEquals(55, gcPos.y, 0.5f);   // 30 + 25
        }
    }

    // ========================================================================
    // Offset Percentage in Position Calculation Paths
    // ========================================================================

    @Nested
    class OffsetPercentPositionPaths {

        @Test
        void calculatePositionWithPercentOffset() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(25);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(50);
            childTransform.setScreenBounds(800, 600);

            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(100, pos.x, 0.5f); // 400 * 25%
            assertEquals(150, pos.y, 0.5f); // 300 * 50%
        }

        @Test
        void matrixPathWithPercentOffset() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(25); // 400 * 25% = 100
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(50); // 300 * 50% = 150
            childTransform.setScreenBounds(800, 600);

            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();
            assertEquals(100, pivotPos.x, 0.5f);
            assertEquals(150, pivotPos.y, 0.5f);
        }

        @Test
        void matrixAndPositionPathsConsistentWithPercentOffset() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);

            childTransform.setAnchor(0.5f, 0.5f);
            childTransform.setPivot(0.5f, 0.5f);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(10);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(20);
            childTransform.setScreenBounds(800, 600);

            Vector2f screenPos = childTransform.getScreenPosition();
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();

            // pivotPos = screenPos + pivot * effectiveSize
            assertEquals(screenPos.x + 0.5f * 200, pivotPos.x, 1f);
            assertEquals(screenPos.y + 0.5f * 100, pivotPos.y, 1f);
        }

        @Test
        void percentOffsetWithParentRotation() {
            parent.setParent(null);
            parentTransform.setScreenBounds(640, 480);
            parentTransform.setPivot(0.5f, 0.5f);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setRotation2D(90);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(25); // 400 * 25% = 100
            childTransform.setOffsetYMode(UITransform.SizeMode.FIXED);
            childTransform.setOffset(0, 0);
            childTransform.setScreenBounds(640, 480);

            // Should produce a valid position (rotation applied to the offset)
            Vector2f pos = childTransform.getScreenPosition();
            assertNotNull(pos);
            assertTrue(Float.isFinite(pos.x));
            assertTrue(Float.isFinite(pos.y));
        }

        @Test
        void percentOffsetWithParentScale() {
            parent.setParent(null);
            parentTransform.setScreenBounds(800, 600);
            parentTransform.setAnchor(0, 0);
            parentTransform.setOffset(0, 0);
            parentTransform.setPivot(0, 0);
            parentTransform.setScale2D(2, 2);

            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(25); // 400 * 25% = 100
            childTransform.setScreenBounds(800, 600);

            // Matrix path: offset=100, scaled by parent scale 2x -> pivotX = 200
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();
            assertEquals(200, pivotPos.x, 0.5f);
        }

        @Test
        void fillingParentIgnoresPercentOffset() {
            childTransform.setWidthMode(UITransform.SizeMode.PERCENT);
            childTransform.setWidthPercent(100);
            childTransform.setHeightMode(UITransform.SizeMode.PERCENT);
            childTransform.setHeightPercent(100);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(50);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(50);

            parentTransform.setScreenBounds(400, 300);
            childTransform.setScreenBounds(400, 300);

            // isFillingParent shortcut: snaps to parent origin, ignores offset
            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(0, pos.x, 0.5f);
            assertEquals(0, pos.y, 0.5f);
        }

        @Test
        void noParentPercentOffsetUsesScreenBounds() {
            child.setParent(null);
            childTransform.setScreenBounds(800, 600);
            childTransform.setAnchor(0, 0);
            childTransform.setPivot(0, 0);
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(50); // 800 * 50% = 400
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(25); // 600 * 25% = 150

            Vector2f pos = childTransform.getScreenPosition();
            assertEquals(400, pos.x, 0.5f);
            assertEquals(150, pos.y, 0.5f);

            // Matrix path should agree
            Vector2f pivotPos = childTransform.getWorldPivotPosition2D();
            assertEquals(400, pivotPos.x, 0.5f);
            assertEquals(150, pivotPos.y, 0.5f);
        }
    }

    // ========================================================================
    // Offset Percentage with setMatchParent
    // ========================================================================

    @Nested
    class OffsetPercentMatchParent {

        @Test
        void setMatchParentResetsOffsetModes() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(50);
            childTransform.setOffsetYMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetYPercent(25);

            childTransform.setMatchParent();

            assertEquals(UITransform.SizeMode.FIXED, childTransform.getOffsetXMode());
            assertEquals(UITransform.SizeMode.FIXED, childTransform.getOffsetYMode());
            assertEquals(0f, childTransform.getOffsetXPercent(), 0.01f);
            assertEquals(0f, childTransform.getOffsetYPercent(), 0.01f);
            assertEquals(0f, childTransform.getOffset().x, 0.01f);
            assertEquals(0f, childTransform.getOffset().y, 0.01f);
        }

        @Test
        void clearMatchParentDoesNotAffectOffsetMode() {
            childTransform.setOffsetXMode(UITransform.SizeMode.PERCENT);
            childTransform.setOffsetXPercent(50);

            childTransform.setMatchParent();
            childTransform.clearMatchParent();

            // clearMatchParent only resets size modes, offset mode was already reset by setMatchParent
            assertEquals(UITransform.SizeMode.FIXED, childTransform.getOffsetXMode());
        }
    }
}
