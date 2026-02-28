package com.pocket.rpg.editor.serialization;

import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.JsonPrefabHierarchyTest;
import com.pocket.rpg.prefab.PrefabHierarchyHelper;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the new prefab serialization format where each prefab instance is stored
 * as a single scene entry with a `prefab` asset path and `childOverrides` map,
 * rather than one entry per hierarchy node.
 */
class PrefabSerializationFormatTest {

    private static final String PREFAB_ID = "guard_tower";
    private static final String SR_TYPE = SpriteRenderer.class.getName();
    private static final String TRANSFORM_TYPE = Transform.class.getName();

    private EditorScene scene;
    private JsonPrefab guardTower;

    @BeforeAll
    static void initRegistry() {
        ComponentRegistry.initialize();
    }

    @BeforeEach
    void setUp() {
        PrefabRegistry.getInstance().clear();
        guardTower = JsonPrefabHierarchyTest.buildGuardTowerFixture();
        guardTower.setSourcePath("gameData/prefabs/guard_tower.prefab");
        PrefabRegistry.getInstance().register(guardTower);

        scene = new EditorScene();
    }

    @AfterAll
    static void cleanup() {
        PrefabRegistry.getInstance().clear();
    }

    /**
     * Creates a prefab instance root + expanded children in the scene.
     */
    private EditorGameObject createPrefabInstance(Vector3f position) {
        EditorGameObject root = new EditorGameObject(PREFAB_ID, position);
        root.setName("Tower_1");
        root.setOrder(0);
        scene.addEntity(root);

        List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, guardTower);
        for (EditorGameObject child : descendants) {
            scene.addEntity(child);
        }
        scene.resolveHierarchy();
        return root;
    }

    // ========================================================================
    // SAVE FORMAT
    // ========================================================================

    @Nested
    class SaveFormat {

        @Test
        void toSceneData_singleEntryPerPrefabInstance() {
            createPrefabInstance(new Vector3f(10, 5, 0));

            SceneData sceneData = EditorSceneSerializer.toSceneData(scene);

            // Should have exactly 1 entry (the root), not 5 (root + 4 children)
            List<GameObjectData> gameObjects = sceneData.getGameObjects();
            assertEquals(1, gameObjects.size(), "Should be 1 entry, not one per child");

            GameObjectData rootData = gameObjects.getFirst();
            assertEquals("Tower_1", rootData.getName());
            assertNotNull(rootData.getPrefab(), "Should have prefab asset path");
            assertEquals("gameData/prefabs/guard_tower.prefab", rootData.getPrefab());
        }

        @Test
        void toSceneData_childrenSkipped() {
            createPrefabInstance(new Vector3f(0, 0, 0));

            SceneData sceneData = EditorSceneSerializer.toSceneData(scene);
            List<GameObjectData> gameObjects = sceneData.getGameObjects();

            // No entries should have prefabNodeId (children are collapsed into root)
            for (GameObjectData goData : gameObjects) {
                assertNull(goData.getPrefabNodeId(),
                        "No child entries should appear separately: " + goData.getName());
            }
        }

        @Test
        void toSceneData_childOverrides_onlyIncludedWhenModified() {
            EditorGameObject root = createPrefabInstance(new Vector3f(0, 0, 0));

            // No overrides on children - childOverrides should be null/empty
            SceneData sceneData = EditorSceneSerializer.toSceneData(scene);
            GameObjectData rootData = sceneData.getGameObjects().getFirst();
            Map<String, GameObjectData.ChildNodeOverrides> overrides = rootData.getChildOverrides();
            assertTrue(overrides == null || overrides.isEmpty(),
                    "No overrides should be recorded when children are unmodified");
        }

        @Test
        void toSceneData_childOverrides_recordsDisabledState() {
            EditorGameObject root = createPrefabInstance(new Vector3f(0, 0, 0));

            // Find and disable the guard child
            EditorGameObject guard = findChildByNodeId(root, "guard001");
            assertNotNull(guard);
            guard.setEnabled(false);

            SceneData sceneData = EditorSceneSerializer.toSceneData(scene);
            GameObjectData rootData = sceneData.getGameObjects().getFirst();

            assertNotNull(rootData.getChildOverrides());
            assertTrue(rootData.getChildOverrides().containsKey("guard001"));
            assertEquals(false, rootData.getChildOverrides().get("guard001").getActive());
        }

        @Test
        void toSceneData_childOverrides_recordsComponentOverrides() {
            EditorGameObject root = createPrefabInstance(new Vector3f(0, 0, 0));

            // Override zIndex on the guard child
            EditorGameObject guard = findChildByNodeId(root, "guard001");
            assertNotNull(guard);
            guard.setFieldValue(SR_TYPE, "zIndex", 99);

            SceneData sceneData = EditorSceneSerializer.toSceneData(scene);
            GameObjectData rootData = sceneData.getGameObjects().getFirst();

            assertNotNull(rootData.getChildOverrides());
            GameObjectData.ChildNodeOverrides guardOverrides = rootData.getChildOverrides().get("guard001");
            assertNotNull(guardOverrides);
            assertNotNull(guardOverrides.getComponentOverrides());
            assertTrue(guardOverrides.getComponentOverrides().containsKey(SR_TYPE));
            assertEquals(99, guardOverrides.getComponentOverrides().get(SR_TYPE).get("zIndex"));
        }

        @Test
        void toSceneData_mixedEntities_prefabAndScratch() {
            // Add a scratch entity
            EditorGameObject scratch = new EditorGameObject("MyScratch", new Vector3f(1, 2, 0), false);
            scratch.setOrder(0);
            scene.addEntity(scratch);

            // Add a prefab instance
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f(10, 5, 0));
            root.setName("Tower_1");
            root.setOrder(1);
            scene.addEntity(root);

            List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, guardTower);
            for (EditorGameObject child : descendants) {
                scene.addEntity(child);
            }
            scene.resolveHierarchy();

            SceneData sceneData = EditorSceneSerializer.toSceneData(scene);
            List<GameObjectData> gameObjects = sceneData.getGameObjects();

            // Should have 2 entries: 1 scratch + 1 prefab root (children collapsed)
            assertEquals(2, gameObjects.size());
        }
    }

    // ========================================================================
    // CHILD OVERRIDES APPLIED
    // ========================================================================

    @Nested
    class ChildOverridesApplied {

        @Test
        void childOverrides_nameAndActiveApplied() {
            // Build scene data manually with the new format
            GameObjectData rootData = new GameObjectData("s1", "Tower_1", PREFAB_ID, null);
            rootData.setPrefab("gameData/prefabs/guard_tower.prefab");
            rootData.setOrder(0);

            GameObjectData.ChildNodeOverrides guardOverrides = new GameObjectData.ChildNodeOverrides();
            guardOverrides.setName("Custom Guard");
            guardOverrides.setActive(false);
            rootData.setChildOverrides(Map.of("guard001", guardOverrides));

            SceneData sceneData = new SceneData("test");
            sceneData.setVersion(4);
            sceneData.addGameObject(rootData);

            // Load into editor scene
            EditorScene loadedScene = EditorSceneSerializer.fromSceneData(sceneData, "test.scene");

            // Find the guard child and verify overrides
            EditorGameObject root = null;
            for (EditorGameObject e : loadedScene.getEntities()) {
                if (!e.isPrefabChildNode() && e.isPrefabInstance()) {
                    root = e;
                    break;
                }
            }
            assertNotNull(root, "Root should be loaded");

            EditorGameObject guard = findChildByNodeId(root, "guard001");
            assertNotNull(guard, "Guard child should exist");
            assertEquals("Custom Guard", guard.getName());
            assertFalse(guard.isOwnEnabled());
        }

        @Test
        void childOverrides_componentOverridesApplied() {
            // Build scene data with component override on guard
            GameObjectData rootData = new GameObjectData("s1", "Tower_1", PREFAB_ID, null);
            rootData.setPrefab("gameData/prefabs/guard_tower.prefab");
            rootData.setOrder(0);

            GameObjectData.ChildNodeOverrides guardOverrides = new GameObjectData.ChildNodeOverrides();
            guardOverrides.setComponentOverrides(Map.of(
                    SR_TYPE, Map.of("zIndex", 99)
            ));
            rootData.setChildOverrides(Map.of("guard001", guardOverrides));

            SceneData sceneData = new SceneData("test");
            sceneData.setVersion(4);
            sceneData.addGameObject(rootData);

            EditorScene loadedScene = EditorSceneSerializer.fromSceneData(sceneData, "test.scene");

            EditorGameObject root = findRootPrefabInstance(loadedScene);
            assertNotNull(root);

            EditorGameObject guard = findChildByNodeId(root, "guard001");
            assertNotNull(guard);

            Object zIndex = guard.getFieldValue(SR_TYPE, "zIndex");
            assertEquals(99, zIndex);
        }
    }

    // ========================================================================
    // PREFAB EVOLUTION
    // ========================================================================

    @Nested
    class PrefabEvolution {

        @Test
        void prefabUpdated_newChildAppears() {
            // Create and save a scene with a prefab instance
            createPrefabInstance(new Vector3f(0, 0, 0));
            SceneData savedData = EditorSceneSerializer.toSceneData(scene);

            // Now "edit" the prefab: add a new child node
            GameObjectData newChild = new GameObjectData("banner1", "Banner",
                    new java.util.ArrayList<>(List.of(new Transform(), new SpriteRenderer())));
            newChild.setParentId("root0001");
            newChild.setOrder(2);
            guardTower.getGameObjects().add(newChild);

            // Load the saved scene again (prefab now has extra child)
            EditorScene reloadedScene = EditorSceneSerializer.fromSceneData(savedData, "test.scene");

            // The new child should appear with defaults
            EditorGameObject root = findRootPrefabInstance(reloadedScene);
            assertNotNull(root);

            EditorGameObject banner = findChildByNodeId(root, "banner1");
            assertNotNull(banner, "New prefab child 'banner1' should appear when loading");
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private EditorGameObject findChildByNodeId(EditorGameObject parent, String nodeId) {
        for (EditorGameObject child : parent.getChildren()) {
            if (nodeId.equals(child.getPrefabNodeId())) {
                return child;
            }
            // Check grandchildren
            EditorGameObject found = findChildByNodeId(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private EditorGameObject findRootPrefabInstance(EditorScene scene) {
        for (EditorGameObject e : scene.getEntities()) {
            if (e.isPrefabInstance() && !e.isPrefabChildNode()) {
                return e;
            }
        }
        return null;
    }
}
