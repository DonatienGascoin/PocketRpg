package com.pocket.rpg.audio.sources;

import com.pocket.rpg.audio.backend.AudioBackend;
import com.pocket.rpg.audio.clips.AudioClip;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Handle to a playing sound, allowing control of in-flight audio.
 * Returned by Audio.playOneShot() and similar methods.
 */
public class AudioHandle {

    private final AudioBackend backend;
    @Getter
    private final int sourceId;
    @Getter
    private final AudioClip clip;
    private final long playStartTime;

    private boolean valid = true;
    private boolean stopped = false;

    public AudioHandle(AudioBackend backend, int sourceId, AudioClip clip) {
        this.backend = backend;
        this.sourceId = sourceId;
        this.clip = clip;
        this.playStartTime = System.currentTimeMillis();
    }

    /**
     * @return true if this handle is still valid (not stopped or recycled)
     */
    public boolean isValid() {
        return valid && !stopped;
    }

    /**
     * @return true if the sound is currently playing
     */
    public boolean isPlaying() {
        if (!isValid()) {
            return false;
        }
        return backend.isSourcePlaying(sourceId);
    }

    /**
     * Stop playback immediately.
     */
    public void stop() {
        if (!isValid()) {
            return;
        }
        backend.stopSource(sourceId);
        stopped = true;
    }

    /**
     * Pause playback.
     */
    public void pause() {
        if (!isValid()) {
            return;
        }
        backend.pauseSource(sourceId);
    }

    /**
     * Resume playback after pause.
     */
    public void resume() {
        if (!isValid()) {
            return;
        }
        backend.playSource(sourceId);
    }

    /**
     * Set the volume of this playing sound.
     *
     * @param volume Volume (0.0 to 1.0+)
     */
    public void setVolume(float volume) {
        if (!isValid()) {
            return;
        }
        backend.setSourceVolume(sourceId, volume);
    }

    /**
     * Set the pitch of this playing sound.
     *
     * @param pitch Pitch multiplier (1.0 = normal)
     */
    public void setPitch(float pitch) {
        if (!isValid()) {
            return;
        }
        backend.setSourcePitch(sourceId, pitch);
    }

    /**
     * Set the 3D position of this playing sound.
     *
     * @param position World position
     */
    public void setPosition(Vector3f position) {
        if (!isValid()) {
            return;
        }
        backend.setSourcePosition(sourceId, position);
    }

    /**
     * Get the current playback time in seconds.
     *
     * @return Playback position in seconds
     */
    public float getPlaybackTime() {
        if (!isValid()) {
            return 0f;
        }
        return backend.getSourcePlaybackTime(sourceId);
    }

    /**
     * Set the playback position in seconds.
     *
     * @param seconds Position in seconds
     */
    public void setTime(float seconds) {
        if (!isValid()) {
            return;
        }
        backend.setSourcePlaybackTime(sourceId, seconds);
    }

    /**
     * Mark this handle as invalid (called when source is recycled).
     */
    public void invalidate() {
        valid = false;
    }
}
