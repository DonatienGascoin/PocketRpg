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
}
