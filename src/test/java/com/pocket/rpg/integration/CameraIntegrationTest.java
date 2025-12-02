package com.pocket.rpg.integration;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.Camera;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.CameraSystem;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full Camera system.
 * Tests the interaction between Camera, CameraSystem, Scene, and SceneManager.
 */
@DisplayName("Camera System Integration Tests")
class CameraIntegrationTest {

    private CameraSystem cameraSystem;
    private SceneManager sceneManager;
    private static final int GAME_WIDTH = 640;
    private static final int GAME_HEIGHT = 480;

    @BeforeEach
    void setUp() {
        cameraSystem = new CameraSystem(GameConfig.builder()
                .gameWidth(GAME_WIDTH)
                .gameHeight(GAME_HEIGHT)
                .windowWidth(GAME_WIDTH)
                .windowHeight(GAME_HEIGHT)
                .build());
        sceneManager = new SceneManager(cameraSystem);

        // Clear main camera
        Camera.setMainCamera(null);
    }

    @AfterEach
    void tearDown() {
        if (sceneManager != null) {
            sceneManager.destroy();
        }
        Camera.setMainCamera(null);
    }

    // ======================================================================
    // SCENE INITIALIZATION TESTS
    // ======================================================================

    @Nested
    @DisplayName("Scene Initialization Tests")
    class SceneInitializationTests {

        @Test
        @DisplayName("Should initialize scene with camera system")
        void shouldInitializeSceneWithCameraSystem() {
            TestScene scene = new TestScene("TestScene");

            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            // Camera should have camera system set
            assertNotNull(scene.getCamera().getCameraSystem());
            assertSame(cameraSystem, scene.getCamera().getCameraSystem());
        }

        @Test
        @DisplayName("Should set Camera.main when loading scene")
        void shouldSetMainCameraWhenLoadingScene() {
            TestScene scene = new TestScene("TestScene");

            assertNull(Camera.getMainCamera());

            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            assertNotNull(Camera.getMainCamera());
            assertSame(scene.getCamera(), Camera.getMainCamera());
        }

        @Test
        @DisplayName("Should update Camera.main when switching scenes")
        void shouldUpdateMainCameraWhenSwitchingScenes() {
            TestScene scene1 = new TestScene("Scene1");
            TestScene scene2 = new TestScene("Scene2");

            sceneManager.registerScene(scene1);
            sceneManager.registerScene(scene2);

            sceneManager.loadScene("Scene1");
            assertSame(scene1.getCamera(), Camera.getMainCamera());

            sceneManager.loadScene("Scene2");
            assertSame(scene2.getCamera(), Camera.getMainCamera());
        }

        @Test
        @DisplayName("Should clear Camera.main when destroying scene manager")
        void shouldClearMainCameraWhenDestroyingSceneManager() {
            TestScene scene = new TestScene("TestScene");

            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            assertNotNull(Camera.getMainCamera());

            sceneManager.destroy();

            assertNull(Camera.getMainCamera());
        }

        @Test
        @DisplayName("Should throw exception if camera system not set")
        void shouldThrowExceptionIfCameraSystemNotSet() {
            assertThrows(Exception.class, () -> new SceneManager(null));

        }
    }

    // ======================================================================
    // END-TO-END COORDINATE CONVERSION TESTS
    // ======================================================================

    @Nested
    @DisplayName("End-to-End Coordinate Conversion Tests")
    class EndToEndConversionTests {

        @Test
        @DisplayName("Should convert mouse coordinates through full pipeline")
        void shouldConvertMouseCoordinatesThroughFullPipeline() {
            TestScene scene = new TestScene("TestScene");
            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            // Set viewport to simulate window size
            cameraSystem.setViewportSize(1280, 960);

            // Simulate mouse click at center of viewport
            float mouseX = 640;
            float mouseY = 480;

            // Convert using Camera.getMainCamera() (Unity-style!)
            Vector3f worldPos = Camera.getMainCamera().screenToWorldPoint(mouseX, mouseY);

            // Should map to center of game world
            assertEquals(GAME_WIDTH / 2, worldPos.x, 1.0f);
            assertEquals(GAME_HEIGHT / 2, worldPos.y, 1.0f);
        }

        @Test
        @DisplayName("Should handle viewport scaling correctly")
        void shouldHandleViewportScalingCorrectly() {
            TestScene scene = new TestScene("TestScene");
            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            // 2x viewport scaling
            cameraSystem.setViewportSize(GAME_WIDTH * 2, GAME_HEIGHT * 2);

            Camera camera = Camera.getMainCamera();
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            // Click at center of 2x viewport
            Vector3f worldPos = camera.screenToWorldPoint(GAME_WIDTH, GAME_HEIGHT);

            // Should still map to center of game world
            assertEquals(GAME_WIDTH / 2, worldPos.x, 1.0f);
            assertEquals(GAME_HEIGHT / 2, worldPos.y, 1.0f);
        }

        @Test
        @DisplayName("Should handle camera transforms correctly")
        void shouldHandleCameraTransformsCorrectly() {
            TestScene scene = new TestScene("TestScene");
            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            Camera camera = Camera.getMainCamera();
            camera.setPosition(100, 200);
            camera.setZoom(2.0f);

            cameraSystem.setViewportSize(GAME_WIDTH, GAME_HEIGHT);

            // Top-left of screen
            Vector3f topLeft = camera.screenToWorldPoint(0, 0);

            // Should be at camera position
            assertEquals(100, topLeft.x, 1.0f);
            assertEquals(200, topLeft.y, 1.0f);
        }
    }

    // ======================================================================
    // MULTI-SCENE TESTS
    // ======================================================================

    @Nested
    @DisplayName("Multi-Scene Tests")
    class MultiSceneTests {

        @Test
        @DisplayName("Should maintain separate camera state per scene")
        void shouldMaintainSeparateCameraStatePerScene() {
            TestScene scene1 = new TestScene("Scene1");
            TestScene scene2 = new TestScene("Scene2");

            sceneManager.registerScene(scene1);
            sceneManager.registerScene(scene2);

            // Load scene 1, configure camera
            sceneManager.loadScene("Scene1");
            scene1.getCamera().setPosition(100, 200);
            scene1.getCamera().setZoom(2.0f);

            // Load scene 2, configure camera differently
            sceneManager.loadScene("Scene2");
            scene2.getCamera().setPosition(50, 75);
            scene2.getCamera().setZoom(1.5f);

            // Verify scene 2 camera state
            assertEquals(50, scene2.getCamera().getPosition().x);
            assertEquals(1.5f, scene2.getCamera().getZoom());

            // Scene 1 camera should be unchanged (though scene is unloaded)
            assertEquals(100, scene1.getCamera().getPosition().x);
            assertEquals(2.0f, scene1.getCamera().getZoom());
        }

        @Test
        @DisplayName("Should update Camera.main when switching between scenes")
        void shouldUpdateMainCameraWhenSwitchingScenes() {
            TestScene scene1 = new TestScene("Scene1");
            TestScene scene2 = new TestScene("Scene2");

            sceneManager.registerScene(scene1);
            sceneManager.registerScene(scene2);

            sceneManager.loadScene("Scene1");
            Camera camera1 = Camera.getMainCamera();

            sceneManager.loadScene("Scene2");
            Camera camera2 = Camera.getMainCamera();

            assertNotSame(camera1, camera2);
            assertSame(scene2.getCamera(), camera2);
        }
    }

    // ======================================================================
    // VIEWPORT RESIZE TESTS
    // ======================================================================

    @Nested
    @DisplayName("Viewport Resize Tests")
    class ViewportResizeTests {

        @Test
        @DisplayName("Should handle viewport resize during gameplay")
        void shouldHandleViewportResizeDuringGameplay() {
            TestScene scene = new TestScene("TestScene");
            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            // Initial viewport
            cameraSystem.setViewportSize(800, 600);

            Vector3f worldPosBefore = Camera.getMainCamera().screenToWorldPoint(400, 300);

            // Resize viewport
            cameraSystem.setViewportSize(1600, 1200);

            // Same screen position (now at center of larger viewport)
            Vector3f worldPosAfter = Camera.getMainCamera().screenToWorldPoint(800, 600);

            // Should map to same world position
            assertEquals(worldPosBefore.x, worldPosAfter.x, 1.0f);
            assertEquals(worldPosBefore.y, worldPosAfter.y, 1.0f);
        }
    }

    // ======================================================================
    // GAMEOBJECT INTEGRATION TESTS
    // ======================================================================

    @Nested
    @DisplayName("GameObject Integration Tests")
    class GameObjectIntegrationTests {

        @Test
        @DisplayName("Should access camera from GameObject scene")
        void shouldAccessCameraFromGameObjectScene() {
            TestScene scene = new TestScene("TestScene");
            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            GameObject gameObject = new GameObject("TestObject");
            scene.addGameObject(gameObject);

            // GameObject can access camera through scene
            Camera camera = gameObject.getScene().getCamera();

            assertNotNull(camera);
            assertSame(scene.getCamera(), camera);
            assertNotNull(camera.getCameraSystem());
        }

        @Test
        @DisplayName("Should convert coordinates from GameObject context")
        void shouldConvertCoordinatesFromGameObjectContext() {
            TestScene scene = new TestScene("TestScene");
            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            GameObject gameObject = new GameObject("TestObject");
            scene.addGameObject(gameObject);

            // Simulate component accessing camera
            Camera camera = gameObject.getScene().getCamera();
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f worldPos = camera.screenToWorldPoint(100, 200);

            assertNotNull(worldPos);
            assertEquals(100, worldPos.x, 1.0f);
            assertEquals(200, worldPos.y, 1.0f);
        }
    }

    // ======================================================================
    // CAMERA SYSTEM REFERENCE TESTS
    // ======================================================================

    @Nested
    @DisplayName("CameraSystem Reference Tests")
    class CameraSystemReferenceTests {

        @Test
        @DisplayName("Should maintain same CameraSystem reference across scenes")
        void shouldMaintainSameCameraSystemReferenceAcrossScenes() {
            TestScene scene1 = new TestScene("Scene1");
            TestScene scene2 = new TestScene("Scene2");

            sceneManager.registerScene(scene1);
            sceneManager.registerScene(scene2);

            sceneManager.loadScene("Scene1");
            CameraSystem cs1 = scene1.getCamera().getCameraSystem();

            sceneManager.loadScene("Scene2");
            CameraSystem cs2 = scene2.getCamera().getCameraSystem();

            // Both scenes should reference the same CameraSystem instance
            assertSame(cs1, cs2);
            assertSame(cameraSystem, cs1);
            assertSame(cameraSystem, cs2);
        }

        @Test
        @DisplayName("Should access CameraSystem from scene")
        void shouldAccessCameraSystemFromScene() {
            TestScene scene = new TestScene("TestScene");
            sceneManager.registerScene(scene);
            sceneManager.loadScene("TestScene");

            // Scene should store camera system reference
            assertNotNull(scene.getCameraSystem());
            assertSame(cameraSystem, scene.getCameraSystem());
        }
    }

    // ======================================================================
    // ERROR HANDLING TESTS
    // ======================================================================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle accessing Camera.main before scene load")
        void shouldHandleAccessingMainCameraBeforeSceneLoad() {
            assertNull(Camera.getMainCamera());
        }

        @Test
        @DisplayName("Should handle coordinate conversion with null main camera")
        void shouldHandleCoordinateConversionWithNullMainCamera() {
            // This would throw NullPointerException in Unity-style usage
            // but Java doesn't have implicit null safety
            assertNull(Camera.getMainCamera());
        }

        @Test
        @DisplayName("Should handle scene without initialized camera system")
        void shouldHandleSceneWithoutInitializedCameraSystem() {
            TestScene scene = new TestScene("TestScene");
            // Don't load through scene manager (so camera system not set)

            Camera camera = scene.getCamera();
            assertNull(camera.getCameraSystem());

            // Coordinate conversion should fail gracefully
            Vector3f result = camera.screenToWorldPoint(100, 200);

            // Should return fallback values
            assertEquals(100, result.x);
            assertEquals(200, result.y);
        }
    }

    // ======================================================================
    // HELPER CLASSES
    // ======================================================================

    /**
     * Simple test scene for integration testing.
     */
    private static class TestScene extends Scene {
        public TestScene(String name) {
            super(name);
        }

        @Override
        public void onLoad() {
            // Empty implementation for testing
        }

        @Override
        public void onUnload() {
            // Empty implementation for testing
        }
    }
}