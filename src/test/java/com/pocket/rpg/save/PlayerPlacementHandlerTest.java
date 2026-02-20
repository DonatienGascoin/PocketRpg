package com.pocket.rpg.save;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.components.pokemon.GridMovement;
import com.pocket.rpg.components.pokemon.PlayerMovement;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerPlacementHandler — battle return and spawn teleportation.
 */
class PlayerPlacementHandlerTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;

    @BeforeAll
    static void initSerializer() {
        com.pocket.rpg.resources.Assets.setContext(new StubAssetContext());
        Serializer.init(com.pocket.rpg.resources.Assets.getContext());
        ComponentRegistry.initialize();
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
        sceneManager.addLifecycleListener(new PlayerPlacementHandler(sceneManager));
    }

    // ========================================================================
    // Battle Return
    // ========================================================================

    @Nested
    @DisplayName("Battle Return")
    class BattleReturn {

        @Test
        @DisplayName("teleports player to saved position when returningFromBattle is true")
        void teleportsOnReturn() {
            PlayerData data = PlayerData.load();
            data.returningFromBattle = true;
            data.lastGridX = 5;
            data.lastGridY = 10;
            data.lastDirection = Direction.LEFT;
            data.save();

            TestScene scene = new TestScene("overworld");
            scene.setSetupAction(() -> addPlayer(scene, 0, 0));
            sceneManager.loadScene(scene);

            GameObject player = scene.findGameObject("Player");
            GridMovement gm = player.getComponent(GridMovement.class);
            assertEquals(5, gm.getGridX());
            assertEquals(10, gm.getGridY());
            assertEquals(Direction.LEFT, gm.getFacingDirection());

            assertFalse(PlayerData.load().returningFromBattle);
        }

        @Test
        @DisplayName("does nothing when returningFromBattle is false")
        void skipsWhenNotReturning() {
            TestScene scene = new TestScene("overworld");
            scene.setSetupAction(() -> addPlayer(scene, 0, 0));
            sceneManager.loadScene(scene);

            GameObject player = scene.findGameObject("Player");
            GridMovement gm = player.getComponent(GridMovement.class);
            assertEquals(0, gm.getGridX());
            assertEquals(0, gm.getGridY());
        }

        @Test
        @DisplayName("preserves flag when no player entity exists in scene")
        void preservesFlagWhenNoPlayer() {
            PlayerData data = PlayerData.load();
            data.returningFromBattle = true;
            data.lastGridX = 3;
            data.lastGridY = 7;
            data.save();

            TestScene scene = new TestScene("cutscene");
            sceneManager.loadScene(scene);

            PlayerData updated = PlayerData.load();
            assertTrue(updated.returningFromBattle);
            assertEquals(3, updated.lastGridX);
            assertEquals(7, updated.lastGridY);
        }

        @Test
        @DisplayName("finds player in nested children")
        void findsNestedPlayer() {
            PlayerData data = PlayerData.load();
            data.returningFromBattle = true;
            data.lastGridX = 12;
            data.lastGridY = 4;
            data.lastDirection = Direction.RIGHT;
            data.save();

            TestScene scene = new TestScene("overworld");
            scene.setSetupAction(() -> {
                GameObject parent = new GameObject("Entities");
                GameObject player = new GameObject("Player");
                player.addComponent(new GridMovement());
                player.addComponent(new PlayerInput());
                player.addComponent(new PlayerMovement());
                parent.addChild(player);
                scene.addGameObject(parent);
            });
            sceneManager.loadScene(scene);

            GameObject parent = scene.findGameObject("Entities");
            GameObject player = parent.getChildren().get(0);
            GridMovement gm = player.getComponent(GridMovement.class);
            assertEquals(12, gm.getGridX());
            assertEquals(4, gm.getGridY());
            assertEquals(Direction.RIGHT, gm.getFacingDirection());
        }
    }

    // ========================================================================
    // Spawn Teleport
    // ========================================================================

    @Nested
    @DisplayName("Spawn Teleport")
    class SpawnTeleport {

        @Test
        @DisplayName("teleports player to spawn point when spawnId is set")
        void teleportsToSpawn() {
            TestScene scene = new TestScene("overworld");
            scene.setSetupAction(() -> {
                addPlayer(scene, 0, 0);
                addSpawnPoint(scene, "door_1", 5, 10, Direction.UP);
            });

            sceneManager.registerScene(scene);
            sceneManager.loadScene("overworld", "door_1");

            GameObject player = scene.findGameObject("Player");
            GridMovement gm = player.getComponent(GridMovement.class);
            assertEquals(5, gm.getGridX());
            assertEquals(10, gm.getGridY());
            assertEquals(Direction.UP, gm.getFacingDirection());
        }

        @Test
        @DisplayName("does nothing when no spawnId is set")
        void skipsWhenNoSpawnId() {
            TestScene scene = new TestScene("overworld");
            scene.setSetupAction(() -> {
                addPlayer(scene, 3, 7);
                addSpawnPoint(scene, "door_1", 5, 10, Direction.UP);
            });

            sceneManager.loadScene(scene);

            GameObject player = scene.findGameObject("Player");
            GridMovement gm = player.getComponent(GridMovement.class);
            assertEquals(3, gm.getGridX());
            assertEquals(7, gm.getGridY());
        }

        @Test
        @DisplayName("does not crash when no player entity exists")
        void skipsWhenNoPlayer() {
            TestScene scene = new TestScene("cutscene");
            scene.setSetupAction(() -> addSpawnPoint(scene, "door_1", 5, 10, Direction.UP));

            sceneManager.registerScene(scene);
            assertDoesNotThrow(() -> sceneManager.loadScene("cutscene", "door_1"));
        }
    }

    // ========================================================================
    // Ordering
    // ========================================================================

    @Nested
    @DisplayName("Ordering")
    class Ordering {

        @Test
        @DisplayName("spawn teleport overwrites battle-return position")
        void spawnOverwritesBattleReturn() {
            PlayerData data = PlayerData.load();
            data.returningFromBattle = true;
            data.lastGridX = 12;
            data.lastGridY = 8;
            data.lastDirection = Direction.DOWN;
            data.save();

            TestScene scene = new TestScene("overworld");
            scene.setSetupAction(() -> {
                addPlayer(scene, 0, 0);
                addSpawnPoint(scene, "door_1", 5, 10, Direction.LEFT);
            });

            sceneManager.registerScene(scene);
            sceneManager.loadScene("overworld", "door_1");

            GameObject player = scene.findGameObject("Player");
            GridMovement gm = player.getComponent(GridMovement.class);
            assertEquals(5, gm.getGridX());
            assertEquals(10, gm.getGridY());
            assertEquals(Direction.LEFT, gm.getFacingDirection());

            // Battle return flag should still be cleared
            assertFalse(PlayerData.load().returningFromBattle);
        }

        @Test
        @DisplayName("battle return stands when no spawnId is provided")
        void battleReturnStandsWithoutSpawn() {
            PlayerData data = PlayerData.load();
            data.returningFromBattle = true;
            data.lastGridX = 12;
            data.lastGridY = 8;
            data.lastDirection = Direction.DOWN;
            data.save();

            TestScene scene = new TestScene("overworld");
            scene.setSetupAction(() -> {
                addPlayer(scene, 0, 0);
                addSpawnPoint(scene, "door_1", 5, 10, Direction.LEFT);
            });

            // No spawnId — battle return position should stand
            sceneManager.loadScene(scene);

            GameObject player = scene.findGameObject("Player");
            GridMovement gm = player.getComponent(GridMovement.class);
            assertEquals(12, gm.getGridX());
            assertEquals(8, gm.getGridY());
            assertEquals(Direction.DOWN, gm.getFacingDirection());
        }
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    private void addPlayer(TestScene scene, int gridX, int gridY) {
        GameObject player = new GameObject("Player");
        GridMovement gm = new GridMovement();
        player.addComponent(gm);
        player.addComponent(new PlayerInput());
        player.addComponent(new PlayerMovement());
        scene.addGameObject(player);
        gm.setGridPosition(gridX, gridY);
    }

    private void addSpawnPoint(TestScene scene, String spawnId, int x, int y, Direction facing) {
        GameObject spawnObj = new GameObject("Spawn_" + spawnId);
        spawnObj.getTransform().setLocalPosition(x, y, 0);
        SpawnPoint sp = new SpawnPoint();
        sp.setSpawnId(spawnId);
        sp.setFacingDirection(facing);
        spawnObj.addComponent(sp);
        scene.addGameObject(spawnObj);
    }

    private static class TestScene extends Scene {
        private Runnable setupAction;

        public TestScene(String name) {
            super(name);
        }

        void setSetupAction(Runnable action) {
            this.setupAction = action;
        }

        @Override
        public void onLoad() {
            if (setupAction != null) setupAction.run();
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
