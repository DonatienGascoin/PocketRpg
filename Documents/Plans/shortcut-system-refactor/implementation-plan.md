# Shortcut System Refactor

## Overview

The shortcut system has four structural problems:

1. **Panel focus context is manually maintained** — `buildShortcutContext()` hardcodes each panel individually. Adding a new panel requires editing that method. The scene view was never added, which broke WASD shortcuts.
2. **Two shortcut systems run in parallel** — Several panels handle keys locally via `ImGui.isKeyPressed` instead of going through the `ShortcutRegistry`. This causes double-firing and bypasses conflict resolution, rebinding, and modifier awareness.
3. **Scene view is not part of the panel system** — `SceneViewport` doesn't extend `EditorPanel`, so it has no `panelId` or `isFocused()` compatible with the shortcut context.
4. **Shortcut context is not shared** — `ViewportInputHandler` had to build its own `ShortcutContext` because the one built in `EditorApplication.render()` isn't accessible. Any future consumer would face the same problem.

## Phase 1: Auto-discover panel focus

**Goal**: Replace the hardcoded panel list in `buildShortcutContext()` with automatic discovery.

- [ ] Add a `List<EditorPanel> panels` field to `EditorUIController`
- [ ] Populate it in `createPanels()` — every panel that is created gets added to the list
- [ ] Rewrite `buildShortcutContext()` to iterate the list:
  ```java
  for (EditorPanel panel : panels) {
      if (panel.isFocused()) {
          builder.panelFocused(panel.getPanelId());
      }
  }
  ```
- [ ] Remove all the individual `if (panel != null && panel.isFocused())` checks
- [ ] Verify existing panel-scoped shortcuts (configuration save, animator save/undo/redo) still work

### Files to Modify

| File | Change |
|------|--------|
| `EditorUIController.java` | Add `panels` list, populate in `createPanels()`, rewrite `buildShortcutContext()` |

## Phase 2: Give the scene view a panel identity

**Goal**: Make the scene view visible to the shortcut context without changing `SceneViewport` into an `EditorPanel` (it has different rendering lifecycle).

- [ ] Add `sceneViewFocused` boolean field to `EditorUIController`
- [ ] In `renderSceneViewport()`, after rendering, capture `sceneViewport.isFocused()` into that field
- [ ] In `buildShortcutContext()`, add the scene view panel focus:
  ```java
  if (sceneViewFocused) {
      builder.panelFocused(EditorShortcuts.PanelIds.SCENE_VIEW);
  }
  ```
- [ ] Remove the custom `ShortcutContext.builder()` call from `ViewportInputHandler.handleCameraInput()` — it should receive the context from outside (see Phase 4)

### Files to Modify

| File | Change |
|------|--------|
| `EditorUIController.java` | Add `sceneViewFocused`, set it in `renderSceneViewport()`, include in `buildShortcutContext()` |
| `ViewportInputHandler.java` | Remove local `ShortcutContext.builder()` call (done in Phase 4) |

## Phase 3: Migrate local panel shortcuts to the registry

**Goal**: Eliminate direct `ImGui.isKeyPressed` shortcuts from panels. All editor shortcuts go through `ShortcutRegistry`.

### 3a: AnimationEditorPanel

The panel's `processKeyboardShortcuts()` handles 11 local shortcuts. Some already exist in the registry (Ctrl+S, Ctrl+N, Ctrl+Z, Ctrl+Y, F5), others are new.

- [ ] Register new shortcuts in `EditorShortcuts` with `PANEL_FOCUSED` scope on `animationEditor`:
  - `editor.animation.playPause` — Space
  - `editor.animation.prevFrame` — LeftArrow
  - `editor.animation.nextFrame` — RightArrow
  - `editor.animation.firstFrame` — Home
  - `editor.animation.lastFrame` — End
  - `editor.animation.deleteFrame` — Delete
- [ ] Add these action IDs as constants in `EditorShortcuts`
- [ ] Add default bindings in `getDefaultBindings()`
- [ ] Add handler methods to `EditorShortcutHandlers` interface
- [ ] Implement handlers — they need access to `AnimationEditorPanel` to call `previewRenderer.togglePlayback()`, navigate frames, etc. Add public methods on the panel for each action if not already exposed
- [ ] Wire handlers in `EditorShortcuts.bindHandlers()`
- [ ] Remove `processKeyboardShortcuts()` from `AnimationEditorPanel` entirely
- [ ] Remove the existing duplicate local shortcuts (Ctrl+S, Ctrl+N, Ctrl+Z, Ctrl+Y, F5) that already exist in the registry as animator-scoped shortcuts

### 3b: SpriteEditorPanel

- [ ] The sprite editor handles Ctrl+Z and Ctrl+Y locally for its own undo/redo stack. Register these in `EditorShortcuts` with `PANEL_FOCUSED` scope on a new `spriteEditor` panel ID:
  - `editor.spriteEditor.undo` — Ctrl+Z
  - `editor.spriteEditor.redo` — Ctrl+Shift+Z / Ctrl+Y
- [ ] Add SpriteEditorPanel to `EditorPanel` system (give it a panelId if it doesn't have one)
- [ ] Add handler methods, wire in `bindHandlers()`
- [ ] Remove `handleKeyboardShortcuts()` from `SpriteEditorPanel`

### 3c: ViewportInputHandler Escape handling

- [ ] The Escape key handling in `handleKeyboardShortcuts()` clears tool selection (brush/fill/rectangle). The global `editor.entity.cancel` (ENTITY_CANCEL) already handles Escape at GLOBAL scope. Instead of registering a separate scene-view-scoped Escape shortcut (which would shadow the global one), extend the existing `ENTITY_CANCEL` handler to also clear tool selection.
- [ ] Update the `onEntityCancel` handler implementation to also clear brush/fill/rectangle selection
- [ ] Remove `handleKeyboardShortcuts()` from `ViewportInputHandler`

### 3d: TilesetPalettePanel Escape handling

- [ ] Register `editor.tileset.clearSelection` with `PANEL_FOCUSED` scope on `tilesetPalette`, bound to Escape
- [ ] Remove local `ImGui.isKeyPressed(Escape)` check
- [ ] Add handler method and wire in `bindHandlers()`

**Note**: Popup navigation keys (arrow keys in AssetPickerPopup, ComponentBrowserPopup, PostEffectBrowserPopup) and dialog escape keys (AnimatorEditorPanel new dialog, EntityInspector prefab dialog, HierarchyTreeRenderer rename) are NOT migrated. These are widget-level UI navigation, not editor shortcuts.

### Files to Modify

| File | Change |
|------|--------|
| `EditorShortcuts.java` | Add ~10 new action IDs, register in `registerDefaults`, add to `getDefaultBindings` |
| `EditorShortcutHandlers.java` | Add ~10 new handler methods |
| `EditorShortcutHandlersImpl.java` | Implement new handler methods |
| `AnimationEditorPanel.java` | Remove `processKeyboardShortcuts()`, expose public methods for frame navigation/playback |
| `SpriteEditorPanel.java` | Remove `handleKeyboardShortcuts()`, expose undo/redo as public methods |
| `ViewportInputHandler.java` | Remove `handleKeyboardShortcuts()` |
| `TilesetPalettePanel.java` | Remove local Escape check |

## Phase 4: Share the shortcut context

**Goal**: Build the `ShortcutContext` once per frame and make it available everywhere, instead of having consumers build their own.

- [ ] Add a `ShortcutContext lastContext` field to `ShortcutRegistry`
- [ ] In `processShortcuts()`, store the context: `this.lastContext = context`
- [ ] Add a `getLastContext()` getter
- [ ] Update `ViewportInputHandler.handleCameraInput()` to use `registry.getLastContext()` instead of building its own context
- [ ] Remove `ShortcutContext` import and builder usage from `ViewportInputHandler`

### Files to Modify

| File | Change |
|------|--------|
| `ShortcutRegistry.java` | Add `lastContext` field, store in `processShortcuts()`, add getter |
| `ViewportInputHandler.java` | Use `registry.getLastContext()` for `isActionHeld` calls |

## Phase 5: Arrow key camera panning via registry

**Goal**: Consistent with the WASD fix — arrow key panning should also go through the shortcut system.

- [ ] Register arrow key camera pan shortcuts in `EditorShortcuts`:
  - `editor.camera.panUpArrow` — UpArrow, `PANEL_FOCUSED` on `sceneView`
  - `editor.camera.panDownArrow` — DownArrow
  - `editor.camera.panLeftArrow` — LeftArrow
  - `editor.camera.panRightArrow` — RightArrow
- [ ] Add default bindings in `getDefaultBindings()`
- [ ] Update `ViewportInputHandler.handleCameraInput()` to use `isActionHeld` for arrow keys too
- [ ] Remove direct `ImGui.isKeyDown(ImGuiKey.UpArrow/DownArrow/LeftArrow/RightArrow)` calls

### Files to Modify

| File | Change |
|------|--------|
| `EditorShortcuts.java` | Add 4 arrow key action IDs, register, add defaults |
| `ViewportInputHandler.java` | Use `isActionHeld` for arrow keys |

## Phase 6: Code review & cleanup

- [ ] Verify no remaining direct `ImGui.isKeyPressed`/`isKeyDown` for editor shortcuts outside popups/dialogs
- [ ] Verify all panel-scoped shortcuts work when their panel is focused
- [ ] Verify Ctrl+S no longer triggers camera pan or any plain-S shortcut
- [ ] Verify WASD camera panning works when scene view is focused
- [ ] Verify arrow key panning works
- [ ] Verify animation editor shortcuts (Space, arrows, Home, End, Delete) work when panel is focused
- [ ] Verify shortcut settings panel shows all new shortcuts
- [ ] Run `mvn compile` — clean build
- [ ] Run `mvn test` — all tests pass

## Testing Strategy

Manual testing (no automated shortcut tests exist):

1. **Modifier conflict**: Hold Ctrl, press S → should save, NOT pan camera
2. **WASD panning**: Focus scene view, press W/A/S/D → camera pans
3. **WASD when unfocused**: Focus hierarchy panel, press S → nothing happens (no panning)
4. **Animation editor**: Focus animation panel, press Space → toggles playback. Press Left/Right → navigates frames
5. **Arrow panning**: Focus scene view, press arrow keys → camera pans
6. **Arrow vs animation**: Focus animation panel, press Left/Right → navigates frames (not camera)
7. **Shortcut settings**: Open shortcut configuration → all new shortcuts appear and can be rebound
