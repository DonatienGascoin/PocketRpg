package com.pocket.rpg.editor;

import com.pocket.rpg.core.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayModeSelectionManagerTest {

    private PlayModeSelectionManager manager;

    @BeforeEach
    void setUp() {
        manager = new PlayModeSelectionManager();
    }

    // ========================================================================
    // BASIC SELECTION
    // ========================================================================

    @Nested
    class BasicSelection {

        @Test
        void initialState_isEmpty() {
            assertTrue(manager.getSelectedObjects().isEmpty());
            assertFalse(manager.isCameraSelected());
            assertNull(manager.getSingleSelected());
        }

        @Test
        void select_singleObject() {
            GameObject obj = new GameObject("Player");
            manager.select(obj);

            assertEquals(1, manager.getSelectedObjects().size());
            assertTrue(manager.isSelected(obj));
            assertEquals(obj, manager.getSingleSelected());
            assertFalse(manager.isCameraSelected());
        }

        @Test
        void select_replacesExisting() {
            GameObject first = new GameObject("First");
            GameObject second = new GameObject("Second");

            manager.select(first);
            manager.select(second);

            assertEquals(1, manager.getSelectedObjects().size());
            assertFalse(manager.isSelected(first));
            assertTrue(manager.isSelected(second));
        }

        @Test
        void select_null_clearsSelection() {
            GameObject obj = new GameObject("Player");
            manager.select(obj);
            manager.select(null);

            assertTrue(manager.getSelectedObjects().isEmpty());
            assertNull(manager.getSingleSelected());
        }

        @Test
        void select_clearsCameraSelection() {
            manager.selectCamera();
            assertTrue(manager.isCameraSelected());

            manager.select(new GameObject("Player"));
            assertFalse(manager.isCameraSelected());
        }
    }

    // ========================================================================
    // CAMERA SELECTION
    // ========================================================================

    @Nested
    class CameraSelection {

        @Test
        void selectCamera_setsFlag() {
            manager.selectCamera();

            assertTrue(manager.isCameraSelected());
            assertTrue(manager.getSelectedObjects().isEmpty());
        }

        @Test
        void selectCamera_clearsObjectSelection() {
            manager.select(new GameObject("Player"));
            manager.selectCamera();

            assertTrue(manager.isCameraSelected());
            assertTrue(manager.getSelectedObjects().isEmpty());
        }
    }

    // ========================================================================
    // TOGGLE SELECTION
    // ========================================================================

    @Nested
    class ToggleSelection {

        @Test
        void toggle_addsIfNotPresent() {
            GameObject obj = new GameObject("Player");
            manager.toggleSelection(obj);

            assertTrue(manager.isSelected(obj));
        }

        @Test
        void toggle_removesIfPresent() {
            GameObject obj = new GameObject("Player");
            manager.select(obj);
            manager.toggleSelection(obj);

            assertFalse(manager.isSelected(obj));
            assertTrue(manager.getSelectedObjects().isEmpty());
        }

        @Test
        void toggle_allowsMultipleObjects() {
            GameObject a = new GameObject("A");
            GameObject b = new GameObject("B");

            manager.toggleSelection(a);
            manager.toggleSelection(b);

            assertEquals(2, manager.getSelectedObjects().size());
            assertTrue(manager.isSelected(a));
            assertTrue(manager.isSelected(b));
            assertNull(manager.getSingleSelected()); // Multiple selected
        }

        @Test
        void toggle_clearsCameraSelection() {
            manager.selectCamera();
            manager.toggleSelection(new GameObject("Player"));

            assertFalse(manager.isCameraSelected());
        }

        @Test
        void toggle_null_isIgnored() {
            manager.select(new GameObject("Player"));
            manager.toggleSelection(null);

            assertEquals(1, manager.getSelectedObjects().size());
        }
    }

    // ========================================================================
    // CLEAR
    // ========================================================================

    @Nested
    class ClearSelection {

        @Test
        void clear_removesAllObjectsAndCamera() {
            manager.select(new GameObject("Player"));
            manager.selectCamera(); // Also sets camera
            // selectCamera clears objects, so select again to have both states set:
            // Actually selectCamera clears objects. Let's test independently.

            manager.selectCamera();
            manager.clearSelection();

            assertFalse(manager.isCameraSelected());
            assertTrue(manager.getSelectedObjects().isEmpty());
        }

        @Test
        void clear_removesObjects() {
            manager.toggleSelection(new GameObject("A"));
            manager.toggleSelection(new GameObject("B"));
            manager.clearSelection();

            assertTrue(manager.getSelectedObjects().isEmpty());
        }
    }

    // ========================================================================
    // PRUNE DESTROYED
    // ========================================================================

    @Nested
    class PruneDestroyed {

        @Test
        void prune_removesDestroyedObjects() {
            GameObject alive = new GameObject("Alive");
            GameObject dying = new GameObject("Dying");

            manager.toggleSelection(alive);
            manager.toggleSelection(dying);
            assertEquals(2, manager.getSelectedObjects().size());

            dying.destroy();
            manager.pruneDestroyedObjects();

            assertEquals(1, manager.getSelectedObjects().size());
            assertTrue(manager.isSelected(alive));
            assertFalse(manager.isSelected(dying));
        }

        @Test
        void prune_noOpWhenNoneDestroyed() {
            GameObject obj = new GameObject("Alive");
            manager.select(obj);

            manager.pruneDestroyedObjects();

            assertEquals(1, manager.getSelectedObjects().size());
            assertTrue(manager.isSelected(obj));
        }

        @Test
        void prune_emptiesSelectionIfAllDestroyed() {
            GameObject obj = new GameObject("Doomed");
            manager.select(obj);
            obj.destroy();

            manager.pruneDestroyedObjects();

            assertTrue(manager.getSelectedObjects().isEmpty());
        }
    }
}
