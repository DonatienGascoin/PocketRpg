# Keyboard Shortcuts Guide

> **Summary:** View, use, and rebind all editor keyboard shortcuts. Shortcuts are context-aware — they activate based on which panel is focused and what editing mode you're in.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Shortcuts Panel](#opening-the-shortcuts-panel)
4. [Default Shortcuts](#default-shortcuts)
5. [Rebinding Shortcuts](#rebinding-shortcuts)
6. [How Scopes Work](#how-scopes-work)
7. [Keyboard Layouts](#keyboard-layouts)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Adding Shortcuts (Developers)](#adding-shortcuts-developers)
11. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| View all shortcuts | **Edit > Shortcuts** |
| Rebind a shortcut | Click the binding in the Shortcuts panel, press new key |
| Reset one shortcut | Click **Reset** next to the binding |
| Reset all shortcuts | Click **Reset All** in the Shortcuts panel |
| Switch keyboard layout | Change layout in `gameData/config/shortcuts.json` |

---

## Overview

The editor uses a centralized shortcut system where all keyboard shortcuts are registered, processed, and configurable from one place. Shortcuts are **scope-aware** — the same key can do different things depending on which panel is focused.

Key concepts:
- **Global shortcuts** (Ctrl+S, Ctrl+Z) work everywhere
- **Panel-focused shortcuts** (Space in Animation Editor) only work when that panel is focused
- **Context-sensitive tools** (B for Brush) adapt based on whether a tilemap layer or collision layer is selected
- All shortcuts are **rebindable** and persist across sessions

---

## Opening the Shortcuts Panel

**Edit > Shortcuts** from the menu bar.

---

## Default Shortcuts

### File

| Shortcut | Action |
|----------|--------|
| Ctrl+N | New Scene |
| Ctrl+O | Open Scene |
| Ctrl+S | Save Scene |
| Ctrl+Shift+S | Save Scene As |

### Edit

| Shortcut | Action |
|----------|--------|
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| Ctrl+Y | Redo (alternative) |
| Ctrl+X | Cut |
| Ctrl+C | Copy |
| Ctrl+V | Paste |
| Delete | Delete |
| Ctrl+A | Select All |
| Ctrl+D | Duplicate |

### View

| Shortcut | Action |
|----------|--------|
| Ctrl+= | Zoom In |
| Ctrl+- | Zoom Out |
| Ctrl+0 | Reset Zoom |
| Ctrl+G | Toggle Grid |

### Panels

| Shortcut | Action |
|----------|--------|
| F1 | Toggle Tileset Palette |
| F2 | Toggle Collision Panel |

### Scene View (when focused)

| Shortcut | Action |
|----------|--------|
| W / Up Arrow | Pan camera up |
| A / Left Arrow | Pan camera left |
| S / Down Arrow | Pan camera down |
| D / Right Arrow | Pan camera right |
| Middle Mouse Drag | Pan camera |
| Scroll Wheel | Zoom |

### Tilemap Tools (when tilemap layer selected)

| Shortcut | Action |
|----------|--------|
| B | Tile Brush |
| E | Tile Eraser |
| F | Tile Fill |
| R | Tile Rectangle |
| I | Tile Picker |
| = | Increase brush size |
| - | Decrease brush size |

### Collision Tools (when collision layer selected)

| Shortcut | Action |
|----------|--------|
| C | Collision Brush |
| X | Collision Eraser |
| G | Collision Fill |
| H | Collision Rectangle |
| V | Collision Picker |
| ] | Increase Z-Level |
| [ | Decrease Z-Level |
| = | Increase brush size |
| - | Decrease brush size |

### Transform Tools (entity mode)

| Shortcut | Action |
|----------|--------|
| V | Selection Tool |
| W | Move Tool |
| E | Rotate Tool |
| R | Scale Tool |
| Escape | Cancel / Deselect |
| Delete | Delete Entity |

### Play Mode

| Shortcut | Action |
|----------|--------|
| Ctrl+P | Play / Pause |
| Ctrl+Shift+P | Stop |

### Animation Editor (when focused)

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save animation |
| Ctrl+N | New animation |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| Ctrl+Y | Redo (alternative) |
| F5 | Refresh animation list |
| Space | Play / Pause preview |
| Left Arrow | Previous frame |
| Right Arrow | Next frame |
| Home | First frame |
| End | Last frame |
| Delete | Delete frame |

### Animator Editor (when focused)

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save animator |
| Ctrl+N | New animator |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| F5 | Refresh |

### Configuration Panel (when focused)

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save configuration |

### Tileset Palette (when focused)

| Shortcut | Action |
|----------|--------|
| Escape | Clear tile selection |

---

## Rebinding Shortcuts

1. Open **Edit > Shortcuts**
2. Find the shortcut you want to change
3. Click the current binding (e.g., "Ctrl+S")
4. Press the new key combination
5. The binding updates immediately and is saved automatically

To revert a single shortcut to its default, click **Reset** next to it.

To revert all shortcuts, click **Reset All**.

---

## How Scopes Work

Shortcuts have scopes that determine when they activate:

| Scope | When it fires |
|-------|---------------|
| **Global** | Always (unless typing in a text field or popup is open) |
| **Panel Focused** | Only when the specified panel has focus |
| **Panel Visible** | When the specified panel is visible (even if not focused) |
| **Popup** | Only when a popup is open |

When multiple shortcuts share the same key, the most specific scope wins. For example, pressing **S** when the Scene View is focused activates "Camera Pan Down" (panel-scoped), not a global action.

Modifier shortcuts (Ctrl+S) are always checked before plain keys (S), so Ctrl+S will never accidentally trigger camera panning.

---

## Keyboard Layouts

The editor supports QWERTY and AZERTY keyboard layouts. The layout affects default bindings for Undo/Redo:

| Action | QWERTY | AZERTY |
|--------|--------|--------|
| Undo | Ctrl+Z | Ctrl+W |
| Redo | Ctrl+Shift+Z | Ctrl+Shift+W |

The layout is configured in `gameData/config/shortcuts.json` under the `keyboardLayout` field. Change it to `"AZERTY"` or `"QWERTY"`.

---

## Tips & Best Practices

- **Focus matters**: Click a panel before using its shortcuts. The title bar highlights when a panel is focused.
- **Tool keys adapt**: B, E, F, R switch between tilemap tools or transform tools depending on what's selected in the Hierarchy.
- **Escape is context-sensitive**: Clears tile selection in the palette, deselects entities globally.
- **Shortcuts are suppressed during play mode**: The game owns the keyboard while playing. Stop play mode to use editor shortcuts.
- **Modifier key safety**: Releasing Ctrl after Ctrl+S won't trigger the plain S shortcut (camera pan).

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Shortcut not working | Check if the correct panel is focused (click its title bar) |
| Wrong tool activates | Check Hierarchy selection — tool shortcuts depend on whether a tilemap, collision, or entity layer is active |
| Shortcut works but shouldn't | The scope may be too broad — check Edit > Shortcuts for conflicts |
| WASD pans camera when typing | Click outside text fields before using keyboard shortcuts |
| Shortcuts don't work in play mode | Expected — stop play mode first (Ctrl+Shift+P) |
| Changed binding not saving | Check that `gameData/config/shortcuts.json` is writable |

---

## Adding Shortcuts (Developers)

Panels can define their own shortcuts by overriding `provideShortcuts()`:

```java
@Override
public List<ShortcutAction> provideShortcuts(KeyboardLayout layout) {
    return List.of(
        panelShortcut()
            .id("editor.myPanel.doThing")
            .displayName("Do Thing")
            .defaultBinding(ShortcutBinding.key(ImGuiKey.Space))
            .handler(this::doThing)
            .build()
    );
}
```

Panel shortcuts are automatically registered during initialization, included in the config file, and shown in the Shortcuts settings panel.

For global shortcuts, add them to `EditorShortcuts.java` and bind handlers via `EditorShortcutHandlers`.

See `.claude/reference/architecture.md` — Shortcut System section for full architecture details.

---

## Related

- [Animation Editor Guide](animation-editor-guide.md) — Animation-specific shortcuts
- [Collision Map Guide](collision-map-guide.md) — Collision tool shortcuts
