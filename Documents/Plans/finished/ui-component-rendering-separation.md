# UI Component Rendering Separation

## Overview

### Problem
`UIComponent` forces an `abstract render(UIRendererBackend)` on all subclasses, conflating "being a UI element" with "having visual output." This causes:

- **4 out of 8** UIComponent subclasses have empty `render()` bodies (UICanvas, UIMask, UIScrollView, UIScrollbar)
- UIButton duplicates UIPanel/UIImage rendering logic
- UIText contains OpenGL calls (`glEnable`, `glBlendFunc`) that belong in the renderer
- `AlphaGroup` uses a hardcoded `instanceof` chain (UIImage → UIPanel → UIButton → UIText) that must be updated for every new visual component
- Color/alpha methods are copy-pasted across UIPanel, UIImage, UIButton, UIText
- `UIRendererBackend` is coupled to `UIImage` enums (`FillMethod`, `FillOrigin`)

### Approach
Align UI rendering with the established world-space pattern: **components hold data, renderers draw it.** `SpriteRenderer` already follows this — it implements `Renderable` (data interface), and `BatchRenderer.drawSpriteRenderer()` does the actual drawing. UI components should work the same way.

### Key Changes
1. Introduce `UIVisual` base class for components that have visual output (color, bounds)
2. Remove `abstract render()` from `UIComponent`
3. Move all rendering logic into `UIRenderer` (type-based dispatch)
4. Make `UIButton` interaction-only — it manages a sibling UIPanel or UIImage based on its `TransitionMode`
5. Fix `AlphaGroup` to use `UIVisual` instead of instanceof chain
6. Move `FillMethod`/`FillOrigin` out of UIImage

### Current UIComponent Hierarchy
```
Component
├── AlphaGroup
└── UIComponent (abstract)
    ├── UIButton      — renders itself (COLOR_TINT quad/sprite or SPRITE_SWAP)
    ├── UICanvas      — empty render()
    ├── UIImage       — renders sprite (simple/sliced/tiled/filled)
    ├── UIMask        — empty render()
    ├── UIPanel       — renders colored quad
    ├── UIScrollbar   — empty render()
    ├── UIScrollView  — empty render()
    └── UIText        — renders glyphs (layout, shadow, autofit)
```

### Architecture After
```
Component
├── AlphaGroup (uses UIVisual for alpha — no instanceof chain)
└── UIComponent (abstract, NO render() method, keeps RenderBounds for hit testing)
    ├── UIVisual (abstract — color, alpha)
    │   ├── UIPanel   — colored quad data
    │   ├── UIImage   — sprite data (simple/sliced/tiled/filled)
    │   └── UIText    — text/font/layout data
    ├── UIButton      — interaction-only, manages sibling UIVisual
    ├── UICanvas      — root container marker
    ├── UIMask        — scissor clipping logic
    ├── UIScrollbar   — scroll handle logic
    └── UIScrollView  — scroll container logic

UIRenderer
  — owns ALL rendering logic
  — dispatches by type: renderPanel(), renderImage(), renderText()
  — reads data from UIVisual subclasses
```

---

## Phase 1: Introduce UIVisual and extract shared state

Extract the visual concerns (color, alpha) into a `UIVisual` intermediate class. This is a pure refactor — no behavior changes.

**Important:** `RenderBounds` and `computeRenderBounds()` stay on `UIComponent` — they are used for hit testing by non-visual components (e.g. `UIButton.containsPoint()`), not just rendering.

### Tasks
- [ ] Create `UIVisual extends UIComponent` with:
  - `Vector4f color` field with getters/setters
  - `setAlpha(float)` method
- [ ] Make `UIPanel`, `UIImage`, `UIText` extend `UIVisual` instead of `UIComponent`
- [ ] Remove duplicate `color`, `setColor()`, `setAlpha()` from UIPanel, UIImage, UIText
  - UIPanel default color: `(0.2, 0.2, 0.2, 1)` — keep via constructor
  - UIImage default color: `(1, 1, 1, 1)` — keep via constructor
  - UIText default color: `(1, 1, 1, 1)` — keep via constructor
- [ ] `RenderBounds` and `computeRenderBounds()` remain on `UIComponent` (used by UIButton for hit testing)
- [ ] Update `AlphaGroup.applyAlphaToComponents()` to use `if (comp instanceof UIVisual visual)` instead of the instanceof chain
- [ ] Verify all existing tests pass

### Files

| File | Change |
|------|--------|
| `components/ui/UIVisual.java` | **NEW** — intermediate base class (color, alpha) |
| `components/ui/UIPanel.java` | Extend `UIVisual`, remove duplicate color/alpha |
| `components/ui/UIImage.java` | Extend `UIVisual`, remove duplicate color/alpha |
| `components/ui/UIText.java` | Extend `UIVisual`, remove duplicate color/alpha |
| `components/ui/AlphaGroup.java` | Replace instanceof chain with `UIVisual` check |

### Notes
- UIButton also has color/alpha but is handled in Phase 3 (it keeps its own color fields for button states)
- `UIVisual` does NOT have a `render()` method yet — that gets removed in Phase 2
- **Serialization is safe:** `ComponentRegistry.collectFields()` walks the class hierarchy, so moving `color` to UIVisual doesn't break existing scene files — the field name stays the same.
- AlphaGroup currently checks: `UIImage → UIPanel → UIButton → UIText`. After this phase, the visual chain becomes a single `UIVisual` check. UIButton keeps its own `setAlpha()` since it doesn't extend UIVisual — AlphaGroup still needs a separate `UIButton` check until Phase 3 removes UIButton's color ownership.

---

## Phase 2: Move rendering logic from components to UIRenderer

Remove `render()` from components entirely. UIRenderer dispatches by type and reads component data.

### Tasks
- [ ] Remove `abstract render(UIRendererBackend)` from `UIComponent`
- [ ] Remove empty `render()` overrides from UICanvas, UIMask, UIScrollView, UIScrollbar
- [ ] Move UIPanel render logic into `UIRenderer.renderPanel(UIPanel)`
- [ ] Move UIImage render logic into `UIRenderer.renderImage(UIImage)` (all 4 image types: simple, sliced, tiled, filled)
- [ ] Move UIText render logic into `UIRenderer.renderText(UIText)`
  - This is the biggest move — includes `renderInternal`, `renderTextPass` with glyph rendering, shadow passes
  - UIText keeps layout state (`lines`, `lineWidths`, `naturalWidth`, `naturalHeight`, `layoutDirty`) and public query methods (`getNaturalWidth`, `getLineCount`, etc.)
  - UIText keeps `getRenderFont()`, `calculateBestFitFontSize()`, `calculateLayout()` (font selection and layout are data, not rendering)
  - **Exposure needed:** Several private fields/methods in UIText must become package-private or gain getters for UIRenderer to read: `cachedFont`, `lines`, `lineWidths`, `naturalWidth`, `naturalHeight`, `layoutDirty`, `getRenderFont(float, float)`. Prefer adding getters over widening access.
  - **OpenGL calls** (`glEnable(GL_BLEND)`, `glBlendFunc`) currently in UIText.render() move to UIRenderer — components must not touch GL state
- [ ] Move UIButton render logic into `UIRenderer.renderButton(UIButton)` — temporary, removed in Phase 3
- [ ] Update `UIRenderer.renderGameObjectUI()` to dispatch by type:
  ```java
  for (var component : go.getAllComponents()) {
      if (component instanceof UIPanel panel && panel.isEnabled()) {
          renderPanel(panel);
      } else if (component instanceof UIImage image && image.isEnabled()) {
          renderImage(image);
      } else if (component instanceof UIText text && text.isEnabled()) {
          renderText(text);
      } else if (component instanceof UIButton button && button.isEnabled()) {
          renderButton(button);
      }
  }
  ```
- [ ] Remove `UIRendererBackend` dependency from all UI component imports
- [ ] Handle UIText's second `render()` overload (`render(backend, x, y, width, height, rotation, pivotX, pivotY)`) — this is used for editor explicit-position rendering. Replace with a `UIRenderer.renderText(UIText, float x, float y, ...)` method.
- [ ] Verify all existing tests pass

### Files

| File | Change |
|------|--------|
| `components/ui/UIComponent.java` | Remove `abstract render()` |
| `components/ui/UICanvas.java` | Remove empty `render()` |
| `components/ui/UIMask.java` | Remove empty `render()` |
| `components/ui/UIScrollView.java` | Remove empty `render()` |
| `components/ui/UIScrollbar.java` | Remove empty `render()` |
| `components/ui/UIPanel.java` | Remove `render()` |
| `components/ui/UIImage.java` | Remove `render()` and private render helpers (`renderSimple`, `renderSliced`, `renderTiled`, `renderFilled`) |
| `components/ui/UIText.java` | Remove `render()` methods and `renderInternal`/`renderTextPass`, keep layout/font logic |
| `components/ui/UIButton.java` | Remove `render()`, `renderColorTint()`, `renderSpriteSwap()` |
| `rendering/ui/UIRenderer.java` | Add `renderPanel()`, `renderImage()`, `renderText()`, `renderButton()`, update `renderGameObjectUI()` dispatch |

### Decision: What about UIRendererBackend?
Once components no longer call `backend.drawQuad()` etc., the interface exists only as self-documentation on UIRenderer. Options:
- **Keep it** — useful if we ever want a mock renderer for testing
- **Remove it** — less indirection, UIRenderer just has the methods directly

Recommend: keep for now, revisit when adding unit tests for UI rendering.

---

## Phase 3: UIButton interaction-only refactor with managed visual

UIButton becomes interaction-only. Instead of rendering itself, it **manages a sibling UIVisual component** (UIPanel or UIImage) based on its `TransitionMode`. When the transition mode changes, UIButton automatically swaps the managed component.

### Design

**TransitionMode → Managed Component mapping:**
| TransitionMode | Managed Component | Why |
|---------------|-------------------|-----|
| `COLOR_TINT` | `UIPanel` | Quad with color tinting for hover/press states |
| `SPRITE_SWAP` | `UIImage` | Sprite that swaps between normal/hover/pressed sprites |

**Lifecycle:**
1. On `onStart()`, UIButton searches the same GameObject for an existing UIPanel/UIImage
2. If one matching the current TransitionMode already exists, adopt it (set `managedVisual` reference, mark it as driven)
3. If the wrong type exists (e.g. UIPanel but mode is SPRITE_SWAP), remove it and create the correct one
4. If none exists, create the correct one and add it to the GameObject
5. UIButton pushes its state (normal/hover/pressed color or sprite) to the managed component on state changes

**When TransitionMode changes (editor):**
1. Remove the current managed component from the GameObject
2. Create the new correct component type
3. Add it to the GameObject
4. Transfer applicable values (e.g. color carries over between UIPanel and UIImage)
5. This is a `CompoundCommand` for undo: (change transitionMode + remove old component + add new component)

**Driven component flag:**
The managed UIPanel/UIImage is marked with a `drivenBy` field (or similar mechanism) so that:
- The inspector shows a warning banner: "Values driven by UIButton"
- Color/sprite fields are rendered as read-only (grayed out)
- The designer edits everything through UIButton's inspector section

**Serialization:**
- The managed UIPanel/UIImage IS serialized as a normal component on the GameObject
- On deserialization + `onStart()`, UIButton finds the existing sibling and adopts it (no recreation needed in the common case)
- On load of old scenes (no sibling exists), UIButton auto-creates one — this handles migration naturally

### UIButton field changes

**Kept fields:**
- `transitionMode` — drives which sibling component is managed
- `color` — normal state color (pushed to managed UIPanel/UIImage)
- `hoveredColor`, `pressedColor` — COLOR_TINT state overrides
- `hoverTint`, `pressedTint` — COLOR_TINT auto-darkening factors
- `sprite` — normal sprite (pushed to managed UIImage in SPRITE_SWAP mode)
- `hoveredSprite`, `pressedSprite` — SPRITE_SWAP state sprites
- `onClick`, `onHover`, `onExit` — callbacks (unchanged)
- `hovered`, `pressed` — runtime state (unchanged)
- `config` — GameConfig reference for default tint values

**Removed:**
- All rendering methods (`render()`, `renderColorTint()`, `renderSpriteSwap()`)
- No longer needs `computeRenderBounds()` (inherited from UIComponent, not UIVisual)

**New fields:**
- `transient UIVisual managedVisual` — reference to the managed sibling (not serialized)

### Inspector changes (UIButtonInspector)

The inspector already shows different fields per TransitionMode. Main changes:
- When the managed UIPanel/UIImage is selected directly, show a read-only view with a "Driven by UIButton" warning banner at the top
- The existing UIButtonInspector remains the primary editing surface — no UX change for the designer
- TransitionMode change triggers CompoundCommand (change mode + swap component)

### Migration (replaces old Phase 3)

Migration is simplified because UIButton self-heals on `onStart()`:
- Old scenes with standalone UIButton: UIButton creates the missing sibling automatically on start
- No loader-level migration needed — UIButton handles it
- First save after loading an old scene will persist the new sibling component

For the editor, `UIEntityFactory.createButton()` must be updated to include the correct sibling from the start.

### Tasks
- [ ] Add `transient UIVisual managedVisual` field to UIButton
- [ ] Implement `ensureManagedVisual()` — finds or creates the correct sibling based on TransitionMode
- [ ] Call `ensureManagedVisual()` from `onStart()`
- [ ] Implement `pushStateToVisual()` — applies current hover/press state to managed component
- [ ] Call `pushStateToVisual()` from `setHoveredInternal()` and `setPressedInternal()` instead of storing tint on self
- [ ] Remove `renderButton()` from UIRenderer (no longer needed — UIPanel/UIImage renders normally)
- [ ] Remove UIButton from UIRenderer dispatch (it's no longer a rendered component)
- [ ] Implement TransitionMode change handling:
  - Remove old managed component
  - Create new managed component of correct type
  - Transfer applicable values
  - Wire up as CompoundCommand in inspector
- [ ] Update UIButtonInspector for TransitionMode swap undo
- [ ] Add "driven by UIButton" warning in UIPanel/UIImage inspectors when the component is managed
- [ ] Update `UIEntityFactory.createButton()` to include sibling UIPanel (default TransitionMode is COLOR_TINT)
- [ ] Update programmatic button creation in game code (`DemoScene.java`, `ExampleScene.java`)
- [ ] Update `AlphaGroup` — remove the separate `UIButton` instanceof check (UIButton no longer has color/alpha; alpha goes through the managed UIVisual)
- [ ] Verify button hover/press visuals still work correctly
- [ ] Verify old scenes load correctly (auto-creation of missing sibling)

### Files

| File | Change |
|------|--------|
| `components/ui/UIButton.java` | Remove rendering, add managed visual lifecycle, add `pushStateToVisual()` |
| `rendering/ui/UIRenderer.java` | Remove `renderButton()` and UIButton from dispatch |
| `components/ui/AlphaGroup.java` | Remove UIButton instanceof check |
| `editor/ui/inspectors/UIButtonInspector.java` | CompoundCommand for TransitionMode swap |
| `editor/ui/inspectors/UIPanelInspector.java` | "Driven by UIButton" warning when managed |
| `editor/ui/inspectors/UIImageInspector.java` | "Driven by UIButton" warning when managed |
| `editor/scene/UIEntityFactory.java` | Update `createButton()` to include sibling UIPanel |
| `scenes/DemoScene.java` | Add UIPanel to programmatic button GOs |
| `scenes/ExampleScene.java` | Add UIPanel to programmatic button GOs |

### Risk
Medium. The main risk is edge cases during TransitionMode switching in the editor (undo/redo ordering, component removal timing). The runtime path is straightforward — UIButton finds its sibling on start and pushes state on hover/press changes.

---

## Phase 4: Editor UI quality-of-life fixes

Small targeted fixes to editor UI behavior that naturally belong in this plan.

### 4a: Auto-UITransform when creating entities under a Canvas

**Problem:** `EntityCreationService.createEmptyEntity()` always creates entities with a `Transform`. When the selected parent is under a UICanvas, the new child should get a `UITransform` instead. The `ReparentEntityCommand` already handles this for drag-drop reparenting (auto-swaps Transform ↔ UITransform), but the creation path has no such logic.

**Fix:** In `createEmptyEntity()`, after determining the parent, check if the parent is a UI element (has UICanvas or UITransform). If so, replace the default Transform with a UITransform on the new entity.

#### Tasks
- [ ] In `EntityCreationService.createEmptyEntity()`, after resolving the parent, check `isUIElement(parent)`
- [ ] If parent is a UI element, remove the default Transform and add a UITransform instead
- [ ] Verify: create empty entity under a Canvas → gets UITransform
- [ ] Verify: create empty entity at root level → gets Transform (unchanged)

#### Files

| File | Change |
|------|--------|
| `editor/panels/hierarchy/EntityCreationService.java` | Add UITransform auto-swap in `createEmptyEntity()` |

### 4b: UIDesigner bounds toggle should always show selected item bounds

**Problem:** The "Bounds" toggle button in UIDesignerPanel hides ALL element bounds, including the currently selected item. The selected item's bounds should always be visible regardless of the toggle — the toggle should only affect unselected items.

**Current code** (UIDesignerPanel, ~line 335):
```java
if (state.isShowElementBounds()) {
    gizmoDrawer.drawSelectionBorders(drawList, scene);
    gizmoDrawer.drawLayoutPadding(drawList, scene);
    gizmoDrawer.drawTrackPadding(drawList, scene);
}
```

**Fix:** Split the gizmo drawing so that:
- Unselected items' borders/padding are conditional on the toggle
- Selected item's borders/padding are always drawn

This likely requires `drawSelectionBorders()` (and possibly `drawLayoutPadding`/`drawTrackPadding`) to accept a filter or to be split into two calls: one for the selected entity (always drawn) and one for everything else (conditional).

#### Tasks
- [ ] Modify `UIDesignerGizmoDrawer.drawSelectionBorders()` to accept a flag or split into selected/unselected
- [ ] Always draw selected item bounds, conditionally draw unselected based on `showElementBounds`
- [ ] Same treatment for `drawLayoutPadding()` and `drawTrackPadding()` if they affect selected items
- [ ] Verify: toggle bounds off → selected item still shows bounds, unselected items hidden
- [ ] Verify: toggle bounds on → all items show bounds (unchanged behavior)

#### Files

| File | Change |
|------|--------|
| `editor/panels/UIDesignerPanel.java` | Split gizmo draw calls for selected vs unselected |
| `editor/panels/uidesigner/UIDesignerGizmoDrawer.java` | Support selected-only vs all-elements rendering |

### 4c: Scrollbar left/right positioning on UIScrollView

**Problem:** The scrollbar is always positioned on the right side of the scroll view. There's no way to place it on the left. The scrollbar's UITransform is hardcoded to anchor `(1, 0)` / pivot `(1, 0)` (right-aligned), and the viewport always occupies the remaining left-side space.

**Design:** Add a `ScrollbarPosition` enum (LEFT, RIGHT) to UIScrollView. When the position changes, UIScrollView adjusts the scrollbar's anchor/pivot and the viewport's offset/anchor in `updateMetrics()`.

```java
public enum ScrollbarPosition { LEFT, RIGHT }
```

**How it works:**
- **RIGHT (default, current behavior):** Scrollbar anchor/pivot = (1, 0). Viewport anchor/pivot = (0, 0), offset = (0, 0).
- **LEFT:** Scrollbar anchor/pivot = (0, 0). Viewport anchor = (0, 0), offset = (scrollbarWidth, 0) — viewport shifts right to make room.

UIScrollView already dynamically computes viewport width in `updateMetrics()` (subtracts scrollbar width). The only change is *where* the remaining space goes — left-offset vs right-aligned.

#### Tasks
- [ ] Add `ScrollbarPosition` enum to `UIScrollView` (LEFT, RIGHT), default RIGHT
- [ ] Add `scrollbarPosition` field with getter/setter
- [ ] In `updateMetrics()`, after computing viewport width, set viewport offset based on position:
  - RIGHT: viewport offset.x = 0, scrollbar anchor.x = 1, scrollbar pivot.x = 1
  - LEFT: viewport offset.x = scrollbarWidth, scrollbar anchor.x = 0, scrollbar pivot.x = 0
- [ ] Update `UIScrollViewInspector` to expose the `scrollbarPosition` enum
- [ ] Update `UIEntityFactory.createScrollView()` — no change needed (default is RIGHT)
- [ ] Test: create scroll view, switch to LEFT, verify scrollbar appears on left and viewport shifts right
- [ ] Test: undo/redo scrollbar position change

#### Files

| File | Change |
|------|--------|
| `components/ui/UIScrollView.java` | Add `ScrollbarPosition` enum and field, update `updateMetrics()` |
| `editor/ui/inspectors/UIScrollViewInspector.java` | Expose scrollbar position enum selector |

---

## Phase 5: Move FillMethod/FillOrigin out of UIImage

`UIRendererBackend.drawFilled()` currently takes `UIImage.FillMethod` and `UIImage.FillOrigin` — the renderer interface shouldn't depend on a component class.

### Tasks
- [ ] Move `FillMethod` and `FillOrigin` enums to `rendering/ui/` package (or a shared `components/ui/` enums file)
- [ ] Update UIImage to use the relocated enums
- [ ] Update UIRendererBackend (or UIRenderer) to use relocated enums
- [ ] Update any editor/inspector code that references UIImage.FillMethod

### Files

| File | Change |
|------|--------|
| `rendering/ui/FillMethod.java` | **NEW** — extracted enum |
| `rendering/ui/FillOrigin.java` | **NEW** — extracted enum |
| `components/ui/UIImage.java` | Remove inner enums, import from new location |
| `rendering/ui/UIRendererBackend.java` | Update import |
| `rendering/ui/UIRenderer.java` | Update import |

### Notes
- Low risk, pure mechanical refactor
- Could also be done in Phase 2 alongside the render move

---

## Phase 6: Code review

- [ ] Verify no UIComponent subclass has a `render()` method
- [ ] Verify UIRenderer handles all visual component types (UIPanel, UIImage, UIText)
- [ ] Verify AlphaGroup works with UIVisual interface (no instanceof chain)
- [ ] Verify UIButton interaction works via managed sibling visual
- [ ] Verify scene file backwards compatibility (UIButton auto-creates missing sibling)
- [ ] Verify prefab file compatibility
- [ ] Verify editor inspector still works for all UI components
- [ ] Verify "driven by UIButton" warning shows on managed UIPanel/UIImage
- [ ] Verify TransitionMode switching creates correct sibling with undo support
- [ ] Verify new entities under canvas get UITransform automatically
- [ ] Verify UIDesigner bounds toggle always shows selected item bounds
- [ ] Run full test suite
- [ ] Check for any remaining `UIRendererBackend` imports in component classes
- [ ] Update architecture docs if needed

---

## Testing Strategy

| Test | Validates |
|------|-----------|
| Existing UI component unit tests | No regression in data behavior |
| `UIMaskTest` | Mask still works after render() removal |
| Unit test: UIButton.ensureManagedVisual() creates UIPanel for COLOR_TINT | Managed visual lifecycle |
| Unit test: UIButton.ensureManagedVisual() creates UIImage for SPRITE_SWAP | Managed visual lifecycle |
| Unit test: UIButton adopts existing UIPanel on start | Deserialization path |
| Unit test: UIButton pushes hover color to managed UIPanel | State propagation |
| Unit test: UIButton pushes pressed sprite to managed UIImage | State propagation |
| Unit test: TransitionMode change swaps managed component | Editor switching |
| Unit test: old scene without sibling — UIButton auto-creates on start | Migration |
| Manual: load DemoScene/Battle.scene, verify buttons visible | Migration works end-to-end |
| Manual: button hover/press in game | Interaction refactor works |
| Manual: change TransitionMode in inspector, undo/redo | Editor undo |
| Manual: select managed UIPanel, verify "driven" warning shows | Inspector UX |
| Manual: AlphaGroup on a button subtree | Alpha propagates through managed visual |
| Manual: UIScrollView scrolling | Scroll + mask clipping still works |
| Manual: UIText with autoFit, wordWrap, shadow | Text rendering moved correctly |
| Manual: create new button in editor, verify UIPanel is included | Factory update |
| Manual: create empty entity under canvas → gets UITransform | Phase 4a auto-UITransform |
| Manual: create empty entity at root → gets Transform | Phase 4a no regression |
| Manual: bounds toggle off, select item → selected bounds visible | Phase 4b bounds fix |
| Manual: bounds toggle on → all bounds visible | Phase 4b no regression |
| Manual: scroll view with scrollbar on LEFT → scrollbar left, viewport shifted right | Phase 4c scrollbar position |
| Manual: scroll view with scrollbar on RIGHT → default behavior unchanged | Phase 4c no regression |

---

## Phase Order Summary

| Phase | Risk | Description |
|-------|------|-------------|
| 1 | Low | Introduce UIVisual, extract shared color/alpha. Pure refactor. |
| 2 | Medium | Move render() logic to UIRenderer, remove abstract render(). |
| 3 | Medium | UIButton interaction-only refactor with managed sibling visual. |
| 4 | Low | Editor UI QoL: auto-UITransform under canvas, bounds toggle fix, scrollbar left/right. |
| 5 | Low | Move FillMethod/FillOrigin enums. Mechanical refactor. |
| 6 | — | Code review and validation. |

Phases 1-2 are independent of Phase 3. Phase 3 can be done after or in parallel with Phase 2 (UIButton rendering moves to UIRenderer in Phase 2, then gets replaced by managed visual in Phase 3). Phase 4 is independent of all other phases and can be done at any time.

---

## Engineering Review Notes

Review performed against the codebase. Key findings that shaped the plan:

| Area | Finding | Resolution |
|------|---------|------------|
| **Serialization** | `ComponentRegistry.collectFields()` walks the full class hierarchy — moving `color` to UIVisual superclass is safe | No migration needed |
| **RenderBounds** | `UIButton.containsPoint()` uses `computeRenderBounds()` for hit testing — can't move to UIVisual | RenderBounds stays on UIComponent |
| **addComponent during onStart** | `GameObject.addComponent()` auto-starts the new component if scene is active — safe for managed visual creation | UIButton can create sibling in onStart() |
| **AlphaGroup + managed visual** | AlphaGroup calls `applyAlphaToComponents(childGO)` which iterates ALL components on a GO. Managed UIPanel on same GO as UIButton will be reached via `UIVisual` check | No issue — sibling components on same GO are iterated together |
| **UIText complexity** | ~5 private fields and methods need getters for UIRenderer access. OpenGL blend calls must move out. | Add getters, move GL calls to UIRenderer |
| **UIDesigner bounds** | `drawSelectionBorders()` already distinguishes selected vs unselected entities (different color/thickness) — easy to add filter parameter | Low complexity split |
| **"Driven by" mechanism** | No existing pattern in codebase for marking components as managed | New transient field + inspector check needed |
| **UIRendererBackend imports** | 9 component files import it; only 4 (UIPanel, UIImage, UIText, UIButton) actually use it in render() | All imports removed in Phase 2 |
