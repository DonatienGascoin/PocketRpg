# Save & Persistence Guide

> **Summary:** The persistence system manages game state across scene transitions and save/load cycles. It uses three distinct patterns depending on the type of data: write-through for immediate saves, scene-unload flush for position data, and ISaveable for per-entity state.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Core Concepts](#core-concepts)
4. [Persistence Patterns](#persistence-patterns)
5. [Scene Transition Flow](#scene-transition-flow)
6. [Code Integration](#code-integration)
7. [Tips & Best Practices](#tips--best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Initialize save system | `SaveManager.initialize(sceneManager)` at startup |
| Start new game | `SaveManager.newGame()` then load starting scene |
| Save game to disk | `SaveManager.save("slot1", "My Save")` |
| Load game from disk | `SaveManager.load("slot1")` then load saved scene |
| Store cross-scene data | `SaveManager.setGlobal("namespace", "key", value)` |
| Store per-scene flags | `SaveManager.setSceneFlag("boss_defeated", true)` |
| Make entity saveable | Add `PersistentId` component + implement `ISaveable` on components |
| Save player state immediately | `PlayerData.load()` → mutate → `data.save()` |
| Save player position on transition | Override `onBeforeSceneUnload()` in your component |
| Mark entity permanently destroyed | `SaveManager.markEntityDestroyed(persistentId)` |

---

## Overview

The persistence system has two layers:

1. **In-memory persistence** — State that survives scene transitions but not application restarts. This is what currently powers gameplay (player position, battle return, spawn teleport).
2. **Disk persistence** — Save slots written to disk that survive application restarts. Uses the same in-memory structures serialized to JSON files.

**Key principle:** Scene files (`.scene`) define the *initial* state. Save files store *deltas* from that initial state. This keeps saves small and allows scene updates without breaking existing saves.

**Save location:**
- Windows: `%APPDATA%/PocketRpg/saves/`
- Unix/Mac: `~/.pocketrpg/saves/`

---

## Core Concepts

### SaveManager

Static API that coordinates all persistence. Hooks into the scene lifecycle as a `SceneLifecycleListener`.

| Method | Purpose |
|--------|---------|
| `initialize(sceneManager)` | Set up save system at startup |
| `newGame()` | Clear all state for a fresh start |
| `save(slot, name)` | Write current state to disk |
| `load(slot)` | Read state from disk |
| `setGlobal(ns, key, val)` | Cross-scene key-value storage |
| `getGlobal(ns, key, default)` | Read cross-scene data |
| `setSceneFlag(key, val)` | Per-scene boolean/string flags |
| `markEntityDestroyed(id)` | Permanently remove entity from scene |

### PlayerData

Single source of truth for player state that survives scene transitions. Stored as JSON in SaveManager's global state under the `"player"` namespace.

| Field | Purpose |
|-------|---------|
| `lastOverworldScene` | Scene name for return transitions |
| `lastGridX`, `lastGridY` | Grid position (flushed on scene unload) |
| `lastDirection` | Facing direction |
| `returningFromBattle` | Flag checked by PlayerPlacementHandler |
| `playerName` | Player's chosen name |
| `money` | Currency (write-through) |

```java
// Reading and writing PlayerData
PlayerData data = PlayerData.load();   // Load from SaveManager (or fresh instance)
data.money += 100;
data.save();                           // Write back to SaveManager (in-memory)
```

### PersistentId Component

Marks a GameObject for entity-level persistence. Add this in the editor to any entity whose state should survive save/load.

| Field | Description |
|-------|-------------|
| `id` | Stable identifier (e.g., "chest_01"). Auto-generated if blank. |
| `persistenceTag` | Optional grouping tag (e.g., "chest", "enemy") |

On `onStart()`, the component registers itself with SaveManager. On `onDestroy()`, it unregisters.

### ISaveable Interface

Components implement this to participate in entity-level persistence.

| Method | Description |
|--------|-------------|
| `getSaveState()` | Return a map of data to save |
| `loadSaveState(state)` | Restore from a saved map |
| `hasSaveableState()` | Return false to skip saving (default: true) |

### PlayerPlacementHandler

A `SceneLifecycleListener` that handles player positioning after a scene loads. Runs during `onPostSceneInitialize` with two concerns in fixed order:

1. **Battle return** — If `PlayerData.returningFromBattle` is true, teleports the player to the saved grid position and clears the flag.
2. **Spawn teleport** — If a `spawnId` was provided for this scene load, teleports the player to the matching `SpawnPoint` entity (overwrites battle-return position).

The ordering is enforced inside the handler, not by listener registration order.

---

## Persistence Patterns

The system uses three distinct patterns. Choose based on your data type:

### 1. Write-Through

**When to use:** Data that must be immediately persisted (currency, inventory, quest flags).

```java
// Immediately save when value changes
PlayerData data = PlayerData.load();
data.money += rewardAmount;
data.save();  // Writes to SaveManager's in-memory global state
```

**Pros:** Simple, always up to date.
**Cons:** Many writes if called frequently.

### 2. Scene-Unload Flush (`onBeforeSceneUnload`)

**When to use:** Data that changes frequently but only matters at scene boundaries (player position).

```java
@Override
public void onBeforeSceneUnload() {
    PlayerData data = PlayerData.load();
    data.lastOverworldScene = gameObject.getScene().getName();
    data.lastGridX = movement.getGridX();
    data.lastGridY = movement.getGridY();
    data.lastDirection = movement.getFacingDirection();
    data.save();
}
```

**Pros:** One write per scene transition instead of every frame.
**Cons:** Data lost if application crashes mid-scene.

**Example:** `PlayerMovement` uses this pattern — position updates every frame via `GridMovement`, but only flushes to `PlayerData` when the scene unloads.

### 3. ISaveable (Per-Entity Per-Scene)

**When to use:** Entity state that must survive save/load cycles (NPC positions, chest opened state, enemy defeated).

```java
public class Chest extends Component implements ISaveable {
    private boolean opened = false;

    @Override
    public Map<String, Object> getSaveState() {
        return Map.of("opened", opened);
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        opened = (Boolean) state.getOrDefault("opened", false);
        if (opened) updateVisualToOpenState();
    }

    @Override
    public boolean hasSaveableState() {
        return opened;  // Only include in save if state changed
    }
}
```

**Requires:** `PersistentId` component on the same GameObject.
**Timing:** `loadSaveState()` is called during `onPostSceneInitialize`, after all `onStart()` calls complete.

### Pattern Selection Guide

| Data Type | Pattern | Example |
|-----------|---------|---------|
| Currency, inventory | Write-through | `PlayerData.save()` on mutation |
| Player position | Scene-unload flush | `onBeforeSceneUnload()` |
| NPC/entity state | ISaveable + PersistentId | `getSaveState()`/`loadSaveState()` |
| Quest progress | Global state | `SaveManager.setGlobal()` |
| Per-scene flags | Scene flags | `SaveManager.setSceneFlag()` |
| Dialogue events | Global state | `DialogueEventStore` wraps `SaveManager.setGlobal()` |

---

## Scene Transition Flow

Understanding the lifecycle is key to persistence:

```
Scene A unloading                    Scene B loading
──────────────────                   ──────────────────
1. notifyBeforeUnload()              4. initialize() (create objects)
   └─ onBeforeSceneUnload()          5. onStart() on all components
      on all components              6. applyCameraData()
2. destroy() (Scene A)               7. onPostSceneInitialize()
3. fireSceneUnloaded()                  ├─ SaveManager restores ISaveable state
                                        └─ PlayerPlacementHandler places player
                                     8. fireSceneLoaded()
```

**Step 1** is where `PlayerMovement` flushes position to `PlayerData`.
**Step 7** is where saved entity states are restored and the player is placed.

---

## Code Integration

### Global State (Cross-Scene)

```java
// Store progress that persists across all scenes
SaveManager.setGlobal("quests", "main_quest", "COMPLETED");
SaveManager.setGlobal("player", "level", 12);

// Retrieve with defaults
String quest = SaveManager.getGlobal("quests", "main_quest", "NOT_STARTED");
int level = SaveManager.getGlobal("player", "level", 1);
```

### Scene Flags (Per-Scene)

```java
// Mark scene-specific events
SaveManager.setSceneFlag("boss_defeated", true);

// Check on next visit
if (SaveManager.getSceneFlag("boss_defeated", false)) {
    // Boss stays dead
}
```

### Permanently Destroying Entities

```java
public void onEnemyKilled() {
    PersistentId pid = getComponent(PersistentId.class);
    if (pid != null) {
        SaveManager.markEntityDestroyed(pid.getId());
    }
    gameObject.destroy();
}
```

### Full Save/Load Cycle

```java
// Save current game
SaveManager.save("slot1", "Village - Level 5");

// Load a save
if (SaveManager.load("slot1")) {
    String sceneName = SaveManager.getSavedSceneName();
    SceneManager.loadScene(sceneName);
    // ISaveable states restored automatically during scene load
}

// List available saves
List<SaveSlotInfo> saves = SaveManager.listSaves();
for (SaveSlotInfo info : saves) {
    System.out.println(info.displayName() + " - " + info.sceneName());
}
```

---

## Tips & Best Practices

- **Pick the right pattern** — Write-through for immediate data, scene-unload for position, ISaveable for entity state. Don't mix patterns for the same data.
- **Only save what changes** — Use `hasSaveableState()` to skip entities with default state. Keeps saves small.
- **Use meaningful PersistentId values** — "chest_village_01" is easier to debug than auto-generated UUIDs.
- **Handle missing keys** — Use `getOrDefault()` in `loadSaveState()` for backwards compatibility with older saves.
- **Keep transient state transient** — Animation timers, cached calculations, and component references don't need saving.
- **Cast numbers safely** — Gson deserializes numbers generically. Use `((Number) value).intValue()` instead of direct casting.

### What to Save vs Not Save

| Save | Don't Save |
|------|------------|
| Player gold, items | Animation frame/timer |
| Grid position (via PlayerData) | Transform lerp progress |
| Quest/dialogue progress | Component references |
| Chest opened state | Default config values |
| Enemy defeated state | Static scenery state |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Entity state not restored after load | Ensure entity has `PersistentId` component with a unique ID |
| Component state not saved | Implement `ISaveable` interface on the component |
| Player not placed at spawn after transition | Verify `PlayerPlacementHandler` is registered in both `GameApplication` and `PlayModeController` |
| Player position not saved on scene change | Ensure component overrides `onBeforeSceneUnload()` and calls `PlayerData.save()` |
| Numbers wrong type after load | Use `((Number) value).intValue()` for safe Gson number conversion |
| Save file not created | Check `SaveManager.getSavesDirectory()` for path, ensure write permissions |
| ISaveable state applied too early | `loadSaveState()` runs after all `onStart()` — don't rely on it during `onStart()` |
| Battle return not working in editor | `PlayerPlacementHandler` must be registered in `PlayModeController` (not just `GameApplication`) |

---

## Related

- [Warp & Spawn Guide](warpSpawnGuide.md) — SpawnPoint component and scene transitions
- [Dialogue System Guide](dialogueSystemGuide.md) — `DialogueEventStore` uses `SaveManager` global state
- [Components Guide](componentsGuide.md) — Component lifecycle hooks including `onBeforeSceneUnload`
- [Play Mode Guide](playModeGuide.md) — Editor play mode initialization (where PlayerPlacementHandler also registers)
