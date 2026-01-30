package com.pocket.rpg.editor.scene;

import com.pocket.rpg.serialization.SceneData;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;

/**
 * Camera configuration for a scene.
 * <p>
 * Defines the initial camera state when the scene loads at runtime,
 * including position and initial bounds zone reference.
 */
public class SceneCameraSettings {

    /**
     * Initial camera position in world coordinates.
     */
    @Getter
    private final Vector2f position = new Vector2f(0, 0);

    // Note: Orthographic size is now controlled globally via RenderingConfig

    /**
     * ID of the CameraBoundsZone to activate on fresh scene load.
     * Leave empty for no bounds.
     */
    @Getter
    @Setter
    private String initialBoundsId = "";

    /**
     * Sets the camera position.
     */
    public void setPosition(float x, float y) {
        position.set(x, y);
    }

    /**
     * Converts to serializable CameraData.
     */
    public SceneData.CameraData toData() {
        // Note: orthographicSize is set to 0 as it's now controlled via RenderingConfig
        SceneData.CameraData data = new SceneData.CameraData(
                position.x, position.y, 0, 0
        );
        data.setInitialBoundsId(initialBoundsId);
        return data;
    }

    /**
     * Loads from serialized CameraData.
     */
    public void fromData(SceneData.CameraData data) {
        if (data == null) {
            return;
        }

        float[] pos = data.getPosition();
        if (pos != null && pos.length >= 2) {
            position.set(pos[0], pos[1]);
        }

        // Note: orthographicSize from data is ignored - now controlled via RenderingConfig

        // Load initialBoundsId (new field)
        String boundsId = data.getInitialBoundsId();
        initialBoundsId = boundsId != null ? boundsId : "";
    }

    /**
     * Resets to default values.
     */
    public void reset() {
        position.set(0, 0);
        initialBoundsId = "";
    }

    @Override
    public String toString() {
        return String.format("SceneCameraSettings[pos=(%.1f,%.1f), initialBoundsId=%s]",
                position.x, position.y,
                initialBoundsId.isEmpty() ? "none" : initialBoundsId);
    }
}
