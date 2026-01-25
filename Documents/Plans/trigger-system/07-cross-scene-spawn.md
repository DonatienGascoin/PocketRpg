# Cross-Scene Spawn System Implementation

## Overview

When a player uses a WARP or DOOR trigger to travel to another scene, they should arrive at a specific spawn point in the target scene. Currently, the trigger data stores `targetScene` and `targetSpawnId`, but SceneManager has no mechanism to pass the spawn point to the loaded scene.

**Problem**: `Scene.loadSceneWithSpawn()` calls `SceneTransition.loadScene(sceneName)` but cannot pass the spawn ID. The target scene doesn't know where to teleport the player.

**Solution**: Add spawn point passing through the entire scene loading chain: SceneTransition → TransitionManager → SceneManager → Scene.

---

## Current State Analysis

### What Works (Editor)

| Component | Status | Notes |
|-----------|--------|-------|
| `SceneUtils.getSpawnPoints(sceneName)` | Complete | Loads scene file, extracts spawn IDs |
| `TriggerInspector` cross-scene dropdown | Complete | Shows spawn points from target scene |
| `WarpTriggerData.targetSpawnId` | Complete | Stores target spawn ID |
| `DoorTriggerData.targetSpawnId` | Complete | Stores target spawn ID |

### What Works (Runtime)

| Component | Status | Notes |
|-----------|--------|-------|
| `Scene.findSpawnPoint(spawnId)` | Complete | Finds spawn in current scene |
| `Scene.teleportToSpawn(entity, spawnId)` | Complete | Same-scene teleport works |
| `WarpHandler` / `DoorHandler` | Partial | Calls `loadSceneWithSpawn` but spawn is lost |

### What's Missing

| Component | Gap |
|-----------|-----|
| `Scene.loadSceneWithSpawn()` | Calls `SceneTransition.loadScene()` without spawn ID |
| `SceneTransition` | No `loadScene(scene, spawnId)` overload |
| `TransitionManager` | No spawn ID storage/passing |
| `SceneManager` | No spawn ID support in `loadScene()` |
| `Scene` / `RuntimeScene` | No callback to teleport player after scene load |
| Player identification | No standard way to find "the player" entity |

---

## Architecture Design

### Data Flow

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           TRIGGER ACTIVATION                                  │
│                                                                              │
│  WarpHandler.onWarp(entity, "cave", "entrance", data)                        │
│         │                                                                    │
│         ▼                                                                    │
│  Scene.loadSceneWithSpawn("cave", "entrance")                                │
│         │                                                                    │
│         ▼                                                                    │
│  SceneTransition.loadScene("cave", "entrance")  ← NEW OVERLOAD               │
│         │                                                                    │
│         ▼                                                                    │
│  TransitionManager.startTransition("cave", "entrance")  ← NEW OVERLOAD       │
│         │                                                                    │
│         │ (stores pendingSpawnId = "entrance")                               │
│         │                                                                    │
│         ▼ (at midpoint)                                                      │
│  SceneManager.loadScene("cave", "entrance")  ← NEW OVERLOAD                  │
│         │                                                                    │
│         │ (stores pendingSpawnId = "entrance")                               │
│         │                                                                    │
│         ▼ (after scene.initialize())                                         │
│  scene.onSceneReady(pendingSpawnId)  ← NEW CALLBACK                          │
│         │                                                                    │
│         ▼                                                                    │
│  RuntimeScene finds player, calls teleportToSpawn(player, "entrance")        │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Player Identification Strategy

The system needs to find "the player" entity in the loaded scene. Options:

**Option A: Tag-based (Recommended)**
```java
// Player has a "Player" tag
GameObject player = scene.findGameObjectWithTag("Player");
```

**Option B: Component-based**
```java
// Player has a PlayerController component
PlayerController pc = scene.findComponent(PlayerController.class);
GameObject player = pc.getGameObject();
```

**Option C: Name-based (Simplest, but fragile)**
```java
GameObject player = scene.findGameObject("Player");
```

**Recommendation**: Use **Option A (tag-based)** as it's flexible and explicit. Add a simple tag system to GameObject if not present.

---

## Phase 1: Core Infrastructure

### 1.1 Add Tag System to GameObject

Add simple tag support for entity identification:

```java
// GameObject.java
private final Set<String> tags = new HashSet<>();

public void addTag(String tag) { tags.add(tag); }
public void removeTag(String tag) { tags.remove(tag); }
public boolean hasTag(String tag) { return tags.contains(tag); }
public Set<String> getTags() { return Collections.unmodifiableSet(tags); }
```

### 1.2 Add Tag Search to Scene

```java
// Scene.java
public GameObject findGameObjectWithTag(String tag) {
    for (GameObject go : gameObjects) {
        if (go.hasTag(tag)) return go;
    }
    return null;
}

public List<GameObject> findGameObjectsWithTag(String tag) {
    return gameObjects.stream()
        .filter(go -> go.hasTag(tag))
        .collect(Collectors.toList());
}
```

### 1.3 Serialize Tags

Update `GameObjectData` and serialization to include tags:

```java
// GameObjectData.java
private List<String> tags;

// Serialization
gameObjectData.setTags(new ArrayList<>(gameObject.getTags()));

// Deserialization
if (data.getTags() != null) {
    data.getTags().forEach(gameObject::addTag);
}
```

### Files to Change (Phase 1)

| File | Change |
|------|--------|
| `core/GameObject.java` | Add tag methods |
| `scenes/Scene.java` | Add `findGameObjectWithTag()` |
| `serialization/GameObjectData.java` | Add tags field |
| `serialization/SceneSerializer.java` | Serialize/deserialize tags |
| `editor/scene/EditorGameObject.java` | Add tag support (mirror GameObject) |

---

## Phase 2: SceneManager Spawn Support

### 2.1 Add Spawn ID to SceneManager

```java
// SceneManager.java
private String pendingSpawnId;

/**
 * Loads a scene and teleports player to spawn point.
 */
public void loadScene(String sceneName, String spawnId) {
    this.pendingSpawnId = spawnId;
    loadScene(sceneName);
}

// In loadScene(Scene scene), after initialize:
private void loadScene(Scene scene) {
    // ... existing code ...

    currentScene = scene;
    currentScene.initialize(viewportConfig, renderingConfig);

    // NEW: Notify scene of pending spawn
    if (pendingSpawnId != null) {
        currentScene.onSceneReady(pendingSpawnId);
        pendingSpawnId = null;
    }

    // ... rest of method ...
}
```

### 2.2 Add onSceneReady Callback to Scene

```java
// Scene.java
/**
 * Called after scene is fully loaded and initialized.
 * Override to handle spawn point teleportation.
 *
 * @param spawnId Spawn point to teleport player to, or null
 */
public void onSceneReady(String spawnId) {
    if (spawnId == null || spawnId.isBlank()) {
        return;
    }

    // Find player by tag
    GameObject player = findGameObjectWithTag("Player");
    if (player == null) {
        System.err.println("[Scene] No entity with 'Player' tag found for spawn teleport");
        return;
    }

    teleportToSpawn(player, spawnId);
}
```

### Files to Change (Phase 2)

| File | Change |
|------|--------|
| `scenes/SceneManager.java` | Add `loadScene(name, spawnId)`, pendingSpawnId field |
| `scenes/Scene.java` | Add `onSceneReady(spawnId)` callback |

---

## Phase 3: TransitionManager Spawn Support

### 3.1 Add Spawn ID to TransitionManager

```java
// TransitionManager.java
private String targetSpawnId;

/**
 * Starts transition with spawn point.
 */
public void startTransition(String sceneName, String spawnId) {
    startTransition(sceneName, spawnId, defaultConfig);
}

public void startTransition(String sceneName, String spawnId, TransitionConfig config) {
    // ... existing validation ...

    this.targetSceneName = sceneName;
    this.targetSpawnId = spawnId;  // NEW
    this.currentTransition = createTransition(config);
    // ...
}

// In performSceneSwitch():
private void performSceneSwitch() {
    try {
        if (targetSpawnId != null) {
            sceneManager.loadScene(targetSceneName, targetSpawnId);
        } else {
            sceneManager.loadScene(targetSceneName);
        }
    } catch (Exception e) {
        // ...
    }
}

// In completeTransition():
private void completeTransition() {
    // ...
    targetSpawnId = null;  // Clear spawn ID
}
```

### Files to Change (Phase 3)

| File | Change |
|------|--------|
| `scenes/transitions/TransitionManager.java` | Add spawn ID support |

---

## Phase 4: SceneTransition Static API

### 4.1 Add Spawn ID Overload

```java
// SceneTransition.java (static utility)
public static void loadScene(String sceneName, String spawnId) {
    if (transitionManager == null) {
        throw new IllegalStateException("SceneTransition not initialized");
    }
    transitionManager.startTransition(sceneName, spawnId);
}

// Keep existing for backwards compatibility
public static void loadScene(String sceneName) {
    loadScene(sceneName, null);
}
```

### 4.2 Update Scene.loadSceneWithSpawn

```java
// Scene.java
protected void loadSceneWithSpawn(String sceneName, String targetSpawn) {
    if (!SceneTransition.isInitialized()) {
        System.err.println("[Scene] SceneTransition not initialized");
        return;
    }

    // Now properly passes spawn ID through the chain
    SceneTransition.loadScene(sceneName, targetSpawn);
}
```

### Files to Change (Phase 4)

| File | Change |
|------|--------|
| `scenes/transitions/SceneTransition.java` | Add `loadScene(name, spawnId)` |
| `scenes/Scene.java` | Update `loadSceneWithSpawn()` to use new API |

---

## Phase 5: Editor Integration

### 5.1 Tag Editor in Inspector

Add UI for editing entity tags in InspectorPanel:

```java
// In InspectorPanel entity section
private void renderTagsSection(EditorGameObject entity) {
    ImGui.text("Tags");

    // Show existing tags as removable chips
    for (String tag : entity.getTags()) {
        ImGui.sameLine();
        if (ImGui.smallButton(tag + " x##tag_" + tag)) {
            entity.removeTag(tag);
            scene.markDirty();
        }
    }

    // Add new tag input
    ImGui.setNextItemWidth(100);
    if (ImGui.inputText("##newTag", newTagBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
        String newTag = newTagBuffer.get().trim();
        if (!newTag.isEmpty()) {
            entity.addTag(newTag);
            newTagBuffer.set("");
            scene.markDirty();
        }
    }
    ImGui.sameLine();
    if (ImGui.smallButton("Add")) {
        // Same logic
    }
}
```

### 5.2 Quick "Player" Tag Button

For convenience, add a quick button to mark entity as player:

```java
if (!entity.hasTag("Player")) {
    if (ImGui.smallButton("Mark as Player")) {
        entity.addTag("Player");
        scene.markDirty();
    }
}
```

### Files to Change (Phase 5)

| File | Change |
|------|--------|
| `editor/panels/InspectorPanel.java` | Add tags section |
| `editor/scene/EditorGameObject.java` | Ensure tag support matches GameObject |

---

## Phase 6: Testing and Validation

### Manual Testing Checklist

- [ ] Create scene A with WARP to scene B, spawn "entrance"
- [ ] Create scene B with SPAWN_POINT "entrance" at (5, 5)
- [ ] Add Player entity with "Player" tag in scene A
- [ ] Enter play mode, trigger warp
- [ ] Verify player appears at (5, 5) in scene B
- [ ] Test same-scene warp still works
- [ ] Test door with destination works
- [ ] Test warp without spawn ID (should load scene at default position)
- [ ] Test missing spawn ID warning

### Edge Cases

- [ ] Target spawn point doesn't exist → Log warning, load at origin
- [ ] No "Player" tag in scene → Log warning, skip teleport
- [ ] Empty spawn ID → Skip teleport (backwards compatible)
- [ ] Scene transition interrupted → Spawn ID cleared properly

### Files to Change (Phase 6)

| File | Change |
|------|--------|
| `components/SceneTransitionTest.java` | Update test component |

---

## Files Summary

### New Files (0)

No new files needed - this extends existing infrastructure.

### Modified Files (9)

| File | Changes |
|------|---------|
| `core/GameObject.java` | Add tag methods |
| `scenes/Scene.java` | Add `findGameObjectWithTag()`, `onSceneReady()` |
| `scenes/SceneManager.java` | Add `loadScene(name, spawnId)`, pendingSpawnId |
| `scenes/transitions/TransitionManager.java` | Add spawn ID support |
| `scenes/transitions/SceneTransition.java` | Add `loadScene(name, spawnId)` |
| `serialization/GameObjectData.java` | Add tags field |
| `serialization/SceneSerializer.java` | Serialize/deserialize tags |
| `editor/scene/EditorGameObject.java` | Add tag support |
| `editor/panels/InspectorPanel.java` | Add tags editor section |

---

## Implementation Order

1. **Phase 1**: Tag system (GameObject, Scene, serialization)
2. **Phase 2**: SceneManager spawn support
3. **Phase 3**: TransitionManager spawn support
4. **Phase 4**: SceneTransition static API + Scene.loadSceneWithSpawn update
5. **Phase 5**: Editor tag UI
6. **Phase 6**: Testing and validation

---

## Future Enhancements

### Default Spawn Point

Allow scenes to define a "default" spawn point used when no specific spawn is requested:

```java
// Scene metadata or first spawn point with id="default"
String defaultSpawnId = "default";
```

### Spawn Point Validation in Editor

- Warning when warp references non-existent spawn point
- Auto-complete for spawn point IDs
- Visual connection lines between warps and spawns

### Facing Direction on Spawn

Currently logged but not applied. When spawning:
```java
if (spawn.facingDirection() != null && movement != null) {
    movement.setFacingDirection(spawn.facingDirection());
}
```

Requires `GridMovement.setFacingDirection()` implementation.
