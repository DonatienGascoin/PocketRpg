package com.pocket.rpg.components;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Trigger-based ambient audio that plays when the listener enters a zone.
 * The zone is defined by a radius around the GameObject's position.
 */
@ComponentMeta(category = "Audio")
public class AmbientZone extends Component {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /**
     * The ambient audio clip to play.
     */
    @Getter
    @Setter
    private AudioClip ambientClip;

    /**
     * Volume when fully inside the zone.
     */
    @Getter
    @Setter
    private float volume = 1.0f;

    /**
     * Fade in duration in seconds.
     */
    @Getter
    @Setter
    private float fadeInTime = 1.0f;

    /**
     * Fade out duration in seconds.
     */
    @Getter
    @Setter
    private float fadeOutTime = 1.0f;

    /**
     * Zone radius (listener must be within this distance).
     */
    @Getter
    @Setter
    private float radius = 10.0f;

    /**
     * Priority for voice stealing.
     */
    @Getter
    @Setter
    private int priority = 200; // Low priority for ambient

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private AudioHandle ambientHandle;

    @HideInInspector
    private boolean listenerInside = false;

    @HideInInspector
    private boolean isFadingOut = false;

    public AmbientZone() {
        // Required for serialization
    }

    // ========================================================================
    // COMPONENT LIFECYCLE
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (!Audio.isInitialized()) {
            return;
        }

        boolean wasInside = listenerInside;
        listenerInside = isListenerInZone();

        if (listenerInside && !wasInside && !isFadingOut) {
            // Entered zone
            startAmbient();
        } else if (!listenerInside && wasInside) {
            // Exited zone
            stopAmbient();
        }
    }

    @Override
    protected void onDestroy() {
        if (ambientHandle != null) {
            ambientHandle.stop();
            ambientHandle = null;
        }
    }

    // ========================================================================
    // ZONE DETECTION
    // ========================================================================

    private boolean isListenerInZone() {
        Vector3f listenerPos = AudioListener.getListenerPosition();
        Vector3f zonePos = getTransform().getWorldPosition();

        float dx = listenerPos.x - zonePos.x;
        float dy = listenerPos.y - zonePos.y;
        float distSq = dx * dx + dy * dy;

        return distSq <= radius * radius;
    }

    // ========================================================================
    // PLAYBACK CONTROL
    // ========================================================================

    private void startAmbient() {
        if (ambientClip == null) {
            return;
        }

        // Stop any existing handle
        if (ambientHandle != null) {
            ambientHandle.stop();
        }

        ambientHandle = Audio.playOneShot(ambientClip, new PlaybackSettings()
                .volume(0f) // Start silent
                .channel(AudioChannel.AMBIENT)
                .loop(true)
                .priority(priority));

        if (ambientHandle != null) {
            // Fade in
            if (fadeInTime > 0) {
                fadeToVolume(volume, fadeInTime);
            } else {
                ambientHandle.setVolume(volume);
            }
        }

        isFadingOut = false;
    }

    private void stopAmbient() {
        if (ambientHandle == null || !ambientHandle.isPlaying()) {
            ambientHandle = null;
            return;
        }

        if (fadeOutTime > 0) {
            isFadingOut = true;
            fadeToVolume(0f, fadeOutTime);
            // Note: We'll need to stop after fade completes
            // For simplicity, we start a background check
        } else {
            ambientHandle.stop();
            ambientHandle = null;
        }
    }

    private void fadeToVolume(float target, float duration) {
        if (ambientHandle == null) {
            return;
        }

        // Simple linear fade - would be better with proper fade tracking
        Audio.getEngine().fadeVolume(ambientHandle, target, duration, () -> {
            if (target <= 0 && ambientHandle != null) {
                ambientHandle.stop();
                ambientHandle = null;
                isFadingOut = false;
            }
        });
    }

    // ========================================================================
    // PUBLIC CONTROL
    // ========================================================================

    /**
     * Manually start the ambient sound.
     */
    public void forceStart() {
        isFadingOut = false;
        startAmbient();
    }

    /**
     * Manually stop the ambient sound.
     */
    public void forceStop() {
        if (ambientHandle != null) {
            ambientHandle.stop();
            ambientHandle = null;
        }
        isFadingOut = false;
    }

    /**
     * @return true if ambient is currently playing
     */
    public boolean isPlaying() {
        return ambientHandle != null && ambientHandle.isPlaying();
    }
}
