package com.pocket.rpg.editor;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EditorStateSnapshotTest {

    private EditorContext context;
    private EditorCamera camera;
    private EditorScene scene;

    @BeforeEach
    void setUp() {
        camera = new EditorCamera(16f, 0.1f, 10f, 200f, 0.1f);
        context = new TestEditorContext(camera);
        scene = new EditorScene();
        context.setCurrentScene(scene);
    }

    // ========================================================================
    // CAPTURE
    // ========================================================================

    @Test
    void capture_recordsCameraPosition() {
        camera.setPosition(5f, 10f);

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertEquals(5f, snapshot.cameraPosition().x, 0.001f);
        assertEquals(10f, snapshot.cameraPosition().y, 0.001f);
    }

    @Test
    void capture_recordsCameraZoom() {
        camera.setZoom(2.5f);

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertEquals(2.5f, snapshot.cameraZoom(), 0.001f);
    }

    @Test
    void capture_recordsSelectedEntityIds() {
        EditorGameObject entity1 = new EditorGameObject("Entity1", new Vector3f(), false);
        EditorGameObject entity2 = new EditorGameObject("Entity2", new Vector3f(), false);
        scene.addEntity(entity1);
        scene.addEntity(entity2);
        context.getSelectionManager().selectEntities(Set.of(entity1, entity2));

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertEquals(2, snapshot.selectedEntityIds().size());
        assertTrue(snapshot.selectedEntityIds().contains(entity1.getId()));
        assertTrue(snapshot.selectedEntityIds().contains(entity2.getId()));
    }

    @Test
    void capture_recordsScenePath() {
        scene.setFilePath("scenes/test.scene");

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertEquals("scenes/test.scene", snapshot.scenePath());
    }

    @Test
    void capture_recordsDirtyFlag() {
        scene.markDirty();

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertTrue(snapshot.dirty());
    }

    @Test
    void capture_recordsCleanFlag() {
        scene.clearDirty();

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertFalse(snapshot.dirty());
    }

    @Test
    void capture_createsDefensiveCopyOfCameraPosition() {
        camera.setPosition(1f, 2f);

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        // Mutate original camera
        camera.setPosition(99f, 99f);

        // Snapshot should still have original values
        assertEquals(1f, snapshot.cameraPosition().x, 0.001f);
        assertEquals(2f, snapshot.cameraPosition().y, 0.001f);
    }

    @Test
    void capture_emptySelection() {
        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertTrue(snapshot.selectedEntityIds().isEmpty());
    }

    // ========================================================================
    // RESTORE
    // ========================================================================

    @Test
    void restore_setsCameraPosition() {
        camera.setPosition(5f, 10f);
        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        // Move camera away
        camera.setPosition(0f, 0f);

        // Restore
        snapshot.restore(context, scene);

        Vector3f pos = camera.getPosition();
        assertEquals(5f, pos.x, 0.001f);
        assertEquals(10f, pos.y, 0.001f);
    }

    @Test
    void restore_setsCameraZoom() {
        camera.setZoom(3f);
        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        camera.setZoom(1f);
        snapshot.restore(context, scene);

        assertEquals(3f, camera.getZoom(), 0.001f);
    }

    @Test
    void restore_selectsEntitiesById() {
        EditorGameObject entity1 = new EditorGameObject("E1", new Vector3f(), false);
        EditorGameObject entity2 = new EditorGameObject("E2", new Vector3f(), false);
        scene.addEntity(entity1);
        scene.addEntity(entity2);
        context.getSelectionManager().selectEntities(Set.of(entity1));

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        // Clear selection
        context.getSelectionManager().clearSelection();
        assertTrue(context.getSelectionManager().getSelectedEntities().isEmpty());

        // Restore on same scene (entities still have same IDs)
        snapshot.restore(context, scene);

        Set<EditorGameObject> selected = context.getSelectionManager().getSelectedEntities();
        assertEquals(1, selected.size());
        assertTrue(selected.contains(entity1));
    }

    @Test
    void restore_missingEntities_doesNotCrash() {
        EditorGameObject entity = new EditorGameObject("E1", new Vector3f(), false);
        scene.addEntity(entity);
        context.getSelectionManager().selectEntity(entity);

        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        // Create a new scene without that entity
        EditorScene newScene = new EditorScene();
        context.setCurrentScene(newScene);

        assertDoesNotThrow(() -> snapshot.restore(context, newScene));
    }

    @Test
    void restore_emptySelection_doesNotCrash() {
        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        assertDoesNotThrow(() -> snapshot.restore(context, scene));
    }

    @Test
    void restore_dirtyFlag_restoresDirty() {
        scene.markDirty();
        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        scene.clearDirty();
        assertFalse(scene.isDirty());

        snapshot.restore(context, scene);

        assertTrue(scene.isDirty());
    }

    @Test
    void restore_cleanFlag_staysClean() {
        scene.clearDirty();
        EditorStateSnapshot snapshot = EditorStateSnapshot.capture(context);

        snapshot.restore(context, scene);

        assertFalse(scene.isDirty());
    }

    // ========================================================================
    // TEST HELPERS
    // ========================================================================

    /**
     * Minimal EditorContext subclass that avoids needing EditorConfig,
     * EditorWindow, and other heavy dependencies.
     */
    private static class TestEditorContext extends EditorContext {
        TestEditorContext(EditorCamera camera) {
            // Use the field-based init path: set camera and selectionManager directly
            // by calling init with minimal args. We can't call super.init() because
            // it requires EditorConfig, EditorWindow, etc.
            // Instead, set fields via reflection to avoid pulling in the full editor.
            try {
                var cameraField = EditorContext.class.getDeclaredField("camera");
                cameraField.setAccessible(true);
                cameraField.set(this, camera);

                var selectionField = EditorContext.class.getDeclaredField("selectionManager");
                selectionField.setAccessible(true);
                selectionField.set(this, new EditorSelectionManager());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set up test context", e);
            }
        }
    }
}
