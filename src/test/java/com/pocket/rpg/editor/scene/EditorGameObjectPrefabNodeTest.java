package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.prefab.JsonPrefabHierarchyTest;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.GameObjectData;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EditorGameObject prefabNodeId support.
 * Uses the guard_tower fixture (5 nodes, 3 levels deep).
 * <p>
 * Guard Tower (root0001)           zIndex=5
 * ├── Guard (guard001)             zIndex=10
 * │   ├── Helmet (helm0001)        zIndex=12
 * │   └── Spear (weap0001)         zIndex=11
 * └── Flag (flag0001)              zIndex=15
 */
class EditorGameObjectPrefabNodeTest {

    private static final String PREFAB_ID = "guard_tower";
    private static final String SR_TYPE = SpriteRenderer.class.getName();
    private static final String TRANSFORM_TYPE = Transform.class.getName();

    @BeforeAll
    static void initRegistry() {
        ComponentRegistry.initialize();
    }

    @BeforeEach
    void setUp() {
        // Clear and re-register the fixture each test
        PrefabRegistry.getInstance().clear();
        JsonPrefab guardTower = JsonPrefabHierarchyTest.buildGuardTowerFixture();
        PrefabRegistry.getInstance().register(guardTower);
    }

    @AfterAll
    static void cleanup() {
        PrefabRegistry.getInstance().clear();
    }

    // ========================================================================
    // GET MERGED COMPONENTS
    // ========================================================================

    @Nested
    class GetMergedComponents {

        @Test
        void childNode_resolvesChildComponents() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            List<Component> merged = entity.getComponents();
            assertFalse(merged.isEmpty());

            SpriteRenderer sr = findComponent(merged, SpriteRenderer.class);
            assertNotNull(sr, "Guard node should have SpriteRenderer");
            assertEquals(10, sr.getZIndex(), "Guard's zIndex default is 10");
        }

        @Test
        void grandchildNode_resolvesGrandchildComponents() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f());

            List<Component> merged = entity.getComponents();
            assertFalse(merged.isEmpty());

            SpriteRenderer sr = findComponent(merged, SpriteRenderer.class);
            assertNotNull(sr, "Helmet node should have SpriteRenderer");
            assertEquals(12, sr.getZIndex(), "Helmet's zIndex default is 12");
        }

        @Test
        void rootNode_resolvesRootComponents() {
            // No prefabNodeId → resolves root
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, new Vector3f());

            List<Component> merged = entity.getComponents();
            assertFalse(merged.isEmpty());

            SpriteRenderer sr = findComponent(merged, SpriteRenderer.class);
            assertNotNull(sr, "Root node should have SpriteRenderer");
            assertEquals(5, sr.getZIndex(), "Root's zIndex default is 5");
        }

        @Test
        void staleNodeId_returnsEmpty() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "removed", new Vector3f());

            List<Component> merged = entity.getComponents();
            assertTrue(merged.isEmpty(), "Stale node ID should return empty list");
        }
    }

    // ========================================================================
    // GET FIELD DEFAULT
    // ========================================================================

    @Nested
    class GetFieldDefault {

        @Test
        void childNode_returnsChildDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            Object zIndex = entity.getFieldDefault(SR_TYPE, "zIndex");
            assertNotNull(zIndex);
            assertEquals(10, zIndex, "Guard's SpriteRenderer.zIndex default is 10");
        }

        @Test
        void grandchildNode_returnsGrandchildDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f());

            Object zIndex = entity.getFieldDefault(SR_TYPE, "zIndex");
            assertNotNull(zIndex);
            assertEquals(12, zIndex, "Helmet's SpriteRenderer.zIndex default is 12");
        }

        @Test
        void nullNodeId_returnsRootDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, new Vector3f());

            Object zIndex = entity.getFieldDefault(SR_TYPE, "zIndex");
            assertNotNull(zIndex);
            assertEquals(5, zIndex, "Root's SpriteRenderer.zIndex default is 5");
        }
    }

    // ========================================================================
    // IS FIELD OVERRIDDEN
    // ========================================================================

    @Nested
    class IsFieldOverridden {

        @Test
        void childNode_comparesAgainstChildDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            // Override zIndex to 15 (differs from default 10) → overridden
            entity.setFieldValue(SR_TYPE, "zIndex", 15);
            assertTrue(entity.isFieldOverridden(SR_TYPE, "zIndex"));

            // Set to 10 (matches child default) → not overridden
            entity.setFieldValue(SR_TYPE, "zIndex", 10);
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));
        }

        @Test
        void grandchildNode_comparesAgainstGrandchildDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f());

            // Set to 12 (matches grandchild default) → not overridden
            entity.setFieldValue(SR_TYPE, "zIndex", 12);
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));

            // Override to 17 → overridden
            entity.setFieldValue(SR_TYPE, "zIndex", 17);
            assertTrue(entity.isFieldOverridden(SR_TYPE, "zIndex"));
        }
    }

    // ========================================================================
    // RESET FIELD TO DEFAULT
    // ========================================================================

    @Nested
    class ResetFieldToDefault {

        @Test
        void childNode_revertsToChildDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());
            entity.setFieldValue(SR_TYPE, "zIndex", 99);

            entity.resetFieldToDefault(SR_TYPE, "zIndex");

            // After reset, getFieldValue should return the child default (10), not root (5)
            Object value = entity.getFieldValue(SR_TYPE, "zIndex");
            assertEquals(10, value, "Reset should revert to Guard's default (10)");
        }

        @Test
        void grandchildNode_revertsToGrandchildDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f());
            entity.setFieldValue(SR_TYPE, "zIndex", 99);

            entity.resetFieldToDefault(SR_TYPE, "zIndex");

            Object value = entity.getFieldValue(SR_TYPE, "zIndex");
            assertEquals(12, value, "Reset should revert to Helmet's default (12)");
        }
    }

    // ========================================================================
    // SERIALIZATION ROUND-TRIP
    // ========================================================================

    @Nested
    class SerializationRoundTrip {

        @Test
        void toData_fromData_preservesPrefabNodeId_child() {
            EditorGameObject original = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f(5, 10, 0));
            original.setFieldValue(SR_TYPE, "zIndex", 20);

            GameObjectData data = original.toData();
            assertEquals("guard001", data.getPrefabNodeId());
            assertEquals(PREFAB_ID, data.getPrefabId());

            EditorGameObject restored = EditorGameObject.fromData(data);
            assertEquals(PREFAB_ID, restored.getPrefabId());
            assertEquals("guard001", restored.getPrefabNodeId());
            assertTrue(restored.isPrefabChildNode());
        }

        @Test
        void toData_fromData_preservesPrefabNodeId_grandchild() {
            EditorGameObject original = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f(1, 2, 0));

            GameObjectData data = original.toData();
            assertEquals("helm0001", data.getPrefabNodeId());

            EditorGameObject restored = EditorGameObject.fromData(data);
            assertEquals("helm0001", restored.getPrefabNodeId());
            assertTrue(restored.isPrefabChildNode());

            // Field default should still resolve correctly after round-trip
            Object zIndex = restored.getFieldDefault(SR_TYPE, "zIndex");
            assertEquals(12, zIndex);
        }

        @Test
        void toData_fromData_nullNodeId_preservedAsNull() {
            EditorGameObject original = new EditorGameObject(PREFAB_ID, new Vector3f());

            GameObjectData data = original.toData();
            assertNull(data.getPrefabNodeId());

            EditorGameObject restored = EditorGameObject.fromData(data);
            assertNull(restored.getPrefabNodeId());
            assertFalse(restored.isPrefabChildNode());
        }
    }

    // ========================================================================
    // IS PREFAB CHILD NODE
    // ========================================================================

    @Nested
    class IsPrefabChildNode {

        @Test
        void withNodeId_returnsTrue() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());
            assertTrue(entity.isPrefabChildNode());
        }

        @Test
        void withoutNodeId_returnsFalse() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, new Vector3f());
            assertFalse(entity.isPrefabChildNode());
        }

        @Test
        void scratchEntity_returnsFalse() {
            EditorGameObject entity = new EditorGameObject("Scratch", new Vector3f(), false);
            assertFalse(entity.isPrefabChildNode());
        }
    }

    // ========================================================================
    // SET FIELD VALUE INVALIDATES CACHE
    // ========================================================================

    @Nested
    class SetFieldValueCache {

        @Test
        void setFieldValue_invalidatesComponentCache() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            // First access caches merged components
            List<Component> first = entity.getComponents();
            SpriteRenderer sr1 = findComponent(first, SpriteRenderer.class);
            assertEquals(10, sr1.getZIndex());

            // Override zIndex
            entity.setFieldValue(SR_TYPE, "zIndex", 25);

            // Components should be re-merged with new override
            List<Component> second = entity.getComponents();
            SpriteRenderer sr2 = findComponent(second, SpriteRenderer.class);
            assertEquals(25, sr2.getZIndex());
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static <T extends Component> T findComponent(List<Component> components, Class<T> type) {
        for (Component comp : components) {
            if (type.isInstance(comp)) {
                return (T) comp;
            }
        }
        return null;
    }
}
