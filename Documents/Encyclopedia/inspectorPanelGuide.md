# Inspector Panel Guide

> **Summary:** View and edit properties of whatever is selected in the editor — entities, components, camera settings, tilemap layers, collision maps, assets, and animator states.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Inspector Contexts](#inspector-contexts)
6. [Workflows](#workflows)
7. [Tips & Best Practices](#tips--best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Edit entity name | Click the name field at the top of the entity inspector |
| Add component | Click **Add Component** button at the bottom |
| Remove component | Right-click component header → **Remove** |
| Edit a field | Click/drag the field value (type-specific editors) |
| Reset field | Right-click field label for reset options |
| Inspect an asset | Click an asset in the Asset Browser |

---

## Overview

The Inspector panel is context-sensitive — it shows different content based on what you've selected in the [Hierarchy](hierarchy-panel-guide.md) or other panels. It's the primary way to configure entities, components, and scene objects.

What the Inspector shows depends on your selection:

| Selection | Inspector shows |
|-----------|----------------|
| Entity | Name, transform, components, Add Component button |
| Multiple entities | Bulk operations |
| Scene Camera | Camera position, bounds, zoom settings |
| Tilemap Layers | Layer list with add/remove/reorder/rename |
| Collision Map | Collision map properties |
| Asset (in Asset Browser) | Asset metadata, import settings |
| Animator state/transition | State or transition properties |
| Trigger tile (collision) | Trigger configuration |

---

## Opening the Panel

The Inspector panel is open by default. If closed, reopen via **View > Inspector** in the menu bar.

---

## Interface Overview

### Entity Inspector

```
┌──────────────────────────────────┐
│ Inspector                        │
├──────────────────────────────────┤
│ Name: [Player_____________]      │
├──────────────────────────────────┤
│ ▾ Transform                      │
│   Position  X [2.0] Y [3.0]     │
│   Rotation  [0.0]               │
│   Scale     X [1.0] Y [1.0]     │
├──────────────────────────────────┤
│ ▾ SpriteRenderer                 │
│   Sprite    [player.png    ▾]   │
│   Color     [████████████]      │
│   Sort Order [0]                 │
├──────────────────────────────────┤
│ ▾ AnimatorComponent              │
│   Controller [player.animator ▾] │
│   Auto Play  [✓]                │
│   Speed      [1.0]              │
├──────────────────────────────────┤
│         [+ Add Component]        │
└──────────────────────────────────┘
```

### Camera Inspector

```
┌──────────────────────────────────┐
│ Inspector                        │
├──────────────────────────────────┤
│ 📷 Scene Camera                  │
│   Position  X [0.0] Y [0.0]     │
│   Zoom      [1.0]               │
│   Bounds    [✓] Enabled          │
│   Min X [-10] Min Y [-10]       │
│   Max X [10]  Max Y [10]        │
└──────────────────────────────────┘
```

---

## Inspector Contexts

### Entity Inspector

Shown when a single entity is selected. Displays:

- **Name field**: Editable entity name
- **Transform**: Position, rotation, scale (always present)
- **Components**: Each component in a collapsible section with its fields
- **Add Component**: Button to add new components from a searchable browser

Component fields are rendered using type-specific editors:
- **Numbers**: Drag to adjust, click to type
- **Strings**: Text input field
- **Booleans**: Checkbox
- **Enums**: Dropdown
- **Vectors**: X/Y fields side by side
- **Colors**: Color picker
- **Assets**: Path field with browse button and drag-drop support
- **Lists**: Expandable list with add/remove buttons

### Multi-Selection Inspector

Shown when multiple entities are selected. Provides:
- Count of selected entities
- Bulk delete option
- Shared property editing (when applicable)

### Camera Inspector

Shown when **Scene Camera** is selected in the Hierarchy:
- Camera position
- Zoom level
- Bounds enable/disable with min/max coordinates

### Tilemap Layers Inspector

Shown when **Tilemap Layers** is selected:
- List of all tilemap layers
- Add/remove layer buttons
- Reorder layers (drag or up/down buttons)
- Rename layers (double-click)
- Visibility toggle per layer
- Lock toggle per layer

### Collision Map Inspector

Shown when **Collision Map** is selected:
- Collision map size and properties
- Z-level configuration

### Asset Inspector

Shown when an asset is clicked in the [Asset Browser](asset-browser-guide.md):
- Asset path and type
- Import settings (e.g., sprite pivot, 9-slice borders)
- Metadata editing
- Unsaved changes warning when switching away

### Animator State/Transition Inspector

Shown when a state or transition is selected in the [Animator Editor](animator-guide.md):
- **State**: Name, type, animation paths, direction parameter, default state toggle
- **Transition**: From/to states, type, conditions list

### Trigger Inspector

Shown when a trigger tile is selected in collision editing mode:
- Trigger type and configuration
- Target scene/spawn point

---

## Workflows

### Adding a Component

1. Select an entity in the Hierarchy
2. Scroll to the bottom of the Inspector
3. Click **Add Component**
4. Search or browse the component list
5. Click a component to add it

### Removing a Component

1. Right-click the component header (e.g., "SpriteRenderer")
2. Click **Remove**
3. Confirm if prompted

### Editing Asset Fields

For fields that reference assets (sprites, animations, sounds):

1. Click the **browse button** next to the field
2. Pick an asset from the popup
3. Or **drag** an asset from the Asset Browser directly onto the field
4. Double-click "None" to clear the field

### Editing Transform

- **Drag** the X/Y value labels to adjust interactively
- **Click** a value to type an exact number
- Or use the [Move/Rotate/Scale tools](scene-view-guide.md) in the viewport

---

## Tips & Best Practices

- **All changes are undoable**: Every field edit creates an undo entry (Ctrl+Z)
- **Drag number fields**: Drag the label text left/right for quick adjustment
- **Required fields**: Fields marked with red highlight (via `@Required`) need a value
- **Component order**: Components render in the order they were added
- **Asset drag-drop**: Drag assets from the browser directly onto Inspector fields

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Inspector is empty | Select something in the Hierarchy or Asset Browser |
| "No scene loaded" | Open or create a scene via **File > Open/New** |
| Field changes not saving | Scene auto-marks as dirty — save with Ctrl+S |
| Can't find a component | Use the search bar in the Add Component popup |
| Asset field shows path but no preview | The asset file may have been moved or deleted |

---

## Related

- [Hierarchy Panel Guide](hierarchy-panel-guide.md) — Selecting items to inspect
- [Custom Inspector Guide](custom-inspector-guide.md) — Creating custom component inspectors
- [Components Guide](components-guide.md) — Available components and their properties
- [Asset Browser Guide](asset-browser-guide.md) — Browsing and selecting assets
