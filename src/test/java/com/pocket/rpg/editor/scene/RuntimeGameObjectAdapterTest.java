package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeGameObjectAdapterTest {

    @BeforeEach
    void setUp() {
        RuntimeGameObjectAdapter.clearCache();
    }

    @AfterEach
    void tearDown() {
        RuntimeGameObjectAdapter.clearCache();
    }

    // ========================================================================
    // CACHING
    // ========================================================================

    @Nested
    class Caching {

        @Test
        void of_returnsSameAdapterForSameGameObject() {
            GameObject obj = new GameObject("Player");
            RuntimeGameObjectAdapter a = RuntimeGameObjectAdapter.of(obj);
            RuntimeGameObjectAdapter b = RuntimeGameObjectAdapter.of(obj);

            assertSame(a, b);
        }

        @Test
        void of_returnsDifferentAdaptersForDifferentObjects() {
            GameObject first = new GameObject("First");
            GameObject second = new GameObject("Second");

            RuntimeGameObjectAdapter a = RuntimeGameObjectAdapter.of(first);
            RuntimeGameObjectAdapter b = RuntimeGameObjectAdapter.of(second);

            assertNotSame(a, b);
        }

        @Test
        void clearCache_createsNewAdapterAfterwards() {
            GameObject obj = new GameObject("Player");
            RuntimeGameObjectAdapter before = RuntimeGameObjectAdapter.of(obj);

            RuntimeGameObjectAdapter.clearCache();
            RuntimeGameObjectAdapter after = RuntimeGameObjectAdapter.of(obj);

            assertNotSame(before, after);
        }
    }

    // ========================================================================
    // DELEGATION
    // ========================================================================

    @Nested
    class Delegation {

        @Test
        void getName_delegatesToGameObject() {
            GameObject obj = new GameObject("Hero");
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(obj);

            assertEquals("Hero", adapter.getName());
        }

        @Test
        void getId_prefixedWithRuntime() {
            GameObject obj = new GameObject("Hero");
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(obj);

            assertTrue(adapter.getId().startsWith("runtime_"));
        }

        @Test
        void getTransform_delegatesToGameObject() {
            GameObject obj = new GameObject("Hero");
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(obj);

            assertSame(obj.getTransform(), adapter.getTransform());
        }

        @Test
        void isEnabled_delegatesToGameObject() {
            GameObject obj = new GameObject("Hero");
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(obj);

            assertTrue(adapter.isEnabled());
            obj.setEnabled(false);
            assertFalse(adapter.isEnabled());
        }

        @Test
        void getAllComponents_delegatesToGameObject() {
            GameObject obj = new GameObject("Hero");
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(obj);

            List<Component> components = adapter.getAllComponents();
            assertFalse(components.isEmpty());
            // At minimum has Transform
            assertTrue(components.stream().anyMatch(c -> c instanceof Transform));
        }

        @Test
        void getComponent_delegatesToGameObject() {
            GameObject obj = new GameObject("Hero");
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(obj);

            Transform transform = adapter.getComponent(Transform.class);
            assertNotNull(transform);
            assertSame(obj.getTransform(), transform);
        }
    }

    // ========================================================================
    // CONTEXT
    // ========================================================================

    @Nested
    class Context {

        @Test
        void isRuntime_returnsTrue() {
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(new GameObject("Test"));
            assertTrue(adapter.isRuntime());
        }

        @Test
        void isEditor_returnsFalse() {
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(new GameObject("Test"));
            assertFalse(adapter.isEditor());
        }

        @Test
        void isEditable_returnsFalse() {
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(new GameObject("Test"));
            assertFalse(adapter.isEditable());
        }
    }

    // ========================================================================
    // HIERARCHY
    // ========================================================================

    @Nested
    class Hierarchy {

        @Test
        void getHierarchyChildren_emptyForNoChildren() {
            GameObject obj = new GameObject("Solo");
            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(obj);

            assertTrue(adapter.getHierarchyChildren().isEmpty());
            assertFalse(adapter.hasHierarchyChildren());
        }

        @Test
        void getHierarchyChildren_wrapsChildrenAsAdapters() {
            GameObject parent = new GameObject("Parent");
            GameObject child1 = new GameObject("Child1");
            GameObject child2 = new GameObject("Child2");
            child1.setParent(parent);
            child2.setParent(parent);

            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(parent);
            List<? extends HierarchyItem> children = adapter.getHierarchyChildren();

            assertEquals(2, children.size());
            assertTrue(adapter.hasHierarchyChildren());
            assertEquals("Child1", children.get(0).getName());
            assertEquals("Child2", children.get(1).getName());
        }

        @Test
        void getHierarchyChildren_returnedItemsAreRuntimeAdapters() {
            GameObject parent = new GameObject("Parent");
            GameObject child = new GameObject("Child");
            child.setParent(parent);

            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(parent);
            HierarchyItem childItem = adapter.getHierarchyChildren().get(0);

            assertInstanceOf(RuntimeGameObjectAdapter.class, childItem);
            assertTrue(childItem.isRuntime());
            assertFalse(childItem.isEditable());
        }

        @Test
        void getHierarchyChildren_usesCache() {
            GameObject parent = new GameObject("Parent");
            GameObject child = new GameObject("Child");
            child.setParent(parent);

            RuntimeGameObjectAdapter adapter = RuntimeGameObjectAdapter.of(parent);
            HierarchyItem first = adapter.getHierarchyChildren().get(0);
            HierarchyItem second = adapter.getHierarchyChildren().get(0);

            // Both calls should return the same cached adapter
            assertSame(first, second);
        }
    }
}
