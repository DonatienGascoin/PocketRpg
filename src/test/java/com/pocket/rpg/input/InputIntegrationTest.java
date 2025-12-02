package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Input System Integration Tests")
class InputIntegrationTest {

    private InputConfig config;
    private KeyListener keyListener;
    private MouseListener mouseListener;
    private GamepadListener gamepadListener;
    private DefaultInputContext context;

    @BeforeEach
    void setUp() {
        config = new InputConfig();
        keyListener = new KeyListener();
        mouseListener = new MouseListener();
        gamepadListener = new GamepadListener();

        context = new DefaultInputContext(config, keyListener, mouseListener, gamepadListener);
        Input.initialize(context);
    }

    @AfterEach
    void tearDown() {
        Input.destroy();
    }

    @Test
    @DisplayName("Should handle complete player control loop")
    void shouldHandleCompletePlayerControlLoop() {
        // Frame 1: Player presses W and D (diagonal movement)
        keyListener.onKeyPressed(KeyCode.W);
        keyListener.onKeyPressed(KeyCode.D);
        Input.update(0.016f);

        float h1 = Input.getAxis(InputAxis.HORIZONTAL);
        float v1 = Input.getAxis(InputAxis.VERTICAL);
        assertTrue(h1 > 0f && v1 > 0f);

        Input.endFrame();

        // Frame 2: Player also starts camera rotation
        mouseListener.onMouseMove(400, 300);
        mouseListener.onMouseMove(450, 280);
        Input.update(0.016f);

        float lookX = Input.getAxis(InputAxis.MOUSE_X);
        float lookY = Input.getAxis(InputAxis.MOUSE_Y);
        assertTrue(lookX > 0f && lookY < 0f);

        Input.endFrame();

        // Frame 3: Player presses fire button
        keyListener.onKeyPressed(KeyCode.SPACE);
        assertTrue(Input.isActionPressed(InputAction.FIRE));
    }

    @Test
    @DisplayName("Should switch seamlessly between keyboard and gamepad")
    void shouldSwitchBetweenKeyboardAndGamepad() {
        // Start with keyboard
        keyListener.onKeyPressed(KeyCode.D);
        Input.update(0.016f);
        float keyboardValue = Input.getAxis(InputAxis.HORIZONTAL);
        assertTrue(keyboardValue > 0f);

        // Release keyboard
        keyListener.onKeyReleased(KeyCode.D);
        for (int i = 0; i < 100; i++) {
            Input.update(0.016f);
            Input.endFrame();
        }

        // Switch to gamepad
        gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.8f);
        Input.update(0.016f);
        float gamepadValue = Input.getAxis(InputAxis.HORIZONTAL);
        assertTrue(Math.abs(gamepadValue - 0.8f) < 0.2f);
    }

    @Test
    @DisplayName("Should handle full game session simulation")
    void shouldHandleFullGameSessionSimulation() {
        // Simulate 5 seconds of gameplay (300 frames at 60fps)
        for (int frame = 0; frame < 300; frame++) {
            // Every 60 frames, simulate different input
            if (frame % 60 == 0) {
                keyListener.onKeyPressed(KeyCode.W);
            }
            if (frame % 60 == 30) {
                keyListener.onKeyReleased(KeyCode.W);
            }

            Input.update(0.016f);

            // Verify system stays stable
            float value = Input.getAxis(InputAxis.VERTICAL);
            assertTrue(value >= 0f && value <= 1f);

            Input.endFrame();
            keyListener.endFrame();
            mouseListener.endFrame();
            gamepadListener.endFrame();
        }

        // System should still be responsive
        keyListener.onKeyPressed(KeyCode.D);
        Input.update(0.016f);
        assertTrue(Input.getAxis(InputAxis.HORIZONTAL) > 0f);
    }
}