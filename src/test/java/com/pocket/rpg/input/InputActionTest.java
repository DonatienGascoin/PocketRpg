package com.pocket.rpg.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InputAction Tests")
class InputActionTest {

    @Test
    @DisplayName("Should provide default binding for FIRE")
    void shouldProvideDefaultBindingForFire() {
        List<KeyCode> binding = InputAction.FIRE.getDefaultBinding();

        assertTrue(binding.contains(KeyCode.SPACE));
        assertTrue(binding.contains(KeyCode.MOUSE_BUTTON_LEFT));
    }

    @Test
    @DisplayName("Should provide default binding for JUMP")
    void shouldProvideDefaultBindingForJump() {
        List<KeyCode> binding = InputAction.JUMP.getDefaultBinding();

        assertTrue(binding.contains(KeyCode.SPACE));
        assertEquals(1, binding.size());
    }

    @Test
    @DisplayName("Should provide default binding for all actions")
    void shouldProvideDefaultBindingForAllActions() {
        for (InputAction action : InputAction.values()) {
            List<KeyCode> binding = action.getDefaultBinding();
            assertNotNull(binding);
            assertFalse(binding.isEmpty());
        }
    }
}