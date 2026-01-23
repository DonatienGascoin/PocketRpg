package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.core.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TriggerSystem runtime behavior.
 */
class TriggerSystemTest {

    private TriggerDataMap triggerDataMap;
    private CollisionMap collisionMap;
    private TriggerSystem triggerSystem;
    private GameObject player;
    private GameObject npc;

    @BeforeEach
    void setUp() {
        triggerDataMap = new TriggerDataMap();
        collisionMap = new CollisionMap();
        triggerSystem = new TriggerSystem(triggerDataMap, collisionMap);

        player = new GameObject("Player");
        npc = new GameObject("NPC");
    }

    // ========================================================================
    // Handler Registration
    // ========================================================================

    @Test
    void registerHandler_addsHandler() {
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> {});

        assertTrue(triggerSystem.hasHandler(WarpTriggerData.class));
        assertEquals(1, triggerSystem.getHandlerCount());
    }

    @Test
    void registerHandler_replacesExisting() {
        AtomicBoolean firstCalled = new AtomicBoolean(false);
        AtomicBoolean secondCalled = new AtomicBoolean(false);

        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> firstCalled.set(true));
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> secondCalled.set(true));

        // Set up a warp trigger
        collisionMap.set(0, 0, 0, CollisionType.WARP);
        triggerDataMap.set(0, 0, 0, new WarpTriggerData(
                "TestScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_ENTER, false, false
        ));

        triggerSystem.onTileEnter(player, 0, 0, 0);

        assertFalse(firstCalled.get());
        assertTrue(secondCalled.get());
        assertEquals(1, triggerSystem.getHandlerCount());
    }

    @Test
    void unregisterHandler_removesHandler() {
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> {});
        triggerSystem.unregisterHandler(WarpTriggerData.class);

        assertFalse(triggerSystem.hasHandler(WarpTriggerData.class));
        assertEquals(0, triggerSystem.getHandlerCount());
    }

    // ========================================================================
    // ON_ENTER Activation
    // ========================================================================

    @Test
    void onTileEnter_firesOnEnterTrigger() {
        AtomicReference<TriggerContext> capturedContext = new AtomicReference<>();
        triggerSystem.registerHandler(WarpTriggerData.class, capturedContext::set);

        WarpTriggerData warpData = new WarpTriggerData(
                "TargetScene", "target_spawn", TransitionType.FADE,
                ActivationMode.ON_ENTER, false, false
        );

        collisionMap.set(5, 5, 0, CollisionType.WARP);
        triggerDataMap.set(5, 5, 0, warpData);

        triggerSystem.onTileEnter(player, 5, 5, 0);

        assertNotNull(capturedContext.get());
        assertEquals(player, capturedContext.get().entity());
        assertEquals(5, capturedContext.get().tileX());
        assertEquals(5, capturedContext.get().tileY());
        assertEquals(0, capturedContext.get().tileElevation());
        assertSame(warpData, capturedContext.get().getData());
    }

    @Test
    void onTileEnter_ignoresNonTriggerTiles() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> triggered.set(true));

        collisionMap.set(0, 0, 0, CollisionType.SOLID); // Not a trigger

        triggerSystem.onTileEnter(player, 0, 0, 0);

        assertFalse(triggered.get());
    }

    @Test
    void onTileEnter_ignoresOnInteractTrigger() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(DoorTriggerData.class, ctx -> triggered.set(true));

        collisionMap.set(0, 0, 0, CollisionType.DOOR);
        triggerDataMap.set(0, 0, 0, new DoorTriggerData(
                false, "", false, "",
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_INTERACT, false, false
        ));

        triggerSystem.onTileEnter(player, 0, 0, 0);

        assertFalse(triggered.get());
    }

    @Test
    void onTileEnter_respectsPlayerOnly() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> triggered.set(true));

        collisionMap.set(0, 0, 0, CollisionType.WARP);
        triggerDataMap.set(0, 0, 0, new WarpTriggerData(
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_ENTER, false, true // playerOnly = true
        ));

        // NPC should not trigger
        triggerSystem.onTileEnter(npc, 0, 0, 0);
        assertFalse(triggered.get());

        // Player should trigger
        triggerSystem.onTileEnter(player, 0, 0, 0);
        assertTrue(triggered.get());
    }

    @Test
    void onTileEnter_handlesMissingTriggerData() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> triggered.set(true));

        collisionMap.set(0, 0, 0, CollisionType.WARP);
        // No trigger data set - common during editing

        // Should not throw, should not trigger
        assertDoesNotThrow(() -> triggerSystem.onTileEnter(player, 0, 0, 0));
        assertFalse(triggered.get());
    }

    // ========================================================================
    // ON_EXIT Activation
    // ========================================================================

    @Test
    void onTileExit_firesOnExitTrigger() {
        AtomicReference<TriggerContext> capturedContext = new AtomicReference<>();
        triggerSystem.registerHandler(WarpTriggerData.class, capturedContext::set);

        WarpTriggerData warpData = new WarpTriggerData(
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_EXIT, false, false
        );

        collisionMap.set(5, 5, 0, CollisionType.WARP);
        triggerDataMap.set(5, 5, 0, warpData);

        triggerSystem.onTileExit(player, 5, 5, 0, Direction.UP);

        assertNotNull(capturedContext.get());
        assertEquals(5, capturedContext.get().tileX());
        assertEquals(5, capturedContext.get().tileY());
    }

    @Test
    void onTileExit_ignoresOnEnterTrigger() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> triggered.set(true));

        collisionMap.set(0, 0, 0, CollisionType.WARP);
        triggerDataMap.set(0, 0, 0, new WarpTriggerData(
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_ENTER, false, false
        ));

        triggerSystem.onTileExit(player, 0, 0, 0, Direction.UP);

        assertFalse(triggered.get());
    }

    // ========================================================================
    // ON_INTERACT Activation
    // ========================================================================

    @Test
    void tryInteract_firesOnInteractTriggerAtCurrentPosition() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(DoorTriggerData.class, ctx -> triggered.set(true));

        collisionMap.set(5, 5, 0, CollisionType.DOOR);
        triggerDataMap.set(5, 5, 0, new DoorTriggerData(
                false, "", false, "",
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_INTERACT, false, false
        ));

        boolean result = triggerSystem.tryInteract(player, 5, 5, 0, Direction.UP);

        assertTrue(result);
        assertTrue(triggered.get());
    }

    @Test
    void tryInteract_firesOnInteractTriggerAtFacingPosition() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(DoorTriggerData.class, ctx -> triggered.set(true));

        // Door is at (5, 6), player at (5, 5) facing UP
        collisionMap.set(5, 6, 0, CollisionType.DOOR);
        triggerDataMap.set(5, 6, 0, new DoorTriggerData(
                false, "", false, "",
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_INTERACT, false, false
        ));

        boolean result = triggerSystem.tryInteract(player, 5, 5, 0, Direction.UP);

        assertTrue(result);
        assertTrue(triggered.get());
    }

    @Test
    void tryInteract_prefersCurrentPositionOverFacing() {
        AtomicReference<Integer> capturedX = new AtomicReference<>();
        triggerSystem.registerHandler(DoorTriggerData.class, ctx -> capturedX.set(ctx.tileX()));

        // Door at current position (5, 5)
        collisionMap.set(5, 5, 0, CollisionType.DOOR);
        triggerDataMap.set(5, 5, 0, new DoorTriggerData(
                false, "", false, "",
                "Scene1", "spawn1", TransitionType.FADE,
                ActivationMode.ON_INTERACT, false, false
        ));

        // Another door at facing position (5, 6)
        collisionMap.set(5, 6, 0, CollisionType.DOOR);
        triggerDataMap.set(5, 6, 0, new DoorTriggerData(
                false, "", false, "",
                "Scene2", "spawn2", TransitionType.FADE,
                ActivationMode.ON_INTERACT, false, false
        ));

        triggerSystem.tryInteract(player, 5, 5, 0, Direction.UP);

        // Should trigger the one at current position
        assertEquals(5, capturedX.get());
    }

    @Test
    void tryInteract_returnsFalseWhenNoTrigger() {
        collisionMap.set(5, 5, 0, CollisionType.NONE);
        collisionMap.set(5, 6, 0, CollisionType.NONE);

        boolean result = triggerSystem.tryInteract(player, 5, 5, 0, Direction.UP);

        assertFalse(result);
    }

    @Test
    void tryInteract_ignoresOnEnterTrigger() {
        AtomicBoolean triggered = new AtomicBoolean(false);
        triggerSystem.registerHandler(WarpTriggerData.class, ctx -> triggered.set(true));

        collisionMap.set(5, 5, 0, CollisionType.WARP);
        triggerDataMap.set(5, 5, 0, new WarpTriggerData(
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_ENTER, false, false
        ));

        boolean result = triggerSystem.tryInteract(player, 5, 5, 0, Direction.UP);

        assertFalse(result);
        assertFalse(triggered.get());
    }

    // ========================================================================
    // Direction Handling
    // ========================================================================

    @Test
    void tryInteract_checksFacingDirectionCorrectly() {
        AtomicReference<Integer> capturedX = new AtomicReference<>();
        AtomicReference<Integer> capturedY = new AtomicReference<>();
        triggerSystem.registerHandler(DoorTriggerData.class, ctx -> {
            capturedX.set(ctx.tileX());
            capturedY.set(ctx.tileY());
        });

        // Test all directions
        for (Direction dir : Direction.values()) {
            capturedX.set(null);
            capturedY.set(null);

            int expectedX = 5 + dir.dx;
            int expectedY = 5 + dir.dy;

            collisionMap.set(expectedX, expectedY, 0, CollisionType.DOOR);
            triggerDataMap.set(expectedX, expectedY, 0, new DoorTriggerData(
                    false, "", false, "",
                    "Scene", "spawn", TransitionType.NONE,
                    ActivationMode.ON_INTERACT, false, false
            ));

            triggerSystem.tryInteract(player, 5, 5, 0, dir);

            assertEquals(expectedX, capturedX.get(), "X for direction " + dir);
            assertEquals(expectedY, capturedY.get(), "Y for direction " + dir);

            // Clean up for next iteration
            collisionMap.set(expectedX, expectedY, 0, CollisionType.NONE);
            triggerDataMap.remove(expectedX, expectedY, 0);
        }
    }

    // ========================================================================
    // Missing Handler
    // ========================================================================

    @Test
    void onTileEnter_handlesMissingHandler() {
        // No handler registered for WarpTriggerData
        collisionMap.set(0, 0, 0, CollisionType.WARP);
        triggerDataMap.set(0, 0, 0, new WarpTriggerData(
                "TargetScene", "spawn", TransitionType.FADE,
                ActivationMode.ON_ENTER, false, false
        ));

        // Should not throw
        assertDoesNotThrow(() -> triggerSystem.onTileEnter(player, 0, 0, 0));
    }

    // ========================================================================
    // Elevation Support
    // ========================================================================

    @Test
    void onTileExit_respectsElevation() {
        AtomicReference<Integer> capturedElevation = new AtomicReference<>();
        triggerSystem.registerHandler(StairsData.class, ctx -> capturedElevation.set(ctx.tileElevation()));

        // Stairs at elevation 1 - StairsData uses ON_EXIT activation
        collisionMap.set(5, 5, 1, CollisionType.STAIRS);
        triggerDataMap.set(5, 5, 1, new StairsData());

        triggerSystem.onTileExit(player, 5, 5, 1, Direction.UP);

        assertEquals(1, capturedElevation.get());
    }

    @Test
    void onTileExit_passesExitDirectionToContext() {
        AtomicReference<Direction> capturedDirection = new AtomicReference<>();
        triggerSystem.registerHandler(StairsData.class, ctx -> capturedDirection.set(ctx.exitDirection()));

        collisionMap.set(5, 5, 0, CollisionType.STAIRS);
        triggerDataMap.set(5, 5, 0, new StairsData());

        triggerSystem.onTileExit(player, 5, 5, 0, Direction.DOWN);

        assertEquals(Direction.DOWN, capturedDirection.get());
    }
}
