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
     */
    public void onTileExit(GameObject entity, int x, int y, int elevation) {
        CollisionType type = collisionMap.get(x, y, elevation);
        if (!type.isTrigger()) return;

        TriggerData data = triggerDataMap.get(x, y, elevation);
        if (data == null) return;

        if (data.activationMode() != ActivationMode.ON_EXIT) return;
        if (data.playerOnly() && !isPlayer(entity)) return;

        fireTrigger(data, entity, x, y, elevation);
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
     * Dispatches trigger to registered handler.
     */
    @SuppressWarnings("unchecked")
    private void fireTrigger(TriggerData data, GameObject entity, int x, int y, int elevation) {
        TriggerHandler<TriggerData> handler =
            (TriggerHandler<TriggerData>) handlers.get(data.getClass());

        if (handler == null) {
            System.err.println("No handler for trigger type: " + data.getClass().getSimpleName());
            return;
        }

        TriggerContext context = new TriggerContext(entity, x, y, elevation, data);
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
    TriggerData data
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
 * Transitions to target scene and positions player at target coordinates.
 */
public class WarpHandler implements TriggerHandler<WarpTriggerData> {

    private final SceneManager sceneManager;
    private final TransitionManager transitionManager;

    public WarpHandler(SceneManager sceneManager, TransitionManager transitionManager) {
        this.sceneManager = sceneManager;
        this.transitionManager = transitionManager;
    }

    @Override
    public void handle(TriggerContext context) {
        WarpTriggerData data = context.getData();
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

            // Position player
            Scene newScene = sceneManager.getCurrentScene();
            GameObject newPlayer = newScene.findWithTag("Player");
            if (newPlayer != null) {
                GridMovement movement = newPlayer.getComponent(GridMovement.class);
                if (movement != null) {
                    movement.setGridPosition(data.targetX(), data.targetY());
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
}
```

### DoorHandler

**File**: `src/main/java/com/pocket/rpg/collision/trigger/handlers/DoorHandler.java`

```java
/**
 * Handles DOOR trigger activation.
 * <p>
 * Checks lock status and key requirements before allowing passage.
 */
public class DoorHandler implements TriggerHandler<DoorTriggerData> {

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
        DoorTriggerData data = context.getData();
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

    private void performTransition(DoorTriggerData data, GameObject player) {
        PlayerController controller = player.getComponent(PlayerController.class);
        if (controller != null) {
            controller.setInputEnabled(false);
        }

        Runnable onComplete = () -> {
            if (data.targetScene() != null && !data.targetScene().isBlank()) {
                sceneManager.loadScene(data.targetScene());
            }

            Scene scene = sceneManager.getCurrentScene();
            GameObject newPlayer = scene.findWithTag("Player");
            if (newPlayer != null) {
                GridMovement movement = newPlayer.getComponent(GridMovement.class);
                if (movement != null) {
                    movement.setGridPosition(data.targetX(), data.targetY());
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
}
```

### StairsHandler

**File**: `src/main/java/com/pocket/rpg/collision/trigger/handlers/StairsHandler.java`

```java
/**
 * Handles STAIRS_UP and STAIRS_DOWN trigger activation.
 * <p>
 * Changes the entity's elevation (floor level) and optionally repositions.
 */
public class StairsHandler implements TriggerHandler<StairsTriggerData> {

    @Override
    public void handle(TriggerContext context) {
        StairsTriggerData data = context.getData();
        GameObject entity = context.entity();

        GridMovement movement = entity.getComponent(GridMovement.class);
        if (movement == null) return;

        // Update elevation
        movement.setElevation(data.targetElevation());

        // Optionally reposition X/Y
        if (data.targetX() != null && data.targetY() != null) {
            movement.setGridPosition(data.targetX(), data.targetY());
        }

        // Update collision system registration
        Scene scene = context.getScene();
        if (scene != null && scene.getCollisionSystem() != null) {
            scene.getCollisionSystem().moveEntity(
                entity,
                context.tileX(), context.tileY(), context.tileElevation(),
                data.targetX() != null ? data.targetX() : context.tileX(),
                data.targetY() != null ? data.targetY() : context.tileY(),
                data.targetElevation()
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
        triggerSystem.registerHandler(WarpTriggerData.class, new WarpHandler(...));
        triggerSystem.registerHandler(DoorTriggerData.class, new DoorHandler(...));
        triggerSystem.registerHandler(StairsTriggerData.class, new StairsHandler());
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
 */
private void beforeMovementStart() {
    // Notify trigger system of exit
    Scene scene = gameObject.getScene();
    if (scene != null && scene.getTriggerSystem() != null) {
        scene.getTriggerSystem().onTileExit(gameObject, gridX, gridY, elevation);
    }
}
```

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
GridMovement.onMovementComplete()
        │
        ▼
TriggerSystem.onTileEnter()
        │
        ├── Get CollisionType from CollisionMap
        ├── Get TriggerData from TriggerDataMap
        ├── Check activation mode (ON_ENTER?)
        ├── Check player-only filter
        │
        ▼
TriggerSystem.fireTrigger()
        │
        ├── Look up handler by TriggerData class
        └── handler.handle(context)
```

---

## Summary of Files

| File | Type | Description |
|------|------|-------------|
| `trigger/TriggerSystem.java` | NEW | Runtime dispatch |
| `trigger/TriggerHandler.java` | NEW | Handler interface |
| `trigger/TriggerContext.java` | NEW | Context record |
| `trigger/handlers/WarpHandler.java` | NEW | Warp logic |
| `trigger/handlers/DoorHandler.java` | NEW | Door logic |
| `trigger/handlers/StairsHandler.java` | NEW | Stairs logic |
| `scenes/Scene.java` | MODIFY | Add TriggerSystem |
| `components/GridMovement.java` | MODIFY | Connect callbacks |
