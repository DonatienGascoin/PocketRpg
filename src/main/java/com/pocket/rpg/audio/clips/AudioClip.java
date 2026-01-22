package com.pocket.rpg.audio.clips;

import lombok.Getter;

/**
 * Audio data container representing a loaded sound.
 * Wraps an OpenAL buffer with metadata about the audio.
 */
@Getter
public class AudioClip {

    private final String name;
    private final String path;
    private final int bufferId;
    private final float duration;
    private final int sampleRate;
    private final int channels;

    /**
     * Create a new AudioClip.
     *
     * @param name       Display name (usually filename without extension)
     * @param path       Relative path to the audio file
     * @param bufferId   OpenAL buffer ID
     * @param duration   Duration in seconds
     * @param sampleRate Sample rate in Hz
     * @param channels   Number of channels (1 = mono, 2 = stereo)
     */
    public AudioClip(String name, String path, int bufferId, float duration, int sampleRate, int channels) {
        this.name = name;
        this.path = path;
        this.bufferId = bufferId;
        this.duration = duration;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    /**
     * @return true if this clip is mono (single channel)
     */
    public boolean isMono() {
        return channels == 1;
    }

    /**
     * @return true if this clip is stereo (two channels)
     */
    public boolean isStereo() {
        return channels == 2;
    }

    /**
     * 3D audio requires mono clips for proper spatialization.
     *
     * @return true if this clip supports 3D audio positioning
     */
    public boolean supports3D() {
        return isMono();
    }

    @Override
    public String toString() {
        return String.format("AudioClip[%s, %.2fs, %s, %dHz]",
                name, duration, isMono() ? "mono" : "stereo", sampleRate);
    }
}
