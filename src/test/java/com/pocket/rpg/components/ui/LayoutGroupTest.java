package com.pocket.rpg.components.ui;

import com.pocket.rpg.core.GameObject;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayoutGroupTest {

    /** Creates a GameObject with a UITransform of the given size. */
    private static GameObject makeUI(String name, float w, float h) {
        GameObject go = new GameObject(name);
        UITransform t = new UITransform(w, h);
        go.addComponent(t);
        return go;
    }

    /** Creates a PERCENT child (both axes). */
    private static GameObject makePercentChild(String name, float wPct, float hPct) {
        GameObject go = new GameObject(name);
        UITransform t = new UITransform();
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(wPct);
        t.setHeightMode(UITransform.SizeMode.PERCENT);
        t.setHeightPercent(hPct);
        go.addComponent(t);
        return go;
    }

    private static UITransform tr(GameObject go) {
        return go.getComponent(UITransform.class);
    }

    // ========================================================================
    // Vertical Layout Group
    // ========================================================================

    @Nested
    class VerticalLayout {

        private GameObject container;

        @BeforeEach
        void setUp() {
            container = makeUI("Container", 400, 300);
        }

        private UIVerticalLayoutGroup addVLG() {
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);
            return vlg;
        }

        @Test
        void percentChildrenFitWithinContentArea() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setPaddingTop(20);
            vlg.setPaddingBottom(30);

            GameObject a = makePercentChild("A", 100, 60);
            GameObject b = makePercentChild("B", 100, 40);
            a.setParent(container);
            b.setParent(container);

            vlg.applyLayout();

            float contentHeight = 300 - 20 - 30; // 250
            float aHeight = tr(a).getEffectiveHeight(); // 60% of 250 = 150
            float bHeight = tr(b).getEffectiveHeight(); // 40% of 250 = 100

            assertEquals(150, aHeight, 0.01f);
            assertEquals(100, bHeight, 0.01f);
            assertEquals(contentHeight, aHeight + bHeight, 0.01f);
        }

        @Test
        void spacingSubtractedFromPercentBase() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setSpacing(10);

            GameObject a = makePercentChild("A", 100, 50);
            GameObject b = makePercentChild("B", 100, 50);
            a.setParent(container);
            b.setParent(container);

            vlg.applyLayout();

            // contentHeight=300, totalSpacing=10, percentBase=290
            float aHeight = tr(a).getEffectiveHeight(); // 50% of 290 = 145
            float bHeight = tr(b).getEffectiveHeight(); // 50% of 290 = 145
            float totalSpacing = 10;

            assertEquals(145, aHeight, 0.01f);
            assertEquals(145, bHeight, 0.01f);
            // Total children + spacing fits exactly
            assertEquals(300, aHeight + bHeight + totalSpacing, 0.01f);
        }

        @Test
        void crossAxisPercentResolvesAgainstContentWidth() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setPaddingLeft(20);
            vlg.setPaddingRight(30);

            GameObject a = makePercentChild("A", 100, 100);
            a.setParent(container);

            vlg.applyLayout();

            // contentWidth = 400 - 20 - 30 = 350
            assertEquals(350, tr(a).getEffectiveWidth(), 0.01f);
        }

        @Test
        void fixedChildrenNotAffectedByOverrides() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setPaddingTop(50);

            GameObject fixed = makeUI("Fixed", 100, 80);
            fixed.setParent(container);

            vlg.applyLayout();

            // FIXED child keeps its own size
            assertEquals(100, tr(fixed).getEffectiveWidth(), 0.01f);
            assertEquals(80, tr(fixed).getEffectiveHeight(), 0.01f);
        }

        @Test
        void forceExpandDistributesEvenly() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setChildForceExpandHeight(true);
            vlg.setSpacing(10);

            GameObject a = makeUI("A", 100, 50);
            GameObject b = makeUI("B", 100, 50);
            a.setParent(container);
            b.setParent(container);

            vlg.applyLayout();

            // contentHeight=300, spacing=10, each = (300-10)/2 = 145
            assertEquals(145, tr(a).getEffectiveHeight(), 0.01f);
            assertEquals(145, tr(b).getEffectiveHeight(), 0.01f);
        }

        @Test
        void zeroChildrenDoesNotCrash() {
            addVLG();
            // No children — should return early without error
            assertDoesNotThrow(() -> container.getComponent(UIVerticalLayoutGroup.class).applyLayout());
        }

        @Test
        void singleChildGetsFullContentArea() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setSpacing(50); // spacing should not matter with 1 child

            GameObject a = makePercentChild("A", 100, 100);
            a.setParent(container);

            vlg.applyLayout();

            // totalSpacing = 50 * max(0, 1-1) = 0
            assertEquals(300, tr(a).getEffectiveHeight(), 0.01f);
        }

        @Test
        void percentReferenceSetCorrectly() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setPaddingTop(10);
            vlg.setPaddingBottom(20);
            vlg.setSpacing(5);
            vlg.setPaddingLeft(15);
            vlg.setPaddingRight(25);

            GameObject a = makePercentChild("A", 50, 50);
            GameObject b = makePercentChild("B", 50, 50);
            a.setParent(container);
            b.setParent(container);

            vlg.applyLayout();

            // Cross axis (width) reference = contentWidth = 400 - 15 - 25 = 360
            assertEquals(360, tr(a).getPercentReferenceWidth(), 0.01f);
            // Layout axis (height) reference = contentHeight - spacing = (300-10-20) - 5 = 265
            assertEquals(265, tr(a).getPercentReferenceHeight(), 0.01f);
        }

        @Test
        void childrenPositionedCorrectly() {
            UIVerticalLayoutGroup vlg = addVLG();
            vlg.setPaddingTop(10);
            vlg.setPaddingLeft(20);
            vlg.setSpacing(5);

            GameObject a = makeUI("A", 100, 40);
            GameObject b = makeUI("B", 100, 60);
            a.setParent(container);
            b.setParent(container);

            vlg.applyLayout();

            assertEquals(20, tr(a).getOffset().x, 0.01f);
            assertEquals(10, tr(a).getOffset().y, 0.01f);
            assertEquals(20, tr(b).getOffset().x, 0.01f);
            assertEquals(55, tr(b).getOffset().y, 0.01f); // 10 + 40 + 5
        }
    }

    // ========================================================================
    // Horizontal Layout Group
    // ========================================================================

    @Nested
    class HorizontalLayout {

        private GameObject container;

        @BeforeEach
        void setUp() {
            container = makeUI("Container", 400, 300);
        }

        private UIHorizontalLayoutGroup addHLG() {
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            container.addComponent(hlg);
            return hlg;
        }

        @Test
        void percentChildrenFitWithinContentArea() {
            UIHorizontalLayoutGroup hlg = addHLG();
            hlg.setPaddingLeft(20);
            hlg.setPaddingRight(30);

            GameObject a = makePercentChild("A", 60, 100);
            GameObject b = makePercentChild("B", 40, 100);
            a.setParent(container);
            b.setParent(container);

            hlg.applyLayout();

            float contentWidth = 400 - 20 - 30; // 350
            float aWidth = tr(a).getEffectiveWidth(); // 60% of 350 = 210
            float bWidth = tr(b).getEffectiveWidth(); // 40% of 350 = 140

            assertEquals(210, aWidth, 0.01f);
            assertEquals(140, bWidth, 0.01f);
            assertEquals(contentWidth, aWidth + bWidth, 0.01f);
        }

        @Test
        void spacingSubtractedFromPercentBase() {
            UIHorizontalLayoutGroup hlg = addHLG();
            hlg.setSpacing(20);

            GameObject a = makePercentChild("A", 70, 100);
            GameObject b = makePercentChild("B", 30, 100);
            a.setParent(container);
            b.setParent(container);

            hlg.applyLayout();

            // contentWidth=400, totalSpacing=20, percentBase=380
            float aWidth = tr(a).getEffectiveWidth(); // 70% of 380 = 266
            float bWidth = tr(b).getEffectiveWidth(); // 30% of 380 = 114

            assertEquals(266, aWidth, 0.01f);
            assertEquals(114, bWidth, 0.01f);
            assertEquals(400, aWidth + bWidth + 20, 0.01f);
        }

        @Test
        void crossAxisPercentResolvesAgainstContentHeight() {
            UIHorizontalLayoutGroup hlg = addHLG();
            hlg.setPaddingTop(10);
            hlg.setPaddingBottom(40);

            GameObject a = makePercentChild("A", 100, 100);
            a.setParent(container);

            hlg.applyLayout();

            // contentHeight = 300 - 10 - 40 = 250
            assertEquals(250, tr(a).getEffectiveHeight(), 0.01f);
        }

        @Test
        void mixedFixedAndPercentChildren() {
            UIHorizontalLayoutGroup hlg = addHLG();
            hlg.setSpacing(10);

            // Fixed child takes 100px, percent child takes 50% of (400 - 10) = 195
            GameObject fixed = makeUI("Fixed", 100, 50);
            fixed.setParent(container);

            GameObject pct = makePercentChild("Pct", 50, 100);
            pct.setParent(container);

            hlg.applyLayout();

            assertEquals(100, tr(fixed).getEffectiveWidth(), 0.01f);
            assertEquals(195, tr(pct).getEffectiveWidth(), 0.01f);
        }

        @Test
        void percentReferenceSetCorrectly() {
            UIHorizontalLayoutGroup hlg = addHLG();
            hlg.setPaddingLeft(10);
            hlg.setPaddingRight(20);
            hlg.setPaddingTop(5);
            hlg.setPaddingBottom(15);
            hlg.setSpacing(8);

            GameObject a = makePercentChild("A", 50, 50);
            GameObject b = makePercentChild("B", 50, 50);
            a.setParent(container);
            b.setParent(container);

            hlg.applyLayout();

            // Layout axis (width) reference = contentWidth - spacing = (400-10-20) - 8 = 362
            assertEquals(362, tr(a).getPercentReferenceWidth(), 0.01f);
            // Cross axis (height) reference = contentHeight = 300-5-15 = 280
            assertEquals(280, tr(a).getPercentReferenceHeight(), 0.01f);
        }
    }

    // ========================================================================
    // Grid Layout Group
    // ========================================================================

    @Nested
    class GridLayout {

        private GameObject container;

        @BeforeEach
        void setUp() {
            container = makeUI("Container", 400, 300);
        }

        @Test
        void setsLayoutOverridesOnChildren() {
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            container.addComponent(grid);

            GameObject a = makePercentChild("A", 100, 100);
            a.setParent(container);

            grid.applyLayout();

            // Grid overrides child size to cell size regardless of PERCENT mode
            assertEquals(80, tr(a).getEffectiveWidth(), 0.01f);
            assertEquals(60, tr(a).getEffectiveHeight(), 0.01f);
        }

        @Test
        void setsPercentReferenceToActualCellSize() {
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setChildForceExpandWidth(true);
            container.addComponent(grid);

            GameObject a = makeUI("A", 50, 50);
            GameObject b = makeUI("B", 50, 50);
            a.setParent(container);
            b.setParent(container);

            grid.applyLayout();

            // Force-expand: actualCellWidth = (400 - 0 spacing) / 2 = 200
            assertEquals(200, tr(a).getPercentReferenceWidth(), 0.01f);
            assertEquals(60, tr(a).getPercentReferenceHeight(), 0.01f);
        }
    }

    // ========================================================================
    // Nested Layouts (integration)
    // ========================================================================

    @Nested
    class NestedLayouts {

        @Test
        void childLayoutResolvesPercentAgainstParentOverride() {
            // Grandparent (400x300) → VLG → Parent (80% height) → VLG → Child (50% height)
            GameObject grandparent = makeUI("Grandparent", 400, 300);
            UIVerticalLayoutGroup outerVLG = new UIVerticalLayoutGroup();
            grandparent.addComponent(outerVLG);

            GameObject parentGo = makePercentChild("Parent", 100, 80);
            parentGo.setParent(grandparent);
            UIVerticalLayoutGroup innerVLG = new UIVerticalLayoutGroup();
            parentGo.addComponent(innerVLG);

            GameObject childGo = makePercentChild("Child", 100, 50);
            childGo.setParent(parentGo);

            // Apply outer layout first (sets parent's effective height)
            outerVLG.applyLayout();
            // Parent's effective height = 300 * 80% = 240 (no padding/spacing)
            assertEquals(240, tr(parentGo).getEffectiveHeight(), 0.01f);

            // Apply inner layout (should use parent's overridden height as base)
            innerVLG.applyLayout();
            // Child's effective height = 240 * 50% = 120
            assertEquals(120, tr(childGo).getEffectiveHeight(), 0.01f);
        }

        @Test
        void percentRoundTripThroughLayout() {
            // Simulate the editor toggle scenario
            GameObject containerGo = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            hlg.setPaddingLeft(20);
            hlg.setPaddingRight(30);
            hlg.setSpacing(10);
            containerGo.addComponent(hlg);

            GameObject a = makePercentChild("A", 60, 100);
            GameObject b = makePercentChild("B", 40, 100);
            a.setParent(containerGo);
            b.setParent(containerGo);

            hlg.applyLayout();

            UITransform at = tr(a);
            float originalPercent = at.getWidthPercent(); // 60
            float pixelSize = at.getEffectiveWidth();

            // Toggle to FIXED (what the editor does)
            at.setWidthMode(UITransform.SizeMode.FIXED);
            at.setWidth(pixelSize);

            // Next frame: layout clears overrides, child is FIXED
            hlg.applyLayout();

            // Toggle back to PERCENT (what the editor does)
            float referenceWidth = at.getPercentReferenceWidth();
            float computedPercent = pixelSize / referenceWidth * 100f;

            assertEquals(originalPercent, computedPercent, 0.01f,
                    "PERCENT→FIXED→PERCENT should round-trip to the original percentage");
        }

        @Test
        void overridePropagationThroughHierarchy() {
            // Grandparent (600px) → HLG padded → Parent (50%) → HLG → Child (50%)
            GameObject gp = makeUI("GP", 600, 400);
            UIHorizontalLayoutGroup outerHLG = new UIHorizontalLayoutGroup();
            outerHLG.setPaddingLeft(50);
            outerHLG.setPaddingRight(50);
            gp.addComponent(outerHLG);

            GameObject mid = makePercentChild("Mid", 50, 100);
            mid.setParent(gp);
            UIHorizontalLayoutGroup innerHLG = new UIHorizontalLayoutGroup();
            mid.addComponent(innerHLG);

            GameObject leaf = makePercentChild("Leaf", 50, 100);
            leaf.setParent(mid);

            outerHLG.applyLayout();
            // Mid contentWidth = 600 - 50 - 50 = 500. Mid at 50% = 250
            assertEquals(250, tr(mid).getEffectiveWidth(), 0.01f);

            innerHLG.applyLayout();
            // Leaf: parent effective = 250 (mid's override). Leaf at 50% of 250 = 125
            assertEquals(125, tr(leaf).getEffectiveWidth(), 0.01f);
        }
    }

    // ========================================================================
    // Grid Layout Edge Cases
    // ========================================================================

    @Nested
    class GridLayoutEdgeCases {

        private GameObject container;

        @BeforeEach
        void setUp() {
            container = makeUI("Container", 400, 300);
        }

        private UIGridLayoutGroup addGrid() {
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            container.addComponent(grid);
            return grid;
        }

        @Test
        void flexibleHorizontalAxisCalculatesColumnsFromWidth() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setStartAxis(UIGridLayoutGroup.Axis.HORIZONTAL);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FLEXIBLE);

            for (int i = 0; i < 6; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // columns = floor((400+0)/(80+0)) = 5, clamped to min(5,6)=5
            // rows = ceil(6/5) = 2
            // Last child (index 5): col=5%5=0, row=5/5=1
            UITransform last = tr(container.getChildren().get(5));
            assertEquals(0, last.getOffset().x, 0.01f);
            assertEquals(60, last.getOffset().y, 0.01f); // row 1
        }

        @Test
        void flexibleVerticalAxisCalculatesRowsFromHeight() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setStartAxis(UIGridLayoutGroup.Axis.VERTICAL);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FLEXIBLE);

            for (int i = 0; i < 6; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // rows = floor((300+0)/(60+0)) = 5, clamped to min(5,6)=5
            // cols = ceil(6/5) = 2
            // Child 0: row=0%5=0, col=0/5=0. Child 5: row=5%5=0, col=5/5=1
            UITransform child5 = tr(container.getChildren().get(5));
            assertEquals(80, child5.getOffset().x, 0.01f); // col 1
            assertEquals(0, child5.getOffset().y, 0.01f);  // row 0
        }

        @Test
        void fixedColumnCountConstraint() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(3);

            for (int i = 0; i < 7; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // 3 columns, ceil(7/3)=3 rows
            UITransform child6 = tr(container.getChildren().get(6));
            // col=6%3=0, row=6/3=2
            assertEquals(0, child6.getOffset().x, 0.01f);
            assertEquals(120, child6.getOffset().y, 0.01f); // row 2 * 60
        }

        @Test
        void fixedRowCountConstraint() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_ROW_COUNT);
            grid.setConstraintCount(2);

            for (int i = 0; i < 7; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // 2 rows, ceil(7/2)=4 columns
            UITransform child6 = tr(container.getChildren().get(6));
            // col=6%4=2 (HORIZONTAL axis), row=6/4=1
            // Wait - default startAxis is HORIZONTAL. col=6%4=2, row=6/4=1
            assertEquals(160, child6.getOffset().x, 0.01f); // col 2 * 80
            assertEquals(60, child6.getOffset().y, 0.01f);  // row 1 * 60
        }

        @Test
        void upperRightCornerFlipsColumns() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setStartCorner(UIGridLayoutGroup.Corner.UPPER_RIGHT);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(3);

            for (int i = 0; i < 3; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // Child 0: col=0%3=0 -> flipped to 3-1-0=2 -> x = 2*80 = 160
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(160, child0.getOffset().x, 0.01f);
        }

        @Test
        void lowerLeftCornerFlipsRows() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setStartCorner(UIGridLayoutGroup.Corner.LOWER_LEFT);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(3);

            for (int i = 0; i < 6; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // 3 cols, 2 rows. Child 0: col=0, row=0 -> flipped row=2-1-0=1
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(60, child0.getOffset().y, 0.01f); // row 1 * 60
        }

        @Test
        void lowerRightCornerFlipsBothAxes() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setStartCorner(UIGridLayoutGroup.Corner.LOWER_RIGHT);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(3);

            for (int i = 0; i < 6; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // 3 cols, 2 rows. Child 0: col=0->2, row=0->1
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(160, child0.getOffset().x, 0.01f); // col 2 * 80
            assertEquals(60, child0.getOffset().y, 0.01f);  // row 1 * 60
        }

        @Test
        void childForceExpandHeightExpandsCells() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setChildForceExpandHeight(true);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            for (int i = 0; i < 4; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // 2 cols, 2 rows. expandedHeight = (300 - 0) / 2 = 150
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(150, child0.getEffectiveHeight(), 0.01f);
        }

        @Test
        void childForceExpandBothAxes() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setChildForceExpandWidth(true);
            grid.setChildForceExpandHeight(true);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            for (int i = 0; i < 4; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // 2 cols, 2 rows. expandedWidth = 400/2 = 200, expandedHeight = 300/2 = 150
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(200, child0.getEffectiveWidth(), 0.01f);
            assertEquals(150, child0.getEffectiveHeight(), 0.01f);
        }

        @Test
        void horizontalAlignmentCenter() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setHorizontalAlignment(LayoutGroup.ChildHorizontalAlignment.CENTER);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            makeUI("C0", 50, 50).setParent(container);
            makeUI("C1", 50, 50).setParent(container);

            grid.applyLayout();

            // gridWidth = 2*80 + 1*0 = 160. offset = (400-160)/2 = 120
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(120, child0.getOffset().x, 0.01f);
        }

        @Test
        void horizontalAlignmentRight() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setHorizontalAlignment(LayoutGroup.ChildHorizontalAlignment.RIGHT);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            makeUI("C0", 50, 50).setParent(container);
            makeUI("C1", 50, 50).setParent(container);

            grid.applyLayout();

            // gridWidth = 160. offset = 400-160 = 240
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(240, child0.getOffset().x, 0.01f);
        }

        @Test
        void verticalAlignmentMiddle() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setVerticalAlignment(LayoutGroup.ChildVerticalAlignment.MIDDLE);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            makeUI("C0", 50, 50).setParent(container);
            makeUI("C1", 50, 50).setParent(container);

            grid.applyLayout();

            // gridHeight = 1*60 = 60. offset = (300-60)/2 = 120
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(120, child0.getOffset().y, 0.01f);
        }

        @Test
        void verticalAlignmentBottom() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setVerticalAlignment(LayoutGroup.ChildVerticalAlignment.BOTTOM);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            makeUI("C0", 50, 50).setParent(container);
            makeUI("C1", 50, 50).setParent(container);

            grid.applyLayout();

            // gridHeight = 60. offset = 300-60 = 240
            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(240, child0.getOffset().y, 0.01f);
        }

        @Test
        void spacingAffectsFlexibleColumnCalculation() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setSpacing(40);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FLEXIBLE);

            for (int i = 0; i < 6; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // cols = floor((400+40)/(80+40)) = floor(440/120) = 3, clamped to min(3,6)=3
            // rows = ceil(6/3) = 2
            UITransform child3 = tr(container.getChildren().get(3));
            // col=3%3=0, row=3/3=1 -> y = 1*(60+40) = 100
            assertEquals(0, child3.getOffset().x, 0.01f);
            assertEquals(100, child3.getOffset().y, 0.01f);
        }

        @Test
        void paddingReducesAvailableArea() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setPaddingLeft(50);
            grid.setPaddingTop(30);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            makeUI("C0", 50, 50).setParent(container);

            grid.applyLayout();

            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(50, child0.getOffset().x, 0.01f);
            assertEquals(30, child0.getOffset().y, 0.01f);
        }

        @Test
        void singleChildGridPlacement() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(3);

            makeUI("C0", 50, 50).setParent(container);

            grid.applyLayout();

            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(0, child0.getOffset().x, 0.01f);
            assertEquals(0, child0.getOffset().y, 0.01f);
        }

        @Test
        void constraintCountMinimumOne() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(0);

            for (int i = 0; i < 3; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            // constraintCount=0 -> clamped to 1
            assertDoesNotThrow(() -> grid.applyLayout());

            // With 1 column, 3 rows. Child 2 at row 2
            UITransform child2 = tr(container.getChildren().get(2));
            assertEquals(0, child2.getOffset().x, 0.01f);
            assertEquals(120, child2.getOffset().y, 0.01f); // row 2 * 60
        }

        @Test
        void verticalAxisFillOrder() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setStartAxis(UIGridLayoutGroup.Axis.VERTICAL);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_ROW_COUNT);
            grid.setConstraintCount(3);

            for (int i = 0; i < 6; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }

            grid.applyLayout();

            // 3 rows, ceil(6/3)=2 cols. VERTICAL axis: row=i%3, col=i/3
            // Child 0: row=0, col=0 -> (0, 0)
            // Child 3: row=3%3=0, col=3/3=1 -> (80, 0)
            UITransform child3 = tr(container.getChildren().get(3));
            assertEquals(80, child3.getOffset().x, 0.01f);
            assertEquals(0, child3.getOffset().y, 0.01f);
        }

        @Test
        void spacingGreaterThanAvailableWidth() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(50);
            grid.setCellHeight(50);
            grid.setSpacing(200);
            grid.setChildForceExpandWidth(true);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);

            makeUI("C0", 50, 50).setParent(container);
            makeUI("C1", 50, 50).setParent(container);

            // availableWidth=400, spacing=200, force-expand: (400-200)/2 = 100
            assertDoesNotThrow(() -> grid.applyLayout());

            UITransform child0 = tr(container.getChildren().get(0));
            assertEquals(100, child0.getEffectiveWidth(), 0.01f);
        }

        @Test
        void flexibleColumnsClampedToChildCount() {
            UIGridLayoutGroup grid = addGrid();
            grid.setCellWidth(50);
            grid.setCellHeight(50);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FLEXIBLE);

            makeUI("C0", 50, 50).setParent(container);
            makeUI("C1", 50, 50).setParent(container);

            grid.applyLayout();

            // cols = floor((400+0)/(50+0)) = 8, clamped to min(8, 2) = 2
            // Both children should be on row 0
            UITransform child0 = tr(container.getChildren().get(0));
            UITransform child1 = tr(container.getChildren().get(1));
            assertEquals(0, child0.getOffset().y, 0.01f);
            assertEquals(0, child1.getOffset().y, 0.01f);
            assertEquals(50, child1.getOffset().x, 0.01f);
        }
    }

    // ========================================================================
    // Disabled Children
    // ========================================================================

    @Nested
    class DisabledChildren {

        @Test
        void disabledChildrenExcludedFromVerticalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            GameObject b = makeUI("B", 100, 40);
            GameObject c = makeUI("C", 100, 40);
            a.setParent(container);
            b.setParent(container);
            c.setParent(container);

            b.setEnabled(false);
            vlg.applyLayout();

            // Only A and C should be laid out
            assertEquals(0, tr(a).getOffset().y, 0.01f);
            assertEquals(40, tr(c).getOffset().y, 0.01f); // directly after A
        }

        @Test
        void disabledChildrenExcludedFromHorizontalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            container.addComponent(hlg);

            GameObject a = makeUI("A", 100, 40);
            GameObject b = makeUI("B", 100, 40);
            a.setParent(container);
            b.setParent(container);

            b.setEnabled(false);
            hlg.applyLayout();

            // Only A laid out
            assertEquals(0, tr(a).getOffset().x, 0.01f);
        }

        @Test
        void disabledChildrenExcludedFromGridLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(3);
            container.addComponent(grid);

            GameObject a = makeUI("A", 50, 50);
            GameObject b = makeUI("B", 50, 50);
            GameObject c = makeUI("C", 50, 50);
            a.setParent(container);
            b.setParent(container);
            c.setParent(container);

            b.setEnabled(false);
            grid.applyLayout();

            // A at col 0, C at col 1 (B skipped)
            assertEquals(0, tr(a).getOffset().x, 0.01f);
            assertEquals(80, tr(c).getOffset().x, 0.01f);
        }

        @Test
        void enablingChildChangesLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            GameObject b = makeUI("B", 100, 40);
            GameObject c = makeUI("C", 100, 40);
            a.setParent(container);
            b.setParent(container);
            c.setParent(container);

            b.setEnabled(false);
            vlg.applyLayout();
            assertEquals(40, tr(c).getOffset().y, 0.01f);

            b.setEnabled(true);
            vlg.applyLayout();
            assertEquals(80, tr(c).getOffset().y, 0.01f); // A(40) + B(40)
        }

        @Test
        void disablingChildChangesLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            GameObject b = makeUI("B", 100, 40);
            GameObject c = makeUI("C", 100, 40);
            a.setParent(container);
            b.setParent(container);
            c.setParent(container);

            vlg.applyLayout();
            assertEquals(80, tr(c).getOffset().y, 0.01f); // 40 + 40

            b.setEnabled(false);
            vlg.applyLayout();
            assertEquals(40, tr(c).getOffset().y, 0.01f); // only A(40)
        }

        @Test
        void allChildrenDisabledDoesNotCrash() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);
            a.setEnabled(false);

            assertDoesNotThrow(() -> vlg.applyLayout());
        }

        @Test
        void childWithoutUITransformExcluded() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            // Plain GO without UITransform
            GameObject plain = new GameObject("Plain");
            plain.setParent(container);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            assertDoesNotThrow(() -> vlg.applyLayout());
            assertEquals(0, tr(a).getOffset().y, 0.01f);
        }
    }

    // ========================================================================
    // Dynamic Child Count
    // ========================================================================

    @Nested
    class DynamicChildCount {

        @Test
        void addChildAndReapplyVerticalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);
            vlg.applyLayout();

            assertEquals(0, tr(a).getOffset().y, 0.01f);

            GameObject b = makeUI("B", 100, 60);
            b.setParent(container);
            vlg.applyLayout();

            assertEquals(0, tr(a).getOffset().y, 0.01f);
            assertEquals(40, tr(b).getOffset().y, 0.01f);
        }

        @Test
        void removeChildAndReapplyVerticalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            GameObject b = makeUI("B", 100, 60);
            GameObject c = makeUI("C", 100, 30);
            a.setParent(container);
            b.setParent(container);
            c.setParent(container);
            vlg.applyLayout();

            assertEquals(100, tr(c).getOffset().y, 0.01f); // 40 + 60

            b.setParent(null);
            vlg.applyLayout();

            assertEquals(40, tr(c).getOffset().y, 0.01f); // only A(40)
        }

        @Test
        void addChildAndReapplyHorizontalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            container.addComponent(hlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);
            hlg.applyLayout();

            assertEquals(0, tr(a).getOffset().x, 0.01f);

            GameObject b = makeUI("B", 80, 40);
            b.setParent(container);
            hlg.applyLayout();

            assertEquals(100, tr(b).getOffset().x, 0.01f);
        }

        @Test
        void removeChildAndReapplyHorizontalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            container.addComponent(hlg);

            GameObject a = makeUI("A", 100, 40);
            GameObject b = makeUI("B", 80, 40);
            GameObject c = makeUI("C", 60, 40);
            a.setParent(container);
            b.setParent(container);
            c.setParent(container);
            hlg.applyLayout();

            assertEquals(180, tr(c).getOffset().x, 0.01f); // 100 + 80

            b.setParent(null);
            hlg.applyLayout();

            assertEquals(100, tr(c).getOffset().x, 0.01f); // only A(100)
        }

        @Test
        void addChildAndReapplyGridLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(3);
            container.addComponent(grid);

            for (int i = 0; i < 3; i++) {
                makeUI("C" + i, 50, 50).setParent(container);
            }
            grid.applyLayout();

            // All on row 0
            assertEquals(0, tr(container.getChildren().get(2)).getOffset().y, 0.01f);

            makeUI("C3", 50, 50).setParent(container);
            grid.applyLayout();

            // 4th child at row 1
            assertEquals(60, tr(container.getChildren().get(3)).getOffset().y, 0.01f);
        }

        @Test
        void removeChildAndReapplyGridLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);
            container.addComponent(grid);

            GameObject a = makeUI("A", 50, 50);
            GameObject b = makeUI("B", 50, 50);
            GameObject c = makeUI("C", 50, 50);
            a.setParent(container);
            b.setParent(container);
            c.setParent(container);
            grid.applyLayout();

            // C at col=0, row=1 -> y=60
            assertEquals(60, tr(c).getOffset().y, 0.01f);

            b.setParent(null);
            grid.applyLayout();

            // Now only 2 children, C at col=1, row=0 -> y=0
            assertEquals(0, tr(c).getOffset().y, 0.01f);
            assertEquals(80, tr(c).getOffset().x, 0.01f);
        }

        @Test
        void spacingAdjustsWithChangingChildCount() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            vlg.setSpacing(10);
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);
            vlg.applyLayout();
            assertEquals(0, tr(a).getOffset().y, 0.01f);

            GameObject b = makeUI("B", 100, 40);
            b.setParent(container);
            vlg.applyLayout();
            assertEquals(50, tr(b).getOffset().y, 0.01f); // 40 + 10

            GameObject c = makeUI("C", 100, 40);
            c.setParent(container);
            vlg.applyLayout();
            assertEquals(100, tr(c).getOffset().y, 0.01f); // 40 + 10 + 40 + 10
        }

        @Test
        void percentDistributionUpdatesOnChildCountChange() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makePercentChild("A", 100, 50);
            GameObject b = makePercentChild("B", 100, 50);
            a.setParent(container);
            b.setParent(container);
            vlg.applyLayout();

            assertEquals(150, tr(a).getEffectiveHeight(), 0.01f); // 50% of 300
            assertEquals(150, tr(b).getEffectiveHeight(), 0.01f);

            // Add third child with 33.33% each
            GameObject c = makePercentChild("C", 100, 33.33f);
            c.setParent(container);
            tr(a).setHeightPercent(33.33f);
            tr(b).setHeightPercent(33.33f);
            vlg.applyLayout();

            assertEquals(100, tr(a).getEffectiveHeight(), 1f); // ~33.33% of 300
        }
    }

    // ========================================================================
    // Pivot with Layout Groups
    // ========================================================================

    @Nested
    class PivotWithLayoutGroups {

        @Test
        void layoutForcesPivotToZeroZero() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            tr(a).setPivot(0.5f, 0.5f);
            a.setParent(container);

            vlg.applyLayout();

            assertEquals(0, tr(a).getPivot().x, 0.001f);
            assertEquals(0, tr(a).getPivot().y, 0.001f);
        }

        @Test
        void gridLayoutForcesPivotToZeroZero() {
            GameObject container = makeUI("Container", 400, 300);
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            grid.setCellWidth(80);
            grid.setCellHeight(60);
            container.addComponent(grid);

            GameObject a = makeUI("A", 50, 50);
            tr(a).setPivot(0.5f, 0.5f);
            a.setParent(container);

            grid.applyLayout();

            assertEquals(0, tr(a).getPivot().x, 0.001f);
            assertEquals(0, tr(a).getPivot().y, 0.001f);
        }

        @Test
        void effectivePivotWithFillingParentInLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            container.addComponent(vlg);

            GameObject a = makePercentChild("A", 100, 100);
            a.setParent(container);

            vlg.applyLayout();

            // Layout sets pivot to (0,0), and the child fills parent (100%)
            // But layout also sets overrides so the child is filling the content area
            assertEquals(0, tr(a).getPivot().x, 0.001f);
            assertEquals(0, tr(a).getPivot().y, 0.001f);
        }

        @Test
        void pivotResetByLayoutDoesNotAffectScreenPosition() {
            GameObject container = makeUI("Container", 400, 300);
            container.setParent(null);
            tr(container).setScreenBounds(800, 600);
            tr(container).setAnchor(0, 0);
            tr(container).setOffset(0, 0);
            tr(container).setPivot(0, 0);

            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            vlg.setPaddingTop(10);
            vlg.setPaddingLeft(20);
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            vlg.applyLayout();

            // After layout: pivot(0,0), offset = (20, 10)
            // screenPos = containerPos + offset = (0,0) + (20,10) = (20, 10)
            tr(a).setScreenBounds(800, 600);
            Vector2f pos = tr(a).getScreenPosition();
            assertEquals(20, pos.x, 0.5f);
            assertEquals(10, pos.y, 0.5f);
        }
    }

    // ========================================================================
    // Vertical Layout Alignment
    // ========================================================================

    @Nested
    class VerticalLayoutAlignment {

        @Test
        void centerAlignmentCentersChild() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            vlg.setChildAlignment(LayoutGroup.ChildHorizontalAlignment.CENTER);
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            vlg.applyLayout();

            // contentWidth = 400, childWidth = 100, offset = (400-100)/2 = 150
            assertEquals(150, tr(a).getOffset().x, 0.01f);
        }

        @Test
        void rightAlignmentAlignsToRight() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            vlg.setChildAlignment(LayoutGroup.ChildHorizontalAlignment.RIGHT);
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            vlg.applyLayout();

            // contentWidth = 400, childWidth = 100, offset = 400-100 = 300
            assertEquals(300, tr(a).getOffset().x, 0.01f);
        }

        @Test
        void forceExpandWidthInVerticalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            vlg.setChildForceExpandWidth(true);
            container.addComponent(vlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            vlg.applyLayout();

            assertEquals(400, tr(a).getEffectiveWidth(), 0.01f);
        }

        @Test
        void forceExpandHeightTakesPriorityOverPercent() {
            GameObject container = makeUI("Container", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            vlg.setChildForceExpandHeight(true);
            container.addComponent(vlg);

            GameObject a = makePercentChild("A", 100, 50);
            a.setParent(container);

            vlg.applyLayout();

            // forceExpand: only 1 child, gets full 300
            assertEquals(300, tr(a).getEffectiveHeight(), 0.01f);
        }
    }

    // ========================================================================
    // Horizontal Layout Alignment
    // ========================================================================

    @Nested
    class HorizontalLayoutAlignment {

        @Test
        void middleAlignmentCentersChild() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            hlg.setChildAlignment(LayoutGroup.ChildVerticalAlignment.MIDDLE);
            container.addComponent(hlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            hlg.applyLayout();

            // contentHeight = 300, childHeight = 40, offset = (300-40)/2 = 130
            assertEquals(130, tr(a).getOffset().y, 0.01f);
        }

        @Test
        void bottomAlignmentAlignsToBottom() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            hlg.setChildAlignment(LayoutGroup.ChildVerticalAlignment.BOTTOM);
            container.addComponent(hlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            hlg.applyLayout();

            // contentHeight = 300, childHeight = 40, offset = 300-40 = 260
            assertEquals(260, tr(a).getOffset().y, 0.01f);
        }

        @Test
        void forceExpandHeightInHorizontalLayout() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            hlg.setChildForceExpandHeight(true);
            container.addComponent(hlg);

            GameObject a = makeUI("A", 100, 40);
            a.setParent(container);

            hlg.applyLayout();

            assertEquals(300, tr(a).getEffectiveHeight(), 0.01f);
        }

        @Test
        void forceExpandWidthDistributesEvenly() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            hlg.setChildForceExpandWidth(true);
            hlg.setSpacing(0);
            container.addComponent(hlg);

            GameObject a = makeUI("A", 50, 40);
            GameObject b = makeUI("B", 50, 40);
            a.setParent(container);
            b.setParent(container);

            hlg.applyLayout();

            // Each gets 400/2 = 200
            assertEquals(200, tr(a).getEffectiveWidth(), 0.01f);
            assertEquals(200, tr(b).getEffectiveWidth(), 0.01f);
        }

        @Test
        void forceExpandWidthTakesPriorityOverPercent() {
            GameObject container = makeUI("Container", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            hlg.setChildForceExpandWidth(true);
            container.addComponent(hlg);

            GameObject a = makePercentChild("A", 30, 100);
            a.setParent(container);

            hlg.applyLayout();

            // forceExpand: only 1 child, gets full 400
            assertEquals(400, tr(a).getEffectiveWidth(), 0.01f);
        }
    }

    // ========================================================================
    // Layout Group Defensive Paths
    // ========================================================================

    @Nested
    class LayoutGroupDefensivePaths {

        @Test
        void applyLayoutWithNoGameObjectDoesNotCrash() {
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            // Not attached to any GO
            assertDoesNotThrow(() -> vlg.applyLayout());
        }

        @Test
        void applyLayoutWithNoUITransformDoesNotCrash() {
            GameObject go = new GameObject("NoUITransform");
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            go.addComponent(vlg);
            // GO has no UITransform
            assertDoesNotThrow(() -> vlg.applyLayout());
        }

        @Test
        void reparentBetweenDifferentLayoutGroups() {
            // HLG container
            GameObject hlgContainer = makeUI("HLG", 400, 300);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            hlg.setChildForceExpandWidth(true);
            hlgContainer.addComponent(hlg);

            // VLG container
            GameObject vlgContainer = makeUI("VLG", 400, 300);
            UIVerticalLayoutGroup vlg = new UIVerticalLayoutGroup();
            vlg.setChildForceExpandHeight(true);
            vlgContainer.addComponent(vlg);

            GameObject child = makeUI("Child", 100, 100);
            child.setParent(hlgContainer);

            hlg.applyLayout();
            assertEquals(400, tr(child).getEffectiveWidth(), 0.01f); // HLG force-expand

            // Reparent to VLG — clears stale HLG overrides
            child.setParent(vlgContainer);
            // After reparent, override is cleared; HLG's forceExpand also set width=400 on the field
            // So FIXED width is now 400 (the raw field was mutated by HLG)
            assertEquals(400, tr(child).getWidth(), 0.01f);

            vlg.applyLayout();

            // VLG force-expand height applies fresh override
            assertEquals(300, tr(child).getEffectiveHeight(), 0.01f);
            // VLG has no forceExpandWidth — child uses FIXED width (which HLG set to 400)
            // The key point: stale HLG *override* is cleared, VLG applies its own overrides
            assertEquals(400, tr(child).getEffectiveWidth(), 0.01f);
        }
    }

    // ========================================================================
    // Three-Level Nested Layouts
    // ========================================================================

    @Nested
    class ThreeLevelNestedLayouts {

        @Test
        void threeLevelVlgHlgVlgNesting() {
            // Level 1: VLG
            GameObject l1 = makeUI("L1", 400, 300);
            UIVerticalLayoutGroup vlg1 = new UIVerticalLayoutGroup();
            l1.addComponent(vlg1);

            // Level 2: HLG child (80% height of L1)
            GameObject l2 = makePercentChild("L2", 100, 80);
            l2.setParent(l1);
            UIHorizontalLayoutGroup hlg2 = new UIHorizontalLayoutGroup();
            l2.addComponent(hlg2);

            // Level 3: VLG leaf (50% width within L2)
            GameObject l3 = makePercentChild("L3", 50, 100);
            l3.setParent(l2);

            vlg1.applyLayout();
            // L2 height = 300 * 80% = 240
            assertEquals(240, tr(l2).getEffectiveHeight(), 0.01f);

            hlg2.applyLayout();
            // L3 width: L2 effective width = 400 (100% cross axis of VLG)
            // L3 at 50% of 400 = 200
            assertEquals(200, tr(l3).getEffectiveWidth(), 0.01f);
        }

        @Test
        void mixedLayoutTypesThreeLevels() {
            // Level 1: HLG
            GameObject l1 = makeUI("L1", 600, 400);
            UIHorizontalLayoutGroup hlg = new UIHorizontalLayoutGroup();
            l1.addComponent(hlg);

            // Level 2: Grid (50% width)
            GameObject l2 = makePercentChild("L2", 50, 100);
            l2.setParent(l1);
            UIGridLayoutGroup grid = new UIGridLayoutGroup();
            grid.setCellWidth(60);
            grid.setCellHeight(40);
            grid.setConstraint(UIGridLayoutGroup.Constraint.FIXED_COLUMN_COUNT);
            grid.setConstraintCount(2);
            l2.addComponent(grid);

            // Level 3: leaf children in grid
            GameObject leaf1 = makeUI("Leaf1", 50, 50);
            GameObject leaf2 = makeUI("Leaf2", 50, 50);
            leaf1.setParent(l2);
            leaf2.setParent(l2);

            hlg.applyLayout();
            // L2 width = 50% of 600 = 300
            assertEquals(300, tr(l2).getEffectiveWidth(), 0.01f);

            grid.applyLayout();
            // Grid cells at 60x40 within 300px container
            assertEquals(60, tr(leaf1).getEffectiveWidth(), 0.01f);
            assertEquals(40, tr(leaf1).getEffectiveHeight(), 0.01f);
            assertEquals(60, tr(leaf2).getOffset().x, 0.01f); // col 1
        }
    }
}
