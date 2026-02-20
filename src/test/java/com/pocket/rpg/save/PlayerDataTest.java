package com.pocket.rpg.save;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.AssetsConfiguration;
import com.pocket.rpg.resources.CacheStats;
import com.pocket.rpg.resources.LoadOptions;
import com.pocket.rpg.scenes.MockSceneManager;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerData persistence.
 */
class PlayerDataTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initSerializer() {
        StubAssetContext stub = new StubAssetContext();
        Assets.setContext(stub);
        Serializer.init(stub);
    }

    @BeforeEach
    void setUp() {
        SaveManager.initialize(new MockSceneManager(), tempDir);
        SaveManager.newGame();
    }

    /** Minimal AssetContext stub for tests that don't need asset loading. */
    private static class StubAssetContext implements AssetContext {
        @Override public <T> T load(String path) { return null; }
        @Override public <T> T load(String path, LoadOptions loadOptions) { return null; }
        @Override public <T> T load(String path, Class<T> type) { return null; }
        @Override public <T> T load(String path, LoadOptions loadOptions, Class<T> type) { return null; }
        @Override public <T> T get(String path) { return null; }
        @Override public <T> List<T> getAll(Class<T> type) { return Collections.emptyList(); }
        @Override public boolean isLoaded(String path) { return false; }
        @Override public Set<String> getLoadedPaths() { return Collections.emptySet(); }
        @Override public String getPathForResource(Object resource) { return null; }
        @Override public void persist(Object resource) {}
        @Override public void persist(Object resource, String path) {}
        @Override public void persist(Object resource, String path, LoadOptions options) {}
        @Override public AssetsConfiguration configure() { return null; }
        @Override public CacheStats getStats() { return null; }
        @Override public List<String> scanByType(Class<?> type) { return Collections.emptyList(); }
        @Override public List<String> scanByType(Class<?> type, String directory) { return Collections.emptyList(); }
        @Override public List<String> scanAll() { return Collections.emptyList(); }
        @Override public List<String> scanAll(String directory) { return Collections.emptyList(); }
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
        @Override public Set<com.pocket.rpg.resources.EditorCapability> getEditorCapabilities(Class<?> type) { return Collections.emptySet(); }
        @Override public String getIconCodepoint(Class<?> type) { return null; }
    }

    // ========================================================================
    // In-Memory Round-Trip
    // ========================================================================

    @Test
    @DisplayName("save() then load() preserves all fields")
    void testInMemoryRoundTrip() {
        PlayerData data = new PlayerData();
        data.lastOverworldScene = "route_1";
        data.lastGridX = 12;
        data.lastGridY = 8;
        data.lastDirection = Direction.DOWN;
        data.returningFromBattle = true;
        data.playerName = "Red";
        data.money = 3000;
        data.boxNames = List.of("Box 1", "Box 2", "Box 3");

        data.save();

        PlayerData loaded = PlayerData.load();

        assertEquals("route_1", loaded.lastOverworldScene);
        assertEquals(12, loaded.lastGridX);
        assertEquals(8, loaded.lastGridY);
        assertEquals(Direction.DOWN, loaded.lastDirection);
        assertTrue(loaded.returningFromBattle);
        assertEquals("Red", loaded.playerName);
        assertEquals(3000, loaded.money);
        assertEquals(List.of("Box 1", "Box 2", "Box 3"), loaded.boxNames);
    }

    // ========================================================================
    // Disk Round-Trip
    // ========================================================================

    @Test
    @DisplayName("save() then SaveManager.save() then SaveManager.load() then load() preserves all fields")
    void testDiskRoundTrip() {
        PlayerData data = new PlayerData();
        data.lastOverworldScene = "town_square";
        data.lastGridX = 5;
        data.lastGridY = 10;
        data.lastDirection = Direction.UP;
        data.returningFromBattle = false;
        data.playerName = "Blue";
        data.money = 500;
        data.boxNames = List.of("My Box");

        data.save();

        // Write to disk
        assertTrue(SaveManager.save("test_slot"));

        // Clear in-memory state and reload from disk
        SaveManager.newGame();
        PlayerData cleared = PlayerData.load();
        assertNull(cleared.lastOverworldScene, "After newGame, PlayerData should be empty");

        // Load from disk
        assertTrue(SaveManager.load("test_slot"));

        PlayerData loaded = PlayerData.load();

        assertEquals("town_square", loaded.lastOverworldScene);
        assertEquals(5, loaded.lastGridX);
        assertEquals(10, loaded.lastGridY);
        assertEquals(Direction.UP, loaded.lastDirection);
        assertFalse(loaded.returningFromBattle);
        assertEquals("Blue", loaded.playerName);
        assertEquals(500, loaded.money);
        assertEquals(List.of("My Box"), loaded.boxNames);
    }

    // ========================================================================
    // Empty / Default State
    // ========================================================================

    @Test
    @DisplayName("load() with empty globalState returns fresh instance with defaults")
    void testLoadEmptyState() {
        PlayerData data = PlayerData.load();

        assertNull(data.lastOverworldScene);
        assertEquals(0, data.lastGridX);
        assertEquals(0, data.lastGridY);
        assertNull(data.lastDirection);
        assertFalse(data.returningFromBattle);
        assertNull(data.playerName);
        assertEquals(0, data.money);
        assertNull(data.boxNames);
    }

    // ========================================================================
    // Forward Compatibility (missing fields)
    // ========================================================================

    @Test
    @DisplayName("Gson handles missing fields gracefully (old save file without new fields)")
    void testForwardCompatibility() {
        // Simulate an old save file that only has playerName and money
        String oldJson = "{\"playerName\":\"Old\",\"money\":100}";
        SaveManager.setGlobal("player", "data", oldJson);

        PlayerData loaded = PlayerData.load();

        assertEquals("Old", loaded.playerName);
        assertEquals(100, loaded.money);
        // Fields not in JSON get Java defaults
        assertNull(loaded.lastOverworldScene);
        assertEquals(0, loaded.lastGridX);
        assertEquals(0, loaded.lastGridY);
        assertNull(loaded.lastDirection);
        assertFalse(loaded.returningFromBattle);
        assertNull(loaded.boxNames);
    }

    // ========================================================================
    // Multiple save/load cycles
    // ========================================================================

    @Test
    @DisplayName("Multiple save cycles update state correctly")
    void testMultipleSaveCycles() {
        PlayerData data = new PlayerData();
        data.money = 100;
        data.save();

        PlayerData loaded = PlayerData.load();
        assertEquals(100, loaded.money);

        // Update and save again
        loaded.money = 200;
        loaded.lastGridX = 5;
        loaded.save();

        PlayerData loaded2 = PlayerData.load();
        assertEquals(200, loaded2.money);
        assertEquals(5, loaded2.lastGridX);
    }
}
