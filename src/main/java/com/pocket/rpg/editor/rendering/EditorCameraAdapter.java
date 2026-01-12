package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.rendering.core.RenderCamera;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Adapter that wraps EditorCamera to implement RenderCamera interface.
 * Allows EditorCamera to be used with RenderPipeline.
 */
public class EditorCameraAdapter implements RenderCamera {

    private final EditorCamera editorCamera;

    public EditorCameraAdapter(EditorCamera editorCamera) {
        this.editorCamera = editorCamera;
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        return editorCamera.getProjectionMatrix();
    }

    @Override
    public Matrix4f getViewMatrix() {
        return editorCamera.getViewMatrix();
    }

    @Override
    public float[] getWorldBounds() {
        return editorCamera.getWorldBounds();
    }

    @Override
    public Vector2f worldToScreen(float worldX, float worldY) {
        return editorCamera.worldToScreen(worldX, worldY);
    }

    @Override
    public Vector3f screenToWorld(float screenX, float screenY) {
        return editorCamera.screenToWorld(screenX, screenY);
    }

    /**
     * Gets the wrapped EditorCamera.
     */
    public EditorCamera getEditorCamera() {
        return editorCamera;
    }
}
