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

## Phase 1: Panel Skeleton & General Settings Tab

Create the panel infrastructure and the first tab exposing editor settings.

### Tasks

- [ ] Create `EditorSettingsPanel extends EditorPanel` with the ConfigurationPanel pattern (header with Save/Revert/Reset, tab bar, dirty tracking, panel-scoped Ctrl+S shortcut)
- [ ] Create `EditorSettingsConfigTab implements ConfigTab` — the General tab
- [ ] Expose editor settings grouped in collapsible sections:
  - **Appearance**: `clearColor`, `gridColor`, `gridMajorColor`, `gridMajorLineInterval`, `fontSize`, `showGrid`, `showTileCoordinates`, `showElementBounds`
  - **Camera**: `defaultZoom`, `minZoom`, `maxZoom`, `cameraPanSpeed`, `zoomSpeed`, `trackpadPanMode`
  - **Paths**: `scenesDirectory`, `editorAssetsDirectory`, `gameAssetsDirectory`, `defaultUiFont`
  - **Window**: `fullscreen`, `windowWidth`, `windowHeight`, `vsync`
- [ ] Register panel in `EditorUIController` (instantiation, rendering, panel visibility)
- [ ] Add menu entry: Edit or Tools menu → "Editor Settings..."
- [ ] Save: calls `EditorConfig.save()` (writes editor.json)
- [ ] Revert: reloads `EditorConfig` from disk via `ConfigLoader`
- [ ] Reset: applies `EditorConfig.createDefault()` values to live config

### Files to Modify/Create

| File | Change |
|------|--------|
| `editor/panels/EditorSettingsPanel.java` | **NEW** — dockable panel with 3 tabs |
| `editor/panels/config/EditorSettingsConfigTab.java` | **NEW** — General settings tab |
| `editor/EditorUIController.java` | Register & render new panel |
| `editor/ui/EditorMenuBar.java` | Add "Editor Settings..." menu item |
| `editor/core/EditorConfig.java` | Add revert-from-disk helper if needed |

### Notes

- Live editing: widgets directly mutate the `EditorConfig` singleton. Grid color, clear color, etc. take effect immediately since the renderer reads from the live config each frame.
- Font size changes may require an ImGui font atlas rebuild — flag for next frame rather than applying mid-frame.

---

## Phase 2: Shortcuts Tab

Add a tab to view and rebind all keyboard shortcuts from within the editor.

### Tasks

- [ ] Create `ShortcutsConfigTab implements ConfigTab`
- [ ] Display all actions from `ShortcutRegistry.getAllActions()` grouped by category (file, edit, view, tools, panels, camera, etc.)
- [ ] For each action show: display name, current binding (or "default" badge if not customized), Reset button (if customized)
- [ ] Keyboard layout selector at top (QWERTY/AZERTY) — changing layout switches the active bindings set
- [ ] Search/filter text box to quickly find a shortcut
- [ ] Click on a binding to enter **rebind mode**: capture the next key+modifier combo, apply via `ShortcutRegistry.setBinding()`
- [ ] Conflict detection: after rebinding, call `ShortcutRegistry.findConflicts()` and warn the user inline (highlight conflicting entries)
- [ ] "Reset All" button to call `ShortcutRegistry.resetAllToDefaults()`
- [ ] Save: calls `ShortcutRegistry.saveConfig()` (writes editorShortcuts.json)
- [ ] Revert: reloads bindings from `editorShortcuts.json` and re-applies via `ShortcutRegistry.applyConfigBindings()`
- [ ] Reset to defaults: calls `ShortcutRegistry.resetAllToDefaults()` then marks dirty

### Files to Modify/Create

| File | Change |
|------|--------|
| `editor/panels/config/ShortcutsConfigTab.java` | **NEW** — shortcuts editing tab |
| `editor/shortcut/ShortcutRegistry.java` | May need `reloadConfig()` helper for revert |

### Key Design Decisions

- **All actions visible**: iterate `getAllActions()`, not the config file. Actions that have no custom binding show their default binding in dimmed text.
- **Rebind UX**: when a binding cell is clicked, it enters a "waiting for input" state (highlighted, "Press a key..." text). The next keypress is captured via ImGui key polling. Escape cancels. The panel should suppress normal shortcut processing during rebind (set a flag that `ShortcutRegistry.processShortcuts()` checks).
- **Scope awareness**: display scope as a subtle badge (Global, Panel: SceneView, etc.) so users understand why some bindings don't conflict.

---

## Phase 3: Color Palette Tab & EditorConfig Integration

Move UIPanel color presets into `EditorConfig` so they're user-customizable, and add the palette editor tab.

### Tasks

- [ ] Add `ColorPreset` data class: `String name`, `Vector4f color` (or `float[4]`)
- [ ] Add `List<ColorPreset> colorPalette` field to `EditorConfig` with `@Builder.Default` initialized to current 6 presets (Dark, Black, Blue, Darker, White, Overlay)
- [ ] Create `ColorPaletteConfigTab implements ConfigTab`
- [ ] Display each preset as an editable row: color swatch (color picker), name text field
- [ ] Add preset button (appends to list), Remove button per preset (with confirmation or undo)
- [ ] Drag-to-reorder presets (optional — can defer to later)
- [ ] Save/Revert/Reset follow the same EditorConfig persistence as Phase 1
- [ ] Update `UIPanelInspector` to read presets from `EditorConfig.getColorPalette()` instead of hardcoded arrays
- [ ] Ensure `UIPanelInspector` gracefully handles empty palette (no crash if user removes all presets)

### Files to Modify/Create

| File | Change |
|------|--------|
| `editor/core/ColorPreset.java` | **NEW** — simple data class |
| `editor/core/EditorConfig.java` | Add `colorPalette` field with defaults |
| `editor/panels/config/ColorPaletteConfigTab.java` | **NEW** — palette editor tab |
| `editor/ui/inspectors/UIPanelInspector.java` | Read presets from EditorConfig instead of hardcoded arrays |

### Serialization Notes

- `ColorPreset` needs to serialize cleanly with Gson. Use a simple POJO with `String name` and `Vector4f color` (Vector4f already serializes as `{x, y, z, w}` in the project).
- Adding `colorPalette` to `EditorConfig` is safe — unknown fields are silently ignored by the deserializer, so old config files without this field will get the `@Builder.Default` values.

---

## Phase 4: Polish & Code Review

- [ ] Verify all three tabs save/revert/reset independently but correctly (panel-level Save saves all tabs)
- [ ] Verify shortcuts tab shows panel-provided shortcuts (animation, hierarchy, etc.), not just global ones
- [ ] Verify conflict warnings display correctly and don't block saving (warn, don't prevent)
- [ ] Verify color palette changes in settings are immediately reflected in UIPanelInspector presets
- [ ] Test edge cases: empty palette, duplicate shortcut bindings, very long action lists
- [ ] Code review

## Testing Strategy

- **General tab**: Change clear color → viewport background updates. Change grid color → grid updates. Save → restart editor → settings persist. Revert → changes undone.
- **Shortcuts tab**: Rebind Ctrl+S to Ctrl+Shift+S → verify new binding works. Check conflict warning appears when binding collides. Reset → default restored. Save → restart → custom binding persists.
- **Color palette**: Add a preset → appears in UIPanelInspector. Remove a preset → disappears. Edit color → UIPanelInspector shows updated swatch. Save → restart → custom palette persists.
- **Cross-tab**: Save with changes in multiple tabs → all persist. Revert → all revert. Reset → all reset to defaults.
