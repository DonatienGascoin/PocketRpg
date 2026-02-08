# UICanvas UITransform - Implementation Plan

## Overview

Add a UITransform to UICanvas, managed by the canvas to always reflect full-screen dimensions. This gives children a real parent UITransform to resolve PERCENT sizing against, instead of relying on fragile fallback logic.

### Current State

- UICanvas is a pure marker component with no UITransform
- UICanvas overrides `getWidth()`/`getHeight()` to return 0
- `UIComponent.onStart()` exempts UICanvas from the UITransform requirement via `instanceof` check
- Direct children of Canvas can't enable PERCENT mode in the inspector — `UITransformInspector` disables the `%` toggle and "MATCH PARENT" buttons when `hasParentUITransform` is false (lines 394, 245)
- `UITransform.getParentWidth()` uses `parentTransform.getWidth()` (raw Lombok getter) instead of `getEffectiveWidth()`, breaking nested PERCENT sizing at any depth
- `UITransform` has three dead public methods (`getBounds()`, `getScaledBounds()`, `containsPoint()`) that use raw `width`/`height` instead of effective sizes — zero callers in the codebase

### Design Decisions

1. **Canvas manages its UITransform**: The canvas enforces anchor=(0,0), pivot=(0,0), offset=(0,0), FIXED mode, width/height=screen dimensions. Values are synced by the renderer each frame via `updateScreenSize()`.
2. **`@RequiredComponent` annotation**: UICanvas declares `@RequiredComponent(UITransform.class)` — auto-adds UITransform when UICanvas is added, integrates with editor undo system, prevents removal in inspector.
3. **Inspector read-only**: `UITransformInspector` detects when the UITransform is on a Canvas and shows a disabled info line instead of editable controls.
4. **Scene migration**: Handled at both runtime and editor load paths (see below).
5. **Companion fix**: `getParentWidth()`/`getParentHeight()`/`getParentBounds()` changed to use `getEffectiveWidth()`/`getEffectiveHeight()` so nested PERCENT sizing works at any depth.
6. **Dead method cleanup**: Remove `getBounds()`, `getScaledBounds()`, `containsPoint()` from UITransform — all three use raw `width`/`height` instead of effective sizes and have zero callers. `UIButton` has its own rotation-aware `containsPoint()` that correctly uses `getEffectiveWidth()`.

---

## Phase 0 — Verification & Prerequisite Fix

### Problem: Editor deserialization does not honor `@RequiredComponent`

**Verified:** `EditorGameObject.fromData()` takes the component list as-is from `GameObjectData` via a private constructor. It does NOT call `addComponent()`, and `EditorGameObject.addComponent()` does NOT process `@RequiredComponent` annotations. Only two paths honor the annotation:
- `GameObject.addComponent()` (runtime)
- `AddComponentCommand.execute()` (editor undo system — user-initiated "Add Component")

This means existing scenes opened **in the editor** would have UICanvas without UITransform — breaking the assumption.

### Solution: Add `@RequiredComponent` processing to `EditorGameObject`

**`EditorGameObject.addComponent()`** — add `addRequiredComponents()` call. Must be placed **after** the UITransform special-case block (which does `removeIf(Transform.class)` + early return) and **before** `getComponents().add(component)` on the normal path. The UITransform branch already handles its own addition — required-component processing only applies to non-UITransform components (like UICanvas triggering UITransform auto-add).

**`EditorGameObject.fromData()`** — add a post-construction pass: iterate all loaded components, check for `@RequiredComponent` annotations, and add any missing dependencies. This fixes the editor scene loading path.

This is the correct long-term fix — any future `@RequiredComponent` annotations on other components will also be honored.

### Files
| File | Change |
|------|--------|
| `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java` | Add `addRequiredComponents()` to `addComponent()` and post-construction pass in `fromData()` |

---

## Challenges & Mitigations

### 1. `updateScreenSize()` unnecessary work when screen size unchanged

**Risk:** Calling `updateScreenSize()` every frame sets width/height and marks dirty — wasteful when screen size hasn't changed (most frames).

**Mitigation:** Guard with early return: `if (width == currentWidth && height == currentHeight) return;`. When size **does** change, call `markDirtyRecursive()` (not `markDirty()`) so children in PERCENT mode recalculate.

**Note:** `markDirty()` only sets flags on the current transform — it does NOT cascade to children. Only `markDirtyRecursive()` propagates. In practice the renderer currently calls `setScreenBounds()` + `markDirty()` on every UITransform every frame (`UIRenderer.java:249-251`), so children are marked dirty regardless. But `updateScreenSize()` should be correct in isolation — use `markDirtyRecursive()` when values change.

### 2. Legacy position system (`getParentBounds`) uses same raw `getWidth()`

**Risk:** `getParentBounds()` feeds `calculatePosition()` (legacy `getScreenPosition()`). Changing it to effective sizes could shift behavior for callers.

**Verified safe:** `getParentBounds()` is private, only called by `calculatePosition()`. `getScreenPosition()` has 5 callers — 4 internal to UITransform (bounds/hit-testing) and 1 in `UIRenderer.renderCanvasSubtree()`. No editor code calls it directly. The change is a bug fix — the legacy system was using wrong sizes for PERCENT parents.

### 3. Pre-render `getWidth()` returns default 100 instead of 0

**Risk:** Removing UICanvas's `getWidth()`/`getHeight()` overrides means before the first render frame (before `updateScreenSize()` runs), `canvas.getWidth()` returns UITransform's default of 100 instead of 0.

**Verified safe:** No code in the codebase calls `canvas.getWidth()` or `canvas.getHeight()` explicitly. The overrides were dead code.

### 4. Transform position lost on runtime scene load

**Risk:** When `@RequiredComponent` auto-adds UITransform in `RuntimeSceneLoader`, it replaces the base Transform. Position/rotation/scale values copied from the scene file onto the base Transform are discarded.

**Mitigated:** For `SCREEN_SPACE_OVERLAY` Canvas, position is always (0,0,0) and irrelevant. For future `WORLD_SPACE` Canvas, this needs revisiting — but `WORLD_SPACE` is not yet implemented.

### 5. Existing scene files remain stale on disk

**Risk:** After this change, existing `.scene` files still list `Transform` + `UICanvas`. Auto-migration happens at load time. When the user opens and saves a scene in the editor, `EditorGameObject.toData()` serializes whatever is in the components list — the UITransform (auto-added at load) replaces Transform in the output, and plain Transform is skipped. On next load, the scene has UITransform natively.

**Result:** Self-healing on first save. No manual migration step needed.

### 6. Renderer marks every UITransform dirty every frame

**Pre-existing issue:** `UIRenderer.renderCanvasSubtree()` calls `setScreenBounds()` + `markDirty()` on every UITransform in the tree every frame (lines 249-251). After this plan, the canvas root's UITransform gets dirtied twice per frame: once by `updateScreenSize()`, once by the renderer's own loop.

**Acceptable:** The redundancy is harmless. Optimizing the renderer's per-node dirty logic is out of scope for this plan but noted for future cleanup.

---

## Changes

### 1. `EditorGameObject.java` (Phase 0 prerequisite)
**Path:** `src/main/java/com/pocket/rpg/editor/scene/EditorGameObject.java`

- In `addComponent()`: add `addRequiredComponents()` call on the normal (non-UITransform) code path, before `getComponents().add(component)`. The UITransform early-return branch at lines 527-531 is left unchanged — it handles its own addition.
- Add post-construction pass in `fromData()` to process `@RequiredComponent` annotations on all loaded components

### 2. `UICanvas.java`
**Path:** `src/main/java/com/pocket/rpg/components/ui/UICanvas.java`

- Add `@RequiredComponent(UITransform.class)` annotation
- Add `updateScreenSize(float width, float height)` — guards with early return if unchanged, then sets UITransform to FIXED mode, width/height to screen dimensions, anchor/pivot/offset to (0,0), calls `markDirtyRecursive()` on the UITransform
- Remove `getWidth()`/`getHeight()` overrides that return 0

### 3. `UITransform.java`
**Path:** `src/main/java/com/pocket/rpg/components/ui/UITransform.java`

- `getParentWidth()` (line 612): `parentTransform.getWidth()` → `parentTransform.getEffectiveWidth()`
- `getParentHeight()` (line 622): `parentTransform.getHeight()` → `parentTransform.getEffectiveHeight()`
- `getParentBounds()` (lines 596-597): `parentTransform.getWidth()`/`getHeight()` → `getEffectiveWidth()`/`getEffectiveHeight()`
- **Delete** `getBounds()` (lines 782-785) — zero callers, uses raw `width`/`height`
- **Delete** `getScaledBounds()` (lines 791-795) — zero callers, uses raw `width`/`height`
- **Delete** `containsPoint()` (lines 804-808) — zero callers, uses raw `width`/`height`. `UIButton` has its own independent rotation-aware implementation that correctly uses `getEffectiveWidth()`

### 4. `UIRenderer.java`
**Path:** `src/main/java/com/pocket/rpg/rendering/ui/UIRenderer.java`

- In `render()`, before `renderCanvasSubtree()`, call `canvas.updateScreenSize(gameWidth, gameHeight)`

### 5. `UIComponent.java`
**Path:** `src/main/java/com/pocket/rpg/components/ui/UIComponent.java`

- Remove the `instanceof UICanvas` exemption in `onStart()` — Canvas now has UITransform

### 6. `UITransformInspector.java`
**Path:** `src/main/java/com/pocket/rpg/editor/ui/inspectors/UITransformInspector.java`

- At top of `draw()`, check if `entity` has a `UICanvas` component
- If yes: show disabled info line ("Managed by UICanvas — Screen: WxH"), return early

### 7. `UIEntityFactory.java`
**Path:** `src/main/java/com/pocket/rpg/editor/scene/UIEntityFactory.java`

- No change needed — `@RequiredComponent` handles auto-adding UITransform

---

## Verification

1. `mvn compile` — builds without errors
2. `mvn test` — existing tests pass
3. Run editor:
   - Open DemoScene — Canvas still renders correctly, existing UI elements unaffected
   - Select Canvas — inspector shows UITransform as "Managed by UICanvas" (read-only)
   - Add child Panel under Canvas — `%` toggle and "MATCH PARENT" buttons are now enabled
   - Set child Panel to PERCENT 100% — fills the screen
   - Add nested child Panel, also PERCENT 100% — also fills the screen (validates `getParentWidth` fix)
   - Save and reopen scene — Canvas now has UITransform in the scene file (self-healing migration)
   - Create a new Canvas via entity factory — UITransform auto-added via `@RequiredComponent`
