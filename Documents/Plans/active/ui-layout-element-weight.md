# UILayoutElement — Layout Weight System

**Status:** Reviewed — Ready for Implementation
**Date:** 2026-03-21

## Overview

Add a `UILayoutElement` component that allows children of horizontal/vertical layout groups to receive a proportional share of remaining space via a `layoutWeight` field. This is analogous to CSS `flex-grow` or Android's `layout_weight`.

## Component Design

New component: `UILayoutElement` in `com.pocket.rpg.components.ui`, annotated `@ComponentMeta(category = "UI")`.

**Fields:**
- `float layoutWeight = 1` — proportional share of remaining space on the layout axis. Default is 1 so the component has an immediate effect when added.

The component is passive — it holds data, the parent layout group reads it. No lifecycle methods needed.

**Registration:** Automatic via `ComponentRegistry` classpath scanning.

**Serialization:** Automatic via `ComponentTypeAdapterFactory`. Old scenes without `UILayoutElement` are unaffected.

## Acceptance Scenarios

### Scenario 1: Header + Content + Footer (Vertical)

Container: 400x300, VerticalLayoutGroup, padding 0, spacing 0.

```
┌──────────────────────────────────────┐
│ Header (fixed 50px)                  │  h = 50
├──────────────────────────────────────┤
│                                      │
│ Content (weight: 3)                  │  h = 187.5  (250 * 3/4)
│                                      │
├──────────────────────────────────────┤
│ Footer (weight: 1)                   │  h = 62.5   (250 * 1/4)
└──────────────────────────────────────┘
         remaining = 300 - 50 = 250
```

### Scenario 2: Sidebar + Main (Horizontal)

Container: 600x400, HorizontalLayoutGroup, padding 10, spacing 5.

```
contentWidth = 600 - 10 - 10 = 580
remaining = 580 - 5 = 575  (1 spacing gap)

┌─────────────┬────────────────────────────────────────────┐
│             │                                            │
│  Sidebar    │                                            │
│  weight: 1  │  Main                                      │
│             │  weight: 3                                  │
│  w = 143.75 │  w = 431.25                                │
│  (575*1/4)  │  (575*3/4)                                 │
│             │                                            │
└─────────────┴────────────────────────────────────────────┘
  x=10          x=10+143.75+5=158.75
```

### Scenario 3: Icon + Label + Value (Horizontal, mixed)

Container: 400x40, HorizontalLayoutGroup, padding 0, spacing 5.

```
contentWidth = 400, spacing = 5*2 = 10
remaining = 400 - 10 - 40 = 350  (icon is fixed)

┌──────┬────────────────────────┬────────────────────────┐
│ Icon │        Label           │        Value           │
│ 40px │      weight: 1         │      weight: 1         │
│fixed │      w = 175           │      w = 175           │
│      │      (350 * 1/2)       │      (350 * 1/2)       │
└──────┴────────────────────────┴────────────────────────┘
 x=0    x=45                     x=225
```

### Scenario 4: Percent + Weighted (Vertical)

Container: 400x200, VerticalLayoutGroup, padding 0, spacing 0.

```
PERCENT child resolves first: 30% of 200 = 60
remaining = 200 - 60 = 140

┌──────────────────────────────────────┐
│ Banner (PERCENT 30%)                 │  h = 60
├──────────────────────────────────────┤
│                                      │
│ Content (weight: 1)                  │  h = 140
│                                      │
└──────────────────────────────────────┘
```

### Scenario 5: Negative remaining space

Container: 400x100, VerticalLayoutGroup, padding 0, spacing 0.

```
Fixed children exceed container. Weighted child clamped to 0.

┌──────────────────────────────────────┐
│ Header (fixed 60px)                  │  h = 60
├──────────────────────────────────────┤
│ Body (fixed 80px)                    │  h = 80   (overflows)
├──────────────────────────────────────┤
│ Spacer (weight: 1)                   │  h = 0    (remaining < 0 → 0)
└──────────────────────────────────────┘
  remaining = 100 - 60 - 80 = -40 → clamped to 0
```

## Layout Algorithm Change

The layout axis gains a pre-scan before the existing positioning loop. Cross-axis logic is untouched.

### Current (layout axis):

```
for each child:
    if forceExpand → equal share
    else if PERCENT → percentage of (content - spacing)
    else → fixed size
    position child
```

### New:

```
// Pre-scan: measure non-weighted children, collect weights
float[] sizes = new float[children.size()]
for i, child in children:
    UILayoutElement le = child.getComponent(UILayoutElement.class)
    float weight = (le != null) ? le.getLayoutWeight() : 0

    if layout-axis forceExpand is ON OR weight <= 0:
        sizes[i] = resolve as before (forceExpand/percent/fixed)
        consumedSpace += sizes[i]
    else:
        sizes[i] = -weight              // negative = marker for weighted
        totalWeight += weight

remainingSpace = max(0, (content - spacing) - consumedSpace)

// Positioning loop (same structure as current, uses sizes[])
for i, child in children:
    // Layout axis: use pre-scanned size or distribute remaining
    if sizes[i] < 0:                    // weighted
        layoutAxisSize = remainingSpace * (-sizes[i] / totalWeight)
    else:
        layoutAxisSize = sizes[i]

    // Cross axis: resolve exactly as current code (no weight involvement)
    // forceExpand → stretch to content, PERCENT → percentage of content, else → fixed
    crossAxisSize = resolve cross axis as before

    // position, overrides, cursor — identical to current code
    set anchor(0,0), pivot(0,0), offset (alignment uses crossAxisSize)
    setLayoutPercentReference(crossBase, layoutBase)
    if weighted OR forceExpand OR PERCENT:
        setLayoutOverride on layout axis(layoutAxisSize)
    if weighted OR forceExpand:
        set raw field on layout axis(layoutAxisSize)
    // cross-axis overrides unchanged (same conditions as current code)
    cursor += layoutAxisSize + spacing
```

The `getComponent` call happens once per child in the pre-scan. The positioning loop uses the cached `sizes[]` array.

### ForceExpand axis clarification

- Vertical layout: only `childForceExpandHeight` (layout axis) blocks weights. `childForceExpandWidth` (cross axis) is independent.
- Horizontal layout: only `childForceExpandWidth` (layout axis) blocks weights. `childForceExpandHeight` (cross axis) is independent.

### Priority order (highest wins):

1. `childForceExpand` on layout axis — equal distribution, weights ignored
2. `layoutWeight > 0` — proportional distribution of remaining space
3. PERCENT mode — percentage of (content - spacing)
4. FIXED mode — raw pixel size

### Negative weight

Clamped in the algorithm (`weight <= 0` → not weighted). Inspector shows the raw value so the user can notice and fix it.

### Negative remaining space

Clamped to 0 before distributing. Weighted children get size 0, still consume spacing (same as a 0-height fixed child).

### `setWidth/Height` and undo

`setWidth`/`setHeight` are direct field setters called by layout code every frame — they do NOT create undo entries. Only inspector edits go through `SetterUndoCommand`. When `UILayoutElement` is removed, the child's raw `width`/`height` retains the last layout-synced value, so there's no visual snap.

## `getChildDriverInfo`

Override in each subclass (not the base class), so each references its layout-axis forceExpand flag:

**UIVerticalLayoutGroup:**
```
heightDriven = childForceExpandHeight || hasWeight(child)
widthDriven = childForceExpandWidth
```

**UIHorizontalLayoutGroup:**
```
widthDriven = childForceExpandWidth || hasWeight(child)
heightDriven = childForceExpandHeight
```

Where `hasWeight(child)` is a `protected` method on `LayoutGroup` (shared by both subclasses, avoids duplication):
```
protected boolean hasWeight(GameObject child) {
    UILayoutElement le = child.getComponent(UILayoutElement.class);
    return le != null && le.getLayoutWeight() > 0;
}
```

Grid layout keeps its current implementation (both axes always driven).

## UILayoutElement Inspector

Extend `CustomComponentInspector<UILayoutElement>`, annotate with `@InspectorFor(UILayoutElement.class)`. Auto-discovered by `CustomComponentEditorRegistry` at startup (same pattern as `DoorInspector`, `PokemonStorageInspector`, etc.).

```
┌─ UILayoutElement ───────────────────────────┐
│                                             │
│  Weight    [====== 2.0 ======]              │
│                                             │
│  Resolved Size   187.5 x 400.0  (disabled)  │
│                                             │
└─────────────────────────────────────────────┘
```

When parent has layout-axis forceExpand ON:

```
┌─ UILayoutElement ───────────────────────────┐
│                                             │
│  Weight    [====== 2.0 ======]  (disabled)  │
│                                             │
│  Weight ignored — parent uses Force Expand  │
│                                             │
└─────────────────────────────────────────────┘
```

When no parent layout group:

```
┌─ UILayoutElement ───────────────────────────┐
│                                             │
│  Weight    [====== 1.0 ======]              │
│                                             │
│  No parent layout group                     │
│                                             │
└─────────────────────────────────────────────┘
```

**Implementation detail:** The inspector walks up to `gameObject.getParent()` and checks for `LayoutGroup` subclass. If found, checks the layout-axis forceExpand flag. This is the same pattern `TransformDriverDetector` uses — no new traversal infrastructure needed.

## Discoverability

**V1:** Tooltip on the layout group inspector's forceExpand checkboxes: *"For proportional sizing, add a UILayoutElement component to individual children."*

**Future:** Collapsed "Layout Weight" row in the child's UITransform inspector when inside a layout group, with an "Add UILayoutElement" button.

## Impact on Existing Systems

| System | Impact | Detail |
|--------|--------|--------|
| **Layout algorithm** | Modified | Pre-scan + positioning loop in Vertical + Horizontal. Grid untouched. |
| **`getChildDriverInfo`** | Modified | Overridden in each subclass to check child for UILayoutElement. |
| **TransformDriverDetector** | None | Already iterates parent components. |
| **UITransformInspector** | None | Already disables fields based on `TransformDriverInfo`. |
| **Layout overrides** | Modified | Weighted children get `setLayoutOverride` + `setWidth/Height` sync, same as forceExpand. |
| **Percent reference** | None | Called unconditionally with same values. |
| **Serialization** | None | Automatic. |
| **Component browser** | None | Automatic via `@ComponentMeta`. |
| **UILayoutElement inspector** | New | Weight field + resolved size + conflict warning. |
| **Layout group inspectors** | Modified | Tooltip on forceExpand. |
| **contentExtentHeight** | None | Cursor accumulates resolved sizes. |
| **ScrollView** | None | Independent system. |

## Edge Cases

| Case | Behavior |
|------|----------|
| All children weighted, no fixed | Entire (content - spacing) distributed by weight |
| All children fixed, none weighted | Identical to current behavior |
| Remaining space negative | Clamped to 0. Weighted children get size 0. |
| Single weighted child | Gets all remaining space |
| Weight = 0 on UILayoutElement | Not weighted — same as no component |
| Negative weight | Clamped in algorithm, treated as not weighted |
| UILayoutElement without parent layout | Does nothing |
| UILayoutElement on Grid child | Ignored |
| Layout-axis forceExpand + weight | ForceExpand wins |
| Cross-axis forceExpand + weight | Weights apply normally |
| PERCENT + weighted mixed | PERCENT resolves first, weighted gets remainder |
| Child has PERCENT mode + weight > 0 | Weight wins on layout axis |
| Disabled weighted child | Excluded, siblings redistribute |

## Testing Plan

Unit tests in `LayoutGroupTest.java`, new `WeightedLayout` nested class.

### Core distribution
1. Two weighted (1:1) — equal split
2. Two weighted (1:2) — 1/3 and 2/3
3. Matches Scenario 1 numbers: header 50px fixed + content w:3 + footer w:1 in 300px → 187.5 + 62.5

### Mixed with fixed
4. Fixed + weighted — weighted gets remainder
5. Fixed + weighted + fixed order — positions correct
6. Matches Scenario 3 numbers: icon 40px + label w:1 + value w:1 in 400px, spacing 5

### Mixed with percent
7. PERCENT + weighted — PERCENT first, weighted gets remainder
8. Matches Scenario 4 numbers: banner 30% + content w:1 in 200px → 60 + 140

### Priority/interaction
9. Layout-axis forceExpand ON + weight — forceExpand wins
10. Cross-axis forceExpand ON + layout-axis weight — weights apply
11. Weight = 0 — not weighted
12. Child has PERCENT mode + weight > 0 — weight wins

### Edge cases
13. All weighted, no fixed — full area distributed
14. Remaining space 0 — weighted get 0
15. Remaining space negative (Scenario 5) — weighted get 0, no crash
16. Disabled weighted child — siblings redistribute

### Integration
17. Weighted child containing inner layout group — inner resolves against computed size
18. Cross-axis of weighted child unaffected (FIXED stays, PERCENT resolves against content)
19. `applyLayout()` idempotent — two calls produce same result
20. Grid layout ignores UILayoutElement

### Driver info
21. Weighted child → layout axis driven, cross axis unchanged
22. Non-weighted sibling in same group → not driven
23. Weight = 0 → not driven

### contentExtentHeight
24. Vertical: extent includes weighted children's resolved sizes
25. Horizontal: extent unaffected by weights

### Regression
All existing tests pass unchanged.

## Files Changed

| File | Change |
|------|--------|
| `components/ui/UILayoutElement.java` | **New** — component with `layoutWeight` field (default 1) |
| `components/ui/UIVerticalLayoutGroup.java` | **Modified** — pre-scan + override `getChildDriverInfo` |
| `components/ui/UIHorizontalLayoutGroup.java` | **Modified** — pre-scan + override `getChildDriverInfo` |
| `components/ui/LayoutGroup.java` | **Modified** — base `getChildDriverInfo` kept for grid, subclasses override |
| `editor/ui/inspectors/UILayoutElementInspector.java` | **New** — weight + resolved size + conflict warning |
| `editor/ui/inspectors/LayoutGroupInspectorBase.java` | **Modified** — tooltip on forceExpand |
| `test/.../LayoutGroupTest.java` | **Modified** — add WeightedLayout (25 tests) |

**Not changed:** UITransform, TransformDriverDetector, UITransformInspector, serialization, ComponentRegistry, grid layout, ScrollView.
