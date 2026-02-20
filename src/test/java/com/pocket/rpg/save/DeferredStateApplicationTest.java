package com.pocket.rpg.save;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.pokemon.GridMovement;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.scenes.MockSceneManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for deferred ISaveable state application and GridMovement ISaveable.
 */
class DeferredStateApplicationTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;

    @BeforeAll
    static void initSerializer() {
        // Reuse the StubAssetContext from PlayerDataTest approach
        com.pocket.rpg.resources.Assets.setContext(new StubAssetContext());
        Serializer.init(com.pocket.rpg.resources.Assets.getContext());
    }

    @BeforeEach
    void setUp() {
        sceneManager = new SceneManager(
                new ViewportConfig(GameConfig.builder()
                        .gameWidth(800).gameHeight(600)
                        .windowWidth(800).windowHeight(600)
                        .build()),
                RenderingConfig.builder().defaultOrthographicSize(7.5f).build()
        );
        SaveManager.initialize(sceneManager, tempDir);
        SaveManager.newGame();
    }

    // ========================================================================
    // Deferred State Application
    // ========================================================================

    @Nested
    @DisplayName("Deferred State Application")
    class DeferredTests {

        @Test
        @DisplayName("ISaveable loadSaveState() runs after all onStart() calls complete")
        void stateAppliedAfterAllStarts() {
            List<String> events = new ArrayList<>();

            // Create a scene with a PersistentId and a tracking ISaveable component
            TestScene scene1 = new TestScene("Scene1");
            scene1.setSetupAction(() -> {
                GameObject go = new GameObject("Entity");
                go.addComponent(new PersistentId("entity1"));
                go.addComponent(new TrackingISaveable(events));
                scene1.addGameObject(go);
            });

            // Load scene to register entity
            sceneManager.loadScene(scene1);

            // Save state for the entity
            assertTrue(SaveManager.save("test"));

            // Clear and reload
            TestScene scene2 = new TestScene("Scene1");
            scene2.setSetupAction(() -> {
                GameObject go = new GameObject("Entity");
                go.addComponent(new PersistentId("entity1"));
                go.addComponent(new TrackingISaveable(events));
                scene2.addGameObject(go);
            });

            events.clear();
            assertTrue(SaveManager.load("test"));
            sceneManager.loadScene(scene2);

            // Verify onStart happened before loadSaveState
            int startIdx = events.indexOf("onStart");
            int loadIdx = events.indexOf("loadSaveState");

            assertTrue(startIdx >= 0, "onStart should have been called");
            assertTrue(loadIdx >= 0, "loadSaveState should have been called");
            assertTrue(startIdx < loadIdx,
                    "onStart should fire before loadSaveState (deferred application)");
        }

        @Test
        @DisplayName("late-registered entity gets state applied immediately")
        void lateRegisteredEntityGetsStateApplied() {
            // Create scene and save entity state
            TestScene scene1 = new TestScene("Scene1");
            scene1.setSetupAction(() -> {
                GameObject go = new GameObject("Entity");
                go.addComponent(new PersistentId("late_entity"));
                TrackingISaveable tracker = new TrackingISaveable(new ArrayList<>());
                go.addComponent(tracker);
                scene1.addGameObject(go);
            });

            sceneManager.loadScene(scene1);
            assertTrue(SaveManager.save("test"));

            // Reload scene without the entity initially
            TestScene scene2 = new TestScene("Scene1");
            scene2.setSetupAction(() -> {
                // Empty scene initially
            });

            assertTrue(SaveManager.load("test"));
            sceneManager.loadScene(scene2);

            // Now add entity AFTER scene init (late registration)
            List<String> events = new ArrayList<>();
            GameObject lateGo = new GameObject("Entity");
            lateGo.addComponent(new PersistentId("late_entity"));
            TrackingISaveable tracker = new TrackingISaveable(events);
            lateGo.addComponent(tracker);
            scene2.addGameObject(lateGo);

            assertTrue(events.contains("loadSaveState"),
                    "Late-registered entity should get state applied immediately");
        }

        @Test
        @DisplayName("captureCurrentSceneState() finds entities after scene load")
        void captureFindsEntitiesAfterLoad() {
            TestScene scene1 = new TestScene("Scene1");
            scene1.setSetupAction(() -> {
                GameObject go = new GameObject("Entity");
                go.addComponent(new PersistentId("persistent1"));
                go.addComponent(new TrackingISaveable(new ArrayList<>()));
                scene1.addGameObject(go);
            });

            sceneManager.loadScene(scene1);

            // Should be able to save — entities registered and not cleared
            assertTrue(SaveManager.save("test"),
                    "Save should succeed with registered entities");
        }
    }

    // ========================================================================
    // GridMovement ISaveable
    // ========================================================================

    @Nested
    @DisplayName("GridMovement ISaveable")
    class GridMovementISaveableTests {

        @Test
        @DisplayName("getSaveState() returns gridX, gridY, facingDirection")
        void getSaveStateReturnsCorrectFields() {
            TestScene scene = new TestScene("TestScene");
            GridMovement movement = new GridMovement();

            GameObject go = new GameObject("Player");
            go.addComponent(movement);
            scene.addDeferredGameObject(go);

            sceneManager.loadScene(scene);

            movement.setGridPosition(5, 10);
            movement.setFacingDirection(Direction.LEFT);

            Map<String, Object> state = movement.getSaveState();

            assertEquals(5, state.get("gridX"));
            assertEquals(10, state.get("gridY"));
            assertEquals("LEFT", state.get("facingDirection"));
        }

        @Test
        @DisplayName("loadSaveState() restores position and direction")
        void loadSaveStateRestoresPositionAndDirection() {
            TestScene scene = new TestScene("TestScene");
            GridMovement movement = new GridMovement();

            GameObject go = new GameObject("Player");
            go.addComponent(movement);
            scene.addDeferredGameObject(go);

            sceneManager.loadScene(scene);

            // Initial position is 0,0
            assertEquals(0, movement.getGridX());
            assertEquals(0, movement.getGridY());
            assertEquals(Direction.DOWN, movement.getFacingDirection());

            // Load state
            movement.loadSaveState(Map.of(
                    "gridX", 7,
                    "gridY", 3,
                    "facingDirection", "RIGHT"
            ));

            assertEquals(7, movement.getGridX());
            assertEquals(3, movement.getGridY());
            assertEquals(Direction.RIGHT, movement.getFacingDirection());
        }

        @Test
        @DisplayName("loadSaveState() handles null gracefully")
        void loadSaveStateHandlesNull() {
            TestScene scene = new TestScene("TestScene");
            GridMovement movement = new GridMovement();

            GameObject go = new GameObject("Player");
            go.addComponent(movement);
            scene.addDeferredGameObject(go);

            sceneManager.loadScene(scene);

            assertDoesNotThrow(() -> movement.loadSaveState(null));
            assertEquals(0, movement.getGridX());
            assertEquals(0, movement.getGridY());
        }

        @Test
        @DisplayName("loadSaveState() handles missing keys gracefully")
        void loadSaveStateHandlesMissingKeys() {
            TestScene scene = new TestScene("TestScene");
            GridMovement movement = new GridMovement();

            GameObject go = new GameObject("Player");
            go.addComponent(movement);
            scene.addDeferredGameObject(go);

            sceneManager.loadScene(scene);

            // Only set direction, no position keys
            movement.loadSaveState(Map.of("facingDirection", "UP"));

            assertEquals(0, movement.getGridX()); // Unchanged
            assertEquals(0, movement.getGridY()); // Unchanged
            assertEquals(Direction.UP, movement.getFacingDirection());
        }

        @Test
        @DisplayName("full save/load round-trip preserves GridMovement state")
        void fullRoundTripPreservesState() {
            // Scene with PersistentId + GridMovement
            TestScene scene1 = new TestScene("Scene1");
            scene1.setSetupAction(() -> {
                GameObject go = new GameObject("Player");
                go.addComponent(new PersistentId("player1"));
                go.addComponent(new GridMovement());
                scene1.addGameObject(go);
            });

            sceneManager.loadScene(scene1);

            // Move player to a specific position
            GameObject player = scene1.findGameObject("Player");
            GridMovement movement = player.getComponent(GridMovement.class);
            movement.setGridPosition(8, 12);
            movement.setFacingDirection(Direction.RIGHT);

            // Save to disk
            assertTrue(SaveManager.save("test_gm"));

            // Reload from disk into a fresh scene
            TestScene scene2 = new TestScene("Scene1");
            scene2.setSetupAction(() -> {
                GameObject go = new GameObject("Player");
                go.addComponent(new PersistentId("player1"));
                go.addComponent(new GridMovement());
                scene2.addGameObject(go);
            });

            assertTrue(SaveManager.load("test_gm"));
            sceneManager.loadScene(scene2);

            // Verify state restored
            GameObject restoredPlayer = scene2.findGameObject("Player");
            GridMovement restoredMovement = restoredPlayer.getComponent(GridMovement.class);

            assertEquals(8, restoredMovement.getGridX());
            assertEquals(12, restoredMovement.getGridY());
            assertEquals(Direction.RIGHT, restoredMovement.getFacingDirection());
        }
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    /** Scene that allows adding GameObjects before initialization via onLoad(). */
    private static class TestScene extends Scene {
        private final List<GameObject> deferredObjects = new ArrayList<>();
        private Runnable setupAction;

        public TestScene(String name) {
            super(name);
        }

        void addDeferredGameObject(GameObject go) {
            deferredObjects.add(go);
        }

        void setSetupAction(Runnable action) {
            this.setupAction = action;
        }

        @Override
        public void onLoad() {
            for (GameObject go : deferredObjects) {
                addGameObject(go);
            }
            if (setupAction != null) {
                setupAction.run();
            }
        }
    }

    /** ISaveable component that tracks when loadSaveState is called. */
    private static class TrackingISaveable extends Component implements ISaveable {
        private final List<String> events;

        TrackingISaveable(List<String> events) {
            this.events = events;
        }

        @Override
        protected void onStart() {
            events.add("onStart");
        }

        @Override
        public Map<String, Object> getSaveState() {
            return Map.of("tracked", true);
        }

        @Override
        public void loadSaveState(Map<String, Object> state) {
            events.add("loadSaveState");
        }
    }

    /** Minimal AssetContext stub for Serializer initialization. */
    private static class StubAssetContext implements com.pocket.rpg.resources.AssetContext {
        @Override public <T> T load(String path) { return null; }
        @Override public <T> T load(String path, com.pocket.rpg.resources.LoadOptions loadOptions) { return null; }
        @Override public <T> T load(String path, Class<T> type) { return null; }
        @Override public <T> T load(String path, com.pocket.rpg.resources.LoadOptions loadOptions, Class<T> type) { return null; }
        @Override public <T> T get(String path) { return null; }
        @Override public <T> java.util.List<T> getAll(Class<T> type) { return java.util.Collections.emptyList(); }
        @Override public boolean isLoaded(String path) { return false; }
        @Override public java.util.Set<String> getLoadedPaths() { return java.util.Collections.emptySet(); }
        @Override public String getPathForResource(Object resource) { return null; }
        @Override public void persist(Object resource) {}
        @Override public void persist(Object resource, String path) {}
        @Override public void persist(Object resource, String path, com.pocket.rpg.resources.LoadOptions options) {}
        @Override public com.pocket.rpg.resources.AssetsConfiguration configure() { return null; }
        @Override public com.pocket.rpg.resources.CacheStats getStats() { return null; }
        @Override public java.util.List<String> scanByType(Class<?> type) { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanByType(Class<?> type, String directory) { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanAll() { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanAll(String directory) { return java.util.Collections.emptyList(); }
        @Override public void setAssetRoot(String assetRoot) {}
        @Override public String getAssetRoot() { return null; }
        @Override public com.pocket.rpg.resources.ResourceCache getCache() { return null; }
        @Override public void setErrorMode(com.pocket.rpg.resources.ErrorMode errorMode) {}
        @Override public void setStatisticsEnabled(boolean enableStatistics) {}
        @Override public String getRelativePath(String fullPath) { return null; }
        @Override public com.pocket.rpg.rendering.resources.Sprite getPreviewSprite(String path, Class<?> type) { return null; }
        @Override public Class<?> getTypeForPath(String path) { return null; }
        @Override public void registerResource(Object resource, String path) {}
        @Override public void unregisterResource(Object resource) {}
        @Override public boolean isAssetType(Class<?> type) { return false; }
        @Override public boolean canInstantiate(Class<?> type) { return false; }
        @Override public com.pocket.rpg.editor.scene.EditorGameObject instantiate(String path, Class<?> type, org.joml.Vector3f position) { return null; }
        @Override public com.pocket.rpg.editor.EditorPanelType getEditorPanelType(Class<?> type) { return null; }
        @Override public java.util.Set<com.pocket.rpg.resources.EditorCapability> getEditorCapabilities(Class<?> type) { return java.util.Collections.emptySet(); }
        @Override public String getIconCodepoint(Class<?> type) { return null; }
    }
}
