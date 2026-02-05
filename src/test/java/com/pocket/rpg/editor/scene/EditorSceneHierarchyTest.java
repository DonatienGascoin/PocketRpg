package com.pocket.rpg.editor.scene;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EditorSceneHierarchyTest {

    private EditorScene scene;

    @BeforeEach
    void setUp() {
        scene = new EditorScene();
    }

    private EditorGameObject entity(String name) {
        return new EditorGameObject(name, new Vector3f(0, 0, 0), false);
    }

    private EditorGameObject entityAt(String name, float x, float y) {
        return new EditorGameObject(name, new Vector3f(x, y, 0), false);
    }

    // ========================================================================
    // ADD ENTITY
    // ========================================================================

    @Nested
    class AddEntity {

        @Test
        void addsEntityToList() {
            var e = entity("A");
            scene.addEntity(e);
            assertTrue(scene.getEntities().contains(e));
        }

        @Test
        void nullEntity_ignored() {
            scene.addEntity(null);
            assertTrue(scene.getEntities().isEmpty());
        }

        @Test
        void incrementsHierarchyVersion() {
            int before = scene.getHierarchyVersion();
            scene.addEntity(entity("A"));
            assertTrue(scene.getHierarchyVersion() > before);
        }

        @Test
        void marksDirty() {
            scene.clearDirty();
            scene.addEntity(entity("A"));
            assertTrue(scene.isDirty());
        }

        @Test
        void duplicateId_regeneratesId() {
            var e1 = entity("A");
            var e2 = entity("B");
            e2.setId(e1.getId()); // force duplicate

            scene.addEntity(e1);
            scene.addEntity(e2);

            assertNotEquals(e1.getId(), e2.getId());
            assertEquals(2, scene.getEntities().size());
        }

        @Test
        void sameEntityAddedTwice_duplicatesInList() {
            // addEntity doesn't check object reference, only ID
            var e = entity("A");
            scene.addEntity(e);
            // Second add triggers ID regen due to duplicate ID check
            scene.addEntity(e);
            // Entity is in the list twice — this is a grey area
            assertEquals(2, scene.getEntities().size());
        }
    }

    // ========================================================================
    // REMOVE ENTITY
    // ========================================================================

    @Nested
    class RemoveEntity {

        @Test
        void removesEntityFromList() {
            var e = entity("A");
            scene.addEntity(e);
            scene.removeEntity(e);
            assertFalse(scene.getEntities().contains(e));
        }

        @Test
        void nullEntity_ignored() {
            scene.removeEntity(null);
            // no exception
        }

        @Test
        void removesChildrenRecursively() {
            var parent = entity("Parent");
            var child = entity("Child");
            var grandchild = entity("Grandchild");
            scene.addEntity(parent);
            scene.addEntity(child);
            scene.addEntity(grandchild);
            child.setParent(parent);
            grandchild.setParent(child);

            scene.removeEntity(parent);

            assertTrue(scene.getEntities().isEmpty());
        }

        @Test
        void clearsParentReference() {
            var parent = entity("Parent");
            var child = entity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            scene.removeEntity(child);

            assertNull(child.getParent());
            assertNull(child.getParentId());
        }

        @Test
        void removesFromSelection() {
            var e = entity("A");
            scene.addEntity(e);
            scene.setSelectedEntity(e);

            scene.removeEntity(e);

            assertFalse(scene.getSelectedEntities().contains(e));
        }

        @Test
        void incrementsHierarchyVersion() {
            var e = entity("A");
            scene.addEntity(e);
            int before = scene.getHierarchyVersion();

            scene.removeEntity(e);

            assertTrue(scene.getHierarchyVersion() > before);
        }

        @Test
        void entityNotInScene_noException() {
            var e = entity("A");
            scene.removeEntity(e); // not added
            // no exception
        }
    }

    // ========================================================================
    // GET ROOT ENTITIES
    // ========================================================================

    @Nested
    class GetRootEntities {

        @Test
        void returnsOnlyEntitiesWithoutParent() {
            var root1 = entity("R1");
            var root2 = entity("R2");
            var child = entity("C");
            scene.addEntity(root1);
            scene.addEntity(root2);
            scene.addEntity(child);
            child.setParent(root1);

            List<EditorGameObject> roots = scene.getRootEntities();

            assertEquals(2, roots.size());
            assertTrue(roots.contains(root1));
            assertTrue(roots.contains(root2));
            assertFalse(roots.contains(child));
        }

        @Test
        void sortedByOrder() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(2);
            b.setOrder(0);
            c.setOrder(1);

            List<EditorGameObject> roots = scene.getRootEntities();

            assertEquals("B", roots.get(0).getName());
            assertEquals("C", roots.get(1).getName());
            assertEquals("A", roots.get(2).getName());
        }

        @Test
        void emptyParentId_treatedAsRoot() {
            var e = entity("E");
            scene.addEntity(e);
            e.setParentId(""); // empty string, not null

            List<EditorGameObject> roots = scene.getRootEntities();
            assertTrue(roots.contains(e));
        }
    }

    // ========================================================================
    // RESOLVE HIERARCHY
    // ========================================================================

    @Nested
    class ResolveHierarchy {

        @Test
        void rebuildsParentChildFromParentId() {
            var parent = entity("Parent");
            var child = entity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);

            // Set parentId directly (simulating deserialization)
            child.setParentId(parent.getId());

            scene.resolveHierarchy();

            assertEquals(parent, child.getParent());
            assertTrue(parent.getChildren().contains(child));
        }

        @Test
        void missingParent_clearsParentId() {
            var child = entity("Orphan");
            scene.addEntity(child);
            child.setParentId("nonexistent-id");

            scene.resolveHierarchy();

            assertNull(child.getParentId());
            assertNull(child.getParent());
        }

        @Test
        void clearsOldChildrenBeforeRebuilding() {
            var parent = entity("Parent");
            var child1 = entity("C1");
            var child2 = entity("C2");
            scene.addEntity(parent);
            scene.addEntity(child1);
            scene.addEntity(child2);
            child1.setParent(parent);
            child2.setParent(parent);

            // Move child2 to root by clearing its parentId
            child2.setParentId(null);

            scene.resolveHierarchy();

            assertEquals(1, parent.getChildren().size());
            assertTrue(parent.getChildren().contains(child1));
            assertFalse(parent.getChildren().contains(child2));
        }

        @Test
        void reindexesSiblingsAfterResolve() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(10);
            b.setOrder(20);
            c.setOrder(30);

            scene.resolveHierarchy();

            // Should be reindexed to 0, 1, 2
            assertEquals(0, a.getOrder());
            assertEquals(1, b.getOrder());
            assertEquals(2, c.getOrder());
        }

        @Test
        void multiLevel_hierarchyResolved() {
            var root = entity("Root");
            var mid = entity("Mid");
            var leaf = entity("Leaf");
            scene.addEntity(root);
            scene.addEntity(mid);
            scene.addEntity(leaf);

            mid.setParentId(root.getId());
            leaf.setParentId(mid.getId());

            scene.resolveHierarchy();

            assertEquals(root, mid.getParent());
            assertEquals(mid, leaf.getParent());
            assertTrue(root.getChildren().contains(mid));
            assertTrue(mid.getChildren().contains(leaf));
        }
    }

    // ========================================================================
    // REINDEX SIBLINGS
    // ========================================================================

    @Nested
    class ReindexSiblings {

        @Test
        void rootLevel_assignsSequentialOrders() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(5);
            b.setOrder(10);
            c.setOrder(15);

            scene.reindexSiblings(null);

            assertEquals(0, a.getOrder());
            assertEquals(1, b.getOrder());
            assertEquals(2, c.getOrder());
        }

        @Test
        void childLevel_assignsSequentialOrders() {
            var parent = entity("Parent");
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(parent);
            scene.addEntity(a);
            scene.addEntity(b);
            a.setParent(parent);
            b.setParent(parent);
            a.setOrder(10);
            b.setOrder(20);

            scene.reindexSiblings(parent);

            assertEquals(0, a.getOrder());
            assertEquals(1, b.getOrder());
        }

        @Test
        void preservesRelativeOrder() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(100);
            b.setOrder(50);
            c.setOrder(75);

            scene.reindexSiblings(null);

            // Sorted by current order: B(50), C(75), A(100) → 0, 1, 2
            assertEquals(2, a.getOrder()); // was highest
            assertEquals(0, b.getOrder()); // was lowest
            assertEquals(1, c.getOrder()); // was middle
        }
    }

    // ========================================================================
    // INSERT ENTITY AT POSITION
    // ========================================================================

    @Nested
    class InsertEntityAtPosition {

        @Test
        void moveToBeginningOfRoots() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);

            scene.insertEntityAtPosition(c, null, 0);

            List<EditorGameObject> roots = scene.getRootEntities();
            assertEquals("C", roots.get(0).getName());
            assertEquals("A", roots.get(1).getName());
            assertEquals("B", roots.get(2).getName());
        }

        @Test
        void moveToEndOfRoots() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);

            scene.insertEntityAtPosition(a, null, 2);

            List<EditorGameObject> roots = scene.getRootEntities();
            assertEquals("B", roots.get(0).getName());
            assertEquals("C", roots.get(1).getName());
            assertEquals("A", roots.get(2).getName());
        }

        @Test
        void reparentFromRootToChild() {
            var parent = entity("Parent");
            var child = entity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            parent.setOrder(0);
            child.setOrder(1);

            scene.insertEntityAtPosition(child, parent, 0);

            assertEquals(parent, child.getParent());
            assertEquals(parent.getId(), child.getParentId());
            assertTrue(parent.getChildren().contains(child));
        }

        @Test
        void reparentFromChildToRoot() {
            var parent = entity("Parent");
            var child = entity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            scene.insertEntityAtPosition(child, null, 0);

            assertNull(child.getParent());
            assertNull(child.getParentId());
        }

        @Test
        void moveWithinSameParent() {
            var parent = entity("Parent");
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(parent);
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setParent(parent);
            b.setParent(parent);
            c.setParent(parent);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);

            // Move C to first position
            scene.insertEntityAtPosition(c, parent, 0);

            assertEquals(0, c.getOrder());
            assertEquals(1, a.getOrder());
            assertEquals(2, b.getOrder());
        }

        @Test
        void clampsNegativeIndex() {
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);
            a.setOrder(0);
            b.setOrder(1);

            scene.insertEntityAtPosition(b, null, -5);

            assertEquals(0, b.getOrder());
            assertEquals(1, a.getOrder());
        }

        @Test
        void clampsOverflowIndex() {
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);
            a.setOrder(0);
            b.setOrder(1);

            scene.insertEntityAtPosition(a, null, 999);

            assertEquals(0, b.getOrder());
            assertEquals(1, a.getOrder());
        }

        @Test
        void moveBetweenDifferentParents() {
            var parent1 = entity("P1");
            var parent2 = entity("P2");
            var child = entity("Child");
            scene.addEntity(parent1);
            scene.addEntity(parent2);
            scene.addEntity(child);
            child.setParent(parent1);

            scene.insertEntityAtPosition(child, parent2, 0);

            assertEquals(parent2, child.getParent());
            assertFalse(parent1.getChildren().contains(child));
            assertTrue(parent2.getChildren().contains(child));
        }

        @Test
        void oldParentSiblingsReindexed() {
            var parent = entity("Parent");
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(parent);
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            a.setParent(parent);
            b.setParent(parent);
            c.setParent(parent);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);

            // Move B to root
            scene.insertEntityAtPosition(b, null, 0);

            // A and C remain as children of parent, should be reindexed
            assertEquals(0, a.getOrder());
            assertEquals(1, c.getOrder());
        }

        @Test
        void rootSiblingsReindexedWhenMovingToChild() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            var target = entity("Target");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            scene.addEntity(target);
            a.setOrder(0);
            b.setOrder(1);
            c.setOrder(2);
            target.setOrder(3);

            // Move B under Target
            scene.insertEntityAtPosition(b, target, 0);

            // Remaining roots: A, C, Target — should have no gaps
            assertEquals(0, a.getOrder());
            assertEquals(1, c.getOrder());
            assertEquals(2, target.getOrder());
        }
    }

    // ========================================================================
    // PARENT-CHILD (EditorGameObject)
    // ========================================================================

    @Nested
    class ParentChild {

        @Test
        void setParent_updatesParentIdAndTransientRef() {
            var parent = entity("Parent");
            var child = entity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);

            child.setParent(parent);

            assertEquals(parent, child.getParent());
            assertEquals(parent.getId(), child.getParentId());
            assertTrue(parent.getChildren().contains(child));
        }

        @Test
        void setParent_null_clearsParent() {
            var parent = entity("Parent");
            var child = entity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            child.setParent(null);

            assertNull(child.getParent());
            assertNull(child.getParentId());
            assertFalse(parent.getChildren().contains(child));
        }

        @Test
        void setParent_self_rejected() {
            var e = entity("Self");
            scene.addEntity(e);

            e.setParent(e);

            assertNull(e.getParent()); // unchanged
        }

        @Test
        void setParent_circularReference_rejected() {
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);
            b.setParent(a); // A -> B

            a.setParent(b); // Would create A -> B -> A

            assertNull(a.getParent()); // rejected
            assertEquals(a, b.getParent()); // unchanged
        }

        @Test
        void setParent_deepCircularReference_rejected() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addEntity(c);
            b.setParent(a);
            c.setParent(b); // A -> B -> C

            a.setParent(c); // Would create cycle: A -> B -> C -> A

            assertNull(a.getParent()); // rejected
        }

        @Test
        void setParent_changingParent_removesFromOldParent() {
            var p1 = entity("P1");
            var p2 = entity("P2");
            var child = entity("Child");
            scene.addEntity(p1);
            scene.addEntity(p2);
            scene.addEntity(child);
            child.setParent(p1);

            child.setParent(p2);

            assertFalse(p1.getChildren().contains(child));
            assertTrue(p2.getChildren().contains(child));
            assertEquals(p2, child.getParent());
        }

        @Test
        void isAncestorOf_directParent() {
            var parent = entity("Parent");
            var child = entity("Child");
            child.setParent(parent);

            assertTrue(parent.isAncestorOf(child));
            assertFalse(child.isAncestorOf(parent));
        }

        @Test
        void isAncestorOf_deepAncestor() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            b.setParent(a);
            c.setParent(b);

            assertTrue(a.isAncestorOf(c));
            assertFalse(c.isAncestorOf(a));
        }

        @Test
        void isAncestorOf_noRelation() {
            var a = entity("A");
            var b = entity("B");

            assertFalse(a.isAncestorOf(b));
        }

        @Test
        void getDepth_root() {
            var e = entity("Root");
            assertEquals(0, e.getDepth());
        }

        @Test
        void getDepth_nested() {
            var a = entity("A");
            var b = entity("B");
            var c = entity("C");
            b.setParent(a);
            c.setParent(b);

            assertEquals(0, a.getDepth());
            assertEquals(1, b.getDepth());
            assertEquals(2, c.getDepth());
        }

        @Test
        void getChildren_returnsUnmodifiableList() {
            var parent = entity("Parent");
            var child = entity("Child");
            child.setParent(parent);

            assertThrows(UnsupportedOperationException.class,
                    () -> parent.getChildren().add(entity("X")));
        }

        @Test
        void hasChildren_trueWhenHasChild() {
            var parent = entity("Parent");
            var child = entity("Child");
            child.setParent(parent);

            assertTrue(parent.hasChildren());
        }

        @Test
        void hasChildren_falseWhenEmpty() {
            var e = entity("E");
            assertFalse(e.hasChildren());
        }
    }

    // ========================================================================
    // SELECTION
    // ========================================================================

    @Nested
    class Selection {

        @Test
        void setSelectedEntity_singleSelection() {
            var e = entity("A");
            scene.addEntity(e);

            scene.setSelectedEntity(e);

            assertEquals(e, scene.getSelectedEntity());
            assertEquals(1, scene.getSelectedEntities().size());
        }

        @Test
        void setSelectedEntity_null_clearsSelection() {
            var e = entity("A");
            scene.addEntity(e);
            scene.setSelectedEntity(e);

            scene.setSelectedEntity(null);

            assertTrue(scene.getSelectedEntities().isEmpty());
        }

        @Test
        void getSelectedEntity_multipleSelected_returnsNull() {
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addToSelection(a);
            scene.addToSelection(b);

            assertNull(scene.getSelectedEntity());
        }

        @Test
        void toggleSelection_addsIfNotSelected() {
            var e = entity("A");
            scene.addEntity(e);

            scene.toggleSelection(e);

            assertTrue(scene.isSelected(e));
        }

        @Test
        void toggleSelection_removesIfSelected() {
            var e = entity("A");
            scene.addEntity(e);
            scene.addToSelection(e);

            scene.toggleSelection(e);

            assertFalse(scene.isSelected(e));
        }

        @Test
        void toggleSelection_null_ignored() {
            scene.toggleSelection(null); // no exception
        }

        @Test
        void addToSelection_null_ignored() {
            scene.addToSelection(null);
            assertTrue(scene.getSelectedEntities().isEmpty());
        }

        @Test
        void setSelection_replacesExisting() {
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.addToSelection(a);

            scene.setSelection(Set.of(b));

            assertFalse(scene.isSelected(a));
            assertTrue(scene.isSelected(b));
        }

        @Test
        void setSelection_null_clearsAll() {
            var e = entity("A");
            scene.addEntity(e);
            scene.addToSelection(e);

            scene.setSelection(null);

            assertTrue(scene.getSelectedEntities().isEmpty());
        }

        @Test
        void clearSelection_emptiesSet() {
            var e = entity("A");
            scene.addEntity(e);
            scene.addToSelection(e);

            scene.clearSelection();

            assertTrue(scene.getSelectedEntities().isEmpty());
        }

        @Test
        void getSelectedEntities_returnsUnmodifiable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> scene.getSelectedEntities().add(entity("X")));
        }
    }

    // ========================================================================
    // DIRTY STATE
    // ========================================================================

    @Nested
    class DirtyState {

        @Test
        void startsClean() {
            assertFalse(scene.isDirty());
        }

        @Test
        void addEntity_marksDirty() {
            scene.addEntity(entity("A"));
            assertTrue(scene.isDirty());
        }

        @Test
        void removeEntity_marksDirty() {
            var e = entity("A");
            scene.addEntity(e);
            scene.clearDirty();

            scene.removeEntity(e);
            assertTrue(scene.isDirty());
        }

        @Test
        void clearDirty_clears() {
            scene.addEntity(entity("A"));
            scene.clearDirty();
            assertFalse(scene.isDirty());
        }

        @Test
        void insertEntityAtPosition_marksDirty() {
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);
            scene.clearDirty();

            scene.insertEntityAtPosition(a, null, 1);

            assertTrue(scene.isDirty());
        }
    }

    // ========================================================================
    // FIND / LOOKUP
    // ========================================================================

    @Nested
    class FindAndLookup {

        @Test
        void getEntityById_found() {
            var e = entity("A");
            scene.addEntity(e);

            assertEquals(e, scene.getEntityById(e.getId()));
        }

        @Test
        void getEntityById_notFound() {
            assertNull(scene.getEntityById("nonexistent"));
        }

        @Test
        void findEntityAt_findsEntityAtPosition() {
            // Entity at (10, 20) with default 1x1 size and center pivot
            var e = entityAt("A", 10, 20);
            scene.addEntity(e);

            // Click at entity's position
            EditorGameObject found = scene.findEntityAt(10, 20);
            assertEquals(e, found);
        }

        @Test
        void findEntityAt_misses() {
            var e = entityAt("A", 10, 20);
            scene.addEntity(e);

            // Click far away
            assertNull(scene.findEntityAt(1000, 1000));
        }

        @Test
        void findEntityAt_returnsTopmost() {
            // Two entities at same position — later one is on top
            var bottom = entityAt("Bottom", 10, 20);
            var top = entityAt("Top", 10, 20);
            scene.addEntity(bottom);
            scene.addEntity(top);

            EditorGameObject found = scene.findEntityAt(10, 20);
            assertEquals(top, found);
        }
    }

    // ========================================================================
    // EDGE CASES / GREY AREAS
    // ========================================================================

    @Nested
    class EdgeCases {

        @Test
        void resolveHierarchy_withCircularParentIds_setsParent() {
            // Simulate circular parentId references from corrupt data
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);

            // Create cycle via parentId: A -> B -> A
            a.setParentId(b.getId());
            b.setParentId(a.getId());

            // resolveHierarchy uses setParent which has circular ref protection
            scene.resolveHierarchy();

            // One of them should succeed, the other should be rejected
            // The first one processed gets set; the second is rejected by isAncestorOf
            boolean aIsParentOfB = (b.getParent() == a);
            boolean bIsParentOfA = (a.getParent() == b);

            // Only one direction should be established, not both
            assertFalse(aIsParentOfB && bIsParentOfA,
                    "Circular reference should not be possible");
        }

        @Test
        void removeEntity_childrenRemovedFirst_parentIdCleared() {
            var parent = entity("Parent");
            var child = entity("Child");
            scene.addEntity(parent);
            scene.addEntity(child);
            child.setParent(parent);

            scene.removeEntity(parent);

            // Both removed, both parentId cleared
            assertNull(child.getParentId());
            assertNull(child.getParent());
            assertNull(parent.getParentId());
        }

        @Test
        void addEntity_afterRemove_sameEntityReAdded() {
            var e = entity("A");
            scene.addEntity(e);
            scene.removeEntity(e);
            scene.addEntity(e);

            assertEquals(1, scene.getEntities().size());
            assertTrue(scene.getEntities().contains(e));
        }

        @Test
        void insertEntityAtPosition_entityNotInScene_stillWorks() {
            // insertEntityAtPosition doesn't check if entity is in scene
            var a = entity("A");
            var b = entity("B");
            scene.addEntity(a);
            scene.addEntity(b);
            a.setOrder(0);
            b.setOrder(1);

            var outsider = entity("Outsider");
            // outsider is not in scene.entities but we try to insert it
            scene.insertEntityAtPosition(outsider, null, 0);

            // Should not crash, outsider gets order 0
            assertEquals(0, outsider.getOrder());
        }

        @Test
        void multipleChildrenRemoved_parentRemains() {
            var parent = entity("Parent");
            var c1 = entity("C1");
            var c2 = entity("C2");
            scene.addEntity(parent);
            scene.addEntity(c1);
            scene.addEntity(c2);
            c1.setParent(parent);
            c2.setParent(parent);

            scene.removeEntity(c1);
            scene.removeEntity(c2);

            assertEquals(1, scene.getEntities().size());
            assertFalse(parent.hasChildren());
        }

        @Test
        void getScale_defaultsToOneOneOne_forScratchEntity() {
            var e = entity("E");
            Vector3f scale = e.getScale();
            assertEquals(1f, scale.x, 0.001f);
            assertEquals(1f, scale.y, 0.001f);
            assertEquals(1f, scale.z, 0.001f);
        }

        @Test
        void entityEquality_basedOnId() {
            var a = entity("A");
            var b = entity("B");
            b.setId(a.getId());

            assertEquals(a, b); // same ID = equal
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        void regenerateId_changesId() {
            var e = entity("E");
            String oldId = e.getId();
            e.regenerateId();
            assertNotEquals(oldId, e.getId());
        }

        @Test
        void resetAllOverrides_doesNotInvalidateCache() {
            // This documents a known issue: resetAllOverrides() doesn't call
            // invalidateComponentCache(). For prefab instances, cached merged
            // components would be stale after this call.
            // Cannot test fully without PrefabRegistry, but we verify the method
            // doesn't crash on scratch entities.
            var e = entity("Scratch");
            e.resetAllOverrides(); // no exception
        }

        @Test
        void resetFieldToDefault_onlyInvalidatesCacheWhenComponentMapCleared() {
            // resetFieldToDefault only invalidates cache when the entire component
            // type's override map becomes empty. Removing one field while others
            // remain does NOT invalidate cache — this is a known issue.
            var e = entity("Scratch");
            // For scratch entities this is a no-op, just verify no crash
            e.resetFieldToDefault("com.pocket.rpg.components.core.Transform", "localPosition");
        }
    }
}
