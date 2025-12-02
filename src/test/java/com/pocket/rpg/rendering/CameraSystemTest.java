package com.pocket.rpg.rendering;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.Camera;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CameraSystem.
 * Tests coordinate conversions, projection matrices, and viewport management.
 */
@DisplayName("CameraSystem Tests")
class CameraSystemTest {

    private CameraSystem cameraSystem;
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
    }

    // ======================================================================
    // CONSTRUCTION TESTS
    // ======================================================================

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create camera system with valid dimensions")
        void shouldCreateWithValidDimensions() {
            CameraSystem system = new CameraSystem(GameConfig.builder()
                    .gameWidth(800)
                    .gameHeight(600)
                    .windowWidth(800)
                    .windowHeight(600)
                    .build());

            assertEquals(800, system.getGameWidth());
            assertEquals(600, system.getGameHeight());
            assertEquals(800, system.getViewportWidth());
            assertEquals(600, system.getViewportHeight());
        }

        @Test
        @DisplayName("Should throw exception for invalid width")
        void shouldThrowExceptionForInvalidWidth() {
            assertThrows(IllegalArgumentException.class, () -> {
                new CameraSystem(GameConfig.builder()
                        .gameWidth(0)
                        .gameHeight(600)
                        .windowWidth(0)
                        .windowHeight(600)
                        .build());
            });

            assertThrows(IllegalArgumentException.class, () -> {
                new CameraSystem(GameConfig.builder()
                        .gameWidth(-100)
                        .gameHeight(GAME_HEIGHT)
                        .windowWidth(-100)
                        .windowHeight(GAME_HEIGHT)
                        .build());
            });
        }

        @Test
        @DisplayName("Should throw exception for invalid height")
        void shouldThrowExceptionForInvalidHeight() {
            assertThrows(IllegalArgumentException.class, () -> {
                new CameraSystem(GameConfig.builder()
                        .gameWidth(GAME_WIDTH)
                        .gameHeight(0)
                        .windowWidth(GAME_WIDTH)
                        .windowHeight(0)
                        .build());
            });

            assertThrows(IllegalArgumentException.class, () -> {
                new CameraSystem(GameConfig.builder()
                        .gameWidth(GAME_WIDTH)
                        .gameHeight(-100)
                        .windowWidth(GAME_WIDTH)
                        .windowHeight(-100)
                        .build());
            });
        }

        @Test
        @DisplayName("Should initialize viewport to game resolution")
        void shouldInitializeViewportToGameResolution() {
            assertEquals(GAME_WIDTH, cameraSystem.getViewportWidth());
            assertEquals(GAME_HEIGHT, cameraSystem.getViewportHeight());
        }
    }

    // ======================================================================
    // PROJECTION MATRIX TESTS
    // ======================================================================

    @Nested
    @DisplayName("Projection Matrix Tests")
    class ProjectionMatrixTests {

        @Test
        @DisplayName("Should return valid projection matrix")
        void shouldReturnValidProjectionMatrix() {
            Matrix4f projection = cameraSystem.getProjectionMatrix();

            assertNotNull(projection);
            assertFalse(projection.isFinite() == false);
        }

        @Test
        @DisplayName("Should return copy of projection matrix")
        void shouldReturnCopyOfProjectionMatrix() {
            Matrix4f projection1 = cameraSystem.getProjectionMatrix();
            Matrix4f projection2 = cameraSystem.getProjectionMatrix();

            // Should be equal but not same instance
            assertEquals(projection1, projection2);
            assertNotSame(projection1, projection2);
        }

        @Test
        @DisplayName("Modifying returned matrix should not affect internal state")
        void shouldNotAffectInternalStateWhenModifyingReturnedMatrix() {
            Matrix4f projection1 = cameraSystem.getProjectionMatrix();
            projection1.zero(); // Modify the returned matrix

            Matrix4f projection2 = cameraSystem.getProjectionMatrix();

            // Internal matrix should be unchanged
            assertNotEquals(projection1, projection2);
        }

        @Test
        @DisplayName("Should update projection when near/far planes change")
        void shouldUpdateProjectionWhenPlanesChange() {
            Matrix4f before = cameraSystem.getProjectionMatrix();

            cameraSystem.setOrthographicPlanes(-10f, 10f);

            Matrix4f after = cameraSystem.getProjectionMatrix();

            // Matrices should be different after changing planes
            assertNotEquals(before, after);
        }
    }

    // ======================================================================
    // VIEWPORT MANAGEMENT TESTS
    // ======================================================================

    @Nested
    @DisplayName("Viewport Management Tests")
    class ViewportManagementTests {

        @Test
        @DisplayName("Should update viewport size")
        void shouldUpdateViewportSize() {
            cameraSystem.setViewportSize(1920, 1080);

            assertEquals(1920, cameraSystem.getViewportWidth());
            assertEquals(1080, cameraSystem.getViewportHeight());
        }

        @Test
        @DisplayName("Should reject invalid viewport dimensions")
        void shouldRejectInvalidViewportDimensions() {
            int originalWidth = cameraSystem.getViewportWidth();
            int originalHeight = cameraSystem.getViewportHeight();

            // Try to set invalid dimensions
            cameraSystem.setViewportSize(0, 100);
            cameraSystem.setViewportSize(100, 0);
            cameraSystem.setViewportSize(-100, -100);

            // Dimensions should remain unchanged
            assertEquals(originalWidth, cameraSystem.getViewportWidth());
            assertEquals(originalHeight, cameraSystem.getViewportHeight());
        }

        @Test
        @DisplayName("Should handle multiple viewport updates")
        void shouldHandleMultipleViewportUpdates() {
            cameraSystem.setViewportSize(800, 600);
            assertEquals(800, cameraSystem.getViewportWidth());

            cameraSystem.setViewportSize(1024, 768);
            assertEquals(1024, cameraSystem.getViewportWidth());

            cameraSystem.setViewportSize(1920, 1080);
            assertEquals(1920, cameraSystem.getViewportWidth());
        }
    }

    // ======================================================================
    // VIEWPORT <-> GAME COORDINATE CONVERSION TESTS
    // ======================================================================

    @Nested
    @DisplayName("Viewport <-> Game Coordinate Conversion Tests")
    class ViewportGameConversionTests {

        @Test
        @DisplayName("Should convert viewport to game coordinates (1:1 scale)")
        void shouldConvertViewportToGameOneToOne() {
            // When viewport = game resolution, coordinates should match
            cameraSystem.setViewportSize(GAME_WIDTH, GAME_HEIGHT);

            Vector2f result = cameraSystem.viewportToGame(320, 240);

            assertEquals(320, result.x, 0.001f);
            assertEquals(240, result.y, 0.001f);
        }

        @Test
        @DisplayName("Should convert viewport to game coordinates (2x scale)")
        void shouldConvertViewportToGameDoubleScale() {
            // Viewport is 2x larger than game resolution
            cameraSystem.setViewportSize(GAME_WIDTH * 2, GAME_HEIGHT * 2);

            Vector2f result = cameraSystem.viewportToGame(640, 480);

            // Should map to center of game resolution
            assertEquals(320, result.x, 0.001f);
            assertEquals(240, result.y, 0.001f);
        }

        @Test
        @DisplayName("Should convert viewport to game coordinates (0.5x scale)")
        void shouldConvertViewportToGameHalfScale() {
            // Viewport is half the size of game resolution
            cameraSystem.setViewportSize(GAME_WIDTH / 2, GAME_HEIGHT / 2);

            Vector2f result = cameraSystem.viewportToGame(160, 120);

            // Should map to center of game resolution
            assertEquals(320, result.x, 0.001f);
            assertEquals(240, result.y, 0.001f);
        }

        @Test
        @DisplayName("Should convert game to viewport coordinates (1:1 scale)")
        void shouldConvertGameToViewportOneToOne() {
            cameraSystem.setViewportSize(GAME_WIDTH, GAME_HEIGHT);

            Vector2f result = cameraSystem.gameToViewport(320, 240);

            assertEquals(320, result.x, 0.001f);
            assertEquals(240, result.y, 0.001f);
        }

        @Test
        @DisplayName("Should convert game to viewport coordinates (2x scale)")
        void shouldConvertGameToViewportDoubleScale() {
            cameraSystem.setViewportSize(GAME_WIDTH * 2, GAME_HEIGHT * 2);

            Vector2f result = cameraSystem.gameToViewport(320, 240);

            assertEquals(640, result.x, 0.001f);
            assertEquals(480, result.y, 0.001f);
        }

        @Test
        @DisplayName("Should be reversible: viewport -> game -> viewport")
        void shouldBeReversibleViewportGameViewport() {
            cameraSystem.setViewportSize(1920, 1080);

            Vector2f original = new Vector2f(960, 540);
            Vector2f game = cameraSystem.viewportToGame(original.x, original.y);
            Vector2f back = cameraSystem.gameToViewport(game.x, game.y);

            assertEquals(original.x, back.x, 0.001f);
            assertEquals(original.y, back.y, 0.001f);
        }
    }

    // ======================================================================
    // GAME <-> WORLD COORDINATE CONVERSION TESTS
    // ======================================================================

    @Nested
    @DisplayName("Game <-> World Coordinate Conversion Tests")
    class GameWorldConversionTests {

        private Camera camera;

        @BeforeEach
        void setUp() {
            camera = new Camera();
            camera.setCameraSystem(cameraSystem);
        }

        @Test
        @DisplayName("Should convert game to world with camera at origin")
        void shouldConvertGameToWorldAtOrigin() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f result = cameraSystem.gameToWorld(camera, 0, 0, 0);

            // Top-left of screen should be at origin
            assertEquals(0, result.x, 0.1f);
            assertEquals(0, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should convert game to world with camera offset")
        void shouldConvertGameToWorldWithOffset() {
            camera.setPosition(100, 200);
            camera.setZoom(1.0f);

            Vector3f result = cameraSystem.gameToWorld(camera, 0, 0, 0);

            // Top-left of screen should be at camera position
            assertEquals(100, result.x, 0.1f);
            assertEquals(200, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should convert game to world with 2x zoom")
        void shouldConvertGameToWorldWithZoom() {
            camera.setPosition(0, 0);
            camera.setZoom(2.0f);

            // Center of screen
            Vector3f result = cameraSystem.gameToWorld(camera, GAME_WIDTH / 2, GAME_HEIGHT / 2, 0);

            // With 2x zoom, we see half the world, so center should be at quarter dimensions
            assertEquals(GAME_WIDTH / 4, result.x, 1.0f);
            assertEquals(GAME_HEIGHT / 4, result.y, 1.0f);
        }

        @Test
        @DisplayName("Should handle null camera gracefully in gameToWorld")
        void shouldHandleNullCameraInGameToWorld() {
            Vector3f result = cameraSystem.gameToWorld(null, 100, 200, 0);

            // Should return input coordinates
            assertEquals(100, result.x, 0.001f);
            assertEquals(200, result.y, 0.001f);
        }

        @Test
        @DisplayName("Should convert world to game with camera at origin")
        void shouldConvertWorldToGameAtOrigin() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f worldPos = new Vector3f(100, 200, 0);
            Vector3f result = cameraSystem.worldToGame(camera, worldPos);

            assertEquals(100, result.x, 0.1f);
            assertEquals(200, result.y, 0.1f);
        }

        @Test
        @DisplayName("Should be reversible: game -> world -> game")
        void shouldBeReversibleGameWorldGame() {
            camera.setPosition(50, 100);
            camera.setZoom(1.5f);

            Vector3f worldPos = cameraSystem.gameToWorld(camera, 320, 240, 0);
            Vector3f gamePos = cameraSystem.worldToGame(camera, worldPos);

            assertEquals(320, gamePos.x, 0.1f);
            assertEquals(240, gamePos.y, 0.1f);
        }
    }

    // ======================================================================
    // VIEWPORT <-> WORLD COORDINATE CONVERSION TESTS
    // ======================================================================

    @Nested
    @DisplayName("Viewport <-> World (Full Pipeline) Tests")
    class ViewportWorldConversionTests {

        private Camera camera;

        @BeforeEach
        void setUp() {
            camera = new Camera();
            camera.setCameraSystem(cameraSystem);
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);
        }

        @Test
        @DisplayName("Should convert viewport to world (full pipeline)")
        void shouldConvertViewportToWorld() {
            cameraSystem.setViewportSize(1280, 960);

            // Center of viewport
            Vector3f result = cameraSystem.viewportToWorld(camera, 640, 480, 0);

            // Should map to center of game world
            assertEquals(320, result.x, 1.0f);
            assertEquals(240, result.y, 1.0f);
        }

        @Test
        @DisplayName("Should convert world to viewport (full pipeline)")
        void shouldConvertWorldToViewport() {
            cameraSystem.setViewportSize(1280, 960);

            Vector3f worldPos = new Vector3f(320, 240, 0);
            Vector2f result = cameraSystem.worldToViewport(camera, worldPos);

            // Should map to center of viewport
            assertEquals(640, result.x, 1.0f);
            assertEquals(480, result.y, 1.0f);
        }

        @Test
        @DisplayName("Should be reversible: viewport -> world -> viewport")
        void shouldBeReversibleViewportWorldViewport() {
            cameraSystem.setViewportSize(1920, 1080);
            camera.setPosition(100, 50);
            camera.setZoom(1.2f);

            Vector3f worldPos = cameraSystem.viewportToWorld(camera, 960, 540, 0);
            Vector2f viewportPos = cameraSystem.worldToViewport(camera, worldPos);

            assertEquals(960, viewportPos.x, 1.0f);
            assertEquals(540, viewportPos.y, 1.0f);
        }
    }

    // ======================================================================
    // WORLD BOUNDS TESTS
    // ======================================================================

    @Nested
    @DisplayName("World Bounds Tests")
    class WorldBoundsTests {

        private Camera camera;

        @BeforeEach
        void setUp() {
            camera = new Camera();
            camera.setCameraSystem(cameraSystem);
        }

        @Test
        @DisplayName("Should return correct world bounds at origin")
        void shouldReturnWorldBoundsAtOrigin() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f[] bounds = cameraSystem.getWorldBounds(camera);

            assertEquals(4, bounds.length);

            // Top-left should be at origin
            assertEquals(0, bounds[0].x, 0.1f);
            assertEquals(0, bounds[0].y, 0.1f);

            // Bottom-right should be at game dimensions
            assertEquals(GAME_WIDTH, bounds[2].x, 0.1f);
            assertEquals(GAME_HEIGHT, bounds[2].y, 0.1f);
        }

        @Test
        @DisplayName("Should return correct world bounds with camera offset")
        void shouldReturnWorldBoundsWithOffset() {
            camera.setPosition(100, 200);
            camera.setZoom(1.0f);

            Vector3f[] bounds = cameraSystem.getWorldBounds(camera);

            // Top-left should be at camera position
            assertEquals(100, bounds[0].x, 0.1f);
            assertEquals(200, bounds[0].y, 0.1f);

            // Bottom-right should be offset by game dimensions
            assertEquals(100 + GAME_WIDTH, bounds[2].x, 0.1f);
            assertEquals(200 + GAME_HEIGHT, bounds[2].y, 0.1f);
        }

        @Test
        @DisplayName("Should return correct world center")
        void shouldReturnWorldCenter() {
            camera.setPosition(0, 0);
            camera.setZoom(1.0f);

            Vector3f center = cameraSystem.getWorldCenter(camera);

            assertEquals(GAME_WIDTH / 2.0f, center.x, 0.1f);
            assertEquals(GAME_HEIGHT / 2.0f, center.y, 0.1f);
        }

        @Test
        @DisplayName("Should return correct world center with offset")
        void shouldReturnWorldCenterWithOffset() {
            camera.setPosition(100, 200);
            camera.setZoom(1.0f);

            Vector3f center = cameraSystem.getWorldCenter(camera);

            assertEquals(100 + GAME_WIDTH / 2.0f, center.x, 0.1f);
            assertEquals(200 + GAME_HEIGHT / 2.0f, center.y, 0.1f);
        }
    }

    // ======================================================================
    // GETTERS TESTS
    // ======================================================================

    @Nested
    @DisplayName("Getters Tests")
    class GettersTests {

        @Test
        @DisplayName("Should return correct aspect ratio")
        void shouldReturnCorrectAspectRatio() {
            float expectedRatio = (float) GAME_WIDTH / (float) GAME_HEIGHT;
            assertEquals(expectedRatio, cameraSystem.getAspectRatio(), 0.001f);
        }

        @Test
        @DisplayName("Should return correct near/far planes")
        void shouldReturnCorrectPlanes() {
            assertEquals(-1.0f, cameraSystem.getNearPlane());
            assertEquals(1.0f, cameraSystem.getFarPlane());

            cameraSystem.setOrthographicPlanes(-10f, 10f);

            assertEquals(-10.0f, cameraSystem.getNearPlane());
            assertEquals(10.0f, cameraSystem.getFarPlane());
        }

        @Test
        @DisplayName("Should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            String result = cameraSystem.toString();

            assertNotNull(result);
            assertTrue(result.contains("CameraSystem"));
            assertTrue(result.contains(String.valueOf(GAME_WIDTH)));
            assertTrue(result.contains(String.valueOf(GAME_HEIGHT)));
        }
    }
}