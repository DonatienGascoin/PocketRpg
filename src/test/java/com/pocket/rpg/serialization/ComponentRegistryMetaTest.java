package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComponentRegistryMetaTest {

    @BeforeAll
    static void registerComponents() {
        ComponentRegistry.initialize();
    }

    // ========================================================================
    // COMPONENT REFERENCES
    // ========================================================================

    @Nested
    class ComponentReferences {

        @Test
        void mixedComponent_hasBothHierarchyAndKeyRefs() {
            ComponentMeta meta = ComponentRegistry.getByClassName(MixedRefComponent.class.getName());
            assertNotNull(meta);

            assertEquals(2, meta.componentReferences().size());
            assertEquals(1, meta.hierarchyReferences().size());
            assertEquals(1, meta.keyReferences().size());
        }

        @Test
        void hierarchyRef_hasCorrectMeta() {
            ComponentMeta meta = ComponentRegistry.getByClassName(MixedRefComponent.class.getName());
            ComponentReferenceMeta ref = meta.hierarchyReferences().get(0);

            assertEquals("renderer", ref.fieldName());
            assertEquals(SpriteRenderer.class, ref.componentType());
            assertEquals(Source.SELF, ref.source());
            assertTrue(ref.isHierarchySource());
            assertFalse(ref.isKeySource());
        }

        @Test
        void keyRef_hasCorrectMeta() {
            ComponentMeta meta = ComponentRegistry.getByClassName(MixedRefComponent.class.getName());
            ComponentReferenceMeta ref = meta.keyReferences().get(0);

            assertEquals("target", ref.fieldName());
            assertEquals(Source.KEY, ref.source());
            assertTrue(ref.isKeySource());
            assertFalse(ref.isHierarchySource());
        }

        @Test
        void noRefComponent_hasEmptyRefs() {
            ComponentMeta meta = ComponentRegistry.getByClassName(NoRefComponent.class.getName());
            assertTrue(meta.componentReferences().isEmpty());
        }
    }

    // ========================================================================
    // FIELD LIST (serialization)
    // ========================================================================

    @Nested
    class FieldList {

        @Test
        void keyRef_addedToFieldsAsString() {
            ComponentMeta meta = ComponentRegistry.getByClassName(KeyOnlyComponent.class.getName());

            FieldMeta fieldMeta = findField(meta, "target");
            assertNotNull(fieldMeta, "KEY source field should appear in fields list");
            assertEquals(String.class, fieldMeta.type());
        }

        @Test
        void hierarchyRef_excludedFromFields() {
            ComponentMeta meta = ComponentRegistry.getByClassName(HierarchyOnlyComponent.class.getName());

            FieldMeta fieldMeta = findField(meta, "renderer");
            assertNull(fieldMeta, "Hierarchy source field should NOT appear in fields list");
        }

        @Test
        void listKeyRef_addedToFieldsAsList() {
            ComponentMeta meta = ComponentRegistry.getByClassName(ListKeyComponent.class.getName());

            FieldMeta fieldMeta = findField(meta, "targets");
            assertNotNull(fieldMeta, "List KEY source field should appear in fields list");
            assertEquals(List.class, fieldMeta.type());
            assertEquals(String.class, fieldMeta.elementType());
        }

        @Test
        void regularField_stillPresent() {
            ComponentMeta meta = ComponentRegistry.getByClassName(NoRefComponent.class.getName());

            FieldMeta fieldMeta = findField(meta, "speed");
            assertNotNull(fieldMeta);
            assertEquals(float.class, fieldMeta.type());
        }

        @Test
        void mixedComponent_hasKeyRefFieldAndRegularFields() {
            ComponentMeta meta = ComponentRegistry.getByClassName(MixedRefComponent.class.getName());

            // KEY ref should be in fields list
            assertNotNull(findField(meta, "target"));
            // SELF ref should NOT be in fields list
            assertNull(findField(meta, "renderer"));
            // Regular field should be in fields list
            assertNotNull(findField(meta, "speed"));
        }
    }

    // ========================================================================
    // COMPONENT REFERENCE META
    // ========================================================================

    @Nested
    class MetaHelpers {

        @Test
        void getDisplayName_convertsFieldName() {
            ComponentMeta meta = ComponentRegistry.getByClassName(MixedRefComponent.class.getName());
            ComponentReferenceMeta ref = meta.hierarchyReferences().get(0);

            assertEquals("Renderer", ref.getDisplayName());
        }

        @Test
        void getEditorDescription_singleSelf() {
            ComponentMeta meta = ComponentRegistry.getByClassName(MixedRefComponent.class.getName());
            ComponentReferenceMeta ref = meta.hierarchyReferences().get(0);

            assertEquals("SpriteRenderer (self)", ref.getEditorDescription());
        }

        @Test
        void getEditorDescription_key() {
            ComponentMeta meta = ComponentRegistry.getByClassName(MixedRefComponent.class.getName());
            ComponentReferenceMeta ref = meta.keyReferences().get(0);

            assertTrue(ref.getEditorDescription().contains("(key)"));
        }

        @Test
        void getEditorDescription_list_showsBrackets() {
            ComponentMeta meta = ComponentRegistry.getByClassName(ListKeyComponent.class.getName());
            ComponentReferenceMeta ref = meta.keyReferences().get(0);

            assertTrue(ref.getEditorDescription().contains("[]"));
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static FieldMeta findField(ComponentMeta meta, String name) {
        return meta.fields().stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    // ========================================================================
    // TEST COMPONENTS
    // ========================================================================

    public static class MixedRefComponent extends Component {
        @ComponentReference(source = Source.SELF)
        SpriteRenderer renderer;

        @ComponentReference(source = Source.KEY)
        Component target;

        float speed = 5f;
    }

    public static class KeyOnlyComponent extends Component {
        @ComponentReference(source = Source.KEY)
        Component target;
    }

    public static class HierarchyOnlyComponent extends Component {
        @ComponentReference(source = Source.SELF)
        SpriteRenderer renderer;
    }

    public static class NoRefComponent extends Component {
        float speed = 1f;
    }

    public static class ListKeyComponent extends Component {
        @ComponentReference(source = Source.KEY)
        List<Component> targets;
    }
}
