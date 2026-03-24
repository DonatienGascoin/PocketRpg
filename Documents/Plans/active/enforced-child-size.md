# Enforced Child Size in Horizontal/Vertical Layout Groups

**Status:** Reviewed — Ready for Implementation
**Date:** 2026-03-24

## Overview

Add `childWidthMode`/`childHeightMode` (NONE/FIXED/PERCENT) + `childWidth`/`childHeight` fields to `LayoutGroup`, allowing the layout group to enforce a specific size on all children — on either or both axes. This mirrors the proven `cellWidth`/`cellHeight` pattern from `UIGridLayoutGroup`.

## Motivation

Currently, to make all children in a vertical list 48px tall, you must manually set each child's UITransform height. When children are added/removed, each one must be configured individually. The layout group should own this constraint — the same way `UIGridLayoutGroup` owns `cellWidth`/`cellHeight`.

**Use cases:**
- Inventory list rows: every item row exactly 48px tall
- Tab bar: each tab exactly 120px wide in a horizontal layout
- Toolbar: all buttons forced to 32px height (cross-axis)
- Equal-percent layouts: each child takes exactly 25% without needing `childForceExpand`

## Component Design

New enum and fields on `LayoutGroup` (base class):

```java
public enum ChildSizeMode { NONE, FIXED, PERCENT }

@Getter @Setter
protected ChildSizeMode childWidthMode = ChildSizeMode.NONE;

@Getter @Setter
protected float childWidth = 0;

@Getter @Setter
protected ChildSizeMode childHeightMode = ChildSizeMode.NONE;

@Getter @Setter
protected float childHeight = 0;
```

Default `NONE` = no enforcement (current behavior). Serialization is automatic — old scenes without these fields get `NONE` via `ComponentTypeAdapterFactory` (unknown fields silently ignored).

## Acceptance Scenarios

### Scenario 1: Uniform Row Heights (Vertical Layout)

Container: 400x300, VerticalLayoutGroup, `childHeightMode=FIXED`, `childHeight=50`, spacing=5.

```
3 children, totalSpacing = 10

┌──────────────────────────────────────┐
│ Child A                              │  h = 50  (enforced)
├──────────────────────────────────────┤  spacing = 5
│ Child B                              │  h = 50  (enforced)
├──────────────────────────────────────┤  spacing = 5
│ Child C                              │  h = 50  (enforced)
└──────────────────────────────────────┘
  y: 0, 55, 110
  Child widths: each child's own UITransform (not enforced)
```

### Scenario 2: Percent Cross-Axis (Horizontal Layout)

Container: 600x100, HorizontalLayoutGroup, `childHeightMode=PERCENT`, `childHeight=80`.

```
contentHeight = 100
Each child height = 100 * 80/100 = 80px

┌─────────────┬─────────────┬─────────────┐
│             │             │             │  h = 80 each
│  Child A    │  Child B    │  Child C    │  (80% of 100)
│  (100px w)  │  (150px w)  │  (200px w)  │
└─────────────┴─────────────┴─────────────┘
  Widths: each child's own UITransform (not enforced)
```

### Scenario 3: Fixed Layout-Axis Width (Horizontal Layout)

Container: 500x200, HorizontalLayoutGroup, `childWidthMode=FIXED`, `childWidth=120`, spacing=10.

```
3 children, totalSpacing = 20

┌──────────┐  ┌──────────┐  ┌──────────┐
│  120px   │10│  120px   │10│  120px   │
│  Child A │  │  Child B │  │  Child C │
└──────────┘  └──────────┘  └──────────┘
  x: 0, 130, 260
  Heights: each child's own UITransform (not enforced)
```

### Scenario 4: Percent Layout-Axis (Vertical Layout)

Container: 400x400, VerticalLayoutGroup, `childHeightMode=PERCENT`, `childHeight=30`, spacing=0.

```
contentHeight = 400
Each child height = (400 - 0) * 30/100 = 120px

┌──────────────────────────────────────┐
│ Child A                              │  h = 120  (30%)
├──────────────────────────────────────┤
│ Child B                              │  h = 120  (30%)
├──────────────────────────────────────┤
│ Child C                              │  h = 120  (30%)
└──────────────────────────────────────┘
  remaining 40px unused
```

### Scenario 5: Both Axes Enforced

Container: 400x300, VerticalLayoutGroup, `childWidthMode=FIXED`, `childWidth=200`, `childHeightMode=FIXED`, `childHeight=60`, `childAlignment=CENTER`.

```
┌────────────────────────────────────────┐
│         ┌──── 200px ────┐              │
│         │  Child A  h=60│  centered    │
│         └───────────────┘              │
│         ┌───────────────┐              │
│         │  Child B  h=60│  centered    │
│         └───────────────┘              │
└────────────────────────────────────────┘
```

## Layout Algorithm Changes

### Priority Order (highest wins)

| Priority | Mechanism | Scope |
|----------|-----------|-------|
| 1 | `childWidthMode/childHeightMode != NONE` | All children, layout group |
| 2 | `childForceExpand` | All children, layout group |
| 3 | `layoutWeight > 0` (future UILayoutElement) | Per-child |
| 4 | PERCENT mode | Per-child, UITransform |
| 5 | FIXED mode | Per-child, UITransform |

### UIHorizontalLayoutGroup — `applyLayout()` Changes

Enforced-size check goes **before** the existing `childForceExpand` check, on each axis independently.

**Layout axis (width):**
```java
float childWidth;
if (childWidthMode == ChildSizeMode.FIXED) {
    childWidth = this.childWidth;
} else if (childWidthMode == ChildSizeMode.PERCENT) {
    childWidth = (contentWidth - totalSpacing) * this.childWidth / 100f;
} else if (childForceExpandWidth) {
    childWidth = expandedWidth;
} else if (ct.getWidthMode() == SizeMode.PERCENT) {
    childWidth = (contentWidth - totalSpacing) * ct.getWidthPercent() / 100f;
} else {
    childWidth = ct.getEffectiveWidth();
}
```

**Cross axis (height):**
```java
float childHeight;
if (childHeightMode == ChildSizeMode.FIXED) {
    childHeight = this.childHeight;
} else if (childHeightMode == ChildSizeMode.PERCENT) {
    childHeight = contentHeight * this.childHeight / 100f;
} else if (childForceExpandHeight) {
    childHeight = contentHeight;
} else if (ct.getHeightMode() == SizeMode.PERCENT) {
    childHeight = contentHeight * ct.getHeightPercent() / 100f;
} else {
    childHeight = ct.getEffectiveHeight();
}
```

**Layout overrides — add enforced-size to conditions:**
```java
// Width override
if (childWidthMode != ChildSizeMode.NONE || childForceExpandWidth || ct.getWidthMode() == SizeMode.PERCENT) {
    ct.setLayoutOverrideWidth(childWidth);
}
// Height override
if (childHeightMode != ChildSizeMode.NONE || childForceExpandHeight || ct.getHeightMode() == SizeMode.PERCENT) {
    ct.setLayoutOverrideHeight(childHeight);
}
```

**Raw field sync** (for graceful fallback when feature is disabled):
```java
if (childWidthMode != ChildSizeMode.NONE || childForceExpandWidth) {
    ct.setWidth(childWidth);
}
if (childHeightMode != ChildSizeMode.NONE || childForceExpandHeight) {
    ct.setHeight(childHeight);
}
```

### UIVerticalLayoutGroup — Mirror of Horizontal

Identical structure with axes swapped (layout axis = height, cross axis = width).

**Layout axis (height) percent base:** `(contentHeight - totalSpacing) * this.childHeight / 100f`
**Cross axis (width) percent base:** `contentWidth * this.childWidth / 100f`

### Percent Reference Base

No changes needed. `setLayoutPercentReference()` already uses the correct bases:
- Horizontal: `(contentWidth - totalSpacing, contentHeight)`
- Vertical: `(contentWidth, contentHeight - totalSpacing)`

### Grid Layout — Untouched

`UIGridLayoutGroup` has its own `cellWidth`/`cellHeight` and overrides `applyLayout()` entirely. The new base-class fields are inherited but ignored by grid's layout logic.

## `getChildDriverInfo()` Changes

Update existing implementation in `LayoutGroup` base class:

```java
@Override
public TransformDriverInfo getChildDriverInfo(GameObject child) {
    boolean isGrid = this instanceof UIGridLayoutGroup;
    boolean widthDriven = isGrid || childForceExpandWidth || childWidthMode != ChildSizeMode.NONE;
    boolean heightDriven = isGrid || childForceExpandHeight || childHeightMode != ChildSizeMode.NONE;
    return new TransformDriverInfo(false, widthDriven, heightDriven, true, "parent layout");
}
```

This disables width/height fields in the child's UITransform inspector when the parent layout enforces that axis.

**Future UILayoutElement integration:** When weights are added, subclasses will override this method and add `|| hasWeight(child)` to the layout-axis driven check.

## Inspector Changes

### `LayoutGroupInspectorBase` — New `drawChildSize()` Method

Force Expand and Child Size are grouped in the **same section** (matching the grid inspector's pattern) so the override relationship is spatially obvious. Each child-size axis is a single row: label on the left, dropdown + float field on the right.

```
┌─ UIVerticalLayoutGroup ─────────────────────────────────┐
│                                                          │
│  Alignment       [LEFT ▼]                                │
│                                                          │
│  Child Size                                              │
│  ──────────────────────────────────────────────────────  │
│  Force Width     [☐]                                     │
│  Force Height    [☐]          ← disabled when height     │
│                                  mode != NONE            │
│  Child Width     [NONE ▼]                                │
│  Child Height    [FIXED ▼] [=== 48 ===]                  │
│                  ↑ dropdown  ↑ float field (inline)      │
│                  (when NONE, float field hidden)          │
│ ──────────────────────────────────────────────────────── │
│  Spacing         [=== 5 ===]                             │
│  Padding                                                 │
│    Left [0]  Right [0]  Top [0]  Bottom [0]              │
└──────────────────────────────────────────────────────────┘
```

**Implementation — `drawChildSize()` on `LayoutGroupInspectorBase`:**

Uses `inspectorRow` with a custom field Runnable that draws the enum combo + optional float on the same line. The combo and float **must have distinct `##` IDs** to avoid ImGui ID conflicts (e.g., `"##childWidthMode"` and `"##childWidth"`).

```java
protected boolean drawChildSize() {
    // Skip for grid — it has its own cellWidth/cellHeight
    if (component instanceof UIGridLayoutGroup) return false;

    boolean changed = false;

    // Child Width row: [dropdown] [optional float]
    changed |= drawChildSizeRow("Child Width", component, "childWidthMode", "childWidth",
            ChildSizeMode.class);

    // Child Height row: [dropdown] [optional float]
    changed |= drawChildSizeRow("Child Height", component, "childHeightMode", "childHeight",
            ChildSizeMode.class);

    return changed;
}

private boolean drawChildSizeRow(String label, LayoutGroup comp, String modeField,
                                  String valueField, Class<?> enumClass) {
    boolean changed = false;
    final boolean[] changedRef = {false};
    FieldEditors.inspectorRow(label, () -> {
        ChildSizeMode mode = /* getter for modeField */;

        // Dropdown takes ~half the available width when value field is shown
        float availWidth = ImGui.getContentRegionAvailX();
        if (mode != ChildSizeMode.NONE) {
            ImGui.setNextItemWidth(availWidth * 0.45f);
        } else {
            ImGui.setNextItemWidth(availWidth);
        }
        // Draw enum combo inline — use "##modeField" for unique ID
        changedRef[0] |= /* draw combo for modeField with undo */;

        if (mode != ChildSizeMode.NONE) {
            ImGui.sameLine();
            ImGui.setNextItemWidth(availWidth * 0.5f);
            // Use "##valueField" for unique ID, show "%" suffix in PERCENT mode
            String format = (mode == ChildSizeMode.PERCENT) ? "%.1f %%" : "%.1f";
            changedRef[0] |= /* draw float drag for valueField with undo */;
        }
    });
    return changedRef[0];
}
```

The exact implementation will use `EnumEditor` and `PrimitiveEditors` raw methods (or direct ImGui calls with undo wrappers) rather than `FieldEditors.drawEnum`/`drawFloat`, since those high-level methods each call `inspectorRow` internally — and we need both widgets inside a single `inspectorRow`.

### `drawForceExpand()` — Disable When Axis Enforced

```java
protected boolean drawForceExpand() {
    boolean changed = false;

    if (component.getChildWidthMode() != ChildSizeMode.NONE) {
        FieldEditors.inspectorRow("Force Width", () ->
                ImGui.textDisabled("Controlled by Child Width"));
    } else {
        changed |= FieldEditors.drawBoolean("Force Width", component, "childForceExpandWidth");
    }

    if (component.getChildHeightMode() != ChildSizeMode.NONE) {
        FieldEditors.inspectorRow("Force Height", () ->
                ImGui.textDisabled("Controlled by Child Height"));
    } else {
        changed |= FieldEditors.drawBoolean("Force Height", component, "childForceExpandHeight");
    }

    return changed;
}
```

### `drawSpecificFields()` Changes — Grouped "Child Size" Section

Force Expand and Child Size now live together in a single section within `drawSpecificFields()`:

```java
// In UIVerticalLayoutGroupInspector.drawSpecificFields():
changed |= FieldEditors.drawEnum("Alignment", component, "childAlignment", ...);

ImGui.spacing();
ImGui.text("Child Size");
ImGui.separator();
changed |= drawForceExpand();
changed |= drawChildSize();
```

The base `draw()` method no longer calls `drawChildSize()` separately — it moves into the subclass-specific section:

```java
// LayoutGroupInspectorBase.draw():
@Override
public boolean draw() {
    boolean changed = false;
    changed |= drawSpecificFields();   // includes alignment + force expand + child size
    ImGui.spacing();
    ImGui.separator();
    ImGui.spacing();
    changed |= drawSpacing();
    changed |= drawPadding();
    return changed;
}
```

### Grid Inspector — No Changes Needed

`drawChildSize()` returns early for `UIGridLayoutGroup`. Grid keeps its existing `cellWidth`/`cellHeight` UI in `drawSpecificFields()`.

## Interaction with Future UILayoutElement Weights

The weight plan's pre-scan integrates cleanly — enforced size slots above forceExpand:

```
for each child:
    if enforced size on layout axis (childSizeMode != NONE):
        sizes[i] = enforced size (fixed px or percent)
        consumedSpace += sizes[i]
    else if forceExpand on layout axis:
        sizes[i] = equal share
        consumedSpace += sizes[i]
    else if weight > 0:
        sizes[i] = -weight  // marker for weighted
        totalWeight += weight
    else if PERCENT:
        sizes[i] = percent of content
        consumedSpace += sizes[i]
    else:
        sizes[i] = fixed size
        consumedSpace += sizes[i]
```

When `childSizeMode != NONE`, weights are irrelevant (all children get the same enforced size). The UILayoutElement inspector should show: "Weight ignored — parent enforces child size."

No dependencies — this plan can be implemented before, after, or concurrently with the weight plan.

## Edge Cases

| Case | Behavior |
|------|----------|
| `childWidth=0` with FIXED mode | All children get width 0 (valid) |
| Negative `childWidth` (e.g., -10) | No clamping — produces negative size. Inspector allows it; user can see and fix. |
| `childHeight=150` with PERCENT | 150% of content — overflows, no clamping |
| Percent per-child overflow (5 children each 25%) | Each gets 25% independently; total 125%, overflows. No clamping. |
| Enforced + forceExpand both set | Enforced wins (forceExpand disabled in inspector; in code, enforced checked first) |
| Enforced on one axis, forceExpand on other | Each axis independent |
| Mode switched FIXED → NONE | Children use `getEffectiveWidth/Height()` which falls through to their UITransform. Note: raw `width`/`height` field retains the last enforced value (same as forceExpand behavior). |
| Grid layout with these fields set | Ignored by grid's `applyLayout()`. `getChildDriverInfo()` is not affected because `isGrid` check dominates. Inspector skips rendering these fields for grid. |
| Empty children list | Existing guard returns early |
| Enforced size larger than container | Overflows, no clamping, no crash |
| Disabled child | Excluded from layout (existing `getLayoutChildren()` filter) |
| Child has PERCENT mode on UITransform + parent enforces FIXED | Enforced wins. UITransform inspector shows "parent layout" as driver (size field disabled). |
| Enforced size on child that is itself a layout group | Child gets enforced size; its own children resolve against that enforced size as their parent area. Works via existing `getEffectiveWidth/Height()` → layout override mechanism. |
| Single child with enforced size | Works correctly. `totalSpacing=0`, percent base = content area. |
| Container size = 0 (or negative after padding) | Content area can be negative; enforced FIXED still applies as absolute value, PERCENT resolves to negative. Same behavior as existing percent/forceExpand paths. |

## Testing Plan

New `@Nested class EnforcedChildSize` in `LayoutGroupTest.java`, using the existing `makeUI()`, `makePercentChild()`, and `tr()` helpers.

### Fixed size — layout axis (5 tests)

1. **VLayout fixed height** — 3 children, `childHeightMode=FIXED`, `childHeight=50`, spacing=5. Assert each child `getEffectiveHeight()=50`, y positions 0/55/110.
2. **HLayout fixed width** — 3 children, `childWidthMode=FIXED`, `childWidth=120`, spacing=10. Assert each child `getEffectiveWidth()=120`, x positions 0/130/260.
3. **Fixed size with padding** — VLayout, padding top=10, bottom=20. `childHeightMode=FIXED`, `childHeight=40`. Assert positions offset by paddingTop, enforced size unaffected by padding.
4. **Fixed size = 0** — `childHeight=0`. All children height 0, no crash.
5. **Single child** — VLayout, 1 child, `childHeightMode=FIXED`, `childHeight=80`. `totalSpacing=0`. Assert height=80, y=0.

### Fixed size — cross axis (3 tests)

6. **VLayout fixed cross-axis width** — `childWidthMode=FIXED`, `childWidth=200`, container=400, alignment CENTER. Assert each child width=200, x position=(400-200)/2=100.
7. **HLayout fixed cross-axis height** — `childHeightMode=FIXED`, `childHeight=40`, container height=100. Assert each child height=40.
8. **Both axes fixed** — `childWidthMode=FIXED`, `childWidth=100`, `childHeightMode=FIXED`, `childHeight=50`. Assert both dimensions enforced.

### Children with varying native sizes (1 test)

9. **Varying native sizes converge** — 3 children with native heights 20, 80, 200. `childHeightMode=FIXED`, `childHeight=50`. Assert all `getEffectiveHeight()=50` — no child leaks its native size.

### Percent size (4 tests)

10. **VLayout percent height** — `childHeightMode=PERCENT`, `childHeight=30`, contentHeight=400, spacing=0. Each child = (400-0)*30/100 = 120px.
11. **HLayout percent width** — `childWidthMode=PERCENT`, `childWidth=25`, contentWidth=500, spacing=10, 3 children. Each child = (500-20)*25/100 = 120px.
12. **Percent cross-axis** — HLayout, `childHeightMode=PERCENT`, `childHeight=80`, contentHeight=100. Each child height=80.
13. **Percent > 100** — `childHeightMode=PERCENT`, `childHeight=150`. No clamping, children overflow.

### Priority & interaction (5 tests)

14. **Enforced beats forceExpand** — Set `childHeightMode=FIXED`, `childHeight=50` AND `childForceExpandHeight=true` on VLayout. Enforced wins: each child height=50, not equal share.
15. **Enforced one axis + forceExpand other** — `childWidthMode=FIXED` + `childForceExpandHeight`. Each axis resolves independently.
16. **NONE = current behavior (regression)** — All modes NONE, children use own UITransform sizes. Identical to existing tests.
17. **Switch FIXED → NONE mid-test** — Apply with FIXED, verify enforced. Set to NONE, reapply. Assert `getEffectiveWidth/Height()` returns child's current raw value (which is the previously-enforced value, not the original — same graceful-fallback as forceExpand).
18. **PERCENT-mode child overridden by parent FIXED** — Child has `widthMode=PERCENT`, `widthPercent=50`. Parent enforces `childWidthMode=FIXED`, `childWidth=100`. Assert `getEffectiveWidth()=100`. The UITransform's driver info (`widthDriven=true`) ensures the inspector shows the size as managed by "parent layout".

### Driver info (3 tests)

19. **Width enforced → widthDriven** — `childWidthMode=FIXED`, verify `getChildDriverInfo(child).widthDriven() == true`.
20. **Height enforced → heightDriven** — `childHeightMode=PERCENT`, verify `getChildDriverInfo(child).heightDriven() == true`.
21. **NONE + no forceExpand → not driven** — Both modes NONE, both forceExpand false. `widthDriven=false`, `heightDriven=false`.

### Layout overrides & sync (4 tests)

22. **Enforced sets layoutOverride** — Child has FIXED width=200 in UITransform. Parent enforces width=100. `getEffectiveWidth()` returns 100 (override takes precedence).
23. **Enforced syncs raw field** — After layout, `ct.getWidth()` equals enforced value (graceful fallback if feature removed).
24. **contentExtentHeight correct (VLayout)** — 3 children, `childHeight=50`, spacing=5, paddingTop=10, paddingBottom=20. Extent = 10 + 50 + 5 + 50 + 5 + 50 + 20 = 190.
25. **contentExtentHeight correct (HLayout)** — HLayout with enforced `childHeightMode=FIXED`, `childHeight=40`, container height=100. Extent = paddingTop + contentHeight + paddingBottom (cross-axis enforcement does not change extent formula).

### Nested layout (1 test)

26. **Enforced size on child containing inner layout** — Parent VLayout enforces `childHeight=100`. Child is a container with its own HLayout and two grandchildren. After both layouts apply, grandchildren resolve against the child's enforced 100px height.

### Grid isolation (1 test)

27. **Grid ignores enforced fields** — Set `childWidthMode=FIXED`, `childWidth=999` on grid. Children still use `cellWidth`/`cellHeight`.

**Total: 27 tests**

## Files Changed

| File | Change |
|------|--------|
| `components/ui/LayoutGroup.java` | **Modified** — add `ChildSizeMode` enum, 4 fields, update `getChildDriverInfo()` |
| `components/ui/UIHorizontalLayoutGroup.java` | **Modified** — enforced-size branches in `applyLayout()` |
| `components/ui/UIVerticalLayoutGroup.java` | **Modified** — enforced-size branches in `applyLayout()` |
| `editor/ui/inspectors/LayoutGroupInspectorBase.java` | **Modified** — add `drawChildSize()`, update `drawForceExpand()`, update `draw()` |
| `test/.../LayoutGroupTest.java` | **Modified** — add `EnforcedChildSize` nested class (27 tests) |

**Not changed:** UITransform, TransformDriverDetector, UITransformInspector, UIGridLayoutGroup, UIGridLayoutGroupInspector, serialization, ComponentRegistry, ScrollView, UILayoutElement plan.

## Cross-Reference: UILayoutElement Weight Plan

The weight plan (`Documents/Plans/active/ui-layout-element-weight.md`) should be updated to include this case in its UILayoutElement inspector section:

> When parent has enforced child size (`childWidthMode/childHeightMode != NONE`) on the layout axis, the UILayoutElement inspector should show: "Weight ignored — parent enforces child size."

This is the weight plan's responsibility to implement when it ships, but is documented here for traceability.

## Review Notes (2026-03-24)

Reviewed by QA Engineer, Product Owner, and Senior Software Engineer. All approved. Changes incorporated:

**From QA:**
- Added 5 tests: single child (#5), varying native sizes (#9), PERCENT child overridden by FIXED (#18), HLayout extent (#25), nested layout (#26)
- Expanded edge case table: negative values, PERCENT child override, nested layouts, single child, container size=0
- Clarified test #17 (FIXED→NONE): raw field retains enforced value, not original

**From PO:**
- Regrouped inspector: Force Expand and Child Size now in the same "Child Size" section within `drawSpecificFields()` (matches grid inspector pattern)
- Kept PERCENT mode (user decision) — serves equal-percent layouts not covered by forceExpand
- Note: zero-default when switching NONE→FIXED is accepted behavior (same as grid's cellWidth/cellHeight pattern)

**From Senior Engineer:**
- Added ImGui ID conflict note: combo and float in the same `inspectorRow` must use distinct `##` suffixes
- Confirmed algorithm correctness, serialization safety, and clean weight plan composition
- `instanceof UIGridLayoutGroup` pattern is pre-existing; acceptable pragmatic choice
