# PocketRPG Editor - Shortcut Manager System

## Problem Statement

Shortcuts are scattered across multiple classes (EditorMenuBar, panels, etc.) with no centralization. This makes rebinding impossible and creates inconsistent behavior.

## Goals

1. **Centralized management** - Single registry for all shortcuts
2. **Rebinding support** - Persist custom bindings to `editor/editorShortcuts.json`
3. **Scope/priority system** - Respects UI stack:
   - `POPUP` (highest) - Active when modal/popup is open
   - `PANEL_FOCUSED` - Active when specific panel has keyboard focus
   - `PANEL_VISIBLE` - Active when specific panel is rendered
   - `GLOBAL` (lowest) - Always active unless overridden
4. **Conflict detection** - Warns when bindings overlap in same scope
5. **Text input awareness** - Skips shortcuts when typing in text fields

## Architecture

```
shortcut/
├── ShortcutBinding.java       # Key + modifiers (Ctrl+S, etc.)
├── ShortcutScope.java         # Priority enum
├── ShortcutAction.java        # Action definition with builder
├── ShortcutContext.java       # Current frame UI state
├── ShortcutRegistry.java      # Central registry + processing
├── ShortcutConfig.java        # JSON persistence
├── EditorShortcuts.java       # All action IDs + defaults
├── EditorShortcutHandlers.java# Handler interface
├── ShortcutsConfigTab.java    # Config panel UI for rebinding
└── ShortcutContextBuilder.java# Helper for panel state reporting
```

## Key Design Decisions

1. **Actions registered with placeholder handlers** - `EditorShortcuts.registerDefaults()` sets up all actions with empty handlers, then `EditorShortcuts.bindHandlers()` connects actual implementation later. This separates definition from wiring.

2. **Custom bindings override defaults** - Registry stores custom bindings separately. `getBinding(actionId)` returns custom if exists, else default.

3. **Handlers are always Runnable** - Parameterized actions (like "Open Scene") trigger dialogs internally, matching existing pattern in EditorMenuBar.

4. **Config format** - Compact string: `"CS:S"` = Ctrl+Shift+S

## Integration Steps

### 1. Initialize (in EditorApplication or EditorContext)

```java
ShortcutRegistry registry = ShortcutRegistry.getInstance();
EditorShortcuts.registerDefaults(registry);
registry.loadConfig("editor/editorShortcuts.json");
EditorShortcuts.bindHandlers(registry, new EditorShortcutHandlersImpl(...));
```

### 2. Process shortcuts (START of frame, before ImGui windows)

```java
ShortcutContext context = ShortcutContext.current();
registry.processShortcuts(context);
```

### 3. Add config tab to ConfigPanel

```java
// In ConfigPanel.initializeTabs():
tabs.add(new ShortcutsConfigTab(context));
```

### 4. Update menu bar to show current bindings

```java
// Instead of hardcoded "Ctrl+S":
String shortcut = registry.getBindingDisplay(EditorShortcuts.FILE_SAVE);
if (ImGui.menuItem("Save", shortcut)) { ... }
```

### 5. Implement EditorShortcutHandlers

```java
public class EditorShortcutHandlersImpl implements EditorShortcutHandlers {
    private final EditorContext context;
    private final ConfigPanel configPanel;
    
    @Override
    public void onNewScene() {
        // Your existing logic from EditorMenuBar.handleNewScene()
    }
    
    @Override
    public void onUndo() {
        UndoManager.getInstance().undo();
        if (context.getCurrentScene() != null) {
            context.getCurrentScene().markDirty();
        }
    }
    // ... other handlers
}
```

## JSON Config Example

`editor/editorShortcuts.json`:
```json
{
  "bindings": {
    "editor.file.save": "C:S",
    "editor.edit.undo": "C:Z",
    "editor.tool.brush": ":B"
  }
}
```

Format: `"[C][S][A]:KeyName"` where C=Ctrl, S=Shift, A=Alt

## Defined Shortcuts

| ID | Default | Scope |
|----|---------|-------|
| `editor.file.new` | Ctrl+N | GLOBAL |
| `editor.file.open` | Ctrl+O | GLOBAL |
| `editor.file.save` | Ctrl+S | GLOBAL |
| `editor.file.saveAs` | Ctrl+Shift+S | GLOBAL |
| `editor.edit.undo` | Ctrl+Z | GLOBAL |
| `editor.edit.redo` | Ctrl+Shift+Z | GLOBAL |
| `editor.edit.delete` | Delete | GLOBAL |
| `editor.edit.duplicate` | Ctrl+D | GLOBAL |
| `editor.view.zoomIn` | Ctrl+= | GLOBAL |
| `editor.view.zoomOut` | Ctrl+- | GLOBAL |
| `editor.view.toggleGrid` | Ctrl+G | GLOBAL |
| `editor.tool.select` | V | PANEL_FOCUSED (sceneView) |
| `editor.tool.brush` | B | PANEL_FOCUSED (sceneView) |
| `editor.tool.eraser` | E | PANEL_FOCUSED (sceneView) |
| `editor.tool.fill` | G | PANEL_FOCUSED (sceneView) |
| `editor.mode.tilemap` | 1 | GLOBAL |
| `editor.mode.entity` | 2 | GLOBAL |
| `editor.mode.collision` | 3 | GLOBAL |
| `editor.play.toggle` | Ctrl+P | GLOBAL |
| `editor.nav.panUp/Down/Left/Right` | W/S/A/D | PANEL_FOCUSED (sceneView) |
| `editor.popup.confirm` | Enter | POPUP |
| `editor.popup.cancel` | Escape | POPUP |

## Files Created

All files go in `com.pocket.rpg.editor.shortcut` package:

1. **ShortcutBinding.java** - Key + modifiers with factory methods, parsing, serialization
2. **ShortcutScope.java** - Priority enum (POPUP > PANEL_FOCUSED > PANEL_VISIBLE > GLOBAL)
3. **ShortcutAction.java** - Action definition with builder pattern
4. **ShortcutContext.java** - Frame state (popups, focused panels, text input)
5. **ShortcutRegistry.java** - Central singleton with registration, binding management, processing
6. **ShortcutConfig.java** - JSON persistence via Gson
7. **EditorShortcuts.java** - All action ID constants + registerDefaults() + bindHandlers()
8. **EditorShortcutHandlers.java** - Interface with all handler methods
9. **ShortcutsConfigTab.java** - ConfigPanel tab for rebinding UI with key capture
10. **ShortcutContextBuilder.java** - Helper for panels to report visibility/focus

## Migration Notes

After integration, remove shortcut handling from:
- `EditorMenuBar.processShortcuts()` - Delete entirely
- Individual panels with local shortcut handling - Move to EditorShortcutHandlers

The menu bar should still render menus but use `registry.getBindingDisplay()` for shortcut labels.
