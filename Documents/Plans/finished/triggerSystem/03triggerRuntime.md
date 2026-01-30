# Trigger Runtime System

## Overview

The runtime system handles:
1. Detecting when entities enter/exit trigger tiles
2. Dispatching events to appropriate handlers
3. Executing trigger logic (warp, door, stairs)

---

## TriggerSystem

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerSystem.java`

```java
/**
 * Runtime system for handling trigger activation and dispatch.
 * <p>
 * Integrates with GridMovement to detect tile enter/exit events
 * and dispatches to registered handlers.
 */
public class TriggerSystem {

    private final TriggerDataMap triggerDataMap;
    private final CollisionMap collisionMap;
    private final Map<Class<? extends TriggerData>, TriggerHandler<?>> handlers;

    public TriggerSystem(TriggerDataMap triggerDataMap, CollisionMap collisionMap) {
        this.triggerDataMap = triggerDataMap;
        this.collisionMap = collisionMap;
        this.handlers = new HashMap<>();
    }

    /**
     * Registers a handler for a trigger data type.
     */
    public <T extends TriggerData> void registerHandler(Class<T> type, TriggerHandler<T> handler) {
        handlers.put(type, handler);
    }

    /**
     * Called when an entity enters a tile.
     * Checks if tile is a trigger with ON_ENTER activation.
     */
    public void onTileEnter(GameObject entity, int x, int y, int elevation) {
        CollisionType type = collisionMap.get(x, y, elevation);
        if (!type.isTrigger()) return;

        TriggerData data = triggerDataMap.get(x, y, elevation);
        if (data == null) {
            // Trigger tile without configuration - log warning
            System.err.println("Trigger tile at (" + x + ", " + y + ", elev=" + elevation +
                              ") has no configuration");
            return;
        }

        if (data.activationMode() != ActivationMode.ON_ENTER) return;
        if (data.playerOnly() && !isPlayer(entity)) return;

        fireTrigger(data, entity, x, y, elevation);
    }

    /**
     * Called when an entity exits a tile.
     * Checks if tile is a trigger with ON_EXIT activation.
     *
     * @param exitDirection The direction the entity is moving (NORTH, SOUTH, EAST, WEST)
     */
    public void onTileExit(GameObject entity, int x, int y, int elevation, Direction exitDirection) {
        CollisionType type = collisionMap.get(x, y, elevation);
        if (!type.isTrigger()) return;

        TriggerData data = triggerDataMap.get(x, y, elevation);
        if (data == null) return;

        if (data.activationMode() != ActivationMode.ON_EXIT) return;
        if (data.playerOnly() && !isPlayer(entity)) return;

        fireTrigger(data, entity, x, y, elevation, exitDirection);
    }

    /**
     * Called when player presses interact.
     * Checks tile player is on and tile player is facing.
     *
     * @return true if a trigger was activated
     */
    public boolean tryInteract(GameObject player, int x, int y, int elevation, Direction facing) {
        // Check tile player is standing on
        if (tryInteractAt(player, x, y, elevation)) {
            return true;
        }

        // Check tile player is facing
        int facingX = x + facing.getDx();
        int facingY = y + facing.getDy();
        return tryInteractAt(player, facingX, facingY, elevation);
    }

    private boolean tryInteractAt(GameObject player, int x, int y, int elevation) {
        CollisionType type = collisionMap.get(x, y, elevation);
        if (!type.isTrigger()) return false;

        TriggerData data = triggerDataMap.get(x, y, elevation);
        if (data == null) return false;
        if (data.activationMode() != ActivationMode.ON_INTERACT) return false;

        fireTrigger(data, player, x, y, elevation);
        return true;
    }

    /**
     * Dispatches trigger to registered handler (for ON_ENTER and ON_INTERACT).
     */
    private void fireTrigger(TriggerData data, GameObject entity, int x, int y, int elevation) {
        fireTrigger(data, entity, x, y, elevation, null);
    }

    /**
     * Dispatches trigger to registered handler with optional exit direction.
     */
    @SuppressWarnings("unchecked")
    private void fireTrigger(TriggerData data, GameObject entity, int x, int y, int elevation,
                             Direction exitDirection) {
        TriggerHandler<TriggerData> handler =
            (TriggerHandler<TriggerData>) handlers.get(data.getClass());

        if (handler == null) {
            System.err.println("No handler for trigger type: " + data.getClass().getSimpleName());
            return;
        }

        TriggerContext context = new TriggerContext(entity, x, y, elevation, data, exitDirection);
        handler.handle(context);
    }

    private boolean isPlayer(GameObject entity) {
        return entity.hasTag("Player");
    }
}
```

---

## TriggerHandler Interface

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerHandler.java`

```java
/**
 * Handler for a specific trigger data type.
 */
@FunctionalInterface
public interface TriggerHandler<T extends TriggerData> {

    /**
     * Handles trigger activation.
     */
    void handle(TriggerContext context);
}
```

---

## TriggerContext

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerContext.java`

```java
/**
 * Context passed to trigger handlers.
 */
public record TriggerContext(
    GameObject entity,
    int tileX,
    int tileY,
    int tileElevation,
    TriggerData data,
    Direction exitDirection  // null for ON_ENTER/ON_INTERACT, present for ON_EXIT
) {
    /**
     * Casts data to specific type.
     */
    @SuppressWarnings("unchecked")
    public <T extends TriggerData> T getData() {
        return (T) data;
    }

    /**
     * Gets the scene the entity is in.
     */
    public Scene getScene() {
        return entity.getScene();
    }

    /**
     * Returns true if this trigger was activated by exiting a tile.
     */
    public boolean isExitTrigger() {
        return exitDirection != null;
    }
}
```

---

## Handler Implementations

### WarpHandler

**File**: `src/main/java/com/pocket/rpg/collision/trigger/handlers/WarpHandler.java`

```java
/**
 * Handles WARP trigger activation.
 * <p>
 * Transitions to target scene and positions player at the spawn point.
 */
public class WarpHandler implements TriggerHandler<WarpData> {

    private final SceneManager sceneManager;
    private final TransitionManager transitionManager;

    public WarpHandler(SceneManager sceneManager, TransitionManager transitionManager) {
        this.sceneManager = sceneManager;
        this.transitionManager = transitionManager;
    }

    @Override
    public void handle(TriggerContext context) {
        WarpData data = context.getData();
        GameObject player = context.entity();

        // Disable player input during transition
        PlayerController controller = player.getComponent(PlayerController.class);
        if (controller != null) {
            controller.setInputEnabled(false);
        }

        // Determine transition callback
        Runnable onTransitionComplete = () -> {
            // Load target scene
            sceneManager.loadScene(data.targetScene());

            // Find spawn point and position player
            Scene newScene = sceneManager.getCurrentScene();
            TileCoord spawnCoord = findSpawnPoint(newScene, data.spawnPointId());

            GameObject newPlayer = newScene.findWithTag("Player");
            if (newPlayer != null && spawnCoord != null) {
                GridMovement movement = newPlayer.getComponent(GridMovement.class);
                if (movement != null) {
                    movement.setGridPosition(spawnCoord.x(), spawnCoord.y());
                    movement.setElevation(spawnCoord.elevation());
                }
            }

            // Fade in
            if (data.transition() != TransitionType.NONE) {
                transitionManager.fadeIn(null);
            }
        };

        // Start transition
        if (data.transition() == TransitionType.NONE) {
            onTransitionComplete.run();
        } else {
            transitionManager.startTransition(data.transition(), onTransitionComplete);
        }
    }

    /**
     * Finds the spawn point tile by ID in the target scene.
     */
    private TileCoord findSpawnPoint(Scene scene, String spawnPointId) {
        if (spawnPointId == null || spawnPointId.isBlank()) {
            return null;
        }

        TriggerDataMap triggerMap = scene.getTriggerDataMap();
        for (var entry : triggerMap.getAll().entrySet()) {
            if (entry.getValue() instanceof SpawnPointData spawn) {
                if (spawnPointId.equals(spawn.id())) {
                    return entry.getKey();
                }
            }
        }

        System.err.println("Spawn point not found: " + spawnPointId);
        return null;
    }
}
```

### DoorHandler

**File**: `src/main/java/com/pocket/rpg/collision/trigger/handlers/DoorHandler.java`

```java
/**
 * Handles DOOR trigger activation.
 * <p>
 * Checks lock status and key requirements before allowing passage.
 * Uses spawn points for arrival position.
 */
public class DoorHandler implements TriggerHandler<DoorData> {

    private final SceneManager sceneManager;
    private final TransitionManager transitionManager;
    private final DialogueSystem dialogueSystem;  // For locked messages

    public DoorHandler(SceneManager sceneManager, TransitionManager transitionManager,
                       DialogueSystem dialogueSystem) {
        this.sceneManager = sceneManager;
        this.transitionManager = transitionManager;
        this.dialogueSystem = dialogueSystem;
    }

    @Override
    public void handle(TriggerContext context) {
        DoorData data = context.getData();
        GameObject player = context.entity();

        if (data.locked()) {
            // Check for required key
            Inventory inventory = player.getComponent(Inventory.class);
            if (inventory == null || !inventory.hasItem(data.requiredKey())) {
                // Show locked message
                String message = data.lockedMessage() != null
                    ? data.lockedMessage()
                    : "The door is locked.";
                dialogueSystem.showMessage(message);
                return;
            }

            // Consume key if configured
            if (data.consumeKey()) {
                inventory.removeItem(data.requiredKey());
            }
        }

        // Proceed with door transition
        performTransition(data, player);
    }

    private void performTransition(DoorData data, GameObject player) {
        PlayerController controller = player.getComponent(PlayerController.class);
        if (controller != null) {
            controller.setInputEnabled(false);
        }

        Runnable onComplete = () -> {
            if (data.targetScene() != null && !data.targetScene().isBlank()) {
                sceneManager.loadScene(data.targetScene());
            }

            Scene scene = sceneManager.getCurrentScene();
            TileCoord spawnCoord = findSpawnPoint(scene, data.spawnPointId());

            GameObject newPlayer = scene.findWithTag("Player");
            if (newPlayer != null && spawnCoord != null) {
                GridMovement movement = newPlayer.getComponent(GridMovement.class);
                if (movement != null) {
                    movement.setGridPosition(spawnCoord.x(), spawnCoord.y());
                    movement.setElevation(spawnCoord.elevation());
                }
            }

            if (data.transition() != TransitionType.NONE) {
                transitionManager.fadeIn(null);
            }
        };

        if (data.transition() == TransitionType.NONE) {
            onComplete.run();
        } else {
            transitionManager.startTransition(data.transition(), onComplete);
        }
    }

    /**
     * Finds the spawn point tile by ID in the target scene.
     */
    private TileCoord findSpawnPoint(Scene scene, String spawnPointId) {
        if (spawnPointId == null || spawnPointId.isBlank()) {
            return null;
        }

        TriggerDataMap triggerMap = scene.getTriggerDataMap();
        for (var entry : triggerMap.getAll().entrySet()) {
            if (entry.getValue() instanceof SpawnPointData spawn) {
                if (spawnPointId.equals(spawn.id())) {
                    return entry.getKey();
                }
            }
        }

        System.err.println("Spawn point not found: " + spawnPointId);
        return null;
    }
}
```

### StairsHandler

**File**: `src/main/java/com/pocket/rpg/collision/trigger/handlers/StairsHandler.java`

```java
/**
 * Handles STAIRS trigger activation (ON_EXIT only).
 * <p>
 * Elevation change is determined by the exit direction.
 * For example, a stair with {NORTH: +1, SOUTH: -1} will:
 * - Increase elevation by 1 when exiting northward
 * - Decrease elevation by 1 when exiting southward
 */
public class StairsHandler implements TriggerHandler<StairsData> {

    @Override
    public void handle(TriggerContext context) {
        StairsData data = context.getData();
        GameObject entity = context.entity();
        Direction exitDirection = context.exitDirection();

        // This should always be present for ON_EXIT triggers
        if (exitDirection == null) {
            System.err.println("StairsHandler called without exit direction");
            return;
        }

        GridMovement movement = entity.getComponent(GridMovement.class);
        if (movement == null) return;

        // Look up elevation change for this direction
        Integer elevationChange = data.elevationChanges().get(exitDirection);
        if (elevationChange == null || elevationChange == 0) {
            return;  // No change for this direction
        }

        // Apply elevation change
        int newElevation = movement.getElevation() + elevationChange;
        movement.setElevation(newElevation);

        // Update collision system registration
        Scene scene = context.getScene();
        if (scene != null && scene.getCollisionSystem() != null) {
            // Entity is moving to (tileX + dx, tileY + dy) at new elevation
            int newX = context.tileX() + exitDirection.getDx();
            int newY = context.tileY() + exitDirection.getDy();
            scene.getCollisionSystem().moveEntity(
                entity,
                context.tileX(), context.tileY(), context.tileElevation(),
                newX, newY, newElevation
            );
        }
    }
}
```

---

## Scene Integration

### Scene.java Changes

```java
public class Scene {
    // Existing fields...

    private TriggerDataMap triggerDataMap;
    private TriggerSystem triggerSystem;

    public Scene() {
        // Existing initialization...

        this.triggerDataMap = new TriggerDataMap();
        this.triggerSystem = new TriggerSystem(triggerDataMap, collisionMap);

        // Register default handlers
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        // These require dependencies - inject or get from context
        triggerSystem.registerHandler(WarpData.class, new WarpHandler(...));
        triggerSystem.registerHandler(DoorData.class, new DoorHandler(...));
        triggerSystem.registerHandler(StairsData.class, new StairsHandler());
        // SpawnPointData has no handler - it's a passive marker
    }

    public TriggerDataMap getTriggerDataMap() {
        return triggerDataMap;
    }

    public TriggerSystem getTriggerSystem() {
        return triggerSystem;
    }
}
```

### GridMovement.java Changes

The existing `triggerEnter` and `triggerExit` callbacks need to connect to TriggerSystem:

```java
// In GridMovement.java

/**
 * Called when movement to a new tile completes.
 */
private void onMovementComplete() {
    // Existing logic...

    // Notify trigger system
    Scene scene = gameObject.getScene();
    if (scene != null && scene.getTriggerSystem() != null) {
        scene.getTriggerSystem().onTileEnter(gameObject, gridX, gridY, elevation);
    }
}

/**
 * Called before starting movement from current tile.
 *
 * @param moveDirection The direction the entity is about to move
 */
private void beforeMovementStart(Direction moveDirection) {
    // Notify trigger system of exit with direction
    Scene scene = gameObject.getScene();
    if (scene != null && scene.getTriggerSystem() != null) {
        scene.getTriggerSystem().onTileExit(gameObject, gridX, gridY, elevation, moveDirection);
    }
}
```

**Key Change**: `beforeMovementStart` now takes the movement direction and passes it to `onTileExit`. This enables direction-based triggers like stairs to determine the elevation change based on which way the player is exiting.

---

## Handler Registration Flow

```
Game/Editor Startup
        │
        ▼
SceneManager creates Scene
        │
        ▼
Scene creates TriggerSystem
        │
        ▼
Scene registers handlers:
├── WarpHandler (needs SceneManager, TransitionManager)
├── DoorHandler (needs SceneManager, TransitionManager, DialogueSystem)
└── StairsHandler (no dependencies)
        │
        ▼
GridMovement.onMovementComplete()           GridMovement.beforeMovementStart(dir)
        │                                            │
        ▼                                            ▼
TriggerSystem.onTileEnter()                 TriggerSystem.onTileExit(dir)
        │                                            │
        ├── Get CollisionType                        ├── Get CollisionType
        ├── Get TriggerData                          ├── Get TriggerData
        ├── Check ON_ENTER mode                      ├── Check ON_EXIT mode
        ├── Check player-only                        ├── Check player-only
        │                                            │
        ▼                                            ▼
TriggerSystem.fireTrigger()                 TriggerSystem.fireTrigger(exitDir)
        │                                            │
        └── handler.handle(context)                  └── handler.handle(context)
                                                         context.exitDirection() = dir
```

**Key Points**:
- `onTileEnter` triggers have `exitDirection = null`
- `onTileExit` triggers have `exitDirection` set to movement direction
- Stairs use ON_EXIT so they receive the exit direction to determine elevation change

---

## Summary of Files

| File | Type | Description |
|------|------|-------------|
| `trigger/TriggerSystem.java` | NEW | Runtime dispatch with `onTileEnter` and `onTileExit(dir)` |
| `trigger/TriggerHandler.java` | NEW | Handler interface |
| `trigger/TriggerContext.java` | NEW | Context record with `exitDirection` field |
| `trigger/handlers/WarpHandler.java` | NEW | Warp logic (uses spawn points) |
| `trigger/handlers/DoorHandler.java` | NEW | Door logic with key/lock support |
| `trigger/handlers/StairsHandler.java` | NEW | Direction-based elevation (ON_EXIT only) |
| `scenes/Scene.java` | MODIFY | Add TriggerSystem |
| `components/GridMovement.java` | MODIFY | Pass direction to `beforeMovementStart` |
