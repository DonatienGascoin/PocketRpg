# Trigger Data Architecture

## Problem

The original design used `Map<String, Object>` for trigger properties:

```java
// BAD: Fragile, no type safety, hard to understand
record TriggerData(
    TriggerType type,
    Map<String, Object> properties  // What goes here?!
) {}

// Usage is error-prone
String scene = (String) data.properties().get("targetScene");  // Typo? Crash!
int x = (int) data.properties().get("targetX");  // Wrong type? Crash!
```

**Problems**:
- No compile-time safety
- Easy to typo property names
- No IDE autocomplete
- Hard to know what properties each type needs
- Casting errors at runtime

---

## Solution: Sealed Interface with Typed Records

Use Java's sealed interfaces to create a type-safe hierarchy:

```java
// Each trigger type has its own record with specific fields
sealed interface TriggerData
    permits WarpTriggerData, DoorTriggerData, StairsTriggerData, ScriptTriggerData {

    // Common fields required by all triggers
    ActivationMode activationMode();
    boolean oneShot();
    boolean playerOnly();
}
```

---

## Data Structures

### ActivationMode Enum

**File**: `src/main/java/com/pocket/rpg/collision/trigger/ActivationMode.java`

```java
public enum ActivationMode {
    /**
     * Fires when entity steps onto the tile.
     */
    ON_ENTER,

    /**
     * Fires when player presses interact while on or facing tile.
     */
    ON_INTERACT,

    /**
     * Fires when entity steps off the tile.
     */
    ON_EXIT
}
```

### TransitionType Enum

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TransitionType.java`

```java
public enum TransitionType {
    NONE,
    FADE,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SLIDE_UP,
    SLIDE_DOWN
}
```

### TileCoord Record

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TileCoord.java`

```java
public record TileCoord(int x, int y, int elevation) {

    /**
     * Packs coordinates into a single long for map keys.
     */
    public long pack() {
        return ((long) x & 0xFFFFF) |
               (((long) y & 0xFFFFF) << 20) |
               (((long) elevation & 0xFFFF) << 40);
    }

    /**
     * Unpacks a long back into coordinates.
     */
    public static TileCoord unpack(long packed) {
        int x = (int) (packed & 0xFFFFF);
        int y = (int) ((packed >> 20) & 0xFFFFF);
        int elev = (int) ((packed >> 40) & 0xFFFF);
        // Handle sign extension for negative values
        if (x >= 0x80000) x |= 0xFFF00000;
        if (y >= 0x80000) y |= 0xFFF00000;
        if (elev >= 0x8000) elev |= 0xFFFF0000;
        return new TileCoord(x, y, elev);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", elev=" + elevation + ")";
    }
}
```

---

## TriggerData Sealed Interface

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerData.java`

```java
/**
 * Base interface for all trigger data types.
 * <p>
 * Uses sealed interface pattern for type-safe trigger configuration.
 * Each collision type that requires metadata has a corresponding record.
 */
public sealed interface TriggerData
    permits WarpTriggerData, DoorTriggerData, StairsTriggerData, ScriptTriggerData {

    /**
     * When the trigger activates.
     */
    ActivationMode activationMode();

    /**
     * If true, trigger only fires once per game session.
     */
    boolean oneShot();

    /**
     * If true, only the player can activate this trigger.
     */
    boolean playerOnly();

    /**
     * Returns the collision type this data is for.
     */
    CollisionType collisionType();

    /**
     * Validates that all required fields are set.
     * @return List of validation errors, empty if valid.
     */
    default List<String> validate() {
        return List.of();
    }
}
```

---

## Trigger Data Records

### WarpTriggerData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/WarpTriggerData.java`

```java
/**
 * Trigger data for WARP collision type.
 * <p>
 * Teleports the player to another scene at a specific position.
 */
public record WarpTriggerData(
    String targetScene,
    int targetX,
    int targetY,
    TransitionType transition,
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {

    /**
     * Default constructor with common defaults.
     */
    public WarpTriggerData(String targetScene, int targetX, int targetY) {
        this(targetScene, targetX, targetY,
             TransitionType.FADE,
             ActivationMode.ON_ENTER,
             false,
             true);
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.WARP;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (targetScene == null || targetScene.isBlank()) {
            errors.add("Target scene is required");
        }
        return errors;
    }
}
```

### DoorTriggerData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/DoorTriggerData.java`

```java
/**
 * Trigger data for DOOR collision type.
 * <p>
 * Represents a door that may be locked and leads to another location.
 */
public record DoorTriggerData(
    boolean locked,
    String requiredKey,      // Item ID required to unlock (null if not locked)
    boolean consumeKey,      // Whether to remove key from inventory on use
    String lockedMessage,    // Message shown when door is locked
    String targetScene,      // Destination scene (null = same scene)
    int targetX,
    int targetY,
    TransitionType transition,
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {

    /**
     * Simple unlocked door constructor.
     */
    public DoorTriggerData(String targetScene, int targetX, int targetY) {
        this(false, null, false, null,
             targetScene, targetX, targetY,
             TransitionType.FADE,
             ActivationMode.ON_INTERACT,
             false,
             true);
    }

    /**
     * Locked door constructor.
     */
    public DoorTriggerData(String requiredKey, String lockedMessage,
                           String targetScene, int targetX, int targetY) {
        this(true, requiredKey, true, lockedMessage,
             targetScene, targetX, targetY,
             TransitionType.FADE,
             ActivationMode.ON_INTERACT,
             false,
             true);
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.DOOR;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (locked && (requiredKey == null || requiredKey.isBlank())) {
            errors.add("Locked door requires a key item");
        }
        if (targetScene == null || targetScene.isBlank()) {
            errors.add("Target scene is required");
        }
        return errors;
    }
}
```

### StairsTriggerData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/StairsTriggerData.java`

```java
/**
 * Trigger data for STAIRS_UP and STAIRS_DOWN collision types.
 * <p>
 * Changes the entity's elevation (floor level) when stepped on.
 */
public record StairsTriggerData(
    int targetElevation,     // Target elevation (absolute, not relative)
    Integer targetX,         // Optional: reposition X (null = same X)
    Integer targetY,         // Optional: reposition Y (null = same Y)
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {

    /**
     * Simple stairs constructor - just changes elevation.
     */
    public StairsTriggerData(int targetElevation) {
        this(targetElevation, null, null,
             ActivationMode.ON_ENTER,
             false,
             false);  // NPCs can use stairs
    }

    /**
     * Stairs with repositioning.
     */
    public StairsTriggerData(int targetElevation, int targetX, int targetY) {
        this(targetElevation, targetX, targetY,
             ActivationMode.ON_ENTER,
             false,
             false);
    }

    @Override
    public CollisionType collisionType() {
        // Could be either STAIRS_UP or STAIRS_DOWN based on context
        // The collisionType is stored in CollisionMap, not here
        return null; // Determined by CollisionMap
    }

    @Override
    public List<String> validate() {
        // targetElevation is a primitive, always valid
        return List.of();
    }
}
```

### ScriptTriggerData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/ScriptTriggerData.java`

```java
/**
 * Trigger data for SCRIPT_TRIGGER collision type.
 * <p>
 * Executes a named script when activated. This is the extension point
 * for custom trigger behavior without creating new collision types.
 */
public record ScriptTriggerData(
    String scriptId,
    Map<String, String> parameters,  // Type-safe: String -> String only
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {

    /**
     * Simple script trigger constructor.
     */
    public ScriptTriggerData(String scriptId) {
        this(scriptId, Map.of(),
             ActivationMode.ON_ENTER,
             false,
             true);
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.SCRIPT_TRIGGER;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (scriptId == null || scriptId.isBlank()) {
            errors.add("Script ID is required");
        }
        return errors;
    }
}
```

---

## TriggerDataMap

**File**: `src/main/java/com/pocket/rpg/collision/trigger/TriggerDataMap.java`

```java
/**
 * Stores trigger metadata for tiles that require configuration.
 * <p>
 * Works alongside CollisionMap - CollisionMap stores the type (WARP, DOOR, etc.),
 * TriggerDataMap stores the configuration data for that tile.
 */
public class TriggerDataMap {

    private final Map<Long, TriggerData> triggers = new HashMap<>();

    /**
     * Gets trigger data at coordinates.
     * @return TriggerData or null if not configured
     */
    public TriggerData get(int x, int y, int elevation) {
        return triggers.get(packCoords(x, y, elevation));
    }

    /**
     * Gets trigger data at coordinates.
     */
    public TriggerData get(TileCoord coord) {
        return triggers.get(coord.pack());
    }

    /**
     * Sets trigger data at coordinates.
     */
    public void set(int x, int y, int elevation, TriggerData data) {
        triggers.put(packCoords(x, y, elevation), data);
    }

    /**
     * Sets trigger data at coordinates.
     */
    public void set(TileCoord coord, TriggerData data) {
        triggers.put(coord.pack(), data);
    }

    /**
     * Removes trigger data at coordinates.
     */
    public void remove(int x, int y, int elevation) {
        triggers.remove(packCoords(x, y, elevation));
    }

    /**
     * Removes trigger data at coordinates.
     */
    public void remove(TileCoord coord) {
        triggers.remove(coord.pack());
    }

    /**
     * Checks if coordinates have trigger data configured.
     */
    public boolean has(int x, int y, int elevation) {
        return triggers.containsKey(packCoords(x, y, elevation));
    }

    /**
     * Returns all configured triggers.
     */
    public Map<TileCoord, TriggerData> getAll() {
        Map<TileCoord, TriggerData> result = new HashMap<>();
        for (var entry : triggers.entrySet()) {
            result.put(TileCoord.unpack(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * Returns all triggers of a specific type.
     */
    public List<Map.Entry<TileCoord, TriggerData>> getByType(Class<? extends TriggerData> type) {
        return triggers.entrySet().stream()
            .filter(e -> type.isInstance(e.getValue()))
            .map(e -> Map.entry(TileCoord.unpack(e.getKey()), e.getValue()))
            .toList();
    }

    /**
     * Clears all trigger data.
     */
    public void clear() {
        triggers.clear();
    }

    /**
     * Returns number of configured triggers.
     */
    public int size() {
        return triggers.size();
    }

    private long packCoords(int x, int y, int elevation) {
        return new TileCoord(x, y, elevation).pack();
    }
}
```

---

## Serialization

### JSON Format

```json
{
  "triggers": [
    {
      "coord": {"x": 5, "y": 10, "elevation": 0},
      "type": "WarpTriggerData",
      "data": {
        "targetScene": "cave_entrance",
        "targetX": 3,
        "targetY": 5,
        "transition": "FADE",
        "activationMode": "ON_ENTER",
        "oneShot": false,
        "playerOnly": true
      }
    },
    {
      "coord": {"x": 3, "y": 3, "elevation": 0},
      "type": "DoorTriggerData",
      "data": {
        "locked": true,
        "requiredKey": "rusty_key",
        "consumeKey": true,
        "lockedMessage": "The door is locked. You need a key.",
        "targetScene": "house_interior",
        "targetX": 5,
        "targetY": 8,
        "transition": "FADE",
        "activationMode": "ON_INTERACT",
        "oneShot": false,
        "playerOnly": true
      }
    }
  ]
}
```

### Serialization Support

Use Gson with a custom TypeAdapter to handle the sealed interface:

```java
public class TriggerDataTypeAdapter extends TypeAdapter<TriggerData> {

    @Override
    public void write(JsonWriter out, TriggerData data) throws IOException {
        out.beginObject();
        out.name("type").value(data.getClass().getSimpleName());
        out.name("data");
        // Write the specific record fields
        gson.toJson(data, data.getClass(), out);
        out.endObject();
    }

    @Override
    public TriggerData read(JsonReader in) throws IOException {
        in.beginObject();

        String type = null;
        JsonElement dataElement = null;

        while (in.hasNext()) {
            String name = in.nextName();
            if ("type".equals(name)) {
                type = in.nextString();
            } else if ("data".equals(name)) {
                dataElement = JsonParser.parseReader(in);
            }
        }
        in.endObject();

        Class<? extends TriggerData> clazz = switch (type) {
            case "WarpTriggerData" -> WarpTriggerData.class;
            case "DoorTriggerData" -> DoorTriggerData.class;
            case "StairsTriggerData" -> StairsTriggerData.class;
            case "ScriptTriggerData" -> ScriptTriggerData.class;
            default -> throw new IOException("Unknown trigger type: " + type);
        };

        return gson.fromJson(dataElement, clazz);
    }
}
```

---

## Benefits of This Design

| Aspect | Map<String, Object> | Typed Records |
|--------|---------------------|---------------|
| Type safety | None | Full compile-time checking |
| IDE support | None | Autocomplete, refactoring |
| Validation | Manual | Built into each record |
| Documentation | External | Self-documenting fields |
| Serialization | Error-prone | Type-aware |
| Extension | Add strings | Add new record class |

---

## Summary of Files

| File | Type | Description |
|------|------|-------------|
| `trigger/ActivationMode.java` | NEW | When trigger activates |
| `trigger/TransitionType.java` | NEW | Scene transition types |
| `trigger/TileCoord.java` | NEW | Coordinate packing |
| `trigger/TriggerData.java` | NEW | Sealed interface |
| `trigger/WarpTriggerData.java` | NEW | Warp configuration |
| `trigger/DoorTriggerData.java` | NEW | Door configuration |
| `trigger/StairsTriggerData.java` | NEW | Stairs configuration |
| `trigger/ScriptTriggerData.java` | NEW | Script configuration |
| `trigger/TriggerDataMap.java` | NEW | Storage map |
