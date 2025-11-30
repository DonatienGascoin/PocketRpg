package com.pocket.rpg.input;

import com.pocket.rpg.input.listeners.KeyListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for KeyListener.
 * Tests all key state tracking functionality including pressed, held, and released states.
 */
@DisplayName("KeyListener Tests")
class KeyListenerTest {

    private KeyListener keyListener;

    @BeforeEach
    void setUp() {
        keyListener = new KeyListener();
    }

    @Nested
    @DisplayName("Key Press Detection")
    class KeyPressTests {

        @Test
        @DisplayName("Should detect key press on first frame")
        void shouldDetectKeyPressOnFirstFrame() {
            // When
            keyListener.onKeyPressed(KeyCode.W);

            // Then
            assertTrue(keyListener.wasKeyPressed(KeyCode.W), "Should detect key press");
            assertTrue(keyListener.isKeyHeld(KeyCode.W), "Should also be held");
        }

        @Test
        @DisplayName("Should not detect press after endFrame")
        void shouldNotDetectPressAfterEndFrame() {
            // Given
            keyListener.onKeyPressed(KeyCode.W);
            assertTrue(keyListener.wasKeyPressed(KeyCode.W));

            // When
            keyListener.endFrame();

            // Then
            assertFalse(keyListener.wasKeyPressed(KeyCode.W), "Press should be cleared");
            assertTrue(keyListener.isKeyHeld(KeyCode.W), "But should still be held");
        }

        @Test
        @DisplayName("Should only detect press once even if called multiple times same frame")
        void shouldOnlyDetectPressOnce() {
            // When - press same key twice same frame
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.onKeyPressed(KeyCode.W);

            // Then
            assertTrue(keyListener.wasKeyPressed(KeyCode.W), "Should still detect press");
            assertTrue(keyListener.isKeyHeld(KeyCode.W), "Should be held");

            // When - end frame
            keyListener.endFrame();

            // Then
            assertFalse(keyListener.wasKeyPressed(KeyCode.W), "Press should not repeat");
        }

        @Test
        @DisplayName("Should handle UNKNOWN key gracefully")
        void shouldHandleUnknownKey() {
            // When
            keyListener.onKeyPressed(KeyCode.UNKNOWN);

            // Then
            assertFalse(keyListener.wasKeyPressed(KeyCode.UNKNOWN));
            assertFalse(keyListener.isKeyHeld(KeyCode.UNKNOWN));
        }

        @Test
        @DisplayName("Should handle null key gracefully")
        void shouldHandleNullKey() {
            // When/Then - should not throw
            assertDoesNotThrow(() -> keyListener.onKeyPressed(null));
            assertFalse(keyListener.wasKeyPressed(null));
            assertFalse(keyListener.isKeyHeld(null));
        }
    }

    @Nested
    @DisplayName("Key Held Detection")
    class KeyHeldTests {

        @Test
        @DisplayName("Should detect key held across multiple frames")
        void shouldDetectKeyHeldAcrossFrames() {
            // Frame 1
            keyListener.onKeyPressed(KeyCode.W);
            assertTrue(keyListener.isKeyHeld(KeyCode.W));

            // Frame 2
            keyListener.endFrame();
            assertTrue(keyListener.isKeyHeld(KeyCode.W), "Should still be held");
            assertFalse(keyListener.wasKeyPressed(KeyCode.W), "But not 'pressed'");

            // Frame 3
            keyListener.endFrame();
            assertTrue(keyListener.isKeyHeld(KeyCode.W), "Should still be held");
        }

        @Test
        @DisplayName("Should track multiple keys held simultaneously")
        void shouldTrackMultipleKeysHeld() {
            // When
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.onKeyPressed(KeyCode.A);
            keyListener.onKeyPressed(KeyCode.S);

            // Then
            assertTrue(keyListener.isKeyHeld(KeyCode.W));
            assertTrue(keyListener.isKeyHeld(KeyCode.A));
            assertTrue(keyListener.isKeyHeld(KeyCode.S));
            assertFalse(keyListener.isKeyHeld(KeyCode.D));
        }

        @Test
        @DisplayName("Should clear held state on release")
        void shouldClearHeldStateOnRelease() {
            // Given
            keyListener.onKeyPressed(KeyCode.W);
            assertTrue(keyListener.isKeyHeld(KeyCode.W));

            // When
            keyListener.onKeyReleased(KeyCode.W);

            // Then
            assertFalse(keyListener.isKeyHeld(KeyCode.W));
        }
    }

    @Nested
    @DisplayName("Key Release Detection")
    class KeyReleaseTests {

        @Test
        @DisplayName("Should detect key release on release frame")
        void shouldDetectKeyReleaseOnReleaseFrame() {
            // Given
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.endFrame();

            // When
            keyListener.onKeyReleased(KeyCode.W);

            // Then
            assertTrue(keyListener.wasKeyReleased(KeyCode.W), "Should detect release");
            assertFalse(keyListener.isKeyHeld(KeyCode.W), "Should no longer be held");
        }

        @Test
        @DisplayName("Should not detect release after endFrame")
        void shouldNotDetectReleaseAfterEndFrame() {
            // Given
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.endFrame();
            keyListener.onKeyReleased(KeyCode.W);
            assertTrue(keyListener.wasKeyReleased(KeyCode.W));

            // When
            keyListener.endFrame();

            // Then
            assertFalse(keyListener.wasKeyReleased(KeyCode.W), "Release should be cleared");
        }

        @Test
        @DisplayName("Should handle release without press")
        void shouldHandleReleaseWithoutPress() {
            // When - release without ever pressing
            keyListener.onKeyReleased(KeyCode.W);

            // Then - should not crash, just do nothing
            assertTrue(keyListener.wasKeyReleased(KeyCode.W), "Should register release");
            assertFalse(keyListener.isKeyHeld(KeyCode.W));
        }
    }

    @Nested
    @DisplayName("Press and Release Same Frame")
    class SameFramePressReleaseTests {

        @Test
        @DisplayName("Should handle press and release in same frame")
        void shouldHandlePressAndReleaseInSameFrame() {
            // When - press then release before endFrame
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.onKeyReleased(KeyCode.W);

            // Then
            assertFalse(keyListener.wasKeyPressed(KeyCode.W), "Press should be cancelled");
            assertTrue(keyListener.wasKeyReleased(KeyCode.W), "Release should be detected");
            assertFalse(keyListener.isKeyHeld(KeyCode.W), "Should not be held");
        }

        @Test
        @DisplayName("Should clear both states after endFrame")
        void shouldClearBothStatesAfterEndFrame() {
            // Given
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.onKeyReleased(KeyCode.W);

            // When
            keyListener.endFrame();

            // Then
            assertFalse(keyListener.wasKeyPressed(KeyCode.W));
            assertFalse(keyListener.wasKeyReleased(KeyCode.W));
            assertFalse(keyListener.isKeyHeld(KeyCode.W));
        }
    }

    @Nested
    @DisplayName("Modifier Keys")
    class ModifierKeyTests {

        @Test
        @DisplayName("Should detect left shift")
        void shouldDetectLeftShift() {
            keyListener.onKeyPressed(KeyCode.LEFT_SHIFT);
            assertTrue(keyListener.isShiftHeld());
        }

        @Test
        @DisplayName("Should detect right shift")
        void shouldDetectRightShift() {
            keyListener.onKeyPressed(KeyCode.RIGHT_SHIFT);
            assertTrue(keyListener.isShiftHeld());
        }

        @Test
        @DisplayName("Should detect either shift")
        void shouldDetectEitherShift() {
            assertFalse(keyListener.isShiftHeld());

            keyListener.onKeyPressed(KeyCode.LEFT_SHIFT);
            assertTrue(keyListener.isShiftHeld());

            keyListener.onKeyReleased(KeyCode.LEFT_SHIFT);
            assertFalse(keyListener.isShiftHeld());

            keyListener.onKeyPressed(KeyCode.RIGHT_SHIFT);
            assertTrue(keyListener.isShiftHeld());
        }

        @Test
        @DisplayName("Should detect left control")
        void shouldDetectLeftControl() {
            keyListener.onKeyPressed(KeyCode.LEFT_CONTROL);
            assertTrue(keyListener.isControlHeld());
        }

        @Test
        @DisplayName("Should detect right control")
        void shouldDetectRightControl() {
            keyListener.onKeyPressed(KeyCode.RIGHT_CONTROL);
            assertTrue(keyListener.isControlHeld());
        }

        @Test
        @DisplayName("Should detect left alt")
        void shouldDetectLeftAlt() {
            keyListener.onKeyPressed(KeyCode.LEFT_ALT);
            assertTrue(keyListener.isAltHeld());
        }

        @Test
        @DisplayName("Should detect right alt")
        void shouldDetectRightAlt() {
            keyListener.onKeyPressed(KeyCode.RIGHT_ALT);
            assertTrue(keyListener.isAltHeld());
        }

        @Test
        @DisplayName("Should detect multiple modifiers")
        void shouldDetectMultipleModifiers() {
            keyListener.onKeyPressed(KeyCode.LEFT_SHIFT);
            keyListener.onKeyPressed(KeyCode.LEFT_CONTROL);
            keyListener.onKeyPressed(KeyCode.LEFT_ALT);

            assertTrue(keyListener.isShiftHeld());
            assertTrue(keyListener.isControlHeld());
            assertTrue(keyListener.isAltHeld());
        }
    }

    @Nested
    @DisplayName("Any Key Detection")
    class AnyKeyTests {

        @Test
        @DisplayName("Should detect any key held")
        void shouldDetectAnyKeyHeld() {
            assertFalse(keyListener.isAnyKeyHeld());

            keyListener.onKeyPressed(KeyCode.A);
            assertTrue(keyListener.isAnyKeyHeld());
        }

        @Test
        @DisplayName("Should detect any key pressed")
        void shouldDetectAnyKeyPressed() {
            assertFalse(keyListener.wasAnyKeyPressed());

            keyListener.onKeyPressed(KeyCode.A);
            assertTrue(keyListener.wasAnyKeyPressed());

            keyListener.endFrame();
            assertFalse(keyListener.wasAnyKeyPressed());
        }

        @Test
        @DisplayName("Should return false when no keys held")
        void shouldReturnFalseWhenNoKeysHeld() {
            keyListener.onKeyPressed(KeyCode.A);
            assertTrue(keyListener.isAnyKeyHeld());

            keyListener.onKeyReleased(KeyCode.A);
            assertFalse(keyListener.isAnyKeyHeld());
        }
    }

    @Nested
    @DisplayName("Clear Functionality")
    class ClearTests {

        @Test
        @DisplayName("Should clear all key states")
        void shouldClearAllKeyStates() {
            // Given - multiple keys in various states
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.onKeyPressed(KeyCode.A);
            keyListener.onKeyReleased(KeyCode.S);

            assertTrue(keyListener.isKeyHeld(KeyCode.W));
            assertTrue(keyListener.wasKeyPressed(KeyCode.A));
            assertTrue(keyListener.wasKeyReleased(KeyCode.S));

            // When
            keyListener.clear();

            // Then
            assertFalse(keyListener.isKeyHeld(KeyCode.W));
            assertFalse(keyListener.wasKeyPressed(KeyCode.A));
            assertFalse(keyListener.wasKeyReleased(KeyCode.S));
            assertFalse(keyListener.isAnyKeyHeld());
            assertFalse(keyListener.wasAnyKeyPressed());
        }

        @Test
        @DisplayName("Should clear modifier key states")
        void shouldClearModifierKeyStates() {
            // Given
            keyListener.onKeyPressed(KeyCode.LEFT_SHIFT);
            keyListener.onKeyPressed(KeyCode.LEFT_CONTROL);
            assertTrue(keyListener.isShiftHeld());
            assertTrue(keyListener.isControlHeld());

            // When
            keyListener.clear();

            // Then
            assertFalse(keyListener.isShiftHeld());
            assertFalse(keyListener.isControlHeld());
            assertFalse(keyListener.isAltHeld());
        }
    }

    @Nested
    @DisplayName("Realistic Game Scenarios")
    class GameScenarioTests {

        @Test
        @DisplayName("WASD movement pattern")
        void wasdMovementPattern() {
            // Frame 1 - Press W
            keyListener.onKeyPressed(KeyCode.W);
            assertTrue(keyListener.wasKeyPressed(KeyCode.W));
            assertTrue(keyListener.isKeyHeld(KeyCode.W));
            keyListener.endFrame();

            // Frame 2 - Hold W, press A
            assertTrue(keyListener.isKeyHeld(KeyCode.W));
            assertFalse(keyListener.wasKeyPressed(KeyCode.W));
            keyListener.onKeyPressed(KeyCode.A);
            assertTrue(keyListener.wasKeyPressed(KeyCode.A));
            assertTrue(keyListener.isKeyHeld(KeyCode.A));
            keyListener.endFrame();

            // Frame 3 - Hold W and A
            assertTrue(keyListener.isKeyHeld(KeyCode.W));
            assertTrue(keyListener.isKeyHeld(KeyCode.A));
            assertFalse(keyListener.wasKeyPressed(KeyCode.W));
            assertFalse(keyListener.wasKeyPressed(KeyCode.A));
            keyListener.endFrame();

            // Frame 4 - Release W, still hold A
            keyListener.onKeyReleased(KeyCode.W);
            assertFalse(keyListener.isKeyHeld(KeyCode.W));
            assertTrue(keyListener.wasKeyReleased(KeyCode.W));
            assertTrue(keyListener.isKeyHeld(KeyCode.A));
            keyListener.endFrame();

            // Frame 5 - Only A held
            assertFalse(keyListener.wasKeyReleased(KeyCode.W));
            assertTrue(keyListener.isKeyHeld(KeyCode.A));
        }

        @Test
        @DisplayName("Jump with Space (press detection)")
        void jumpWithSpace() {
            // Frame 1 - Press space
            keyListener.onKeyPressed(KeyCode.SPACE);
            assertTrue(keyListener.wasKeyPressed(KeyCode.SPACE));
            // Player jumps here
            keyListener.endFrame();

            // Frame 2 - Hold space (should not jump again)
            assertTrue(keyListener.isKeyHeld(KeyCode.SPACE));
            assertFalse(keyListener.wasKeyPressed(KeyCode.SPACE),
                    "Should not trigger jump again");
            keyListener.endFrame();

            // Frame 3 - Release space
            keyListener.onKeyReleased(KeyCode.SPACE);
            assertFalse(keyListener.isKeyHeld(KeyCode.SPACE));
            keyListener.endFrame();

            // Frame 4 - Press space again
            keyListener.onKeyPressed(KeyCode.SPACE);
            assertTrue(keyListener.wasKeyPressed(KeyCode.SPACE),
                    "Should trigger jump again");
        }

        @Test
        @DisplayName("Ctrl+S save combo")
        void ctrlSSaveCombo() {
            // Frame 1 - Press Ctrl
            keyListener.onKeyPressed(KeyCode.LEFT_CONTROL);
            assertTrue(keyListener.isControlHeld());
            keyListener.endFrame();

            // Frame 2 - Press S while holding Ctrl
            keyListener.onKeyPressed(KeyCode.S);
            assertTrue(keyListener.isControlHeld());
            assertTrue(keyListener.wasKeyPressed(KeyCode.S));
            // Save triggered here
            keyListener.endFrame();

            // Frame 3 - Release both
            keyListener.onKeyReleased(KeyCode.LEFT_CONTROL);
            keyListener.onKeyReleased(KeyCode.S);
            assertFalse(keyListener.isControlHeld());
            assertFalse(keyListener.isKeyHeld(KeyCode.S));
        }

        @Test
        @DisplayName("Quick tap (press and release same frame)")
        void quickTap() {
            // Simulate very fast tap
            keyListener.onKeyPressed(KeyCode.E);
            keyListener.onKeyReleased(KeyCode.E);

            // Should detect the release but not the press
            assertFalse(keyListener.wasKeyPressed(KeyCode.E));
            assertTrue(keyListener.wasKeyReleased(KeyCode.E));
            assertFalse(keyListener.isKeyHeld(KeyCode.E));

            keyListener.endFrame();

            // Next frame - nothing
            assertFalse(keyListener.wasKeyPressed(KeyCode.E));
            assertFalse(keyListener.wasKeyReleased(KeyCode.E));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle multiple endFrame calls")
        void shouldHandleMultipleEndFrameCalls() {
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.endFrame();
            keyListener.endFrame();
            keyListener.endFrame();

            // Should not crash and state should remain consistent
            assertTrue(keyListener.isKeyHeld(KeyCode.W));
            assertFalse(keyListener.wasKeyPressed(KeyCode.W));
        }

        @Test
        @DisplayName("Should handle endFrame before any input")
        void shouldHandleEndFrameBeforeInput() {
            // Should not crash
            assertDoesNotThrow(() -> keyListener.endFrame());
        }

        @Test
        @DisplayName("Should handle all KeyCode values")
        void shouldHandleAllKeyCodeValues() {
            // Test a sampling of different key types
            KeyCode[] testKeys = {
                    KeyCode.A, KeyCode.Z,           // Letters
                    KeyCode.NUM_0, KeyCode.NUM_9,    // Numbers
                    KeyCode.F1, KeyCode.F12,         // Function keys
                    KeyCode.UP, KeyCode.DOWN,        // Arrows
                    KeyCode.SPACE, KeyCode.ENTER,    // Special
                    KeyCode.LEFT_SHIFT, KeyCode.RIGHT_ALT  // Modifiers
            };

            for (KeyCode key : testKeys) {
                keyListener.clear();
                keyListener.onKeyPressed(key);
                assertTrue(keyListener.isKeyHeld(key),
                        "Should handle " + key);
                assertTrue(keyListener.wasKeyPressed(key),
                        "Should detect press for " + key);
            }
        }

        @Test
        @DisplayName("Should handle releasing non-pressed key")
        void shouldHandleReleasingNonPressedKey() {
            // Release a key that was never pressed
            keyListener.onKeyReleased(KeyCode.X);

            assertTrue(keyListener.wasKeyReleased(KeyCode.X));
            assertFalse(keyListener.isKeyHeld(KeyCode.X));
        }

        @Test
        @DisplayName("Should handle rapid press/release cycles")
        void shouldHandleRapidPressReleaseCycles() {
            for (int i = 0; i < 100; i++) {
                keyListener.onKeyPressed(KeyCode.SPACE);
                keyListener.endFrame();
                keyListener.onKeyReleased(KeyCode.SPACE);
                keyListener.endFrame();
            }

            // Should end in clean state
            assertFalse(keyListener.isKeyHeld(KeyCode.SPACE));
            assertFalse(keyListener.wasKeyPressed(KeyCode.SPACE));
            assertFalse(keyListener.wasKeyReleased(KeyCode.SPACE));
        }
    }

    @Nested
    @DisplayName("State Consistency")
    class StateConsistencyTests {

        @Test
        @DisplayName("Held implies either pressed or was previously pressed")
        void heldImpliesPressedOrWasPreviouslyPressed() {
            // When pressed this frame
            keyListener.onKeyPressed(KeyCode.W);
            if (keyListener.isKeyHeld(KeyCode.W)) {
                assertTrue(keyListener.wasKeyPressed(KeyCode.W),
                        "If held this frame, should be pressed this frame");
            }

            // When held next frame
            keyListener.endFrame();
            if (keyListener.isKeyHeld(KeyCode.W)) {
                assertFalse(keyListener.wasKeyPressed(KeyCode.W),
                        "If held but not pressed, was pressed previous frame");
            }
        }

        @Test
        @DisplayName("Cannot be both pressed and released same query")
        void cannotBeBothPressedAndReleased() {
            keyListener.onKeyPressed(KeyCode.W);
            keyListener.onKeyReleased(KeyCode.W);

            // After same-frame press and release
            boolean pressed = keyListener.wasKeyPressed(KeyCode.W);
            boolean released = keyListener.wasKeyReleased(KeyCode.W);

            // At most one should be true (implementation may clear press on release)
            assertFalse(pressed && released,
                    "Key cannot be both pressed and released in same query");
        }

        @Test
        @DisplayName("Released implies was previously held")
        void releasedImpliesWasPreviouslyHeld() {
            // Release without press should still register release
            keyListener.onKeyReleased(KeyCode.W);

            if (keyListener.wasKeyReleased(KeyCode.W)) {
                assertFalse(keyListener.isKeyHeld(KeyCode.W),
                        "Released key should not be held");
            }
        }
    }
}