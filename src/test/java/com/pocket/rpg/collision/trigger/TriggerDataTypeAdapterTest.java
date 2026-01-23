package com.pocket.rpg.collision.trigger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TriggerDataTypeAdapter serialization.
 */
class TriggerDataTypeAdapterTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(TriggerData.class, new TriggerDataTypeAdapter())
                .setPrettyPrinting()
                .create();
    }

    @Test
    @DisplayName("Serialize and deserialize WarpTriggerData")
    void serializeDeserializeWarp() {
        WarpTriggerData original = new WarpTriggerData("cave_entrance", "spawn_point",
                TransitionType.FADE, ActivationMode.ON_ENTER, false, true);

        String json = gson.toJson(original, TriggerData.class);
        TriggerData deserialized = gson.fromJson(json, TriggerData.class);

        assertInstanceOf(WarpTriggerData.class, deserialized);
        WarpTriggerData warp = (WarpTriggerData) deserialized;

        assertEquals("cave_entrance", warp.targetScene());
        assertEquals("spawn_point", warp.targetSpawnId());
        assertEquals(TransitionType.FADE, warp.transition());
        assertEquals(ActivationMode.ON_ENTER, warp.activationMode());
        assertFalse(warp.oneShot());
        assertTrue(warp.playerOnly());
    }

    @Test
    @DisplayName("Serialize and deserialize DoorTriggerData")
    void serializeDeserializeDoor() {
        DoorTriggerData original = new DoorTriggerData(true, "rusty_key", true,
                "The door is locked.", "house_interior", "entrance",
                TransitionType.SLIDE_UP, ActivationMode.ON_INTERACT, false, true);

        String json = gson.toJson(original, TriggerData.class);
        TriggerData deserialized = gson.fromJson(json, TriggerData.class);

        assertInstanceOf(DoorTriggerData.class, deserialized);
        DoorTriggerData door = (DoorTriggerData) deserialized;

        assertTrue(door.locked());
        assertEquals("rusty_key", door.requiredKey());
        assertTrue(door.consumeKey());
        assertEquals("The door is locked.", door.lockedMessage());
        assertEquals("house_interior", door.targetScene());
        assertEquals("entrance", door.targetSpawnId());
        assertEquals(TransitionType.SLIDE_UP, door.transition());
    }

    @Test
    @DisplayName("Serialize and deserialize StairsData going up")
    void serializeDeserializeStairsGoingUp() {
        StairsData original = StairsData.goingUp(com.pocket.rpg.collision.Direction.UP);

        String json = gson.toJson(original, TriggerData.class);
        TriggerData deserialized = gson.fromJson(json, TriggerData.class);

        assertInstanceOf(StairsData.class, deserialized);
        StairsData stairs = (StairsData) deserialized;

        assertEquals(com.pocket.rpg.collision.Direction.UP, stairs.exitDirection());
        assertEquals(1, stairs.elevationChange());
    }

    @Test
    @DisplayName("Serialize and deserialize StairsData going down")
    void serializeDeserializeStairsGoingDown() {
        StairsData original = StairsData.goingDown(com.pocket.rpg.collision.Direction.RIGHT);

        String json = gson.toJson(original, TriggerData.class);
        TriggerData deserialized = gson.fromJson(json, TriggerData.class);

        assertInstanceOf(StairsData.class, deserialized);
        StairsData stairs = (StairsData) deserialized;

        assertEquals(com.pocket.rpg.collision.Direction.RIGHT, stairs.exitDirection());
        assertEquals(-1, stairs.elevationChange());
    }

    @Test
    @DisplayName("Serialize and deserialize SpawnPointData")
    void serializeDeserializeSpawnPoint() {
        SpawnPointData original = new SpawnPointData("cave_entrance");

        String json = gson.toJson(original, TriggerData.class);
        TriggerData deserialized = gson.fromJson(json, TriggerData.class);

        assertInstanceOf(SpawnPointData.class, deserialized);
        SpawnPointData spawn = (SpawnPointData) deserialized;

        assertEquals("cave_entrance", spawn.id());
    }

    @Test
    @DisplayName("JSON contains type discriminator")
    void jsonContainsTypeDiscriminator() {
        WarpTriggerData warp = new WarpTriggerData("test", "spawn");
        String json = gson.toJson(warp, TriggerData.class);

        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"WarpTriggerData\""));
        assertTrue(json.contains("\"data\""));
    }

    @Test
    @DisplayName("Null value serializes as null")
    void nullValueSerializesAsNull() {
        String json = gson.toJson(null, TriggerData.class);
        assertEquals("null", json);
    }

    @Test
    @DisplayName("Null value deserializes as null")
    void nullValueDeserializesAsNull() {
        TriggerData result = gson.fromJson("null", TriggerData.class);
        assertNull(result);
    }

    @Test
    @DisplayName("Registry contains all permitted subclasses")
    void registryContainsAllPermittedSubclasses() {
        var types = TriggerDataTypeAdapter.getRegisteredTypes();

        assertTrue(types.contains("WarpTriggerData"));
        assertTrue(types.contains("DoorTriggerData"));
        assertTrue(types.contains("StairsData"));
        assertTrue(types.contains("SpawnPointData"));
        assertEquals(4, types.size());
    }

    @Test
    @DisplayName("GetTypeClass returns correct class")
    void getTypeClassReturnsCorrectClass() {
        assertEquals(WarpTriggerData.class, TriggerDataTypeAdapter.getTypeClass("WarpTriggerData"));
        assertEquals(DoorTriggerData.class, TriggerDataTypeAdapter.getTypeClass("DoorTriggerData"));
        assertEquals(StairsData.class, TriggerDataTypeAdapter.getTypeClass("StairsData"));
        assertEquals(SpawnPointData.class, TriggerDataTypeAdapter.getTypeClass("SpawnPointData"));
        assertNull(TriggerDataTypeAdapter.getTypeClass("UnknownType"));
    }
}
