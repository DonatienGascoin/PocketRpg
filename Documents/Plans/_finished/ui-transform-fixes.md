# UI Transform System Fixes Plan

## Overview

This plan addresses three issues:
1. Rotation undo/redo giving random values
2. UIText rendering at wrong position and not rotating
3. UITransformInspector inconsistencies

---

## Issue 1: Rotation Undo/Redo Fix

### Root Cause
`UIRotationCommand` captures the result of `getRotation2D()` which is a **computed value**. When `matchParentRotation=true`, this returns the parent's rotation, not the stored `localRotation.z`. On undo, the wrong value is restored.

### Solution
Capture the actual stored field `localRotation.z` directly instead of the computed getter result.

### Files to Modify
- `src/main/java/com/pocket/rpg/editor/undo/commands/UIRotationCommand.java`
- `src/main/java/com/pocket/rpg/editor/ui/inspectors/UITransformInspector.java` (where command is created)

### Changes
1. In `UIRotationCommand` constructor, capture `transform.getLocalRotation().z` (the raw stored value)
2. In `execute()` and `undo()`, set `localRotation.z` directly via a new method or reflection

---

## Issue 2: UIText Rendering Fix

### Root Cause
In `UIDesignerRenderer.renderTextElement()` (line 228):
```java
transform.getScreenPosition().set(bounds[0], bounds[1]);
```
This fails because `getScreenPosition()` returns a **new Vector2f instance**, so the mutation has no effect. Text renders at (0,0).

### Solution
Option A: Add a public setter on UITransform for calculated position
Option B: Use the same parent-bounds-based approach as runtime (preferred)

### Files to Modify
- `src/main/java/com/pocket/rpg/components/ui/UITransform.java` - Add position override method
- `src/main/java/com/pocket/rpg/editor/panels/uidesigner/UIDesignerRenderer.java` - Use new method

### Changes
1. Add `setCalculatedPosition(float x, float y)` method to UITransform
2. In `renderTextElement()`, call `transform.setCalculatedPosition(bounds[0], bounds[1])` instead of the broken hack
3. Ensure rotation pivot is calculated correctly after position is set

---

## Issue 3: UITransformInspector Harmonization

### Current Problems
- Size uses `StretchMode` enum, hides fields when matching
- Rotation uses `matchParentRotation` boolean, disables fields
- Scale uses `matchParentScale` boolean, disables fields
- **Scale has NO undo support**
- Inconsistent "match parent" behavior and placement

### Proposed Unified Inspector Layout

```
+----------------------------------------------------------+
|  [UITransform]                                           |
+----------------------------------------------------------+
|                                                          |
|  ANCHOR                         PIVOT                    |
|  +-----+-----+-----+            +-----+-----+-----+      |
|  | TL  | TC  | TR  |            | TL  | TC  | TR  |      |
|  +-----+-----+-----+            +-----+-----+-----+      |
|  | ML  | MC  | MR  |            | ML  | MC  | MR  |      |
|  +-----+-----+-----+            +-----+-----+-----+      |
|  | BL  | BC  | BR  |            | BL  | BC  | BR  |      |
|  +-----+-----+-----+            +-----+-----+-----+      |
|  [  X: 0.5  ] [  Y: 0.5  ]      [  X: 0.5  ] [  Y: 0.5  ]|
|                                                          |
+----------------------------------------------------------+
|                                                          |
|  Offset      [R]  [    X: 0    ] [    Y: 0    ]         |
|                                                          |
|  +------------------------------------------------------+|
|  |          [  MATCH PARENT  ]  (toggle all)            ||
|  +------------------------------------------------------+|
|                                                          |
|  Size        [M]  [  W: 100   ] [  H: 100   ] [Lock]    |
|                                                          |
|  Rotation    [M]  [    0.0 deg                ]         |
|                                                          |
|  Scale       [M]  [   X: 1.0  ] [   Y: 1.0   ] [Uni]    |
|                                                          |
+----------------------------------------------------------+

Legend:
  [R] = Reset button (resets to default value)
  [M] = Match Parent toggle button (per-field)
        - Grayed out if no parent UITransform
        - When ON: button has ACCENT COLOR (e.g., blue/green)
        - When ON: field becomes read-only, shows inherited value
  [MATCH PARENT] = Master toggle that enables/disables ALL match parent options
        - When ANY [M] is on: button shows accent color
        - Click toggles all [M] buttons together
  [Lock] = Aspect ratio lock
  [Uni] = Uniform scale (sync X and Y)

Button Colors:
  - OFF state: Default ImGui button color (gray)
  - ON state: Accent color (configurable, e.g., ImGuiCol_ButtonActive or custom)
```

### Key Design Decisions

1. **Editable Anchor/Pivot Fields**: X/Y input fields directly below each 3x3 grid
2. **Master "Match Parent" Button**: Between Offset and Size, toggles all match options
3. **Per-Field Match Parent Buttons**: [M] between label and value for granular control
4. **Visual Feedback**: ON state uses accent color for easy identification
5. **Uniform Behavior**: When "Match Parent" is ON:
   - Button shows accent color
   - Fields become disabled (grayed out)
   - Fields still show the current inherited value (not hidden)
6. **Offset has Reset only**: Offset doesn't have match parent (no concept of "parent offset")
7. **All fields have undo support**: Add missing scale undo

### Files to Modify
- `src/main/java/com/pocket/rpg/editor/ui/inspectors/UITransformInspector.java` - Complete refactor
- `src/main/java/com/pocket/rpg/editor/undo/commands/UIScaleCommand.java` - New file for scale undo

### Implementation Steps

1. Create `UIScaleCommand` class (similar to `UIRotationCommand`, but fixed)
2. Refactor `UITransformInspector`:
   - Extract `drawPropertyRow()` helper for consistent field rendering
   - Implement `[M]` match parent toggle button with tooltip
   - Ensure all match parent toggles behave identically
   - Add undo support for scale changes
3. Update tooltips for consistency

---

## Verification

### Test Cases
1. **Rotation Undo**: Change rotation, undo → should restore exact previous value
2. **Rotation Undo with Match Parent**: Enable match parent, change rotation, undo → should work correctly
3. **UIText Position**: Add UIText to a UI element, position should match parent bounds
4. **UIText Rotation**: Rotate parent, text should rotate with it
5. **Scale Undo**: Change scale, undo → should restore (currently broken, will be fixed)
6. **Inspector Consistency**: All "match parent" buttons should look and behave the same

### Manual Testing
1. Run the editor: `mvn exec:java -Dexec.mainClass="com.pocket.rpg.editor.EditorApplication"`
2. Open UI Designer panel
3. Create a UI Canvas with child elements
4. Test all undo/redo operations on UITransform fields
5. Add UIText and verify position/rotation
6. Verify inspector layout matches the mockup

---

## Implementation Order

1. **Phase 1**: Fix rotation undo (quick fix)
2. **Phase 2**: Fix UIText rendering (position + rotation)
3. **Phase 3**: Refactor UITransformInspector with harmonized layout

---

## Implementation Status: COMPLETE

### Changes Made

#### Issue 1: Rotation Undo/Redo
- Added `getLocalRotation2D()` method to `UITransform.java` (returns raw `localRotation.z`)
- Updated `UITransformInspector.java` to use `getLocalRotation2D()` when capturing undo values

#### Issue 2: UIText Rendering
- Added `setCalculatedPosition(float x, float y)` method to `UITransform.java`
- Updated `UIDesignerRenderer.renderTextElement()` to use new method instead of broken hack

#### Issue 3: UITransformInspector Refactor
- Added `getLocalScale2D()` method to `UITransform.java`
- Created `UIScaleCommand.java` for scale undo support
- Refactored inspector layout:
  - Side-by-side anchor/pivot grids with editable X/Y fields below
  - Master "MATCH PARENT" button between Offset and Size sections
  - Per-field [M] toggle buttons with accent color when ON
  - Scale undo support via `UIScaleCommand`
  - Consistent behavior across all match parent toggles

### Files Modified
- `src/main/java/com/pocket/rpg/components/ui/UITransform.java`
- `src/main/java/com/pocket/rpg/editor/ui/inspectors/UITransformInspector.java`
- `src/main/java/com/pocket/rpg/editor/panels/uidesigner/UIDesignerRenderer.java`

### Files Created
- `src/main/java/com/pocket/rpg/editor/undo/commands/UIScaleCommand.java`