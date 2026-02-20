package com.pocket.rpg.prefab;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.GameObjectData;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PrefabHierarchyHelper — expand, capture, collect, reconcile.
 * Uses the guard_tower fixture (5 nodes, 3 levels deep).
 */
class PrefabHierarchyHelperTest {

    private static final String PREFAB_ID = "guard_tower";

    private JsonPrefab guardTower;

    @BeforeAll
    static void initRegistry() {
        ComponentRegistry.initialize();
    }

    @BeforeEach
    void setUp() {
        PrefabRegistry.getInstance().clear();
        guardTower = JsonPrefabHierarchyTest.buildGuardTowerFixture();
        PrefabRegistry.getInstance().register(guardTower);
    }

    @AfterAll
    static void cleanup() {
        PrefabRegistry.getInstance().clear();
    }

    // ========================================================================
    // EXPAND CHILDREN
    // ========================================================================

    @Nested
    class ExpandChildren {

        @Test
        void createsAllDescendants() {
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, guardTower);

            // 4 descendants: Guard, Helmet, Spear, Flag
            assertEquals(4, descendants.size());
        }

        @Test
        void grandchildParentedToChild() {
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            PrefabHierarchyHelper.expandChildren(root, guardTower);

            // Find Helmet — should be parented to Guard, not root
            EditorGameObject guard = findChildByNodeId(root, "guard001");
            assertNotNull(guard, "Guard should exist as child of root");

            EditorGameObject helmet = findChildByNodeId(guard, "helm0001");
            assertNotNull(helmet, "Helmet should be parented to Guard");
            assertSame(guard, helmet.getParent());
        }

        @Test
        void setsCorrectPrefabNodeId() {
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, guardTower);

            List<String> nodeIds = descendants.stream()
                    .map(EditorGameObject::getPrefabNodeId)
                    .toList();

            assertTrue(nodeIds.contains("guard001"));
            assertTrue(nodeIds.contains("helm0001"));
            assertTrue(nodeIds.contains("weap0001"));
            assertTrue(nodeIds.contains("flag0001"));
        }

        @Test
        void flatPrefab_returnsEmpty() {
            JsonPrefab flat = new JsonPrefab("barrel", "Barrel");
            List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(
                    new EditorGameObject("barrel", new Vector3f()), flat);

            assertTrue(descendants.isEmpty());
        }

        @Test
        void descendantsSharePrefabId() {
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, guardTower);

            for (EditorGameObject d : descendants) {
                assertEquals(PREFAB_ID, d.getPrefabId());
                assertTrue(d.isPrefabChildNode());
            }
        }
    }

    // ========================================================================
    // COLLECT ALL
    // ========================================================================

    @Nested
    class CollectAll {

        @Test
        void parentFirstOrder() {
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            PrefabHierarchyHelper.expandChildren(root, guardTower);

            List<EditorGameObject> all = PrefabHierarchyHelper.collectAll(root);

            // 5 total: root + 4 descendants
            assertEquals(5, all.size());
            assertSame(root, all.get(0), "Root should be first");

            // Root before Guard, Guard before Helmet/Spear
            int rootIdx = indexOf(all, null); // root has no prefabNodeId
            int guardIdx = indexOf(all, "guard001");
            int helmIdx = indexOf(all, "helm0001");
            int spearIdx = indexOf(all, "weap0001");
            int flagIdx = indexOf(all, "flag0001");

            assertTrue(rootIdx < guardIdx, "Root before Guard");
            assertTrue(guardIdx < helmIdx, "Guard before Helmet");
            assertTrue(guardIdx < spearIdx, "Guard before Spear");
            assertTrue(rootIdx < flagIdx, "Root before Flag");
        }
    }

    // ========================================================================
    // CAPTURE HIERARCHY
    // ========================================================================

    @Nested
    class CaptureHierarchy {

        @Test
        void roundTrips() {
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            PrefabHierarchyHelper.expandChildren(root, guardTower);

            List<GameObjectData> captured = PrefabHierarchyHelper.captureHierarchy(root);

            // Should have 5 nodes
            assertEquals(5, captured.size());

            // Root has null parentId
            assertNull(captured.get(0).getParentId());

            // All children reference existing parent IDs
            for (int i = 1; i < captured.size(); i++) {
                String parentId = captured.get(i).getParentId();
                assertNotNull(parentId, "Child should have parentId");
                // Parent should exist earlier in the list
                boolean parentExists = captured.stream()
                        .limit(i)
                        .anyMatch(n -> n.getId().equals(parentId));
                assertTrue(parentExists, "Parent should appear before child in list");
            }
        }
    }

    // ========================================================================
    // RECONCILE INSTANCE
    // ========================================================================

    @Nested
    class ReconcileInstance {

        @Test
        void missingGrandchild_autoCreated() {
            // Create full hierarchy, then remove Helmet
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, guardTower);

            EditorScene scene = new EditorScene();
            scene.addEntity(root);
            for (EditorGameObject d : descendants) {
                scene.addEntity(d);
            }
            scene.resolveHierarchy();

            // Remove Helmet from scene
            EditorGameObject helmet = findDescendantByNodeId(root, "helm0001");
            assertNotNull(helmet);
            scene.removeEntity(helmet);
            scene.resolveHierarchy();

            // Reconcile should re-create Helmet
            int countBefore = scene.getEntities().size();
            PrefabHierarchyHelper.reconcileInstance(root, guardTower, scene);

            assertEquals(countBefore + 1, scene.getEntities().size());

            // New Helmet should be parented to Guard
            scene.resolveHierarchy();
            EditorGameObject guard = findDescendantByNodeId(root, "guard001");
            assertNotNull(guard);
            EditorGameObject newHelmet = findChildByNodeId(guard, "helm0001");
            assertNotNull(newHelmet, "Helmet should be re-created under Guard");
        }

        @Test
        void missingChildWithGrandchildren_autoCreatesAll() {
            // Create full hierarchy, then remove Guard + Helmet + Spear
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            List<EditorGameObject> descendants = PrefabHierarchyHelper.expandChildren(root, guardTower);

            EditorScene scene = new EditorScene();
            scene.addEntity(root);
            for (EditorGameObject d : descendants) {
                scene.addEntity(d);
            }
            scene.resolveHierarchy();

            // Remove Guard and its children
            EditorGameObject guard = findDescendantByNodeId(root, "guard001");
            EditorGameObject helmet = findDescendantByNodeId(root, "helm0001");
            EditorGameObject spear = findDescendantByNodeId(root, "weap0001");
            scene.removeEntity(helmet);
            scene.removeEntity(spear);
            scene.removeEntity(guard);
            scene.resolveHierarchy();

            // Only root + Flag remain (2 entities)
            assertEquals(2, scene.getEntities().size());

            // Reconcile should re-create Guard, Helmet, Spear
            PrefabHierarchyHelper.reconcileInstance(root, guardTower, scene);
            scene.resolveHierarchy();

            assertEquals(5, scene.getEntities().size());
        }

        @Test
        void orphanedGrandchild_flaggedAsBroken() {
            // Create hierarchy with an extra child that doesn't exist in prefab
            EditorGameObject root = new EditorGameObject(PREFAB_ID, new Vector3f());
            PrefabHierarchyHelper.expandChildren(root, guardTower);

            EditorScene scene = new EditorScene();
            List<EditorGameObject> all = PrefabHierarchyHelper.collectAll(root);
            for (EditorGameObject e : all) {
                scene.addEntity(e);
            }
            scene.resolveHierarchy();

            // Add an orphan with a prefabNodeId that doesn't exist in the prefab
            EditorGameObject orphan = new EditorGameObject(PREFAB_ID, "removed_node", new Vector3f());
            orphan.setParent(root);
            scene.addEntity(orphan);
            scene.resolveHierarchy();

            // Reconcile should log warning but not crash
            assertDoesNotThrow(() ->
                    PrefabHierarchyHelper.reconcileInstance(root, guardTower, scene));
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static EditorGameObject findChildByNodeId(EditorGameObject parent, String nodeId) {
        for (EditorGameObject child : parent.getChildren()) {
            if (nodeId.equals(child.getPrefabNodeId())) {
                return child;
            }
        }
        return null;
    }

    private static EditorGameObject findDescendantByNodeId(EditorGameObject root, String nodeId) {
        for (EditorGameObject child : root.getChildren()) {
            if (nodeId.equals(child.getPrefabNodeId())) {
                return child;
            }
            EditorGameObject found = findDescendantByNodeId(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private static int indexOf(List<EditorGameObject> list, String prefabNodeId) {
        for (int i = 0; i < list.size(); i++) {
            String nodeId = list.get(i).getPrefabNodeId();
            if (prefabNodeId == null ? nodeId == null : prefabNodeId.equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }
}
