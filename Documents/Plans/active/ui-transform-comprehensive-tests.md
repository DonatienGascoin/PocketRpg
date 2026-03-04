# Comprehensive UITransform + Layout Test Suite

## Context

We fixed 6 bugs in the UITransform/Layout system (percent overflow, pivot flip, round-trip drift, stale overrides, grid overrides, reparent cleanup) and wrote 36 initial tests. The user asked for **full test coverage** of all remaining untested code paths: rotation, scale, anchor presets, pivot+layout interaction, matrix vs position consistency, grid edge cases, disabled children, and dynamic child count.

## Approach

Add **83 new tests** across 10 new `@Nested` classes in the 2 existing test files. No new test files needed.

---

## UITransformTest.java - 39 New Tests

### @Nested Rotation (13 tests)
Covers `getRotation2D()`, `getWorldRotation2D()`, `calculatePosition()` rotation path, `buildUIWorldMatrix()` rotation paths.

| Test | Verifies |
|------|----------|
| `localRotationReturnsLocalZ` | `getRotation2D()` = localRotation.z when matchParentRotation=false |
| `matchParentRotationReturnsParentWorldRotation` | matchParentRotation=true returns parent world rot |
| `matchParentRotationWithNoParentReturnsLocal` | matchParentRotation=true, no parent -> local fallback |
| `worldRotationAccumulatesParentRotation` | child.worldRot = local + parent.worldRot |
| `worldRotationMatchParentIgnoresLocal` | matchParentRotation=true: worldRot = parent only |
| `worldRotationNoParentReturnsLocal` | Unparented: worldRot = local |
| `worldRotationThreeLevelHierarchy` | GP(10)+P(20)+C(5) = 35 recursive accumulation |
| `calculatePositionRotatesChildAroundParentPivot` | Parent rot=90, child position rotated around parent center |
| `calculatePositionNoRotationSkipsRotationPath` | Parent rot=0 -> simple addition path |
| `buildMatrixWorldRotationComposesWithParent` | Matrix: parent(30)+child(15)=45 |
| `buildMatrixMatchParentRotationUsesParentOnly` | Matrix matchParentRotation: uses parent only |
| `buildMatrixFillingParentInheritsParentRotation` | PERCENT 100/100: inherits parent's computed rotation |
| `buildMatrixNoParentUsesLocalRotation` | Unparented: matrix uses local rot |

### @Nested Scale (13 tests)
Covers `getScale2D()`, `getWorldScale2D()`, `calculatePosition()` scale in pivot, `buildUIWorldMatrix()` scale paths.

| Test | Verifies |
|------|----------|
| `localScaleReturnsLocalValues` | getScale2D() = local when matchParentScale=false |
| `matchParentScaleReturnsParentWorldScale` | matchParentScale=true returns parent world scale |
| `matchParentScaleWithNoParentReturnsLocal` | matchParentScale=true, no parent -> local fallback |
| `worldScaleMultipliesWithParent` | P(2,3)*C(0.5,2) = (1,6) |
| `worldScaleMatchParentIgnoresLocal` | matchParentScale: world = parent only |
| `worldScaleNoParentReturnsLocal` | Unparented: world = local |
| `worldScaleThreeLevelHierarchy` | GP(2,2)*P(3,1)*C(0.5,4) = (3,8) |
| `calculatePositionAccountsForWorldScale` | Scale affects pivot offset in calculatePosition |
| `buildMatrixWorldScaleComposesWithParent` | Matrix scale composition |
| `buildMatrixMatchParentScaleUsesParentOnly` | Matrix matchParentScale |
| `buildMatrixFillingParentInheritsParentScale` | 100/100 inherits parent scale |
| `buildMatrixNoParentUsesLocalScale` | Unparented: matrix uses local scale |
| `parentScaleAffectsChildRelativePositionInMatrix` | Parent scale stretches child offset in matrix |

### @Nested AnchorPresets (6 tests)
Non-zero anchors + PERCENT mode, in both calculatePosition and buildUIWorldMatrix.

| Test | Verifies |
|------|----------|
| `centerAnchorWithPercentSize` | Anchor(0.5,0.5) + PERCENT 50% |
| `centerAnchorCenteredPivotWithPercent` | Anchor(0.5,0.5) + pivot(0.5,0.5) + PERCENT centers element |
| `bottomRightAnchorWithOffset` | Anchor(1,1) + offset(-10,-10) |
| `bottomRightAnchorWithPercentSize` | Anchor(1,1) + PERCENT + pivot(1,1) = bottom-right aligned |
| `anchorInMatrixPathMatchesCalculatePosition` | Cross-validate anchor in both paths |
| `anchorWithNoParentUsesScreenBounds` | Unparented + anchor uses screenBounds |

### @Nested MatrixPositionConsistency (7 tests)
Cross-validation: `worldPivotPos = screenPos + pivot * effectiveSize * worldScale`

| Test | Verifies |
|------|----------|
| `pivotPositionEqualsScreenPosPlusPivotOffset` | Non-zero pivot, no rotation/scale |
| `zeroPivotMakesBothPositionsEqual` | pivot(0,0): both return same position |
| `fillingParentBothPathsConsistent` | 100/100 child: both paths consistent |
| `anchorOffsetConsistentBetweenPaths` | Anchor + offset + pivot |
| `percentSizeConsistentBetweenPaths` | PERCENT 60/40 + pivot |
| `withParentScaleConsistentBetweenPaths` | Parent scale affects relationship |
| `noParentConsistentBetweenPaths` | Unparented with screenBounds |

---

## LayoutGroupTest.java - 44 New Tests

### @Nested GridLayoutEdgeCases (18 tests)
All constraint types, corners, axes, alignments, spacing.

| Test | Verifies |
|------|----------|
| `flexibleHorizontalAxisCalculatesColumnsFromWidth` | FLEXIBLE+HORIZONTAL: cols from width |
| `flexibleVerticalAxisCalculatesRowsFromHeight` | FLEXIBLE+VERTICAL: rows from height |
| `fixedColumnCountConstraint` | FIXED_COLUMN_COUNT works |
| `fixedRowCountConstraint` | FIXED_ROW_COUNT works |
| `upperRightCornerFlipsColumns` | UPPER_RIGHT: col = cols-1-col |
| `lowerLeftCornerFlipsRows` | LOWER_LEFT: row = rows-1-row |
| `lowerRightCornerFlipsBothAxes` | LOWER_RIGHT: both flipped |
| `childForceExpandHeightExpandsCells` | Force-expand height |
| `childForceExpandBothAxes` | Both expand flags |
| `horizontalAlignmentCenter` | CENTER alignment offset |
| `horizontalAlignmentRight` | RIGHT alignment offset |
| `verticalAlignmentMiddle` | MIDDLE alignment offset |
| `verticalAlignmentBottom` | BOTTOM alignment offset |
| `spacingAffectsFlexibleColumnCalculation` | Larger spacing = fewer columns |
| `paddingReducesAvailableArea` | Padding shrinks available area |
| `singleChildGridPlacement` | 1 child at baseX, baseY |
| `constraintCountMinimumOne` | constraintCount=0 -> clamped to 1 |
| `verticalAxisFillOrder` | VERTICAL axis: children fill column-first |

### @Nested DisabledChildren (7 tests)
`getLayoutChildren()` filtering of disabled GameObjects.

| Test | Verifies |
|------|----------|
| `disabledChildrenExcludedFromVerticalLayout` | Disabled child skipped in VLG |
| `disabledChildrenExcludedFromHorizontalLayout` | Disabled child skipped in HLG |
| `disabledChildrenExcludedFromGridLayout` | Disabled child skipped in grid |
| `enablingChildChangesLayout` | Enable -> re-apply -> 3 children |
| `disablingChildChangesLayout` | Disable -> re-apply -> 2 children |
| `allChildrenDisabledDoesNotCrash` | Empty children list = no crash |
| `childWithoutUITransformExcluded` | Plain GameObject excluded |

### @Nested DynamicChildCount (8 tests)
Adding/removing children + re-applying layout.

| Test | Verifies |
|------|----------|
| `addChildAndReapplyVerticalLayout` | Add child, spacing adjusts |
| `removeChildAndReapplyVerticalLayout` | Remove child, positions adjust |
| `addChildAndReapplyHorizontalLayout` | Same for HLG |
| `removeChildAndReapplyHorizontalLayout` | Same for HLG |
| `addChildAndReapplyGridLayout` | Grid recalculates rows/cols |
| `removeChildAndReapplyGridLayout` | Grid recalculates |
| `spacingAdjustsWithChangingChildCount` | 1->2->3 children, spacing updates |
| `percentDistributionUpdatesOnChildCountChange` | Percent base recalculated |

### @Nested PivotWithLayoutGroups (4 tests)

| Test | Verifies |
|------|----------|
| `layoutForcesPivotToZeroZero` | VLG sets child pivot to (0,0) |
| `gridLayoutForcesPivotToZeroZero` | Grid sets child pivot to (0,0) |
| `effectivePivotWithFillingParentInLayout` | isFillingParent + layout override |
| `pivotResetByLayoutDoesNotAffectScreenPosition` | Pivot(0,0) -> pos = parentPos + offset |

### @Nested VerticalLayoutAlignment (3 tests)

| Test | Verifies |
|------|----------|
| `centerAlignmentCentersChild` | VLG CENTER alignment |
| `rightAlignmentAlignsToRight` | VLG RIGHT alignment |
| `forceExpandWidthInVerticalLayout` | childForceExpandWidth sets width |

### @Nested HorizontalLayoutAlignment (4 tests)

| Test | Verifies |
|------|----------|
| `middleAlignmentCentersChild` | HLG MIDDLE alignment |
| `bottomAlignmentAlignsToBottom` | HLG BOTTOM alignment |
| `forceExpandHeightInHorizontalLayout` | childForceExpandHeight sets height |
| `forceExpandWidthDistributesEvenly` | HLG force-expand width evenly |

---

## Files to Modify

| File | Action |
|------|--------|
| `src/test/java/.../UITransformTest.java` | Add 4 @Nested classes (39 tests) |
| `src/test/java/.../LayoutGroupTest.java` | Add 6 @Nested classes (44 tests) |

## Verification

```bash
mvn test -Dtest="UITransformTest,LayoutGroupTest"
```

All 119 tests (36 existing + 83 new) must pass.
