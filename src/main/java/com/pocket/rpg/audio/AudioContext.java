package com.pocket.rpg.audio;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.audio.music.MusicPlayer;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import org.joml.Vector3f;

/**
 * Interface for audio system operations.
 * Allows different implementations for game runtime, editor, and testing.
 */
public interface AudioContext {

    /**
     * Initialize the audio context.
     */
    void initialize();

    /**
     * Destroy the audio context and release resources.
     */
    void destroy();

    /**
     * Update the audio context (called each frame).
     */
    void update(float deltaTime);

    // ========================================================================
    // ONE-SHOT PLAYBACK
    // ========================================================================

    /**
     * Play a one-shot sound.
     */
    AudioHandle playOneShot(AudioClip clip, float volume, AudioChannel channel);

    /**
     * Play a one-shot sound at a 3D position.
     */
    AudioHandle playOneShotAt(AudioClip clip, Vector3f position, float volume, AudioChannel channel);

    /**
     * Play a one-shot sound with full settings.
     */
    AudioHandle playOneShot(AudioClip clip, PlaybackSettings settings);

    // ========================================================================
    // GLOBAL CONTROL
    // ========================================================================

    /**
     * Pause all playing sounds.
     */
    void pauseAll();

    /**
     * Resume all paused sounds.
     */
    void resumeAll();

    /**
     * Stop all playing sounds.
     */
    void stopAll();

    /**
     * Stop all sounds on a specific channel.
     */
    void stopChannel(AudioChannel channel);

    // ========================================================================
    // SUBSYSTEMS
    // ========================================================================

    /**
     * Get the music player for background music control.
     */
    MusicPlayer getMusicPlayer();

    /**
     * Get the audio mixer for volume control.
     */
    AudioMixer getMixer();

    /**
     * Get the audio engine for low-level operations.
     */
    AudioEngine getEngine();
}
