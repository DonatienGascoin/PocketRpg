package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityCommandsTest {

    private EditorScene scene;

    @BeforeEach
    void setUp() {
        scene = new EditorScene();
    }

    private EditorGameObject createEntity(String name) {
        return new EditorGameObject(name, new Vector3f(0, 0, 0), false);
    }

    private EditorGameObject createEntity(String name, float x, float y) {
        return new EditorGameObject(name, new Vector3f(x, y, 0), false);
    }

    // ========================================================================
    // ADD ENTITY COMMAND
    // ========================================================================

    @Nested
    class AddEntityCommandTests {

        @Test
        void execute_addsEntityToScene() {
            var entity = createEntity("Player");
            var cmd = new AddEntityCommand(scene, entity);

            cmd.execute();

            assertTrue(scene.getEntities().contains(entity));
        }

        @Test
        void undo_removesEntityFromScene() {
            var entity = createEntity("Player");
            var cmd = new AddEntityCommand(scene, entity);
            cmd.execute();

            cmd.undo();

            assertFalse(scene.getEntities().contains(entity));
        }

        @Test
        void fullCycle_executeUndoRedo() {
            var entity = createEntity("Player");
            var cmd = new AddEntityCommand(scene, entity);

            cmd.execute();
            assertTrue(scene.getEntities().contains(entity));

            cmd.undo();
            assertFalse(scene.getEntities().contains(entity));

            cmd.execute();
            assertTrue(scene.getEntities().contains(entity));
        }

        @Test
        void description_containsEntityName() {
            var entity = createEntity("Player");
            var cmd = new AddEntityCommand(scene, entity);
            assertEquals("Add Player", cmd.getDescription());
        }
    }

    // ========================================================================
    // ADD ENTITIES COMMAND
    // ========================================================================

    @Nested
    class AddEntitiesCommandTests {

        @Test
        void execute_addsMultipleEntities() {
            var e1 = createEntity("A");
            var e2 = createEntity("B");
            var cmd = new AddEntitiesCommand(scene, e1, e2);

            cmd.execute();

            var entities = scene.getEntities();
            assertTrue(entities.contains(e1));
            assertTrue(entities.contains(e2));
        }

        @Test
        void undo_removesAllAddedEntities() {
            var e1 = createEntity("A");
            var e2 = createEntity("B");
            var cmd = new AddEntitiesCommand(scene, e1, e2);
            cmd.execute();

            cmd.undo();

            assertTrue(scene.getEntities().isEmpty());
        }

        @Test
        void redo_restoresParentAndOrder() {
            var parent = createEntity("Parent");
            scene.addEntity(parent);

            var child = createEntity("Child");
            child.setParent(parent);
            child.setOrder(2);

            var cmd = new AddEntitiesCommand(scene, child);
            cmd.execute(); // captures parent + order on first execute
            cmd.undo();

            cmd.execute(); // redo: should restore parent + order

            assertEquals(parent, child.getParent());
            assertEquals(2, child.getOrder());
        }
    }

    // ========================================================================
    // REMOVE ENTITY COMMAND
    // ========================================================================

    @Nested
    class RemoveEntityCommandTests {

        @Test
        void execute_removesEntityFromScene() {
            var entity = createEntity("Player");
            scene.addEntity(entity);
            var cmd = new RemoveEntityCommand(scene, entity);

            cmd.execute();

            assertFalse(scene.getEntities().contains(entity));
        }

        @Test
        void undo_restoresEntity() {
            var entity = createEntity("Player");
            scene.addEntity(entity);
            var cmd = new RemoveEntityCommand(scene, entity);
            cmd.execute();

            cmd.undo();

            assertTrue(scene.getEntities().contains(entity));
        }

        @Test
        void undo_restoresChildrenOfDeletedEntity() {
            var parent = createEntity("Parent");
            var child1 = createEntity("Child1");
            var child2 = createEntity("Child2");
            scene.addEntity(parent);
            scene.addEntity(child1);
            scene.addEntity(child2);
            child1.setParent(parent);
            child2.setParent(parent);

            var cmd = new RemoveEntityCommand(scene, parent);
            cmd.execute();

            // All should be gone
            assertTrue(scene.getEntities().isEmpty());

            cmd.undo();

            // All should be back
            assertEquals(3, scene.getEntities().size());
            assertTrue(scene.getEntities().contains(parent));
            assertTrue(scene.getEntities().contains(child1));
            assertTrue(scene.getEntities().contains(child2));
        }

        @Test
        void undo_restoresHierarchyOfChildren() {
            var parent = createEntity("Parent");
            var child = createEntity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            var cmd = new RemoveEntityCommand(scene, parent);
            cmd.execute();
            cmd.undo();

            // Hierarchy should be restored
            assertEquals(parent, child.getParent());
            assertEquals(parent.getId(), child.getParentId());
            assertTrue(parent.getChildren().contains(child));
        }

        @Test
        void undo_restoresDeeplyNestedHierarchy() {
            var grandparent = createEntity("Grandparent");
            var parent = createEntity("Parent");
            var child = createEntity("Child");
            scene.addEntity(grandparent);
            scene.addEntity(parent);
            scene.addEntity(child);
            parent.setParent(grandparent);
            child.setParent(parent);

            var cmd = new RemoveEntityCommand(scene, grandparent);
            cmd.execute();

            assertTrue(scene.getEntities().isEmpty());

            cmd.undo();

            assertEquals(3, scene.getEntities().size());
            assertEquals(grandparent, parent.getParent());
            assertEquals(parent, child.getParent());
            assertTrue(grandparent.getChildren().contains(parent));
            assertTrue(parent.getChildren().contains(child));
        }

        @Test
        void undo_restoresSiblingOrderOfChildren() {
            var parent = createEntity("Parent");
            var childA = createEntity("ChildA");
            var childB = createEntity("ChildB");
            var childC = createEntity("ChildC");
            scene.addEntity(parent);
            scene.addEntity(childA);
            scene.addEntity(childB);
            scene.addEntity(childC);
            childA.setParent(parent);
            childB.setParent(parent);
            childC.setParent(parent);
            childA.setOrder(0);
            childB.setOrder(1);
            childC.setOrder(2);

            var cmd = new RemoveEntityCommand(scene, parent);
            cmd.execute();
            cmd.undo();

            assertEquals(0, childA.getOrder());
            assertEquals(1, childB.getOrder());
            assertEquals(2, childC.getOrder());
        }

        @Test
        void execute_undo_execute_cycle() {
            var parent = createEntity("Parent");
            var child = createEntity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            var cmd = new RemoveEntityCommand(scene, parent);

            // Delete
            cmd.execute();
            assertTrue(scene.getEntities().isEmpty());

            // Undo
            cmd.undo();
            assertEquals(2, scene.getEntities().size());
            assertEquals(parent, child.getParent());

            // Redo (execute again)
            cmd.execute();
            assertTrue(scene.getEntities().isEmpty());
        }

        @Test
        void undo_childOnlyDeletedEntity_noParent() {
            // Entity with no parent, just remove + undo
            var entity = createEntity("Standalone");
            scene.addEntity(entity);

            var cmd = new RemoveEntityCommand(scene, entity);
            cmd.execute();
            cmd.undo();

            assertTrue(scene.getEntities().contains(entity));
            assertNull(entity.getParent());
            assertNull(entity.getParentId());
        }
    }

    // ========================================================================
    // BULK DELETE COMMAND
    // ========================================================================

    @Nested
    class BulkDeleteCommandTests {

        @Test
        void execute_removesAllSelectedEntities() {
            var e1 = createEntity("A");
            var e2 = createEntity("B");
            scene.addEntity(e1);
            scene.addEntity(e2);

            var cmd = new BulkDeleteCommand(scene, Set.of(e1, e2));
            cmd.execute();

            assertTrue(scene.getEntities().isEmpty());
        }

        @Test
        void undo_restoresAllEntities() {
            var e1 = createEntity("A");
            var e2 = createEntity("B");
            scene.addEntity(e1);
            scene.addEntity(e2);

            var cmd = new BulkDeleteCommand(scene, Set.of(e1, e2));
            cmd.execute();
            cmd.undo();

            assertEquals(2, scene.getEntities().size());
        }

        @Test
        void undo_restoresChildrenNotInSelectionSet() {
            var parent = createEntity("Parent");
            var child = createEntity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            // Only parent is in the selection set, but child gets deleted too
            var cmd = new BulkDeleteCommand(scene, Set.of(parent));
            cmd.execute();

            assertTrue(scene.getEntities().isEmpty());

            cmd.undo();

            assertEquals(2, scene.getEntities().size());
            assertTrue(scene.getEntities().contains(child));
            assertEquals(parent, child.getParent());
        }

        @Test
        void undo_restoresDeepHierarchy() {
            var root = createEntity("Root");
            var mid = createEntity("Mid");
            var leaf = createEntity("Leaf");
            scene.addEntity(root);
            scene.addEntity(mid);
            scene.addEntity(leaf);
            mid.setParent(root);
            leaf.setParent(mid);

            var cmd = new BulkDeleteCommand(scene, Set.of(root));
            cmd.execute();
            cmd.undo();

            assertEquals(3, scene.getEntities().size());
            assertEquals(root, mid.getParent());
            assertEquals(mid, leaf.getParent());
        }

        @Test
        void undo_restoresOrdersForChildren() {
            var parent = createEntity("Parent");
            var c1 = createEntity("C1");
            var c2 = createEntity("C2");
            scene.addEntity(parent);
            scene.addEntity(c1);
            scene.addEntity(c2);
            c1.setParent(parent);
            c2.setParent(parent);
            c1.setOrder(0);
            c2.setOrder(1);

            var cmd = new BulkDeleteCommand(scene, Set.of(parent));
            cmd.execute();
            cmd.undo();

            assertEquals(0, c1.getOrder());
            assertEquals(1, c2.getOrder());
        }

        @Test
        void execute_undo_withOverlappingSelection() {
            // Both parent and child are in selection
            var parent = createEntity("Parent");
            var child = createEntity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            var cmd = new BulkDeleteCommand(scene, Set.of(parent, child));
            cmd.execute();
            cmd.undo();

            assertEquals(2, scene.getEntities().size());
            assertEquals(parent, child.getParent());
        }

        @Test
        void execute_clearsSelection() {
            var entity = createEntity("Test");
            scene.addEntity(entity);
            scene.setSelectedEntity(entity);

            var cmd = new BulkDeleteCommand(scene, Set.of(entity));
            cmd.execute();

            assertNull(scene.getSelectedEntity());
        }
    }

    // ========================================================================
    // REPARENT ENTITY COMMAND
    // ========================================================================

    @Nested
    class ReparentEntityCommandTests {

        @Test
        void execute_movesEntityToNewParent() {
            var entity = createEntity("Entity");
            var newParent = createEntity("NewParent");
            scene.addEntity(entity);
            scene.addEntity(newParent);

            var cmd = new ReparentEntityCommand(scene, entity, newParent, 0);
            cmd.execute();

            assertEquals(newParent, entity.getParent());
        }

        @Test
        void undo_restoresOldParent() {
            var entity = createEntity("Entity");
            var oldParent = createEntity("OldParent");
            var newParent = createEntity("NewParent");
            scene.addEntity(entity);
            scene.addEntity(oldParent);
            scene.addEntity(newParent);
            entity.setParent(oldParent);

            var cmd = new ReparentEntityCommand(scene, entity, newParent, 0);
            cmd.execute();
            assertEquals(newParent, entity.getParent());

            cmd.undo();
            assertEquals(oldParent, entity.getParent());
        }

        @Test
        void execute_movesFromParentToRoot() {
            var parent = createEntity("Parent");
            var child = createEntity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            var cmd = new ReparentEntityCommand(scene, child, null, 0);
            cmd.execute();

            assertNull(child.getParent());
            assertNull(child.getParentId());
        }

        @Test
        void undo_restoresPositionAmongSiblings() {
            var a = createEntity("A");
            var b = createEntity("B");
            var c = createEntity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);

            // Move B under a new parent
            var parent = createEntity("Parent");
            scene.addEntity(parent);

            var cmd = new ReparentEntityCommand(scene, b, parent, 0);
            cmd.execute();

            cmd.undo();

            // B should be back among root entities
            assertNull(b.getParent());
        }
    }

    // ========================================================================
    // REORDER ENTITY COMMAND
    // ========================================================================

    @Nested
    class ReorderEntityCommandTests {

        @Test
        void execute_movesEntityToNewPosition() {
            var a = createEntity("A");
            var b = createEntity("B");
            var c = createEntity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);

            // Move C to position 0 (first)
            var cmd = new ReorderEntityCommand(scene, c, 0);
            cmd.execute();

            List<EditorGameObject> roots = scene.getRootEntities();
            assertEquals("C", roots.get(0).getName());
        }

        @Test
        void undo_restoresOriginalOrder() {
            var a = createEntity("A");
            var b = createEntity("B");
            var c = createEntity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);

            var cmd = new ReorderEntityCommand(scene, c, 0);
            cmd.execute();
            cmd.undo();

            List<EditorGameObject> roots = scene.getRootEntities();
            assertEquals("A", roots.get(0).getName());
            assertEquals("B", roots.get(1).getName());
            assertEquals("C", roots.get(2).getName());
        }

        @Test
        void execute_reordersChildrenWithinParent() {
            var parent = createEntity("Parent");
            var a = createEntity("A");
            var b = createEntity("B");
            scene.addEntity(parent);
            scene.addEntity(a);
            scene.addEntity(b);
            a.setParent(parent);
            b.setParent(parent);
            a.setOrder(0);
            b.setOrder(1);

            // Move B before A
            var cmd = new ReorderEntityCommand(scene, b, 0);
            cmd.execute();

            assertEquals(0, b.getOrder());
            assertEquals(1, a.getOrder());
        }
    }

    // ========================================================================
    // MOVE ENTITY COMMAND
    // ========================================================================

    @Nested
    class MoveEntityCommandTests {

        @Test
        void execute_movesEntity() {
            var entity = createEntity("Player", 10, 20);
            var oldPos = new Vector3f(10, 20, 0);
            var newPos = new Vector3f(50, 60, 0);

            var cmd = new MoveEntityCommand(entity, oldPos, newPos);
            cmd.execute();

            assertEquals(50, entity.getPosition().x, 0.001f);
            assertEquals(60, entity.getPosition().y, 0.001f);
        }

        @Test
        void undo_restoresOriginalPosition() {
            var entity = createEntity("Player", 10, 20);
            var oldPos = new Vector3f(10, 20, 0);
            var newPos = new Vector3f(50, 60, 0);

            var cmd = new MoveEntityCommand(entity, oldPos, newPos);
            cmd.execute();
            cmd.undo();

            assertEquals(10, entity.getPosition().x, 0.001f);
            assertEquals(20, entity.getPosition().y, 0.001f);
        }

        @Test
        void merge_updatesNewPosition() {
            var entity = createEntity("Player", 0, 0);
            var cmd1 = new MoveEntityCommand(entity, new Vector3f(0, 0, 0), new Vector3f(5, 5, 0));
            var cmd2 = new MoveEntityCommand(entity, new Vector3f(5, 5, 0), new Vector3f(10, 10, 0));

            assertTrue(cmd1.canMergeWith(cmd2));
            cmd1.mergeWith(cmd2);

            // After merge, undo should go back to original 0,0
            cmd1.execute();
            assertEquals(10, entity.getPosition().x, 0.001f);

            cmd1.undo();
            assertEquals(0, entity.getPosition().x, 0.001f);
        }

        @Test
        void merge_differentEntities_doesNotMerge() {
            var e1 = createEntity("A");
            var e2 = createEntity("B");
            var cmd1 = new MoveEntityCommand(e1, new Vector3f(), new Vector3f(1, 0, 0));
            var cmd2 = new MoveEntityCommand(e2, new Vector3f(), new Vector3f(1, 0, 0));

            assertFalse(cmd1.canMergeWith(cmd2));
        }
    }

    // ========================================================================
    // BULK MOVE COMMAND
    // ========================================================================

    @Nested
    class BulkMoveCommandTests {

        @Test
        void execute_movesAllEntitiesByOffset() {
            var e1 = createEntity("A", 10, 20);
            var e2 = createEntity("B", 30, 40);
            scene.addEntity(e1);
            scene.addEntity(e2);

            var cmd = new BulkMoveCommand(scene, Set.of(e1, e2), new Vector3f(5, 5, 0));
            cmd.execute();

            assertEquals(15, e1.getPosition().x, 0.001f);
            assertEquals(25, e1.getPosition().y, 0.001f);
            assertEquals(35, e2.getPosition().x, 0.001f);
            assertEquals(45, e2.getPosition().y, 0.001f);
        }

        @Test
        void undo_restoresOriginalPositions() {
            var e1 = createEntity("A", 10, 20);
            var e2 = createEntity("B", 30, 40);
            scene.addEntity(e1);
            scene.addEntity(e2);

            var cmd = new BulkMoveCommand(scene, Set.of(e1, e2), new Vector3f(5, 5, 0));
            cmd.execute();
            cmd.undo();

            assertEquals(10, e1.getPosition().x, 0.001f);
            assertEquals(20, e1.getPosition().y, 0.001f);
            assertEquals(30, e2.getPosition().x, 0.001f);
            assertEquals(40, e2.getPosition().y, 0.001f);
        }
    }
}
