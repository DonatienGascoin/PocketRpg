package com.pocket.rpg.audio.sources;

import com.pocket.rpg.audio.mixing.AudioChannel;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Builder for complex playback configuration.
 * Used with Audio.playOneShot(clip, settings).
 */
@Getter
public class PlaybackSettings {

    private float volume = 1.0f;
    private float pitch = 1.0f;
    private AudioChannel channel = AudioChannel.SFX;
    private Vector3f position = null;  // null = 2D
    private boolean loop = false;
    private float delay = 0f;
    private float minDistance = 1.0f;
    private float maxDistance = 500.0f;
    private float rolloffFactor = 1.0f;
    private int priority = 128; // 0 = highest, 255 = lowest

    public PlaybackSettings volume(float v) {
        this.volume = v;
        return this;
    }

    public PlaybackSettings pitch(float p) {
        this.pitch = p;
        return this;
    }

    public PlaybackSettings channel(AudioChannel c) {
        this.channel = c;
        return this;
    }

    public PlaybackSettings position(Vector3f pos) {
        this.position = pos;
        return this;
    }

    public PlaybackSettings position(float x, float y, float z) {
        this.position = new Vector3f(x, y, z);
        return this;
    }

    public PlaybackSettings loop(boolean l) {
        this.loop = l;
        return this;
    }

    public PlaybackSettings delay(float d) {
        this.delay = d;
        return this;
    }

    public PlaybackSettings minDistance(float d) {
        this.minDistance = d;
        return this;
    }

    public PlaybackSettings maxDistance(float d) {
        this.maxDistance = d;
        return this;
    }

    public PlaybackSettings rolloff(float r) {
        this.rolloffFactor = r;
        return this;
    }

    public PlaybackSettings priority(int p) {
        this.priority = Math.max(0, Math.min(255, p));
        return this;
    }

    /**
     * @return true if this is a 3D positioned sound
     */
    public boolean is3D() {
        return position != null;
    }

    // Convenience factory methods

    /**
     * Create settings for a 3D positioned sound.
     */
    public static PlaybackSettings at(Vector3f position) {
        return new PlaybackSettings().position(position);
    }

    /**
     * Create settings for a 3D positioned sound.
     */
    public static PlaybackSettings at(float x, float y, float z) {
        return new PlaybackSettings().position(x, y, z);
    }

    /**
     * Create settings for a looping sound.
     */
    public static PlaybackSettings looping() {
        return new PlaybackSettings().loop(true);
    }

    /**
     * Create default settings.
     */
    public static PlaybackSettings defaults() {
        return new PlaybackSettings();
    }
}
