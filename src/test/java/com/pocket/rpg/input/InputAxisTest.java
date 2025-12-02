package com.pocket.rpg.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InputAxis Tests")
class InputAxisTest {

    @Test
    @DisplayName("Should provide default config for HORIZONTAL")
    void shouldProvideDefaultConfigForHorizontal() {
        AxisConfig config = InputAxis.HORIZONTAL.getDefaultConfig();

        assertEquals(AxisType.COMPOSITE, config.type());
        assertNotNull(config.sources());
        assertEquals(3, config.sources().length); // Keyboard, left stick, D-pad
    }

    @Test
    @DisplayName("Should provide default config for VERTICAL")
    void shouldProvideDefaultConfigForVertical() {
        AxisConfig config = InputAxis.VERTICAL.getDefaultConfig();

        assertEquals(AxisType.COMPOSITE, config.type());
        assertNotNull(config.sources());
        assertEquals(3, config.sources().length);
    }

    @Test
    @DisplayName("Should provide default config for MOUSE_X")
    void shouldProvideDefaultConfigForMouseX() {
        AxisConfig config = InputAxis.MOUSE_X.getDefaultConfig();

        assertEquals(AxisType.COMPOSITE, config.type());
        assertEquals(2, config.sources().length); // Mouse delta, right stick
    }

    @Test
    @DisplayName("Should provide mouse wheel config")
    void shouldProvideMouseWheelConfig() {
        AxisConfig config = InputAxis.MOUSE_WHEEL.getDefaultConfig();

        assertEquals(AxisType.MOUSE_WHEEL, config.type());
    }

    @Test
    @DisplayName("Should provide gamepad only config for right stick")
    void shouldProvideGamepadOnlyConfigForRightStick() {
        AxisConfig config = InputAxis.RIGHT_STICK_X.getDefaultConfig();

        assertEquals(AxisType.GAMEPAD, config.type());
        assertEquals(GamepadAxis.RIGHT_STICK_X, config.gamepadAxis());
    }
}