# Testing and Code Review

## Testing Strategy

### Unit Tests

#### TriggerDataMap Tests

**File**: `src/test/java/com/pocket/rpg/collision/trigger/TriggerDataMapTest.java`

```java
class TriggerDataMapTest {

    @Test
    void setAndGet_returnsStoredData() {
        TriggerDataMap map = new TriggerDataMap();
        WarpTriggerData data = new WarpTriggerData("cave", 5, 10);

        map.set(3, 4, 0, data);

        TriggerData result = map.get(3, 4, 0);
        assertThat(result).isEqualTo(data);
    }

    @Test
    void get_returnsNullForUnsetCoords() {
        TriggerDataMap map = new TriggerDataMap();

        assertThat(map.get(0, 0, 0)).isNull();
    }

    @Test
    void remove_clearsData() {
        TriggerDataMap map = new TriggerDataMap();
        map.set(1, 2, 0, new WarpTriggerData("town", 0, 0));

        map.remove(1, 2, 0);

        assertThat(map.get(1, 2, 0)).isNull();
    }

    @Test
    void has_returnsTrueForExisting() {
        TriggerDataMap map = new TriggerDataMap();
        map.set(5, 5, 1, new StairsTriggerData(2));

        assertThat(map.has(5, 5, 1)).isTrue();
        assertThat(map.has(5, 5, 0)).isFalse();
    }

    @Test
    void getAll_returnsAllTriggers() {
        TriggerDataMap map = new TriggerDataMap();
        map.set(0, 0, 0, new WarpTriggerData("a", 0, 0));
        map.set(1, 1, 0, new WarpTriggerData("b", 0, 0));

        Map<TileCoord, TriggerData> all = map.getAll();

        assertThat(all).hasSize(2);
    }

    @Test
    void getByType_filtersCorrectly() {
        TriggerDataMap map = new TriggerDataMap();
        map.set(0, 0, 0, new WarpTriggerData("a", 0, 0));
        map.set(1, 1, 0, new DoorTriggerData("b", 0, 0));
        map.set(2, 2, 0, new WarpTriggerData("c", 0, 0));

        var warps = map.getByType(WarpTriggerData.class);

        assertThat(warps).hasSize(2);
    }
}
```

#### TileCoord Tests

**File**: `src/test/java/com/pocket/rpg/collision/trigger/TileCoordTest.java`

```java
class TileCoordTest {

    @Test
    void packUnpack_roundTrips() {
        TileCoord original = new TileCoord(100, 200, 3);

        long packed = original.pack();
        TileCoord unpacked = TileCoord.unpack(packed);

        assertThat(unpacked).isEqualTo(original);
    }

    @Test
    void packUnpack_handlesNegativeCoords() {
        TileCoord original = new TileCoord(-50, -100, -1); // negative elevation = basement

        long packed = original.pack();
        TileCoord unpacked = TileCoord.unpack(packed);

        assertThat(unpacked).isEqualTo(original);
    }

    @Test
    void packUnpack_handlesZero() {
        TileCoord original = new TileCoord(0, 0, 0);

        long packed = original.pack();
        TileCoord unpacked = TileCoord.unpack(packed);

        assertThat(unpacked).isEqualTo(original);
    }
}
```

#### TriggerData Validation Tests

**File**: `src/test/java/com/pocket/rpg/collision/trigger/TriggerDataValidationTest.java`

```java
class TriggerDataValidationTest {

    @Test
    void warpTriggerData_requiresTargetScene() {
        WarpTriggerData data = new WarpTriggerData(null, 0, 0);

        List<String> errors = data.validate();

        assertThat(errors).contains("Target scene is required");
    }

    @Test
    void warpTriggerData_validWithScene() {
        WarpTriggerData data = new WarpTriggerData("cave", 0, 0);

        List<String> errors = data.validate();

        assertThat(errors).isEmpty();
    }

    @Test
    void doorTriggerData_lockedRequiresKey() {
        DoorTriggerData data = new DoorTriggerData(
            true, null, false, "Locked",
            "house", 0, 0,
            TransitionType.FADE,
            ActivationMode.ON_INTERACT,
            false, true
        );

        List<String> errors = data.validate();

        assertThat(errors).contains("Locked door requires a key item");
    }
}
```

#### TriggerSystem Tests

**File**: `src/test/java/com/pocket/rpg/collision/trigger/TriggerSystemTest.java`

```java
class TriggerSystemTest {

    private TriggerDataMap triggerDataMap;
    private CollisionMap collisionMap;
    private TriggerSystem triggerSystem;

    @BeforeEach
    void setUp() {
        triggerDataMap = new TriggerDataMap();
        collisionMap = new CollisionMap();
        triggerSystem = new TriggerSystem(triggerDataMap, collisionMap);
    }

    @Test
    void onTileEnter_firesHandler_whenActivationModeIsOnEnter() {
        // Setup
        collisionMap.set(5, 5, 0, CollisionType.WARP);
        WarpTriggerData data = new WarpTriggerData("cave", 0, 0);
        triggerDataMap.set(5, 5, 0, data);

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> {
            handlerCalled.set(true);
        });

        GameObject player = createPlayer();

        // Act
        triggerSystem.onTileEnter(player, 5, 5, 0);

        // Assert
        assertThat(handlerCalled.get()).isTrue();
    }

    @Test
    void onTileEnter_doesNotFire_whenActivationModeIsOnInteract() {
        collisionMap.set(5, 5, 0, CollisionType.DOOR);
        DoorTriggerData data = new DoorTriggerData("house", 0, 0); // ON_INTERACT by default
        triggerDataMap.set(5, 5, 0, data);

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        triggerSystem.registerHandler(DoorTriggerData.class, ctx -> {
            handlerCalled.set(true);
        });

        GameObject player = createPlayer();

        triggerSystem.onTileEnter(player, 5, 5, 0);

        assertThat(handlerCalled.get()).isFalse();
    }

    @Test
    void tryInteract_firesHandler_whenOnTileWithInteractTrigger() {
        collisionMap.set(5, 5, 0, CollisionType.DOOR);
        DoorTriggerData data = new DoorTriggerData("house", 0, 0);
        triggerDataMap.set(5, 5, 0, data);

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        triggerSystem.registerHandler(DoorTriggerData.class, ctx -> {
            handlerCalled.set(true);
        });

        GameObject player = createPlayer();

        boolean result = triggerSystem.tryInteract(player, 5, 5, 0, Direction.UP);

        assertThat(result).isTrue();
        assertThat(handlerCalled.get()).isTrue();
    }

    @Test
    void onTileEnter_respectsPlayerOnlyFlag() {
        collisionMap.set(5, 5, 0, CollisionType.WARP);
        WarpTriggerData data = new WarpTriggerData(
            "cave", 0, 0,
            TransitionType.FADE,
            ActivationMode.ON_ENTER,
            false,
            true  // playerOnly = true
        );
        triggerDataMap.set(5, 5, 0, data);

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> {
            handlerCalled.set(true);
        });

        GameObject npc = createNPC(); // No "Player" tag

        triggerSystem.onTileEnter(npc, 5, 5, 0);

        assertThat(handlerCalled.get()).isFalse();
    }

    private GameObject createPlayer() {
        GameObject player = new GameObject("Player");
        player.addTag("Player");
        return player;
    }

    private GameObject createNPC() {
        return new GameObject("NPC");
    }
}
```

### Integration Tests

#### Serialization Roundtrip Test

**File**: `src/test/java/com/pocket/rpg/collision/trigger/TriggerSerializationTest.java`

```java
class TriggerSerializationTest {

    @Test
    void triggerDataMap_serializesAndDeserializes() {
        TriggerDataMap original = new TriggerDataMap();
        original.set(5, 10, 0, new WarpTriggerData("cave", 3, 5));
        original.set(3, 3, 0, new DoorTriggerData("key", "Locked!", "house", 0, 0));
        original.set(8, 2, 0, new StairsTriggerData(1));

        // Serialize
        String json = serializeTriggerDataMap(original);

        // Deserialize
        TriggerDataMap loaded = deserializeTriggerDataMap(json);

        // Verify
        assertThat(loaded.size()).isEqualTo(3);

        TriggerData warp = loaded.get(5, 10, 0);
        assertThat(warp).isInstanceOf(WarpTriggerData.class);
        assertThat(((WarpTriggerData) warp).targetScene()).isEqualTo("cave");

        TriggerData door = loaded.get(3, 3, 0);
        assertThat(door).isInstanceOf(DoorTriggerData.class);
        assertThat(((DoorTriggerData) door).locked()).isTrue();
    }
}
```

#### GridMovement Integration Test

**File**: `src/test/java/com/pocket/rpg/components/GridMovementTriggerTest.java`

```java
class GridMovementTriggerTest {

    @Test
    void movementComplete_callsTriggerSystemOnEnter() {
        // Setup scene with trigger
        Scene scene = new Scene();
        scene.getCollisionMap().set(5, 5, 0, CollisionType.WARP);
        scene.getTriggerDataMap().set(5, 5, 0, new WarpTriggerData("cave", 0, 0));

        AtomicBoolean triggered = new AtomicBoolean(false);
        scene.getTriggerSystem().registerHandler(WarpTriggerData.class, ctx -> {
            triggered.set(true);
        });

        // Create player at (4, 5)
        GameObject player = new GameObject("Player");
        player.addTag("Player");
        GridMovement movement = player.addComponent(new GridMovement());
        movement.setGridPosition(4, 5);
        scene.addGameObject(player);

        // Move to (5, 5) - the warp tile
        movement.move(Direction.RIGHT);

        // Simulate movement completion
        simulateMovementComplete(movement);

        assertThat(triggered.get()).isTrue();
    }
}
```

---

## Manual Testing Checklist

### Collision Types

- [ ] All 15 collision types appear in CollisionPanel Types tab
- [ ] Types are grouped by category (Movement, Ledges, Terrain, Z-Level, Triggers)
- [ ] Adding new type to enum automatically appears in UI
- [ ] Selected type shows description at bottom
- [ ] Tooltips show on hover

### Trigger List

- [ ] Triggers tab shows count badge
- [ ] All trigger tiles in scene appear in list
- [ ] Unconfigured triggers show warning icon (orange)
- [ ] Clicking trigger in list selects it
- [ ] "Go To" centers camera on trigger
- [ ] "Delete" removes collision tile
- [ ] Filter dropdown works
- [ ] Search filters by scene name or coordinates

### Trigger Inspector

- [ ] Selecting trigger tile shows TriggerInspector
- [ ] WARP shows: target scene dropdown, X/Y fields, transition dropdown
- [ ] DOOR shows: locked checkbox, key field (when locked), destination fields
- [ ] STAIRS shows: target Z field, optional reposition fields
- [ ] SCRIPT shows: script ID field
- [ ] Common options: oneShot, playerOnly checkboxes
- [ ] Apply button saves changes
- [ ] Revert button restores original
- [ ] Delete button removes trigger data (keeps collision tile)
- [ ] Validation warnings show for incomplete data

### Scene View Rendering

- [ ] Trigger tiles show icons (not just colors)
- [ ] Unconfigured triggers show warning icon
- [ ] Selected trigger has pulsing yellow highlight
- [ ] Icons scale with zoom level
- [ ] Icons have shadow for visibility

### Runtime

- [ ] WARP trigger teleports player to target scene
- [ ] DOOR trigger checks lock status
- [ ] Locked door shows message without key
- [ ] Locked door opens with key (and consumes if configured)
- [ ] STAIRS changes player elevation
- [ ] playerOnly triggers don't fire for NPCs
- [ ] oneShot triggers only fire once (within session)

### Serialization

- [ ] Save scene with triggers
- [ ] Load scene - triggers restored
- [ ] Trigger data survives editor restart
- [ ] Invalid trigger data shows error on load

### Undo/Redo

- [ ] Editing trigger properties can be undone
- [ ] Redo restores changes
- [ ] Deleting trigger data can be undone

---

## Code Review

After implementation, spawn a new agent to perform code review.

**Review document**: `Documents/Reviews/trigger-system-review.md`

The review agent should:
1. Read the implementation plan from `Documents/Plans/trigger-system/`
2. Find and read all new/modified files
3. Check for:
   - Adherence to existing code patterns
   - Proper use of existing systems (Undo, Inspector, etc.)
   - Type safety and null handling
   - Error handling and edge cases
   - Documentation and comments
   - Test coverage
4. Write findings to review document

---

## Encyclopedia Documentation

After implementation and review, ask user if encyclopedia should be created/updated:

**Proposed document**: `Documents/Encyclopedia/collision-and-triggers-guide.md`

Sections:
1. Overview of collision system
2. Collision types reference (table)
3. Setting up warps
4. Setting up doors (including locked doors)
5. Setting up stairs for multi-floor levels
6. Common patterns and troubleshooting
