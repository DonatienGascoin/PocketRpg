package com.pocket.rpg.editor.scene;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that EditorScene selection operations preserve insertion order.
 * Selection uses LinkedHashSet internally so iteration order matches insertion order.
 */
class EditorSceneSelectionOrderTest {

    private EditorScene scene;
    private EditorGameObject a, b, c, d, e;

    @BeforeEach
    void setUp() {
        scene = new EditorScene();
        a = new EditorGameObject("A", new Vector3f(0, 0, 0), false);
        b = new EditorGameObject("B", new Vector3f(1, 0, 0), false);
        c = new EditorGameObject("C", new Vector3f(2, 0, 0), false);
        d = new EditorGameObject("D", new Vector3f(3, 0, 0), false);
        e = new EditorGameObject("E", new Vector3f(4, 0, 0), false);
        scene.addEntity(a);
        scene.addEntity(b);
        scene.addEntity(c);
        scene.addEntity(d);
        scene.addEntity(e);
    }

    private static List<EditorGameObject> toList(Set<EditorGameObject> set) {
        return new ArrayList<>(set);
    }

    // ========================================================================
    // INSERTION ORDER PRESERVATION
    // ========================================================================

    @Nested
    class InsertionOrderTests {

        @Test
        void addToSelection_preservesInsertionOrder() {
            scene.addToSelection(a);
            scene.addToSelection(b);
            scene.addToSelection(c);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(a, b, c), result);
        }

        @Test
        void setSelection_preservesIterationOrderOfInput() {
            Set<EditorGameObject> input = new LinkedHashSet<>();
            input.add(c);
            input.add(a);
            input.add(b);

            scene.setSelection(input);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(c, a, b), result);
        }

        @Test
        void setSelectedEntity_singleElement() {
            scene.setSelectedEntity(b);

            Set<EditorGameObject> selected = scene.getSelectedEntities();
            assertEquals(1, selected.size());
            assertEquals(b, selected.iterator().next());
        }

        @Test
        void addToSelection_fiveEntities_preservesFullOrder() {
            scene.addToSelection(e);
            scene.addToSelection(c);
            scene.addToSelection(a);
            scene.addToSelection(d);
            scene.addToSelection(b);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(e, c, a, d, b), result);
        }
    }

    // ========================================================================
    // TOGGLE PRESERVES REMAINING ORDER
    // ========================================================================

    @Nested
    class ToggleTests {

        @Test
        void toggleSelection_removeMiddle_preservesOrder() {
            scene.addToSelection(a);
            scene.addToSelection(b);
            scene.addToSelection(c);

            scene.toggleSelection(b);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(a, c), result);
        }

        @Test
        void toggleSelection_addNew_appendsAtEnd() {
            scene.addToSelection(a);
            scene.addToSelection(b);

            scene.toggleSelection(c);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(a, b, c), result);
        }
    }

    // ========================================================================
    // CLEAR AND RE-SELECT
    // ========================================================================

    @Nested
    class ClearAndReselectTests {

        @Test
        void clearSelection_thenReselect_newOrder() {
            scene.addToSelection(a);
            scene.addToSelection(b);

            scene.clearSelection();

            scene.addToSelection(b);
            scene.addToSelection(a);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(b, a), result);
        }
    }

    // ========================================================================
    // UNMODIFIABLE VIEW
    // ========================================================================

    @Nested
    class UnmodifiableViewTests {

        @Test
        void getSelectedEntities_isUnmodifiable() {
            scene.addToSelection(a);

            Set<EditorGameObject> selected = scene.getSelectedEntities();
            assertThrows(UnsupportedOperationException.class, () -> selected.add(b));
        }

        @Test
        void getSelectedEntities_reflectsInsertionOrder() {
            scene.addToSelection(d);
            scene.addToSelection(b);
            scene.addToSelection(e);
            scene.addToSelection(a);
            scene.addToSelection(c);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(d, b, e, a, c), result);
        }
    }

    // ========================================================================
    // INTERACTION WITH OTHER OPERATIONS
    // ========================================================================

    @Nested
    class InteractionTests {

        @Test
        void removeEntity_removesFromSelection_preservesOrder() {
            scene.addToSelection(a);
            scene.addToSelection(b);
            scene.addToSelection(c);

            scene.removeEntity(b);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(a, c), result);
        }

        @Test
        void addDuplicate_doesNotChangeOrder() {
            scene.addToSelection(a);
            scene.addToSelection(b);
            scene.addToSelection(c);

            // Adding a again should not move it to the end
            scene.addToSelection(a);

            List<EditorGameObject> result = toList(scene.getSelectedEntities());
            assertEquals(List.of(a, b, c), result);
        }
    }
}
