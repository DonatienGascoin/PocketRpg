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
 * Component for playing sounds attached to GameObjects.
 * Supports both 2D and 3D spatial audio.
 */
@ComponentMeta(category = "Audio")
public class AudioSource extends Component {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /**
     * The audio clip to play.
     */
    @Getter
    @Setter
    private AudioClip clip;

    /**
     * Volume (0-1).
     */
    @Getter
    private float volume = 1.0f;

    /**
     * Pitch multiplier (1.0 = normal).
     */
    @Getter
    private float pitch = 1.0f;

    /**
     * Whether to loop playback.
     */
    @Getter
    @Setter
    private boolean loop = false;

    /**
     * Whether to start playing when the GameObject is enabled.
     */
    @Getter
    @Setter
    private boolean playOnStart = false;

    /**
     * Whether to use 3D spatial audio.
     */
    @Getter
    @Setter
    private boolean spatialize = true;

    /**
     * Distance at which the sound is at full volume.
     */
    @Getter
    @Setter
    private float minDistance = 1.0f;

    /**
     * Distance at which the sound is inaudible.
     */
    @Getter
    @Setter
    private float maxDistance = 500.0f;

    /**
     * Attenuation curve steepness.
     */
    @Getter
    @Setter
    private float rolloffFactor = 1.0f;

    /**
     * Audio channel for mixing.
     */
    @Getter
    @Setter
    private AudioChannel channel = AudioChannel.SFX;

    /**
     * Priority for voice stealing (0 = highest, 255 = lowest).
     */
    @Getter
    @Setter
    private int priority = 128;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private AudioHandle currentHandle;

    // ========================================================================
    // COMPONENT LIFECYCLE
    // ========================================================================

    public AudioSource() {
        // Required for serialization
    }

    @Override
    protected void onEnable() {
        if (playOnStart && clip != null) {
            play();
        }
    }

    @Override
    protected void onDisable() {
        if (currentHandle != null && currentHandle.isPlaying()) {
            pause();
        }
    }

    @Override
    public void update(float deltaTime) {
        // Update 3D position if spatialize is enabled
        if (spatialize && currentHandle != null && currentHandle.isPlaying()) {
            Vector3f worldPos = getTransform().getWorldPosition();
            currentHandle.setPosition(worldPos);
        }
    }

    @Override
    protected void onDestroy() {
        stop();
    }

    // ========================================================================
    // PLAYBACK CONTROL
    // ========================================================================

    /**
     * Start playing the audio clip.
     */
    public void play() {
        if (clip == null || !Audio.isInitialized()) {
            return;
        }

        stop(); // Stop any existing playback

        PlaybackSettings settings = new PlaybackSettings()
                .volume(volume)
                .pitch(pitch)
                .channel(channel)
                .loop(loop)
                .priority(priority)
                .minDistance(minDistance)
                .maxDistance(maxDistance)
                .rolloff(rolloffFactor);

        if (spatialize && clip.supports3D()) {
            settings.position(getTransform().getWorldPosition());
        }

        currentHandle = Audio.playOneShot(clip, settings);
    }

    /**
     * Play a one-shot sound (doesn't affect the main clip).
     */
    public void playOneShot(AudioClip clip) {
        playOneShot(clip, 1.0f);
    }

    /**
     * Play a one-shot sound with volume.
     */
    public void playOneShot(AudioClip clip, float volume) {
        if (clip == null || !Audio.isInitialized()) {
            return;
        }

        PlaybackSettings settings = new PlaybackSettings()
                .volume(this.volume * volume)
                .pitch(pitch)
                .channel(channel)
                .priority(priority);

        if (spatialize && clip.supports3D()) {
            settings.position(getTransform().getWorldPosition());
        }

        Audio.playOneShot(clip, settings);
    }

    /**
     * Pause playback.
     */
    public void pause() {
        if (currentHandle != null) {
            currentHandle.pause();
        }
    }

    /**
     * Resume playback.
     */
    public void resume() {
        if (currentHandle != null) {
            currentHandle.resume();
        }
    }

    /**
     * Stop playback.
     */
    public void stop() {
        if (currentHandle != null) {
            currentHandle.stop();
            currentHandle = null;
        }
    }

    /**
     * Set playback time in seconds.
     */
    public void setTime(float seconds) {
        if (currentHandle != null) {
            currentHandle.setTime(seconds);
        }
    }

    // ========================================================================
    // STATE QUERIES
    // ========================================================================

    /**
     * @return true if currently playing
     */
    public boolean isPlaying() {
        return currentHandle != null && currentHandle.isPlaying();
    }

    /**
     * @return current playback time in seconds
     */
    public float getTime() {
        return currentHandle != null ? currentHandle.getPlaybackTime() : 0f;
    }

    // ========================================================================
    // SETTERS WITH LIVE UPDATE
    // ========================================================================

    /**
     * Set volume (updates live if playing).
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        if (currentHandle != null) {
            currentHandle.setVolume(this.volume);
        }
    }

    /**
     * Set pitch (updates live if playing).
     */
    public void setPitch(float pitch) {
        this.pitch = Math.max(0.01f, pitch);
        if (currentHandle != null) {
            currentHandle.setPitch(this.pitch);
        }
    }
}
