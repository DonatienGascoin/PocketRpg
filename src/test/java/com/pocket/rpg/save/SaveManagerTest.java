package com.pocket.rpg.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for the save system.
 */
class SaveManagerTest {

    private static Path testSavesDir;
    private static Gson gson;

    @BeforeAll
    static void setup() throws Exception {
        testSavesDir = Files.createTempDirectory("pocketrpg-test-saves");
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @AfterAll
    static void cleanup() throws Exception {
        // Clean up test saves
        if (testSavesDir != null && Files.exists(testSavesDir)) {
            Files.walk(testSavesDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    void testSaveDataSerialization() {
        // Create save data
        SaveData save = new SaveData();
        save.setDisplayName("Test Save");
        save.setCurrentScene("Village");
        save.setPlayTime(3600.5f);

        // Add global state
        save.getGlobalState().put("player", new HashMap<>(Map.of(
            "gold", 500,
            "level", 12
        )));

        // Add scene state
        SavedSceneState sceneState = new SavedSceneState("Village");

        SavedEntityState entityState = new SavedEntityState("chest_01");
        entityState.setPosition(new float[]{10f, 20f, 0f});
        entityState.getComponentStates().put(
            "com.pocket.rpg.components.Chest",
            Map.of("opened", true, "looted", true)
        );
        sceneState.getModifiedEntities().put("chest_01", entityState);
        sceneState.getDestroyedEntities().add("enemy_01");

        save.getSceneStates().put("Village", sceneState);

        // Serialize
        String json = gson.toJson(save);
        System.out.println("=== Serialized Save ===");
        System.out.println(json);

        // Deserialize
        SaveData loaded = gson.fromJson(json, SaveData.class);

        // Verify
        assertEquals("Test Save", loaded.getDisplayName());
        assertEquals("Village", loaded.getCurrentScene());
        assertEquals(3600.5f, loaded.getPlayTime(), 0.01f);

        // Verify global state
        @SuppressWarnings("unchecked")
        Map<String, Object> playerState = (Map<String, Object>) loaded.getGlobalState().get("player");
        assertEquals(500, ((Number) playerState.get("gold")).intValue());
        assertEquals(12, ((Number) playerState.get("level")).intValue());

        // Verify scene state
        SavedSceneState loadedScene = loaded.getSceneStates().get("Village");
        assertNotNull(loadedScene);
        assertTrue(loadedScene.getDestroyedEntities().contains("enemy_01"));

        SavedEntityState loadedEntity = loadedScene.getModifiedEntities().get("chest_01");
        assertNotNull(loadedEntity);
        assertArrayEquals(new float[]{10f, 20f, 0f}, loadedEntity.getPosition(), 0.01f);

        System.out.println("=== All assertions passed! ===");
    }

    @Test
    void testPersistentIdGeneration() {
        // Test random ID
        String id1 = PersistentId.generateId();
        String id2 = PersistentId.generateId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertEquals(8, id1.length());

        System.out.println("Generated IDs: " + id1 + ", " + id2);

        // Test deterministic ID
        String detId1 = PersistentId.deterministicId("Village", "Chest", 0);
        String detId2 = PersistentId.deterministicId("Village", "Chest", 0);
        String detId3 = PersistentId.deterministicId("Village", "Chest", 1);

        assertEquals(detId1, detId2, "Same inputs should produce same ID");
        assertNotEquals(detId1, detId3, "Different inputs should produce different IDs");

        System.out.println("Deterministic IDs: " + detId1 + " (same: " + detId2 + "), different: " + detId3);
    }

    @Test
    void testSaveSlotInfo() {
        SaveSlotInfo info = new SaveSlotInfo(
            "slot1",
            "Village - Level 5",
            System.currentTimeMillis(),
            7200.0f,
            "Village"
        );

        assertEquals("slot1", info.slotName());
        assertEquals("Village - Level 5", info.displayName());
        assertEquals(7200.0f, info.playTime());
        assertEquals("Village", info.sceneName());

        System.out.println("SaveSlotInfo: " + info);
    }
}
