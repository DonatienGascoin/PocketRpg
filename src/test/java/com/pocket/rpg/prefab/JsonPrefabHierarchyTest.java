package com.pocket.rpg.prefab;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.GameObjectData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonPrefab hierarchy support (gameObjects field, node lookup, etc.).
 * Uses the guard_tower fixture from the design plan (5 nodes, 3 levels deep).
 */
public class JsonPrefabHierarchyTest {

    private JsonPrefab guardTower;

    @BeforeAll
    static void initRegistry() {
        ComponentRegistry.initialize();
    }

    @BeforeEach
    void setUp() {
        guardTower = buildGuardTowerFixture();
    }

    // ========================================================================
    // FIND NODE
    // ========================================================================

    @Nested
    class FindNode {

        @Test
        void returnsChild() {
            GameObjectData guard = guardTower.findNode("guard001");
            assertNotNull(guard);
            assertEquals("Guard", guard.getName());
        }

        @Test
        void returnsGrandchild() {
            GameObjectData helmet = guardTower.findNode("helm0001");
            assertNotNull(helmet);
            assertEquals("Helmet", helmet.getName());
            assertEquals("guard001", helmet.getParentId());
        }

        @Test
        void returnsOtherGrandchild() {
            GameObjectData spear = guardTower.findNode("weap0001");
            assertNotNull(spear);
            assertEquals("Spear", spear.getName());
        }

        @Test
        void invalidId_returnsNull() {
            assertNull(guardTower.findNode("doesNotExist"));
        }

        @Test
        void nullId_returnsNull() {
            assertNull(guardTower.findNode(null));
        }
    }

    // ========================================================================
    // GET ROOT NODE
    // ========================================================================

    @Nested
    class GetRootNode {

        @Test
        void returnsNullParentId() {
            GameObjectData root = guardTower.getRootNode();
            assertNotNull(root);
            assertEquals("root0001", root.getId());
            assertNull(root.getParentId());
        }

        @Test
        void ignoresChildrenAndGrandchildren() {
            GameObjectData root = guardTower.getRootNode();
            assertNotEquals("guard001", root.getId());
            assertNotEquals("helm0001", root.getId());
        }

        @Test
        void emptyList_returnsNull() {
            JsonPrefab empty = new JsonPrefab();
            empty.setGameObjects(new ArrayList<>());
            assertNull(empty.getRootNode());
        }

        @Test
        void nullGameObjects_returnsNull() {
            JsonPrefab empty = new JsonPrefab();
            assertNull(empty.getRootNode());
        }
    }

    // ========================================================================
    // HAS CHILDREN
    // ========================================================================

    @Nested
    class HasChildren {

        @Test
        void withHierarchy_returnsTrue() {
            assertTrue(guardTower.hasChildren());
        }

        @Test
        void singleRoot_returnsFalse() {
            JsonPrefab singleRoot = new JsonPrefab("barrel", "Barrel");
            assertFalse(singleRoot.hasChildren());
        }

        @Test
        void nullGameObjects_returnsFalse() {
            JsonPrefab empty = new JsonPrefab();
            assertFalse(empty.hasChildren());
        }
    }

    // ========================================================================
    // GET COMPONENTS (root delegation)
    // ========================================================================

    @Nested
    class GetComponents {

        @Test
        void delegatesToRoot() {
            List<Component> components = guardTower.getComponents();
            assertNotNull(components);
            // Root has Transform + SpriteRenderer
            assertEquals(2, components.size());
        }

        @Test
        void doesNotReturnChildComponents() {
            List<Component> rootComponents = guardTower.getComponents();
            // Root's SpriteRenderer has zIndex 5
            for (Component comp : rootComponents) {
                if (comp instanceof SpriteRenderer sr) {
                    assertEquals(5, sr.getZIndex());
                    return;
                }
            }
            fail("Root should have a SpriteRenderer");
        }

        @Test
        void emptyPrefab_returnsEmptyList() {
            JsonPrefab empty = new JsonPrefab();
            assertTrue(empty.getComponents().isEmpty());
        }
    }

    // ========================================================================
    // SET COMPONENTS (backward compat)
    // ========================================================================

    @Nested
    class SetComponents {

        @Test
        void updatesRootNodeComponents() {
            List<Component> newComps = new ArrayList<>();
            newComps.add(new Transform());
            guardTower.setComponents(newComps);

            assertEquals(1, guardTower.getComponents().size());
            assertInstanceOf(Transform.class, guardTower.getComponents().getFirst());
        }

        @Test
        void preservesChildNodes() {
            List<Component> newComps = new ArrayList<>();
            newComps.add(new Transform());
            guardTower.setComponents(newComps);

            // Children should still be present
            assertTrue(guardTower.hasChildren());
            assertNotNull(guardTower.findNode("guard001"));
            assertNotNull(guardTower.findNode("helm0001"));
        }
    }

    // ========================================================================
    // ADD COMPONENT (backward compat)
    // ========================================================================

    @Nested
    class AddComponent {

        @Test
        void addsToRootNode() {
            int before = guardTower.getComponents().size();
            guardTower.addComponent(new SpriteRenderer());
            assertEquals(before + 1, guardTower.getComponents().size());
        }
    }

    // ========================================================================
    // NODE ID INDEX
    // ========================================================================

    @Nested
    class NodeIdIndex {

        @Test
        void builtLazilyOnFirstFindNode() {
            // nodeIdIndex starts null
            assertNull(guardTower.getNodeIdIndex());

            // First findNode triggers build
            guardTower.findNode("guard001");
            assertNotNull(guardTower.getNodeIdIndex());
        }

        @Test
        void setGameObjects_clearsIndex() {
            // Build the index
            guardTower.findNode("guard001");
            assertNotNull(guardTower.getNodeIdIndex());

            // setGameObjects should clear it
            guardTower.setGameObjects(guardTower.getGameObjects());
            assertNull(guardTower.getNodeIdIndex());
        }
    }

    // ========================================================================
    // PREFAB INTERFACE DEFAULTS
    // ========================================================================

    @Nested
    class PrefabInterfaceDefaults {

        @Test
        void getChildFieldDefault_returnsChildValue() {
            // Guard's SpriteRenderer zIndex default is 10
            Object zIndex = guardTower.getChildFieldDefault("guard001",
                    SpriteRenderer.class.getName(), "zIndex");
            assertNotNull(zIndex);
            assertEquals(10, zIndex);
        }

        @Test
        void getChildFieldDefault_returnsGrandchildValue() {
            // Helmet's SpriteRenderer zIndex default is 12
            Object zIndex = guardTower.getChildFieldDefault("helm0001",
                    SpriteRenderer.class.getName(), "zIndex");
            assertNotNull(zIndex);
            assertEquals(12, zIndex);
        }

        @Test
        void getChildFieldDefault_unknownNode_returnsNull() {
            assertNull(guardTower.getChildFieldDefault("missing",
                    SpriteRenderer.class.getName(), "zIndex"));
        }

        @Test
        void getNodeComponentsCopy_returnsDeepClone() {
            List<Component> copy = guardTower.getNodeComponentsCopy("guard001");
            assertFalse(copy.isEmpty());

            // Should be different instances
            GameObjectData guardNode = guardTower.findNode("guard001");
            for (int i = 0; i < copy.size(); i++) {
                assertNotSame(guardNode.getComponents().get(i), copy.get(i));
            }
        }

        @Test
        void getNodeComponentsCopy_unknownNode_returnsEmpty() {
            assertTrue(guardTower.getNodeComponentsCopy("missing").isEmpty());
        }
    }

    // ========================================================================
    // FIXTURE BUILDER
    // ========================================================================

    /**
     * Builds the guard_tower fixture from the design plan:
     * <pre>
     * Guard Tower (root0001)           zIndex=5
     * ├── Guard (guard001)             zIndex=10
     * │   ├── Helmet (helm0001)        zIndex=12
     * │   └── Spear (weap0001)         zIndex=11
     * └── Flag (flag0001)              zIndex=15
     * </pre>
     */
    public static JsonPrefab buildGuardTowerFixture() {
        List<GameObjectData> nodes = new ArrayList<>();

        // Root: Guard Tower
        nodes.add(buildNode("root0001", "Guard Tower", null, 0, 5,
                0f, 0f, 0f, 1f, 1f, 1f));

        // Child: Guard
        nodes.add(buildNode("guard001", "Guard", "root0001", 0, 10,
                0f, 1f, 0f, 1f, 1f, 1f));

        // Grandchild: Helmet
        nodes.add(buildNode("helm0001", "Helmet", "guard001", 0, 12,
                0f, 0.3f, 0f, 1f, 1f, 1f));

        // Grandchild: Spear
        nodes.add(buildNode("weap0001", "Spear", "guard001", 1, 11,
                0.3f, 0f, 0f, 1f, 1f, 1f));

        // Child: Flag
        nodes.add(buildNode("flag0001", "Flag", "root0001", 1, 15,
                0.5f, 2f, 0f, 1f, 1f, 1f));

        JsonPrefab prefab = new JsonPrefab();
        prefab.setId("guard_tower");
        prefab.setDisplayName("Guard Tower");
        prefab.setCategory("Structures");
        prefab.setGameObjects(nodes);

        return prefab;
    }

    private static GameObjectData buildNode(String id, String name, String parentId,
                                            int order, int zIndex,
                                            float px, float py, float pz,
                                            float sx, float sy, float sz) {
        List<Component> components = new ArrayList<>();

        Transform transform = new Transform();
        transform.setPosition(px, py, pz);
        transform.setLocalScale(new org.joml.Vector3f(sx, sy, sz));
        components.add(transform);

        SpriteRenderer sr = new SpriteRenderer();
        sr.setZIndex(zIndex);
        components.add(sr);

        GameObjectData node = new GameObjectData(id, name, components);
        node.setParentId(parentId);
        node.setOrder(order);
        return node;
    }
}
