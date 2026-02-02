# Warp & Spawn System Guide

> **Summary:** The warp and spawn system lets you teleport the player between scenes or within the same scene using entity components. Place SpawnPoints as destinations, TriggerZones as activation areas, and WarpZones to wire them together.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Components](#components)
4. [Workflows](#workflows)
5. [Inspector Fields](#inspector-fields)
6. [Gizmo Visuals](#gizmo-visuals)
7. [Tips & Best Practices](#tips--best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Code Integration](#code-integration)
10. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create a warp between scenes | Add TriggerZone + WarpZone to an entity, set targetScene and targetSpawnId |
| Create a same-scene warp | Add TriggerZone + WarpZone, set targetSpawnId only (leave targetScene empty) |
| Mark a spawn location | Add SpawnPoint component, set a unique spawnId |
| Make the player persist across scenes | Add PersistentEntity component with entityTag "Player" |
| Disable fade transition | Uncheck "Use Fade" on WarpZone |
| Customize fade timing | Check "Override Transition Defaults" and set fadeOutDuration / fadeInDuration |

---

## Overview

The warp system replaces collision-map-based WARP and DOOR triggers with entity components. This gives you full inspector control over teleportation behavior — destinations, transitions, sounds, and camera bounds — without editing collision maps.

The system has four components that work together:

- **SpawnPoint** — Marks a destination tile. Each has a unique `spawnId` that WarpZones reference.
- **TriggerZone** — A non-blocking area that detects when entities step on its tiles. It fires callbacks but does not block movement.
- **WarpZone** — The orchestrator. It listens to a TriggerZone for player entry, then either teleports within the scene or loads a new scene via the transition system.
- **PersistentEntity** — Marks entities (typically the player) that should be preserved across scene transitions. Their component state is snapshotted before unload and restored after load.

The full chain: player steps on TriggerZone → WarpZone fires → SceneTransition → TransitionManager → SceneManager loads scene → player is placed at the matching SpawnPoint.

---

## Components

### SpawnPoint

Marks a spawn location. Place the entity where the player should appear. The tile position is derived from the entity's Transform.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Spawn Id | String | "" | Unique identifier referenced by WarpZones |
| Facing Direction | Direction | DOWN | Direction the player faces after spawning |
| Arrival Sound | AudioClip | null | Sound played when an entity arrives |
| Camera Bounds Id | String | "" | CameraBoundsZone to activate on arrival (leave empty for none) |

### TriggerZone

Detects entity entry on its tiles. Non-blocking — entities walk through freely.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Offset X | int | 0 | Horizontal offset from entity position (tiles) |
| Offset Y | int | 0 | Vertical offset from entity position (tiles) |
| Width | int | 1 | Trigger area width (tiles, minimum 1) |
| Height | int | 1 | Trigger area height (tiles, minimum 1) |
| One Shot | boolean | false | Fire only once, then deactivate |
| Player Only | boolean | true | Only trigger for the player entity |
| Elevation | int | 0 | Elevation level for trigger detection |

### WarpZone

Orchestrates teleportation. Requires a TriggerZone on the same entity (resolved via `@ComponentRef`).

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Target Scene | String | "" | Scene to load. **Empty = same-scene warp** |
| Target Spawn Id | String | "" | SpawnPoint ID in the destination scene |
| Show Destination Label | boolean | true | Show destination in editor gizmo |
| Warp Out Sound | AudioClip | null | Departure sound |
| Use Fade | boolean | true | Use fade transition effect |
| Override Transition Defaults | boolean | false | Use custom timing instead of global defaults |
| Fade Out Duration | float | 0.3 | Seconds for fade-out (only if overriding) |
| Fade In Duration | float | 0.3 | Seconds for fade-in (only if overriding) |
| Transition Name | String | "" | Named transition type (only if overriding) |

### PersistentEntity

Marks an entity for preservation across scene loads. Add this to the player and any companions.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| Entity Tag | String | "Player" | Identifier to match across scenes |

The `sourcePrefabId` field is auto-managed and hidden from the inspector.

---

## Workflows

### Setting Up a Cross-Scene Warp

1. **In the destination scene**, create an entity named e.g. "Spawn_FromForest"
2. Position it on the tile where the player should appear
3. Add a **SpawnPoint** component, set `spawnId` to `"from_forest"`
4. Set `facingDirection` to the direction the player should face on arrival
5. **In the source scene**, create an entity named e.g. "Warp_ToCave"
6. Position it on the tile the player walks onto to trigger the warp
7. Add a **TriggerZone** component (1x1 is typical for doorways)
8. Add a **WarpZone** component, set `targetScene` to `"cave"` and `targetSpawnId` to `"from_forest"`

### Setting Up a Same-Scene Warp

1. Create a SpawnPoint entity at the destination, set its `spawnId`
2. Create a TriggerZone + WarpZone entity at the activation tile
3. On the WarpZone, set `targetSpawnId` to the spawn ID — **leave `targetScene` empty**
4. The player will teleport within the scene (with optional fade)

### Making the Player Persist Across Scenes

1. Select the player entity
2. Add a **PersistentEntity** component
3. Set `entityTag` to `"Player"`
4. Every scene that the player can warp into should have its own player entity with the same PersistentEntity tag — the system will match them and apply the snapshot

This design keeps scenes self-contained: each scene can have its own player for editor testing. During gameplay, the persistent entity system overwrites the local player with the snapshotted state from the previous scene.

### Bidirectional Warps

For a two-way connection (e.g., forest ↔ cave):

**Forest scene:**
- SpawnPoint with `spawnId = "from_cave"` (where player appears when coming from cave)
- TriggerZone + WarpZone with `targetScene = "cave"`, `targetSpawnId = "from_forest"`

**Cave scene:**
- SpawnPoint with `spawnId = "from_forest"` (where player appears when coming from forest)
- TriggerZone + WarpZone with `targetScene = "forest"`, `targetSpawnId = "from_cave"`

### Adding Camera Bounds on Arrival

1. Ensure the destination scene has a **CameraBoundsZone** entity with a `boundsId`
2. On the SpawnPoint, set `cameraBoundsId` to match the CameraBoundsZone's `boundsId`
3. When the player arrives, camera bounds are automatically applied

---

## Inspector Fields

When you add a WarpZone, the inspector looks like:

```
+---------------------------------------------+
| v TriggerZone                          [X]  |
|   Offset X          [0]                     |
|   Offset Y          [0]                     |
|   Width              [1]                     |
|   Height             [1]                     |
|   One Shot           [ ]                     |
|   Player Only        [x]                     |
|   Elevation          [0]                     |
+---------------------------------------------+
| v WarpZone                             [X]  |
|   Target Scene       [cave]                  |
|   Target Spawn Id    [from_forest]           |
|   Show Destination.. [x]                     |
|   Warp Out Sound     [None]                  |
|   Use Fade           [x]                     |
|   Override Transi... [ ]                     |
|   Fade Out Duration  [0.3]                   |
|   Fade In Duration   [0.3]                   |
|   Transition Name    []                      |
|   --- Component References ---               |
|   * TriggerZone:     (sibling component)     |
+---------------------------------------------+
```

---

## Gizmo Visuals

| Component | Visual | Color |
|-----------|--------|-------|
| SpawnPoint | Blue diamond in a filled tile box, with spawn ID label above | Blue |
| TriggerZone | Yellow semi-transparent filled rectangle covering the tile area | Yellow |
| WarpZone | Purple double-ring portal icon with destination label | Purple |

SpawnPoints are always visible so they are easy to find when designing scene layouts. WarpZone labels show the destination as `-> targetScene:targetSpawnId` (or `-> targetSpawnId` for same-scene warps).

---

## Tips & Best Practices

- Use consistent spawn ID naming: `"from_<source_scene>"` makes it clear where the player came from
- Every scene the player can enter should have a player entity with PersistentEntity — the snapshot system overwrites it on arrival
- Keep WarpZone trigger areas to 1x1 for doorways and stairs; use wider areas for zone transitions
- Same-scene warps with fade give a clean visual effect for stairways or pit traps
- The "Override Transition Defaults" fields are ignored unless the checkbox is enabled — no need to clear them
- WarpZone only triggers for entities with a PersistentEntity component, so NPCs walking over a warp tile are unaffected
- Place SpawnPoints one tile away from WarpZones to avoid re-triggering the warp on arrival

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Player doesn't warp | Check that the player has a PersistentEntity component and that TriggerZone `playerOnly` isn't filtering it out |
| "Spawn point not found" in console | Verify the SpawnPoint's `spawnId` matches the WarpZone's `targetSpawnId` exactly (case-sensitive) |
| Player warps but appears at wrong position | Check SpawnPoint entity Transform position — spawn uses the entity's tile coordinates |
| No fade transition | Verify `useFade` is checked and SceneTransition is initialized (only works in play mode, not editor) |
| Player re-triggers warp on arrival | Move the SpawnPoint one tile away from any TriggerZone in the destination scene |
| Player state lost between scenes | Ensure PersistentEntity component is on the player in both scenes with the same `entityTag` |
| Camera doesn't change on arrival | Set `cameraBoundsId` on the SpawnPoint to match a CameraBoundsZone in the destination scene |
| Warp works for player but not companion | Add PersistentEntity to the companion with a unique `entityTag` (e.g., "Companion1") |

---

## Code Integration

For most cases, the inspector fields are sufficient. Code is needed when you want to trigger warps programmatically.

### Programmatic cross-scene warp

```java
// Warp to another scene with a transition
SceneTransition.loadScene("cave", "from_forest");

// Instant load (no fade)
SceneTransition.loadSceneInstant("cave", "from_forest");

// Custom transition timing
TransitionConfig config = TransitionConfig.builder()
    .fadeOutDuration(0.5f)
    .fadeInDuration(0.5f)
    .build();
SceneTransition.loadScene("cave", "from_forest", config);
```

### Programmatic same-scene teleport

```java
// Teleport player to a spawn point within the current scene
scene.teleportToSpawn("secret_area");
```

### Setting up a warp entity in code

```java
GameObject warp = new GameObject("Warp_ToCave");
warp.getTransform().setPosition(5, 10, 0);
warp.addComponent(new TriggerZone());

WarpZone warpZone = new WarpZone();
warpZone.setTargetScene("cave");
warpZone.setTargetSpawnId("from_forest");
warp.addComponent(warpZone);

scene.addGameObject(warp);
```

---

## Related

- [Interactable System Guide](interactableSystemGuide.md) — Doors, signs, chests (also use TriggerZone)
- [Collision Entities Guide](collisionEntitiesGuide.md) — TileEntityMap, entity-based collision
- [Camera Bounds Guide](cameraBoundsGuide.md) — CameraBoundsZone used by SpawnPoint
- [Play Mode Guide](playModeGuide.md) — Testing warps in the editor
