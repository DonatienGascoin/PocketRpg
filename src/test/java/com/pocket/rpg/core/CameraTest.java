package com.pocket.rpg.core;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.rendering.CameraSystem;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Camera class.
 * Tests Unity-style coordinate conversion API and camera transforms.
 */
@DisplayName("Camera Tests")
class CameraTest {

    private Camera camera;
    private CameraSystem cameraSystem;
    private static final int GAME_WIDTH = 640;
    private static final int GAME_HEIGHT = 480;

    @BeforeEach
    void setUp() {
        camera = new Camera();
        cameraSystem = new CameraSystem(GameConfig.builder()
                .gameHeight(GAME_HEIGHT)
                .gameWidth(GAME_WIDTH)
                .windowHeight(GAME_HEIGHT)
                .windowWidth(GAME_WIDTH)
                .build());
        camera.setCameraSystem(cameraSystem);

        // Clear main camera before each test
        Camera.setMainCamera(null);
    }

    @AfterEach
    void tearDown() {
        // Clean up main camera after each test
        Camera.setMainCamera(null);
    }

    // ======================================================================
    // CONSTRUCTION TESTS
    // ======================================================================

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create camera with default values")
        void shouldCreateWithDefaultValues() {
            Camera cam = new Camera();

            assertEquals(0, cam.getPosition().x);
            assertEquals(0, cam.getPosition().y);
            assertEquals(0, cam.getRotation());
            assertEquals(1.0f, cam.getZoom());
        }
    }

    // ======================================================================
    // MAIN CAMERA TESTS (Unity-style static accessor)
    // ======================================================================

    @Nested
    @DisplayName("Main Camera Tests")
    class MainCameraTests {

        @Test
        @DisplayName("Should set and get main camera")
        void shouldSetAndGetMainCamera() {
            assertNull(Camera.getMainCamera());

            Camera.setMainCamera(camera);

            assertSame(camera, Camera.getMainCamera());
        }

        @Test
        @DisplayName("Should allow changing main camera")
        void shouldAllowChangingMainCamera() {
            Camera camera1 = new Camera();
            Camera camera2 = new Camera();

            Camera.setMainCamera(camera1);
            assertSame(camera1, Camera.getMainCamera());

            Camera.setMainCamera(camera2);
            assertSame(camera2, Camera.getMainCamera());
        }

        @Test
        @DisplayName("Should allow setting main camera to null")
        void shouldAllowSettingMainCameraToNull() {
            Camera.setMainCamera(camera);
            assertNotNull(Camera.getMainCamera());

            Camera.setMainCamera(null);
            assertNull(Camera.getMainCamera());
        }
    }

    // ======================================================================
    // CAMERA SYSTEM SETUP TESTS
    // ======================================================================

    @Nested
    @DisplayName("CameraSystem Setup Tests")
    class CameraSystemSetupTests {

        @Test
        @DisplayName("Should set camera system")
        void shouldSetCameraSystem() {
            Camera cam = new Camera();
            assertNull(cam.getCameraSystem());

            cam.setCameraSystem(cameraSystem);

            assertSame(cameraSystem, cam.getCameraSystem());
        }

        @Test
        @DisplayName("Should handle null camera system")
        void shouldHandleNullCameraSystem() {
            Camera cam = new Camera();
            cam.setCameraSystem(null);

            assertNull(cam.getCameraSystem());
        }
    }

    // ======================================================================
    // SCREEN TO WORLD POINT TESTS (Unity-style API)
    // ======================================================================

    @Nested
    @DisplayName("screenToWorldPoint() Tests")
    class ScreenToWorldPointTests {

        @Test
        @DisplayName("Should convert screen to world at origin")
        void shouldConvertScreenToWorldAtOrigin() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f result = camera.screenToWorldPoint(0, 0);

            assertEquals(0, result.x, 0.1f);
            assertEquals(0, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should convert screen to world with depth parameter")
        void shouldConvertScreenToWorldWithDepth() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f result = camera.screenToWorldPoint(100, 200, 5.0f);

            // Z should be preserved
            assertEquals(5.0f, result.z, 0.001f);
        }

        @Test
        @DisplayName("Should convert screen to world with Vector2f")
        void shouldConvertScreenToWorldWithVector2f() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector2f screenPos = new Vector2f(100, 200);
            Vector3f result = camera.screenToWorldPoint(screenPos);

            assertNotNull(result);
            // Z should default to 0
            assertEquals(0, result.z, 0.001f);
        }

        @Test
        @DisplayName("Should convert screen center to world center")
        void shouldConvertScreenCenterToWorldCenter() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);
            cameraSystem.setViewportSize(GAME_WIDTH, GAME_HEIGHT);

            Vector3f result = camera.screenToWorldPoint(GAME_WIDTH / 2, GAME_HEIGHT / 2);

            assertEquals(GAME_WIDTH / 2, result.x, 1.0f);
            assertEquals(GAME_HEIGHT / 2, result.y, 1.0f);
        }

        @Test
        @DisplayName("Should handle camera offset in screenToWorldPoint")
        void shouldHandleCameraOffsetInScreenToWorldPoint() {
            camera.setPosition(100, 200);
            camera.setZoom(1.0f);

            Vector3f result = camera.screenToWorldPoint(0, 0);

            // Top-left of screen should be at camera position
            assertEquals(100, result.x, 0.1f);
            assertEquals(200, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should handle camera zoom in screenToWorldPoint")
        void shouldHandleCameraZoomInScreenToWorldPoint() {
            camera.setPosition(0, 0);
            camera.setZoom(2.0f);

            // With 2x zoom, we see half the world
            Vector3f result = camera.screenToWorldPoint(GAME_WIDTH / 2, GAME_HEIGHT / 2);

            assertEquals(GAME_WIDTH / 4, result.x, 1.0f);
            assertEquals(GAME_HEIGHT / 4, result.y, 1.0f);
        }

        @Test
        @DisplayName("Should handle null camera system gracefully")
        void shouldHandleNullCameraSystemGracefully() {
            Camera cam = new Camera();
            // Don't set camera system

            Vector3f result = cam.screenToWorldPoint(100, 200);

            // Should return input coordinates as fallback
            assertEquals(100, result.x);
            assertEquals(200, result.y);
        }
    }

    // ======================================================================
    // WORLD TO SCREEN POINT TESTS (Unity-style API)
    // ======================================================================

    @Nested
    @DisplayName("worldToScreenPoint() Tests")
    class WorldToScreenPointTests {

        @Test
        @DisplayName("Should convert world to screen at origin")
        void shouldConvertWorldToScreenAtOrigin() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f worldPos = new Vector3f(100, 200, 0);
            Vector2f result = camera.worldToScreenPoint(worldPos);

            assertEquals(100, result.x, 0.1f);
            assertEquals(200, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should convert world to screen with separate coordinates")
        void shouldConvertWorldToScreenWithSeparateCoordinates() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector2f result = camera.worldToScreenPoint(150, 250);

            assertEquals(150, result.x, 0.1f);
            assertEquals(250, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should handle camera offset in worldToScreenPoint")
        void shouldHandleCameraOffsetInWorldToScreenPoint() {
            camera.setPosition(100, 200);
            camera.setZoom(1.0f);

            Vector3f worldPos = new Vector3f(100, 200, 0);
            Vector2f result = camera.worldToScreenPoint(worldPos);

            // Camera position should map to screen origin
            assertEquals(0, result.x, 0.1f);
            assertEquals(0, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should handle camera zoom in worldToScreenPoint")
        void shouldHandleCameraZoomInWorldToScreenPoint() {
            camera.setPosition(0, 0);
            camera.setZoom(2.0f);

            Vector3f worldPos = new Vector3f((float) GAME_WIDTH / 4, (float) GAME_HEIGHT / 4, 0);
            Vector2f result = camera.worldToScreenPoint(worldPos);

            // With 2x zoom, quarter world = half screen
            assertEquals((float) GAME_WIDTH / 2, result.x, 1.0f);
            assertEquals((float) GAME_HEIGHT / 2, result.y, 1.0f);
        }

        @Test
        @DisplayName("Should handle null camera system gracefully")
        void shouldHandleNullCameraSystemGracefully() {
            Camera cam = new Camera();
            // Don't set camera system

            Vector3f worldPos = new Vector3f(100, 200, 0);
            Vector2f result = cam.worldToScreenPoint(worldPos);

            // Should return XY coordinates as fallback
            assertEquals(100, result.x);
            assertEquals(200, result.y);
        }
    }

    // ======================================================================
    // COORDINATE CONVERSION ROUNDTRIP TESTS
    // ======================================================================

    @Nested
    @DisplayName("Coordinate Conversion Roundtrip Tests")
    class CoordinateConversionRoundtripTests {

        @Test
        @DisplayName("Should be reversible: screen -> world -> screen")
        void shouldBeReversibleScreenWorldScreen() {
            camera.setPosition(50, 100);
            camera.setZoom(1.5f);
            cameraSystem.setViewportSize(1280, 960);

            Vector2f original = new Vector2f(640, 480);
            Vector3f world = camera.screenToWorldPoint(original);
            Vector2f back = camera.worldToScreenPoint(world);

            assertEquals(original.x, back.x, 1.0f);
            assertEquals(original.y, back.y, 1.0f);
        }

        @Test
        @DisplayName("Should be reversible: world -> screen -> world")
        void shouldBeReversibleWorldScreenWorld() {
            camera.setPosition(50, 100);
            camera.setZoom(1.5f);

            Vector3f original = new Vector3f(200, 300, 0);
            Vector2f screen = camera.worldToScreenPoint(original);
            Vector3f back = camera.screenToWorldPoint(screen);

            assertEquals(original.x, back.x, 1.0f);
            assertEquals(original.y, back.y, 1.0f);
        }
    }

    // ======================================================================
    // WORLD BOUNDS TESTS
    // ======================================================================

    @Nested
    @DisplayName("World Bounds Tests")
    class WorldBoundsTests {

        @Test
        @DisplayName("Should get world bounds")
        void shouldGetWorldBounds() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f[] bounds = camera.getWorldBounds();

            assertNotNull(bounds);
            assertEquals(4, bounds.length);
        }

        @Test
        @DisplayName("Should get world center")
        void shouldGetWorldCenter() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f center = camera.getWorldCenter();

            assertNotNull(center);
            assertEquals(GAME_WIDTH / 2.0f, center.x, 0.1f);
            assertEquals(GAME_HEIGHT / 2.0f, center.y, 0.1f);
        }

        @Test
        @DisplayName("Should handle null camera system in getWorldBounds")
        void shouldHandleNullCameraSystemInGetWorldBounds() {
            Camera cam = new Camera();
            // Don't set camera system

            Vector3f[] bounds = cam.getWorldBounds();

            assertNotNull(bounds);
            assertEquals(0, bounds.length);
        }

        @Test
        @DisplayName("Should handle null camera system in getWorldCenter")
        void shouldHandleNullCameraSystemInGetWorldCenter() {
            Camera cam = new Camera();
            // Don't set camera system

            Vector3f center = cam.getWorldCenter();

            assertNotNull(center);
            // Should return camera position as fallback
            assertEquals(0, center.x, 0.001f);
            assertEquals(0, center.y, 0.001f);
        }
    }

    // ======================================================================
    // CAMERA TRANSFORM TESTS
    // ======================================================================

    @Nested
    @DisplayName("Camera Transform Tests")
    class CameraTransformTests {

        @Test
        @DisplayName("Should set and get position")
        void shouldSetAndGetPosition() {
            camera.setPosition(100, 200);

            Vector3f pos = camera.getPosition();
            assertEquals(100, pos.x);
            assertEquals(200, pos.y);
            assertEquals(0, pos.z);
        }

        @Test
        @DisplayName("Should set position with Vector3f")
        void shouldSetPositionWithVector3f() {
            Vector3f newPos = new Vector3f(50, 75, 10);
            camera.setPosition(newPos);

            Vector3f pos = camera.getPosition();
            assertEquals(50, pos.x);
            assertEquals(75, pos.y);
            assertEquals(10, pos.z);
        }

        @Test
        @DisplayName("Should translate camera")
        void shouldTranslateCamera() {
            camera.setPosition(100, 100);
            camera.translate(50, 25);

            Vector3f pos = camera.getPosition();
            assertEquals(150, pos.x);
            assertEquals(125, pos.y);
        }

        @Test
        @DisplayName("Should set and get rotation")
        void shouldSetAndGetRotation() {
            camera.setRotation(45);
            assertEquals(45, camera.getRotation());
        }

        @Test
        @DisplayName("Should rotate camera")
        void shouldRotateCamera() {
            camera.setRotation(30);
            camera.rotate(15);
            assertEquals(45, camera.getRotation());
        }

        @Test
        @DisplayName("Should set and get zoom")
        void shouldSetAndGetZoom() {
            camera.setZoom(2.0f);
            assertEquals(2.0f, camera.getZoom());
        }

        @Test
        @DisplayName("Should zoom by factor")
        void shouldZoomByFactor() {
            camera.setZoom(1.0f);
            camera.zoomBy(2.0f);
            assertEquals(2.0f, camera.getZoom());
        }

        @Test
        @DisplayName("Should reject zero zoom")
        void shouldRejectZeroZoom() {
            camera.setZoom(1.5f);
            camera.setZoom(0);

            // Zoom should remain unchanged
            assertEquals(1.5f, camera.getZoom());
        }

        @Test
        @DisplayName("Should reject negative zoom")
        void shouldRejectNegativeZoom() {
            camera.setZoom(1.5f);
            camera.setZoom(-2.0f);

            // Zoom should remain unchanged
            assertEquals(1.5f, camera.getZoom());
        }

        @Test
        @DisplayName("Should reject zero zoom factor")
        void shouldRejectZeroZoomFactor() {
            camera.setZoom(1.5f);
            camera.zoomBy(0);

            // Zoom should remain unchanged
            assertEquals(1.5f, camera.getZoom());
        }
    }

    // ======================================================================
    // VIEW MATRIX TESTS
    // ======================================================================

    @Nested
    @DisplayName("View Matrix Tests")
    class ViewMatrixTests {

        @Test
        @DisplayName("Should return valid view matrix")
        void shouldReturnValidViewMatrix() {
            Matrix4f viewMatrix = camera.getViewMatrix();

            assertNotNull(viewMatrix);
        }

        @Test
        @DisplayName("Should return copy of view matrix")
        void shouldReturnCopyOfViewMatrix() {
            Matrix4f matrix1 = camera.getViewMatrix();
            Matrix4f matrix2 = camera.getViewMatrix();

            assertEquals(matrix1, matrix2);
            assertNotSame(matrix1, matrix2);
        }

        @Test
        @DisplayName("Should update view matrix when position changes")
        void shouldUpdateViewMatrixWhenPositionChanges() {
            Matrix4f before = camera.getViewMatrix();

            camera.setPosition(100, 200);

            Matrix4f after = camera.getViewMatrix();

            assertNotEquals(before, after);
        }

        @Test
        @DisplayName("Should update view matrix when rotation changes")
        void shouldUpdateViewMatrixWhenRotationChanges() {
            Matrix4f before = camera.getViewMatrix();

            camera.setRotation(45);

            Matrix4f after = camera.getViewMatrix();

            assertNotEquals(before, after);
        }

        @Test
        @DisplayName("Should update view matrix when zoom changes")
        void shouldUpdateViewMatrixWhenZoomChanges() {
            Matrix4f before = camera.getViewMatrix();

            camera.setZoom(2.0f);

            Matrix4f after = camera.getViewMatrix();

            assertNotEquals(before, after);
        }

        @Test
        @DisplayName("Should mark view dirty")
        void shouldMarkViewDirty() {
            camera.getViewMatrix(); // Force update
            assertFalse(camera.hasTransformChanged());

            camera.markViewDirty();

            assertTrue(camera.hasTransformChanged());
        }
    }

    // ======================================================================
    // UPDATE TESTS
    // ======================================================================

    @Nested
    @DisplayName("Update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should detect transform changes on update")
        void shouldDetectTransformChangesOnUpdate() {
            camera.update(0.016f);
            assertFalse(camera.hasTransformChanged());

            camera.setPosition(100, 200);
            assertTrue(camera.hasTransformChanged());

            camera.update(0.016f);
            assertFalse(camera.hasTransformChanged());
        }

        @Test
        @DisplayName("Should detect rotation changes on update")
        void shouldDetectRotationChangesOnUpdate() {
            camera.update(0.016f);

            camera.setRotation(45);
            assertTrue(camera.hasTransformChanged());

            camera.update(0.016f);
            assertFalse(camera.hasTransformChanged());
        }

        @Test
        @DisplayName("Should detect zoom changes on update")
        void shouldDetectZoomChangesOnUpdate() {
            camera.update(0.016f);

            camera.setZoom(2.0f);
            assertTrue(camera.hasTransformChanged());

            camera.update(0.016f);
            assertFalse(camera.hasTransformChanged());
        }
    }

    // ======================================================================
    // TO STRING TESTS
    // ======================================================================

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            camera.setPosition(100, 200);
            camera.setRotation(45);
            camera.setZoom(1.5f);

            String result = camera.toString();

            assertNotNull(result);
            assertTrue(result.contains("Camera"));
            assertTrue(result.contains("100"));
            assertTrue(result.contains("200"));
        }
    }
}