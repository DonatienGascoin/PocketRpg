# Shortcut Manager System - Implementation Plan

## Overview

This plan describes the integration of a centralized shortcut management system into the PocketRpg editor. The system replaces scattered hardcoded shortcuts with a registry-based approach that supports rebinding, scopes, and conflict detection.

## Current State Analysis

### Existing Shortcut Handling

Shortcuts are currently scattered across multiple files:

| File | Location | Shortcuts | Description |
|------|----------|-----------|-------------|
| `EditorMenuBar.java` | Lines 365-425 | 8 | File/Edit global shortcuts (Ctrl+N/O/S, Ctrl+Z/Y) |
| `EditorToolController.java` | Lines 139-324 | ~25 | Mode switching (M/N/E) and tool shortcuts |
| `AnimationEditorPanel.java` | Lines 260-342 | 10 | Local animation editor shortcuts |
| `ViewportInputHandler.java` | Lines 128-144 | 6 | Camera pan (WASD), Escape |

### Problems with Current Approach

1. **No central registry** - Cannot list all shortcuts
2. **No rebinding** - Users cannot customize shortcuts
3. **No conflict detection** - Same key can be bound multiple times
4. **Inconsistent display** - Menu items show hardcoded strings
5. **Duplicate checking** - Each location manually checks `getWantTextInput()` and modifiers

---

## Proposed Architecture

```
com.pocket.rpg.editor.shortcut/
├── ShortcutBinding.java          # Key + modifiers (immutable, serializable)
├── ShortcutScope.java            # Priority enum (POPUP > PANEL_FOCUSED > PANEL_VISIBLE > GLOBAL)
├── ShortcutAction.java           # Action definition with builder
├── ShortcutContext.java          # Current frame UI state
├── ShortcutContextBuilder.java   # Helper for building context during render
├── ShortcutRegistry.java         # Central singleton registry
├── ShortcutConfig.java           # JSON persistence
├── EditorShortcuts.java          # All action IDs + registerDefaults()
├── EditorShortcutHandlers.java   # Handler interface
└── EditorShortcutHandlersImpl.java # Actual handler implementations
```

### Key Design Decisions

1. **Singleton Registry** - `ShortcutRegistry.getInstance()` provides global access
2. **Separate definition from binding** - Actions registered with placeholder handlers, then bound to real implementations
3. **Custom overrides default** - Registry stores custom bindings separately from defaults
4. **Scope priority** - Higher-priority scopes (POPUP) override lower ones (GLOBAL)
5. **Text input awareness** - Built into registry processing, not each location

---

## Implementation Phases

### Phase 1: Core Infrastructure

**Goal:** Add the core shortcut classes without modifying existing behavior.

**Files to Create:**

1. **ShortcutBinding.java**
   - Immutable key + modifiers (ctrl, shift, alt)
   - Factory methods: `key()`, `ctrl()`, `ctrlShift()`, etc.
   - `isPressed()` - checks current ImGui state
   - `toConfigString()` / `fromConfigString()` - compact serialization (e.g., "CS:S" for Ctrl+Shift+S)
   - `getDisplayString()` - human readable (e.g., "Ctrl+Shift+S")

2. **ShortcutScope.java**
   - Enum: `POPUP` (0), `PANEL_FOCUSED` (1), `PANEL_VISIBLE` (2), `GLOBAL` (3)
   - Lower priority number = higher priority
   - `higherPriorityThan()` comparison method

3. **ShortcutAction.java**
   - Builder pattern for fluent construction
   - Fields: id, displayName, category (derived from id), defaultBinding, scope, panelId, handler, allowInTextInput
   - `isApplicable(ShortcutContext)` - checks scope and text input state
   - `execute()` - runs the handler

4. **ShortcutContext.java**
   - Frame state: popupOpen, textInputActive, visiblePanels, focusedPanels
   - `current()` factory reads ImGui state
   - Builder for custom context construction

5. **ShortcutRegistry.java**
   - Singleton with `getInstance()`
   - Maps: actions by ID, custom bindings, binding-to-actions index
   - `register()` / `registerAll()` - add actions
   - `getBinding()` - returns custom or default
   - `setBinding()` / `resetToDefault()` - modify bindings
   - `processShortcuts(ShortcutContext)` - main processing loop
   - `findConflicts()` - detect overlapping bindings
   - `loadConfig()` / `saveConfig()` - persistence

6. **ShortcutConfig.java**
   - Gson-based JSON persistence
   - Custom adapter for ShortcutBinding serialization
   - Load/save to `editor/editorShortcuts.json`

**Testing:**
- Unit tests for ShortcutBinding parsing
- Unit tests for ShortcutScope priority
- Integration test for registry registration

---

### Phase 2: Action Definitions

**Goal:** Define all editor actions with their default bindings.

**Files to Create:**

1. **EditorShortcuts.java**
   - All action ID constants (e.g., `FILE_SAVE = "editor.file.save"`)
   - `PanelIds` inner class with panel identifiers
   - `registerDefaults(ShortcutRegistry)` - registers all actions with placeholder handlers

**Action Categories:**

| Category | Actions | Default Bindings |
|----------|---------|------------------|
| File | new, open, save, saveAs, configuration | Ctrl+N, Ctrl+O, Ctrl+S, Ctrl+Shift+S, - |
| Edit | undo, redo, cut, copy, paste, delete, selectAll, duplicate | Ctrl+Z, Ctrl+Shift+Z, Ctrl+X, Ctrl+C, Ctrl+V, Delete, Ctrl+A, Ctrl+D |
| View | zoomIn, zoomOut, zoomReset, fitScene, toggleGrid | Ctrl+=, Ctrl+-, Ctrl+0, Home, Ctrl+G |
| Mode | tilemap, collision, entity | M, N, E |
| Tool (Tilemap) | brush, eraser, fill, rectangle, picker | B, E, F, R, I |
| Tool (Collision) | brush, eraser, fill, rectangle, picker | C, X, G, H, V |
| Tool (Entity) | select, placer | V, P |
| Play | toggle, stop | Ctrl+P, Ctrl+Shift+P |
| Brush | sizeIncrease, sizeDecrease | +, - |
| Navigation | panUp, panDown, panLeft, panRight | W, S, A, D |

2. **EditorShortcutHandlers.java**
   - Interface with all handler method signatures
   - One method per action (e.g., `onNewScene()`, `onUndo()`, `onToolBrush()`)

---

### Phase 3: Handler Implementation

**Goal:** Implement actual handlers that wire to existing editor functionality.

**Files to Create:**

1. **EditorShortcutHandlersImpl.java**
   - Implements `EditorShortcutHandlers`
   - Dependencies: EditorContext, ConfigPanel, EditorMenuBar (for dialogs)
   - Extracts logic from EditorMenuBar and EditorToolController

**Handler Implementations:**

```java
public class EditorShortcutHandlersImpl implements EditorShortcutHandlers {
    private final EditorContext context;
    private final EditorMenuBar menuBar;
    private final EditorToolController toolController;
    private final Consumer<String> messageCallback;

    // File handlers delegate to menuBar (for unsaved changes dialog)
    @Override
    public void onNewScene() {
        menuBar.handleNewScene();
    }

    // Edit handlers
    @Override
    public void onUndo() {
        if (UndoManager.getInstance().undo()) {
            if (context.getCurrentScene() != null) {
                context.getCurrentScene().markDirty();
            }
        }
    }

    // Tool handlers
    @Override
    public void onToolBrush() {
        if (context.getModeManager().isTilemapMode()) {
            context.getToolManager().setActiveTool(toolController.getBrushTool());
            messageCallback.accept("Tile Brush");
        }
    }

    // Mode handlers
    @Override
    public void onModeTilemap() {
        context.getModeManager().switchToTilemap();
        context.getToolManager().setActiveTool(toolController.getBrushTool());
        messageCallback.accept("Switched to Tilemap Mode");
    }

    // ... etc
}
```

---

### Phase 4: Integration

**Goal:** Wire the shortcut system into the editor.

**Files to Modify:**

1. **EditorApplication.java**
   - Initialize registry in `init()` or constructor
   - Create EditorShortcutHandlersImpl after controllers are ready
   - Call `registry.processShortcuts()` at start of update loop

```java
// In init() or after controllers are created:
ShortcutRegistry registry = ShortcutRegistry.getInstance();
EditorShortcuts.registerDefaults(registry);
registry.loadConfig("editor/editorShortcuts.json");

EditorShortcutHandlersImpl handlers = new EditorShortcutHandlersImpl(
    context, menuBar, toolController, this::showStatusMessage
);
EditorShortcuts.bindHandlers(registry, handlers);

// In update():
ShortcutContext shortcutContext = ShortcutContext.current();
registry.processShortcuts(shortcutContext);
```

2. **EditorMenuBar.java**
   - Remove `processShortcuts()` method entirely
   - Update menu items to use `registry.getBindingDisplay()` for shortcut labels
   - Keep handler methods (`handleNewScene()`, etc.) as they're called by handlers impl

```java
// Before:
if (ImGui.menuItem("Save", "Ctrl+S", false, canSave)) { ... }

// After:
String saveShortcut = ShortcutRegistry.getInstance().getBindingDisplay(EditorShortcuts.FILE_SAVE);
if (ImGui.menuItem("Save", saveShortcut, false, canSave)) { ... }
```

3. **EditorToolController.java**
   - Remove `processShortcuts()` method entirely
   - Keep tool creation and settings rendering
   - Handler methods stay but are called from EditorShortcutHandlersImpl

4. **EditorUIController.java**
   - Remove call to `menuBar.processShortcuts()`

---

### Phase 5: Configuration UI

**Goal:** Add UI for viewing and rebinding shortcuts.

**Files to Create:**

1. **ShortcutsConfigTab.java**
   - Implements `ConfigTab` interface
   - Filter by category and text search
   - List all shortcuts grouped by category
   - Click to rebind (captures next key press)
   - Reset to default button per action
   - Conflict warnings
   - Pending changes applied on save

**Files to Modify:**

1. **ConfigPanel.java**
   - Add `ShortcutsConfigTab` to tabs list

```java
// In initializeTabs():
tabs.add(new ShortcutsConfigTab(context));
```

---

### Phase 6: Panel-Scoped Shortcuts

**Goal:** Support shortcuts that only work when specific panels are focused.

**Files to Create:**

1. **ShortcutContextBuilder.java**
   - Mutable builder for collecting panel state during render
   - `reportPanel(panelId)` - called by panels after `ImGui.begin()`
   - `build()` - creates immutable ShortcutContext

**Files to Modify:**

1. **SceneViewPanel.java** (or viewport renderer)
   - Report panel focus state to context builder

2. **Other panels with scoped shortcuts**
   - Report visibility/focus as needed

**Integration Approach:**

```java
// Option A: Simple (GLOBAL + POPUP only)
ShortcutContext context = ShortcutContext.current();
registry.processShortcuts(context);

// Option B: Full panel tracking
ShortcutContextBuilder builder = new ShortcutContextBuilder();
// ... render panels, each calls builder.reportPanel(...)
ShortcutContext context = builder.build();
registry.processShortcuts(context);
```

For Phase 6, start with Option A and only add panel tracking if needed.

---

### Phase 7: Keyboard Shortcuts Help Dialog

**Goal:** Add a help dialog showing all available shortcuts.

**Files to Modify:**

1. **EditorMenuBar.java** - Wire Help > Keyboard Shortcuts menu item

**Files to Create:**

1. **KeyboardShortcutsDialog.java**
   - Modal dialog listing all shortcuts
   - Grouped by category
   - Searchable
   - Shows current binding (including custom)

---

## File Changes Summary

### New Files (Phase 1-5)

| File | Phase | Description |
|------|-------|-------------|
| `shortcut/ShortcutBinding.java` | 1 | Key + modifiers |
| `shortcut/ShortcutScope.java` | 1 | Priority enum |
| `shortcut/ShortcutAction.java` | 1 | Action definition |
| `shortcut/ShortcutContext.java` | 1 | Frame state |
| `shortcut/ShortcutRegistry.java` | 1 | Central registry |
| `shortcut/ShortcutConfig.java` | 1 | JSON persistence |
| `shortcut/EditorShortcuts.java` | 2 | Action IDs + registration |
| `shortcut/EditorShortcutHandlers.java` | 2 | Handler interface |
| `shortcut/EditorShortcutHandlersImpl.java` | 3 | Handler implementations |
| `panels/config/ShortcutsConfigTab.java` | 5 | Rebinding UI |
| `shortcut/ShortcutContextBuilder.java` | 6 | Panel state builder |
| `dialogs/KeyboardShortcutsDialog.java` | 7 | Help dialog |

### Modified Files (Phase 4-7)

| File | Phase | Changes |
|------|-------|---------|
| `EditorApplication.java` | 4 | Initialize registry, process shortcuts |
| `EditorMenuBar.java` | 4, 7 | Remove processShortcuts(), use registry for labels |
| `EditorToolController.java` | 4 | Remove processShortcuts() |
| `EditorUIController.java` | 4 | Remove menuBar.processShortcuts() call |
| `ConfigPanel.java` | 5 | Add ShortcutsConfigTab |

---

## Migration Notes

### Preserving Existing Behavior

1. **AZERTY support** - Current undo uses both Z and W keys. Add both bindings or make primary + alternative system.

2. **Context-aware tools** - Tool shortcuts currently differ by mode (B = Tile Brush in tilemap mode, but no action in collision mode). This is handled by:
   - Separate actions per mode: `tool.tilemap.brush`, `tool.collision.brush`
   - Or handler checks mode internally

3. **Brush size shortcuts** - +/- for brush size should only work when a brush tool is active. Use PANEL_FOCUSED scope or check in handler.

### Testing Checklist

- [ ] All existing shortcuts still work
- [ ] Menu items show correct shortcut labels
- [ ] Shortcuts blocked when typing in text fields
- [ ] Undo/Redo work even with popups open
- [ ] Custom bindings persist across sessions
- [ ] Conflicts are detected and warned
- [ ] Reset to defaults works

---

## Implementation Order

1. **Phase 1** - Core classes (can be done independently)
2. **Phase 2** - Action definitions (depends on Phase 1)
3. **Phase 3** - Handlers (depends on Phase 2)
4. **Phase 4** - Integration (depends on all above, breaks existing shortcuts temporarily)
5. **Phase 5** - Config UI (depends on Phase 4)
6. **Phase 6** - Panel scopes (optional, depends on Phase 4)
7. **Phase 7** - Help dialog (optional, depends on Phase 4)

Phases 1-3 can be done without modifying existing code. Phase 4 is the "switchover" where old shortcut handling is removed.

---

## Appendix: Source Files

The barebone implementation files are located in:
`Documents/Plans/Shortcutmanager/ShortcutSystem/shortcut/`

These files should be moved to `src/main/java/com/pocket/rpg/editor/shortcut/` and adapted during implementation.
