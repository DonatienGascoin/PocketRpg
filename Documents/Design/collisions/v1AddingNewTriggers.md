# Adding New Trigger Types

## When Do You Need a New Type?

**Question**: Is this fundamentally different from existing types, or just a variation?

| Scenario | Solution |
|----------|----------|
| Similar to WARP but with different animation | Use `WARP` with extra properties |
| Completely new visual in editor | Add new `CollisionType` |
| Needs special rendering | Add new `CollisionType` |
| Just different handler logic | Use `SCRIPT_TRIGGER` with custom handler |

**Example**: Shop trigger doesn't need new CollisionType:
```java
// Use SCRIPT_TRIGGER with custom handler
new TriggerData(
    TriggerType.CUSTOM,
    ActivationMode.ON_INTERACT,
    Map.of("scriptId", "shop", "shopId", "village_general_store"),
    false, true
)
```

---

## Full Process (9 Steps)

### Step 1: Add CollisionType (If Needed)

```java
// CollisionType.java
public enum CollisionType {
    // ... existing types ...
    
    /**
     * Shop zone - opens shop interface
     */
    SHOP(13, "Shop", new float[]{0.2f, 0.8f, 0.2f, 0.6f});  // Green
    
    // Update helper method
    public boolean isTrigger() {
        return switch (this) {
            case WARP, DOOR, SCRIPT_TRIGGER, SHOP -> true;  // Add SHOP
            default -> false;
        };
    }
}
```

### Step 2: Add TriggerType (If Needed)

```java
// TriggerData.java
public enum TriggerType {
    WARP,
    DOOR,
    DIALOGUE,
    TRAP,
    SIGN,
    CUTSCENE,
    CHECKPOINT,
    SHOP,      // NEW
    CUSTOM;
    
    public static TriggerType fromCollisionType(CollisionType type) {
        return switch (type) {
            case WARP -> WARP;
            case DOOR -> DOOR;
            case SHOP -> SHOP;  // NEW
            case SCRIPT_TRIGGER -> CUSTOM;
            default -> null;
        };
    }
}
```

### Step 3: Create Event Class

```java
// ShopTriggerEvent.java
public record ShopTriggerEvent(
    GameObject entity,
    int tileX,
    int tileY,
    int tileZ,
    TriggerData triggerData
) implements TriggerEvent {
    
    public String getShopId() {
        return (String) triggerData.properties().get("shopId");
    }
}
```

### Step 4: Create Handler

```java
// ShopHandler.java
public class ShopHandler implements TriggerHandler<ShopTriggerEvent> {
    
    private final ShopSystem shopSystem;
    private final UIManager uiManager;
    
    @Override
    public Class<ShopTriggerEvent> getEventType() {
        return ShopTriggerEvent.class;
    }
    
    @Override
    public void handle(ShopTriggerEvent event) {
        String shopId = event.getShopId();
        
        ShopData shop = shopSystem.getShop(shopId);
        if (shop == null) {
            System.err.println("Shop not found: " + shopId);
            return;
        }
        
        GameState.setPaused(true);
        
        uiManager.openShop(shop, () -> {
            GameState.setPaused(false);
        });
    }
}
```

### Step 5: Register Handler

```java
// TriggerHandlerRegistry.java
public class TriggerHandlerRegistry {
    
    private final Map<Class<? extends TriggerEvent>, TriggerHandler<?>> handlers 
        = new HashMap<>();
    
    public void registerDefaults() {
        register(new WarpHandler());
        register(new DoorHandler());
        register(new DialogueHandler());
        register(new TrapHandler());
        register(new ShopHandler());  // NEW
    }
    
    public <T extends TriggerEvent> void register(TriggerHandler<T> handler) {
        handlers.put(handler.getEventType(), handler);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends TriggerEvent> void dispatch(T event) {
        TriggerHandler<T> handler = (TriggerHandler<T>) handlers.get(event.getClass());
        if (handler != null) {
            handler.handle(event);
        }
    }
}
```

### Step 6: Update TriggerSystem

```java
// TriggerSystem.java
private TriggerEvent createEvent(TriggerData data, GameObject entity, 
                                  int x, int y, int z) {
    return switch (data.type()) {
        case WARP -> new WarpTriggerEvent(entity, x, y, z, data);
        case DOOR -> new DoorTriggerEvent(entity, x, y, z, data);
        case DIALOGUE -> new DialogueTriggerEvent(entity, x, y, z, data);
        case TRAP -> new TrapTriggerEvent(entity, x, y, z, data);
        case SHOP -> new ShopTriggerEvent(entity, x, y, z, data);  // NEW
        case CUSTOM -> new CustomTriggerEvent(entity, x, y, z, data);
        default -> null;
    };
}
```

### Step 7: Add Editor Inspector UI

```java
// TriggerInspectorPanel.java
private void renderTypeSpecificProperties() {
    CollisionType type = getSelectedCollisionType();
    
    switch (type) {
        case WARP -> renderWarpProperties();
        case DOOR -> renderDoorProperties();
        case SCRIPT_TRIGGER -> renderScriptProperties();
        case SHOP -> renderShopProperties();  // NEW
    }
}

private void renderShopProperties() {
    ImGui.text("Shop Configuration");
    ImGui.spacing();
    
    ImGui.text("Shop:");
    if (ImGui.beginCombo("##shopId", shopId.get())) {
        for (String id : availableShops) {
            if (ImGui.selectable(id, id.equals(shopId.get()))) {
                shopId.set(id);
                isDirty = true;
            }
        }
        ImGui.endCombo();
    }
    
    // Preview
    ShopData shop = shopSystem.getShop(shopId.get());
    if (shop != null) {
        ImGui.spacing();
        ImGui.textDisabled("Items: " + shop.getItems().size());
    }
}
```

### Step 8: Update Serialization

Usually automatic if using Gson with enum support. Verify `TriggerDataMap` serializes correctly.

### Step 9: Test

1. Draw new collision type in editor
2. Configure properties
3. Save/load scene
4. Test in play mode

---

## Minimal Approach (Simple Triggers)

For simple triggers, skip steps 1-3 and use `SCRIPT_TRIGGER`:

```java
// 1. Create handler
public class MinimalShopHandler {
    public void handle(CustomTriggerEvent event) {
        String shopId = (String) event.triggerData().properties().get("shopId");
        // ... shop logic ...
    }
}

// 2. Register
registry.registerCustom("shop", shopHandler::handle);
registry.registerCustom("battle_zone", battleHandler::handle);
registry.registerCustom("teleporter", teleportHandler::handle);
```

This approach:
- Uses existing `SCRIPT_TRIGGER` collision type
- No new event classes needed
- Quick to implement

---

## Checklist

When adding a new trigger type, verify:

- [ ] CollisionType added (if needed for editor visualization)
- [ ] TriggerType enum updated
- [ ] Event class created
- [ ] Handler class created
- [ ] Handler registered in TriggerHandlerRegistry
- [ ] TriggerSystem.createEvent() updated
- [ ] Editor inspector UI added
- [ ] Serialization works (test save/load)
- [ ] Overlay color chosen (for editor visibility)
- [ ] Tooltip/description added

---

## Common Patterns

### Conditional Triggers

```java
public boolean checkCondition(TriggerData data, GameObject entity) {
    String condition = (String) data.properties().get("condition");
    if (condition == null) return true;
    
    String[] parts = condition.split(":");
    String type = parts[0];
    String value = parts.length > 1 ? parts[1] : "";
    
    return switch (type) {
        case "hasItem" -> inventory.hasItem(value);
        case "flag" -> gameState.getFlag(value);
        case "quest" -> questSystem.isCompleted(value);
        case "level" -> stats.getLevel() >= Integer.parseInt(value);
        default -> true;
    };
}
```

### One-Shot Persistence

```java
// In handler
if (data.oneShot()) {
    String triggerId = x + "," + y + "," + scene.getName();
    gameState.markTriggerFired(triggerId);
}

// In TriggerSystem before firing
if (data.oneShot() && gameState.hasTriggerFired(triggerId)) {
    return; // Don't fire again
}
```

### Entity Filtering

```java
// In TriggerSystem
private boolean shouldTrigger(TriggerData data, GameObject entity) {
    if (data.playerOnly() && !entity.hasTag("Player")) {
        return false;
    }
    
    // Check layer mask if needed
    int entityLayer = entity.getLayer();
    int triggerMask = (int) data.properties().getOrDefault("layerMask", 0xFFFF);
    if ((entityLayer & triggerMask) == 0) {
        return false;
    }
    
    return true;
}
```

---

## Quick Reference: Property Names

Common property keys used across trigger types:

| Key | Type | Used By | Description |
|-----|------|---------|-------------|
| `targetScene` | String | WARP, DOOR | Destination scene name |
| `targetX` | int | WARP, DOOR | Destination X coordinate |
| `targetY` | int | WARP, DOOR | Destination Y coordinate |
| `transition` | int | WARP | Transition type (0=none, 1=fade, etc.) |
| `locked` | boolean | DOOR | Whether door is locked |
| `keyItem` | String | DOOR | Required item to unlock |
| `dialogueId` | String | DIALOGUE | Dialogue to start |
| `freezePlayer` | boolean | DIALOGUE | Disable input during dialogue |
| `damage` | int | TRAP | Damage amount |
| `trapType` | String | TRAP | "damage", "poison", "teleport", "push" |
| `tickInterval` | float | ON_STAY | Seconds between ticks |
| `condition` | String | Any | Condition string "type:value" |
| `sound` | String | Any | Sound effect to play |
| `scriptId` | String | CUSTOM | Script identifier |
