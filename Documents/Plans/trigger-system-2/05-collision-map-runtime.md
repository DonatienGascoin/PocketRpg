# Phase 5: Collision Map - Terrain Only

## Overview

This phase clarifies the role of the collision map in the new architecture. The collision map is **terrain only** - all interactive blocking is handled by entities via `TileEntityMap`.

---

## Collision Map Purpose

The collision map stores static terrain collision:

| Type | Purpose |
|------|---------|
| `SOLID` | Impassable walls |
| `WATER` | Drowning hazard |
| `LEDGE_*` | Directional ledges |
| `ICE` | Slippery surface |
| `SAND` | Slow movement |
| `PIT` | Fall hazard |
| `STAIRS` | Elevation change (ON_EXIT trigger) |

**NOT in collision map:**
- Doors (use `Door` entity)
- Chests (use `Chest` + `StaticOccupant`)
- NPCs (use `GridMovement`)
- Warps (use `WarpZone` entity)
- Spawn points (use `SpawnPoint` entity)
- Secret passages (use `SecretPassage` entity)

---

## No Runtime Collision Map Modification

Interactive objects do **not** modify the collision map. Instead, they register/unregister with `TileEntityMap`:

### Why Entity-Based Blocking?

| Aspect | Collision Map Modification | Entity-Based (TileEntityMap) |
|--------|---------------------------|------------------------------|
| **Audio** | No natural place | AudioSource component |
| **Visuals** | Must sync manually | SpriteRenderer component |
| **Animation** | Not possible | Animator component |
| **State** | Scattered data | Component fields |
| **Save/Load** | Track tile changes | Track entity state |
| **Editor** | Paint tiles | Configure in Inspector |
| **Prefabs** | Not supported | Full prefab support |

### Entity Pattern for Blocking

All interactive blocking uses this pattern:

```java
public class InteractiveBlocker extends Component {
    private List<TileCoord> registeredTiles = new ArrayList<>();
    private TileEntityMap tileEntityMap;

    @Override
    public void start() {
        tileEntityMap = getScene().getCollisionSystem().getTileEntityMap();
        registerBlocking();
    }

    @Override
    public void onDestroy() {
        unregisterBlocking();
    }

    private void registerBlocking() {
        for (TileCoord tile : getAbsoluteTiles()) {
            tileEntityMap.register(this, tile);
            registeredTiles.add(tile);
        }
    }

    private void unregisterBlocking() {
        for (TileCoord tile : registeredTiles) {
            tileEntityMap.unregister(this, tile);
        }
        registeredTiles.clear();
    }

    // Toggle blocking on state change
    public void setBlocking(boolean blocking) {
        unregisterBlocking();
        if (blocking) {
            registerBlocking();
        }
    }
}
```

---

## Interactive Object Examples

### Door (Blocks When Closed)

```java
// In Door.java
public void openDoor() {
    open = true;
    updateBlocking();  // Unregisters from TileEntityMap
    updateVisuals();
    playSound(openSound);
}

public void closeDoor() {
    open = false;
    updateBlocking();  // Registers with TileEntityMap
    updateVisuals();
    playSound(closeSound);
}

private void updateBlocking() {
    for (TileCoord tile : registeredTiles) {
        tileEntityMap.unregister(this, tile);
    }
    registeredTiles.clear();

    if (!open) {
        for (TileCoord tile : getAbsoluteTiles()) {
            tileEntityMap.register(this, tile);
            registeredTiles.add(tile);
        }
    }
}
```

### SecretPassage (Blocks When Hidden)

```java
// In SecretPassage.java
public void reveal() {
    revealed = true;
    updateBlocking();  // Unregisters from TileEntityMap
    spriteRenderer.setEnabled(false);  // Hide wall sprite
    playSound(revealSound);
}
```

### Chest (Always Blocks)

```java
// Chest uses StaticOccupant component for blocking
GameObject chest = new GameObject("Chest");
chest.addComponent(new SpriteRenderer());
chest.addComponent(new AudioSource());
chest.addComponent(new StaticOccupant());  // Always blocks
chest.addComponent(new Chest());           // Interaction logic
```

---

## What Stays in Collision Map

Only **terrain** that never changes at runtime:

| Type | Notes |
|------|-------|
| `SOLID` | Painted walls |
| `WATER` | Lakes, rivers |
| `LEDGE_*` | Cliffs, drops |
| `ICE` | Frozen surfaces |
| `SAND` | Desert, beach |
| `PIT` | Holes |
| `STAIRS` | Elevation changes (keeps TriggerData for direction) |

### STAIRS Special Case

STAIRS is the only collision type that has runtime behavior (elevation change). It stays in the collision map because:

1. **Direction-based logic** - Exit direction determines elevation change
2. **ON_EXIT activation** - Triggers when leaving tile, not entering
3. **No visual/audio** - Just elevation math
4. **Tightly coupled** - Integrated with GridMovement elevation system

```java
// STAIRS handled by existing TriggerSystem + StairsHandler
// No entity needed for stairs
CollisionType.STAIRS at (5, 10)
StairsData { exitUp: NORTH, exitDown: SOUTH }
```

---

## Migration from Old Approach

If you previously used collision map modification for interactive objects:

### Old Pattern (DON'T USE)

```java
// OLD: Modify collision map directly
collisionMap.set(x, y, z, CollisionType.SOLID);  // Block
collisionMap.set(x, y, z, CollisionType.NONE);   // Unblock
```

### New Pattern (USE THIS)

```java
// NEW: Use TileEntityMap
tileEntityMap.register(this, tile);    // Block
tileEntityMap.unregister(this, tile);  // Unblock
```

---

## Performance

### TileEntityMap is Efficient

- HashMap-based: O(1) register/unregister/query
- No chunk overhead like CollisionMap
- Only tracks tiles with entities

### Best Practices

```java
// Good: State-based updates
public void setBlocking(boolean blocking) {
    if (this.blocking == blocking) return;  // No change
    this.blocking = blocking;
    updateBlocking();
}

// Bad: Frequent updates
public void update(float dt) {
    updateBlocking();  // Don't do this every frame
}
```

---

## Summary

| Object Type | Blocking System |
|-------------|-----------------|
| Walls, Water, Terrain | CollisionMap |
| Door, Chest, Pot | TileEntityMap via component |
| NPC, Enemy, Player | TileEntityMap via GridMovement |
| Secret Passage | TileEntityMap via SecretPassage |
| Stairs | CollisionMap + TriggerSystem |

**Key principle**: Collision map = terrain painted in editor. Interactive objects = entities with TileEntityMap.

---

## Next Phase

Phase 6: Editor Integration
