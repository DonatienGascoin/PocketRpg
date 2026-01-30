# Save System Guide

> **Summary:** The save system persists game state (player progress, world changes) across sessions. It's separate from scene files - scenes define initial state, saves capture runtime changes.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Setup](#setup)
4. [Core Concepts](#core-concepts)
5. [Workflows](#workflows)
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
| Save game | `SaveManager.save("slot1", "My Save")` |
| Load game | `SaveManager.load("slot1")` then `SceneManager.loadScene(SaveManager.getSavedSceneName())` |
| Make entity saveable | Add `PersistentId` component + implement `ISaveable` on components |
| Store global data | `SaveManager.setGlobal("player", "gold", 500)` |
| Mark entity destroyed | `SaveManager.markEntityDestroyed(persistentId)` |

---

## Overview

The save system captures runtime changes to your game world:

- **Player position and state** - Where the player is, their health, inventory
- **World changes** - Opened chests, defeated enemies, triggered events
- **Global progress** - Quest states, unlocked areas, statistics

**Key principle:** Scene files define the *initial* state of a level. Save files store *changes* from that initial state (delta approach). This keeps saves small and allows scene updates without breaking existing saves.

**Save location:**
- Windows: `%APPDATA%/PocketRpg/saves/`
- Unix/Mac: `~/.pocketrpg/saves/`

---

## Setup

### 1. Initialize at Game Startup

In your game's initialization (e.g., `GameApplication`):

```java
@Override
public void initialize() {
    // After creating SceneManager
    SaveManager.initialize(sceneManager);
}
```

### 2. Add PersistentId to Saveable Entities

In the editor, add the `PersistentId` component to any entity that needs to persist:
- Player character
- Chests that can be opened
- NPCs with dialogue state
- Enemies that stay dead
- Collectibles that don't respawn

Set the `id` field to a meaningful name (e.g., "player", "chest_01") or leave blank for auto-generation.

### 3. Implement ISaveable on Components

Components that need custom save logic must implement `ISaveable`:

```java
public class Inventory extends Component implements ISaveable {
    private int gold;
    private List<String> items;

    @Override
    public Map<String, Object> getSaveState() {
        return Map.of(
            "gold", gold,
            "items", new ArrayList<>(items)
        );
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        gold = ((Number) state.get("gold")).intValue();
        items = new ArrayList<>((List<String>) state.get("items"));
    }
}
```

---

## Core Concepts

### PersistentId Component

Marks a GameObject as saveable with a stable identifier.

| Field | Description |
|-------|-------------|
| `id` | Unique identifier (e.g., "player", "chest_01"). Auto-generated if blank. |
| `persistenceTag` | Optional grouping tag (e.g., "chest", "enemy") |

### ISaveable Interface

Components implement this to save/load custom state.

| Method | Description |
|--------|-------------|
| `getSaveState()` | Return map of data to save |
| `loadSaveState(state)` | Restore from saved map |
| `hasSaveableState()` | Return false to skip saving (default: true) |

### Global vs Scene State

| Type | Use For | Example |
|------|---------|---------|
| **Global** | Data that persists across all scenes | Player gold, quest progress, settings |
| **Scene** | Data specific to one scene | Opened chests, defeated enemies |

---

## Workflows

### New Game Flow

```java
// Player clicks "New Game"
SaveManager.newGame();
SceneManager.loadScene("IntroScene");
```

### Save Game Flow

```java
// Player opens pause menu, clicks Save
if (SaveManager.save("slot1", "Village - Level 5")) {
    StatusBar.showMessage("Game saved!");
}
```

### Load Game Flow

```java
// Player selects a save slot
if (SaveManager.load("slot1")) {
    String sceneName = SaveManager.getSavedSceneName();
    SceneManager.loadScene(sceneName);
    // State automatically restored via PersistentId components
}
```

### Permanently Destroying an Entity

When the player kills an enemy or collects a one-time pickup:

```java
public void onEnemyKilled() {
    PersistentId pid = getComponent(PersistentId.class);
    if (pid != null) {
        SaveManager.markEntityDestroyed(pid.getId());
    }
    gameObject.destroy();
}
```

---

## Code Integration

### Example: Health Component

```java
public class Health extends Component implements ISaveable {
    private float maxHealth = 100;

    @HideInInspector
    private float currentHealth;

    @Override
    protected void onStart() {
        if (currentHealth <= 0) {
            currentHealth = maxHealth;  // Initialize if not loaded
        }
    }

    @Override
    public Map<String, Object> getSaveState() {
        return Map.of(
            "currentHealth", currentHealth,
            "maxHealth", maxHealth
        );
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        currentHealth = ((Number) state.get("currentHealth")).floatValue();
        maxHealth = ((Number) state.get("maxHealth")).floatValue();
    }
}
```

### Example: Chest Component

```java
public class Chest extends Component implements ISaveable {
    private boolean opened = false;
    private boolean looted = false;

    public void interact(Inventory playerInventory) {
        if (!opened) {
            opened = true;
            // Play animation, give loot...
            looted = true;
        }
    }

    @Override
    public Map<String, Object> getSaveState() {
        return Map.of("opened", opened, "looted", looted);
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        opened = (Boolean) state.getOrDefault("opened", false);
        looted = (Boolean) state.getOrDefault("looted", false);
        if (opened) updateVisualToOpenState();
    }

    @Override
    public boolean hasSaveableState() {
        return opened;  // Only save if state changed
    }
}
```

### Example: Using Global State

```java
// Store player stats globally (persist across scenes)
SaveManager.setGlobal("player", "gold", 500);
SaveManager.setGlobal("player", "level", 12);
SaveManager.setGlobal("quests", "main_quest", "COMPLETED");

// Retrieve with defaults
int gold = SaveManager.getGlobal("player", "gold", 0);
String questState = SaveManager.getGlobal("quests", "main_quest", "NOT_STARTED");
```

### Example: Scene Flags

```java
// Mark scene-specific events
SaveManager.setSceneFlag("boss_defeated", true);
SaveManager.setSceneFlag("secret_door_opened", true);

// Check flags
if (SaveManager.getSceneFlag("boss_defeated", false)) {
    // Boss stays dead
}
```

---

## Tips & Best Practices

- **Only save what changes** - Don't save configuration values that come from prefabs/scene files
- **Use `hasSaveableState()`** - Return false when entity hasn't been modified to keep saves small
- **Use meaningful IDs** - "player", "chest_village_01" are better than auto-generated UUIDs for debugging
- **Handle missing keys** - Use `getOrDefault()` in `loadSaveState()` for backwards compatibility
- **Test save/load cycle** - Save, close game, load, verify all state restored correctly
- **Keep runtime state transient** - Animation timers, cached values don't need saving

### What to Save vs Not Save

| Save | Don't Save |
|------|------------|
| Player gold, items | Animation frame/timer |
| Current health | Cached calculations |
| Quest progress | Component references (@ComponentRef) |
| Chest opened state | Default config values |
| Entity position (if moved) | Static scenery state |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Entity state not restored | Ensure entity has `PersistentId` component with ID set |
| Component state not saved | Implement `ISaveable` interface on the component |
| Numbers wrong type after load | Use `((Number) value).intValue()` for safe conversion |
| Save file not created | Check `SaveManager.getSavesDirectory()` for path, ensure write permissions |
| Entity destroyed on load but shouldn't be | Check if `markEntityDestroyed()` was called; entity ID might be in `destroyedEntities` |

---

## Related

- [Asset Loader Guide](asset-loader-guide.md) - Loading assets referenced in saves
- [Animation Editor Guide](animation-editor-guide.md) - Animation state is NOT saved (transient)
