package com.pocket.rpg.input;

import com.pocket.rpg.input.listeners.GamepadListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GamepadListener Tests")
class GamepadListenerTest {

    private GamepadListener gamepadListener;

    @BeforeEach
    void setUp() {
        gamepadListener = new GamepadListener();
    }

    @Nested
    @DisplayName("Button Press Detection")
    class ButtonPressTests {

        @Test
        @DisplayName("Should detect button press on first frame")
        void shouldDetectButtonPressOnFirstFrame() {
            gamepadListener.onButtonPressed(GamepadButton.A);

            assertTrue(gamepadListener.wasButtonPressed(GamepadButton.A));
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));
        }

        @Test
        @DisplayName("Should not detect press after endFrame")
        void shouldNotDetectPressAfterEndFrame() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            assertTrue(gamepadListener.wasButtonPressed(GamepadButton.A));

            gamepadListener.endFrame();

            assertFalse(gamepadListener.wasButtonPressed(GamepadButton.A));
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));
        }

        @Test
        @DisplayName("Should handle UNKNOWN button gracefully")
        void shouldHandleUnknownButton() {
            gamepadListener.onButtonPressed(GamepadButton.UNKNOWN);

            assertFalse(gamepadListener.wasButtonPressed(GamepadButton.UNKNOWN));
            assertFalse(gamepadListener.isButtonHeld(GamepadButton.UNKNOWN));
        }

        @Test
        @DisplayName("Should handle null button gracefully")
        void shouldHandleNullButton() {
            assertDoesNotThrow(() -> gamepadListener.onButtonPressed(null));
            assertFalse(gamepadListener.wasButtonPressed(null));
        }

        @Test
        @DisplayName("Should only detect press once even if called multiple times")
        void shouldOnlyDetectPressOnce() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            gamepadListener.onButtonPressed(GamepadButton.A);

            assertTrue(gamepadListener.wasButtonPressed(GamepadButton.A));

            gamepadListener.endFrame();

            assertFalse(gamepadListener.wasButtonPressed(GamepadButton.A));
        }
    }

    @Nested
    @DisplayName("Button Hold Detection")
    class ButtonHoldTests {

        @Test
        @DisplayName("Should detect button held across multiple frames")
        void shouldDetectButtonHeldAcrossFrames() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));

            gamepadListener.endFrame();
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));

            gamepadListener.endFrame();
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));
        }

        @Test
        @DisplayName("Should track multiple buttons held simultaneously")
        void shouldTrackMultipleButtonsHeld() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            gamepadListener.onButtonPressed(GamepadButton.B);
            gamepadListener.onButtonPressed(GamepadButton.X);

            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.B));
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.X));
            assertFalse(gamepadListener.isButtonHeld(GamepadButton.Y));
        }

        @Test
        @DisplayName("Should clear held state on release")
        void shouldClearHeldStateOnRelease() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));

            gamepadListener.onButtonReleased(GamepadButton.A);

            assertFalse(gamepadListener.isButtonHeld(GamepadButton.A));
        }
    }

    @Nested
    @DisplayName("Button Release Detection")
    class ButtonReleaseTests {

        @Test
        @DisplayName("Should detect button release")
        void shouldDetectButtonRelease() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            gamepadListener.endFrame();

            gamepadListener.onButtonReleased(GamepadButton.A);

            assertTrue(gamepadListener.wasButtonReleased(GamepadButton.A));
            assertFalse(gamepadListener.isButtonHeld(GamepadButton.A));
        }

        @Test
        @DisplayName("Should not detect release after endFrame")
        void shouldNotDetectReleaseAfterEndFrame() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            gamepadListener.endFrame();
            gamepadListener.onButtonReleased(GamepadButton.A);
            assertTrue(gamepadListener.wasButtonReleased(GamepadButton.A));

            gamepadListener.endFrame();

            assertFalse(gamepadListener.wasButtonReleased(GamepadButton.A));
        }

        @Test
        @DisplayName("Should handle press and release in same frame")
        void shouldHandlePressAndReleaseInSameFrame() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            gamepadListener.onButtonReleased(GamepadButton.A);

            assertFalse(gamepadListener.wasButtonPressed(GamepadButton.A));
            assertTrue(gamepadListener.wasButtonReleased(GamepadButton.A));
            assertFalse(gamepadListener.isButtonHeld(GamepadButton.A));
        }
    }

    @Nested
    @DisplayName("Analog Axis Tests")
    class AnalogAxisTests {

        @Test
        @DisplayName("Should track axis values")
        void shouldTrackAxisValues() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.5f);

            assertEquals(0.5f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_X), 0.001f);
        }

        @Test
        @DisplayName("Should apply dead zone")
        void shouldApplyDeadZone() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.1f);

            // Default dead zone is 0.15
            assertEquals(0f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_X), 0.001f);
        }

        @Test
        @DisplayName("Should not apply dead zone to values above threshold")
        void shouldNotApplyDeadZoneToLargeValues() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.8f);

            assertEquals(0.8f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_X), 0.001f);
        }

        @Test
        @DisplayName("Should track previous axis values")
        void shouldTrackPreviousAxisValues() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.5f);
            gamepadListener.endFrame();
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.8f);

            assertEquals(0.8f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_X), 0.001f);
            assertEquals(0.5f, gamepadListener.getPreviousAxis(GamepadAxis.LEFT_STICK_X), 0.001f);
        }

        @Test
        @DisplayName("Should detect axis change")
        void shouldDetectAxisChange() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.5f);
            gamepadListener.endFrame();
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.8f);

            assertTrue(gamepadListener.hasAxisChanged(GamepadAxis.LEFT_STICK_X, 0.1f));
        }

        @Test
        @DisplayName("Should handle null axis gracefully")
        void shouldHandleNullAxis() {
            assertDoesNotThrow(() -> gamepadListener.onAxisChanged(null, 0.5f));
            assertEquals(0f, gamepadListener.getAxis(null), 0.001f);
        }

        @Test
        @DisplayName("Should allow custom dead zone")
        void shouldAllowCustomDeadZone() {
            gamepadListener.setDeadZone(0.3f);

            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.25f);
            assertEquals(0f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_X), 0.001f);

            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.4f);
            assertEquals(0.4f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_X), 0.001f);

            assertEquals(0.3f, gamepadListener.getDeadZone(), 0.001f);
        }
    }

    @Nested
    @DisplayName("Any Button Detection")
    class AnyButtonTests {

        @Test
        @DisplayName("Should detect any button held")
        void shouldDetectAnyButtonHeld() {
            assertFalse(gamepadListener.isAnyButtonHeld());

            gamepadListener.onButtonPressed(GamepadButton.A);
            assertTrue(gamepadListener.isAnyButtonHeld());
        }

        @Test
        @DisplayName("Should detect any button pressed")
        void shouldDetectAnyButtonPressed() {
            assertFalse(gamepadListener.wasAnyButtonPressed());

            gamepadListener.onButtonPressed(GamepadButton.A);
            assertTrue(gamepadListener.wasAnyButtonPressed());

            gamepadListener.endFrame();
            assertFalse(gamepadListener.wasAnyButtonPressed());
        }
    }

    @Nested
    @DisplayName("Convenience Methods")
    class ConvenienceTests {

        @Test
        @DisplayName("Should get left stick as vector")
        void shouldGetLeftStickAsVector() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.5f);
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 0.8f);

            float[] stick = gamepadListener.getLeftStick();
            assertEquals(0.5f, stick[0], 0.001f);
            assertEquals(0.8f, stick[1], 0.001f);
        }

        @Test
        @DisplayName("Should get right stick as vector")
        void shouldGetRightStickAsVector() {
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_X, -0.3f);
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_Y, 0.6f);

            float[] stick = gamepadListener.getRightStick();
            assertEquals(-0.3f, stick[0], 0.001f);
            assertEquals(0.6f, stick[1], 0.001f);
        }

        @Test
        @DisplayName("Should detect left stick active")
        void shouldDetectLeftStickActive() {
            assertFalse(gamepadListener.isLeftStickActive());

            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.5f);
            assertTrue(gamepadListener.isLeftStickActive());
        }

        @Test
        @DisplayName("Should detect right stick active")
        void shouldDetectRightStickActive() {
            assertFalse(gamepadListener.isRightStickActive());

            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_Y, -0.8f);
            assertTrue(gamepadListener.isRightStickActive());
        }
    }

    @Nested
    @DisplayName("Clear Functionality")
    class ClearTests {

        @Test
        @DisplayName("Should clear all button states")
        void shouldClearAllButtonStates() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            gamepadListener.onButtonPressed(GamepadButton.B);

            gamepadListener.clear();

            assertFalse(gamepadListener.isButtonHeld(GamepadButton.A));
            assertFalse(gamepadListener.isButtonHeld(GamepadButton.B));
            assertFalse(gamepadListener.isAnyButtonHeld());
        }

        @Test
        @DisplayName("Should clear all axis values")
        void shouldClearAllAxisValues() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.5f);
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_Y, 0.8f);

            gamepadListener.clear();

            assertEquals(0f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_X), 0.001f);
            assertEquals(0f, gamepadListener.getAxis(GamepadAxis.RIGHT_STICK_Y), 0.001f);
        }
    }

    @Nested
    @DisplayName("Realistic Gamepad Scenarios")
    class GamepadScenarioTests {

        @Test
        @DisplayName("Should handle analog movement pattern")
        void shouldHandleAnalogMovementPattern() {
            // Gradually push stick forward
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 0.2f);
            gamepadListener.endFrame();

            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 0.5f);
            gamepadListener.endFrame();

            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 1.0f);
            assertEquals(1.0f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_Y), 0.001f);

            // Release stick (spring back to center)
            gamepadListener.endFrame();
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 0.0f);
            assertEquals(0.0f, gamepadListener.getAxis(GamepadAxis.LEFT_STICK_Y), 0.001f);
        }

        @Test
        @DisplayName("Should handle button combo (A + B)")
        void shouldHandleButtonCombo() {
            gamepadListener.onButtonPressed(GamepadButton.A);
            gamepadListener.endFrame();

            gamepadListener.onButtonPressed(GamepadButton.B);

            assertTrue(gamepadListener.isButtonHeld(GamepadButton.A));
            assertTrue(gamepadListener.wasButtonPressed(GamepadButton.B));
        }

        @Test
        @DisplayName("Should handle rapid button tapping")
        void shouldHandleRapidButtonTapping() {
            for (int i = 0; i < 10; i++) {
                gamepadListener.onButtonPressed(GamepadButton.A);
                assertTrue(gamepadListener.wasButtonPressed(GamepadButton.A));

                gamepadListener.endFrame();

                gamepadListener.onButtonReleased(GamepadButton.A);
                assertTrue(gamepadListener.wasButtonReleased(GamepadButton.A));

                gamepadListener.endFrame();
            }

            assertFalse(gamepadListener.isButtonHeld(GamepadButton.A));
        }

        @Test
        @DisplayName("Should handle D-pad navigation")
        void shouldHandleDPadNavigation() {
            // Press right
            gamepadListener.onButtonPressed(GamepadButton.DPAD_RIGHT);
            assertTrue(gamepadListener.wasButtonPressed(GamepadButton.DPAD_RIGHT));
            gamepadListener.endFrame();

            // Hold right, then press down (diagonal)
            gamepadListener.onButtonPressed(GamepadButton.DPAD_DOWN);
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.DPAD_RIGHT));
            assertTrue(gamepadListener.wasButtonPressed(GamepadButton.DPAD_DOWN));
            gamepadListener.endFrame();

            // Release right, keep down
            gamepadListener.onButtonReleased(GamepadButton.DPAD_RIGHT);
            assertFalse(gamepadListener.isButtonHeld(GamepadButton.DPAD_RIGHT));
            assertTrue(gamepadListener.isButtonHeld(GamepadButton.DPAD_DOWN));
        }

        @Test
        @DisplayName("Should handle trigger as axis")
        void shouldHandleTriggerAsAxis() {
            // Partially press trigger
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_TRIGGER, 0.3f);
            assertEquals(0.3f, gamepadListener.getAxis(GamepadAxis.RIGHT_TRIGGER), 0.001f);

            // Fully press trigger
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_TRIGGER, 1.0f);
            assertEquals(1.0f, gamepadListener.getAxis(GamepadAxis.RIGHT_TRIGGER), 0.001f);

            // Release trigger
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_TRIGGER, 0.0f);
            assertEquals(0.0f, gamepadListener.getAxis(GamepadAxis.RIGHT_TRIGGER), 0.001f);
        }

        @Test
        @DisplayName("Should handle both sticks simultaneously")
        void shouldHandleBothSticksSimultaneously() {
            // Left stick for movement
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.8f);
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 0.6f);

            // Right stick for camera
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_X, -0.5f);
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_Y, 0.3f);

            assertTrue(gamepadListener.isLeftStickActive());
            assertTrue(gamepadListener.isRightStickActive());

            float[] leftStick = gamepadListener.getLeftStick();
            float[] rightStick = gamepadListener.getRightStick();

            assertEquals(0.8f, leftStick[0], 0.001f);
            assertEquals(-0.5f, rightStick[0], 0.001f);
        }
    }
}