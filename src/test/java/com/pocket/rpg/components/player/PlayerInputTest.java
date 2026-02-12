package com.pocket.rpg.components.player;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.testing.MockInputTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerInputTest {

    private MockInputTesting mockInput;
    private GameObject gameObject;
    private PlayerInput playerInput;

    @BeforeEach
    void setUp() {
        mockInput = new MockInputTesting();
        Input.setContext(mockInput);

        gameObject = new GameObject("Player");
        playerInput = new PlayerInput();
        gameObject.addComponent(playerInput);
        gameObject.start();
    }

    @AfterEach
    void tearDown() {
        Input.setContext(null);
    }

    // ========================================================================
    // MODE
    // ========================================================================

    @Nested
    class ModeTests {

        @Test
        void defaultModeIsOverworld() {
            assertEquals(InputMode.OVERWORLD, playerInput.getMode());
            assertTrue(playerInput.isOverworld());
            assertFalse(playerInput.isDialogue());
        }

        @Test
        void setModeChangesMode() {
            playerInput.setMode(InputMode.DIALOGUE);
            assertEquals(InputMode.DIALOGUE, playerInput.getMode());
            assertFalse(playerInput.isOverworld());
            assertTrue(playerInput.isDialogue());
        }

        @Test
        void setModeToBattle() {
            playerInput.setMode(InputMode.BATTLE);
            assertEquals(InputMode.BATTLE, playerInput.getMode());
            assertFalse(playerInput.isOverworld());
            assertFalse(playerInput.isDialogue());
        }

        @Test
        void setModeToMenu() {
            playerInput.setMode(InputMode.MENU);
            assertEquals(InputMode.MENU, playerInput.getMode());
            assertFalse(playerInput.isOverworld());
            assertFalse(playerInput.isDialogue());
        }

        @Test
        void modeCanSwitchBackAndForth() {
            playerInput.setMode(InputMode.DIALOGUE);
            assertTrue(playerInput.isDialogue());

            playerInput.setMode(InputMode.OVERWORLD);
            assertTrue(playerInput.isOverworld());
        }
    }

    // ========================================================================
    // POLLED VALUES — Movement Direction
    // ========================================================================

    @Nested
    class MovementDirectionTests {

        @Test
        void returnsNullWhenNoKeyPressed() {
            assertNull(playerInput.getMovementDirection());
        }

        @Test
        void wKeyReturnsUp() {
            mockInput.pressKey(KeyCode.W);
            assertEquals(Direction.UP, playerInput.getMovementDirection());
        }

        @Test
        void upArrowReturnsUp() {
            mockInput.pressKey(KeyCode.UP);
            assertEquals(Direction.UP, playerInput.getMovementDirection());
        }

        @Test
        void sKeyReturnsDown() {
            mockInput.pressKey(KeyCode.S);
            assertEquals(Direction.DOWN, playerInput.getMovementDirection());
        }

        @Test
        void downArrowReturnsDown() {
            mockInput.pressKey(KeyCode.DOWN);
            assertEquals(Direction.DOWN, playerInput.getMovementDirection());
        }

        @Test
        void aKeyReturnsLeft() {
            mockInput.pressKey(KeyCode.A);
            assertEquals(Direction.LEFT, playerInput.getMovementDirection());
        }

        @Test
        void leftArrowReturnsLeft() {
            mockInput.pressKey(KeyCode.LEFT);
            assertEquals(Direction.LEFT, playerInput.getMovementDirection());
        }

        @Test
        void dKeyReturnsRight() {
            mockInput.pressKey(KeyCode.D);
            assertEquals(Direction.RIGHT, playerInput.getMovementDirection());
        }

        @Test
        void rightArrowReturnsRight() {
            mockInput.pressKey(KeyCode.RIGHT);
            assertEquals(Direction.RIGHT, playerInput.getMovementDirection());
        }

        @Test
        void directionAvailableInAnyMode() {
            mockInput.pressKey(KeyCode.W);

            playerInput.setMode(InputMode.DIALOGUE);
            assertEquals(Direction.UP, playerInput.getMovementDirection());

            playerInput.setMode(InputMode.BATTLE);
            assertEquals(Direction.UP, playerInput.getMovementDirection());
        }

        @Test
        void returnsNullWithNoContext() {
            Input.setContext(null);
            assertNull(playerInput.getMovementDirection());
        }
    }

    // ========================================================================
    // POLLED VALUES — Action Pressed
    // ========================================================================

    @Nested
    class PolledActionTests {

        @Test
        void isInteractPressedReturnsFalseByDefault() {
            assertFalse(playerInput.isInteractPressed());
        }

        @Test
        void isMenuPressedReturnsFalseByDefault() {
            assertFalse(playerInput.isMenuPressed());
        }

        @Test
        void isInteractPressedReturnsFalseWithNoContext() {
            Input.setContext(null);
            assertFalse(playerInput.isInteractPressed());
        }

        @Test
        void isMenuPressedReturnsFalseWithNoContext() {
            Input.setContext(null);
            assertFalse(playerInput.isMenuPressed());
        }
    }

    // ========================================================================
    // CALLBACKS — Mode Filtering
    // ========================================================================

    @Nested
    class CallbackTests {

        @Test
        void overworldInteractCallbackFiresInOverworldMode() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("interact"));

            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);

            assertEquals(1, fired.size());
            assertEquals("interact", fired.get(0));
        }

        @Test
        void overworldInteractCallbackDoesNotFireInDialogueMode() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("interact"));

            playerInput.setMode(InputMode.DIALOGUE);
            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);

            assertTrue(fired.isEmpty());
        }

        @Test
        void dialogueInteractCallbackFiresInDialogueMode() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.DIALOGUE, () -> fired.add("dialogue-interact"));

            playerInput.setMode(InputMode.DIALOGUE);
            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);

            assertEquals(1, fired.size());
        }

        @Test
        void dialogueInteractCallbackDoesNotFireInOverworldMode() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.DIALOGUE, () -> fired.add("dialogue-interact"));

            // Mode is OVERWORLD by default
            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);

            assertTrue(fired.isEmpty());
        }

        @Test
        void menuCallbackFiresInMatchingMode() {
            List<String> fired = new ArrayList<>();
            playerInput.onMenu(InputMode.OVERWORLD, () -> fired.add("menu"));

            mockInput.pressKey(KeyCode.ESCAPE);
            playerInput.update(0.016f);

            assertEquals(1, fired.size());
        }

        @Test
        void menuCallbackDoesNotFireInNonMatchingMode() {
            List<String> fired = new ArrayList<>();
            playerInput.onMenu(InputMode.OVERWORLD, () -> fired.add("menu"));

            playerInput.setMode(InputMode.DIALOGUE);
            mockInput.pressKey(KeyCode.ESCAPE);
            playerInput.update(0.016f);

            assertTrue(fired.isEmpty());
        }

        @Test
        void multipleCallbacksForSameModeAllFire() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("cb1"));
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("cb2"));

            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);

            assertEquals(2, fired.size());
            assertTrue(fired.contains("cb1"));
            assertTrue(fired.contains("cb2"));
        }

        @Test
        void callbacksForDifferentModesAreFiltered() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("overworld"));
            playerInput.onInteract(InputMode.DIALOGUE, () -> fired.add("dialogue"));

            // In OVERWORLD mode — only overworld callback fires
            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);

            assertEquals(1, fired.size());
            assertEquals("overworld", fired.get(0));
        }

        @Test
        void noCallbacksFireWhenNoKeyPressed() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("interact"));
            playerInput.onMenu(InputMode.OVERWORLD, () -> fired.add("menu"));

            playerInput.update(0.016f);

            assertTrue(fired.isEmpty());
        }

        @Test
        void noCallbacksFireWithNoContext() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("interact"));

            Input.setContext(null);
            playerInput.update(0.016f);

            assertTrue(fired.isEmpty());
        }

        @Test
        void callbacksFireAgainAfterModeSwitchBack() {
            List<String> fired = new ArrayList<>();
            playerInput.onInteract(InputMode.OVERWORLD, () -> fired.add("interact"));

            // Fire in OVERWORLD
            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);
            assertEquals(1, fired.size());
            mockInput.endFrame();

            // Switch to DIALOGUE — should NOT fire
            playerInput.setMode(InputMode.DIALOGUE);
            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);
            assertEquals(1, fired.size()); // still 1
            mockInput.endFrame();

            // Switch back to OVERWORLD — should fire again
            playerInput.setMode(InputMode.OVERWORLD);
            mockInput.pressKey(KeyCode.E);
            playerInput.update(0.016f);
            assertEquals(2, fired.size());
        }
    }
}
