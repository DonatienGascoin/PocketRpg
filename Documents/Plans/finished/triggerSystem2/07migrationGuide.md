# Phase 7: Migration Guide

## Overview

This phase covers migrating from the collision map DOOR trigger to the entity-based Door component.

---

## What's Changing

### Before: DOOR in Collision Map

```
CollisionType.DOOR (tile in collision map)
    +
DoorTriggerData (in TriggerDataMap)
    - locked
    - requiredKey
    - consumeKey
    - lockedMessage
    - targetScene
    - targetSpawnId
    - transition
```

**Limitations**:
- No visual feedback (can't show open/closed)
- No animation support
- No audio support
- Coupled to tilemap visuals (fragile)

### After: Door Entity (GameObject)

```
GameObject with:
    - SpriteRenderer (visual)
    - AudioSource (sounds)
    - Door component (behavior)
        - All DoorTriggerData properties
        - Open/closed state
        - Collision blocking
```

**Benefits**:
- Self-contained entity
- Full visual/audio support
- Works with prefab system
- Flexible positioning

---

## Migration Approach

Remove collision-map triggers and replace with entities.

**Steps**:
1. Remove `DOOR`, `WARP`, `SPAWN_POINT` from `CollisionType` enum
2. Remove `DoorTriggerData`, `WarpTriggerData`, `SpawnPointData` classes
3. Remove `DoorHandler`, `WarpHandler` classes
4. Update `CollisionTypeSelector` to not show removed types
5. Migrate existing scenes to use entity components

---

## Scene Migration Steps

### 1. Identify DOOR Tiles

Find all scenes using DOOR collision type:

```java
// Migration script
for (String sceneName : SceneUtils.getAvailableSceneNames()) {
    SceneData data = loadSceneData(sceneName);
    CollisionMap map = data.getCollisionMap();

    for (TileCoord tile : findAllTiles(map, CollisionType.DOOR)) {
        System.out.println(sceneName + ": DOOR at " + tile);
    }
}
```

### 2. Create Door Entities

For each DOOR tile:

1. Get DoorTriggerData from TriggerDataMap
2. Create new GameObject at tile position
3. Add SpriteRenderer with door sprite
4. Add AudioSource
5. Add Door component with migrated properties

```java
public GameObject migrateDoorTile(TileCoord tile, DoorTriggerData data, Scene scene) {
    GameObject doorEntity = new GameObject("Door_" + tile.x() + "_" + tile.y());

    // Position at tile center
    doorEntity.getTransform().setPosition(tile.x() + 0.5f, tile.y() + 0.5f, 0);

    // Add sprite renderer
    SpriteRenderer sr = new SpriteRenderer();
    sr.setSprite(getDefaultDoorSprite());
    doorEntity.addComponent(sr);

    // Add audio source
    doorEntity.addComponent(new AudioSource());

    // Add door component
    Door door = new Door();
    door.setLocked(data.locked());
    door.setRequiredKey(data.requiredKey());
    door.setConsumeKey(data.consumeKey());
    door.setLockedMessage(data.lockedMessage());
    door.setTargetScene(data.targetScene());
    door.setTargetSpawnId(data.targetSpawnId());
    door.setBlockedTiles(List.of(new TileCoord(0, 0, tile.elevation())));
    doorEntity.addComponent(door);

    // Add to scene
    scene.addGameObject(doorEntity);

    return doorEntity;
}
```

### 3. Remove DOOR from Collision Map

```java
// Clear DOOR tiles (Door component now handles collision)
for (TileCoord tile : doorTiles) {
    collisionMap.set(tile.x(), tile.y(), tile.elevation(), CollisionType.NONE);
    triggerDataMap.remove(tile.x(), tile.y(), tile.elevation());
}
```

### 4. Update Tilemap (If Needed)

If tilemap had door tiles synced with collision:
- Keep tilemap door tile as visual reference, OR
- Remove tilemap tile (Door entity now provides visual)

---

## Code Changes

### Remove from CollisionType.java

```java
// Remove this line:
DOOR(11, "Door", CollisionCategory.TRIGGER, "Locked door", true, MaterialIcons.Door),
```

### Remove DoorTriggerData

Delete or deprecate:
- `collision/trigger/DoorTriggerData.java`

### Remove DoorHandler

Delete or deprecate:
- `collision/trigger/handlers/DoorHandler.java`

### Update Scene.java

Remove DoorHandler registration:

```java
// Remove from registerDefaultTriggerHandlers():
// triggerSystem.registerHandler(DoorTriggerData.class, new DoorHandler(...));
```

### Update CollisionTypeSelector

Remove DOOR from UI if using Option A.

---

## Testing Migration

### Before Migration

1. Load scene with DOOR tiles
2. Verify doors work (lock, unlock, teleport)
3. Note door positions and configurations

### After Migration

1. Load migrated scene
2. Verify Door entities exist at correct positions
3. Verify door interactions work:
   - Open/close animation (new!)
   - Sound effects (new!)
   - Lock/unlock
   - Teleportation
4. Verify collision blocking works

### Regression Checklist

- [ ] Locked doors require key
- [ ] Keys consumed when configured
- [ ] Locked message displays
- [ ] Same-scene teleport works
- [ ] Cross-scene teleport works
- [ ] Door blocks movement when closed
- [ ] Door allows movement when open

---

## What Stays in Collision Map

Only STAIRS remains collision-map-based:

| Type | Status | Reason |
|------|--------|--------|
| STAIRS | ✅ Keep | Complex direction-based elevation change, ON_EXIT activation |
| WARP | ❌ Remove | Becomes TriggerZone + WarpZone entity |
| SPAWN_POINT | ❌ Remove | Becomes SpawnPoint entity |
| DOOR | ❌ Remove | Becomes Door entity |

**Why STAIRS stays:**
- Direction-dependent elevation changes (N:+1, S:-1)
- ON_EXIT activation (not ON_ENTER)
- Tightly coupled with GridMovement elevation system
- No visual representation needed

---

## WARP Migration

WARP tiles become TriggerZone + WarpZone entities:

```java
// Old: Collision map
CollisionType.WARP at (5, 10)
WarpTriggerData { targetScene: "cave", targetSpawnId: "entrance" }

// New: Entity
GameObject warp = new GameObject("Warp_To_Cave");
warp.getTransform().setPosition(5, 10, 0);
warp.addComponent(new TriggerZone());
warp.addComponent(new WarpZone("cave", "entrance"));
```

## SPAWN_POINT Migration

SPAWN_POINT tiles become SpawnPoint entities:

```java
// Old: Collision map
CollisionType.SPAWN_POINT at (0, 0)
SpawnPointData { id: "entrance" }

// New: Entity
GameObject spawn = new GameObject("Spawn_Entrance");
spawn.getTransform().setPosition(0, 0, 0);
spawn.addComponent(new SpawnPoint("entrance"));
```

---

## Summary

| Item | Action |
|------|--------|
| `DOOR` CollisionType | ❌ Remove → Door entity |
| `WARP` CollisionType | ❌ Remove → TriggerZone + WarpZone entity |
| `SPAWN_POINT` CollisionType | ❌ Remove → SpawnPoint entity |
| `STAIRS` CollisionType | ✅ Keep (with StairsData + StairsHandler) |
| `DoorTriggerData` | ❌ Remove |
| `WarpTriggerData` | ❌ Remove |
| `SpawnPointData` | ❌ Remove |
| `StairsData` | ✅ Keep |
| `DoorHandler` | ❌ Remove |
| `WarpHandler` | ❌ Remove |
| `StairsHandler` | ✅ Keep |
| `TriggerDataMap` | ✅ Keep (for StairsData only) |
| `TriggerSystem` | ✅ Keep (for STAIRS only) |
| `EntityOccupancyMap` | 🔄 Extend → TileEntityMap |
