package com.pocket.rpg.components.audio;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import org.joml.Vector3f;

/**
 * Component that defines the "ears" of the game for 3D audio.
 * Usually attached to the camera or player GameObject.
 * <p>
 * Only one AudioListener can be active at a time.
 */
@ComponentMeta(category = "Audio")
public class AudioListener extends Component {

    private static AudioListener activeListener;

    // Reusable vectors for updates
    private final Vector3f forward = new Vector3f();
    private final Vector3f up = new Vector3f();

    public AudioListener() {
        // Required for serialization
    }

    // ========================================================================
    // COMPONENT LIFECYCLE
    // ========================================================================

    @Override
    protected void onEnable() {
        // Only one listener active at a time
        if (activeListener != null && activeListener != this) {
            activeListener.setEnabled(false);
        }
        activeListener = this;
    }

    @Override
    protected void onDisable() {
        if (activeListener == this) {
            activeListener = null;
        }
    }

    @Override
    public void lateUpdate(float deltaTime) {
        if (!Audio.isInitialized()) {
            return;
        }

        // Update listener position from transform
        Vector3f pos = getTransform().getWorldPosition();
        Audio.getEngine().setListenerPosition(pos);

        // Update listener orientation
        // For 2D games, forward is typically (0, 0, -1) and up is (0, 1, 0)
        forward.set(0, 0, -1);
        up.set(0, 1, 0);

        // If the transform has rotation, apply it (use Z rotation for 2D)
        float rotation = getTransform().getRotation().z;
        if (rotation != 0) {
            float cos = (float) Math.cos(Math.toRadians(rotation));
            float sin = (float) Math.sin(Math.toRadians(rotation));
            forward.set(-sin, 0, -cos);
        }

        Audio.getEngine().setListenerOrientation(forward, up);
    }

    @Override
    protected void onDestroy() {
        if (activeListener == this) {
            activeListener = null;
        }
    }

    // ========================================================================
    // STATIC ACCESS
    // ========================================================================

    /**
     * @return the currently active AudioListener, or null
     */
    public static AudioListener getActive() {
        return activeListener;
    }

    /**
     * @return the listener's world position, or zero if no listener
     */
    public static Vector3f getListenerPosition() {
        if (activeListener != null) {
            return activeListener.getTransform().getWorldPosition();
        }
        return new Vector3f(0, 0, 0);
    }
}
