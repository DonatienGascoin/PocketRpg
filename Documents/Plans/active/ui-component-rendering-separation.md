# UI Component Rendering Separation

## Overview

### Problem
`UIComponent` forces an `abstract render(UIRendererBackend)` on all subclasses, conflating "being a UI element" with "having visual output." This causes:

- **4 out of 7** UIComponent subclasses have empty `render()` bodies (UICanvas, UIMask, UIScrollView, UIScrollbar)
- UIButton duplicates UIPanel/UIImage rendering logic
- UIText contains OpenGL calls (`glEnable`, `glBlendFunc`) that belong in the renderer
- `AlphaGroup` uses a hardcoded `instanceof` chain that must be updated for every new visual component
- Color/alpha methods are copy-pasted across UIPanel, UIImage, UIButton, UIText
- `UIRendererBackend` is coupled to `UIImage` enums (`FillMethod`, `FillOrigin`)

### Approach
Align UI rendering with the established world-space pattern: **components hold data, renderers draw it.** `SpriteRenderer` already follows this — it implements `Renderable` (data interface), and `BatchRenderer.drawSpriteRenderer()` does the actual drawing. UI components should work the same way.

### Key Changes
1. Remove `abstract render()` from `UIComponent`
2. Introduce `UIVisual` base class for components that have visual output (color, bounds)
3. Move all rendering logic into `UIRenderer` (component dispatch)
4. Auto-migrate existing scene/prefab files so UIButton GOs get a sibling UIPanel
5. Make `UIButton` interaction-only (modifies sibling UIPanel/UIImage)
6. Fix `AlphaGroup` to use `UIVisual` interface instead of instanceof chain

---

## Phase 1: Introduce UIVisual and extract shared state

Extract the visual concerns (color, alpha, render bounds) into a `UIVisual` intermediate class. This is a pure refactor — no behavior changes.

### Tasks
- [ ] Create `UIVisual extends UIComponent` with:
  - `Vector4f color` field with getters/setters
  - `setAlpha(float)` method
  - `RenderBounds` record (moved from UIComponent)
  - `computeRenderBounds()` method (moved from UIComponent)
- [ ] Make `UIPanel`, `UIImage`, `UIText` extend `UIVisual` instead of `UIComponent`
- [ ] Remove duplicate `color`, `setColor()`, `setAlpha()` from UIPanel, UIImage, UIText
  - UIPanel default color: `(0.2, 0.2, 0.2, 1)` — keep via constructor
  - UIImage default color: `(1, 1, 1, 1)` — keep via constructor
  - UIText default color: `(1, 1, 1, 1)` — keep via constructor
- [ ] Remove `RenderBounds` and `computeRenderBounds()` from `UIComponent` base class
- [ ] Update `AlphaGroup.applyAlphaToComponents()` to use `if (comp instanceof UIVisual visual)` instead of the instanceof chain
- [ ] Verify all existing tests pass

### Files

| File | Change |
|------|--------|
| `components/ui/UIVisual.java` | **NEW** — intermediate base class |
| `components/ui/UIComponent.java` | Remove `RenderBounds`, `computeRenderBounds()` |
| `components/ui/UIPanel.java` | Extend `UIVisual`, remove duplicate color/alpha |
| `components/ui/UIImage.java` | Extend `UIVisual`, remove duplicate color/alpha |
| `components/ui/UIText.java` | Extend `UIVisual`, remove duplicate color/alpha |
| `components/ui/AlphaGroup.java` | Replace instanceof chain with `UIVisual` check |

### Notes
- UIButton also has color/alpha but is handled in Phase 3 (interaction refactor)
- `UIVisual` does NOT have a `render()` method yet — that gets removed in Phase 2
- Serialization: no field name changes, so existing scene files work unchanged

---

## Phase 2: Move rendering logic from components to UIRenderer

Remove `render()` from components entirely. UIRenderer dispatches by type and reads component data.

### Tasks
- [ ] Remove `abstract render(UIRendererBackend)` from `UIComponent`
- [ ] Remove empty `render()` overrides from UICanvas, UIMask, UIScrollView, UIScrollbar
- [ ] Move UIPanel render logic into `UIRenderer.renderPanel(UIPanel)`
- [ ] Move UIImage render logic into `UIRenderer.renderImage(UIImage)` (all 4 image types: simple, sliced, tiled, filled)
- [ ] Move UIText render logic into `UIRenderer.renderText(UIText)`
  - This is the biggest move — includes layout calculation, glyph rendering, shadow passes
  - The `renderInternal`, `renderTextPass`, `calculateLayout`, etc. methods move to UIRenderer
  - UIText keeps layout state (`lines`, `lineWidths`, `naturalWidth`, `naturalHeight`, `layoutDirty`) and public query methods (`getNaturalWidth`, `getLineCount`, etc.)
  - UIText keeps `getRenderFont()`, `calculateBestFitFontSize()` (font selection is data, not rendering)
- [ ] Move UIButton render logic into `UIRenderer.renderButton(UIButton)` (temporary — Phase 4 removes this)
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
- [ ] Remove the `UIText.render(backend, x, y, width, height, rotation, pivotX, pivotY)` overload (editor explicit-position render) — replace with UIRenderer method that takes UIText + explicit bounds
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
| `components/ui/UIImage.java` | Remove `render()` and private render helpers |
| `components/ui/UIText.java` | Remove render methods, keep layout/font logic |
| `components/ui/UIButton.java` | Remove `render()` and private render helpers |
| `rendering/ui/UIRenderer.java` | Add `renderPanel()`, `renderImage()`, `renderText()`, `renderButton()`, update dispatch |

### Notes
- `UIRendererBackend` interface may still be useful for testing/mocking — evaluate whether to keep or inline into UIRenderer
- UIText's `render(backend, x, y, ...)` overload is used by the editor for preview rendering — need to check all call sites before removing

### Decision: What about UIRendererBackend?
Once components no longer call `backend.drawQuad()` etc., the interface exists only as self-documentation on UIRenderer. Options:
- **Keep it** — useful if we ever want a mock renderer for testing
- **Remove it** — less indirection, UIRenderer just has the methods directly

Recommend: keep for now, revisit when adding unit tests for UI rendering.

---

## Phase 3: Auto-migrate scene/prefab files (UIButton -> UIButton + UIPanel)

### Problem
Existing scene and prefab files have UIButton as the sole visual component on button GOs. After Phase 4, UIButton becomes interaction-only and requires a sibling UIPanel (or UIImage) for visual output. Without migration, all existing buttons become invisible.

### Audit of affected files
| File | UIButton occurrences |
|------|---------------------|
| `gameData/scenes/DemoScene.scene` | 5 |
| `gameData/scenes/Battle.scene` | 4 |
| `gameData/assets/prefabs/overworld_player.prefab.json` | 5 |

All have UIButton with only UITransform — **no sibling UIPanel or UIImage**.

### Migration strategy
**JSON-level auto-migration** in `SceneDataLoader.load()`, following the established pattern used for v3->v4 migration and Transform/UITransform cleanup. No version bump needed — the migration is idempotent (safe to run multiple times).

### How it works
For every `GameObjectData` in the scene:
1. Check if it has a `UIButton` component
2. Check if it does NOT already have a `UIPanel` or `UIImage` component
3. If both conditions met, inject a `UIPanel` component with color copied from UIButton's `color` field
4. The injected UIPanel is inserted before the UIButton in the components list (render order: panel draws first, button handles interaction)

### Migration logic (pseudocode)
```java
private void migrateStandaloneButtons(SceneData data) {
    if (data.getGameObjects() == null) return;

    for (GameObjectData go : data.getGameObjects()) {
        List<Component> components = go.getComponents();
        if (components == null) continue;

        UIButton button = null;
        boolean hasVisual = false;

        for (Component comp : components) {
            if (comp instanceof UIButton b) button = b;
            if (comp instanceof UIPanel || comp instanceof UIImage) hasVisual = true;
        }

        if (button != null && !hasVisual) {
            // Create UIPanel with button's color
            UIPanel panel = new UIPanel();
            panel.setColor(button.getColor());

            // Insert before the UIButton
            int buttonIndex = components.indexOf(button);
            components.add(buttonIndex, panel);

            System.out.println("Migrated standalone UIButton on: " + go.getName());
        }
    }
}
```

### The same migration runs for prefab files
Need to check `PrefabDataLoader` or equivalent for the hook point. Prefab files (`.prefab.json`) go through a similar deserialization path.

### Tasks
- [ ] Add `migrateStandaloneButtons(SceneData)` to `SceneDataLoader.load()` (after existing migrations)
- [ ] Add equivalent migration in prefab loading path
- [ ] Test: load Battle.scene, verify UIPanel is injected on button GOs
- [ ] Test: load overworld_player.prefab.json, verify migration
- [ ] Test: re-save a migrated scene, verify UIPanel persists (no duplicate on next load)
- [ ] Test: scene with UIButton + existing UIImage is NOT migrated (idempotent)

### Files

| File | Change |
|------|--------|
| `resources/loaders/SceneDataLoader.java` | Add `migrateStandaloneButtons()` call in `load()` |
| `resources/loaders/PrefabDataLoader.java` (or equivalent) | Same migration for prefab files |
| `editor/scene/UIEntityFactory.java` | Update `createButton()` to include UIPanel sibling |

### Notes
- Migration happens at load time in the loader, before any runtime processing
- Idempotent: if UIPanel already exists, no change
- The injected UIPanel uses UIButton's existing `color` field, so visual appearance is preserved
- UIButton's `color` field becomes redundant after Phase 4 (the sibling UIPanel owns the color), but that's fine — UIButton can still store normal/hover/pressed colors for tinting the sibling
- Programmatic button creation in game code (`DemoScene.java`, `ExampleScene.java`) also needs updating — these create buttons at runtime without going through scene files

---

## Phase 4: UIButton interaction-only refactor

UIButton currently renders its own quad/sprite with hover/press tint applied. Instead, it should modify a sibling UIPanel or UIImage — same pattern UIScrollbar already uses (scrollbar doesn't render; sibling UIImage on the track GO does).

**Prerequisite: Phase 3 migration must be complete** — all existing buttons must have a sibling UIPanel/UIImage before this phase removes UIButton's rendering.

### Tasks
- [ ] Remove all rendering from UIButton (already done in Phase 2's temporary `renderButton()`)
- [ ] UIButton applies state to sibling visual:
  - On hover/press state change, find sibling UIPanel or UIImage on same GameObject
  - Apply color tint or sprite swap to the sibling
  - Store original color to restore on exit
- [ ] Remove `renderButton()` from UIRenderer (no longer needed)
- [ ] Update UIButton color fields:
  - Keep `hoveredColor`, `pressedColor` for COLOR_TINT mode
  - Keep `hoveredSprite`, `pressedSprite` for SPRITE_SWAP mode
  - `color` field becomes "normal color" — applied to sibling on init and restored on hover exit
- [ ] UIButton no longer extends UIVisual — it stays as UIComponent (it's not a visual)
- [ ] Update `UIEntityFactory.createButton()` to include UIPanel sibling (done in Phase 3)
- [ ] Update programmatic button creation:
  - `scenes/DemoScene.java`
  - `scenes/ExampleScene.java`
  - Any other runtime code that creates UIButton
- [ ] Verify button hover/press visuals still work correctly

### Files

| File | Change |
|------|--------|
| `components/ui/UIButton.java` | Remove rendering, add sibling visual modification |
| `rendering/ui/UIRenderer.java` | Remove `renderButton()` |
| `scenes/DemoScene.java` | Add UIPanel to button GOs |
| `scenes/ExampleScene.java` | Add UIPanel to button GOs |

### Risk
Medium risk after Phase 3 migration. The migration ensures all serialized buttons have a sibling visual. Remaining risk is programmatic button creation in game code — requires manual audit and update.

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
- [ ] Verify UIRenderer handles all visual component types
- [ ] Verify AlphaGroup works with UIVisual interface
- [ ] Verify UIButton interaction works via sibling modification
- [ ] Verify scene file backwards compatibility (migration + no field renames)
- [ ] Verify prefab file migration works
- [ ] Verify editor inspector still works for all UI components
- [ ] Run full test suite
- [ ] Check for any remaining `UIRendererBackend` imports in component classes
- [ ] Update architecture docs if needed

---

## Testing Strategy

| Test | Validates |
|------|-----------|
| Existing UI component unit tests | No regression in data behavior |
| `UIMaskTest` | Mask still works after render() removal |
| Unit test: migration adds UIPanel to standalone UIButton GO | Phase 3 migration logic |
| Unit test: migration is idempotent (UIButton+UIPanel GO unchanged) | No double-migration |
| Unit test: migration preserves UIButton color on injected UIPanel | Visual fidelity |
| Manual: load Battle.scene, verify buttons visible | Migration works end-to-end |
| Manual: load overworld_player prefab, verify buttons visible | Prefab migration |
| Manual: button hover/press | UIButton interaction refactor works |
| Manual: AlphaGroup on a subtree | Alpha applies to all visuals |
| Manual: UIScrollView scrolling | Scroll + mask clipping still works |
| Manual: UIText with autoFit, wordWrap, shadow | Text rendering moved correctly |
| Manual: create new button in editor, verify UIPanel is included | Factory update |

## Architecture After

```
UIComponent (base)
  - canvas lookup, transform validation, raycastTarget
  - NO render() method

UIVisual extends UIComponent
  - color, alpha, RenderBounds, computeRenderBounds()
  - UIPanel, UIImage, UIText extend this

UIButton extends UIComponent
  - interaction state (hover, pressed)
  - modifies sibling UIVisual for visual feedback

UICanvas, UIMask, UIScrollView, UIScrollbar extend UIComponent
  - pure logic/marker components, no rendering concern

UIRenderer
  - owns ALL rendering logic
  - dispatches by component type
  - reads data from UIVisual subclasses

SceneDataLoader / PrefabDataLoader
  - auto-migrates standalone UIButton GOs by injecting sibling UIPanel
  - idempotent, runs at load time
```