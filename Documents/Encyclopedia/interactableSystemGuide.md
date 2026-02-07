# Interactable System Guide

> **Summary:** The interactable system provides a framework for creating objects the player can interact with (signs, chests, doors, etc.). It handles TriggerZone setup, tile registration, directional constraints, and editor gizmos automatically.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Built-in Interactables](#built-in-interactables)
4. [Creating a New Interactable](#creating-a-new-interactable)
5. [Directional Interaction](#directional-interaction)
6. [Gizmo Icon System](#gizmo-icon-system)
7. [Inspector Fields](#inspector-fields)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Make an object interactable | Add a Sign, Chest, or custom InteractableComponent |
| TriggerZone auto-added | Handled by `@RequiredComponent` — no manual setup needed |
| Restrict interaction direction | Enable "Directional Interaction" and set "Interact From" list |
| Change gizmo icon | Set `gizmoShape` and `gizmoColor` in subclass constructor |
| Create a new interactable type | Extend `InteractableComponent`, implement `interact()` and `getInteractionPrompt()` |

---

## Overview

The interactable system is built around the `InteractableComponent` abstract base class. It eliminates boilerplate by handling:

- **TriggerZone dependency** — Automatically added via `@RequiredComponent(TriggerZone.class)`. No manual setup needed.
- **TileEntityMap registration** — The interactable registers itself with the collision system so the `InteractionController` can detect it when the player is nearby.
- **Directional interaction** — Optional constraint on which directions the player can approach from.
- **Editor gizmos** — Each interactable type gets a unique, recognizable icon drawn on top of the TriggerZone tile area.

The player interacts using the `InteractionController`, which checks the TileEntityMap for nearby interactables, validates `canInteract()`, and calls `interact()` on the highest-priority match.

---

## Built-in Interactables

### Sign

A simple interactable that logs a message to the console.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Message | String | "Hello, world!" | Text logged when interacted |
| Prompt | String | "Read" | Verb shown in the interaction prompt |

**Gizmo:** Cyan diamond

### Chest

An openable container that tracks its open/closed state.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Contents | String | "Gold Coins" | Item description inside |
| Open | boolean | false | Whether the chest is currently open |
| Reopenable | boolean | false | Whether the chest can be opened again |

**Gizmo:** Gold square

The Chest overrides `canInteract()` to prevent interaction when already opened (unless reopenable). It has a higher priority (5) than the default (0).

---

## Creating a New Interactable

Extend `InteractableComponent` and implement two methods:

```java
@ComponentMeta(category = "Interaction")
public class Lever extends InteractableComponent {

    @Getter @Setter
    private boolean activated = false;

    public Lever() {
        gizmoShape = GizmoShape.CROSS;
        gizmoColor = GizmoColors.fromRGBA(1.0f, 0.5f, 0.0f, 0.9f); // Orange
    }

    @Override
    public void interact(GameObject player) {
        activated = !activated;
        System.out.println("[Lever] " + gameObject.getName() + " is now " + (activated ? "ON" : "OFF"));
    }

    @Override
    public String getInteractionPrompt() {
        return activated ? "Deactivate" : "Activate";
    }
}
```

That's it. The base class handles TriggerZone setup, tile registration, gizmo drawing, and directional interaction.

### Optional overrides

| Method | Default | Override when... |
|--------|---------|-----------------|
| `canInteract(GameObject player)` | Checks directional constraints | You need extra conditions (e.g., Chest checks if already opened) |
| `getInteractionPriority()` | `0` | Multiple interactables overlap and you need one to take precedence |
| `onInteractableStart()` | No-op | You need initialization after tile registration |
| `onInteractableDestroy()` | No-op | You need cleanup before tile unregistration |

---

## Directional Interaction

By default, interactables enforce directional constraints. The player must be in one of the allowed tiles relative to the object to interact.

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Directional Interaction | boolean | true | Enable/disable direction checking |
| Interact From | List\<Direction\> | [DOWN] | Allowed approach directions |

### How directions work

Directions refer to where the **player must be** relative to the interactable:

| Direction | Player position |
|-----------|----------------|
| DOWN | One tile below the object |
| UP | One tile above the object |
| LEFT | One tile to the left |
| RIGHT | One tile to the right |

Example: A sign on a wall should only be readable from below, so set `Interact From = [DOWN]`. A chest in the middle of a room might allow `[DOWN, LEFT, RIGHT]`.

### Gizmo arrows

When directional interaction is enabled, the editor draws arrows in the allowed neighbour tiles pointing toward the interactable. The arrows use the same color as the interactable's gizmo shape.

---

## Gizmo Icon System

Each interactable type has a unique icon drawn centered on its TriggerZone area. Set these in the subclass constructor:

### Available shapes

| Shape | Constant | Visual |
|-------|----------|--------|
| Diamond | `GizmoShape.DIAMOND` | Rotated square outline |
| Circle | `GizmoShape.CIRCLE` | Circle outline |
| Square | `GizmoShape.SQUARE` | Axis-aligned square outline |
| Cross | `GizmoShape.CROSS` | Crosshair |

### Color conventions

| Interactable | Color | Code |
|-------------|-------|------|
| Sign | Cyan | `GizmoColors.fromRGBA(0.4f, 0.9f, 1.0f, 0.9f)` |
| Chest | Gold | `GizmoColors.fromRGBA(1.0f, 0.8f, 0.2f, 0.9f)` |

Choose a distinct color for each new interactable type so they're easy to distinguish in the editor.

### How it renders

1. The **TriggerZone** draws the yellow tile area (handles multi-tile via width/height)
2. The **InteractableComponent** draws the icon centered on the TriggerZone area
3. If directional interaction is on, arrows are drawn in the allowed neighbour tiles

The icon uses a fixed world-space size (0.25 tiles) — it does not scale with zoom.

---

## Inspector Fields

When you add an interactable component, the inspector shows:

```
+---------------------------------------------+
| v Sign                                  [X]  |
|   Message           [Hello, world!]          |
|   Prompt            [Read]                   |
|   Directional In... [x]                      |
|   Interact From (1)                     [+]  |
|     0   [DOWN]                          [x]  |
|   --- Component References ---               |
|   * TriggerZone:    (sibling component)      |
+---------------------------------------------+
| v TriggerZone                           [-]  |
|   (greyed out remove button)                 |
|   Width             [1]                      |
|   Height            [1]                      |
|   ...                                        |
+---------------------------------------------+
```

The TriggerZone's remove button is greyed out because it is required by the Sign. Hovering shows "Required by Sign".

---

## Tips & Best Practices

- Use distinct gizmo shape + color combinations so interactable types are recognizable at a glance
- Set `directionalInteraction = false` for objects that should be interactable from any side
- Use `getInteractionPriority()` when multiple interactables can overlap (higher = takes precedence)
- The base class handles `@ComponentReference` for TriggerZone — use `getComponent(TriggerZone.class)` if you need it in gizmo code (editor context doesn't resolve `@ComponentReference`)
- Keep `interact()` lightweight — it runs on the game thread

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| TriggerZone not auto-added | Rebuild project; check console for `@RequiredComponent` validation warnings |
| Player can't interact | Check directional constraints; verify TriggerZone width/height covers the right tiles |
| Gizmo icon not centered | Ensure TriggerZone offset and size are set correctly |
| Interaction prompt not showing | Verify the component implements `getInteractionPrompt()` and returns a non-null string |
| Can't remove TriggerZone | It's required by the interactable — remove the interactable first |
| Arrows not showing | Enable "Directional Interaction" and add at least one direction to "Interact From" |

---

## Related

- [Components Guide](componentsGuide.md) — Annotations reference (`@RequiredComponent`, `@Tooltip`, etc.)
- [Collision Entities Guide](collisionEntitiesGuide.md) — TriggerZone, TileEntityMap
- [Gizmos Guide](gizmosGuide.md) — GizmoContext drawing API
- [Camera Bounds Guide](cameraBoundsGuide.md) — CameraBoundsZone used by SpawnPoint
