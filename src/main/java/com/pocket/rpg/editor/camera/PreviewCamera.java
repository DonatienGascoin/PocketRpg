package com.pocket.rpg.editor.camera;

import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.rendering.core.RenderCamera;
import lombok.Getter;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Camera wrapper for preview rendering in the editor.
 * <p>
 * Ensures preview mode uses the exact same camera math as runtime,
 * fixing visual inconsistencies between preview and play mode.
 * <p>
 * Usage:
 * <pre>
 * PreviewCamera preview = new PreviewCamera(viewportConfig);
 * preview.applySceneSettings(position, orthographicSize);
 * renderer.beginWithMatrices(preview.getProjectionMatrix(), preview.getViewMatrix(), ...);
 * </pre>
 */
@Getter
public class PreviewCamera implements RenderCamera {

    private final GameCamera camera;

    /**
     * Creates a preview camera with the given viewport configuration.
     *
     * @param viewport Shared viewport config (must match runtime config)
     */
    public PreviewCamera(ViewportConfig viewport) {
        this.camera = new GameCamera(viewport);
    }

    /**
     * Creates a preview camera wrapping an existing GameCamera.
     *
     * @param camera The camera to wrap
     */
    public PreviewCamera(GameCamera camera) {
        this.camera = camera;
    }

    /**
     * Applies scene camera settings to match runtime behavior.
     * <p>
     * This method ensures the preview camera uses identical math to the runtime camera,
     * preventing zoom/position discrepancies between preview and play mode.
     *
     * @param position         Camera position in world coordinates
     * @param orthographicSize Half-height in world units
     */
    public void applySceneSettings(Vector2f position, float orthographicSize) {
        camera.setPosition(position.x, position.y);
        camera.setOrthographicSize(orthographicSize);
        camera.setZoom(1.0f);
    }

    /**
     * Applies scene camera settings with explicit zoom.
     *
     * @param position         Camera position in world coordinates
     * @param orthographicSize Half-height in world units
     * @param zoom             Additional zoom multiplier
     */
    public void applySceneSettings(Vector2f position, float orthographicSize, float zoom) {
        camera.setPosition(position.x, position.y);
        camera.setOrthographicSize(orthographicSize);
        camera.setZoom(zoom);
    }

    public void setPosition(float x, float y) {
        camera.setPosition(x, y);
    }

    public void setPosition(Vector2f position) {
        camera.setPosition(position.x, position.y);
    }

    public void setOrthographicSize(float orthographicSize) {
        camera.setOrthographicSize(orthographicSize);
    }

    public void setZoom(float zoom) {
        camera.setZoom(zoom);
    }

    public Vector3f getPosition() {
        return camera.getPosition();
    }

    public float getOrthographicSize() {
        return camera.getOrthographicSize();
    }

    public float getZoom() {
        return camera.getZoom();
    }

    // ========================================================================
    // RenderCamera interface
    // ========================================================================

    @Override
    public Matrix4f getProjectionMatrix() {
        return camera.getProjectionMatrix();
    }

    @Override
    public Matrix4f getViewMatrix() {
        return camera.getViewMatrix();
    }

    @Override
    public float[] getWorldBounds() {
        return camera.getWorldBounds();
    }

    @Override
    public Vector2f worldToScreen(float worldX, float worldY) {
        return camera.worldToScreen(worldX, worldY);
    }

    @Override
    public Vector3f screenToWorld(float screenX, float screenY) {
        return camera.screenToWorld(screenX, screenY);
    }

    @Override
    public String toString() {
        return String.format("PreviewCamera[%s]", camera.toString());
    }
}
