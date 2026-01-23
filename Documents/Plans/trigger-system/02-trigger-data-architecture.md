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
    permits WarpData, DoorData, StairsData, SpawnPointData {

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
     * Passive marker - no activation (e.g., spawn points).
     */
    NONE,

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
     * For ON_EXIT triggers, the handler receives the exit direction.
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
 * <p>
 * Benefits of sealed interface:
 * - Exhaustive switch expressions (compiler ensures all cases handled)
 * - Adding new type forces updates everywhere it's used
 * - Registry-based serialization via getPermittedSubclasses()
 */
public sealed interface TriggerData
    permits WarpData, DoorData, StairsData, SpawnPointData {

    /**
     * When the trigger activates.
     * - NONE: Passive marker (spawn points)
     * - ON_ENTER: When stepping onto tile (warps)
     * - ON_EXIT: When leaving tile (stairs - receives exit direction)
     * - ON_INTERACT: When pressing action button (doors)
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

### WarpData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/WarpData.java`

```java
/**
 * Trigger data for WARP collision type.
 * <p>
 * Teleports the player to another scene at a spawn point.
 * Uses spawn point IDs instead of raw coordinates for maintainability.
 */
public record WarpData(
    String targetScene,
    String spawnPointId,     // References SpawnPointData.id in target scene
    TransitionType transition,
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {

    /**
     * Default constructor with common defaults.
     */
    public WarpData(String targetScene, String spawnPointId) {
        this(targetScene, spawnPointId,
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
        if (spawnPointId == null || spawnPointId.isBlank()) {
            errors.add("Spawn point ID is required");
        }
        return errors;
    }
}
```

### DoorData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/DoorData.java`

```java
/**
 * Trigger data for DOOR collision type.
 * <p>
 * Represents a door that may be locked and leads to another location.
 * Uses spawn points for arrival position.
 */
public record DoorData(
    boolean locked,
    String requiredKey,      // Item ID required to unlock (null if not locked)
    boolean consumeKey,      // Whether to remove key from inventory on use
    String lockedMessage,    // Message shown when door is locked
    String targetScene,      // Destination scene (null = same scene)
    String spawnPointId,     // References SpawnPointData.id in target scene
    TransitionType transition,
    ActivationMode activationMode,
    boolean oneShot,
    boolean playerOnly
) implements TriggerData {

    /**
     * Simple unlocked door constructor.
     */
    public DoorData(String targetScene, String spawnPointId) {
        this(false, null, false, null,
             targetScene, spawnPointId,
             TransitionType.FADE,
             ActivationMode.ON_INTERACT,
             false,
             true);
    }

    /**
     * Locked door constructor.
     */
    public DoorData(String requiredKey, String lockedMessage,
                    String targetScene, String spawnPointId) {
        this(true, requiredKey, true, lockedMessage,
             targetScene, spawnPointId,
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
        if (spawnPointId == null || spawnPointId.isBlank()) {
            errors.add("Spawn point ID is required");
        }
        return errors;
    }
}
```

### StairsData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/StairsData.java`

```java
/**
 * Trigger data for STAIRS collision type.
 * <p>
 * Changes the entity's elevation based on exit direction.
 * Uses ON_EXIT activation (mandatory) - the handler receives the direction
 * the entity is exiting and looks up the corresponding elevation change.
 * <p>
 * Example: A north-south staircase:
 *   elevationChanges = {NORTH: +1, SOUTH: -1, EAST: 0, WEST: 0}
 *   - Exiting north → elevation increases by 1
 *   - Exiting south → elevation decreases by 1
 *   - Exiting east/west → no change (or blocked)
 */
public record StairsData(
    Map<Direction, Integer> elevationChanges  // Direction → elevation delta
) implements TriggerData {

    /**
     * Simple vertical stairs (north = up, south = down).
     */
    public static StairsData vertical() {
        return new StairsData(Map.of(
            Direction.NORTH, 1,
            Direction.SOUTH, -1
        ));
    }

    /**
     * Simple horizontal stairs (east = up, west = down).
     */
    public static StairsData horizontal() {
        return new StairsData(Map.of(
            Direction.EAST, 1,
            Direction.WEST, -1
        ));
    }

    @Override
    public ActivationMode activationMode() {
        return ActivationMode.ON_EXIT;  // Mandatory for direction-based stairs
    }

    @Override
    public boolean oneShot() {
        return false;
    }

    @Override
    public boolean playerOnly() {
        return false;  // NPCs can use stairs
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.STAIRS;
    }

    @Override
    public List<String> validate() {
        if (elevationChanges == null || elevationChanges.isEmpty()) {
            return List.of("At least one direction must have an elevation change");
        }
        return List.of();
    }
}
```

### SpawnPointData

**File**: `src/main/java/com/pocket/rpg/collision/trigger/SpawnPointData.java`

```java
/**
 * Trigger data for SPAWN_POINT collision type.
 * <p>
 * A passive marker that serves as an arrival point for warps and doors.
 * Does not activate - it's only referenced by other triggers via its ID.
 */
public record SpawnPointData(
    String id  // Unique identifier within this scene (e.g., "entrance", "from_cave")
) implements TriggerData {

    @Override
    public ActivationMode activationMode() {
        return ActivationMode.NONE;  // Passive marker
    }

    @Override
    public boolean oneShot() {
        return false;
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public CollisionType collisionType() {
        return CollisionType.SPAWN_POINT;
    }

    @Override
    public List<String> validate() {
        if (id == null || id.isBlank()) {
            return List.of("Spawn point ID is required");
        }
        return List.of();
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
  "triggerData": {
    "5,10,0": {
      "type": "WarpData",
      "targetScene": "cave",
      "spawnPointId": "entrance",
      "transition": "FADE",
      "activationMode": "ON_ENTER",
      "oneShot": false,
      "playerOnly": true
    },
    "3,3,0": {
      "type": "DoorData",
      "locked": true,
      "requiredKey": "rusty_key",
      "consumeKey": true,
      "lockedMessage": "The door is locked. You need a key.",
      "targetScene": "house_interior",
      "spawnPointId": "front_door",
      "transition": "FADE",
      "activationMode": "ON_INTERACT",
      "oneShot": false,
      "playerOnly": true
    },
    "8,2,0": {
      "type": "StairsData",
      "elevationChanges": {
        "NORTH": 1,
        "SOUTH": -1
      }
    },
    "0,0,0": {
      "type": "SpawnPointData",
      "id": "entrance"
    }
  }
}
```

**Key format**: `"x,y,elevation"` string keys for easy human readability in scene files.

### Serialization Support

Use Gson with a custom TypeAdapter that uses **registry-based discovery** via `getPermittedSubclasses()`:

```java
public class TriggerDataTypeAdapter extends TypeAdapter<TriggerData> {

    private final Gson gson;

    // Registry built automatically from sealed interface permits clause
    private static final Map<String, Class<? extends TriggerData>> TYPE_REGISTRY;

    static {
        TYPE_REGISTRY = new HashMap<>();
        for (Class<?> permitted : TriggerData.class.getPermittedSubclasses()) {
            @SuppressWarnings("unchecked")
            Class<? extends TriggerData> clazz = (Class<? extends TriggerData>) permitted;
            TYPE_REGISTRY.put(clazz.getSimpleName(), clazz);
        }
    }

    public TriggerDataTypeAdapter(Gson gson) {
        this.gson = gson;
    }

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

        // Look up class from registry - no switch needed!
        Class<? extends TriggerData> clazz = TYPE_REGISTRY.get(type);
        if (clazz == null) {
            throw new IOException("Unknown trigger type: " + type);
        }

        return gson.fromJson(dataElement, clazz);
    }
}
```

**Benefits of registry-based approach:**
- Adding a new trigger type to the `permits` clause automatically registers it
- No switch statement to maintain
- Compile-time safety from sealed interface + runtime discovery for serialization

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
| `trigger/ActivationMode.java` | NEW | When trigger activates (NONE, ON_ENTER, ON_EXIT, ON_INTERACT) |
| `trigger/TransitionType.java` | NEW | Scene transition types |
| `trigger/TileCoord.java` | NEW | Coordinate packing |
| `trigger/TriggerData.java` | NEW | Sealed interface |
| `trigger/WarpData.java` | NEW | Warp configuration (spawn point based) |
| `trigger/DoorData.java` | NEW | Door configuration (spawn point based, key/lock) |
| `trigger/StairsData.java` | NEW | Stairs configuration (direction-based elevation, ON_EXIT) |
| `trigger/SpawnPointData.java` | NEW | Spawn point marker (passive, NONE activation) |
| `trigger/TriggerDataMap.java` | NEW | Storage map |
| `trigger/TriggerDataTypeAdapter.java` | NEW | Gson serialization |
