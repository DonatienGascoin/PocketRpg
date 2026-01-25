# Interactable System Implementation Plan

## Overview

This plan introduces a component-based interaction system for doors, chests, NPCs, levers, and other interactive objects. It also addresses the relationship between collision map tiles and interactive entities.

**Problem**: The current DOOR trigger in the collision map is too limited for real interactive objects that need visuals, animations, sounds, and complex state.

**Solution**:
1. Entity-based Interactable system with full component support
2. Keep collision map for STAIRS only (complex elevation logic, ON_EXIT activation)
3. All other triggers (WARP, DOOR, SPAWN_POINT) become entities

---

## Plan Documents

| Document | Purpose |
|----------|---------|
| [00-overview.md](00-overview.md) | This file - architecture overview |
| [01-interactable-core.md](01-interactable-core.md) | Interactable interface, InteractionController, StaticOccupant |
| [02-door-component.md](02-door-component.md) | Door implementation with all features |
| [03-chest-and-others.md](03-chest-and-others.md) | Chest, Lever, Sign, SecretPassage components |
| [04-inventory-system.md](04-inventory-system.md) | Basic inventory for key items |
| [05-collision-map-runtime.md](05-collision-map-runtime.md) | Collision map is terrain only - no runtime modification |
| [06-editor-integration.md](06-editor-integration.md) | Prefabs, inspector, workflow |
| [07-migration-guide.md](07-migration-guide.md) | Migrating from collision-map triggers to entities |

---

## Comparison: Three Approaches

There are three ways to implement interactive objects:

| Approach | Description |
|----------|-------------|
| **A: Collision Map Trigger** | Invisible tile in collision map + TriggerData |
| **B: Dynamic Tilemap Tile** | Visible tile in tilemap that changes at runtime |
| **C: GameObject Entity** | Full entity with components |

---

### Approach A: Collision Map Trigger (Current DOOR)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         TILEMAP LAYER                                │
│  ┌─────┬─────┬─────┬─────┬─────┐                                    │
│  │     │     │ 🚪  │     │     │  ← Door tile (visual only)         │
│  └─────┴─────┴─────┴─────┴─────┘                                    │
└─────────────────────────────────────────────────────────────────────┘
                              ↕ (must stay in sync - fragile!)
┌─────────────────────────────────────────────────────────────────────┐
│                       COLLISION MAP                                  │
│  ┌─────┬─────┬─────┬─────┬─────┐                                    │
│  │SOLID│SOLID│DOOR │SOLID│SOLID│  ← Door collision type             │
│  └─────┴─────┴─────┴─────┴─────┘                                    │
│                  │                                                   │
│                  ▼                                                   │
│         DoorTriggerData {                                           │
│           locked: true,                                              │
│           requiredKey: "dungeon_key"                                │
│         }                                                            │
└─────────────────────────────────────────────────────────────────────┘
```

**Pros**:
- Lightweight (no GameObject overhead)
- Simple for basic cases
- Integrated with tile painting workflow

**Cons**:
- No visual state (can't show open/closed door sprite)
- No animation support
- No audio support
- Fragile coupling between tilemap visual and collision data
- Hard to edit (must keep two layers in sync)
- No prefab/reusability
- Limited to grid positions

---

### Approach B: Dynamic Tilemap Tile

```
┌─────────────────────────────────────────────────────────────────────┐
│                         TILEMAP LAYER                                │
│  ┌─────┬─────┬─────┬─────┬─────┐                                    │
│  │     │     │ 🚪  │     │     │  ← Door tile (tileId = 42)         │
│  └─────┴─────┴─────┴─────┴─────┘                                    │
│                  │                                                   │
│                  ▼ (on interact)                                     │
│            tilemap.setTile(x, y, 43)  ← Swap to open door tile      │
│                                                                      │
│  ┌─────┬─────┬─────┬─────┬─────┐                                    │
│  │     │     │ ▯▯  │     │     │  ← Open door tile (tileId = 43)    │
│  └─────┴─────┴─────┴─────┴─────┘                                    │
└─────────────────────────────────────────────────────────────────────┘
```

**How it works**:
1. Tilemap contains door tile at position (x, y)
2. Some system detects interaction at that tile
3. Tilemap tile is swapped to different tile ID
4. Collision map is updated separately

**Pros**:
- Lightweight (no GameObject overhead)
- Works with existing tilemap workflow
- Tile-based aesthetic consistency
- Can use tileset animations

**Cons**:
- **Interaction detection is complex**: Need separate system to detect "interact with tile"
- **State management scattered**: Tile visual in tilemap, collision in collision map, logic in... where?
- **No component system**: Can't attach AudioSource, Animator, custom scripts
- **Configuration storage**: Where does lock/key/destination data live?
- **Limited to grid**: Can't position between tiles
- **Coupling**: Tileset must include all state variations (closed/open/locked)
- **Audio**: No natural place to play sounds
- **Animation**: Limited to tileset's built-in tile animations

**When it works**:
- Very simple toggles (lever on/off)
- Purely visual changes (decorative)
- When door is part of environment art, not a distinct object

**Example: Simple Lever**
```java
// Lever tile that toggles visual
public void interactWithTile(int x, int y) {
    int currentTile = tilemap.getTile(x, y);
    if (currentTile == LEVER_OFF_TILE) {
        tilemap.setTile(x, y, LEVER_ON_TILE);
        triggerConnectedDoor();
    }
}
```

**Problem: Where's the logic?**
```
Q: How do you know tile 42 is a door?
A: Hard-coded tile ID check? Tile metadata in tileset?

Q: Where's the key requirement stored?
A: Separate data structure keyed by position?

Q: How do you play a sound?
A: Global audio manager? Scene-level audio?

Q: How do you know what spawn point to teleport to?
A: More metadata somewhere?
```

This quickly becomes the same fragile sync problem as Approach A.

---

### Approach C: GameObject Entity (Proposed)

```
┌─────────────────────────────────────────────────────────────────────┐
│                      SCENE HIERARCHY                                 │
│                                                                      │
│  └─ Entities                                                        │
│      └─ Door_Dungeon_01                                             │
│          ├─ Transform: (5, 3, 0)                                    │
│          ├─ SpriteRenderer: door_closed.png                         │
│          ├─ Animator: door_animations                               │
│          ├─ AudioSource                                             │
│          ├─ BoxCollider: blocks when closed                         │
│          └─ Door: {                                                 │
│                locked: true,                                         │
│                requiredKey: "dungeon_key",                          │
│                openSprite: door_open.png,                           │
│                closedSprite: door_closed.png,                       │
│                openSound: door_open.wav,                            │
│                lockedSound: door_locked.wav                         │
│              }                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

**Pros**:
- Full visual control (sprites, animations)
- Audio support via AudioSource
- Complex state management
- Works with existing Inspector
- Prefab support (create once, reuse everywhere)
- Can be positioned freely (not grid-locked)
- Self-contained (no sync issues)
- Easy to extend with new behaviors

**Cons**:
- More overhead than simple tile
- Requires entity placement (vs tile painting)
- More complex for very simple cases

---

### Comparison Table

| Aspect | A: Collision Map | B: Dynamic Tilemap | C: GameObject Entity |
|--------|-----------------|-------------------|---------------------|
| **Visual** | None (invisible) | Tileset tiles | SpriteRenderer |
| **Animation** | Not possible | Tileset animation only | Animator component |
| **Audio** | Not possible | Requires external system | AudioSource component |
| **State Display** | Not possible | Tile swap | Sprite/animation |
| **Configuration** | TriggerData (typed) | Where? Scattered | Component fields |
| **Interaction** | Tile enter/exit | Custom system needed | InteractionController |
| **Editing** | Paint + configure | Paint + ??? | Inspector |
| **Reusability** | Manual copy | Manual copy | Prefabs |
| **Position** | Grid-locked | Grid-locked | Free |
| **Collision** | Built-in | Separate update | Component manages |
| **Complexity** | Medium | High (scattered) | Self-contained |
| **Performance** | Lightweight | Lightweight | GameObject overhead |
| **Extensibility** | Limited | Very limited | Add any component |
| **Self-Contained** | No (3 systems) | No (4+ systems) | Yes |

---

### Recommendation by Object Type

| Object | Approach | Reason |
|--------|----------|--------|
| **Stairs** | A: Collision Map | Complex elevation logic, ON_EXIT, no visuals needed |
| **Door** | C: Entity | Needs visuals, animation, sound, lock logic |
| **Chest** | C: Entity | Needs visuals, animation, inventory interaction |
| **Lever/Switch** | C: Entity | Needs visuals, state, connections to other objects |
| **Sign** | C: Entity | Needs visuals, text display on interact |
| **NPC** | C: Entity | Needs visuals, dialogue, movement, AI |
| **Warp Zone** | C: Entity | TriggerZone + WarpZone for scene transitions |
| **Spawn Point** | C: Entity | SpawnPoint marker component |
| **Pressure Plate** | C: Entity | Needs audio, state, connections to targets |
| **Secret Wall** | C: Entity | SecretPassage with StaticOccupant for blocking |
| **Breakable Wall** | C: Entity | Needs destruction effect, sound, visual change |
| **Animated Torch** | B: Tilemap | Pure decoration, tileset animation works |
| **Water Flow** | B: Tilemap | Environmental, no interaction needed |

**Key Insight**: Use **Dynamic Tilemap (B)** only for purely decorative elements that don't need interaction, audio, or complex state. Use **Entities (C)** for anything interactive. Use **Collision Map (A)** only for STAIRS (complex elevation logic).

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                           PLAYER                                     │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ InteractionController                                            ││
│  │  - Finds nearby Interactables                                   ││
│  │  - Shows interaction prompt                                      ││
│  │  - Handles input → calls interact()                             ││
│  └─────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ Inventory                                                        ││
│  │  - Holds key items                                              ││
│  │  - hasItem(), addItem(), removeItem()                           ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ interact()
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    INTERACTABLE OBJECTS                              │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │    Door      │  │    Chest     │  │    Lever     │              │
│  │  - locked    │  │  - contents  │  │  - activated │              │
│  │  - open/close│  │  - opened    │  │  - targets   │              │
│  │  - teleport  │  │  - give items│  │  - toggle    │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │    Sign      │  │     NPC      │  │  SecretPassage           │  │
│  │  - text      │  │  - dialogue  │  │  - StaticOccupant        │  │
│  │  - show UI   │  │  - quests    │  │  - toggle blocking       │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ (optional)
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      COLLISION MAP                                   │
│  - Terrain only: SOLID, WATER, LEDGE, ICE, SAND                     │
│  - STAIRS only trigger (elevation changes via TriggerSystem)        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Entity-to-Entity Collision (NPC Blocking)

### The Problem

NPCs (and other moving entities) need to block the player's movement, but they can also move themselves. This creates a challenge:

- **Static collision map**: Only works for immovable objects (walls, doors when closed)
- **NPCs move**: Their blocking position changes every frame
- **Tile-based movement**: Both player and NPCs occupy discrete tiles

### The Solution: EntityOccupancyMap

The engine already implements this via `EntityOccupancyMap` - a runtime tracking system for entity positions that's separate from the static collision map.

```
┌─────────────────────────────────────────────────────────────────────┐
│                     COLLISION SYSTEM                                 │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ CollisionMap (static)                                            ││
│  │  - SOLID walls                                                   ││
│  │  - WATER, LEDGE, etc.                                            ││
│  │  - Loaded from scene file                                        ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                    +                                 │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ EntityOccupancyMap (dynamic)                                     ││
│  │  - Tracks which entity occupies each tile                       ││
│  │  - Updated when entities move                                    ││
│  │  - Checked FIRST before tile collision                           ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                    ↓                                 │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ CollisionSystem.canMove(entity, fromTile, toTile)               ││
│  │  1. Check entityOccupancyMap.isOccupied(toTile)                 ││
│  │     → MoveResult.BlockedByEntity() if occupied                  ││
│  │  2. Check collisionMap.get(toTile)                               ││
│  │     → MoveResult.BlockedByTile() if solid                       ││
│  │  3. Check tile behaviors                                         ││
│  │     → MoveResult.Success() if passable                          ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

### How It Works

**1. Registration on Start**
```java
// GridMovement.java - onStart()
public void onStart() {
    collisionSystem = getScene().getCollisionSystem();
    collisionSystem.registerEntity(this, getCurrentTile());
}
```

**2. Move Updates Occupancy**
```java
// GridMovement.java - when entity moves
private void completeMove() {
    TileCoord from = currentTile;
    TileCoord to = targetTile;
    collisionSystem.moveEntity(this, from, to);
    currentTile = to;
}
```

**3. Collision Check Includes Entities**
```java
// CollisionSystem.java - canMove()
public MoveResult canMove(Object entity, int toX, int toY, int z) {
    // Check entity blocking FIRST
    if (entityOccupancyMap.isOccupied(toX, toY, z, entity)) {
        return MoveResult.BlockedByEntity();
    }

    // Then check tile collision
    CollisionType type = collisionMap.get(toX, toY, z);
    if (type.isSolid()) {
        return MoveResult.BlockedByTile(type);
    }

    return MoveResult.Success();
}
```

**4. Cleanup on Destroy**
```java
// GridMovement.java - onDestroy()
public void onDestroy() {
    collisionSystem.unregisterEntity(this, getCurrentTile());
}
```

### What This Means for Interactables

This system is **already implemented** and works for any entity with GridMovement:

| Entity Type | Blocking Behavior |
|-------------|-------------------|
| **NPC** | Blocks player movement via EntityOccupancyMap |
| **Player** | Blocks NPC movement via EntityOccupancyMap |
| **Door (closed)** | Uses collision map modification (static) |
| **Door (entity)** | Could use either approach |
| **Pushable Box** | Uses EntityOccupancyMap (can be moved) |

### NPCs Don't Need Special Code

Since EntityOccupancyMap is integrated into CollisionSystem, NPCs automatically block the player:

```java
// Just add GridMovement - blocking is automatic
GameObject npc = new GameObject("Guard");
npc.addComponent(new SpriteRenderer());
npc.addComponent(new GridMovement());  // ← Automatically registers with EntityOccupancyMap
npc.addComponent(new NpcController()); // AI logic
```

When the player tries to move into the NPC's tile:
1. GridMovement calls `collisionSystem.canMove(player, targetTile)`
2. CollisionSystem checks `entityOccupancyMap.isOccupied(targetTile)`
3. Returns `MoveResult.BlockedByEntity()` because NPC is there
4. Player's movement is blocked

### Interaction vs Blocking

NPCs both block movement AND are interactable:

```java
public class NpcController extends Component implements Interactable {
    // Blocking: handled automatically by GridMovement + EntityOccupancyMap

    // Interaction: explicit via Interactable interface
    @Override
    public void interact(GameObject actor) {
        startDialogue();
    }

    @Override
    public float getInteractionRadius() {
        return 1.0f;  // Can talk when adjacent
    }
}
```

---

## StaticOccupant Component

For static objects (chests, pots, signs) that block movement but don't move themselves, a lightweight `StaticOccupant` component registers with `EntityOccupancyMap` without the full `GridMovement` overhead.

### Implementation

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.CollisionSystem;
import com.pocket.rpg.collision.TileCoord;
import com.pocket.rpg.components.Component;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers static objects with EntityOccupancyMap so they block movement.
 *
 * Use this for non-moving objects like chests, pots, signs, statues.
 * For moving entities, use GridMovement instead.
 */
public class StaticOccupant extends Component {

    /**
     * Tiles this object occupies.
     * Default: single tile at (0,0,0).
     */
    @Getter @Setter
    private List<TileCoord> occupiedTiles = new ArrayList<>(List.of(new TileCoord(0, 0, 0)));

    /**
     * If true, tiles are relative to entity position.
     * If false, tiles are absolute world coordinates.
     */
    @Getter @Setter
    private boolean tilesRelative = true;

    private CollisionSystem collisionSystem;

    @Override
    public void onStart() {
        collisionSystem = getScene().getCollisionSystem();
        for (TileCoord tile : getAbsoluteTiles()) {
            collisionSystem.registerEntity(this, tile);
        }
    }

    @Override
    public void onDestroy() {
        if (collisionSystem != null) {
            for (TileCoord tile : getAbsoluteTiles()) {
                collisionSystem.unregisterEntity(this, tile);
            }
        }
    }

    /**
     * Gets occupied tiles in world coordinates.
     */
    public List<TileCoord> getAbsoluteTiles() {
        if (!tilesRelative) {
            return occupiedTiles;
        }

        Vector3f pos = getTransform().getPosition();
        int baseX = (int) pos.x;
        int baseY = (int) pos.y;
        int baseZ = (int) pos.z;

        List<TileCoord> absolute = new ArrayList<>();
        for (TileCoord tile : occupiedTiles) {
            absolute.add(new TileCoord(
                baseX + tile.x(),
                baseY + tile.y(),
                baseZ + tile.elevation()
            ));
        }
        return absolute;
    }
}
```

### Usage Examples

**Single-tile object (pot, sign):**
```java
GameObject pot = new GameObject("Pot");
pot.addComponent(new SpriteRenderer());
pot.addComponent(new StaticOccupant());  // Blocks the tile it's on
```

**Multi-tile object (large statue, table):**
```java
GameObject statue = new GameObject("Statue");
statue.addComponent(new SpriteRenderer());

StaticOccupant occupant = new StaticOccupant();
occupant.setOccupiedTiles(List.of(
    new TileCoord(0, 0, 0),   // Base tile
    new TileCoord(1, 0, 0)    // Tile to the right
));
statue.addComponent(occupant);
```

**Chest (blocks + interactable):**
```java
GameObject chest = new GameObject("Chest");
chest.addComponent(new SpriteRenderer());
chest.addComponent(new AudioSource());
chest.addComponent(new StaticOccupant());  // Blocks movement
chest.addComponent(new Chest());           // Interactable behavior
```

### Collision Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                    WHAT BLOCKS MOVEMENT                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  COLLISION MAP (static terrain)         ENTITY OCCUPANCY MAP        │
│  ┌─────────────────────────────┐        ┌─────────────────────────┐ │
│  │ • Walls (SOLID)             │        │ • Player (GridMovement) │ │
│  │ • Water (WATER)             │        │ • NPCs (GridMovement)   │ │
│  │ • Ledges (LEDGE_*)          │        │ • Chests (StaticOccup.) │ │
│  │ • Pits (PIT)                │   +    │ • Pots (StaticOccupant) │ │
│  │ • Elevation boundaries      │        │ • Signs (StaticOccup.)  │ │
│  │                             │        │ • Doors (when closed)   │ │
│  │ Painted in editor           │        │ • Any entity object     │ │
│  │ Saved in scene file         │        │ • Registered at runtime │ │
│  └─────────────────────────────┘        └─────────────────────────┘ │
│               │                                    │                 │
│               └────────────┬───────────────────────┘                 │
│                            ▼                                         │
│              ┌─────────────────────────────┐                         │
│              │ CollisionSystem.canMove()   │                         │
│              │  1. Check EntityOccupancyMap│                         │
│              │  2. Check CollisionMap      │                         │
│              │  3. Check TileBehaviors     │                         │
│              └─────────────────────────────┘                         │
└─────────────────────────────────────────────────────────────────────┘
```

### What Goes Where

| Object Type | Collision Map | Component |
|-------------|---------------|-----------|
| **Terrain** | | |
| Walls | SOLID | - |
| Water | WATER | - |
| Ledges | LEDGE_* | - |
| Pits | PIT | - |
| Stairs | STAIRS | - (complex elevation logic) |
| **Static Objects** | | |
| Chest | - | StaticOccupant + Chest |
| Pot/Barrel | - | StaticOccupant |
| Sign | - | StaticOccupant + Sign |
| Statue | - | StaticOccupant |
| Closed Door | - | StaticOccupant + Door |
| **Moving Entities** | | |
| Player | - | GridMovement |
| NPC | - | GridMovement |
| Enemy | - | GridMovement |
| Pushable Block | - | GridMovement |
| **Triggers** | | |
| Warp Zone | - | TriggerZone + WarpZone |
| Spawn Point | - | SpawnPoint |
| Cutscene | - | TriggerZone + CutsceneTrigger |

### Key Insight

**Collision map = terrain only.** All objects use `TileEntityMap`:
- Moving objects → `GridMovement`
- Static objects → `StaticOccupant`
- Triggers → `TriggerZone`

This eliminates sync issues between collision map and entity positions.

---

## Unified Tile Entity System

Instead of separate systems for blocking and triggers, use one unified map that tracks all entities per tile:

### TileEntityMap

```java
package com.pocket.rpg.collision;

import com.pocket.rpg.components.Component;
import java.util.*;

/**
 * Unified system tracking all entities per tile.
 *
 * Used for:
 * - Blocking queries (before movement)
 * - Trigger queries (after movement)
 * - Future: damage zones, cutscene triggers, etc.
 */
public class TileEntityMap {

    private final Map<Long, Set<Component>> entities = new HashMap<>();

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    public void register(Component comp, TileCoord tile) {
        long key = toKey(tile);
        entities.computeIfAbsent(key, k -> new HashSet<>()).add(comp);
    }

    public void unregister(Component comp, TileCoord tile) {
        long key = toKey(tile);
        Set<Component> set = entities.get(key);
        if (set != null) {
            set.remove(comp);
            if (set.isEmpty()) {
                entities.remove(key);
            }
        }
    }

    public void move(Component comp, TileCoord from, TileCoord to) {
        unregister(comp, from);
        register(comp, to);
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    /**
     * Gets all components at a tile.
     */
    public Set<Component> getAll(TileCoord tile) {
        return entities.getOrDefault(toKey(tile), Collections.emptySet());
    }

    /**
     * Gets components of a specific type at a tile.
     */
    public <T> List<T> get(TileCoord tile, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component c : getAll(tile)) {
            if (type.isInstance(c)) {
                result.add(type.cast(c));
            }
        }
        return result;
    }

    /**
     * Checks if tile is blocked by another entity.
     * Used BEFORE movement.
     */
    public boolean isBlocked(TileCoord tile, Object mover) {
        for (Component c : getAll(tile)) {
            if (c == mover) continue;

            // These component types block movement
            if (c instanceof StaticOccupant || c instanceof GridMovement) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets trigger zones at tile.
     * Used AFTER movement.
     */
    public List<TriggerZone> getTriggers(TileCoord tile) {
        return get(tile, TriggerZone.class);
    }

    private long toKey(TileCoord tile) {
        return ((long) tile.x() << 40) | ((long) tile.y() << 16) | (tile.elevation() & 0xFFFF);
    }
}
```

### Integration with CollisionSystem

```java
public class CollisionSystem {
    private final CollisionMap collisionMap;      // Terrain (walls, water)
    private final TileEntityMap tileEntityMap;    // All entities

    /**
     * Checks if movement is allowed.
     * Called BEFORE move.
     */
    public MoveResult canMove(Object mover, TileCoord from, TileCoord to) {
        // 1. Check entity blocking
        if (tileEntityMap.isBlocked(to, mover)) {
            return MoveResult.BlockedByEntity();
        }

        // 2. Check terrain
        CollisionType type = collisionMap.get(to.x(), to.y(), to.elevation());
        if (type.isSolid()) {
            return MoveResult.BlockedByTile(type);
        }

        // 3. Check tile behaviors (ledges, etc.)
        // ...

        return MoveResult.Success();
    }

    /**
     * Called AFTER move completes.
     * Fires triggers at new position.
     */
    public void onEntityMoved(GameObject entity, TileCoord newTile) {
        for (TriggerZone trigger : tileEntityMap.getTriggers(newTile)) {
            trigger.onEntityEnter(entity);
        }
    }
}
```

### TriggerZone Component

```java
package com.pocket.rpg.components.interaction;

/**
 * Non-blocking trigger that fires when entities enter its tile(s).
 *
 * Use cases:
 * - WarpZone (teleport on enter)
 * - CutsceneTrigger (start cutscene)
 * - DamageZone (hurt player)
 * - EventTrigger (custom scripting)
 */
public class TriggerZone extends Component {

    @Getter @Setter
    private List<TileCoord> triggerTiles = new ArrayList<>(List.of(new TileCoord(0, 0, 0)));

    @Getter @Setter
    private boolean tilesRelative = true;

    @Getter @Setter
    private boolean oneShot = false;  // Fire only once

    @Getter @Setter
    private boolean playerOnly = true;  // Only trigger for player

    @Transient
    private boolean triggered = false;

    @Transient
    private TileEntityMap tileEntityMap;

    @Override
    public void onStart() {
        tileEntityMap = getScene().getCollisionSystem().getTileEntityMap();
        for (TileCoord tile : getAbsoluteTiles()) {
            tileEntityMap.register(this, tile);
        }
    }

    @Override
    public void onDestroy() {
        if (tileEntityMap != null) {
            for (TileCoord tile : getAbsoluteTiles()) {
                tileEntityMap.unregister(this, tile);
            }
        }
    }

    /**
     * Called by CollisionSystem when entity enters this tile.
     */
    public void onEntityEnter(GameObject entity) {
        if (oneShot && triggered) return;
        if (playerOnly && !isPlayer(entity)) return;

        triggered = true;

        // Notify all TriggerListener components on this GameObject
        for (Component c : gameObject.getAllComponents()) {
            if (c instanceof TriggerListener listener) {
                listener.onTriggerEnter(entity, this);
            }
        }
    }

    private List<TileCoord> getAbsoluteTiles() {
        // Same as StaticOccupant
    }
}

/**
 * Interface for components that respond to trigger events.
 */
public interface TriggerListener {
    void onTriggerEnter(GameObject entity, TriggerZone trigger);
    default void onTriggerExit(GameObject entity, TriggerZone trigger) {}
}
```

### Trigger-Based Components

**WarpZone:**
```java
public class WarpZone extends Component implements TriggerListener {
    @Getter @Setter private String targetScene;
    @Getter @Setter private String targetSpawnId;
    @Getter @Setter private TransitionType transition = TransitionType.FADE;

    @Override
    public void onTriggerEnter(GameObject entity, TriggerZone trigger) {
        SceneManager.transition(targetScene, targetSpawnId, transition);
    }
}

// Usage:
GameObject warp = new GameObject("Warp_To_Town");
warp.addComponent(new TriggerZone());
warp.addComponent(new WarpZone("town", "spawn_entrance"));
```

**CutsceneTrigger:**
```java
public class CutsceneTrigger extends Component implements TriggerListener {
    @Getter @Setter private String cutsceneId;

    @Override
    public void onTriggerEnter(GameObject entity, TriggerZone trigger) {
        CutsceneManager.play(cutsceneId);
    }
}
```

**SpawnPoint (no trigger needed - just a marker):**
```java
public class SpawnPoint extends Component {
    @Getter @Setter private String spawnId = "default";
    // No TriggerZone - just a position marker
}
```

### Component Summary

| Component | Registers with TileEntityMap | Blocks | Triggers | Purpose |
|-----------|------------------------------|--------|----------|---------|
| StaticOccupant | ✓ | ✓ | - | Blocking for static objects |
| GridMovement | ✓ | ✓ | - | Blocking for moving entities |
| TriggerZone | ✓ | - | ✓ | Non-blocking tile detection |
| SpawnPoint | - | - | - | Position marker only |

### What Stays in Collision Map

Only terrain that doesn't need entity representation:

| Type | Collision Map | Entity |
|------|---------------|--------|
| Walls | SOLID | - |
| Water | WATER | - |
| Ledges | LEDGE_* | - |
| Pits | PIT | - |
| Stairs | STAIRS | - (elevation logic) |
| Warp | - | TriggerZone + WarpZone |
| Spawn | - | SpawnPoint |
| Door | - | StaticOccupant + Door |

**STAIRS stays in collision map permanently:**
- Direction-based elevation changes (exit NORTH = +1, exit SOUTH = -1)
- ON_EXIT activation (triggers when leaving tile, not entering)
- Integrated with GridMovement elevation system
- No visual/audio representation needed
- Existing TriggerInspector handles STAIRS configuration
- Uses existing TriggerSystem + StairsData + StairsHandler

---

## Implementation Phases

### Phase 1: Core Systems
- `TileEntityMap` - unified entity-per-tile tracking
- `StaticOccupant` component (blocking for static objects)
- `TriggerZone` component (non-blocking triggers)
- `TriggerListener` interface
- Update `CollisionSystem` to use `TileEntityMap`
- Update `GridMovement` to fire triggers after move

### Phase 2: Interactable System
- `Interactable` interface
- `InteractionController` component (on player)
- Basic interaction detection and input handling
- Interaction prompt UI

### Phase 3: Door Component
- `Door` component implementing Interactable
- Open/close state with sprite switching
- Locked state with key checking
- Optional teleport destination
- Audio integration

### Phase 4: Inventory System
- Basic `Inventory` component
- Key item support
- Integration with Door/Chest

### Phase 5: Additional Interactables
- `Chest` component (gives items)
- `Lever` component (toggles state, triggers targets)
- `Sign` component (shows text)
- `SecretPassage` component (toggleable blocking via StaticOccupant)

### Phase 6: Trigger Migration
- `SpawnPoint` component (replaces SPAWN_POINT collision type)
- `WarpZone` component (replaces WARP collision type)
- Update scene loading to find SpawnPoint entities
- Remove WARP, SPAWN_POINT from CollisionType

### Phase 7: Editor Integration
- Door/Chest/Lever/WarpZone prefabs
- Inspector UI for new components
- Interaction radius visualization
- TriggerZone tile overlay in editor

### Phase 8: Cleanup & Migration
- Remove DOOR from CollisionType
- Remove old trigger handlers (DoorHandler, WarpHandler, etc.)
- Migrate existing scenes
- Documentation

---

## Files Summary

### New Files

| File | Purpose |
|------|---------|
| **Core Systems** | |
| `collision/TileEntityMap.java` | Unified entity-per-tile tracking |
| `components/interaction/TriggerZone.java` | Non-blocking tile trigger |
| `components/interaction/TriggerListener.java` | Interface for trigger events |
| `components/interaction/StaticOccupant.java` | Blocking for static objects |
| **Interactables** | |
| `components/interaction/Interactable.java` | Core interface |
| `components/interaction/InteractionController.java` | Player component |
| `components/interaction/Door.java` | Door behavior |
| `components/interaction/Chest.java` | Chest behavior |
| `components/interaction/Lever.java` | Lever/switch behavior |
| `components/interaction/Sign.java` | Readable sign |
| `components/interaction/SecretPassage.java` | Toggleable blocking (secret walls) |
| **Triggers** | |
| `components/interaction/SpawnPoint.java` | Spawn position marker |
| `components/interaction/WarpZone.java` | Teleport on enter |
| **Inventory** | |
| `components/inventory/Inventory.java` | Item storage |
| `components/inventory/ItemStack.java` | Item + quantity |

### Modified Files

| File | Changes |
|------|---------|
| `collision/CollisionSystem.java` | Use TileEntityMap, add onEntityMoved() |
| `collision/CollisionType.java` | Remove DOOR, WARP, SPAWN_POINT |
| `components/GridMovement.java` | Register with TileEntityMap, fire triggers |
| `scenes/Scene.java` | Add findSpawnPoint(), findComponentsInRadius() |
| `scenes/SceneManager.java` | Use SpawnPoint entities instead of collision map |

### Files to Remove

| File | Reason |
|------|--------|
| `collision/trigger/DoorTriggerData.java` | Replaced by Door component |
| `collision/trigger/WarpTriggerData.java` | Replaced by WarpZone component |
| `collision/trigger/SpawnPointTriggerData.java` | Replaced by SpawnPoint component |
| `collision/trigger/handlers/DoorHandler.java` | Replaced by Door component |
| `collision/trigger/handlers/WarpHandler.java` | Replaced by WarpZone component |
| `collision/EntityOccupancyMap.java` | Replaced by TileEntityMap |

---

## Analysis: Current vs New Architecture

This section compares the existing trigger-system implementation with the proposed entity-based system.

### What Exists (trigger-system)

| Component | Location | Purpose |
|-----------|----------|---------|
| `EntityOccupancyMap` | `collision/` | Tracks entities per tile for blocking |
| `CollisionSystem` | `collision/` | Query API: CollisionMap + EntityOccupancyMap + TileBehavior |
| `TriggerSystem` | `collision/trigger/` | Dispatches ON_ENTER/ON_EXIT/ON_INTERACT events |
| `TriggerDataMap` | `collision/trigger/` | Stores trigger config per tile |
| `TriggerData` interface | `collision/trigger/` | Sealed interface for trigger types |
| `WarpTriggerData` | `collision/trigger/` | Warp configuration |
| `DoorTriggerData` | `collision/trigger/` | Door configuration |
| `SpawnPointData` | `collision/trigger/` | Spawn point configuration |
| `StairsData` | `collision/trigger/` | Stairs configuration |
| `WarpHandler` | `collision/trigger/handlers/` | Handles warp execution |
| `DoorHandler` | `collision/trigger/handlers/` | Handles door interaction |
| `StairsHandler` | `collision/trigger/handlers/` | Handles elevation change |
| `CollisionType.WARP` | `collision/` | Collision type for warps |
| `CollisionType.DOOR` | `collision/` | Collision type for doors |
| `CollisionType.SPAWN_POINT` | `collision/` | Collision type for spawn points |
| `CollisionType.STAIRS` | `collision/` | Collision type for stairs |

### What Changes

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CURRENT ARCHITECTURE                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CollisionMap          TriggerDataMap         TriggerSystem                 │
│  ┌──────────────┐     ┌──────────────────┐   ┌──────────────────┐          │
│  │ (5,10) WARP  │     │ (5,10) → WarpData│   │ ON_ENTER dispatch│          │
│  │ (3,3)  DOOR  │     │ (3,3)  → DoorData│   │ Handler registry │          │
│  │ (0,0) SPAWN  │     │ (0,0)  → SpawnD. │   │                  │          │
│  └──────────────┘     └──────────────────┘   └──────────────────┘          │
│         │                     │                       │                     │
│         └─────────────────────┴───────────────────────┘                     │
│                               │                                              │
│                     GridMovement calls TriggerSystem                        │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                            NEW ARCHITECTURE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CollisionMap (terrain only)      TileEntityMap (unified)                   │
│  ┌──────────────────────┐        ┌────────────────────────────────────────┐│
│  │ SOLID, WATER, LEDGE  │        │ Tile → [Components...]                 ││
│  │ ICE, SAND, STAIRS    │        │                                        ││
│  │                      │        │ (5,10) → [TriggerZone, WarpZone]       ││
│  │ No WARP, DOOR, SPAWN │        │ (3,3)  → [StaticOccupant, Door]        ││
│  └──────────────────────┘        │ (0,0)  → [SpawnPoint]                  ││
│                                  │ (2,5)  → [GridMovement] (NPC)          ││
│                                  │ (4,4)  → [StaticOccupant] (Chest)      ││
│                                  └────────────────────────────────────────┘│
│                                           │                                 │
│               ┌───────────────────────────┼───────────────────────────┐    │
│               │                           │                           │    │
│               ▼                           ▼                           ▼    │
│         isBlocked()               getTriggers()                findSpawn() │
│    (before move check)       (after move dispatch)         (scene loading) │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Migration Table

| Current | Status | New Replacement | Notes |
|---------|--------|-----------------|-------|
| **Keep** | | | |
| `CollisionMap` | ✅ Keep | - | Terrain only |
| `CollisionSystem` | ✅ Keep | Update | Use TileEntityMap |
| `CollisionType` (terrain) | ✅ Keep | - | SOLID, WATER, LEDGE, ICE, etc. |
| `CollisionType.STAIRS` | ✅ Keep | - | Complex elevation logic |
| `StairsData` | ✅ Keep | - | Direction-based elevation |
| `StairsHandler` | ✅ Keep | - | Elevation change logic |
| `TileBehavior` system | ✅ Keep | - | ICE sliding, SAND slowing, etc. |
| **Rename/Extend** | | | |
| `EntityOccupancyMap` | 🔄 Extend | `TileEntityMap` | Add trigger queries |
| **Remove** | | | |
| `CollisionType.WARP` | ❌ Remove | `WarpZone` component | Entity-based |
| `CollisionType.DOOR` | ❌ Remove | `Door` component | Entity-based |
| `CollisionType.SPAWN_POINT` | ❌ Remove | `SpawnPoint` component | Entity-based |
| `TriggerSystem` | ❌ Remove | `TileEntityMap.getTriggers()` | Integrated |
| `TriggerDataMap` | ❌ Remove | Component fields | Data in components |
| `WarpTriggerData` | ❌ Remove | `WarpZone` component | |
| `DoorTriggerData` | ❌ Remove | `Door` component | |
| `SpawnPointData` | ❌ Remove | `SpawnPoint` component | |
| `WarpHandler` | ❌ Remove | `WarpZone.onTriggerEnter()` | |
| `DoorHandler` | ❌ Remove | `Door.interact()` | |
| **New** | | | |
| - | ✨ New | `TileEntityMap` | Unified entity tracking |
| - | ✨ New | `TriggerZone` | Non-blocking triggers |
| - | ✨ New | `TriggerListener` | Trigger event interface |
| - | ✨ New | `StaticOccupant` | Blocking for static objects |
| - | ✨ New | `Interactable` | Interaction interface |
| - | ✨ New | `InteractionController` | Player interaction handling |
| - | ✨ New | `SpawnPoint` | Spawn marker component |
| - | ✨ New | `WarpZone` | Teleport trigger component |
| - | ✨ New | `Door` | Door component |
| - | ✨ New | `Chest`, `Lever`, `Sign` | Additional interactables |
| - | ✨ New | `Inventory` | Item storage |

### Why This Is Better

| Aspect | Old (Collision Map) | New (Entity) |
|--------|---------------------|--------------|
| **Data location** | Split: CollisionMap + TriggerDataMap | Unified: Component fields |
| **Visual feedback** | None (invisible) | SpriteRenderer, Animator |
| **Audio** | None | AudioSource component |
| **Reusability** | Manual copy | Prefabs |
| **Editor UX** | Paint + configure separately | Place entity, configure in Inspector |
| **Extensibility** | New CollisionType + TriggerData + Handler | New component |
| **Blocking objects** | Manual SOLID painting | Automatic via StaticOccupant |
| **Moving blockers** | EntityOccupancyMap (already works) | TileEntityMap (same) |

### STAIRS Integration

STAIRS remains the only collision-map trigger. After a move, GridMovement checks both:

```java
// GridMovement after move:
public void onMoveComplete(TileCoord newTile) {
    // Check TileEntityMap for entity-based triggers
    for (TriggerZone trigger : tileEntityMap.getTriggers(newTile)) {
        trigger.onEntityEnter(gameObject);
    }

    // Check TriggerSystem for STAIRS (ON_EXIT elevation changes)
    triggerSystem.onTileEnter(gameObject, newTile.x(), newTile.y(), newTile.elevation());
}
```

---

## Key Design Decisions

### 1. Interactable is an Interface, Not Base Class

Allows any component to be interactable without inheritance constraints:
```java
public class Door extends Component implements Interactable { }
public class Chest extends Component implements Interactable { }
public class NPC extends Component implements Interactable, Talkable { }
```

### 2. InteractionController Handles Detection

Player doesn't need to know about specific interactable types:
```java
// InteractionController finds ANY Interactable nearby
Interactable target = findNearest();
if (target != null && input.interact()) {
    target.interact(player);
}
```

### 3. Blocking is Component-Based

Blocking is managed by registering/unregistering with TileEntityMap:
```java
// Door registers when closed, unregisters when open
if (!open) {
    tileEntityMap.register(this, blockedTile);
} else {
    tileEntityMap.unregister(this, blockedTile);
}
```

### 4. Prefabs for Reusability

Common door/chest configurations become prefabs:
- `LockedDoor.prefab` - Door with key requirement
- `WoodenChest.prefab` - Standard chest
- `SecretPassage.prefab` - SecretPassage with StaticOccupant for wall blocking

---

## Player Facing Direction Detection

Interaction with objects (doors, chests, NPCs) requires the player to be facing them. This prevents interacting with objects behind the player.

### Implementation in InteractionController

```java
// InteractionController.java - isFacing check
private boolean isFacing(Interactable target) {
    if (gridMovement == null) {
        return true; // No GridMovement = always facing
    }

    Vector3f myPos = gameObject.getTransform().getPosition();
    Vector3f targetPos = target.getPosition();

    float dx = targetPos.x - myPos.x;
    float dy = targetPos.y - myPos.y;

    // Determine which direction target is in
    Direction targetDir;
    if (Math.abs(dx) > Math.abs(dy)) {
        targetDir = dx > 0 ? Direction.RIGHT : Direction.LEFT;
    } else {
        targetDir = dy > 0 ? Direction.UP : Direction.DOWN;
    }

    // Check if player is facing that direction
    Direction facing = gridMovement.getFacingDirection();
    return facing == targetDir;
}
```

### Behavior

- If `Interactable.requiresFacing()` returns `true` (default), player must face the object
- Some interactables may allow interaction from any direction (e.g., area triggers)
- GridMovement already tracks facing direction based on last move input

---

## Item/Key System Integration

The existing `DoorTriggerData.requiredKey` mechanism needs to integrate with the `Inventory` component system.

### Current System (to be removed)

```java
// DoorTriggerData in collision system
record DoorTriggerData(
    boolean locked,
    String requiredKey,     // Item ID to check
    boolean consumeKey,
    String lockedMessage,
    String targetScene,
    String targetSpawnId
)
```

### New System Integration

The `Door` component checks the player's `Inventory` component:

```java
// Door.java
private boolean tryUnlock(GameObject actor) {
    if (requiredKey == null || requiredKey.isBlank()) {
        return true; // No key required
    }

    Inventory inventory = actor.getComponent(Inventory.class);
    if (inventory == null || !inventory.hasItem(requiredKey)) {
        return false;
    }

    if (consumeKey) {
        inventory.removeItem(requiredKey, 1);
    }
    return true;
}
```

### Inventory Component (Plan Reference)

See `04-inventory-system.md` for full Inventory component design:
- `hasItem(String itemId)` - Check if player has item
- `addItem(String itemId, int count)` - Add items
- `removeItem(String itemId, int count)` - Remove items
- Items stored as `Map<String, Integer>` (itemId → count)

### Integration Notes

- Key items are just item IDs (strings like `"dungeon_key"`, `"boss_key"`)
- Inventory component attached to Player GameObject
- Door/Chest/etc. look up Inventory on the interacting actor
- No special "key item" type needed - any item ID works

---

## Save System Integration

Door states, chest states, and lever positions need to persist across save/load cycles.

### Saveable Components

Each interactable tracks its own state:

| Component | Saveable State |
|-----------|----------------|
| `Door` | `open`, `locked` |
| `Chest` | `opened`, `contents` (if not yet taken) |
| `Lever` | `activated` |
| `SecretPassage` | `revealed` |

### Integration Approach

1. **Component implements Saveable interface**:
```java
public interface Saveable {
    /** Unique ID for this object in save data */
    String getSaveId();

    /** Serialize state to save data */
    void writeSaveData(SaveData data);

    /** Restore state from save data */
    void readSaveData(SaveData data);
}
```

2. **Door implementation example**:
```java
public class Door extends Component implements Interactable, Saveable {
    @Override
    public String getSaveId() {
        return "door_" + gameObject.getName();
    }

    @Override
    public void writeSaveData(SaveData data) {
        data.putBoolean("open", open);
        data.putBoolean("locked", locked);
    }

    @Override
    public void readSaveData(SaveData data) {
        open = data.getBoolean("open", false);
        locked = data.getBoolean("locked", this.locked);
        updateVisuals();
        updateCollision();
    }
}
```

3. **Scene save/load flow**:
```java
// On save
for (Saveable s : scene.findComponentsOfType(Saveable.class)) {
    saveData.put(s.getSaveId(), s.writeSaveData());
}

// On load
for (Saveable s : scene.findComponentsOfType(Saveable.class)) {
    SaveData data = saveData.get(s.getSaveId());
    if (data != null) {
        s.readSaveData(data);
    }
}
```

### Not Part of This Plan

The actual save system implementation is separate. This plan defines:
- What state each component needs to save
- The interface pattern to use
- Integration points for the save system

---

## Obsolete Inspector Implementations

The current `TriggerInspector` handles configuration for collision-map triggers. With entity-based triggers, most of this becomes obsolete.

### Current TriggerInspector Handlers

| Trigger Type | Current Handler | New Status |
|--------------|-----------------|------------|
| `DOOR` | Door properties in TriggerInspector | ❌ **Obsolete** - Use Door component Inspector |
| `WARP` | Warp properties in TriggerInspector | ❌ **Obsolete** - Use WarpZone component Inspector |
| `SPAWN_POINT` | Spawn ID in TriggerInspector | ❌ **Obsolete** - Use SpawnPoint component Inspector |
| `STAIRS` | Stairs config in TriggerInspector | ✅ **Keep** - STAIRS stays collision-based |

### What to Remove from TriggerInspector

```java
// Remove these cases from TriggerInspector.render():
case DOOR -> renderDoorProperties();      // Now in Door component
case WARP -> renderWarpProperties();      // Now in WarpZone component
case SPAWN_POINT -> renderSpawnProperties(); // Now in SpawnPoint component

// Keep only:
case STAIRS -> renderStairsProperties();  // STAIRS stays collision-based
```

### New Component Inspectors

Standard component inspection handles new entities:

| Component | Inspector | Fields |
|-----------|-----------|--------|
| `Door` | Standard component inspector | locked, requiredKey, openSprite, closedSprite, sounds, targetScene, etc. |
| `WarpZone` | Standard component inspector | targetScene, targetSpawnId, transition |
| `SpawnPoint` | Standard component inspector | spawnId |
| `Chest` | Standard component inspector | contents, opened, sounds |
| `TriggerZone` | Standard component inspector | triggerTiles, oneShot, playerOnly |
| `StaticOccupant` | Standard component inspector | occupiedTiles, tilesRelative |

### CollisionType Cleanup

Remove from `CollisionType` enum and `CollisionTypeSelector`:
- `DOOR` - No longer a collision type
- `WARP` - No longer a collision type
- `SPAWN_POINT` - No longer a collision type

Keep in `CollisionType`:
- `STAIRS` - Complex elevation logic, stays collision-based
- All terrain types (SOLID, WATER, LEDGE_*, ICE, SAND, PIT, etc.)

---

## Future Enhancements

### Interaction Animations
Player plays "use" animation when interacting.

### Multi-Tile Doors
Door entity that spans multiple tiles (double doors, large gates).

### Linked Interactables
Lever A opens Door B (event/signal system).

### Interaction Conditions
- Time-based (only at night)
- Quest-based (need to talk to NPC first)
- Skill-based (need lockpick skill)

### Save System Integration
Persist door open/closed state, chest emptied state, lever positions.
