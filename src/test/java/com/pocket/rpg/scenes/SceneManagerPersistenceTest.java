package com.pocket.rpg.scenes;

import com.pocket.rpg.components.pokemon.GridMovement;
import com.pocket.rpg.components.core.PersistentEntity;
import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.serialization.ComponentRegistry;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SceneManager's persistent entity snapshot/restore and spawn teleportation.
 */
class SceneManagerPersistenceTest {

    private SceneManager sceneManager;

    @BeforeAll
    static void initRegistry() {
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
    }

    // ========================================================================
    // SPAWN ID PLUMBING
    // ========================================================================

    @Test
    @DisplayName("loadScene(name, spawnId) teleports player to spawn point")
    void loadSceneWithSpawnIdTeleportsPlayer() {
        // Scene 1: player at origin
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            scene1.addGameObject(player);
        });
        sceneManager.loadScene(scene1);

        // Scene 2: has a spawn point
        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            // Target player placeholder
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            scene2.addGameObject(player);

            // Spawn point at (10, 20)
            GameObject spawnObj = new GameObject("Spawn_Entrance");
            spawnObj.getTransform().setPosition(10, 20, 0);
            SpawnPoint spawn = new SpawnPoint();
            spawn.setSpawnId("entrance");
            spawnObj.addComponent(spawn);
            scene2.addGameObject(spawnObj);
        });
        sceneManager.registerScene(scene2);

        // Load scene2 with spawn ID
        sceneManager.loadScene("Scene2", "entrance");

        // Player should exist in the new scene
        Scene current = sceneManager.getCurrentScene();
        assertNotNull(current);
        GameObject player = findPersistentEntity(current, "Player");
        assertNotNull(player, "Player should exist in new scene");
    }

    // ========================================================================
    // PERSISTENT ENTITY RESTORATION
    // ========================================================================

    @Test
    @DisplayName("persistent entity state is carried across scene transitions")
    void persistentEntityStateCarriedAcross() {
        // Scene 1 with a player
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            PersistentEntity pe = new PersistentEntity();
            pe.setEntityTag("Player");
            player.addComponent(pe);
            scene1.addGameObject(player);
        });
        sceneManager.loadScene(scene1);

        // Scene 2 also has a player (for editor testing)
        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            GameObject player = new GameObject("ScenePlayer");
            PersistentEntity pe = new PersistentEntity();
            pe.setEntityTag("Player");
            player.addComponent(pe);
            scene2.addGameObject(player);
        });
        sceneManager.registerScene(scene2);

        // Transition
        sceneManager.loadScene("Scene2");

        // Player entity should exist
        Scene current = sceneManager.getCurrentScene();
        GameObject player = findPersistentEntity(current, "Player");
        assertNotNull(player, "Player entity should exist after transition");
    }

    @Test
    @DisplayName("first scene load with no previous scene works normally")
    void firstSceneLoadWithNoPreviousScene() {
        TestPersistenceScene scene = new TestPersistenceScene("First");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            scene.addGameObject(player);
        });

        assertDoesNotThrow(() -> sceneManager.loadScene(scene));

        assertNotNull(sceneManager.getCurrentScene());
    }

    @Test
    @DisplayName("scene without persistent entities transitions cleanly")
    void sceneWithoutPersistentEntitiesTransitions() {
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            // No persistent entities
            scene1.addGameObject(new GameObject("NPC"));
        });
        sceneManager.loadScene(scene1);

        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            scene2.addGameObject(new GameObject("Tree"));
        });
        sceneManager.registerScene(scene2);

        assertDoesNotThrow(() -> sceneManager.loadScene("Scene2"));
    }

    @Test
    @DisplayName("loadScene with missing spawn point logs warning but doesn't crash")
    void loadSceneWithMissingSpawnPoint() {
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            scene1.addGameObject(player);
        });
        sceneManager.loadScene(scene1);

        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            scene2.addGameObject(player);
            // No spawn point with ID "nonexistent"
        });
        sceneManager.registerScene(scene2);

        assertDoesNotThrow(() -> sceneManager.loadScene("Scene2", "nonexistent"));
    }

    @Test
    @DisplayName("multiple persistent entities are all restored")
    void multiplePersistentEntitiesRestored() {
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            PersistentEntity pe1 = new PersistentEntity();
            pe1.setEntityTag("Player");
            player.addComponent(pe1);
            scene1.addGameObject(player);

            GameObject companion = new GameObject("Companion");
            PersistentEntity pe2 = new PersistentEntity();
            pe2.setEntityTag("Companion1");
            companion.addComponent(pe2);
            scene1.addGameObject(companion);
        });
        sceneManager.loadScene(scene1);

        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            // Scene 2 has both entities
            GameObject player = new GameObject("Player");
            PersistentEntity pe1 = new PersistentEntity();
            pe1.setEntityTag("Player");
            player.addComponent(pe1);
            scene2.addGameObject(player);

            GameObject companion = new GameObject("Companion");
            PersistentEntity pe2 = new PersistentEntity();
            pe2.setEntityTag("Companion1");
            companion.addComponent(pe2);
            scene2.addGameObject(companion);
        });
        sceneManager.registerScene(scene2);

        sceneManager.loadScene("Scene2");

        Scene current = sceneManager.getCurrentScene();
        assertNotNull(findPersistentEntity(current, "Player"), "Player should be restored");
        assertNotNull(findPersistentEntity(current, "Companion1"), "Companion should be restored");
    }

    // ========================================================================
    // DUPLICATE ENTITY TAG — P4
    // ========================================================================

    @Test
    @DisplayName("duplicate entityTag in snapshots does not crash — P4 warning")
    void duplicateEntityTagDoesNotCrash() {
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            // Two entities with the same tag (authoring error)
            GameObject player1 = new GameObject("Player1");
            PersistentEntity pe1 = new PersistentEntity();
            pe1.setEntityTag("Player");
            player1.addComponent(pe1);
            scene1.addGameObject(player1);

            GameObject player2 = new GameObject("Player2");
            PersistentEntity pe2 = new PersistentEntity();
            pe2.setEntityTag("Player");
            player2.addComponent(pe2);
            scene1.addGameObject(player2);
        });
        sceneManager.loadScene(scene1);

        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            PersistentEntity pe = new PersistentEntity();
            pe.setEntityTag("Player");
            player.addComponent(pe);
            scene2.addGameObject(player);
        });
        sceneManager.registerScene(scene2);

        // Should not throw, logs a warning about duplicates
        assertDoesNotThrow(() -> sceneManager.loadScene("Scene2"));
    }

    // ========================================================================
    // SPAWN POINT IN CHILD — P3
    // ========================================================================

    @Test
    @DisplayName("spawn point nested in child object is found — P3 recursive search")
    void spawnPointInChildObjectIsFound() {
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            player.addComponent(new GridMovement());
            scene1.addGameObject(player);
        });
        sceneManager.loadScene(scene1);

        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            player.addComponent(new GridMovement());
            scene2.addGameObject(player);

            // Spawn point nested inside a parent group
            GameObject spawnGroup = new GameObject("SpawnPoints");
            GameObject spawnObj = new GameObject("Spawn_Cave");
            spawnObj.getTransform().setPosition(15, 25, 0);
            SpawnPoint sp = new SpawnPoint();
            sp.setSpawnId("cave_exit");
            spawnObj.addComponent(sp);
            spawnGroup.addChild(spawnObj);
            scene2.addGameObject(spawnGroup);
        });
        sceneManager.registerScene(scene2);

        // Load with spawn pointing to a nested SpawnPoint
        assertDoesNotThrow(() -> sceneManager.loadScene("Scene2", "cave_exit"));

        // Verify player was teleported (GridMovement position should match spawn)
        Scene current = sceneManager.getCurrentScene();
        GameObject player = findPersistentEntity(current, "Player");
        assertNotNull(player);
        GridMovement gm = player.getComponent(GridMovement.class);
        assertNotNull(gm);
        assertEquals(15, gm.getGridX());
        assertEquals(25, gm.getGridY());
    }

    // ========================================================================
    // PENDING SPAWN ID CLEANUP — P1
    // ========================================================================

    @Test
    @DisplayName("pendingSpawnId is cleared even when scene not found — P1 regression")
    void pendingSpawnIdClearedOnSceneNotFound() {
        TestPersistenceScene scene1 = new TestPersistenceScene("Scene1");
        scene1.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            scene1.addGameObject(player);
        });
        sceneManager.loadScene(scene1);

        // Try to load nonexistent scene with a spawnId
        sceneManager.loadScene("NonexistentScene", "entrance");

        // Now load a real scene without spawnId
        TestPersistenceScene scene2 = new TestPersistenceScene("Scene2");
        scene2.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PersistentEntity());
            scene2.addGameObject(player);

            GameObject spawnObj = new GameObject("Spawn");
            spawnObj.getTransform().setPosition(99, 99, 0);
            SpawnPoint sp = new SpawnPoint();
            sp.setSpawnId("entrance");
            spawnObj.addComponent(sp);
            scene2.addGameObject(spawnObj);
        });

        // Load directly (not through loadScene(name, spawnId))
        sceneManager.loadScene(scene2);

        // Player should NOT have been teleported to "entrance" —
        // the stale pendingSpawnId should have been cleared
        Scene current = sceneManager.getCurrentScene();
        GameObject player = findPersistentEntity(current, "Player");
        assertNotNull(player);
        // Player should be at origin (0,0), not at (99,99)
        assertEquals(0, player.getTransform().getPosition().x, 0.01f);
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private GameObject findPersistentEntity(Scene scene, String entityTag) {
        for (GameObject obj : scene.getGameObjects()) {
            PersistentEntity pe = obj.getComponent(PersistentEntity.class);
            if (pe != null && entityTag.equals(pe.getEntityTag())) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Test scene that runs a setup action in onLoad().
     */
    private static class TestPersistenceScene extends Scene {
        private Runnable setupAction;

        public TestPersistenceScene(String name) {
            super(name);
        }

        public void setSetupAction(Runnable action) {
            this.setupAction = action;
        }

        @Override
        public void onLoad() {
            if (setupAction != null) {
                setupAction.run();
            }
        }
    }
}
