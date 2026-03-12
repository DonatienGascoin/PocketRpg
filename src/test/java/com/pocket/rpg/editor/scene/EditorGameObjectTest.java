package com.pocket.rpg.editor.scene;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Phase B contract: EditorGameObject extends GameObject.
 * Covers inheritance, type identity, lifecycle no-ops, enabled state,
 * hierarchy type guards, and transform propagation through EGO hierarchy.
 */
class EditorGameObjectTest {

    private EditorGameObject scratch(String name) {
        return new EditorGameObject(name, new Vector3f(), false);
    }

    private EditorGameObject scratchAt(String name, float x, float y) {
        return new EditorGameObject(name, new Vector3f(x, y, 0), false);
    }

    // ========================================================================
    // INHERITANCE & TYPE IDENTITY
    // ========================================================================

    @Nested
    class InheritanceAndTypeIdentity {

        @Test
        void editorGameObject_isInstanceOfGameObject() {
            EditorGameObject ego = scratch("Test");
            assertInstanceOf(GameObject.class, ego);
        }

        @Test
        void isRuntime_returnsFalse() {
            EditorGameObject ego = scratch("Test");
            assertFalse(ego.isRuntime());
        }

        @Test
        void isEditor_returnsTrue() {
            EditorGameObject ego = scratch("Test");
            assertTrue(ego.isEditor());
        }

        @Test
        void implementsHierarchyItem() {
            EditorGameObject ego = scratch("Test");
            assertInstanceOf(HierarchyItem.class, ego);
        }

        @Test
        void gameObject_isRuntime_returnsTrue() {
            GameObject go = new GameObject("Runtime");
            assertTrue(go.isRuntime());
            assertFalse(go.isEditor());
        }
    }

    // ========================================================================
    // LIFECYCLE NO-OPS
    // ========================================================================

    @Nested
    class LifecycleNoOps {

        @Test
        void start_doesNothing() {
            EditorGameObject ego = scratch("Test");
            // Add a component that tracks start calls
            LifecycleTracker tracker = new LifecycleTracker();
            tracker.setGameObject(ego);
            ego.getComponents().add(tracker);

            ego.start();

            assertFalse(tracker.startCalled, "EGO.start() should not call component.start()");
        }

        @Test
        void update_doesNothing() {
            EditorGameObject ego = scratch("Test");
            LifecycleTracker tracker = new LifecycleTracker();
            tracker.setGameObject(ego);
            ego.getComponents().add(tracker);

            ego.update(0.016f);

            assertFalse(tracker.updateCalled, "EGO.update() should not call component.update()");
        }

        @Test
        void lateUpdate_doesNothing() {
            EditorGameObject ego = scratch("Test");
            LifecycleTracker tracker = new LifecycleTracker();
            tracker.setGameObject(ego);
            ego.getComponents().add(tracker);

            ego.lateUpdate(0.016f);

            assertFalse(tracker.lateUpdateCalled, "EGO.lateUpdate() should not call component.lateUpdate()");
        }

        @Test
        void destroy_doesNothing() {
            EditorGameObject ego = scratch("Test");
            LifecycleTracker tracker = new LifecycleTracker();
            tracker.setGameObject(ego);
            ego.getComponents().add(tracker);

            ego.destroy();

            assertFalse(tracker.destroyCalled, "EGO.destroy() should not call component.destroy()");
            assertFalse(ego.isDestroyed(), "EGO.destroy() should not set destroyed flag");
        }

        /** Minimal component that tracks lifecycle calls. */
        private static class LifecycleTracker extends Component {
            boolean startCalled;
            boolean updateCalled;
            boolean lateUpdateCalled;
            boolean destroyCalled;

            @Override
            protected void onStart() {
                startCalled = true;
            }

            @Override
            public void update(float dt) {
                updateCalled = true;
            }

            @Override
            public void lateUpdate(float dt) {
                lateUpdateCalled = true;
            }

            @Override
            protected void onDestroy() {
                destroyCalled = true;
            }
        }
    }

    // ========================================================================
    // ENABLED STATE
    // ========================================================================

    @Nested
    class EnabledState {

        @Test
        void setEnabled_setsFieldDirectly_noComponentNotifications() {
            EditorGameObject ego = scratch("Test");
            EnableTracker tracker = new EnableTracker();
            tracker.setGameObject(ego);
            ego.getComponents().add(tracker);
            // Force started state so triggerEnable/triggerDisable would fire on GO
            tracker.start();
            tracker.reset();

            ego.setEnabled(false);
            assertFalse(ego.isEnabled());
            assertFalse(tracker.disableCalled, "EGO.setEnabled should not notify components");

            ego.setEnabled(true);
            assertTrue(ego.isEnabled());
            assertFalse(tracker.enableCalled, "EGO.setEnabled should not notify components");
        }

        @Test
        void isEnabled_returnsRawField_inheritedFromGameObject() {
            EditorGameObject ego = scratch("Test");
            assertTrue(ego.isEnabled());

            ego.setEnabled(false);
            assertFalse(ego.isEnabled());
        }

        @Test
        void isActiveInHierarchy_walksParentChain() {
            EditorGameObject parent = scratch("Parent");
            EditorGameObject child = scratch("Child");
            EditorGameObject grandchild = scratch("Grandchild");
            child.setParent(parent);
            grandchild.setParent(child);

            assertTrue(grandchild.isActiveInHierarchy());

            // Disable mid-level parent
            child.setEnabled(false);
            assertFalse(grandchild.isActiveInHierarchy());
            assertTrue(parent.isActiveInHierarchy());

            // Re-enable
            child.setEnabled(true);
            assertTrue(grandchild.isActiveInHierarchy());
        }

        @Test
        void isActiveInHierarchy_disabledRoot_affectsAllDescendants() {
            EditorGameObject root = scratch("Root");
            EditorGameObject child = scratch("Child");
            child.setParent(root);

            root.setEnabled(false);
            assertFalse(child.isActiveInHierarchy());
        }

        /** Tracks enable/disable callbacks. */
        private static class EnableTracker extends Component {
            boolean enableCalled;
            boolean disableCalled;

            void reset() {
                enableCalled = false;
                disableCalled = false;
            }

            @Override
            protected void onEnable() {
                enableCalled = true;
            }

            @Override
            protected void onDisable() {
                disableCalled = true;
            }
        }
    }

    // ========================================================================
    // HIERARCHY TYPE GUARD
    // ========================================================================

    @Nested
    class HierarchyTypeGuard {

        @Test
        void setParent_plainGameObject_throwsIllegalArgument() {
            EditorGameObject ego = scratch("Editor");
            GameObject plainGo = new GameObject("Runtime");

            assertThrows(IllegalArgumentException.class, () -> ego.setParent(plainGo));
        }

        @Test
        void setParent_editorGameObject_succeeds() {
            EditorGameObject parent = scratch("Parent");
            EditorGameObject child = scratch("Child");

            child.setParent(parent);

            assertEquals(parent, child.getParent());
            assertEquals(parent.getId(), child.getParentId());
        }

        @Test
        void setParent_null_succeeds() {
            EditorGameObject parent = scratch("Parent");
            EditorGameObject child = scratch("Child");
            child.setParent(parent);

            child.setParent(null);

            assertNull(child.getParent());
            assertNull(child.getParentId());
        }
    }

    // ========================================================================
    // COMPONENT OWNERSHIP
    // ========================================================================

    @Nested
    class ComponentOwnership {

        @Test
        void componentGameObject_isSetToEditorGameObject() {
            EditorGameObject ego = scratch("Test");
            Transform t = ego.getTransform();

            assertNotNull(t.getGameObject());
            assertSame(ego, t.getGameObject());
            assertInstanceOf(EditorGameObject.class, t.getGameObject());
        }

        @Test
        void addComponent_setsGameObjectOnComponent() {
            EditorGameObject ego = scratch("Test");
            StubComponent stub = new StubComponent();

            ego.addComponent(stub);

            assertSame(ego, stub.getGameObject());
        }

        @Test
        void replaceComponent_setsGameObjectOnNewComponent() {
            EditorGameObject ego = scratch("Test");
            StubComponent old = new StubComponent();
            ego.addComponent(old);

            StubComponent replacement = new StubComponent();
            ego.replaceComponent(old, replacement);

            assertSame(ego, replacement.getGameObject());
        }

        private static class StubComponent extends Component {}
    }

    // ========================================================================
    // TRANSFORM PROPAGATION
    // ========================================================================

    @Nested
    class TransformPropagation {

        @Test
        void parentTransform_resolvesThroughEditorHierarchy() {
            EditorGameObject parent = scratchAt("Parent", 10, 20);
            EditorGameObject child = scratchAt("Child", 5, 5);
            child.setParent(parent);

            // Child's world position should include parent offset
            Transform childTransform = child.getTransform();
            Vector3f worldPos = childTransform.getWorldPosition();

            assertEquals(15f, worldPos.x, 0.001f, "Child world X = parent(10) + local(5)");
            assertEquals(25f, worldPos.y, 0.001f, "Child world Y = parent(20) + local(5)");
        }

        @Test
        void grandchildTransform_accumulatesParentChain() {
            EditorGameObject root = scratchAt("Root", 100, 200);
            EditorGameObject mid = scratchAt("Mid", 10, 10);
            EditorGameObject leaf = scratchAt("Leaf", 1, 1);
            mid.setParent(root);
            leaf.setParent(mid);

            Vector3f leafWorld = leaf.getTransform().getWorldPosition();

            assertEquals(111f, leafWorld.x, 0.001f, "100 + 10 + 1");
            assertEquals(211f, leafWorld.y, 0.001f, "200 + 10 + 1");
        }

        @Test
        void markChildrenDirty_propagatesThroughEditorChildren() {
            EditorGameObject parent = scratchAt("Parent", 0, 0);
            EditorGameObject child = scratchAt("Child", 5, 5);
            child.setParent(parent);

            // Force child world position to be cached
            Vector3f childWorldBefore = child.getTransform().getWorldPosition();
            assertEquals(5f, childWorldBefore.x, 0.001f);

            // Move parent — should invalidate child's world cache
            parent.getTransform().setPosition(new Vector3f(100, 0, 0));

            // Child's world position should reflect parent's new position
            Vector3f childWorldAfter = child.getTransform().getWorldPosition();
            assertEquals(105f, childWorldAfter.x, 0.001f, "Child world should update after parent moves");
        }

        @Test
        void reparenting_updatesWorldPosition() {
            EditorGameObject parentA = scratchAt("ParentA", 10, 0);
            EditorGameObject parentB = scratchAt("ParentB", 50, 0);
            EditorGameObject child = scratchAt("Child", 1, 0);
            child.setParent(parentA);

            assertEquals(11f, child.getTransform().getWorldPosition().x, 0.001f);

            // Reparent to B
            child.setParent(parentB);

            assertEquals(51f, child.getTransform().getWorldPosition().x, 0.001f);
        }

        @Test
        void unparenting_worldEqualsLocal() {
            EditorGameObject parent = scratchAt("Parent", 100, 100);
            EditorGameObject child = scratchAt("Child", 5, 5);
            child.setParent(parent);

            assertEquals(105f, child.getTransform().getWorldPosition().x, 0.001f);

            child.setParent(null);

            assertEquals(5f, child.getTransform().getWorldPosition().x, 0.001f,
                    "After unparenting, world position should equal local position");
        }
    }

    // ========================================================================
    // HIERARCHY ITEM IMPLEMENTATION
    // ========================================================================

    @Nested
    class HierarchyItemImpl {

        @Test
        void getHierarchyParent_returnsParent() {
            EditorGameObject parent = scratch("Parent");
            EditorGameObject child = scratch("Child");
            child.setParent(parent);

            assertSame(parent, child.getHierarchyParent());
        }

        @Test
        void getHierarchyParent_null_forRoot() {
            EditorGameObject root = scratch("Root");
            assertNull(root.getHierarchyParent());
        }

        @Test
        void getHierarchyChildren_returnsChildren() {
            EditorGameObject parent = scratch("Parent");
            EditorGameObject child1 = scratch("Child1");
            EditorGameObject child2 = scratch("Child2");
            child1.setParent(parent);
            child2.setParent(parent);

            var children = parent.getHierarchyChildren();
            assertEquals(2, children.size());
        }

        @Test
        void isEditable_returnsTrue() {
            EditorGameObject ego = scratch("Test");
            assertTrue(ego.isEditable());
        }

        @Test
        void findComponentInParent_traversesHierarchy() {
            EditorGameObject parent = scratch("Parent");
            EditorGameObject child = scratch("Child");
            child.setParent(parent);

            // Transform is on parent — findComponentInParent from child should find it
            Transform found = child.findComponentInParent(Transform.class);
            assertNotNull(found);
            assertSame(parent.getTransform(), found);
        }
    }
}
