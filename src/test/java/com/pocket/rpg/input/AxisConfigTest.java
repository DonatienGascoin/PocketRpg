package com.pocket.rpg.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AxisConfig Tests")
class AxisConfigTest {

    @Nested
    @DisplayName("Keyboard Axis Creation")
    class KeyboardAxisTests {

        @Test
        @DisplayName("Should create keyboard axis with defaults")
        void shouldCreateKeyboardAxisWithDefaults() {
            AxisConfig config = new AxisConfig(KeyCode.D, KeyCode.A);

            assertEquals(AxisType.KEYBOARD, config.type());
            assertEquals(KeyCode.D, config.positiveKey());
            assertEquals(KeyCode.A, config.negativeKey());
            assertNull(config.altPositiveKey());
            assertNull(config.altNegativeKey());
            assertNull(config.gamepadAxis());
            assertEquals(1.0f, config.sensitivity());
            assertEquals(3.0f, config.gravity());
            assertEquals(0.001f, config.deadZone());
            assertTrue(config.snap());
        }

        @Test
        @DisplayName("Should support fluent API")
        void shouldSupportFluentAPI() {
            AxisConfig config = new AxisConfig(KeyCode.D, KeyCode.A)
                    .withAltKeys(KeyCode.RIGHT, KeyCode.LEFT)
                    .withSensitivity(2.0f)
                    .withGravity(5.0f)
                    .withDeadZone(0.05f)
                    .withSnap(false);

            assertEquals(KeyCode.RIGHT, config.altPositiveKey());
            assertEquals(KeyCode.LEFT, config.altNegativeKey());
            assertEquals(2.0f, config.sensitivity());
            assertEquals(5.0f, config.gravity());
            assertEquals(0.05f, config.deadZone());
            assertFalse(config.snap());
        }

        @Test
        @DisplayName("Should throw on invalid keyboard axis")
        void shouldThrowOnInvalidKeyboardAxis() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AxisConfig(AxisType.KEYBOARD, null, null, null, null,
                            null, null, null, null, 1.0f, 3.0f, 0.001f, true));
        }
    }

    @Nested
    @DisplayName("Gamepad Axis Creation")
    class GamepadAxisTests {

        @Test
        @DisplayName("Should create gamepad analog axis")
        void shouldCreateGamepadAnalogAxis() {
            AxisConfig config = AxisConfig.gamepad(GamepadAxis.LEFT_STICK_X);

            assertEquals(AxisType.GAMEPAD, config.type());
            assertEquals(GamepadAxis.LEFT_STICK_X, config.gamepadAxis());
            assertEquals(1.0f, config.sensitivity());
            assertEquals(0.15f, config.deadZone());
        }

        @Test
        @DisplayName("Should create gamepad axis with custom settings")
        void shouldCreateGamepadAxisWithCustomSettings() {
            AxisConfig config = AxisConfig.gamepad(GamepadAxis.RIGHT_STICK_Y, 2.0f, 0.2f);

            assertEquals(GamepadAxis.RIGHT_STICK_Y, config.gamepadAxis());
            assertEquals(2.0f, config.sensitivity());
            assertEquals(0.2f, config.deadZone());
        }

        @Test
        @DisplayName("Should create gamepad button axis")
        void shouldCreateGamepadButtonAxis() {
            AxisConfig config = AxisConfig.gamepadButtons(
                    GamepadButton.DPAD_RIGHT,
                    GamepadButton.DPAD_LEFT);

            assertEquals(AxisType.GAMEPAD, config.type());
            assertEquals(GamepadButton.DPAD_RIGHT, config.positiveButton());
            assertEquals(GamepadButton.DPAD_LEFT, config.negativeButton());
        }
    }

    @Nested
    @DisplayName("Mouse Axis Creation")
    class MouseAxisTests {

        @Test
        @DisplayName("Should create mouse delta axis")
        void shouldCreateMouseDeltaAxis() {
            AxisConfig config = AxisConfig.mouseDelta(1.5f);

            assertEquals(AxisType.MOUSE_DELTA, config.type());
            assertEquals(1.5f, config.sensitivity());
        }

        @Test
        @DisplayName("Should create mouse wheel axis")
        void shouldCreateMouseWheelAxis() {
            AxisConfig config = AxisConfig.mouseWheel(2.0f);

            assertEquals(AxisType.MOUSE_WHEEL, config.type());
            assertEquals(2.0f, config.sensitivity());
        }
    }

    @Nested
    @DisplayName("Composite Axis Creation")
    class CompositeAxisTests {

        @Test
        @DisplayName("Should create composite axis")
        void shouldCreateCompositeAxis() {
            AxisConfig keyboard = new AxisConfig(KeyCode.D, KeyCode.A);
            AxisConfig gamepad = AxisConfig.gamepad(GamepadAxis.LEFT_STICK_X);

            AxisConfig composite = AxisConfig.composite(keyboard, gamepad);

            assertEquals(AxisType.COMPOSITE, composite.type());
            assertNotNull(composite.sources());
            assertEquals(2, composite.sources().length);
        }

        @Test
        @DisplayName("Should throw on empty composite")
        void shouldThrowOnEmptyComposite() {
            assertThrows(IllegalArgumentException.class,
                    () -> AxisConfig.composite());
        }

        @Test
        @DisplayName("Should throw on null composite sources")
        void shouldThrowOnNullCompositeSources() {
            assertThrows(IllegalArgumentException.class,
                    () -> AxisConfig.composite((AxisConfig[]) null));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw on null axis type")
        void shouldThrowOnNullAxisType() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AxisConfig(null, KeyCode.D, KeyCode.A, null, null,
                            null, null, null, null, 1.0f, 3.0f, 0.001f, true));
        }

        @Test
        @DisplayName("Should throw on invalid sensitivity")
        void shouldThrowOnInvalidSensitivity() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AxisConfig(AxisType.KEYBOARD, KeyCode.D, KeyCode.A, null, null,
                            null, null, null, null, -1.0f, 3.0f, 0.001f, true));
        }

        @Test
        @DisplayName("Should throw on negative gravity")
        void shouldThrowOnNegativeGravity() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AxisConfig(AxisType.KEYBOARD, KeyCode.D, KeyCode.A, null, null,
                            null, null, null, null, 1.0f, -1.0f, 0.001f, true));
        }

        @Test
        @DisplayName("Should throw when gamepad axis has key bindings")
        void shouldThrowWhenGamepadAxisHasKeyBindings() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AxisConfig(AxisType.GAMEPAD, KeyCode.D, null, null, null,
                            GamepadAxis.LEFT_STICK_X, null, null, null, 1.0f, 0f, 0.15f, false));
        }
    }
}