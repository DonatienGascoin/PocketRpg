package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.ui.ComponentKeyRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComponentReferenceResolverTest {

    @BeforeEach
    void setUp() {
        ComponentKeyRegistry.clear();
        ComponentReferenceResolver.clearPendingKeys();
    }

    @AfterEach
    void tearDown() {
        ComponentKeyRegistry.clear();
        ComponentReferenceResolver.clearPendingKeys();
    }

    // ========================================================================
    // PENDING KEY STORAGE
    // ========================================================================

    @Nested
    class PendingKeyStorage {

        @Test
        void storePendingKey_andRetrieve() {
            var comp = new StubComponent();
            ComponentReferenceResolver.storePendingKey(comp, "myField", "someKey");

            assertEquals("someKey", ComponentReferenceResolver.getPendingKey(comp, "myField"));
        }

        @Test
        void getPendingKey_returnsEmptyForUnknown() {
            assertEquals("", ComponentReferenceResolver.getPendingKey(new StubComponent(), "missing"));
        }

        @Test
        void storePendingKey_nullClears() {
            var comp = new StubComponent();
            ComponentReferenceResolver.storePendingKey(comp, "field", "key");
            ComponentReferenceResolver.storePendingKey(comp, "field", null);

            assertEquals("", ComponentReferenceResolver.getPendingKey(comp, "field"));
        }

        @Test
        void storePendingKey_emptyStringClears() {
            var comp = new StubComponent();
            ComponentReferenceResolver.storePendingKey(comp, "field", "key");
            ComponentReferenceResolver.storePendingKey(comp, "field", "");

            assertEquals("", ComponentReferenceResolver.getPendingKey(comp, "field"));
        }

        @Test
        void clearPendingKeys_removesAll() {
            var comp = new StubComponent();
            ComponentReferenceResolver.storePendingKey(comp, "a", "keyA");
            ComponentReferenceResolver.storePendingKeyList(comp, "b", List.of("k1", "k2"));

            ComponentReferenceResolver.clearPendingKeys();

            assertEquals("", ComponentReferenceResolver.getPendingKey(comp, "a"));
            assertTrue(ComponentReferenceResolver.getPendingKeyList(comp, "b").isEmpty());
        }
    }

    // ========================================================================
    // PENDING KEY LIST STORAGE
    // ========================================================================

    @Nested
    class PendingKeyListStorage {

        @Test
        void storePendingKeyList_andRetrieve() {
            var comp = new StubComponent();
            ComponentReferenceResolver.storePendingKeyList(comp, "items", List.of("a", "b"));

            assertEquals(List.of("a", "b"), ComponentReferenceResolver.getPendingKeyList(comp, "items"));
        }

        @Test
        void getPendingKeyList_returnsEmptyForUnknown() {
            assertTrue(ComponentReferenceResolver.getPendingKeyList(new StubComponent(), "missing").isEmpty());
        }

        @Test
        void storePendingKeyList_nullClears() {
            var comp = new StubComponent();
            ComponentReferenceResolver.storePendingKeyList(comp, "field", List.of("a"));
            ComponentReferenceResolver.storePendingKeyList(comp, "field", null);

            assertTrue(ComponentReferenceResolver.getPendingKeyList(comp, "field").isEmpty());
        }

        @Test
        void storePendingKeyList_emptyListClears() {
            var comp = new StubComponent();
            ComponentReferenceResolver.storePendingKeyList(comp, "field", List.of("a"));
            ComponentReferenceResolver.storePendingKeyList(comp, "field", List.of());

            assertTrue(ComponentReferenceResolver.getPendingKeyList(comp, "field").isEmpty());
        }

        @Test
        void storePendingKeyList_isolatedFromInput() {
            var comp = new StubComponent();
            var original = new java.util.ArrayList<>(List.of("a", "b"));
            ComponentReferenceResolver.storePendingKeyList(comp, "field", original);

            original.add("c");
            assertEquals(2, ComponentReferenceResolver.getPendingKeyList(comp, "field").size());
        }
    }

    // ========================================================================
    // HIERARCHY RESOLUTION — SELF
    // ========================================================================

    @Nested
    class SelfResolution {

        @Test
        void resolveAll_selfRef_injectsSiblingComponent() {
            // Register SelfRefComponent so the resolver can find its meta
            ComponentRegistry.initialize();

            var go = new GameObject("test");
            var selfRef = new SelfRefComponent();
            var sprite = new SpriteRenderer();
            go.addComponent(selfRef);
            go.addComponent(sprite);

            ComponentReferenceResolver.resolveAll(go);

            assertSame(sprite, selfRef.renderer);
        }

        @Test
        void resolveAll_selfRef_nullWhenMissing() {
            ComponentRegistry.initialize();

            var go = new GameObject("test");
            var selfRef = new SelfRefComponent();
            go.addComponent(selfRef);

            ComponentReferenceResolver.resolveAll(go);

            assertNull(selfRef.renderer);
        }
    }

    // ========================================================================
    // HIERARCHY RESOLUTION — PARENT
    // ========================================================================

    @Nested
    class ParentResolution {

        @Test
        void resolveAll_parentRef_injectsParentComponent() {
            ComponentRegistry.initialize();

            var parent = new GameObject("parent");
            var child = new GameObject("child");
            var sprite = new SpriteRenderer();
            parent.addComponent(sprite);
            parent.addChild(child);

            var ref = new ParentRefComponent();
            child.addComponent(ref);

            ComponentReferenceResolver.resolveAll(child);

            assertSame(sprite, ref.parentRenderer);
        }

        @Test
        void resolveAll_parentRef_nullWhenNoParent() {
            ComponentRegistry.initialize();

            var go = new GameObject("orphan");
            var ref = new ParentRefComponent();
            go.addComponent(ref);

            ComponentReferenceResolver.resolveAll(go);

            assertNull(ref.parentRenderer);
        }
    }

    // ========================================================================
    // HIERARCHY RESOLUTION — CHILDREN
    // ========================================================================

    @Nested
    class ChildrenResolution {

        @Test
        void resolveAll_childrenRef_injectsFirstChildComponent() {
            ComponentRegistry.initialize();

            var parent = new GameObject("parent");
            var child1 = new GameObject("child1");
            var child2 = new GameObject("child2");
            var sprite1 = new SpriteRenderer();
            var sprite2 = new SpriteRenderer();
            child1.addComponent(sprite1);
            child2.addComponent(sprite2);
            parent.addChild(child1);
            parent.addChild(child2);

            var ref = new ChildrenRefComponent();
            parent.addComponent(ref);

            ComponentReferenceResolver.resolveAll(parent);

            assertSame(sprite1, ref.childRenderer);
        }

        @Test
        void resolveAll_childrenListRef_injectsAll() {
            ComponentRegistry.initialize();

            var parent = new GameObject("parent");
            var child1 = new GameObject("child1");
            var child2 = new GameObject("child2");
            child1.addComponent(new SpriteRenderer());
            child2.addComponent(new SpriteRenderer());
            parent.addChild(child1);
            parent.addChild(child2);

            var ref = new ChildrenListRefComponent();
            parent.addComponent(ref);

            ComponentReferenceResolver.resolveAll(parent);

            assertNotNull(ref.childRenderers);
            assertEquals(2, ref.childRenderers.size());
        }
    }

    // ========================================================================
    // HIERARCHY RESOLUTION — CHILDREN_RECURSIVE
    // ========================================================================

    @Nested
    class ChildrenRecursiveResolution {

        @Test
        void resolveAll_recursiveRef_findsDeepChild() {
            ComponentRegistry.initialize();

            var root = new GameObject("root");
            var child = new GameObject("child");
            var grandchild = new GameObject("grandchild");
            var sprite = new SpriteRenderer();
            grandchild.addComponent(sprite);
            child.addChild(grandchild);
            root.addChild(child);

            var ref = new RecursiveRefComponent();
            root.addComponent(ref);

            ComponentReferenceResolver.resolveAll(root);

            assertSame(sprite, ref.deepRenderer);
        }
    }

    // ========================================================================
    // KEY RESOLUTION
    // ========================================================================

    @Nested
    class KeyResolution {

        @Test
        void resolveAll_keyRef_resolvesFromRegistry() {
            ComponentRegistry.initialize();

            var target = new StubComponent();
            ComponentKeyRegistry.register("myTarget", target);

            var go = new GameObject("test");
            var ref = new KeyRefComponent();
            go.addComponent(ref);

            ComponentReferenceResolver.storePendingKey(ref, "target", "myTarget");
            ComponentReferenceResolver.resolveAll(go);

            assertSame(target, ref.target);
        }

        @Test
        void resolveAll_keyRef_nullWhenKeyNotInRegistry() {
            ComponentRegistry.initialize();

            var go = new GameObject("test");
            var ref = new KeyRefComponent();
            go.addComponent(ref);

            ComponentReferenceResolver.storePendingKey(ref, "target", "missing");
            ComponentReferenceResolver.resolveAll(go);

            assertNull(ref.target);
        }

        @Test
        void resolveAll_keyRef_nullWhenNoPendingKey() {
            ComponentRegistry.initialize();

            var go = new GameObject("test");
            var ref = new KeyRefComponent();
            go.addComponent(ref);

            ComponentReferenceResolver.resolveAll(go);

            assertNull(ref.target);
        }
    }

    // ========================================================================
    // TEST COMPONENTS
    // ========================================================================

    public static class StubComponent extends Component {}

    public static class SelfRefComponent extends Component {
        @ComponentReference(source = Source.SELF)
        SpriteRenderer renderer;
    }

    public static class ParentRefComponent extends Component {
        @ComponentReference(source = Source.PARENT)
        SpriteRenderer parentRenderer;
    }

    public static class ChildrenRefComponent extends Component {
        @ComponentReference(source = Source.CHILDREN)
        SpriteRenderer childRenderer;
    }

    public static class ChildrenListRefComponent extends Component {
        @ComponentReference(source = Source.CHILDREN)
        List<SpriteRenderer> childRenderers;
    }

    public static class RecursiveRefComponent extends Component {
        @ComponentReference(source = Source.CHILDREN_RECURSIVE)
        SpriteRenderer deepRenderer;
    }

    public static class KeyRefComponent extends Component {
        @ComponentReference(source = Source.KEY)
        StubComponent target;
    }
}
