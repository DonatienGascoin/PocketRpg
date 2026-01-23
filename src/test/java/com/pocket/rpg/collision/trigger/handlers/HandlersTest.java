package com.pocket.rpg.collision.trigger.handlers;

import com.pocket.rpg.collision.trigger.*;
import com.pocket.rpg.core.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for trigger handlers.
 */
class HandlersTest {

    private GameObject player;

    @BeforeEach
    void setUp() {
        player = new GameObject("Player");
    }

    // ========================================================================
    // WarpHandler Tests
    // ========================================================================

    @Nested
    class WarpHandlerTests {

        @Test
        void handle_callsCallback() {
            AtomicReference<String> capturedScene = new AtomicReference<>();
            AtomicReference<String> capturedSpawnId = new AtomicReference<>();

            WarpHandler handler = new WarpHandler((entity, scene, spawnId, data) -> {
                capturedScene.set(scene);
                capturedSpawnId.set(spawnId);
            });

            WarpTriggerData warpData = new WarpTriggerData(
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_ENTER, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, warpData);
            handler.handle(context);

            assertEquals("TargetScene", capturedScene.get());
            assertEquals("target_spawn", capturedSpawnId.get());
        }

        @Test
        void handle_withoutCallback_doesNotThrow() {
            WarpHandler handler = new WarpHandler();

            WarpTriggerData warpData = new WarpTriggerData(
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_ENTER, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, warpData);

            assertDoesNotThrow(() -> handler.handle(context));
        }
    }

    // ========================================================================
    // DoorHandler Tests
    // ========================================================================

    @Nested
    class DoorHandlerTests {

        @Test
        void handle_unlockedDoor_callsCallback() {
            AtomicBoolean callbackCalled = new AtomicBoolean(false);

            DoorHandler handler = new DoorHandler(
                    null, // keyChecker
                    null, // keyConsumer
                    null, // messageDisplay
                    (entity, data) -> callbackCalled.set(true)
            );

            DoorTriggerData doorData = new DoorTriggerData(
                    false, "", false, "", // not locked
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_INTERACT, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, doorData);
            handler.handle(context);

            assertTrue(callbackCalled.get());
        }

        @Test
        void handle_lockedDoor_withoutKey_showsMessage() {
            AtomicReference<String> capturedMessage = new AtomicReference<>();
            AtomicBoolean doorOpened = new AtomicBoolean(false);

            DoorHandler handler = new DoorHandler(
                    (entity, keyId) -> false, // hasKey returns false
                    null,
                    capturedMessage::set,
                    (entity, data) -> doorOpened.set(true)
            );

            DoorTriggerData doorData = new DoorTriggerData(
                    true, "gold_key", false, "You need a golden key.",
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_INTERACT, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, doorData);
            handler.handle(context);

            assertFalse(doorOpened.get());
            assertEquals("You need a golden key.", capturedMessage.get());
        }

        @Test
        void handle_lockedDoor_withKey_opensAndConsumes() {
            AtomicBoolean keyConsumed = new AtomicBoolean(false);
            AtomicBoolean doorOpened = new AtomicBoolean(false);

            DoorHandler handler = new DoorHandler(
                    (entity, keyId) -> "gold_key".equals(keyId), // has the key
                    (entity, keyId) -> keyConsumed.set(true),
                    null,
                    (entity, data) -> doorOpened.set(true)
            );

            DoorTriggerData doorData = new DoorTriggerData(
                    true, "gold_key", true, "", // locked, consumeKey = true
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_INTERACT, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, doorData);
            handler.handle(context);

            assertTrue(doorOpened.get());
            assertTrue(keyConsumed.get());
        }

        @Test
        void handle_lockedDoor_withKey_doesNotConsumeIfNotConfigured() {
            AtomicBoolean keyConsumed = new AtomicBoolean(false);

            DoorHandler handler = new DoorHandler(
                    (entity, keyId) -> true, // has the key
                    (entity, keyId) -> keyConsumed.set(true),
                    null,
                    null
            );

            DoorTriggerData doorData = new DoorTriggerData(
                    true, "gold_key", false, "", // locked but consumeKey = false
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_INTERACT, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, doorData);
            handler.handle(context);

            assertFalse(keyConsumed.get());
        }

        @Test
        void handle_defaultLockedMessage() {
            AtomicReference<String> capturedMessage = new AtomicReference<>();

            DoorHandler handler = new DoorHandler(
                    (entity, keyId) -> false,
                    null,
                    capturedMessage::set,
                    null
            );

            DoorTriggerData doorData = new DoorTriggerData(
                    true, "key", false, "", // no custom message
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_INTERACT, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, doorData);
            handler.handle(context);

            assertEquals("The door is locked.", capturedMessage.get());
        }

        @Test
        void handle_withoutCallbacks_doesNotThrow() {
            DoorHandler handler = new DoorHandler();

            DoorTriggerData doorData = new DoorTriggerData(
                    false, "", false, "",
                    "TargetScene", "target_spawn", TransitionType.FADE,
                    ActivationMode.ON_INTERACT, false, false
            );

            TriggerContext context = new TriggerContext(player, 5, 5, 0, doorData);

            assertDoesNotThrow(() -> handler.handle(context));
        }
    }

    // ========================================================================
    // StairsHandler Tests
    // ========================================================================

    @Nested
    class StairsHandlerTests {

        @Test
        void handle_withoutGridMovement_doesNotThrow() {
            StairsHandler handler = new StairsHandler();

            // Player has no GridMovement component
            StairsData stairsData = new StairsData();

            // StairsData uses ON_EXIT - pass exit direction in context
            TriggerContext context = new TriggerContext(player, 5, 5, 1, stairsData,
                    com.pocket.rpg.collision.Direction.UP);

            // Should log error but not throw
            assertDoesNotThrow(() -> handler.handle(context));
        }

        @Test
        void handle_withoutExitDirection_doesNotThrow() {
            StairsHandler handler = new StairsHandler();

            StairsData stairsData = new StairsData();

            // Exit direction null (shouldn't happen in normal use, but test safety)
            TriggerContext context = new TriggerContext(player, 5, 5, 1, stairsData);

            // Should handle gracefully
            assertDoesNotThrow(() -> handler.handle(context));
        }

        // Note: Full StairsHandler testing requires GridMovement component
        // which needs scene initialization. Integration tests would cover this.
    }

    // ========================================================================
    // TriggerContext Tests
    // ========================================================================

    @Nested
    class TriggerContextTests {

        @Test
        void getData_returnsTypedData() {
            WarpTriggerData warpData = new WarpTriggerData(
                    "Scene", "spawn", TransitionType.NONE,
                    ActivationMode.ON_ENTER, false, false
            );

            TriggerContext context = new TriggerContext(player, 0, 0, 0, warpData);

            WarpTriggerData retrieved = context.getData();
            assertEquals("Scene", retrieved.targetScene());
        }

        @Test
        void getData_withDifferentType() {
            DoorTriggerData doorData = new DoorTriggerData(
                    true, "key", false, "Locked!",
                    "Scene", "spawn", TransitionType.FADE,
                    ActivationMode.ON_INTERACT, false, false
            );

            TriggerContext context = new TriggerContext(player, 10, 20, 1, doorData);

            assertEquals(player, context.entity());
            assertEquals(10, context.tileX());
            assertEquals(20, context.tileY());
            assertEquals(1, context.tileElevation());

            DoorTriggerData retrieved = context.getData();
            assertTrue(retrieved.locked());
            assertEquals("key", retrieved.requiredKey());
        }
    }
}
