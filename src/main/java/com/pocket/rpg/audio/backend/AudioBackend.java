package com.pocket.rpg.audio.backend;

import com.pocket.rpg.audio.clips.AudioClip;
import org.joml.Vector3f;

/**
 * Platform abstraction for audio playback.
 * Implementations handle the low-level audio API (OpenAL, etc.).
 */
public interface AudioBackend {

    /**
     * Initialize the audio backend.
     */
    void initialize();

    /**
     * Shutdown the audio backend and release all resources.
     */
    void destroy();

    /**
     * Create an audio buffer from raw PCM data.
     *
     * @param data       Raw PCM audio data
     * @param format     Audio format (mono8, mono16, stereo8, stereo16)
     * @param sampleRate Sample rate in Hz
     * @return Buffer ID for the created buffer
     */
    int createBuffer(short[] data, int format, int sampleRate);

    /**
     * Delete an audio buffer.
     *
     * @param bufferId Buffer ID to delete
     */
    void deleteBuffer(int bufferId);

    /**
     * Create a new audio source.
     *
     * @return Source ID for the created source
     */
    int createSource();

    /**
     * Delete an audio source.
     *
     * @param sourceId Source ID to delete
     */
    void deleteSource(int sourceId);

    /**
     * Attach a buffer to a source.
     *
     * @param sourceId Source ID
     * @param bufferId Buffer ID
     */
    void setSourceBuffer(int sourceId, int bufferId);

    /**
     * Play a source.
     *
     * @param sourceId Source ID to play
     */
    void playSource(int sourceId);

    /**
     * Pause a source.
     *
     * @param sourceId Source ID to pause
     */
    void pauseSource(int sourceId);

    /**
     * Stop a source.
     *
     * @param sourceId Source ID to stop
     */
    void stopSource(int sourceId);

    /**
     * Check if a source is currently playing.
     *
     * @param sourceId Source ID to check
     * @return true if playing
     */
    boolean isSourcePlaying(int sourceId);

    /**
     * Set source volume (gain).
     *
     * @param sourceId Source ID
     * @param volume   Volume (0.0 to 1.0+)
     */
    void setSourceVolume(int sourceId, float volume);

    /**
     * Set source pitch.
     *
     * @param sourceId Source ID
     * @param pitch    Pitch multiplier (1.0 = normal)
     */
    void setSourcePitch(int sourceId, float pitch);

    /**
     * Set source looping.
     *
     * @param sourceId Source ID
     * @param loop     true to loop
     */
    void setSourceLooping(int sourceId, boolean loop);

    /**
     * Set source 3D position.
     *
     * @param sourceId Source ID
     * @param position World position
     */
    void setSourcePosition(int sourceId, Vector3f position);

    /**
     * Set distance attenuation parameters.
     *
     * @param sourceId      Source ID
     * @param minDistance   Distance at which attenuation begins
     * @param maxDistance   Distance at which sound is inaudible
     * @param rolloffFactor Attenuation curve steepness
     */
    void setSourceAttenuation(int sourceId, float minDistance, float maxDistance, float rolloffFactor);

    /**
     * Get the current playback position in seconds.
     *
     * @param sourceId Source ID
     * @return Playback position in seconds
     */
    float getSourcePlaybackTime(int sourceId);

    /**
     * Set the playback position in seconds.
     *
     * @param sourceId Source ID
     * @param seconds  Position in seconds
     */
    void setSourcePlaybackTime(int sourceId, float seconds);

    /**
     * Set the listener position (usually camera or player).
     *
     * @param position World position
     */
    void setListenerPosition(Vector3f position);

    /**
     * Set the listener orientation.
     *
     * @param forward Forward direction vector
     * @param up      Up direction vector
     */
    void setListenerOrientation(Vector3f forward, Vector3f up);

    /**
     * Get the audio format constant for mono 16-bit.
     */
    int getFormatMono16();

    /**
     * Get the audio format constant for stereo 16-bit.
     */
    int getFormatStereo16();
}
