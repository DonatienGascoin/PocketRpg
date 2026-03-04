# UI Layout & Percentage Sizing Bug Fixes

## Overview

Three related bugs in the UI layout system where percentage-based sizing doesn't account for layout context (padding, spacing) and where position calculation uses the wrong size value.

---

## Bug 1: Percentage children overflow parent with padding

**Location:** `UIVerticalLayoutGroup.applyLayout()` (line 57), `UIHorizontalLayoutGroup.applyLayout()` (line 56)

**Problem:** When `childForceExpandHeight` is false, the layout calls `ct.getEffectiveHeight()` to get each child's size. For PERCENT children, this resolves against the **full parent size**, ignoring the layout group's padding.

**Example from MenuMockups.scene:**

```
PokemonMenu (VerticalLayoutGroup)
├── Size: 100% x 100% of parent (effective: 640 x 480)
├── Padding: top=25, bottom=25, left=25, right=50
├── childForceExpandHeight: false
│
├── PokemonHorizontalLayout (heightMode: PERCENT, heightPercent: 80%)
│   height = parentEffectiveHeight * 80% = 480 * 0.80 = 384px
│
└── DetailsLayout (heightMode: PERCENT, heightPercent: 20%)
    height = parentEffectiveHeight * 20% = 480 * 0.20 = 96px
```

**What happens:**
- Content area after padding = 480 - 25 (top) - 25 (bottom) = **430px**
- Total children height = 384 + 96 = **480px**
- **Overflow = 480 - 430 = 50px** (the padding is ignored)

**Expected:** 80% + 20% = 100% should fill exactly the content area (430px), not the full parent (480px).

---

## Bug 2: Spacing causes overflow with percentage children

**Location:** Same layout group methods.

**Problem:** When `childForceExpand` is false, spacing (in pixels) is added between children on top of their percentage-based sizes. The percentages resolve against the full parent, so spacing causes overflow.

**Example from MenuMockups.scene:**

```
DetailsLayout (HorizontalLayoutGroup)
├── Size: 100% width of parent (effective: ~565px)
├── spacing: 34px
├── childForceExpandWidth: false
│
├── Panel (widthMode: PERCENT, widthPercent: 70%)
│   width = parentEffectiveWidth * 70% = 565 * 0.70 = 395.5px
│
└── Panel (widthMode: PERCENT, widthPercent: 20%)
    width = parentEffectiveWidth * 20% = 565 * 0.20 = 113px
```

**What happens:**
- Total = 395.5 + 113 + 34 (spacing) = **542.5px** (fits in this case)
- But if widths were 80% + 20%: 452 + 113 + 34 = **599px > 565px** = **overflow**
- Even 70% + 20% = 90%: the 10% "gap" is only 56.5px, but if spacing exceeds that, overflow occurs

**Expected:** Percentages should resolve against `contentWidth - totalSpacing`, so 80% + 20% = 100% of the space remaining after spacing is subtracted.

---

## Bug 3: PERCENT mode flips pivot behavior (affects images, text, all UI)

**Location:** `UITransform.calculatePosition()` (lines 687-688)

**Problem:** The position calculation uses the raw `width`/`height` fields for pivot offset, but the renderer uses `getEffectiveWidth()`/`getEffectiveHeight()`. When switching to PERCENT mode, the effective size changes but the position pivot offset still uses the old pixel value.

**Current code (UITransform.java line 687-688):**
```java
float scaledWidth = width * worldScale.x;        // BUG: uses raw 'width' field
float scaledHeight = height * worldScale.y;      // BUG: uses raw 'height' field

localX -= pivot.x * scaledWidth;   // pivot offset based on wrong size
localY -= pivot.y * scaledHeight;
```

**Renderer code (UIComponent.computeRenderBounds):**
```java
float w = transform.getEffectiveWidth() * scale.x;   // CORRECT: uses effective
float h = transform.getEffectiveHeight() * scale.y;

float x = pivotWorld.x - pivot.x * w;   // pivot offset based on effective size
float y = pivotWorld.y - pivot.y * h;
```

**Example:**

```
Element: anchor=(0,0), pivot=(0.5, 0), offset=(100, 50)
         width=200, height=30

FIXED mode (correct):
  getEffectiveWidth() = 200
  Position pivot offset: 0.5 * 200 = 100
  Render pivot offset:   0.5 * 200 = 100  ✓ match

Switch to PERCENT mode (widthPercent=100%, parent=640px):
  getEffectiveWidth() = 640
  Position pivot offset: 0.5 * 200 = 100    ← still uses raw 'width' field!
  Render pivot offset:   0.5 * 640 = 320    ← uses effective width

  Mismatch = 220px → element appears to "flip/jump"
```

The position says the pivot is at one location, but the renderer draws it from a different pivot point. With pivot (0,0) this is invisible (0 * anything = 0), but with any non-zero pivot the element visually shifts.

---

## Fix Plan

### Phase 1: Add layout override mechanism to UITransform

Add transient override fields so layout groups can tell a UITransform "your effective size is X" — overriding the default percentage-of-parent calculation. This ensures:
- Children in a layout resolve percentages against the correct available space
- Grandchildren also see the correct parent effective size

**Changes to `UITransform.java`:**

1. Add two transient fields after `uiMatrixDirty`:
   ```java
   private transient float layoutOverrideWidth = Float.NaN;
   private transient float layoutOverrideHeight = Float.NaN;
   ```

2. Modify `getEffectiveWidth()` — check override first:
   ```java
   public float getEffectiveWidth() {
       if (!Float.isNaN(layoutOverrideWidth)) return layoutOverrideWidth;
       if (widthMode == SizeMode.PERCENT) {
           return getParentWidth() * widthPercent / 100f;
       }
       return width;
   }
   ```

3. Same for `getEffectiveHeight()`.

4. Add public setters + clear method:
   ```java
   public void setLayoutOverrideWidth(float width) { ... }
   public void setLayoutOverrideHeight(float height) { ... }
   public void clearLayoutOverrides() { ... }
   ```

### Phase 2: Fix layout groups to resolve percentages against content area

**Changes to `UIVerticalLayoutGroup.java`:**

Compute `totalSpacing` upfront (currently only computed inside the `if (childForceExpandHeight)` block). For each child:

- **Layout axis (height):** If child is PERCENT, resolve against `(contentHeight - totalSpacing)` instead of raw parent height
- **Cross axis (width):** If child is PERCENT, resolve against `contentWidth` instead of raw parent width
- Set layout overrides on the child so `getEffectiveWidth/Height()` returns the corrected values

```java
// Before (buggy):
float childHeight = childForceExpandHeight ? expandedHeight : ct.getEffectiveHeight();
// ct.getEffectiveHeight() = parentHeight * percent / 100  ← ignores padding & spacing

// After (fixed):
float childHeight;
if (childForceExpandHeight) {
    childHeight = expandedHeight;
} else if (ct.getHeightMode() == UITransform.SizeMode.PERCENT) {
    childHeight = (contentHeight - totalSpacing) * ct.getHeightPercent() / 100f;
} else {
    childHeight = ct.getEffectiveHeight();
}
// Then: ct.setLayoutOverrideHeight(childHeight) for PERCENT/force-expand children
```

**Same pattern for `UIHorizontalLayoutGroup.java`** (axes swapped).

### Phase 3: Fix calculatePosition pivot mismatch

**Change in `UITransform.calculatePosition()`:**

```java
// Before (buggy):
float scaledWidth = width * worldScale.x;
float scaledHeight = height * worldScale.y;

// After (fixed):
float scaledWidth = getEffectiveWidth() * worldScale.x;
float scaledHeight = getEffectiveHeight() * worldScale.y;
```

This makes the position calculation agree with the renderer about pivot offset.

---

## Files to Modify

| File | Change |
|------|--------|
| `UITransform.java` | Add layout override fields, modify `getEffectiveWidth/Height`, fix `calculatePosition` pivot |
| `UIVerticalLayoutGroup.java` | Resolve PERCENT children against content area minus spacing, set overrides |
| `UIHorizontalLayoutGroup.java` | Same pattern, axes swapped |

## Testing

1. Open MenuMockups.scene — PokemonHorizontalLayout (80%) + DetailsLayout (20%) should fit within PokemonMenu padding
2. Increase DetailsLayout spacing — percentage children should shrink to accommodate spacing, not overflow
3. Create a UI element with non-zero pivot, switch width from FIXED to PERCENT — position should stay consistent (no visual flip)
4. Verify nested layouts (children of PokemonHorizontalLayout) still size correctly with the layout overrides flowing down

## Code Review

After implementation, verify:
- [ ] No serialization side effects (override fields are transient)
- [ ] Force-expand behavior unchanged for FIXED children
- [ ] Grid layout (`UIGridLayoutGroup`) — same pattern may be needed later, but not in scope now
