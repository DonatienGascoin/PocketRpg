package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.editor.camera.PreviewCamera;
import com.pocket.rpg.rendering.core.RenderCamera;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Adapter that wraps PreviewCamera to implement RenderCamera interface.
 * Used by GameViewPanel for static preview rendering.
 */
public class PreviewCameraAdapter implements RenderCamera {

    private final PreviewCamera previewCamera;

    public PreviewCameraAdapter(PreviewCamera previewCamera) {
        this.previewCamera = previewCamera;
    }

    @Override
    public Matrix4f getProjectionMatrix() {
        return previewCamera.getProjectionMatrix();
    }

    @Override
    public Matrix4f getViewMatrix() {
        return previewCamera.getViewMatrix();
    }

    @Override
    public float[] getWorldBounds() {
        return previewCamera.getWorldBounds();
    }

    @Override
    public Vector2f worldToScreen(float worldX, float worldY) {
        return previewCamera.worldToScreen(worldX, worldY);
    }

    @Override
    public Vector3f screenToWorld(float screenX, float screenY) {
        return previewCamera.screenToWorld(screenX, screenY);
    }

    /**
     * Gets the wrapped PreviewCamera.
     */
    public PreviewCamera getPreviewCamera() {
        return previewCamera;
    }
}
