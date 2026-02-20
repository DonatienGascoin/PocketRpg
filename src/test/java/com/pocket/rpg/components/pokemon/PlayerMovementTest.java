package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.save.PlayerData;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerMovement.onBeforeSceneUnload() position flush.
 */
class PlayerMovementTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;

    @BeforeAll
    static void initSerializer() {
        com.pocket.rpg.resources.Assets.setContext(new TestStubAssetContext());
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
    }

    @Test
    @DisplayName("onBeforeSceneUnload flushes position to PlayerData")
    void flushesPositionOnSceneUnload() {
        TestScene scene1 = new TestScene("route_1");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            GridMovement gm = new GridMovement();
            player.addComponent(gm);
            player.addComponent(new PlayerInput());
            player.addComponent(new PlayerMovement());
            scene1.addGameObject(player);
        });

        sceneManager.loadScene(scene1);

        // Move the player to a specific position
        GameObject player = scene1.findGameObject("Player");
        GridMovement gm = player.getComponent(GridMovement.class);
        gm.setGridPosition(7, 14);
        gm.setFacingDirection(Direction.LEFT);

        // Load another scene to trigger onBeforeSceneUnload
        TestScene scene2 = new TestScene("town");
        sceneManager.loadScene(scene2);

        // Verify PlayerData was flushed
        PlayerData data = PlayerData.load();
        assertEquals("route_1", data.lastOverworldScene);
        assertEquals(7, data.lastGridX);
        assertEquals(14, data.lastGridY);
        assertEquals(Direction.LEFT, data.lastDirection);
    }

    @Test
    @DisplayName("onBeforeSceneUnload handles missing GridMovement gracefully")
    void handlesMissingGridMovement() {
        TestScene scene1 = new TestScene("test_scene");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            // No GridMovement — @ComponentReference will be null
            player.addComponent(new PlayerMovement());
            scene1.addGameObject(player);
        });

        sceneManager.loadScene(scene1);

        // Load another scene — should not crash
        TestScene scene2 = new TestScene("other");
        assertDoesNotThrow(() -> sceneManager.loadScene(scene2));
    }

    @Test
    @DisplayName("position flush does not fire on first scene load")
    void noFlushOnFirstLoad() {
        TestScene scene1 = new TestScene("first");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new GridMovement());
            player.addComponent(new PlayerInput());
            player.addComponent(new PlayerMovement());
            scene1.addGameObject(player);
        });

        sceneManager.loadScene(scene1);

        // PlayerData should have defaults (no flush happened, no previous scene)
        PlayerData data = PlayerData.load();
        assertNull(data.lastOverworldScene);
        assertEquals(0, data.lastGridX);
        assertEquals(0, data.lastGridY);
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

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

    /** Minimal AssetContext stub. */
    private static class TestStubAssetContext implements com.pocket.rpg.resources.AssetContext {
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
