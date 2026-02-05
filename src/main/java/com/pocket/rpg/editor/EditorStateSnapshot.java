package com.pocket.rpg.editor;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of editor UI state that should be preserved across
 * operations like scene reload.
 * <p>
 * Captures state that is NOT part of the scene data itself (camera position,
 * selection, dirty flag) and can restore it to a new scene instance.
 */
public record EditorStateSnapshot(
    String scenePath,
    boolean dirty,
    Vector3f cameraPosition,
    float cameraZoom,
    List<String> selectedEntityIds
) {
    /**
     * Captures the current editor state.
     */
    public static EditorStateSnapshot capture(EditorContext context) {
        EditorScene scene = context.getCurrentScene();
        EditorCamera camera = context.getCamera();
        EditorSelectionManager selection = context.getSelectionManager();

        List<String> selectedIds = selection.getSelectedEntities().stream()
            .map(EditorGameObject::getId)
            .toList();

        return new EditorStateSnapshot(
            scene.getFilePath(),
            scene.isDirty(),
            camera.getPosition(),   // returns copy (Vector3f)
            camera.getZoom(),
            selectedIds
        );
    }

    /**
     * Restores editor state to a newly loaded scene.
     * Must be called AFTER context.setCurrentScene(newScene).
     */
    public void restore(EditorContext context, EditorScene newScene) {
        // Restore camera
        EditorCamera camera = context.getCamera();
        camera.setPosition(cameraPosition);
        camera.setZoom(cameraZoom);

        // Restore selection — use selectEntities() with all at once.
        // selectEntity() replaces the selection each call; calling it in a
        // loop would only keep the last entity.
        EditorSelectionManager selection = context.getSelectionManager();
        Set<EditorGameObject> entitiesToSelect = new LinkedHashSet<>();
        for (String id : selectedEntityIds) {
            EditorGameObject entity = newScene.getEntityById(id);
            if (entity != null) {
                entitiesToSelect.add(entity);
            }
        }
        if (!entitiesToSelect.isEmpty()) {
            selection.selectEntities(entitiesToSelect);
        }

        // Restore dirty flag.
        // fromSceneData() calls clearDirty(), so if it was dirty before
        // we re-mark it. If it was clean, it stays clean.
        if (dirty) {
            newScene.markDirty();
        }
    }
}
