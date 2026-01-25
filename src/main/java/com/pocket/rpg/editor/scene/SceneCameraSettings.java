package com.pocket.rpg.editor.scene;

import com.pocket.rpg.serialization.SceneData;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Camera configuration for a scene.
 * <p>
 * Defines the initial camera state when the scene loads at runtime,
 * including position, zoom level, follow behavior, and bounds.
 */
public class SceneCameraSettings {

    /**
     * Initial camera position in world coordinates.
     */
    @Getter
    private final Vector2f position = new Vector2f(0, 0);

    // Note: Orthographic size is now controlled globally via RenderingConfig

    /**
     * Whether the camera should follow a target entity.
     */
    @Getter
    @Setter
    private boolean followPlayer = true;

    /**
     * Name of the entity to follow (if followPlayer is true).
     */
    @Getter
    @Setter
    private String followTargetName = "Player";

    /**
     * Whether camera movement should be clamped to bounds.
     */
    @Getter
    @Setter
    private boolean useBounds = false;

    /**
     * Camera bounds (minX, minY, maxX, maxY).
     */
    @Getter
    private final Vector4f bounds = new Vector4f(0, 0, 20, 15);

    /**
     * Sets the camera position.
     */
    public void setPosition(float x, float y) {
        position.set(x, y);
    }

    /**
     * Sets the camera bounds.
     */
    public void setBounds(float minX, float minY, float maxX, float maxY) {
        bounds.set(minX, minY, maxX, maxY);
    }

    /**
     * Converts to serializable CameraData.
     */
    public SceneData.CameraData toData() {
        // Note: orthographicSize is set to 0 as it's now controlled via RenderingConfig
        SceneData.CameraData data = new SceneData.CameraData(
                position.x, position.y, 0, 0
        );
        data.setFollowPlayer(followPlayer);
        data.setFollowTargetName(followTargetName);
        data.setUseBounds(useBounds);
        data.setBounds(new float[]{bounds.x, bounds.y, bounds.z, bounds.w});
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
        followPlayer = data.isFollowPlayer();

        String target = data.getFollowTargetName();
        if (target != null) {
            followTargetName = target;
        }

        useBounds = data.isUseBounds();

        float[] b = data.getBounds();
        if (b != null && b.length >= 4) {
            bounds.set(b[0], b[1], b[2], b[3]);
        }
    }

    /**
     * Resets to default values.
     */
    public void reset() {
        position.set(0, 0);
        followPlayer = true;
        followTargetName = "Player";
        useBounds = false;
        bounds.set(0, 0, 20, 15);
    }

    @Override
    public String toString() {
        return String.format("SceneCameraSettings[pos=(%.1f,%.1f), follow=%s, bounds=%s]",
                position.x, position.y,
                followPlayer ? followTargetName : "none",
                useBounds ? String.format("(%.1f,%.1f,%.1f,%.1f)", bounds.x, bounds.y, bounds.z, bounds.w) : "none");
    }
}
