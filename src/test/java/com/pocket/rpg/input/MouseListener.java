package com.pocket.rpg.input;

import com.pocket.rpg.input.listeners.MouseListener;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MouseListener.
 * Tests mouse button states, position tracking, movement delta, scrolling, and drag detection.
 */
@DisplayName("MouseListener Tests")
class MouseListenerTest {

    private MouseListener mouseListener;

    @BeforeEach
    void setUp() {
        mouseListener = new MouseListener();
    }

    @Nested
    @DisplayName("Mouse Button Press Detection")
    class ButtonPressTests {

        @Test
        @DisplayName("Should detect left button press")
        void shouldDetectLeftButtonPress() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);

            assertTrue(mouseListener.wasButtonPressed(KeyCode.MOUSE_BUTTON_LEFT));
            assertTrue(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));
            assertTrue(mouseListener.wasLeftButtonPressed());
            assertTrue(mouseListener.isLeftButtonHeld());
        }

        @Test
        @DisplayName("Should detect right button press")
        void shouldDetectRightButtonPress() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_RIGHT);

            assertTrue(mouseListener.wasButtonPressed(KeyCode.MOUSE_BUTTON_RIGHT));
            assertTrue(mouseListener.wasRightButtonPressed());
        }

        @Test
        @DisplayName("Should detect middle button press")
        void shouldDetectMiddleButtonPress() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_MIDDLE);

            assertTrue(mouseListener.wasButtonPressed(KeyCode.MOUSE_BUTTON_MIDDLE));
            assertTrue(mouseListener.isMiddleButtonHeld());
        }

        @Test
        @DisplayName("Should not detect press after endFrame")
        void shouldNotDetectPressAfterEndFrame() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            assertTrue(mouseListener.wasButtonPressed(KeyCode.MOUSE_BUTTON_LEFT));

            mouseListener.endFrame();

            assertFalse(mouseListener.wasButtonPressed(KeyCode.MOUSE_BUTTON_LEFT));
            assertTrue(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Should handle non-mouse button KeyCodes")
        void shouldHandleNonMouseButtonKeyCodes() {
            // Regular keys should not be treated as mouse buttons
            mouseListener.onMouseButtonPressed(KeyCode.A);

            assertFalse(mouseListener.wasButtonPressed(KeyCode.A));
            assertFalse(mouseListener.isButtonHeld(KeyCode.A));
        }

        @Test
        @DisplayName("Should handle null button")
        void shouldHandleNullButton() {
            assertDoesNotThrow(() -> mouseListener.onMouseButtonPressed(null));
            assertFalse(mouseListener.wasButtonPressed(null));
        }
    }

    @Nested
    @DisplayName("Mouse Button Hold Detection")
    class ButtonHoldTests {

        @Test
        @DisplayName("Should detect button held across multiple frames")
        void shouldDetectButtonHeldAcrossFrames() {
            // Frame 1
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            assertTrue(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));

            // Frame 2
            mouseListener.endFrame();
            assertTrue(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));
            assertFalse(mouseListener.wasButtonPressed(KeyCode.MOUSE_BUTTON_LEFT));

            // Frame 3
            mouseListener.endFrame();
            assertTrue(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Should track multiple buttons held simultaneously")
        void shouldTrackMultipleButtonsHeld() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_RIGHT);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_MIDDLE);

            assertTrue(mouseListener.isLeftButtonHeld());
            assertTrue(mouseListener.isRightButtonHeld());
            assertTrue(mouseListener.isMiddleButtonHeld());
        }

        @Test
        @DisplayName("Should detect any button held")
        void shouldDetectAnyButtonHeld() {
            assertFalse(mouseListener.isAnyButtonHeld());

            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            assertTrue(mouseListener.isAnyButtonHeld());

            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);
            assertFalse(mouseListener.isAnyButtonHeld());
        }
    }

    @Nested
    @DisplayName("Mouse Button Release Detection")
    class ButtonReleaseTests {

        @Test
        @DisplayName("Should detect button release")
        void shouldDetectButtonRelease() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.endFrame();

            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);

            assertTrue(mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_LEFT));
            assertFalse(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Should not detect release after endFrame")
        void shouldNotDetectReleaseAfterEndFrame() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.endFrame();
            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);
            assertTrue(mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_LEFT));

            mouseListener.endFrame();

            assertFalse(mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Should handle press and release same frame")
        void shouldHandlePressAndReleaseSameFrame() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);

            assertFalse(mouseListener.wasButtonPressed(KeyCode.MOUSE_BUTTON_LEFT));
            assertTrue(mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_LEFT));
            assertFalse(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));
        }
    }

    @Nested
    @DisplayName("Mouse Position Tracking")
    class PositionTrackingTests {

        @Test
        @DisplayName("Should track mouse position")
        void shouldTrackMousePosition() {
            mouseListener.onMouseMove(100, 200);

            Vector2f pos = mouseListener.getMousePosition();
            assertEquals(100, pos.x, 0.001);
            assertEquals(200, pos.y, 0.001);

            assertEquals(100, mouseListener.getXPos(), 0.001);
            assertEquals(200, mouseListener.getYPos(), 0.001);
        }

        @Test
        @DisplayName("Should update position on movement")
        void shouldUpdatePositionOnMovement() {
            mouseListener.onMouseMove(100, 200);
            mouseListener.onMouseMove(150, 250);

            Vector2f pos = mouseListener.getMousePosition();
            assertEquals(150, pos.x, 0.001);
            assertEquals(250, pos.y, 0.001);
        }

        @Test
        @DisplayName("Should start at origin by default")
        void shouldStartAtOrigin() {
            Vector2f pos = mouseListener.getMousePosition();
            assertEquals(0, pos.x, 0.001);
            assertEquals(0, pos.y, 0.001);
        }

        @Test
        @DisplayName("Should handle negative coordinates")
        void shouldHandleNegativeCoordinates() {
            mouseListener.onMouseMove(-50, -100);

            Vector2f pos = mouseListener.getMousePosition();
            assertEquals(-50, pos.x, 0.001);
            assertEquals(-100, pos.y, 0.001);
        }

        @Test
        @DisplayName("Should provide defensive copy of position")
        void shouldProvideDefensiveCopyOfPosition() {
            mouseListener.onMouseMove(100, 200);

            Vector2f pos1 = mouseListener.getMousePosition();
            Vector2f pos2 = mouseListener.getMousePosition();

            assertNotSame(pos1, pos2, "Should return different instances");
            assertEquals(pos1, pos2, "But should have same values");

            // Modifying returned vector should not affect internal state
            pos1.x = 999;
            assertEquals(100, mouseListener.getXPos(), 0.001);
        }
    }

    @Nested
    @DisplayName("Mouse Delta Calculation")
    class DeltaCalculationTests {

        @Test
        @DisplayName("Should calculate delta correctly")
        void shouldCalculateDeltaCorrectly() {
            // Start position
            mouseListener.onMouseMove(100, 100);

            // Move mouse
            mouseListener.onMouseMove(150, 120);

            Vector2f delta = mouseListener.getMouseDelta();
            assertEquals(50, delta.x, 0.001, "Delta X should be 50");
            assertEquals(20, delta.y, 0.001, "Delta Y should be 20");
        }

        @Test
        @DisplayName("Should have zero delta initially")
        void shouldHaveZeroDeltaInitially() {
            mouseListener.onMouseMove(100, 100);

            Vector2f delta = mouseListener.getMouseDelta();
            assertEquals(0, delta.x, 0.001);
            assertEquals(0, delta.y, 0.001);
        }

        @Test
        @DisplayName("Should calculate negative delta")
        void shouldCalculateNegativeDelta() {
            mouseListener.onMouseMove(150, 120);
            mouseListener.onMouseMove(100, 100);

            Vector2f delta = mouseListener.getMouseDelta();
            assertEquals(-50, delta.x, 0.001);
            assertEquals(-20, delta.y, 0.001);
        }

        @Test
        @DisplayName("Should reset delta when position doesn't change")
        void shouldResetDeltaWhenPositionDoesntChange() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseMove(150, 120);

            // Delta is 50, 20

            mouseListener.onMouseMove(150, 120); // Same position

            Vector2f delta = mouseListener.getMouseDelta();
            assertEquals(0, delta.x, 0.001);
            assertEquals(0, delta.y, 0.001);
        }

        @Test
        @DisplayName("Should track last position correctly")
        void shouldTrackLastPositionCorrectly() {
            mouseListener.onMouseMove(100, 100);
            assertEquals(0, mouseListener.getLastX(), 0.001);
            assertEquals(0, mouseListener.getLastY(), 0.001);

            mouseListener.onMouseMove(150, 120);
            assertEquals(100, mouseListener.getLastX(), 0.001);
            assertEquals(100, mouseListener.getLastY(), 0.001);
        }

        @Test
        @DisplayName("Should provide defensive copy of delta")
        void shouldProvideDefensiveCopyOfDelta() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseMove(150, 120);

            Vector2f delta1 = mouseListener.getMouseDelta();
            Vector2f delta2 = mouseListener.getMouseDelta();

            assertNotSame(delta1, delta2);
            assertEquals(delta1, delta2);
        }
    }

    @Nested
    @DisplayName("Movement Detection")
    class MovementDetectionTests {

        @Test
        @DisplayName("Should detect when mouse has moved")
        void shouldDetectWhenMouseHasMoved() {
            mouseListener.onMouseMove(100, 100);
            assertFalse(mouseListener.hasMoved());

            mouseListener.onMouseMove(150, 120);
            assertTrue(mouseListener.hasMoved());
        }

        @Test
        @DisplayName("Should detect no movement when position unchanged")
        void shouldDetectNoMovementWhenPositionUnchanged() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseMove(100, 100);

            assertFalse(mouseListener.hasMoved());
        }

        @Test
        @DisplayName("Should calculate movement distance")
        void shouldCalculateMovementDistance() {
            mouseListener.onMouseMove(0, 0);
            mouseListener.onMouseMove(3, 4); // 3-4-5 triangle

            double distance = mouseListener.getMovementDistance();
            assertEquals(5.0, distance, 0.001);
        }

        @Test
        @DisplayName("Should have zero distance when not moved")
        void shouldHaveZeroDistanceWhenNotMoved() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseMove(100, 100);

            assertEquals(0, mouseListener.getMovementDistance(), 0.001);
        }
    }

    @Nested
    @DisplayName("Mouse Scroll")
    class ScrollTests {

        @Test
        @DisplayName("Should track scroll values")
        void shouldTrackScrollValues() {
            mouseListener.onMouseScroll(1.5, 2.5);

            assertEquals(1.5, mouseListener.getScrollX(), 0.001);
            assertEquals(2.5, mouseListener.getScrollY(), 0.001);
        }

        @Test
        @DisplayName("Should clear scroll after endFrame")
        void shouldClearScrollAfterEndFrame() {
            mouseListener.onMouseScroll(1.0, 2.0);
            assertEquals(2.0, mouseListener.getScrollY(), 0.001);

            mouseListener.endFrame();

            assertEquals(0, mouseListener.getScrollX(), 0.001);
            assertEquals(0, mouseListener.getScrollY(), 0.001);
        }

        @Test
        @DisplayName("Should detect scroll direction")
        void shouldDetectScrollDirection() {
            // Scroll up
            mouseListener.onMouseScroll(0, 1.0);
            assertTrue(mouseListener.wasScrolledUp());
            assertFalse(mouseListener.wasScrolledDown());

            mouseListener.endFrame();

            // Scroll down
            mouseListener.onMouseScroll(0, -1.0);
            assertFalse(mouseListener.wasScrolledUp());
            assertTrue(mouseListener.wasScrolledDown());

            mouseListener.endFrame();

            // No scroll
            assertFalse(mouseListener.wasScrolledUp());
            assertFalse(mouseListener.wasScrolledDown());
        }

        @Test
        @DisplayName("Should detect if scrolled")
        void shouldDetectIfScrolled() {
            assertFalse(mouseListener.wasScrolled());

            mouseListener.onMouseScroll(0, 1.0);
            assertTrue(mouseListener.wasScrolled());

            mouseListener.endFrame();
            assertFalse(mouseListener.wasScrolled());
        }

        @Test
        @DisplayName("Should provide scroll delta vector")
        void shouldProvideScrollDeltaVector() {
            mouseListener.onMouseScroll(1.5, 2.5);

            Vector2f scroll = mouseListener.getScrollDelta();
            assertEquals(1.5, scroll.x, 0.001);
            assertEquals(2.5, scroll.y, 0.001);
        }
    }

    @Nested
    @DisplayName("Drag Detection")
    class DragDetectionTests {

        @Test
        @DisplayName("Should detect drag when button held and mouse moved")
        void shouldDetectDragWhenButtonHeldAndMouseMoved() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);

            assertFalse(mouseListener.isDragging(KeyCode.MOUSE_BUTTON_LEFT),
                    "Not dragging yet - no movement");

            mouseListener.onMouseMove(150, 120);
            assertTrue(mouseListener.isDragging(KeyCode.MOUSE_BUTTON_LEFT),
                    "Now dragging - button held and moved");
        }

        @Test
        @DisplayName("Should not detect drag when button not held")
        void shouldNotDetectDragWhenButtonNotHeld() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseMove(150, 120);

            assertFalse(mouseListener.isDragging(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Should not detect drag when button held but not moving")
        void shouldNotDetectDragWhenButtonHeldButNotMoving() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseMove(100, 100); // Same position

            assertFalse(mouseListener.isDragging(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Should stop detecting drag when button released")
        void shouldStopDetectingDragWhenButtonReleased() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseMove(150, 120);
            assertTrue(mouseListener.isDragging(KeyCode.MOUSE_BUTTON_LEFT));

            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);
            assertFalse(mouseListener.isDragging(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Should detect left drag with convenience method")
        void shouldDetectLeftDragWithConvenienceMethod() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseMove(150, 120);

            assertTrue(mouseListener.isDraggingLeft());
            assertFalse(mouseListener.isDraggingRight());
            assertFalse(mouseListener.isDraggingMiddle());
        }

        @Test
        @DisplayName("Should detect different button drags independently")
        void shouldDetectDifferentButtonDragsIndependently() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_RIGHT);
            mouseListener.onMouseMove(150, 120);

            assertTrue(mouseListener.isDraggingLeft());
            assertTrue(mouseListener.isDraggingRight());
        }
    }

    @Nested
    @DisplayName("Position Manipulation")
    class PositionManipulationTests {

        @Test
        @DisplayName("Should allow manual position setting")
        void shouldAllowManualPositionSetting() {
            mouseListener.setPosition(300, 400);

            assertEquals(300, mouseListener.getXPos(), 0.001);
            assertEquals(400, mouseListener.getYPos(), 0.001);
        }

        @Test
        @DisplayName("Should preserve last position when setting")
        void shouldPreserveLastPositionWhenSetting() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.setPosition(200, 200);

            assertEquals(100, mouseListener.getLastX(), 0.001);
            assertEquals(100, mouseListener.getLastY(), 0.001);
        }

        @Test
        @DisplayName("Should reset delta to current position")
        void shouldResetDeltaToCurrentPosition() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseMove(200, 200);

            // Delta is currently 100, 100
            assertTrue(mouseListener.hasMoved());

            mouseListener.resetDelta();

            // Last should now equal current
            assertEquals(mouseListener.getXPos(), mouseListener.getLastX(), 0.001);
            assertEquals(mouseListener.getYPos(), mouseListener.getLastY(), 0.001);

            // Delta should be zero
            Vector2f delta = mouseListener.getMouseDelta();
            assertEquals(0, delta.x, 0.001);
            assertEquals(0, delta.y, 0.001);
        }
    }

    @Nested
    @DisplayName("Clear Functionality")
    class ClearTests {

        @Test
        @DisplayName("Should clear all button states")
        void shouldClearAllButtonStates() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_RIGHT);
            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_MIDDLE);

            mouseListener.clear();

            assertFalse(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT));
            assertFalse(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_RIGHT));
            assertFalse(mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_MIDDLE));
            assertFalse(mouseListener.isAnyButtonHeld());
        }

        @Test
        @DisplayName("Should clear scroll state")
        void shouldClearScrollState() {
            mouseListener.onMouseScroll(5.0, 10.0);
            mouseListener.clear();

            assertEquals(0, mouseListener.getScrollX(), 0.001);
            assertEquals(0, mouseListener.getScrollY(), 0.001);
            assertFalse(mouseListener.wasScrolled());
        }

        @Test
        @DisplayName("Should not affect position on clear")
        void shouldNotAffectPositionOnClear() {
            mouseListener.onMouseMove(100, 200);
            double x = mouseListener.getXPos();
            double y = mouseListener.getYPos();

            mouseListener.clear();

            assertEquals(x, mouseListener.getXPos(), 0.001);
            assertEquals(y, mouseListener.getYPos(), 0.001);
        }
    }

    @Nested
    @DisplayName("Realistic Game Scenarios")
    class GameScenarioTests {

        @Test
        @DisplayName("Click and drag scenario")
        void clickAndDragScenario() {
            // Frame 1 - Mouse at starting position
            mouseListener.onMouseMove(100, 100);
            mouseListener.endFrame();

            // Frame 2 - Click left button
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            assertTrue(mouseListener.wasLeftButtonPressed());
            assertFalse(mouseListener.isDraggingLeft());
            mouseListener.endFrame();

            // Frame 3 - Start dragging
            mouseListener.onMouseMove(120, 110);
            assertTrue(mouseListener.isLeftButtonHeld());
            assertFalse(mouseListener.wasLeftButtonPressed());
            assertTrue(mouseListener.isDraggingLeft());
            assertEquals(20, mouseListener.getMouseDelta().x, 0.001);
            mouseListener.endFrame();

            // Frame 4 - Continue dragging
            mouseListener.onMouseMove(150, 130);
            assertTrue(mouseListener.isDraggingLeft());
            assertEquals(30, mouseListener.getMouseDelta().x, 0.001);
            mouseListener.endFrame();

            // Frame 5 - Release button
            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);
            assertFalse(mouseListener.isLeftButtonHeld());
            assertFalse(mouseListener.isDraggingLeft());
            assertTrue(mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_LEFT));
        }

        @Test
        @DisplayName("Mouse look camera control")
        void mouseLookCameraControl() {
            // Frame 1 - Initial position
            mouseListener.onMouseMove(400, 300);
            mouseListener.endFrame();

            // Frame 2 - Look right
            mouseListener.onMouseMove(450, 300);
            Vector2f delta = mouseListener.getMouseDelta();
            assertEquals(50, delta.x, 0.001);
            assertEquals(0, delta.y, 0.001);
            // Camera rotates right based on delta
            mouseListener.endFrame();

            // Frame 3 - Look up and right
            mouseListener.onMouseMove(480, 280);
            delta = mouseListener.getMouseDelta();
            assertEquals(30, delta.x, 0.001);
            assertEquals(-20, delta.y, 0.001);
            // Camera rotates
            mouseListener.endFrame();

            // Frame 4 - No movement
            mouseListener.onMouseMove(480, 280);
            delta = mouseListener.getMouseDelta();
            assertEquals(0, delta.x, 0.001);
            assertEquals(0, delta.y, 0.001);
        }

        @Test
        @DisplayName("Scroll wheel zoom")
        void scrollWheelZoom() {
            float zoom = 1.0f;

            // Frame 1 - Scroll up (zoom in)
            mouseListener.onMouseScroll(0, 1.0);
            if (mouseListener.wasScrolledUp()) {
                zoom += 0.1f;
            }
            assertEquals(1.1f, zoom, 0.001);
            mouseListener.endFrame();

            // Frame 2 - No scroll
            assertFalse(mouseListener.wasScrolled());
            mouseListener.endFrame();

            // Frame 3 - Scroll down (zoom out)
            mouseListener.onMouseScroll(0, -2.0);
            if (mouseListener.wasScrolledDown()) {
                zoom -= 0.2f;
            }
            assertEquals(0.9f, zoom, 0.001);
        }

        @Test
        @DisplayName("Right-click context menu")
        void rightClickContextMenu() {
            // Frame 1 - Right click
            mouseListener.onMouseMove(200, 150);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_RIGHT);

            Vector2f clickPos = mouseListener.getMousePosition();
            assertTrue(mouseListener.wasRightButtonPressed());
            // Show context menu at clickPos

            mouseListener.endFrame();

            // Frame 2 - Menu is open, mouse moved
            mouseListener.onMouseMove(210, 160);
            assertTrue(mouseListener.isRightButtonHeld());
            mouseListener.endFrame();

            // Frame 3 - Release right button
            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_RIGHT);
            assertTrue(mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_RIGHT));
            // Close menu
        }

        @Test
        @DisplayName("Double click detection pattern")
        void doubleClickDetectionPattern() {
            long lastClickTime = 0;
            int clickCount = 0;
            final long DOUBLE_CLICK_TIME = 300; // ms

            // First click
            long currentTime = 0;
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            if (mouseListener.wasLeftButtonPressed()) {
                if (currentTime - lastClickTime < DOUBLE_CLICK_TIME) {
                    clickCount++;
                } else {
                    clickCount = 1;
                }
                lastClickTime = currentTime;
            }
            assertEquals(1, clickCount);
            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.endFrame();

            // Second click within time window
            currentTime = 200;
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            if (mouseListener.wasLeftButtonPressed()) {
                if (currentTime - lastClickTime < DOUBLE_CLICK_TIME) {
                    clickCount++;
                } else {
                    clickCount = 1;
                }
                lastClickTime = currentTime;
            }
            assertEquals(2, clickCount);
            // Double click detected!
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle all mouse button types")
        void shouldHandleAllMouseButtonTypes() {
            KeyCode[] mouseButtons = {
                    KeyCode.MOUSE_BUTTON_LEFT,
                    KeyCode.MOUSE_BUTTON_RIGHT,
                    KeyCode.MOUSE_BUTTON_MIDDLE,
                    KeyCode.MOUSE_BUTTON_4,
                    KeyCode.MOUSE_BUTTON_5,
                    KeyCode.MOUSE_BUTTON_6,
                    KeyCode.MOUSE_BUTTON_7,
                    KeyCode.MOUSE_BUTTON_8
            };

            for (KeyCode button : mouseButtons) {
                mouseListener.clear();
                mouseListener.onMouseButtonPressed(button);
                assertTrue(mouseListener.isButtonHeld(button),
                        "Should handle " + button);
            }
        }

        @Test
        @DisplayName("Should handle very large position values")
        void shouldHandleVeryLargePositionValues() {
            mouseListener.onMouseMove(1000000, 2000000);

            assertEquals(1000000, mouseListener.getXPos(), 0.001);
            assertEquals(2000000, mouseListener.getYPos(), 0.001);
        }

        @Test
        @DisplayName("Should handle rapid position changes")
        void shouldHandleRapidPositionChanges() {
            for (int i = 0; i < 1000; i++) {
                mouseListener.onMouseMove(i, i);
            }

            assertEquals(999, mouseListener.getXPos(), 0.001);
            assertEquals(999, mouseListener.getYPos(), 0.001);
        }

        @Test
        @DisplayName("Should handle multiple endFrame calls")
        void shouldHandleMultipleEndFrameCalls() {
            mouseListener.onMouseScroll(5, 10);
            mouseListener.endFrame();
            mouseListener.endFrame();
            mouseListener.endFrame();

            assertEquals(0, mouseListener.getScrollY(), 0.001);
        }

        @Test
        @DisplayName("Should handle endFrame before any input")
        void shouldHandleEndFrameBeforeInput() {
            assertDoesNotThrow(() -> mouseListener.endFrame());
        }
    }

    @Nested
    @DisplayName("State Consistency")
    class StateConsistencyTests {

        @Test
        @DisplayName("Dragging implies button held and movement")
        void draggingImpliesButtonHeldAndMovement() {
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseMove(150, 120);

            if (mouseListener.isDraggingLeft()) {
                assertTrue(mouseListener.isLeftButtonHeld(),
                        "If dragging, button must be held");
                assertTrue(mouseListener.hasMoved(),
                        "If dragging, mouse must have moved");
            }
        }

        @Test
        @DisplayName("Released implies not held")
        void releasedImpliesNotHeld() {
            mouseListener.onMouseButtonPressed(KeyCode.MOUSE_BUTTON_LEFT);
            mouseListener.onMouseButtonReleased(KeyCode.MOUSE_BUTTON_LEFT);

            if (mouseListener.wasButtonReleased(KeyCode.MOUSE_BUTTON_LEFT)) {
                assertFalse(mouseListener.isButtonHeld(KeyCode.MOUSE_BUTTON_LEFT),
                        "Released button should not be held");
            }
        }

        @Test
        @DisplayName("Delta consistency across frames")
        void deltaConsistencyAcrossFrames() {
            // Move from 100 to 200
            mouseListener.onMouseMove(100, 100);
            mouseListener.onMouseMove(200, 200);

            Vector2f delta1 = mouseListener.getMouseDelta();
            assertEquals(100, delta1.x, 0.001);

            // Move from 200 to 300
            mouseListener.onMouseMove(300, 300);
            Vector2f delta2 = mouseListener.getMouseDelta();
            assertEquals(100, delta2.x, 0.001);

            // Deltas should be same magnitude
            assertEquals(delta1.length(), delta2.length(), 0.001);
        }
    }
}