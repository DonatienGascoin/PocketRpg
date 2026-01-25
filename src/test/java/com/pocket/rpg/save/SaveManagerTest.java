package com.pocket.rpg.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the save system.
 */
class SaveManagerTest {

    private static Gson gson;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setupGson() {
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // ========================================================================
    // SaveData Tests
    // ========================================================================

    @Nested
    @DisplayName("SaveData")
    class SaveDataTests {

        @Test
        @DisplayName("should initialize with default values")
        void testDefaultValues() {
            SaveData save = new SaveData();

            assertEquals(1, save.getVersion());
            assertNotNull(save.getSaveId());
            assertFalse(save.getSaveId().isEmpty());
            assertEquals(0, save.getPlayTime());
            assertNull(save.getCurrentScene());
            assertNotNull(save.getGlobalState());
            assertTrue(save.getGlobalState().isEmpty());
            assertNotNull(save.getSceneStates());
            assertTrue(save.getSceneStates().isEmpty());
        }

        @Test
        @DisplayName("should generate unique save IDs")
        void testUniqueSaveIds() {
            SaveData save1 = new SaveData();
            SaveData save2 = new SaveData();

            assertNotEquals(save1.getSaveId(), save2.getSaveId());
        }

        @Test
        @DisplayName("should serialize and deserialize correctly")
        void testSerialization() {
            SaveData save = new SaveData();
            save.setDisplayName("Test Save");
            save.setCurrentScene("Village");
            save.setPlayTime(3600.5f);
            save.setTimestamp(System.currentTimeMillis());

            String json = gson.toJson(save);
            SaveData loaded = gson.fromJson(json, SaveData.class);

            assertEquals(save.getVersion(), loaded.getVersion());
            assertEquals(save.getSaveId(), loaded.getSaveId());
            assertEquals(save.getDisplayName(), loaded.getDisplayName());
            assertEquals(save.getCurrentScene(), loaded.getCurrentScene());
            assertEquals(save.getPlayTime(), loaded.getPlayTime(), 0.01f);
            assertEquals(save.getTimestamp(), loaded.getTimestamp());
        }

        @Test
        @DisplayName("should handle global state with multiple namespaces")
        void testGlobalStateNamespaces() {
            SaveData save = new SaveData();

            save.getGlobalState().put("player", new HashMap<>(Map.of(
                "gold", 500,
                "level", 12,
                "name", "Hero"
            )));
            save.getGlobalState().put("settings", new HashMap<>(Map.of(
                "musicVolume", 0.8,
                "sfxVolume", 1.0
            )));
            save.getGlobalState().put("achievements", new HashMap<>(Map.of(
                "firstKill", true,
                "speedrun", false
            )));

            String json = gson.toJson(save);
            SaveData loaded = gson.fromJson(json, SaveData.class);

            assertEquals(3, loaded.getGlobalState().size());

            @SuppressWarnings("unchecked")
            Map<String, Object> player = (Map<String, Object>) loaded.getGlobalState().get("player");
            assertEquals(500, ((Number) player.get("gold")).intValue());
            assertEquals("Hero", player.get("name"));

            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) loaded.getGlobalState().get("settings");
            assertEquals(0.8, ((Number) settings.get("musicVolume")).doubleValue(), 0.01);
        }

        @Test
        @DisplayName("should handle multiple scene states")
        void testMultipleSceneStates() {
            SaveData save = new SaveData();

            SavedSceneState village = new SavedSceneState("Village");
            village.getSceneFlags().put("visited", true);

            SavedSceneState dungeon = new SavedSceneState("Dungeon");
            dungeon.getSceneFlags().put("bossDefeated", true);
            dungeon.getDestroyedEntities().add("boss_01");

            save.getSceneStates().put("Village", village);
            save.getSceneStates().put("Dungeon", dungeon);

            String json = gson.toJson(save);
            SaveData loaded = gson.fromJson(json, SaveData.class);

            assertEquals(2, loaded.getSceneStates().size());
            assertTrue((Boolean) loaded.getSceneStates().get("Village").getSceneFlags().get("visited"));
            assertTrue(loaded.getSceneStates().get("Dungeon").getDestroyedEntities().contains("boss_01"));
        }

        @Test
        @DisplayName("should not need migration for current version")
        void testNoMigrationNeeded() {
            SaveData save = new SaveData();
            assertFalse(save.needsMigration());
        }
    }

    // ========================================================================
    // SavedSceneState Tests
    // ========================================================================

    @Nested
    @DisplayName("SavedSceneState")
    class SavedSceneStateTests {

        @Test
        @DisplayName("should initialize with empty collections")
        void testDefaultValues() {
            SavedSceneState state = new SavedSceneState();

            assertNotNull(state.getModifiedEntities());
            assertTrue(state.getModifiedEntities().isEmpty());
            assertNotNull(state.getDestroyedEntities());
            assertTrue(state.getDestroyedEntities().isEmpty());
            assertNotNull(state.getSceneFlags());
            assertTrue(state.getSceneFlags().isEmpty());
        }

        @Test
        @DisplayName("should set scene name via constructor")
        void testConstructorWithName() {
            SavedSceneState state = new SavedSceneState("TestScene");
            assertEquals("TestScene", state.getSceneName());
        }

        @Test
        @DisplayName("should track destroyed entities")
        void testDestroyedEntities() {
            SavedSceneState state = new SavedSceneState("Village");

            state.getDestroyedEntities().add("enemy_01");
            state.getDestroyedEntities().add("enemy_02");
            state.getDestroyedEntities().add("pickup_coin");

            String json = gson.toJson(state);
            SavedSceneState loaded = gson.fromJson(json, SavedSceneState.class);

            assertEquals(3, loaded.getDestroyedEntities().size());
            assertTrue(loaded.getDestroyedEntities().contains("enemy_01"));
            assertTrue(loaded.getDestroyedEntities().contains("enemy_02"));
            assertTrue(loaded.getDestroyedEntities().contains("pickup_coin"));
        }

        @Test
        @DisplayName("should handle various scene flag types")
        void testSceneFlags() {
            SavedSceneState state = new SavedSceneState("Dungeon");

            state.getSceneFlags().put("bossDefeated", true);
            state.getSceneFlags().put("secretsFound", 3);
            state.getSceneFlags().put("difficulty", "hard");
            state.getSceneFlags().put("completionTime", 125.5);

            String json = gson.toJson(state);
            SavedSceneState loaded = gson.fromJson(json, SavedSceneState.class);

            assertEquals(true, loaded.getSceneFlags().get("bossDefeated"));
            assertEquals(3, ((Number) loaded.getSceneFlags().get("secretsFound")).intValue());
            assertEquals("hard", loaded.getSceneFlags().get("difficulty"));
            assertEquals(125.5, ((Number) loaded.getSceneFlags().get("completionTime")).doubleValue(), 0.01);
        }
    }

    // ========================================================================
    // SavedEntityState Tests
    // ========================================================================

    @Nested
    @DisplayName("SavedEntityState")
    class SavedEntityStateTests {

        @Test
        @DisplayName("should initialize with empty component states")
        void testDefaultValues() {
            SavedEntityState state = new SavedEntityState();

            assertNull(state.getPersistentId());
            assertNull(state.getPosition());
            assertNull(state.getActive());
            assertNotNull(state.getComponentStates());
            assertTrue(state.getComponentStates().isEmpty());
        }

        @Test
        @DisplayName("should set persistent ID via constructor")
        void testConstructorWithId() {
            SavedEntityState state = new SavedEntityState("player");
            assertEquals("player", state.getPersistentId());
        }

        @Test
        @DisplayName("should handle position data")
        void testPosition() {
            SavedEntityState state = new SavedEntityState("entity_01");
            state.setPosition(new float[]{10.5f, 20.25f, 0f});

            String json = gson.toJson(state);
            SavedEntityState loaded = gson.fromJson(json, SavedEntityState.class);

            assertNotNull(loaded.getPosition());
            assertEquals(10.5f, loaded.getPosition()[0], 0.01f);
            assertEquals(20.25f, loaded.getPosition()[1], 0.01f);
            assertEquals(0f, loaded.getPosition()[2], 0.01f);
        }

        @Test
        @DisplayName("should handle nullable active state")
        void testActiveState() {
            SavedEntityState activeNull = new SavedEntityState("e1");
            SavedEntityState activeTrue = new SavedEntityState("e2");
            SavedEntityState activeFalse = new SavedEntityState("e3");

            activeTrue.setActive(true);
            activeFalse.setActive(false);

            assertNull(activeNull.getActive());
            assertTrue(activeTrue.getActive());
            assertFalse(activeFalse.getActive());

            // Test serialization
            String json = gson.toJson(activeFalse);
            SavedEntityState loaded = gson.fromJson(json, SavedEntityState.class);
            assertFalse(loaded.getActive());
        }

        @Test
        @DisplayName("should handle multiple component states")
        void testComponentStates() {
            SavedEntityState state = new SavedEntityState("player");

            state.getComponentStates().put(
                "com.pocket.rpg.components.Health",
                Map.of("currentHealth", 75, "maxHealth", 100)
            );
            state.getComponentStates().put(
                "com.pocket.rpg.components.Inventory",
                Map.of("gold", 500, "items", List.of("sword", "potion"))
            );

            String json = gson.toJson(state);
            SavedEntityState loaded = gson.fromJson(json, SavedEntityState.class);

            assertEquals(2, loaded.getComponentStates().size());

            @SuppressWarnings("unchecked")
            Map<String, Object> health = loaded.getComponentStates().get("com.pocket.rpg.components.Health");
            assertEquals(75, ((Number) health.get("currentHealth")).intValue());

            @SuppressWarnings("unchecked")
            Map<String, Object> inventory = loaded.getComponentStates().get("com.pocket.rpg.components.Inventory");
            assertEquals(500, ((Number) inventory.get("gold")).intValue());

            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) inventory.get("items");
            assertEquals(2, items.size());
            assertTrue(items.contains("sword"));
        }
    }

    // ========================================================================
    // PersistentId Tests
    // ========================================================================

    @Nested
    @DisplayName("PersistentId")
    class PersistentIdTests {

        @Test
        @DisplayName("should generate unique random IDs")
        void testRandomIdGeneration() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids.add(PersistentId.generateId());
            }
            assertEquals(100, ids.size(), "All generated IDs should be unique");
        }

        @Test
        @DisplayName("should generate 8-character IDs")
        void testIdLength() {
            for (int i = 0; i < 10; i++) {
                String id = PersistentId.generateId();
                assertEquals(8, id.length());
            }
        }

        @Test
        @DisplayName("should generate deterministic IDs for same inputs")
        void testDeterministicId() {
            String id1 = PersistentId.deterministicId("Scene1", "Entity1", 0);
            String id2 = PersistentId.deterministicId("Scene1", "Entity1", 0);
            String id3 = PersistentId.deterministicId("Scene1", "Entity1", 0);

            assertEquals(id1, id2);
            assertEquals(id2, id3);
        }

        @Test
        @DisplayName("should generate different IDs for different inputs")
        void testDeterministicIdVariations() {
            String base = PersistentId.deterministicId("Scene", "Entity", 0);
            String diffScene = PersistentId.deterministicId("OtherScene", "Entity", 0);
            String diffEntity = PersistentId.deterministicId("Scene", "OtherEntity", 0);
            String diffIndex = PersistentId.deterministicId("Scene", "Entity", 1);

            assertNotEquals(base, diffScene);
            assertNotEquals(base, diffEntity);
            assertNotEquals(base, diffIndex);
        }

        @Test
        @DisplayName("should create component with null ID by default")
        void testDefaultConstructor() {
            PersistentId pid = new PersistentId();
            assertNull(pid.getId());
            assertNull(pid.getPersistenceTag());
        }

        @Test
        @DisplayName("should create component with specified ID")
        void testIdConstructor() {
            PersistentId pid = new PersistentId("my-entity");
            assertEquals("my-entity", pid.getId());
        }

        @Test
        @DisplayName("should allow setting persistence tag")
        void testPersistenceTag() {
            PersistentId pid = new PersistentId("chest_01");
            pid.setPersistenceTag("chest");

            assertEquals("chest_01", pid.getId());
            assertEquals("chest", pid.getPersistenceTag());
        }
    }

    // ========================================================================
    // SaveSlotInfo Tests
    // ========================================================================

    @Nested
    @DisplayName("SaveSlotInfo")
    class SaveSlotInfoTests {

        @Test
        @DisplayName("should store all properties correctly")
        void testProperties() {
            long timestamp = System.currentTimeMillis();
            SaveSlotInfo info = new SaveSlotInfo(
                "slot1",
                "My Save Game",
                timestamp,
                7200.5f,
                "DungeonLevel3"
            );

            assertEquals("slot1", info.slotName());
            assertEquals("My Save Game", info.displayName());
            assertEquals(timestamp, info.timestamp());
            assertEquals(7200.5f, info.playTime(), 0.01f);
            assertEquals("DungeonLevel3", info.sceneName());
        }

        @Test
        @DisplayName("should implement record equality")
        void testEquality() {
            long timestamp = 1234567890L;
            SaveSlotInfo info1 = new SaveSlotInfo("slot1", "Save", timestamp, 100f, "Scene");
            SaveSlotInfo info2 = new SaveSlotInfo("slot1", "Save", timestamp, 100f, "Scene");
            SaveSlotInfo info3 = new SaveSlotInfo("slot2", "Save", timestamp, 100f, "Scene");

            assertEquals(info1, info2);
            assertNotEquals(info1, info3);
        }
    }

    // ========================================================================
    // File I/O Tests
    // ========================================================================

    @Nested
    @DisplayName("File I/O")
    class FileIOTests {

        @Test
        @DisplayName("should write and read save file correctly")
        void testWriteAndReadSaveFile() throws IOException {
            SaveData save = new SaveData();
            save.setDisplayName("Test Save");
            save.setCurrentScene("Village");
            save.setPlayTime(1800f);
            save.getGlobalState().put("player", new HashMap<>(Map.of("gold", 100)));

            // Write to file
            Path savePath = tempDir.resolve("test.save");
            String json = gson.toJson(save);
            Files.writeString(savePath, json);

            assertTrue(Files.exists(savePath));

            // Read from file
            String loadedJson = Files.readString(savePath);
            SaveData loaded = gson.fromJson(loadedJson, SaveData.class);

            assertEquals(save.getSaveId(), loaded.getSaveId());
            assertEquals(save.getDisplayName(), loaded.getDisplayName());
            assertEquals(save.getCurrentScene(), loaded.getCurrentScene());
            assertEquals(save.getPlayTime(), loaded.getPlayTime(), 0.01f);
        }

        @Test
        @DisplayName("should handle complex nested save data")
        void testComplexSaveFile() throws IOException {
            SaveData save = createComplexSaveData();

            Path savePath = tempDir.resolve("complex.save");
            Files.writeString(savePath, gson.toJson(save));

            SaveData loaded = gson.fromJson(Files.readString(savePath), SaveData.class);

            // Verify structure preserved
            assertEquals(2, loaded.getGlobalState().size());
            assertEquals(2, loaded.getSceneStates().size());

            SavedSceneState village = loaded.getSceneStates().get("Village");
            assertEquals(2, village.getModifiedEntities().size());
            assertEquals(3, village.getDestroyedEntities().size());

            SavedEntityState player = village.getModifiedEntities().get("player");
            assertEquals(2, player.getComponentStates().size());
        }

        private SaveData createComplexSaveData() {
            SaveData save = new SaveData();
            save.setDisplayName("Complex Save");
            save.setCurrentScene("Village");
            save.setPlayTime(7200f);
            save.setTimestamp(System.currentTimeMillis());

            // Global state
            save.getGlobalState().put("player", new HashMap<>(Map.of(
                "gold", 1500,
                "level", 15,
                "class", "warrior"
            )));
            save.getGlobalState().put("quests", new HashMap<>(Map.of(
                "mainQuest", "ACT_2",
                "sideQuestCount", 5
            )));

            // Village scene
            SavedSceneState village = new SavedSceneState("Village");
            village.getSceneFlags().put("visited", true);
            village.getSceneFlags().put("shopUnlocked", true);

            SavedEntityState player = new SavedEntityState("player");
            player.setPosition(new float[]{100f, 50f, 0f});
            player.getComponentStates().put("Health", Map.of("current", 80, "max", 100));
            player.getComponentStates().put("Inventory", Map.of("items", List.of("sword", "shield")));
            village.getModifiedEntities().put("player", player);

            SavedEntityState chest = new SavedEntityState("chest_01");
            chest.getComponentStates().put("Chest", Map.of("opened", true));
            village.getModifiedEntities().put("chest_01", chest);

            village.getDestroyedEntities().addAll(Set.of("enemy_01", "enemy_02", "pickup_01"));

            save.getSceneStates().put("Village", village);

            // Dungeon scene
            SavedSceneState dungeon = new SavedSceneState("Dungeon");
            dungeon.getSceneFlags().put("bossDefeated", true);
            dungeon.getDestroyedEntities().add("boss_dragon");
            save.getSceneStates().put("Dungeon", dungeon);

            return save;
        }
    }

    // ========================================================================
    // ISaveable Contract Tests
    // ========================================================================

    @Nested
    @DisplayName("ISaveable Contract")
    class ISaveableContractTests {

        @Test
        @DisplayName("should have default hasSaveableState returning true")
        void testDefaultHasSaveableState() {
            ISaveable saveable = new ISaveable() {
                @Override
                public Map<String, Object> getSaveState() {
                    return Map.of("test", true);
                }

                @Override
                public void loadSaveState(Map<String, Object> state) {
                }
            };

            assertTrue(saveable.hasSaveableState());
        }

        @Test
        @DisplayName("should allow overriding hasSaveableState")
        void testOverrideHasSaveableState() {
            ISaveable emptyState = new ISaveable() {
                private boolean hasData = false;

                @Override
                public Map<String, Object> getSaveState() {
                    return hasData ? Map.of("data", true) : Map.of();
                }

                @Override
                public void loadSaveState(Map<String, Object> state) {
                    hasData = state != null && !state.isEmpty();
                }

                @Override
                public boolean hasSaveableState() {
                    return hasData;
                }
            };

            assertFalse(emptyState.hasSaveableState());
        }

        @Test
        @DisplayName("should handle null state in loadSaveState")
        void testNullStateHandling() {
            final int[] value = {0};

            ISaveable saveable = new ISaveable() {
                @Override
                public Map<String, Object> getSaveState() {
                    return Map.of("value", value[0]);
                }

                @Override
                public void loadSaveState(Map<String, Object> state) {
                    if (state != null && state.containsKey("value")) {
                        value[0] = ((Number) state.get("value")).intValue();
                    }
                }
            };

            // Should not throw
            assertDoesNotThrow(() -> saveable.loadSaveState(null));
            assertEquals(0, value[0]);

            // Should load valid state
            saveable.loadSaveState(Map.of("value", 42));
            assertEquals(42, value[0]);
        }
    }

    // ========================================================================
    // Integration Test - Full Save/Load Cycle
    // ========================================================================

    @Test
    @DisplayName("Full save/load cycle should preserve all data")
    void testFullSaveLoadCycle() throws IOException {
        // Create initial save
        SaveData original = new SaveData();
        original.setDisplayName("Integration Test Save");
        original.setCurrentScene("TestScene");
        original.setPlayTime(999.5f);
        original.setTimestamp(System.currentTimeMillis());

        // Add global state
        original.getGlobalState().put("game", new HashMap<>(Map.of(
            "difficulty", "hard",
            "newGamePlus", true,
            "playCount", 3
        )));

        // Add scene with entities
        SavedSceneState scene = new SavedSceneState("TestScene");
        scene.getSceneFlags().put("completed", false);
        scene.getSceneFlags().put("secretsFound", 2);

        SavedEntityState entity = new SavedEntityState("test_entity");
        entity.setPosition(new float[]{1.5f, 2.5f, 3.5f});
        entity.setActive(true);
        entity.getComponentStates().put("TestComponent", Map.of(
            "stringVal", "hello",
            "intVal", 42,
            "boolVal", true,
            "listVal", List.of("a", "b", "c")
        ));
        scene.getModifiedEntities().put("test_entity", entity);
        scene.getDestroyedEntities().add("destroyed_entity");

        original.getSceneStates().put("TestScene", scene);

        // Save to file
        Path savePath = tempDir.resolve("integration.save");
        Files.writeString(savePath, gson.toJson(original));

        // Load from file
        SaveData loaded = gson.fromJson(Files.readString(savePath), SaveData.class);

        // Verify everything
        assertEquals(original.getSaveId(), loaded.getSaveId());
        assertEquals(original.getDisplayName(), loaded.getDisplayName());
        assertEquals(original.getCurrentScene(), loaded.getCurrentScene());
        assertEquals(original.getPlayTime(), loaded.getPlayTime(), 0.001f);

        // Verify global state
        @SuppressWarnings("unchecked")
        Map<String, Object> gameState = (Map<String, Object>) loaded.getGlobalState().get("game");
        assertEquals("hard", gameState.get("difficulty"));
        assertEquals(true, gameState.get("newGamePlus"));
        assertEquals(3, ((Number) gameState.get("playCount")).intValue());

        // Verify scene state
        SavedSceneState loadedScene = loaded.getSceneStates().get("TestScene");
        assertNotNull(loadedScene);
        assertEquals(false, loadedScene.getSceneFlags().get("completed"));
        assertEquals(2, ((Number) loadedScene.getSceneFlags().get("secretsFound")).intValue());
        assertTrue(loadedScene.getDestroyedEntities().contains("destroyed_entity"));

        // Verify entity state
        SavedEntityState loadedEntity = loadedScene.getModifiedEntities().get("test_entity");
        assertNotNull(loadedEntity);
        assertArrayEquals(new float[]{1.5f, 2.5f, 3.5f}, loadedEntity.getPosition(), 0.001f);
        assertTrue(loadedEntity.getActive());

        @SuppressWarnings("unchecked")
        Map<String, Object> compState = loadedEntity.getComponentStates().get("TestComponent");
        assertEquals("hello", compState.get("stringVal"));
        assertEquals(42, ((Number) compState.get("intVal")).intValue());
        assertEquals(true, compState.get("boolVal"));

        @SuppressWarnings("unchecked")
        List<String> listVal = (List<String>) compState.get("listVal");
        assertEquals(List.of("a", "b", "c"), listVal);
    }
}
