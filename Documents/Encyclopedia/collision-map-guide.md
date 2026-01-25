# Collision Map Guide

> **Summary:** The collision map defines terrain-based movement rules using a tile grid. Paint collision types to create walls, ledges, water, ice, and elevation changes without needing entity objects.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Opening the Panel](#opening-the-panel)
4. [Interface Overview](#interface-overview)
5. [Collision Types](#collision-types)
6. [Workflows](#workflows)
7. [Keyboard Shortcuts](#keyboard-shortcuts)
8. [Tips & Best Practices](#tips--best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Code Integration](#code-integration)
11. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Paint collision tiles | Select type in palette > Click/drag in scene |
| Erase collision tiles | Select "None" type > Paint over tiles |
| Change elevation | Use elevation dropdown in Collision Panel |
| Configure stairs | Paint STAIRS tile > Configure in Inspector |
| Toggle overlay | View menu > Show Collision Overlay |

---

## Overview

The collision map is a tile-based system that defines how entities move through the world. Each tile can have a collision type that affects movement:

- **Blocking**: Walls that stop movement
- **Directional**: Ledges that allow one-way jumps
- **Terrain effects**: Ice (sliding), sand (slow), water (requires swimming)
- **Elevation**: Stairs that change the player's floor level

The collision map is **static terrain data** - it doesn't change at runtime. For dynamic blocking (doors, NPCs, chests), use entity-based collision instead.

### When to Use Collision Map

| Use Collision Map For | Use Entities For |
|-----------------------|------------------|
| Walls, cliffs | Doors (open/close) |
| Water, lava | NPCs (move around) |
| Ledges | Chests, pots |
| Ice, sand | Warps, spawn points |
| Stairs (elevation) | Any interactive object |

---

## Opening the Panel

1. Open a scene in the editor
2. Go to **View > Collision Panel** (or press `C`)
3. The Collision Panel appears with the type palette

To see collision tiles in the scene:
- **View > Show Collision Overlay** toggles the colored overlay
- Each collision type has a distinct color

---

## Interface Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│ Collision Panel                                                      │
├─────────────────────────────────────────────────────────────────────┤
│ Elevation: [Ground ▼]                                               │
├──────────────────────┬──────────────────────┬───────────────────────┤
│ MOVEMENT             │ LEDGES               │ TRIGGERS              │
│ ┌──────────────────┐ │ ┌──────────────────┐ │ ┌───────────────────┐ │
│ │ ○ None           │ │ │ ○ Ledge ↓        │ │ │ Triggers (2)      │ │
│ │ ● Solid          │ │ │ ○ Ledge ↑        │ │ │ • Stairs (5,3)    │ │
│ └──────────────────┘ │ │ ○ Ledge ←        │ │ │   ⚠ Not configured│ │
│                      │ │ ○ Ledge →        │ │ └───────────────────┘ │
│ TERRAIN              │ └──────────────────┘ │                       │
│ ┌──────────────────┐ │                      │                       │
│ │ ○ Water          │ │ ELEVATION            │                       │
│ │ ○ Tall Grass     │ │ ┌──────────────────┐ │                       │
│ │ ○ Ice            │ │ │ ○ Stairs         │ │                       │
│ │ ○ Sand           │ │ └──────────────────┘ │                       │
│ └──────────────────┘ │                      │                       │
└──────────────────────┴──────────────────────┴───────────────────────┘
```

### Panel Sections

| Section | Purpose |
|---------|---------|
| **Elevation** | Switch between floor levels (Ground, Floor 1, etc.) |
| **Movement** | Basic walkability (None = walkable, Solid = blocked) |
| **Terrain** | Special movement effects |
| **Ledges** | One-way jump tiles |
| **Elevation** | Floor-changing tiles (Stairs) |
| **Triggers** | List of trigger tiles needing configuration |

---

## Collision Types

### Movement Category

| Type | Color | Behavior |
|------|-------|----------|
| **None** | Transparent | Fully walkable, no collision |
| **Solid** | Red | Blocks all movement |

### Terrain Category

| Type | Color | Behavior |
|------|-------|----------|
| **Water** | Blue | Blocks unless entity can swim |
| **Tall Grass** | Green | Walkable, triggers random encounters |
| **Ice** | Light blue | Entity slides until hitting non-ice |
| **Sand** | Tan | Slows movement speed |

### Ledge Category

Ledges allow one-way movement. The arrow shows the direction you CAN jump.

| Type | Direction | Example Use |
|------|-----------|-------------|
| **Ledge ↓** | Jump down (south) | Cliffs, drop-downs |
| **Ledge ↑** | Jump up (north) | Rare, special areas |
| **Ledge ←** | Jump left (west) | Side ledges |
| **Ledge →** | Jump right (east) | Side ledges |

Elevated ledges (purple tint) also change elevation when jumped.

### Elevation Category

| Type | Behavior |
|------|----------|
| **Stairs** | Changes elevation based on exit direction. Requires configuration in Inspector. |

---

## Workflows

### Painting Collision Tiles

1. Open the Collision Panel
2. Select a collision type from the palette
3. Click or drag in the scene view to paint
4. Tiles show colored overlay when collision view is enabled

### Creating a Wall

1. Select **Solid** from the Movement category
2. Paint tiles where walls should be
3. Entities cannot walk through these tiles

### Creating Ledges (Pokemon-Style)

Ledges let the player jump in one direction but not climb back.

1. Select the ledge direction (e.g., **Ledge ↓**)
2. Paint a row of ledge tiles
3. Player can jump DOWN over these tiles
4. Player cannot climb UP from below

```
  [Grass] [Grass] [Grass]     ← Player here can jump down
  [Ledge↓][Ledge↓][Ledge↓]    ← Ledge row
  [Grass] [Grass] [Grass]     ← Player here cannot climb up
```

### Creating Stairs (Elevation Change)

Stairs change the player's floor level when exited in a specific direction.

1. Select **Stairs** from Elevation category
2. Paint the stairs tile
3. Click the tile to select it (appears in Inspector)
4. Configure in Inspector:
   - **Exit Direction**: Which way player exits to trigger change
   - **Destination**: Target elevation level

Example: Stairs going up when exiting north:
- Exit Direction: UP
- Destination: Floor 1

### Working with Multiple Elevations

1. Use the **Elevation** dropdown to switch floors
2. Each floor has its own collision layer
3. Stairs connect different elevations
4. Entities only collide with tiles on their current elevation

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| C | Toggle Collision Panel |
| V | Toggle collision overlay visibility |
| 1-9 | Quick-select collision type (when panel focused) |
| Escape | Deselect current tool |

---

## Tips & Best Practices

- **Use None to erase**: Select "None" and paint to remove collision tiles
- **Match visuals**: Collision tiles should align with your tilemap visuals
- **Test ledges**: Walk around ledges in play mode to verify direction
- **Configure stairs immediately**: Unconfigured stairs show warnings in the Triggers list
- **One elevation at a time**: Work on one floor level before moving to the next

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Player walks through walls | Check elevation - player and wall must be on same level |
| Stairs don't work | Configure exit direction and destination in Inspector |
| Collision not visible | Enable View > Show Collision Overlay |
| Ice doesn't slide | Verify GridMovement component handles ICE behavior |
| Ledge blocks both ways | Check you painted the correct direction ledge |

---

## Code Integration

### Querying Collision at Runtime

```java
CollisionMap collisionMap = scene.getCollisionMap();

// Get collision type at position
CollisionType type = collisionMap.get(x, y, elevation);

if (type == CollisionType.SOLID) {
    // Tile is blocked
}

if (type.isLedge()) {
    Direction ledgeDir = type.getLedgeDirection();
    // Can only cross in this direction
}
```

### Using CollisionSystem

The `CollisionSystem` combines collision map with entity blocking:

```java
CollisionSystem collision = scene.getCollisionSystem();

// Check if movement is allowed
MoveResult result = collision.canMove(entity, fromX, fromY, toX, toY, elevation);

if (result.isBlocked()) {
    // Movement not allowed
    if (result.isBlockedByTile()) {
        CollisionType blocker = result.getBlockingType();
    }
}
```

### Handling Terrain Effects

```java
// In GridMovement or custom movement component
CollisionType terrain = collisionMap.get(currentX, currentY, elevation);

switch (terrain) {
    case ICE -> startSliding(currentDirection);
    case SAND -> applySpeedMultiplier(0.5f);
    case TALL_GRASS -> checkRandomEncounter();
    case WATER -> {
        if (!canSwim) blockMovement();
    }
}
```

---

## Related

- [Collision Entities Guide](collision-entities-guide.md) - Entity-based blocking and triggers
- [Components Guide](components-guide.md) - Creating custom components
- [Gizmos Guide](gizmos-guide.md) - Custom editor visualization
