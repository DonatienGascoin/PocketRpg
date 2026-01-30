# Collision Entities Guide

> **Summary:** Entity-based collision uses components to create interactive objects like doors, warps, and spawn points. Unlike the static collision map, entities can have visuals, animations, and dynamic behavior.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Entity vs Collision Map](#entity-vs-collision-map)
4. [Core Components](#core-components)
5. [Blocking Components](#blocking-components)
6. [Trigger Components](#trigger-components)
7. [Interaction Components](#interaction-components)
8. [Workflows](#workflows)
9. [Tips & Best Practices](#tips--best-practices)
10. [Troubleshooting](#troubleshooting)
11. [Code Integration](#code-integration)
12. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create blocking object | Add entity > Add `StaticOccupant` component |
| Create warp zone | Add entity > Add `TriggerZone` + `WarpZone` components |
| Create door | Add entity > Add `Door` component (includes blocking) |
| Create spawn point | Add entity > Add `SpawnPoint` component |
| Make object interactable | Implement `Interactable` interface on component |

---

## Overview

Entity-based collision handles everything the static collision map cannot:

- **Dynamic objects**: Doors that open/close, NPCs that move
- **Visual feedback**: Sprites, animations, particle effects
- **Audio**: Sound effects when interacting
- **Complex logic**: Locked doors, multi-step puzzles
- **Triggers**: Warps, cutscene triggers, damage zones

The system uses the `TileEntityMap` to track which components occupy which tiles. This integrates with the `CollisionSystem` to provide unified collision checking.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        COLLISION SYSTEM                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   CollisionMap (static)              TileEntityMap (dynamic)        │
│   ┌─────────────────────┐           ┌─────────────────────────────┐ │
│   │ Walls, water, ledges│           │ Doors, NPCs, chests         │ │
│   │ Ice, sand, stairs   │     +     │ Warps, spawn points         │ │
│   │ Painted in editor   │           │ Any entity component        │ │
│   └─────────────────────┘           └─────────────────────────────┘ │
│              │                                  │                    │
│              └──────────────┬───────────────────┘                    │
│                             ▼                                        │
│              CollisionSystem.canMove()                               │
│              (checks both systems)                                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Entity vs Collision Map

| Aspect | Collision Map | Entity |
|--------|---------------|--------|
| **Visual** | None (overlay only) | SpriteRenderer, Animator |
| **Audio** | None | AudioSource component |
| **Animation** | None | Full animation support |
| **State changes** | Static | Open/close, activate, etc. |
| **Runtime changes** | Never changes | Can enable/disable blocking |
| **Editing** | Paint tiles | Place entity, use Inspector |
| **Reusability** | Copy/paste | Prefabs |
| **Position** | Grid-locked | Grid or free positioning |

### When to Use Each

| Object Type | Use |
|-------------|-----|
| Walls, cliffs | Collision Map (SOLID) |
| Water, lava | Collision Map (WATER) |
| Ledges | Collision Map (LEDGE_*) |
| Ice patches | Collision Map (ICE) |
| Stairs | Collision Map (STAIRS) |
| Doors | Entity (Door component) |
| NPCs | Entity (GridMovement) |
| Chests, pots | Entity (StaticOccupant) |
| Warp zones | Entity (TriggerZone + WarpZone) |
| Spawn points | Entity (SpawnPoint) |
| Levers, switches | Entity (custom component) |

---

## Core Components

### TileEntityMap

The central registry tracking all entity components per tile. Components register on start and unregister on destroy.

**Used by:**
- `StaticOccupant` - blocking for static objects
- `GridMovement` - blocking for moving entities
- `TriggerZone` - non-blocking trigger detection

### How Registration Works

```
Entity Start:
  Component.onStart() → TileEntityMap.register(component, tile)

Entity Move:
  GridMovement.move() → TileEntityMap.move(component, from, to)

Entity Destroy:
  Component.onDestroy() → TileEntityMap.unregister(component, tile)
```

---

## Blocking Components

### StaticOccupant

Registers static objects (chests, pots, signs) with the TileEntityMap so they block movement.

**Inspector Fields:**

| Field | Type | Description |
|-------|------|-------------|
| Occupied Tiles | List<TileCoord> | Tiles this object blocks |
| Tiles Relative | boolean | If true, tiles are relative to entity position |

**Usage:**
```
┌─────────────────────────────────────┐
│ ▼ StaticOccupant                    │
│   Occupied Tiles (1)            [+] │
│     0   (0, 0, 0)               [x] │
│   Tiles Relative   [✓]             │
└─────────────────────────────────────┘
```

For multi-tile objects (tables, large statues), add multiple tiles:
```
Occupied Tiles (2)                [+]
  0   (0, 0, 0)                   [x]  ← Base tile
  1   (1, 0, 0)                   [x]  ← Tile to the right
```

### GridMovement

For entities that move (player, NPCs). Automatically registers with TileEntityMap and updates position as entity moves.

When an entity with GridMovement occupies a tile, other entities cannot enter that tile.

---

## Trigger Components

### TriggerZone

Non-blocking trigger that fires events when entities enter its tiles. Does NOT block movement.

**Inspector Fields:**

| Field | Type | Description |
|-------|------|-------------|
| Trigger Tiles | List<TileCoord> | Tiles that activate this trigger |
| Tiles Relative | boolean | If true, tiles are relative to entity position |
| One Shot | boolean | If true, only triggers once |
| Player Only | boolean | If true, only player triggers it |

**Usage:**

TriggerZone alone does nothing - pair it with a listener component:

```
Entity: Warp_To_Town
├─ TriggerZone (defines WHERE)
│    Trigger Tiles: [(0,0,0)]
│    Player Only: true
│
└─ WarpZone (defines WHAT HAPPENS)
     Target Scene: "town"
     Target Spawn: "entrance"
```

### WarpZone

Teleports entities to another location when triggered. Requires TriggerZone to detect entry.

**Inspector Fields:**

| Field | Type | Description |
|-------|------|-------------|
| Target Scene | String | Scene to load (empty = same scene) |
| Target Spawn ID | String | SpawnPoint ID to teleport to |

**Validation:**
- Red background if Target Spawn ID is empty or invalid
- Yellow warning text explains the issue

### SpawnPoint

Marks a position where entities can spawn or teleport to. Does NOT block or trigger - just a marker.

**Inspector Fields:**

| Field | Type | Description |
|-------|------|-------------|
| Spawn ID | String | Unique identifier for this spawn point |

**Usage:**
- WarpZone references SpawnPoint by ID
- Scene loading can specify initial spawn
- Multiple spawn points per scene allowed

---

## Interaction Components

### Interactable Interface

Components implementing `Interactable` can be activated by the player pressing the interact button.

```java
public interface Interactable {
    void interact(GameObject actor);
    boolean canInteract(GameObject actor);
    float getInteractionRadius();
    Vector3f getInteractionPoint();
    String getInteractionPrompt();
}
```

### InteractionController

Attach to player to enable interaction with nearby Interactables.

**How it works:**
1. Scans for Interactable components within range
2. Shows interaction prompt for nearest valid target
3. On interact input, calls `target.interact(player)`

### Door

Full-featured door component with visuals, audio, and optional teleportation.

**Inspector Fields:**

| Field | Type | Description |
|-------|------|-------------|
| Locked | boolean | Requires key to open |
| Required Key | String | Item ID needed to unlock |
| Consume Key | boolean | Remove key after use |
| Open Sprite | Sprite | Visual when open |
| Closed Sprite | Sprite | Visual when closed |
| Open Sound | AudioClip | Sound when opening |
| Locked Sound | AudioClip | Sound when locked |
| Target Scene | String | Optional: teleport destination scene |
| Target Spawn ID | String | Optional: spawn point in target scene |

**Behavior:**
- Closed door blocks movement (manages its own StaticOccupant)
- Open door allows passage
- Can optionally teleport player when entered while open

---

## Workflows

### Creating a Blocking Object (Chest, Pot)

1. Create empty entity
2. Add `SpriteRenderer` - set visual
3. Add `StaticOccupant` - blocks the tile
4. Position entity in scene

```
Entity: Chest_01
├─ Transform: (5, 3, 0)
├─ SpriteRenderer: chest.sprite
└─ StaticOccupant
     Occupied Tiles: [(0, 0, 0)]
     Tiles Relative: true
```

### Creating a Warp Zone

1. Create empty entity at warp location
2. Add `TriggerZone` - defines trigger area
3. Add `WarpZone` - defines destination
4. Configure target scene and spawn ID

```
Entity: Warp_To_Dungeon
├─ Transform: (10, 5, 0)
├─ TriggerZone
│    Trigger Tiles: [(0, 0, 0)]
│    Player Only: true
│    One Shot: false
│
└─ WarpZone
     Target Scene: "dungeon_01"
     Target Spawn ID: "entrance"
```

### Creating a Spawn Point

1. Create empty entity
2. Add `SpawnPoint` component
3. Set unique Spawn ID
4. Position where player should appear

```
Entity: Spawn_Entrance
├─ Transform: (2, 8, 0)
└─ SpawnPoint
     Spawn ID: "entrance"
```

### Creating a Door

1. Create entity with sprite
2. Add `Door` component
3. Configure open/closed sprites
4. Optional: set lock and key requirement
5. Optional: set teleport destination

```
Entity: Door_Locked
├─ Transform: (7, 4, 0)
├─ SpriteRenderer: door_closed.sprite
├─ AudioSource
└─ Door
     Locked: true
     Required Key: "dungeon_key"
     Open Sprite: door_open.sprite
     Closed Sprite: door_closed.sprite
     Open Sound: door_open.wav
     Locked Sound: door_locked.wav
```

### Creating a Multi-Tile Trigger

For triggers larger than one tile (boss room entrance, large warp):

1. Add TriggerZone
2. Add multiple tiles to Trigger Tiles list:

```
TriggerZone
  Trigger Tiles (3)               [+]
    0   (0, 0, 0)                 [x]
    1   (1, 0, 0)                 [x]
    2   (2, 0, 0)                 [x]
  Tiles Relative: true
```

---

## Tips & Best Practices

- **Use prefabs**: Create prefab for common objects (locked door, chest, etc.)
- **Unique spawn IDs**: Each SpawnPoint needs a unique ID within its scene
- **Test warps**: Verify spawn points exist before referencing them
- **Combine components**: TriggerZone + WarpZone, StaticOccupant + custom behavior
- **Layer sprites correctly**: Set Z-index so entities render at correct depth

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Entity doesn't block movement | Add `StaticOccupant` component |
| Warp shows red/warning | Check Target Spawn ID exists in target scene |
| Door won't open | Check if player has required key item |
| Trigger fires multiple times | Enable "One Shot" on TriggerZone |
| Player walks through closed door | Verify Door component is properly initialized |
| Spawn point not found | Ensure SpawnPoint has non-empty Spawn ID |

---

## Code Integration

### Creating a Custom Interactable

```java
@ComponentMeta(category = "Interaction")
public class Lever extends Component implements Interactable {
    private boolean activated = false;
    private Sprite onSprite;
    private Sprite offSprite;

    @Override
    public void interact(GameObject actor) {
        activated = !activated;
        updateVisual();
        // Trigger connected objects...
    }

    @Override
    public boolean canInteract(GameObject actor) {
        return true; // Always interactable
    }

    @Override
    public String getInteractionPrompt() {
        return activated ? "Turn Off" : "Turn On";
    }
}
```

### Creating a Custom Trigger Listener

```java
public class CutsceneTrigger extends Component implements TriggerListener {
    private String cutsceneId;

    @Override
    public void onTriggerEnter(GameObject entity, TriggerZone trigger) {
        CutsceneManager.play(cutsceneId);
    }
}

// Usage:
// Entity with TriggerZone + CutsceneTrigger
```

### Querying Entities at a Tile

```java
TileEntityMap entityMap = scene.getCollisionSystem().getTileEntityMap();
TileCoord tile = new TileCoord(5, 3, 0);

// Get all components at tile
Set<Component> components = entityMap.getAll(tile);

// Get specific component type
List<Door> doors = entityMap.get(tile, Door.class);

// Check if blocked
boolean blocked = entityMap.isBlocked(tile, mover);
```

### Finding Spawn Points

```java
// In Scene class
TileCoord spawn = scene.findSpawnPoint("entrance");
if (spawn != null) {
    player.teleportTo(spawn.x(), spawn.y(), spawn.elevation());
}
```

---

## Related

- [Collision Map Guide](collision-map-guide.md) - Terrain-based collision
- [Components Guide](components-guide.md) - Creating custom components
- [Custom Inspector Guide](custom-inspector-guide.md) - Custom component UI
- [Gizmos Guide](gizmos-guide.md) - Editor visualization for components
