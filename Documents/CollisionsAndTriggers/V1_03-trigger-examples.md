# Trigger Examples

## Activation Modes

Not all triggers activate the same way:

| Mode | Description | Examples |
|------|-------------|----------|
| `ON_ENTER` | Fires immediately when entity steps on tile | Warp, Trap, Cutscene |
| `ON_INTERACT` | Fires when player presses interact button | Dialogue, Door, Sign |
| `ON_STAY` | Fires continuously while entity remains on tile | Poison floor, Healing zone |
| `ON_EXIT` | Fires when entity leaves the tile | Pressure plate release |

### TriggerData Structure

```java
public record TriggerData(
    TriggerType type,
    ActivationMode activationMode,
    Map<String, Object> properties,
    boolean oneShot,
    boolean playerOnly
) {
    public enum TriggerType {
        WARP, DOOR, DIALOGUE, TRAP, SIGN, CUTSCENE, CHECKPOINT, CUSTOM
    }
    
    public enum ActivationMode {
        ON_ENTER,
        ON_INTERACT,
        ON_STAY,
        ON_EXIT
    }
}
```

---

## Example 1: Warp Tile (Automatic)

**Behavior**: Player steps on tile → immediate scene transition

```
┌─────────────────────────────────────┐
│ Trigger: WARP                       │
│ Activation: ON_ENTER                │
├─────────────────────────────────────┤
│ Target Scene: [cave_entrance     ]  │
│ Target X: [5    ]  Target Y: [12  ] │
│ Transition: [Fade ▼]                │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

**Runtime Flow**:
```
Player moves to (10, 5)
    │
    ▼
GridMovement.onMovementComplete()
    │
    ▼
TriggerSystem.checkTileTrigger(10, 5)
    │
    ├─► CollisionType = WARP (isTrigger = true)
    ├─► TriggerData.activationMode = ON_ENTER
    ├─► Entity is Player? YES
    │
    ▼
TriggerEventBus.fire(WarpTriggerEvent)
    │
    ▼
WarpHandler.handle()
    │
    ├─► TransitionManager.startTransition("fade")
    ├─► SceneManager.loadScene("cave_entrance")
    └─► Player.setGridPosition(5, 12)
```

**Handler Code**:
```java
public class WarpHandler implements TriggerHandler<WarpTriggerEvent> {
    
    @Override
    public void handle(WarpTriggerEvent event) {
        TriggerData data = event.triggerData();
        
        String targetScene = (String) data.properties().get("targetScene");
        int targetX = (int) data.properties().get("targetX");
        int targetY = (int) data.properties().get("targetY");
        
        transitionManager.fadeOut(() -> {
            sceneManager.loadScene(targetScene);
            
            GameObject player = sceneManager.getCurrentScene().findWithTag("Player");
            player.getComponent(GridMovement.class).setGridPosition(targetX, targetY);
            
            transitionManager.fadeIn(null);
        });
    }
}
```

---

## Example 2: Sign/NPC Dialogue (Interact)

**Behavior**: Player stands near tile → presses interact button → dialogue starts

```
┌─────────────────────────────────────┐
│ Trigger: DIALOGUE                   │
│ Activation: ON_INTERACT             │
├─────────────────────────────────────┤
│ Dialogue ID: [sign_welcome_001   ]  │
│ Speaker: [                       ]  │
│ ☐ Face Speaker                      │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

**Runtime Flow**:
```
Player moves to (8, 3)
    │
    ▼
TriggerSystem.checkTileTrigger(8, 3)
    │
    ├─► TriggerData.activationMode = ON_INTERACT
    ├─► Store as "pendingInteraction"
    │
    ▼
(Nothing happens yet - waiting for button press)

    ...

Player presses Interact action (E key or gamepad button)
    │
    ▼
PlayerController.onInteract()
    │
    ▼
TriggerSystem.tryInteract(playerX, playerY, facingDirection)
    │
    ├─► Check tile player is ON
    ├─► Check tile player is FACING
    ├─► Found pending ON_INTERACT trigger
    │
    ▼
TriggerEventBus.fire(DialogueTriggerEvent)
    │
    ▼
DialogueHandler → dialogueSystem.start("sign_welcome_001")
```

**Interaction Code**:
```java
// TriggerSystem.java
public boolean tryInteract(int playerX, int playerY, Direction facing) {
    // Check tile player is standing on
    TriggerData standingTrigger = getTriggerAt(playerX, playerY);
    if (standingTrigger != null && 
        standingTrigger.activationMode() == ActivationMode.ON_INTERACT) {
        fireTrigger(standingTrigger, playerX, playerY);
        return true;
    }
    
    // Check tile player is facing (for signs, NPCs)
    int facingX = playerX + facing.dx;
    int facingY = playerY + facing.dy;
    
    TriggerData facingTrigger = getTriggerAt(facingX, facingY);
    if (facingTrigger != null && 
        facingTrigger.activationMode() == ActivationMode.ON_INTERACT) {
        fireTrigger(facingTrigger, facingX, facingY);
        return true;
    }
    
    return false;
}

// InputAction.java - Define interact action
public enum InputAction {
    // Movement
    MOVE_UP(InputType.KEYBOARD),
    MOVE_DOWN(InputType.KEYBOARD),
    MOVE_LEFT(InputType.KEYBOARD),
    MOVE_RIGHT(InputType.KEYBOARD),
    
    // Actions
    INTERACT(InputType.KEYBOARD),    // E key by default
    CANCEL(InputType.KEYBOARD),      // Escape
    CONFIRM(InputType.KEYBOARD),     // Enter/Space
    
    // ... etc
}

// InputConfig.java - Configure bindings
public class InputConfig {
    public InputConfig() {
        // Default bindings
        bindKey(InputAction.INTERACT, GLFW.GLFW_KEY_E);
        bindKey(InputAction.CANCEL, GLFW.GLFW_KEY_ESCAPE);
        bindKey(InputAction.CONFIRM, GLFW.GLFW_KEY_ENTER);
        
        // Gamepad bindings
        bindGamepadButton(InputAction.INTERACT, GLFW.GLFW_GAMEPAD_BUTTON_A);
    }
}

// PlayerController.java - Using InputManager
public class PlayerController extends Component {
    private GridMovement movement;
    
    @Override
    public void start() {
        movement = getComponent(GridMovement.class);
    }
    
    @Override
    public void update(float deltaTime) {
        if (movement.isMoving()) return;
        
        // Movement using actions
        if (InputManager.isActionPressed(InputAction.MOVE_UP)) {
            movement.move(Direction.UP);
        } else if (InputManager.isActionPressed(InputAction.MOVE_DOWN)) {
            movement.move(Direction.DOWN);
        } else if (InputManager.isActionPressed(InputAction.MOVE_LEFT)) {
            movement.move(Direction.LEFT);
        } else if (InputManager.isActionPressed(InputAction.MOVE_RIGHT)) {
            movement.move(Direction.RIGHT);
        }
        
        // Interact - use isActionPressedThisFrame for single press
        if (InputManager.isActionPressedThisFrame(InputAction.INTERACT)) {
            tryInteract();
        }
    }
    
    private void tryInteract() {
        TriggerSystem triggers = gameObject.getScene().getTriggerSystem();
        boolean handled = triggers.tryInteract(
            movement.getGridX(), 
            movement.getGridY(), 
            movement.getFacingDirection()
        );
        
        if (!handled) {
            // No trigger found - maybe check for NPC interaction
            checkNPCInteraction();
        }
    }
    
    private void checkNPCInteraction() {
        int facingX = movement.getGridX() + movement.getFacingDirection().dx;
        int facingY = movement.getGridY() + movement.getFacingDirection().dy;
        
        // Query EntityOccupancyMap for NPC at facing position
        CollisionSystem collision = gameObject.getScene().getCollisionSystem();
        Set<Object> entities = collision.getEntitiesAt(facingX, facingY, 0);
        
        for (Object entity : entities) {
            if (entity instanceof GameObject go) {
                NPCController npc = go.getComponent(NPCController.class);
                if (npc != null) {
                    npc.onInteract(gameObject);
                    return;
                }
            }
        }
    }
}
```

---

## Example 3: Automatic Cutscene Dialogue (One-Shot)

**Behavior**: Player steps on tile → dialogue starts immediately (only once)

```
┌─────────────────────────────────────┐
│ Trigger: DIALOGUE                   │
│ Activation: ON_ENTER                │
├─────────────────────────────────────┤
│ Dialogue ID: [intro_cutscene_001 ]  │
│ Speaker: [Old Man              ]    │
│ ☑ Freeze Player                     │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☑ One Shot    ◄── Only triggers once│
└─────────────────────────────────────┘
```

**Use Case**: First time entering a town, tutorial messages, story beats.

**Handler Code**:
```java
public class DialogueHandler implements TriggerHandler<DialogueTriggerEvent> {
    
    @Override
    public void handle(DialogueTriggerEvent event) {
        TriggerData data = event.triggerData();
        
        String dialogueId = (String) data.properties().get("dialogueId");
        boolean freezePlayer = (boolean) data.properties().getOrDefault("freezePlayer", false);
        
        if (freezePlayer) {
            PlayerController player = findPlayer();
            player.setInputEnabled(false);
            
            dialogueSystem.start(dialogueId, () -> {
                player.setInputEnabled(true);
            });
        } else {
            dialogueSystem.start(dialogueId, null);
        }
        
        // Mark as triggered (for one-shot)
        if (data.oneShot()) {
            gameState.markTriggerFired(event.tileX(), event.tileY());
        }
    }
}
```

---

## Example 4: Damage Trap (Immediate)

**Behavior**: Player steps on tile → takes damage immediately

```
┌─────────────────────────────────────┐
│ Trigger: TRAP                       │
│ Activation: ON_ENTER                │
├─────────────────────────────────────┤
│ Trap Type: [Damage ▼]               │
│ Damage: [10        ]                │
│ ☐ Knockback                         │
│ Sound: [trap_spike.wav          ]   │
├─────────────────────────────────────┤
│ ☐ Player Only  ◄── NPCs can trigger │
│ ☐ One Shot     ◄── Repeatable       │
└─────────────────────────────────────┘
```

**Handler Code**:
```java
public class TrapHandler implements TriggerHandler<TrapTriggerEvent> {
    
    @Override
    public void handle(TrapTriggerEvent event) {
        TriggerData data = event.triggerData();
        GameObject entity = event.entity();
        
        String trapType = (String) data.properties().get("trapType");
        
        switch (trapType) {
            case "damage" -> handleDamageTrap(entity, data);
            case "poison" -> handlePoisonTrap(entity, data);
            case "teleport" -> handleTeleportTrap(entity, data);
            case "push" -> handlePushTrap(entity, data);
        }
        
        String sound = (String) data.properties().get("sound");
        if (sound != null) {
            Audio.play(sound);
        }
    }
    
    private void handleDamageTrap(GameObject entity, TriggerData data) {
        int damage = (int) data.properties().getOrDefault("damage", 10);
        boolean knockback = (boolean) data.properties().getOrDefault("knockback", false);
        
        Health health = entity.getComponent(Health.class);
        if (health != null) {
            health.takeDamage(damage);
        }
        
        if (knockback) {
            GridMovement movement = entity.getComponent(GridMovement.class);
            Direction pushDir = movement.getFacingDirection().opposite();
            movement.forceMove(pushDir);
        }
    }
}
```

---

## Example 5: Poison Floor (Continuous)

**Behavior**: While standing on tile → take damage every X seconds

```
┌─────────────────────────────────────┐
│ Trigger: TRAP                       │
│ Activation: ON_STAY                 │
├─────────────────────────────────────┤
│ Trap Type: [Poison Floor ▼]         │
│ Damage Per Tick: [5       ]         │
│ Tick Interval: [1.0   ] seconds     │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

**Runtime Flow**:
```
Player moves to poison tile
    │
    ▼
TriggerSystem.onTileEnter()
    │
    ├─► activationMode = ON_STAY
    ├─► Register in "activeStayTriggers" map
    │
    ▼
Every frame: TriggerSystem.update(deltaTime)
    │
    ├─► accumulator += deltaTime
    │   if (accumulator >= tickInterval):
    │       fireTrigger()
    │       accumulator = 0
    │
    ▼
Player moves OFF poison tile
    │
    ▼
TriggerSystem.onTileExit()
    │
    └─► Remove from "activeStayTriggers"
```

**Stay Trigger Code**:
```java
// TriggerSystem.java
private Map<GameObject, StayTriggerState> activeStayTriggers = new HashMap<>();

private record StayTriggerState(TileCoord tile, TriggerData data, float accumulator) {}

public void onTileEnter(GameObject entity, int x, int y, int z) {
    TriggerData data = getTriggerAt(x, y, z);
    if (data == null) return;
    
    switch (data.activationMode()) {
        case ON_ENTER -> fireTrigger(data, entity, x, y, z);
        case ON_STAY -> {
            activeStayTriggers.put(entity, new StayTriggerState(
                new TileCoord(x, y, z), data, 0f
            ));
            fireTrigger(data, entity, x, y, z); // Also fire immediately
        }
    }
}

public void update(float deltaTime) {
    for (var entry : activeStayTriggers.entrySet()) {
        GameObject entity = entry.getKey();
        StayTriggerState state = entry.getValue();
        
        float tickInterval = (float) state.data().properties()
            .getOrDefault("tickInterval", 1.0f);
        
        float newAccumulator = state.accumulator() + deltaTime;
        
        if (newAccumulator >= tickInterval) {
            fireTrigger(state.data(), entity, 
                state.tile().x(), state.tile().y(), state.tile().z());
            newAccumulator = 0f;
        }
        
        entry.setValue(new StayTriggerState(state.tile(), state.data(), newAccumulator));
    }
}
```

---

## Example 6: Healing Zone (Continuous Positive)

**Behavior**: Standing on tile gradually restores health

```
┌─────────────────────────────────────┐
│ Trigger: CUSTOM                     │
│ Activation: ON_STAY                 │
├─────────────────────────────────────┤
│ Script ID: [healing_zone         ]  │
│ Parameters:                         │
│   healPerTick: 2                    │
│   tickInterval: 0.5                 │
│   maxHeal: 100                      │
│   particles: true                   │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

---

## Example 7: Pressure Plate (Enter + Exit)

**Behavior**: 
- ON_ENTER: Opens door / activates mechanism
- ON_EXIT: Closes door / deactivates mechanism

```
┌─────────────────────────────────────┐
│ Trigger: CUSTOM                     │
│ Activation: ON_ENTER + ON_EXIT      │
├─────────────────────────────────────┤
│ Script ID: [pressure_plate       ]  │
│ Parameters:                         │
│   linkedDoor: "door_01"             │
│   stayOpen: false                   │
│   sound_activate: "plate_down.wav"  │
│   sound_deactivate: "plate_up.wav"  │
├─────────────────────────────────────┤
│ ☐ Player Only  ◄── Boxes can trigger│
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

**Handler Code**:
```java
public class PressurePlateHandler {
    
    public void handleEnter(CustomTriggerEvent event) {
        String doorId = (String) event.data().properties().get("linkedDoor");
        Door door = findDoor(doorId);
        door.open();
        Audio.play("plate_down.wav");
    }
    
    public void handleExit(CustomTriggerEvent event) {
        boolean stayOpen = (boolean) event.data().properties()
            .getOrDefault("stayOpen", false);
        
        if (!stayOpen) {
            String doorId = (String) event.data().properties().get("linkedDoor");
            findDoor(doorId).close();
            Audio.play("plate_up.wav");
        }
    }
}
```

---

## Example 8: Locked Door (Interact + Condition)

**Behavior**: Player interacts → check for key → open or show message

```
┌─────────────────────────────────────┐
│ Trigger: DOOR                       │
│ Activation: ON_INTERACT             │
├─────────────────────────────────────┤
│ ☑ Locked                            │
│   Required Key: [rusty_key       ]  │
│   Consume Key: ☑                    │
│   Locked Message: [door_locked_01]  │
│                                     │
│ Destination:                        │
│   Scene: [house_interior         ]  │
│   X: [3     ]  Y: [8     ]         │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

**Handler Code**:
```java
public class DoorHandler implements TriggerHandler<DoorTriggerEvent> {
    
    @Override
    public void handle(DoorTriggerEvent event) {
        TriggerData data = event.triggerData();
        GameObject player = event.entity();
        
        boolean locked = (boolean) data.properties().getOrDefault("locked", false);
        
        if (locked) {
            String keyItem = (String) data.properties().get("keyItem");
            Inventory inventory = player.getComponent(Inventory.class);
            
            if (inventory.hasItem(keyItem)) {
                boolean consumeKey = (boolean) data.properties()
                    .getOrDefault("consumeKey", true);
                if (consumeKey) {
                    inventory.removeItem(keyItem);
                }
                openDoor(data);
            } else {
                String lockedMessage = (String) data.properties()
                    .getOrDefault("lockedMessage", "The door is locked.");
                dialogueSystem.showMessage(lockedMessage);
            }
        } else {
            openDoor(data);
        }
    }
}
```

---

## Example 9: Checkpoint/Save Point (Interact)

**Behavior**: Player interacts → saves game, sets respawn point

```
┌─────────────────────────────────────┐
│ Trigger: CHECKPOINT                 │
│ Activation: ON_INTERACT             │
├─────────────────────────────────────┤
│ Checkpoint ID: [checkpoint_cave_01] │
│ ☑ Auto Save                         │
│ ☑ Full Heal                         │
│ Activation Message: [Game Saved!  ] │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

---

## Example 10: Hidden/Conditional Trigger

**Behavior**: Only activates if game condition is met

```
┌─────────────────────────────────────┐
│ Trigger: CUSTOM                     │
│ Activation: ON_ENTER                │
├─────────────────────────────────────┤
│ Script ID: [secret_passage       ]  │
│ Condition: [hasItem:magic_lens   ]  │
│                                     │
│ Parameters:                         │
│   targetScene: "secret_room"        │
│   targetX: 5                        │
│   targetY: 5                        │
├─────────────────────────────────────┤
│ ☑ Player Only                       │
│ ☐ One Shot                          │
└─────────────────────────────────────┘
```

**Condition Checking Code**:
```java
public boolean checkCondition(TriggerData data, GameObject entity) {
    String condition = (String) data.properties().get("condition");
    if (condition == null || condition.isEmpty()) {
        return true;
    }
    
    String[] parts = condition.split(":");
    String type = parts[0];
    String value = parts.length > 1 ? parts[1] : "";
    
    return switch (type) {
        case "hasItem" -> {
            Inventory inv = entity.getComponent(Inventory.class);
            yield inv != null && inv.hasItem(value);
        }
        case "flag" -> gameState.getFlag(value);
        case "quest" -> questSystem.isCompleted(value);
        case "level" -> {
            Stats stats = entity.getComponent(Stats.class);
            int required = Integer.parseInt(value);
            yield stats != null && stats.getLevel() >= required;
        }
        default -> true;
    };
}
```
