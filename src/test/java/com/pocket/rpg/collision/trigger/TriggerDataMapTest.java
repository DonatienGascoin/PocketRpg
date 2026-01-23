package com.pocket.rpg.collision.trigger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TriggerDataMap.
 */
class TriggerDataMapTest {

    private TriggerDataMap map;

    @BeforeEach
    void setUp() {
        map = new TriggerDataMap();
    }

    @Test
    @DisplayName("New map is empty")
    void newMapIsEmpty() {
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    @DisplayName("Set and get trigger data")
    void setAndGetTriggerData() {
        WarpTriggerData warp = new WarpTriggerData("cave", "entrance");
        map.set(3, 7, 0, warp);

        TriggerData retrieved = map.get(3, 7, 0);
        assertEquals(warp, retrieved);
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("Set and get with TileCoord")
    void setAndGetWithTileCoord() {
        TileCoord coord = new TileCoord(3, 7, 1);
        DoorTriggerData door = new DoorTriggerData("house", "spawn");
        map.set(coord, door);

        TriggerData retrieved = map.get(coord);
        assertEquals(door, retrieved);
    }

    @Test
    @DisplayName("Get returns null for unset coordinates")
    void getReturnsNullForUnset() {
        assertNull(map.get(0, 0, 0));
        assertNull(map.get(new TileCoord(5, 5)));
    }

    @Test
    @DisplayName("Has returns correct value")
    void hasReturnsCorrectValue() {
        assertFalse(map.has(3, 7, 0));

        map.set(3, 7, 0, new WarpTriggerData("test", "spawn"));

        assertTrue(map.has(3, 7, 0));
        assertTrue(map.has(new TileCoord(3, 7, 0)));
        assertFalse(map.has(3, 7, 1)); // Different elevation
    }

    @Test
    @DisplayName("Remove returns removed data")
    void removeReturnsRemovedData() {
        WarpTriggerData warp = new WarpTriggerData("cave", "entrance");
        map.set(5, 5, 0, warp);

        TriggerData removed = map.remove(5, 5, 0);
        assertEquals(warp, removed);
        assertFalse(map.has(5, 5, 0));
        assertTrue(map.isEmpty());
    }

    @Test
    @DisplayName("Remove returns null for unset coordinates")
    void removeReturnsNullForUnset() {
        assertNull(map.remove(0, 0, 0));
    }

    @Test
    @DisplayName("Clear removes all triggers")
    void clearRemovesAllTriggers() {
        map.set(1, 1, 0, new WarpTriggerData("a", "spawn1"));
        map.set(2, 2, 0, new WarpTriggerData("b", "spawn2"));
        map.set(3, 3, 0, new WarpTriggerData("c", "spawn3"));

        assertEquals(3, map.size());

        map.clear();

        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    @DisplayName("GetAll returns all triggers")
    void getAllReturnsAllTriggers() {
        map.set(1, 1, 0, new WarpTriggerData("a", "spawn"));
        map.set(2, 2, 1, new DoorTriggerData("b", "spawn"));
        map.set(3, 3, -1, new StairsData());

        Map<TileCoord, TriggerData> all = map.getAll();

        assertEquals(3, all.size());
        assertTrue(all.containsKey(new TileCoord(1, 1, 0)));
        assertTrue(all.containsKey(new TileCoord(2, 2, 1)));
        assertTrue(all.containsKey(new TileCoord(3, 3, -1)));
    }

    @Test
    @DisplayName("GetByType filters by trigger type")
    void getByTypeFiltersByTriggerType() {
        map.set(1, 1, 0, new WarpTriggerData("a", "spawn1"));
        map.set(2, 2, 0, new WarpTriggerData("b", "spawn2"));
        map.set(3, 3, 0, new DoorTriggerData("c", "spawn3"));
        map.set(4, 4, 0, new StairsData());

        var warps = map.getByType(WarpTriggerData.class);
        var doors = map.getByType(DoorTriggerData.class);
        var stairs = map.getByType(StairsData.class);

        assertEquals(2, warps.size());
        assertEquals(1, doors.size());
        assertEquals(1, stairs.size());
    }

    @Test
    @DisplayName("Different elevations are separate entries")
    void differentElevationsAreSeparate() {
        map.set(5, 5, 0, new WarpTriggerData("ground", "spawn_ground"));
        map.set(5, 5, 1, new WarpTriggerData("floor1", "spawn_floor1"));
        map.set(5, 5, 2, new WarpTriggerData("floor2", "spawn_floor2"));

        assertEquals(3, map.size());

        WarpTriggerData ground = (WarpTriggerData) map.get(5, 5, 0);
        WarpTriggerData floor1 = (WarpTriggerData) map.get(5, 5, 1);
        WarpTriggerData floor2 = (WarpTriggerData) map.get(5, 5, 2);

        assertEquals("ground", ground.targetScene());
        assertEquals("floor1", floor1.targetScene());
        assertEquals("floor2", floor2.targetScene());
    }

    @Test
    @DisplayName("CopyFrom copies all data")
    void copyFromCopiesAllData() {
        TriggerDataMap source = new TriggerDataMap();
        source.set(1, 1, 0, new WarpTriggerData("a", "spawn1"));
        source.set(2, 2, 0, new DoorTriggerData("b", "spawn2"));

        map.copyFrom(source);

        assertEquals(2, map.size());
        assertTrue(map.has(1, 1, 0));
        assertTrue(map.has(2, 2, 0));
    }
}
