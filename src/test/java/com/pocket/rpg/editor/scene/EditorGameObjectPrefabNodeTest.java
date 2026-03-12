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
    // COMPONENT RESOLUTION
    // ========================================================================

    @Nested
    class ComponentResolution {

        @Test
        void childNode_resolvesChildComponents() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            List<Component> components = entity.getComponents();
            assertFalse(components.isEmpty());

            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertNotNull(sr, "Guard node should have SpriteRenderer");
            assertEquals(10, sr.getZIndex(), "Guard's zIndex default is 10");
        }

        @Test
        void grandchildNode_resolvesGrandchildComponents() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f());

            List<Component> components = entity.getComponents();
            assertFalse(components.isEmpty());

            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertNotNull(sr, "Helmet node should have SpriteRenderer");
            assertEquals(12, sr.getZIndex(), "Helmet's zIndex default is 12");
        }

        @Test
        void rootNode_resolvesRootComponents() {
            // No prefabNodeId → resolves root
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, new Vector3f());

            List<Component> components = entity.getComponents();
            assertFalse(components.isEmpty());

            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertNotNull(sr, "Root node should have SpriteRenderer");
            assertEquals(5, sr.getZIndex(), "Root's zIndex default is 5");
        }

        @Test
        void staleNodeId_hasOnlyAutoCreatedTransform() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "removed", new Vector3f());

            List<Component> components = entity.getComponents();
            assertEquals(1, components.size(), "Stale node should only have auto-created Transform");
            assertInstanceOf(com.pocket.rpg.components.core.Transform.class, components.getFirst());
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
        void childNode_overrideTracking() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            // Override zIndex to 15 (differs from default 10) → overridden
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 15)));
            assertTrue(entity.isFieldOverridden(SR_TYPE, "zIndex"));

            // Clear the override → not overridden
            entity.clearFieldOverride(SR_TYPE, "zIndex");
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));
        }

        @Test
        void grandchildNode_overrideTracking() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f());

            // Not overridden initially
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));

            // Mark as overridden → true
            entity.markFieldOverridden(SR_TYPE, "zIndex");
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
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 99)));

            entity.resetFieldToDefault(SR_TYPE, "zIndex");

            // After reset, the component should have the child default (10), not root (5)
            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(10, sr.getZIndex(), "Reset should revert to Guard's default (10)");
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));
        }

        @Test
        void grandchildNode_revertsToGrandchildDefault() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "helm0001", new Vector3f());
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 99)));

            entity.resetFieldToDefault(SR_TYPE, "zIndex");

            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(12, sr.getZIndex(), "Reset should revert to Helmet's default (12)");
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));
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
            original.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 20)));

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
    // APPLY SERIALIZED OVERRIDES
    // ========================================================================

    @Nested
    class ApplySerializedOverrides {

        @Test
        void appliesValueAndMarksOverridden() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            // Apply override
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 25)));

            // Component should have the overridden value
            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(25, sr.getZIndex());

            // Field should be marked as overridden
            assertTrue(entity.isFieldOverridden(SR_TYPE, "zIndex"));
        }

        @Test
        void multipleOverridesApplied() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 25)));
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 30)));

            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(30, sr.getZIndex(), "Latest override should win");
        }

        @Test
        void copyOverrides_doesNotShareMutableState() {
            // Simulate entity duplication: copy overrides from original to copy
            EditorGameObject original = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f(10, 20, 0));
            original.markFieldOverridden(TRANSFORM_TYPE, "localPosition");

            EditorGameObject copy = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());
            copy.applySerializedOverrides(original.getComponentOverrides());

            // Modify the copy's position
            copy.setPosition(99, 99, 0);

            // Original should be unaffected
            assertEquals(10, original.getPosition().x, "Original X should be unchanged");
            assertEquals(20, original.getPosition().y, "Original Y should be unchanged");
        }

        @Test
        void getComponentOverrides_returnsIndependentCopy() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f(10, 20, 0));
            entity.markFieldOverridden(TRANSFORM_TYPE, "localPosition");

            Map<String, Map<String, Object>> overrides1 = entity.getComponentOverrides();
            Map<String, Map<String, Object>> overrides2 = entity.getComponentOverrides();

            // Mutating one returned map should not affect the other
            overrides1.get(TRANSFORM_TYPE).put("localPosition", "corrupted");
            assertNotEquals("corrupted", overrides2.get(TRANSFORM_TYPE).get("localPosition"));
        }
    }

    // ========================================================================
    // REFRESH FROM TEMPLATE
    // ========================================================================

    @Nested
    class RefreshFromTemplate {

        @Test
        void preservesOverriddenFields() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 99)));

            entity.refreshFromTemplate();

            // Overridden field should still have the override value
            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(99, sr.getZIndex());
            assertTrue(entity.isFieldOverridden(SR_TYPE, "zIndex"));
        }

        @Test
        void preservesTransformOverrides() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            // Override position
            entity.setPosition(50, 60, 0);
            entity.markFieldOverridden(TRANSFORM_TYPE, "localPosition");

            entity.refreshFromTemplate();

            // Position override should be preserved
            assertEquals(50, entity.getPosition().x);
            assertEquals(60, entity.getPosition().y);
            assertTrue(entity.isFieldOverridden(TRANSFORM_TYPE, "localPosition"));
        }

        @Test
        void picksUpTemplateChangesOnNonOverriddenFields() {
            EditorGameObject entity = new EditorGameObject(PREFAB_ID, "guard001", new Vector3f());

            // The entity should have zIndex=10 (guard default)
            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(10, sr.getZIndex());

            // Now "edit" the prefab template's guard node default
            // (Simulated by modifying the fixture directly)
            JsonPrefab guardTower = (JsonPrefab) PrefabRegistry.getInstance().getPrefab(PREFAB_ID);
            GameObjectData guardNode = guardTower.findNode("guard001");
            for (Component comp : guardNode.getComponents()) {
                if (comp instanceof SpriteRenderer templateSr) {
                    templateSr.setZIndex(42);
                }
            }

            entity.refreshFromTemplate();

            // Non-overridden field should pick up the new template default
            SpriteRenderer refreshedSr = entity.getComponent(SpriteRenderer.class);
            assertEquals(42, refreshedSr.getZIndex());
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
