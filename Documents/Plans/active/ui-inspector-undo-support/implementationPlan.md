# Add Missing Undo Support to UI Component Inspectors

## Overview

21 field-change paths across 6 UI inspectors bypass the undo system. Users can modify properties (alpha sliders, alignment combos, preset buttons, checkboxes, match-parent toggles, etc.) with no way to Ctrl+Z. This plan adds undo/redo to every identified gap, plus fixes a double-apply bug in UITextInspector's fontPath.

### Approach

- **Replace with built-in editors** where possible (`EnumEditor.drawEnum()`, `PrimitiveEditors.drawBoolean()`) — they already handle undo + prefab overrides.
- **Manual undo tracking** where custom layout prevents using standard editors:
  - *Continuous* (slider/drag/text): capture on `isItemActivated()`, push `SetComponentFieldCommand` on `isItemDeactivatedAfterEdit()`.
  - *Discrete* (button/checkbox/combo): capture old value, apply, immediately push command.
- Always use `push()` (not `execute()`) since ImGui already applied the value.
- Create a new `CompoundCommand` to group multi-field changes (shadow presets, custom tints, match parent master) into a single undo entry.

---

## Phase 1: CompoundCommand + UIPanelInspector

### 1.1 Create `CompoundCommand`
- New class `editor/undo/commands/CompoundCommand.java`
- Implements `EditorCommand`, wraps a `List<EditorCommand>`
- `execute()` runs all forward; `undo()` runs all in reverse
- `canMergeWith()` returns `false`

### 1.2 UIPanelInspector — Alpha slider undo (line 48-56)
- Add instance field `Vector4f alphaEditStartColor`
- On `isItemActivated()`: capture `new Vector4f(color)`
- On `isItemDeactivatedAfterEdit()`: push `SetComponentFieldCommand(component, "color", start, current, entity)`

### 1.3 UIPanelInspector — Color preset buttons undo (line 70-73)
- Capture `oldColor` before the preset loop
- After each `colorButton` click + `setFieldValue`, push `SetComponentFieldCommand(component, "color", oldColor, newColor, entity)`

**Files:**
| File | Change |
|------|--------|
| `editor/undo/commands/CompoundCommand.java` | **NEW** |
| `editor/ui/inspectors/UIPanelInspector.java` | Add undo to alpha slider + color presets |

---

## Phase 2: UITextInspector

### 2.1 Text multiline undo (line 66-70)
- Add `String textEditStartValue` field
- Capture on `isItemActivated()`, push `SetComponentFieldCommand` on `isItemDeactivatedAfterEdit()`

### 2.2 horizontalAlignment combo undo (line 98-101)
- Capture `oldValue` before combo, push `SetComponentFieldCommand` after `setEnumValue()`

### 2.3 horizontalAlignment quick buttons undo (line 105-118)
- Capture `oldValue` before button block, push `SetComponentFieldCommand` after each `setEnumValue()`

### 2.4 verticalAlignment combo undo (line 129-132)
- Same pattern as 2.2

### 2.5 verticalAlignment quick buttons undo (line 136-149)
- Same pattern as 2.3

### 2.6 wordWrap checkbox undo (line 154-157)
- Capture old value, after `setFieldValue`, push `SetComponentFieldCommand`

### 2.7 autoFit checkbox undo (line 167-172)
- Same discrete pattern; keep the `markLayoutDirty()` side-effect

### 2.8 minFontSize slider undo (line 186-189)
- Add `int minFontSizeEditStart` field
- Continuous tracking via activation/deactivation, push `SetComponentFieldCommand(component, "minFontSize", ...)` on deactivation

### 2.9 maxFontSize slider undo (line 196-200)
- Same as 2.8 for `maxFontSize`

### 2.10 fontSize slider undo (line 407-413)
- Same as 2.8 for `fontSize`

### 2.11 shadow checkbox undo (line 214-217)
- Discrete: capture old, push after toggle

### 2.12 Shadow preset buttons undo (line 238-254)
- Each button sets `shadowOffset` + `shadowColor`. Capture old values of both before click.
- Use `CompoundCommand` grouping two `SetComponentFieldCommand`s, push after `setFieldValue` calls.

### 2.13 fontPath double-apply bug fix (line 358-375)
- Currently: `component.setFontPath(newPath)` then `UndoManager.execute(new SetComponentFieldCommand(...))` — applies twice.
- Fix: keep manual `setFontPath()` + `markLayoutDirty()`, change `execute()` to `push()`

**Files:**
| File | Change |
|------|--------|
| `editor/ui/inspectors/UITextInspector.java` | Undo for 11 widgets + fontPath bug fix |

---

## Phase 3: UIImageInspector

### 3.1 Alpha slider undo (line 58-63)
- Same pattern as Phase 1.2 — add `Vector4f alphaEditStartColor` field, activation/deactivation tracking

### 3.2 fillOrigin combo undo (line 178-182)
- Capture `oldOrigin` before combo, after `component.setFillOrigin(newOrigin)`, push `SetComponentFieldCommand(component, "fillOrigin", oldOrigin, newOrigin, entity)`

### 3.3 resetSizeToSprite button undo (line 226-235)
- Capture old width/height/offset/anchor/pivot from UITransform
- After `setFieldValue` calls, push `UITransformDragCommand.resize(entity, transform, oldOffset, oldW, oldH, oldOffset, newW, newH, anchor, pivot)`

**Files:**
| File | Change |
|------|--------|
| `editor/ui/inspectors/UIImageInspector.java` | Undo for alpha, fillOrigin, resetSize |

---

## Phase 4: UIButtonInspector

### 4.1 Alpha slider undo (line 128-134)
- Same pattern as Phase 1.2

### 4.2 hoverTint/pressedTint slider undo (drawTintSlider, line 137-154)
- Add `Map<String, Float>` or two explicit fields for undo start values
- Continuous: capture on `isItemActivated()`, push `SetComponentFieldCommand` on `isItemDeactivatedAfterEdit()`

### 4.3 Custom Tints checkbox undo (line 188-201)
- Capture old values of both `hoverTint` and `pressedTint` before toggle
- After toggle, push `CompoundCommand` with two `SetComponentFieldCommand`s

### 4.4 resetSizeToSprite button undo (line 226-234)
- Same pattern as Phase 3.3

**Files:**
| File | Change |
|------|--------|
| `editor/ui/inspectors/UIButtonInspector.java` | Undo for alpha, tint sliders, custom tints, resetSize |

---

## Phase 5: UITransformInspector

### 5.1 MATCH PARENT master button undo (line 261-267)
- Capture old `stretchMode`, `matchParentRotation`, `matchParentScale` before click
- After setting all three, push `CompoundCommand` with three `SetterUndoCommand`s

### 5.2 Individual M toggles undo (lines 322-325, 518-519, 565-566)
- Size M toggle: capture old `StretchMode`, after toggle push `SetterUndoCommand<>(component::setStretchMode, old, new, "...")`
- Rotation M toggle: capture old `boolean`, push `SetterUndoCommand<>(t::setMatchParentRotation, old, !old, "...")`
- Scale M toggle: same pattern for `matchParentScale`

### 5.3 Match parent size button — anchor/pivot undo (line 391-406)
- Size/offset already have undo via `startSizeEdit()`/`commitSizeEdit()`
- Before the existing logic, capture `oldAnchor` and `oldPivot`
- After `commitSizeEdit()` + the direct `setAnchor`/`setPivot` calls, push `SetterUndoCommand` for each if changed

**Files:**
| File | Change |
|------|--------|
| `editor/ui/inspectors/UITransformInspector.java` | Undo for match parent toggles + anchor/pivot |

---

## Phase 6: UICanvasInspector

### 6.1 renderMode combo undo (line 44-48)
- Replace raw `ImGui.combo()` + `setFieldValue()` with `EnumEditor.drawEnum("Render Mode", component, "renderMode", UICanvas.RenderMode.class)`
- Keep tooltip after the call
- Remove now-unused `RENDER_MODES` array, `getEnumValue()`, and `indexOf()` helper methods

**Files:**
| File | Change |
|------|--------|
| `editor/ui/inspectors/UICanvasInspector.java` | Replace raw combo with `EnumEditor.drawEnum()` |

---

## All Files Summary

| File | Phase | Change |
|------|-------|--------|
| `src/.../editor/undo/commands/CompoundCommand.java` | 1 | **NEW** — compound undo command |
| `src/.../editor/ui/inspectors/UIPanelInspector.java` | 1 | Alpha slider + color presets undo |
| `src/.../editor/ui/inspectors/UITextInspector.java` | 2 | 11 widgets undo + fontPath bug fix |
| `src/.../editor/ui/inspectors/UIImageInspector.java` | 3 | Alpha + fillOrigin + resetSize undo |
| `src/.../editor/ui/inspectors/UIButtonInspector.java` | 4 | Alpha + tints + custom tints + resetSize undo |
| `src/.../editor/ui/inspectors/UITransformInspector.java` | 5 | Match parent toggles + anchor/pivot undo |
| `src/.../editor/ui/inspectors/UICanvasInspector.java` | 6 | Replace raw combo with `EnumEditor` |

---

## Testing Strategy

1. **`mvn compile`** after each phase to verify no compile errors
2. **`mvn test`** after each phase to catch regressions
3. **Manual testing per phase** — for each fixed widget:
   - Modify the field
   - Ctrl+Z — verify value reverts
   - Ctrl+Y — verify value re-applies
   - For sliders: rapid drag should produce a single undo entry (not per-frame)
   - For shadow/tint presets: undo should restore all fields atomically
4. **Edge cases**:
   - fontPath: select font, undo, verify no double-apply
   - Match parent master: undo should restore all 3 fields at once
   - resetSizeToSprite: undo should restore both width and height

## Code Review

- [ ] Verify every path uses `push()` (not `execute()`) where ImGui already applied the value
- [ ] Verify no double-apply bugs introduced
- [ ] Verify `CompoundCommand.undo()` reverses in correct order
- [ ] Verify continuous edit tracking fields are instance fields (not static)
- [ ] Verify `entity` null-guards before pushing commands
