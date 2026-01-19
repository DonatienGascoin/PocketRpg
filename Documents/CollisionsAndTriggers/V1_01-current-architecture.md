# PocketRPG: Current Collision & Movement Architecture

## Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                           Scene                                  │
├─────────────────────────────────────────────────────────────────┤
│  CollisionMap              CollisionSystem                       │
│  ┌─────────────┐          ┌──────────────────────────────┐      │
│  │ Chunk-based │◄────────►│ Query API                    │      │
│  │ storage     │          │ - canMove(from, to, dir)     │      │
│  │ per (x,y,z) │          │ - isWalkable(x, y)           │      │
│  └─────────────┘          │ - getCollisionAt(x, y)       │      │
│                           └──────────────────────────────┘      │
│                                      ▲                           │
│  EntityOccupancyMap                  │                           │
│  ┌─────────────────┐                 │                           │
│  │ Entity position │◄────────────────┤                           │
│  │ tracking        │                 │                           │
│  └─────────────────┘                 │                           │
│                                      │                           │
│  TileBehavior Registry               │                           │
│  ┌─────────────────┐                 │                           │
│  │ Strategy per    │◄────────────────┘                           │
│  │ CollisionType   │                                             │
│  └─────────────────┘                                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## CollisionType Enum (13 types)

| Type | Purpose | Current Behavior |
|------|---------|------------------|
| `NONE` | Walkable | Allows movement |
| `SOLID` | Walls | Blocks all movement |
| `LEDGE_DOWN/UP/LEFT/RIGHT` | One-way jumps | Returns `MoveResult.JUMP` in correct direction |
| `WATER` | Swimming | Returns `MovementModifier.SWIM` (needs ability check) |
| `TALL_GRASS` | Encounters | Returns `MovementModifier.ENCOUNTER` |
| `ICE` | Sliding | Returns `MovementModifier.SLIDE` |
| `SAND` | Slow terrain | Returns `MovementModifier.SLOW` |
| `WARP` | Scene transition | **Defined but NO trigger logic** |
| `DOOR` | Door interaction | **Defined but NO trigger logic** |
| `SCRIPT_TRIGGER` | Custom scripts | **Defined but NO trigger logic** |

---

## Data Flow: Movement Request

```
GridMovement.move(Direction.UP)
       │
       ▼
CollisionSystem.canMove(fromX, fromY, toX, toY, direction, entity)
       │
       ├──► EntityOccupancyMap.isOccupied(toX, toY, z, entity)
       │           │
       │           └──► If occupied → MoveResult.blockedByEntity()
       │
       ├──► CollisionMap.get(toX, toY, z) → CollisionType
       │
       └──► TileBehavior.checkMove(...) → MoveResult
                   │
                   └──► Contains: allowed, modifier, blockedReason
       │
       ▼
GridMovement applies MovementModifier (speed changes, etc.)
```

---

## Player Controller Integration

### How It Already Works

`GridMovement` is **already integrated** with the collision system. A player controller simply uses `GridMovement`:

```java
public class PlayerController extends Component {
    private GridMovement movement;
    
    @Override
    public void start() {
        movement = getComponent(GridMovement.class);
        if (movement == null) {
            movement = gameObject.addComponent(new GridMovement());
        }
    }
    
    @Override
    public void update(float deltaTime) {
        if (movement.isMoving()) return;
        
        // Input handling
        if (Input.isKeyPressed(GLFW.GLFW_KEY_W)) {
            movement.move(Direction.UP);
        } else if (Input.isKeyPressed(GLFW.GLFW_KEY_S)) {
            movement.move(Direction.DOWN);
        }
        // ... etc
    }
}
```

### What GridMovement Handles Automatically

1. **Tile collision** - Queries `CollisionSystem.canMove()`
2. **Entity collision** - Checks `EntityOccupancyMap` (if entity registered)
3. **Movement modifiers** - Applies speed changes from `MoveResult.modifier()`
4. **Position updates** - Calls `CollisionSystem.moveEntity()` when movement completes

### What's Missing for Full Integration

| Gap | Status | Solution |
|-----|--------|----------|
| Auto-register on `start()` | ❌ Not implemented | Add in `GridMovement.start()` |
| Auto-unregister on `destroy()` | ❌ Not implemented | Add in `GridMovement.onDestroy()` |
| Trigger callbacks (`onEnter`, `onExit`) | ❌ Not implemented | See Trigger Solutions |
| Movement modifier effects (ice sliding) | ⚠️ Partial | `MoveResult` returns modifier, but `GridMovement` doesn't fully handle sliding continuation |

---

## NPC Registration

### Current Mechanism

NPCs with `GridMovement` can be registered in `EntityOccupancyMap`:

```java
// Manual registration (current state)
CollisionSystem collision = scene.getCollisionSystem();
collision.registerEntity(npcGameObject, gridX, gridY, zLevel);

// When NPC moves
collision.moveEntity(npcGameObject, oldX, oldY, oldZ, newX, newY, newZ);

// When NPC removed
collision.unregisterEntity(npcGameObject, gridX, gridY, zLevel);
```

### Problem: Registration Is Manual

`GridMovement` does **not** auto-register. You must do it yourself or modify `GridMovement`:

```java
// GridMovement.java - PROPOSED FIX
@Override
public void start() {
    super.start();
    
    // Auto-register in EntityOccupancyMap
    if (gameObject != null && gameObject.getScene() != null) {
        CollisionSystem collision = gameObject.getScene().getCollisionSystem();
        if (collision != null) {
            collision.registerEntity(gameObject, gridX, gridY, zLevel);
        }
    }
}

@Override
public void onDestroy() {
    // Auto-unregister
    if (gameObject != null && gameObject.getScene() != null) {
        CollisionSystem collision = gameObject.getScene().getCollisionSystem();
        if (collision != null) {
            collision.unregisterEntity(gameObject, gridX, gridY, zLevel);
        }
    }
    super.onDestroy();
}
```

### NPC-to-NPC Collision Check

Once registered, collision is automatic:

```java
// In CollisionSystem.canMove()
if (entityOccupancyMap.isOccupied(toX, toY, toZ, movingEntity)) {
    return MoveResult.blockedByEntity();
}
```
