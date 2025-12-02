package com.pocket.rpg.input;

import com.pocket.rpg.config.InputConfig;
import com.pocket.rpg.input.listeners.GamepadListener;
import com.pocket.rpg.input.listeners.KeyListener;
import com.pocket.rpg.input.listeners.MouseListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DefaultInputContext Tests")
class DefaultInputContextTest {

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
    }

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should initialize with config")
        void shouldInitializeWithConfig() {
            assertNotNull(context);
        }

        @Test
        @DisplayName("Should call listener endFrame on endFrame")
        void shouldCallListenerEndFrame() {
            keyListener.onKeyPressed(KeyCode.W);
            gamepadListener.onButtonPressed(GamepadButton.A);

            context.endFrame();

            assertFalse(keyListener.wasKeyPressed(KeyCode.W));
            assertFalse(gamepadListener.wasButtonPressed(GamepadButton.A));
        }

        @Test
        @DisplayName("Should clear all state")
        void shouldClearAllState() {
            keyListener.onKeyPressed(KeyCode.W);
            context.update(0.016f);

            context.clear();

            assertEquals(0f, context.getAxis(InputAxis.HORIZONTAL));
            assertFalse(keyListener.isKeyHeld(KeyCode.W));
        }
    }

    @Nested
    @DisplayName("Keyboard Delegation Tests")
    class KeyboardTests {

        @Test
        @DisplayName("Should delegate getKey")
        void shouldDelegateGetKey() {
            keyListener.onKeyPressed(KeyCode.W);

            assertTrue(context.getKey(KeyCode.W));
        }

        @Test
        @DisplayName("Should delegate getKeyDown")
        void shouldDelegateGetKeyDown() {
            keyListener.onKeyPressed(KeyCode.SPACE);

            assertTrue(context.getKeyDown(KeyCode.SPACE));
        }

        @Test
        @DisplayName("Should delegate getKeyUp")
        void shouldDelegateGetKeyUp() {
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.endFrame();
            keyListener.onKeyReleased(KeyCode.W);

            assertTrue(context.getKeyUp(KeyCode.W));
        }

        @Test
        @DisplayName("Should delegate anyKey")
        void shouldDelegateAnyKey() {
            keyListener.onKeyPressed(KeyCode.A);

            assertTrue(context.anyKey());
        }
    }

    @Nested
    @DisplayName("Action Tests")
    class ActionTests {

        @Test
        @DisplayName("Should detect action held")
        void shouldDetectActionHeld() {
            keyListener.onKeyPressed(KeyCode.SPACE);

            assertTrue(context.isActionHeld(InputAction.FIRE));
        }

        @Test
        @DisplayName("Should detect action pressed")
        void shouldDetectActionPressed() {
            keyListener.onKeyPressed(KeyCode.E);

            assertTrue(context.isActionPressed(InputAction.INTERACT));
        }

        @Test
        @DisplayName("Should detect action with multiple bindings")
        void shouldDetectActionWithMultipleBindings() {
            // FIRE is bound to both SPACE and MOUSE_BUTTON_LEFT
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);

            assertTrue(context.isActionPressed(InputAction.FIRE));
        }
    }

    @Nested
    @DisplayName("Keyboard Axis Tests")
    class KeyboardAxisTests {

        @Test
        @DisplayName("Should return zero initially")
        void shouldReturnZeroInitially() {
            assertEquals(0f, context.getAxis(InputAxis.HORIZONTAL));
        }

        @Test
        @DisplayName("Should interpolate towards target")
        void shouldInterpolateTowardsTarget() {
            keyListener.onKeyPressed(KeyCode.D);

            context.update(0.016f);
            float value1 = context.getAxis(InputAxis.HORIZONTAL);
            assertTrue(value1 > 0f && value1 < 1f);

            // Continue updating
            for (int i = 0; i < 100; i++) {
                context.update(0.016f);
            }

            float value2 = context.getAxis(InputAxis.HORIZONTAL);
            assertEquals(1f, value2, 0.01f);
        }

        @Test
        @DisplayName("Should return raw axis immediately")
        void shouldReturnRawAxisImmediately() {
            keyListener.onKeyPressed(KeyCode.D);

            assertEquals(1f, context.getAxisRaw(InputAxis.HORIZONTAL), 0.001f);
        }

        @Test
        @DisplayName("Should handle negative input")
        void shouldHandleNegativeInput() {
            keyListener.onKeyPressed(KeyCode.A);

            assertEquals(-1f, context.getAxisRaw(InputAxis.HORIZONTAL), 0.001f);
        }

        @Test
        @DisplayName("Should handle alternative keys")
        void shouldHandleAlternativeKeys() {
            keyListener.onKeyPressed(KeyCode.RIGHT);

            assertEquals(1f, context.getAxisRaw(InputAxis.HORIZONTAL), 0.001f);
        }

        @Test
        @DisplayName("Should apply gravity when released")
        void shouldApplyGravityWhenReleased() {
            keyListener.onKeyPressed(KeyCode.D);
            for (int i = 0; i < 100; i++) {
                context.update(0.016f);
            }
            float maxValue = context.getAxis(InputAxis.HORIZONTAL);

            keyListener.onKeyReleased(KeyCode.D);
            context.update(0.016f);

            float valueAfterRelease = context.getAxis(InputAxis.HORIZONTAL);
            assertTrue(valueAfterRelease < maxValue);
        }
    }

    @Nested
    @DisplayName("Gamepad Axis Tests")
    class GamepadAxisTests {

        @Test
        @DisplayName("Should read gamepad analog axis")
        void shouldReadGamepadAnalogAxis() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.8f);

            context.update(0.016f);

            // Composite axis should pick up gamepad input
            float value = context.getAxis(InputAxis.HORIZONTAL);
            assertTrue(Math.abs(value - 0.8f) < 0.1f);
        }

        @Test
        @DisplayName("Should handle gamepad button axis")
        void shouldHandleGamepadButtonAxis() {
            gamepadListener.onButtonPressed(GamepadButton.DPAD_RIGHT);

            context.update(0.016f);

            float value = context.getAxis(InputAxis.HORIZONTAL);
            assertTrue(value > 0f);
        }
    }

    @Nested
    @DisplayName("Composite Axis Tests")
    class CompositeAxisTests {

        @Test
        @DisplayName("Should combine keyboard and gamepad")
        void shouldCombineKeyboardAndGamepad() {
            // Keyboard input
            keyListener.onKeyPressed(KeyCode.D);
            context.update(0.016f);
            float keyboardValue = context.getAxis(InputAxis.HORIZONTAL);

            keyListener.onKeyReleased(KeyCode.D);
            for (int i = 0; i < 100; i++) {
                context.update(0.016f);
            }

            // Gamepad input
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.9f);
            context.update(0.016f);
            float gamepadValue = context.getAxis(InputAxis.HORIZONTAL);

            assertTrue(Math.abs(gamepadValue - 0.9f) < 0.1f);
        }

        @Test
        @DisplayName("Should take strongest input")
        void shouldTakeStrongestInput() {
            // Weak keyboard input
            keyListener.onKeyPressed(KeyCode.D);
            context.update(0.016f);

            // Strong gamepad input
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 1.0f);
            context.update(0.016f);

            float value = context.getAxis(InputAxis.HORIZONTAL);
            // Gamepad 1.0 should win over partial keyboard interpolation
            assertTrue(value > 0.9f);
        }

        @Test
        @DisplayName("Should work with both sources simultaneously")
        void shouldWorkWithBothSourcesSimultaneously() {
            keyListener.onKeyPressed(KeyCode.W);
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 0.8f);

            context.update(0.016f);

            float value = context.getAxis(InputAxis.VERTICAL);
            assertTrue(value > 0f);
        }
    }

    @Nested
    @DisplayName("Mouse Axis Tests")
    class MouseAxisTests {

        @Test
        @DisplayName("Should read mouse delta")
        void shouldReadMouseDelta() {
            mouseListener.onMouseMove(0, 0);
            mouseListener.onMouseMove(50, 0);

            context.update(0.016f);

            float value = context.getAxis(InputAxis.MOUSE_X);
            assertEquals(50f, value, 0.1f);
        }

        @Test
        @DisplayName("Should reset mouse delta each frame")
        void shouldResetMouseDeltaEachFrame() {
            mouseListener.onMouseMove(0, 0);
            mouseListener.onMouseMove(50, 0);
            context.update(0.016f);

            mouseListener.endFrame();
            mouseListener.onMouseMove(50, 0); // No movement

            context.update(0.016f);

            float value = context.getAxis(InputAxis.MOUSE_X);
            assertEquals(0f, value, 0.001f);
        }
    }

    @Nested
    @DisplayName("Axis Query Tests")
    class AxisQueryTests {

        @Test
        @DisplayName("Should get axis by enum")
        void shouldGetAxisByEnum() {
            keyListener.onKeyPressed(KeyCode.D);
            context.update(0.016f);

            float value = context.getAxis(InputAxis.HORIZONTAL);
            assertTrue(value > 0f);
        }

        @Test
        @DisplayName("Should get axis by string")
        void shouldGetAxisByString() {
            keyListener.onKeyPressed(KeyCode.D);
            context.update(0.016f);

            float valueEnum = context.getAxis(InputAxis.HORIZONTAL);
            float valueString = context.getAxis("Horizontal");

            assertEquals(valueEnum, valueString, 0.001f);
        }

        @Test
        @DisplayName("Should handle case insensitive string")
        void shouldHandleCaseInsensitiveString() {
            keyListener.onKeyPressed(KeyCode.D);
            context.update(0.016f);

            float value1 = context.getAxis("horizontal");
            float value2 = context.getAxis("HORIZONTAL");
            float value3 = context.getAxis("Horizontal");

            assertEquals(value1, value2, 0.001f);
            assertEquals(value2, value3, 0.001f);
        }

        @Test
        @DisplayName("Should return zero for unknown axis")
        void shouldReturnZeroForUnknownAxis() {
            assertEquals(0f, context.getAxis("UnknownAxis"), 0.001f);
        }

        @Test
        @DisplayName("Should allow manual axis setting")
        void shouldAllowManualAxisSetting() {
            context.setAxisValue("Horizontal", 0.75f);

            assertEquals(0.75f, context.getAxis("Horizontal"), 0.001f);
        }
    }

    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("Should handle player movement scenario")
        void shouldHandlePlayerMovementScenario() {
            // Player uses WASD
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.onKeyPressed(KeyCode.D);

            context.update(0.016f);

            float horizontal = context.getAxis(InputAxis.HORIZONTAL);
            float vertical = context.getAxis(InputAxis.VERTICAL);

            assertTrue(horizontal > 0f);
            assertTrue(vertical > 0f);
        }

        @Test
        @DisplayName("Should handle gamepad player movement")
        void shouldHandleGamepadPlayerMovement() {
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_X, 0.8f);
            gamepadListener.onAxisChanged(GamepadAxis.LEFT_STICK_Y, 0.6f);

            context.update(0.016f);

            float horizontal = context.getAxis(InputAxis.HORIZONTAL);
            float vertical = context.getAxis(InputAxis.VERTICAL);

            assertTrue(Math.abs(horizontal - 0.8f) < 0.1f);
            assertTrue(Math.abs(vertical - 0.6f) < 0.1f);
        }

        @Test
        @DisplayName("Should handle camera control with mouse")
        void shouldHandleCameraControlWithMouse() {
            mouseListener.onMouseMove(400, 300);
            mouseListener.onMouseMove(450, 280);

            context.update(0.016f);

            float lookX = context.getAxis(InputAxis.MOUSE_X);
            float lookY = context.getAxis(InputAxis.MOUSE_Y);

            assertEquals(50f, lookX, 0.1f);
            assertEquals(-20f, lookY, 0.1f);
        }

        @Test
        @DisplayName("Should handle camera control with gamepad")
        void shouldHandleCameraControlWithGamepad() {
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_X, 0.7f);
            gamepadListener.onAxisChanged(GamepadAxis.RIGHT_STICK_Y, -0.5f);

            context.update(0.016f);

            float lookX = context.getAxis(InputAxis.MOUSE_X);
            float lookY = context.getAxis(InputAxis.MOUSE_Y);

            // Gamepad look has higher sensitivity (3.0f)
            assertTrue(Math.abs(lookX - 2.1f) < 0.2f);
            assertTrue(Math.abs(lookY + 1.5f) < 0.2f);
        }
    }
}