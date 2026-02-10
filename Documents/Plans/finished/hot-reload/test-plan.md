# Test Plan - Scene Reload with Registry Refresh (PR #8)

## Manual Tests (in the editor)

### Scene Reload Core

- [ ] **Basic reload** - Open a scene, press `Ctrl+Shift+R`, verify the scene reloads without errors
- [ ] **Camera preservation** - Move/zoom the camera to a specific position, reload, verify camera position and zoom are unchanged
- [ ] **Selection preservation** - Select one or more entities, reload, verify the same entities are still selected
- [ ] **Dirty flag preservation** - Modify something (make scene dirty), reload, verify the dirty indicator is still showing
- [ ] **Clean scene reload** - Open a scene without modifying it, reload, verify it stays clean (no dirty flag)
- [ ] **File > Reload Scene menu** - Use the menu item instead of the shortcut, verify it works identically

### Registry Refresh

- [ ] **New Component discovery** - Add a new `Component` subclass, recompile (without restarting editor), press `Ctrl+Shift+R`, verify it appears in "Add Component" menu
- [ ] **New custom inspector discovery** - Add a new `@InspectorFor` class, recompile, reload, verify the custom inspector renders
- [ ] **New PostEffect discovery** - Add a new `PostEffect` subclass, recompile, reload, verify it appears in post-effect options

### Play Mode Guard

- [ ] **Shortcut blocked in play mode** - Enter play mode, press `Ctrl+Shift+R`, verify nothing happens
- [ ] **Menu blocked in play mode** - Enter play mode, verify "Reload Scene" menu item is disabled/grayed out

### Edge Cases

- [ ] **Untitled scene reload** - Create a new scene (no file path), make edits, reload, verify it rebuilds from snapshot
- [ ] **Failed reload recovery** - If reload fails, verify old scene remains intact and functional
- [ ] **Undo history cleared** - Make changes, undo a few, reload, verify undo/redo are both empty after reload

### PlayMode Refactor (regression)

- [ ] **Play mode lifecycle** - Enter play mode, verify game runs, pause/resume/stop all work, editor scene is restored on stop
- [ ] **Audio in play mode** - Verify audio plays during play mode and stops when exiting
- [ ] **Scene transitions in play mode** - Verify scene transitions still work in play mode (if applicable)

---

## Unit Tests (automated)

### `EditorStateSnapshotTest`

- `capture` correctly reads camera position, zoom, selected entity IDs, scene path, and dirty flag
- `restore` correctly sets camera position, zoom, re-selects entities by ID, and restores dirty flag
- Capture creates a defensive copy of camera position (mutating original doesn't affect snapshot)
- Restore with missing entities (entity ID no longer in reloaded scene) doesn't crash
- Restore with empty selection

### `EditorSharedAudioContextTest`

- `initialize()` and `destroy()` are no-ops (don't call delegate)
- All playback methods (`playOneShot`, `pauseAll`, `resumeAll`, `stopAll`, etc.) delegate to the wrapped context
- `update()` delegates to wrapped context

### `RegistriesRefreshRequestEventTest`

- Event can be published and received through `EditorEventBus`

### `ComponentRegistryReinitializeTest`

- `reinitialize()` clears existing entries and re-scans (verify count resets and rebuilds)

### `PostEffectRegistryReinitializeTest`

- `reinitialize()` clears existing entries and re-scans

### `CustomComponentEditorRegistryReinitializeTest`

- `reinitialize()` clears existing entries and re-scans
