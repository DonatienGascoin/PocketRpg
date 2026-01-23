package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TriggerData sealed interface and its implementations.
 */
class TriggerDataTest {

    // ==================== WarpTriggerData Tests ====================

    @Test
    @DisplayName("WarpTriggerData simple constructor sets defaults")
    void warpSimpleConstructorSetsDefaults() {
        WarpTriggerData warp = new WarpTriggerData("cave", "cave_entrance");

        assertEquals("cave", warp.targetScene());
        assertEquals("cave_entrance", warp.targetSpawnId());
        assertEquals(TransitionType.FADE, warp.transition());
        assertEquals(ActivationMode.ON_ENTER, warp.activationMode());
        assertFalse(warp.oneShot());
        assertTrue(warp.playerOnly());
    }

    @Test
    @DisplayName("WarpTriggerData validates missing spawn point")
    void warpValidatesMissingSpawnPoint() {
        WarpTriggerData warp = new WarpTriggerData("cave", "");
        List<String> errors = warp.validate();

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("spawn")));
    }

    @Test
    @DisplayName("WarpTriggerData validates null spawn point")
    void warpValidatesNullSpawnPoint() {
        WarpTriggerData warp = new WarpTriggerData("cave", null,
                TransitionType.FADE, ActivationMode.ON_ENTER, false, true);
        List<String> errors = warp.validate();

        assertFalse(errors.isEmpty());
    }

    @Test
    @DisplayName("WarpTriggerData valid when spawn point is set")
    void warpValidWhenSpawnPointSet() {
        WarpTriggerData warp = new WarpTriggerData("cave", "entrance");
        List<String> errors = warp.validate();

        assertTrue(errors.isEmpty());
        assertFalse(warp.hasErrors());
    }

    @Test
    @DisplayName("WarpTriggerData returns WARP collision type")
    void warpReturnsCorrectCollisionType() {
        WarpTriggerData warp = new WarpTriggerData("cave", "spawn1");
        assertEquals(CollisionType.WARP, warp.collisionType());
    }

    @Test
    @DisplayName("WarpTriggerData isCrossScene returns true when scene set")
    void warpIsCrossSceneWhenSceneSet() {
        WarpTriggerData crossScene = new WarpTriggerData("other_scene", "spawn");
        WarpTriggerData sameScene = new WarpTriggerData("", "spawn");

        assertTrue(crossScene.isCrossScene());
        assertFalse(sameScene.isCrossScene());
    }

    // ==================== DoorTriggerData Tests ====================

    @Test
    @DisplayName("DoorTriggerData simple constructor creates unlocked door")
    void doorSimpleConstructorCreatesUnlockedDoor() {
        DoorTriggerData door = new DoorTriggerData("house", "house_entrance");

        assertFalse(door.locked());
        assertEquals("", door.requiredKey());
        assertEquals("house", door.targetScene());
        assertEquals("house_entrance", door.targetSpawnId());
        assertEquals(ActivationMode.ON_INTERACT, door.activationMode());
    }

    @Test
    @DisplayName("DoorTriggerData locked constructor sets lock properties")
    void doorLockedConstructorSetsLockProperties() {
        DoorTriggerData door = new DoorTriggerData("rusty_key", "Locked!", "house", "entrance");

        assertTrue(door.locked());
        assertEquals("rusty_key", door.requiredKey());
        assertEquals("Locked!", door.lockedMessage());
        assertTrue(door.consumeKey());
    }

    @Test
    @DisplayName("DoorTriggerData validates locked door without key")
    void doorValidatesLockedWithoutKey() {
        DoorTriggerData door = new DoorTriggerData(true, "", false, "Locked",
                "house", "spawn", TransitionType.FADE, ActivationMode.ON_INTERACT, false, true);

        List<String> errors = door.validate();
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("key")));
    }

    @Test
    @DisplayName("DoorTriggerData valid without spawn (unlock in place)")
    void doorValidWithoutSpawn() {
        // Door with no destination (unlock in place) is valid
        DoorTriggerData door = new DoorTriggerData("", "");
        List<String> errors = door.validate();

        assertTrue(errors.isEmpty()); // No spawn is valid for doors
    }

    @Test
    @DisplayName("DoorTriggerData returns DOOR collision type")
    void doorReturnsCorrectCollisionType() {
        DoorTriggerData door = new DoorTriggerData("house", "spawn");
        assertEquals(CollisionType.DOOR, door.collisionType());
    }

    @Test
    @DisplayName("DoorTriggerData hasDestination and isCrossScene")
    void doorHasDestinationAndIsCrossScene() {
        DoorTriggerData withDest = new DoorTriggerData("scene", "spawn");
        DoorTriggerData noDest = new DoorTriggerData("", "");
        DoorTriggerData sameScene = new DoorTriggerData("", "local_spawn");

        assertTrue(withDest.hasDestination());
        assertTrue(withDest.isCrossScene());
        assertFalse(noDest.hasDestination());
        assertTrue(sameScene.hasDestination());
        assertFalse(sameScene.isCrossScene());
    }

    // ==================== StairsData Tests ====================

    @Test
    @DisplayName("StairsData default constructor creates upward stairs")
    void stairsDefaultConstructorCreatesUpwardStairs() {
        StairsData stairs = new StairsData();

        assertEquals(Direction.UP, stairs.exitDirection());
        assertEquals(1, stairs.elevationChange());
        assertTrue(stairs.goesUp());
    }

    @Test
    @DisplayName("StairsData goingUp factory creates ascending stairs")
    void stairsGoingUpFactoryCreatesAscendingStairs() {
        StairsData stairs = StairsData.goingUp(Direction.RIGHT);

        assertEquals(Direction.RIGHT, stairs.exitDirection());
        assertEquals(1, stairs.elevationChange());
        assertTrue(stairs.goesUp());
        assertFalse(stairs.goesDown());
    }

    @Test
    @DisplayName("StairsData goingDown factory creates descending stairs")
    void stairsGoingDownFactoryCreatesDescendingStairs() {
        StairsData stairs = StairsData.goingDown(Direction.LEFT);

        assertEquals(Direction.LEFT, stairs.exitDirection());
        assertEquals(-1, stairs.elevationChange());
        assertFalse(stairs.goesUp());
        assertTrue(stairs.goesDown());
    }

    @Test
    @DisplayName("StairsData triggersFor checks exit direction")
    void stairsTriggersForChecksExitDirection() {
        StairsData stairs = new StairsData(Direction.UP, 1);

        assertTrue(stairs.triggersFor(Direction.UP));
        assertFalse(stairs.triggersFor(Direction.DOWN));
        assertFalse(stairs.triggersFor(Direction.LEFT));
        assertFalse(stairs.triggersFor(Direction.RIGHT));
    }

    @Test
    @DisplayName("StairsData always uses ON_EXIT activation")
    void stairsAlwaysUsesOnExitActivation() {
        StairsData stairs = new StairsData();
        assertEquals(ActivationMode.ON_EXIT, stairs.activationMode());
    }

    @Test
    @DisplayName("StairsData validates null direction")
    void stairsValidatesNullDirection() {
        // Constructor should default null to UP
        StairsData stairs = new StairsData(null, 1);
        assertEquals(Direction.UP, stairs.exitDirection());
        assertTrue(stairs.validate().isEmpty());
    }

    @Test
    @DisplayName("StairsData validates zero elevation")
    void stairsValidatesZeroElevation() {
        // Constructor should default 0 to 1
        StairsData stairs = new StairsData(Direction.UP, 0);
        assertEquals(1, stairs.elevationChange());
        assertTrue(stairs.validate().isEmpty());
    }

    @Test
    @DisplayName("StairsData valid with proper values")
    void stairsValidWithProperValues() {
        StairsData stairs = new StairsData(Direction.DOWN, -1);
        assertTrue(stairs.validate().isEmpty());
    }

    @Test
    @DisplayName("StairsData returns STAIRS collision type")
    void stairsReturnsCorrectCollisionType() {
        StairsData stairs = new StairsData();
        assertEquals(CollisionType.STAIRS, stairs.collisionType());
    }

    @Test
    @DisplayName("StairsData withExitDirection creates modified copy")
    void stairsWithExitDirectionCreatesModifiedCopy() {
        StairsData original = new StairsData(Direction.UP, 1);
        StairsData modified = original.withExitDirection(Direction.LEFT);

        // Original unchanged
        assertEquals(Direction.UP, original.exitDirection());

        // Modified has new value
        assertEquals(Direction.LEFT, modified.exitDirection());
        assertEquals(1, modified.elevationChange()); // Preserved
    }

    @Test
    @DisplayName("StairsData withElevationChange creates modified copy")
    void stairsWithElevationChangeCreatesModifiedCopy() {
        StairsData original = new StairsData(Direction.UP, 1);
        StairsData modified = original.withElevationChange(-2);

        // Original unchanged
        assertEquals(1, original.elevationChange());

        // Modified has new value
        assertEquals(-2, modified.elevationChange());
        assertEquals(Direction.UP, modified.exitDirection()); // Preserved
    }

    // ==================== SpawnPointData Tests ====================

    @Test
    @DisplayName("SpawnPointData simple constructor sets ID")
    void spawnPointSimpleConstructorSetsId() {
        SpawnPointData spawn = new SpawnPointData("cave_entrance");

        assertEquals("cave_entrance", spawn.id());
        assertNull(spawn.facingDirection());
    }

    @Test
    @DisplayName("SpawnPointData validates missing ID")
    void spawnPointValidatesMissingId() {
        SpawnPointData spawn = new SpawnPointData("");
        List<String> errors = spawn.validate();

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("id")));
    }

    @Test
    @DisplayName("SpawnPointData returns SPAWN_POINT collision type")
    void spawnPointReturnsCorrectCollisionType() {
        SpawnPointData spawn = new SpawnPointData("test");
        assertEquals(CollisionType.SPAWN_POINT, spawn.collisionType());
    }

    // ==================== Sealed Interface Tests ====================

    @Test
    @DisplayName("Exhaustive switch on TriggerData covers all types")
    void exhaustiveSwitchOnTriggerData() {
        TriggerData warp = new WarpTriggerData("test", "spawn");
        TriggerData door = new DoorTriggerData("test", "spawn");
        TriggerData stairs = new StairsData();
        TriggerData spawn = new SpawnPointData("spawn1");

        // This switch must be exhaustive due to sealed interface
        String warpResult = switch (warp) {
            case WarpTriggerData w -> "warp";
            case DoorTriggerData d -> "door";
            case StairsData s -> "stairs";
            case SpawnPointData sp -> "spawn";
        };

        String doorResult = switch (door) {
            case WarpTriggerData w -> "warp";
            case DoorTriggerData d -> "door";
            case StairsData s -> "stairs";
            case SpawnPointData sp -> "spawn";
        };

        String stairsResult = switch (stairs) {
            case WarpTriggerData w -> "warp";
            case DoorTriggerData d -> "door";
            case StairsData s -> "stairs";
            case SpawnPointData sp -> "spawn";
        };

        String spawnResult = switch (spawn) {
            case WarpTriggerData w -> "warp";
            case DoorTriggerData d -> "door";
            case StairsData s -> "stairs";
            case SpawnPointData sp -> "spawn";
        };

        assertEquals("warp", warpResult);
        assertEquals("door", doorResult);
        assertEquals("stairs", stairsResult);
        assertEquals("spawn", spawnResult);
    }
}
