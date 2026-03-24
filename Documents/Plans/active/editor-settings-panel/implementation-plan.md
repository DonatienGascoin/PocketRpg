# Editor Settings Panel

## Overview

**Problem:** Editor settings (grid colors, camera, zoom, font size), keyboard shortcuts, and UIPanel color presets are either not editable from the UI or only partially exposed. Users must hand-edit JSON files or recompile to change them.

**Solution:** A new dockable `EditorSettingsPanel` with three tabs — General, Shortcuts, and Color Palette — following the same pattern as the existing `ConfigurationPanel`. Live editing model: changes apply immediately, with explicit Save/Revert/Reset actions.

## Existing Systems to Leverage

| System | Location | How Used |
|--------|----------|----------|
| **ConfigurationPanel** | `editor/panels/ConfigurationPanel.java` | Pattern to follow (tabs, save/revert/reset, dirty tracking) |
| **ConfigTab** | `editor/panels/config/ConfigTab.java` | Interface for each tab |
| **EditorConfig** | `editor/core/EditorConfig.java` | Stores editor settings, already has save/load |
| **ConfigLoader** | `config/ConfigLoader.java` | Loading/saving config JSON files |
| **ShortcutRegistry** | `editor/shortcut/ShortcutRegistry.java` | All shortcut actions, binding management, conflict detection |
| **ShortcutConfig** | `editor/shortcut/ShortcutConfig.java` | Shortcut persistence (editorShortcuts.json) |
| **EditorPanel** | `editor/panels/EditorPanel.java` | Base class for dockable panels |
| **FieldEditors** | `editor/ui/fields/FieldEditors.java` | Reusable field widgets (color, float, int, checkbox, etc.) |
| **EditorColors** | `editor/core/EditorColors.java` | Semantic editor colors reference |
| **UIPanelInspector** | `editor/ui/inspectors/UIPanelInspector.java` | Current hardcoded color presets to migrate |

## Design Decisions

### Panel type: dockable (not modal)
Consistent with `ConfigurationPanel`. Users can dock it, keep it open, and see changes live.

### Revert strategy: field-copy from fresh deserialization
`ConfigLoader.loadAllConfigs()` has a `if (configInstance == null)` guard — it won't reload already-loaded configs. The existing `RenderingConfigTab.revert()` calls it and is silently broken. We need a proper revert:

1. Add `ConfigLoader.reloadConfig(ConfigType)` — forces re-read from disk, returns a fresh instance (does NOT replace the singleton).
2. Add `EditorConfig.copyEditableFieldsFrom(EditorConfig source)` — copies all user-editable fields from the fresh instance into the live singleton, preserving the object reference that everything else holds.
3. Non-editable runtime fields (recentScenes, recentAssets, favoriteAssets, panelVisibility) are NOT copied during revert — they are session state, not "settings".

### Font size: restart required
ImGui font atlas rebuild requires destroying textures and recreating them. This is fragile mid-session. Font size changes will be marked "(requires restart)" in the UI and only take effect on next editor launch. No flag/rebuild mechanism needed.

### No undo integration
Consistent with `ConfigurationPanel` — config changes use Save/Revert, not the undo system. This is a deliberate choice: config editing is separate from scene editing.

### Shortcut rebind suppression
During rebind mode, the shortcuts tab sets a flag (`ShortcutRegistry.setRebindMode(true)`) that causes `processShortcuts()` to skip all processing. This prevents the captured key from triggering an action. Escape is reserved to cancel rebind — it is not bindable. Modifier-only combos (Ctrl alone) are not valid bindings — a base key is always required (matches existing `ShortcutBinding` design).

### Color palette: simple Map serialization
Use `Map<String, float[]>` entries in a list rather than a custom `ColorPreset` class. Actually — for Gson and clean code, a minimal POJO is cleaner. Use a simple inner class or record-style class with `String name` and `float[] color` (4 floats). `Vector4f` serializes as `{x,y,z,w}` which is fine but `float[]` is more natural for color presets that map directly to ImGui APIs.

### Play mode behavior
Panel remains accessible during play mode (read-only wouldn't make sense — it's editor config, not game state). Shortcut rebinding is blocked during play mode since `ShortcutRegistry` suppresses processing anyway.

---

## Phase 1: Panel Skeleton + Infrastructure

Create the panel shell, register it, and add the menu entry. No tabs yet — just the header with Save/Revert/Reset and an empty tab bar. This validates the wiring before adding content.

### Tasks

- [ ] Add `ConfigLoader.reloadConfig(ConfigType)` — reads file from disk, returns fresh instance without replacing the singleton
- [ ] Add `EditorConfig.copyEditableFieldsFrom(EditorConfig source)` — copies appearance, camera, paths, window, colorPalette fields (NOT recent files, favorites, panelVisibility)
- [ ] Create `EditorSettingsPanel extends EditorPanel` with:
  - Header: Save / Revert / Reset to Defaults buttons (same pattern as `ConfigurationPanel`)
  - Dirty tracking
  - Tab bar (empty for now, tabs added in subsequent phases)
  - Panel-scoped Ctrl+S shortcut for save
- [ ] Register panel in `EditorUIController` (instantiation, `initPanel()`, render call)
- [ ] Add `panelVisibility` entry: `"editorSettings"`
- [ ] Add menu entry in `EditorMenuBar` (Edit menu → "Editor Settings...") that calls `editorSettingsPanel.requestFocus()`
- [ ] Add keyboard shortcut to open panel (e.g., Ctrl+Comma or similar — confirm with user)

### Files to Modify/Create

| File | Change |
|------|--------|
| `editor/panels/EditorSettingsPanel.java` | **NEW** — dockable panel with tab bar |
| `config/ConfigLoader.java` | Add `reloadConfig(ConfigType)` method |
| `editor/core/EditorConfig.java` | Add `copyEditableFieldsFrom()` method |
| `editor/EditorUIController.java` | Register & render new panel |
| `editor/ui/EditorMenuBar.java` | Add "Editor Settings..." menu item |

### Validation
- Panel opens/closes from menu
- Save/Revert/Reset buttons render (disabled states correct)
- Dirty indicator works
- Panel state persists across editor restarts

---

## Phase 2: General Settings Tab

Add the first tab exposing editor settings. Split into collapsible sections.

### Tasks

- [ ] Create `EditorSettingsConfigTab implements ConfigTab`
- [ ] **Appearance** section (collapsible, default open):
  - `clearColor` — color picker (live: viewport background updates immediately)
  - `gridColor` — color picker (live: grid updates immediately)
  - `gridMajorColor` — color picker
  - `gridMajorLineInterval` — int slider
  - `showGrid` — checkbox
  - `showTileCoordinates` — checkbox
  - `showElementBounds` — checkbox
  - `fontSize` — float field, marked "(requires restart)"
- [ ] **Camera** section:
  - `defaultZoom` — float slider
  - `minZoom` / `maxZoom` — float sliders with validation (min < max)
  - `cameraPanSpeed` — float slider
  - `zoomSpeed` — float slider
  - `trackpadPanMode` — checkbox
- [ ] **Paths** section:
  - `scenesDirectory` — text input
  - `editorAssetsDirectory` — text input
  - `gameAssetsDirectory` — text input
  - `defaultUiFont` — text input (could add file picker later)
- [ ] **Window** section:
  - `fullscreen` — checkbox, marked "(requires restart)"
  - `windowWidth` / `windowHeight` — int fields, marked "(requires restart)"
  - `vsync` — checkbox, marked "(requires restart)"
- [ ] Wire tab into `EditorSettingsPanel`
- [ ] Implement save: `ConfigLoader.saveConfigToFile(config, ConfigType.EDITOR)`
- [ ] Implement revert: `ConfigLoader.reloadConfig(EDITOR)` → `config.copyEditableFieldsFrom(fresh)`
- [ ] Implement reset: `EditorConfig.createDefault()` → `config.copyEditableFieldsFrom(defaults)`

### Files to Modify/Create

| File | Change |
|------|--------|
| `editor/panels/config/EditorSettingsConfigTab.java` | **NEW** — General settings tab |
| `editor/panels/EditorSettingsPanel.java` | Wire in the new tab |

### Notes

- Live editing: widgets directly mutate the `EditorConfig` singleton via getters/setters + `markDirty`. Grid, clear color, camera settings take effect immediately since renderers read from the live config each frame.
- Fields marked "(requires restart)" show a `ImGui.textDisabled()` hint. The value is still saved — it just won't apply until next launch.
- Tooltips on each field explaining what it does (follow `RenderingConfigTab` pattern).

---

## Phase 3: Shortcuts Tab

Add a tab to view and rebind all keyboard shortcuts from within the editor.

### Tasks

- [ ] Create `ShortcutsConfigTab implements ConfigTab`
- [ ] **Layout selector** at top: QWERTY / AZERTY radio buttons. Changing layout:
  - Saves any pending custom bindings for the current layout first
  - Switches `ShortcutConfig.keyboardLayout`
  - Reloads bindings for the new layout
  - Marks dirty
- [ ] **Search box**: filters displayed actions by name, category, or binding text
- [ ] **Action table** grouped by category (collapsible headers):
  - Column 1: Action display name
  - Column 2: Current binding (button-style — click to rebind). Show "Default" badge in dimmed text if not customized, "Custom" badge if overridden
  - Column 3: Scope badge (Global, Panel: X) — dimmed text
  - Column 4: Reset button (only enabled if customized)
- [ ] **Rebind mode** — when a binding button is clicked:
  - Button text changes to "Press a key..." with highlight color
  - Set `ShortcutRegistry.setRebindMode(true)` to suppress normal shortcut processing
  - Each frame, poll all ImGui keys to detect the next keypress + active modifiers
  - On valid key detected: create `ShortcutBinding`, apply via `ShortcutRegistry.setBinding()`, exit rebind mode
  - Escape: cancel rebind, restore previous binding, exit rebind mode
  - Clicking elsewhere: cancel rebind
  - Modifier-only presses (just Ctrl/Shift/Alt without a base key) are ignored — wait for base key
  - Tab key: captured as a binding (not consumed by ImGui since we suppress shortcuts)
- [ ] **Conflict detection**: after rebinding, call `ShortcutRegistry.findConflicts()`. If conflicts exist:
  - Show warning icon + tooltip listing conflicting actions next to the binding
  - Do NOT block the binding — user may intend to override (different scopes don't truly conflict)
- [ ] **"Reset All Shortcuts"** button with confirmation
- [ ] Save: `ShortcutRegistry.saveConfig()`
- [ ] Revert: add `ShortcutRegistry.reloadConfig()` — re-reads `editorShortcuts.json` and re-applies bindings
- [ ] Reset to defaults: `ShortcutRegistry.resetAllToDefaults()`, mark dirty
- [ ] **Menu shortcut hints**: verify that `EditorMenuBar` reads binding display text from `ShortcutRegistry.getBindingDisplay()` so rebinds are reflected in menus. If hardcoded, fix.

### Files to Modify/Create

| File | Change |
|------|--------|
| `editor/panels/config/ShortcutsConfigTab.java` | **NEW** — shortcuts editing tab |
| `editor/shortcut/ShortcutRegistry.java` | Add `rebindMode` flag, `reloadConfig()` method |
| `editor/panels/EditorSettingsPanel.java` | Wire in the new tab |
| `editor/ui/EditorMenuBar.java` | Verify/fix shortcut hint display (if needed) |

### Edge Cases

- Actions registered by panels that aren't currently loaded: `getAllActions()` returns everything registered at init time, including panel shortcuts. All are editable.
- Very long action lists: the search box and collapsible categories prevent overwhelming the UI. Consider `ImGuiListClipper` if performance is an issue with 50+ actions.
- Rebind during play mode: play mode disables shortcuts via `playModeActive` flag. Rebind mode should also be blocked (show "Exit play mode to rebind" message).

---

## Phase 4: Color Palette Tab

Move UIPanel color presets into `EditorConfig` so they're user-customizable, and add the palette editor tab.

### Tasks

- [ ] Add `ColorPreset` as a simple POJO: `String name`, `float[] color` (length 4, RGBA). Use `float[]` because ImGui APIs (`colorButton`, `colorEdit4`) work with `float[]` directly — avoids constant Vector4f↔float[] conversion
- [ ] Add `List<ColorPreset> colorPalette` field to `EditorConfig` with `@Builder.Default` initialized to the current 6 presets (Dark, Black, Blue, Darker, White, Overlay — values from `UIPanelInspector`)
- [ ] Include `colorPalette` in `copyEditableFieldsFrom()` (deep copy the list)
- [ ] Create `ColorPaletteConfigTab implements ConfigTab`
- [ ] For each preset, render an editable row:
  - Color swatch button (opens ImGui color picker on click)
  - Name text field
  - Remove button (with shift-click skip confirmation, or just remove — list is small)
- [ ] "Add Preset" button at bottom (appends a new entry with default name "New Color" and white color)
- [ ] Drag-to-reorder: use up/down arrow buttons (same pattern as `RenderingConfigTab` post-effects). Full drag-and-drop can be deferred.
- [ ] Save/Revert/Reset follow the same `EditorConfig` persistence as Phase 2
- [ ] Update `UIPanelInspector` to read presets from `EditorConfig`:
  - Get `EditorConfig` via `ConfigLoader.getConfig(ConfigType.EDITOR)`
  - Replace hardcoded `COLOR_PRESETS` / `PRESET_NAMES` with `config.getColorPalette()`
  - Handle empty palette gracefully (skip preset section, don't crash)
- [ ] Palette is shared: any component inspector that wants color presets can read from the same `EditorConfig.getColorPalette()`. Currently only `UIPanelInspector` uses presets but the door is open.

### Files to Modify/Create

| File | Change |
|------|--------|
| `editor/core/ColorPreset.java` | **NEW** — simple POJO (name + float[4]) |
| `editor/core/EditorConfig.java` | Add `colorPalette` field with defaults |
| `editor/panels/config/ColorPaletteConfigTab.java` | **NEW** — palette editor tab |
| `editor/panels/EditorSettingsPanel.java` | Wire in the new tab |
| `editor/ui/inspectors/UIPanelInspector.java` | Read presets from EditorConfig |

### Serialization Notes

- `float[]` serializes naturally with Gson as a JSON array `[0.2, 0.2, 0.2, 1.0]`.
- Adding `colorPalette` to `EditorConfig` is safe — unknown fields are silently ignored by the deserializer, so old config files without this field get the `@Builder.Default` values.
- `copyEditableFieldsFrom()` must deep-copy the list (new list + new `ColorPreset` instances) to avoid aliasing between live config and reverted snapshot.

---

## Phase 5: Polish & Code Review

- [ ] Verify all three tabs save/revert/reset independently but correctly (panel-level Save saves all tabs, Revert reverts all)
- [ ] Verify shortcuts tab shows ALL registered actions — global, panel-provided (animation, hierarchy, etc.)
- [ ] Verify conflict warnings display inline and don't block saving
- [ ] Verify color palette changes in settings are immediately reflected in `UIPanelInspector` presets
- [ ] Verify menu shortcut hints update after rebinding
- [ ] Test circular dependency: editor settings panel is open → Reset to Defaults → `panelVisibility` is NOT reset (excluded from copyEditableFieldsFrom)
- [ ] Test edge cases: empty palette, duplicate shortcut bindings, rebind to same key, rebind then revert
- [ ] Verify play mode doesn't break anything (panel accessible, rebind blocked)
- [ ] Code review

## Testing Strategy

- **Phase 1**: Panel opens/closes from menu and keyboard shortcut. Save/Revert/Reset buttons render correctly. Dirty indicator appears/clears. Panel visibility persists across restarts.
- **Phase 2**: Change clear color → viewport background updates live. Change grid color → grid updates live. Save → restart → settings persist. Revert → changes undone. Reset → all back to defaults. "(requires restart)" fields show hint.
- **Phase 3**: All shortcuts visible and grouped. Rebind Ctrl+S to Ctrl+Shift+S → new binding works, menu hint updates. Conflict warning appears on collision. Escape cancels rebind. Reset individual → default restored. Reset All → all defaults. Save → restart → custom bindings persist. Revert → custom bindings from disk restored. Layout switch → bindings swap correctly.
- **Phase 4**: Presets display in palette tab. Edit color → UIPanelInspector shows updated swatch immediately. Add preset → appears in both places. Remove preset → gone. Save → restart → custom palette persists. Empty palette → UIPanelInspector gracefully hides presets section.
- **Cross-tab**: Save with changes in multiple tabs → all persist. Revert → all revert. Reset → all reset.
