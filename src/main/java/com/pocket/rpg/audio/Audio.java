package com.pocket.rpg.audio;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.audio.music.MusicPlayer;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import org.joml.Vector3f;

/**
 * Static facade for audio operations.
 * Follows the pattern of Input, Time, and other engine facades.
 * <p>
 * Usage:
 * <pre>
 * // Simple one-shot
 * Audio.playOneShot(explosionClip);
 *
 * // With volume
 * Audio.playOneShot(footstepClip, 0.5f);
 *
 * // At position (3D)
 * Audio.playOneShot(gunfireClip, enemyPosition);
 *
 * // Full control
 * Audio.playOneShot(clip, new PlaybackSettings()
 *     .volume(0.8f)
 *     .pitch(1.2f)
 *     .channel(AudioChannel.SFX)
 *     .position(worldPos));
 *
 * // Music
 * Audio.music().play(bgmClip, 2.0f); // 2 second fade in
 * Audio.music().crossfadeTo(newTrack, 1.5f);
 *
 * // Volume control
 * Audio.setMasterVolume(0.8f);
 * Audio.setChannelVolume(AudioChannel.SFX, 0.5f);
 * </pre>
 */
public final class Audio {

    private static AudioContext context;

    private Audio() {
    } // Prevent instantiation

    /**
     * Initialize the audio system with a context.
     */
    public static void initialize(AudioContext audioContext) {
        if (context != null) {
            context.destroy();
        }
        context = audioContext;
        context.initialize();
    }

    /**
     * Destroy the audio system.
     */
    public static void destroy() {
        if (context != null) {
            context.destroy();
            context = null;
        }
    }

    /**
     * Check if audio is initialized.
     */
    public static boolean isInitialized() {
        return context != null;
    }

    // ========================================================================
    // ONE-SHOT SOUNDS
    // ========================================================================

    /**
     * Play a one-shot sound on the SFX channel at full volume.
     */
    public static AudioHandle playOneShot(AudioClip clip) {
        if (context == null) return null;
        return context.playOneShot(clip, 1.0f, AudioChannel.SFX);
    }

    /**
     * Play a one-shot sound on the SFX channel with volume.
     */
    public static AudioHandle playOneShot(AudioClip clip, float volume) {
        if (context == null) return null;
        return context.playOneShot(clip, volume, AudioChannel.SFX);
    }

    /**
     * Play a one-shot sound at a 3D position on the SFX channel.
     */
    public static AudioHandle playOneShot(AudioClip clip, Vector3f position) {
        if (context == null) return null;
        return context.playOneShotAt(clip, position, 1.0f, AudioChannel.SFX);
    }

    /**
     * Play a one-shot sound at a 3D position with volume.
     */
    public static AudioHandle playOneShot(AudioClip clip, Vector3f position, float volume) {
        if (context == null) return null;
        return context.playOneShotAt(clip, position, volume, AudioChannel.SFX);
    }

    /**
     * Play a one-shot sound with full control via PlaybackSettings.
     */
    public static AudioHandle playOneShot(AudioClip clip, PlaybackSettings settings) {
        if (context == null) return null;
        return context.playOneShot(clip, settings);
    }

    // ========================================================================
    // MUSIC
    // ========================================================================

    /**
     * Get the music player for background music control.
     */
    public static MusicPlayer music() {
        return context != null ? context.getMusicPlayer() : null;
    }

    // ========================================================================
    // VOLUME CONTROL
    // ========================================================================

    /**
     * Set the master volume (affects all audio).
     */
    public static void setMasterVolume(float volume) {
        if (context != null) {
            context.getMixer().setVolume(AudioChannel.MASTER, volume);
        }
    }

    /**
     * Get the master volume.
     */
    public static float getMasterVolume() {
        return context != null ? context.getMixer().getVolume(AudioChannel.MASTER) : 0f;
    }

    /**
     * Set volume for a specific channel.
     */
    public static void setChannelVolume(AudioChannel channel, float volume) {
        if (context != null) {
            context.getMixer().setVolume(channel, volume);
        }
    }

    /**
     * Get volume for a specific channel.
     */
    public static float getChannelVolume(AudioChannel channel) {
        return context != null ? context.getMixer().getVolume(channel) : 0f;
    }

    /**
     * Mute a channel.
     */
    public static void muteChannel(AudioChannel channel) {
        if (context != null) {
            context.getMixer().mute(channel);
        }
    }

    /**
     * Unmute a channel.
     */
    public static void unmuteChannel(AudioChannel channel) {
        if (context != null) {
            context.getMixer().unmute(channel);
        }
    }

    /**
     * Check if a channel is muted.
     */
    public static boolean isChannelMuted(AudioChannel channel) {
        return context != null && context.getMixer().isMuted(channel);
    }

    // ========================================================================
    // GLOBAL CONTROL
    // ========================================================================

    /**
     * Pause all playing sounds.
     */
    public static void pauseAll() {
        if (context != null) {
            context.pauseAll();
        }
    }

    /**
     * Resume all paused sounds.
     */
    public static void resumeAll() {
        if (context != null) {
            context.resumeAll();
        }
    }

    /**
     * Stop all playing sounds.
     */
    public static void stopAll() {
        if (context != null) {
            context.stopAll();
        }
    }

    /**
     * Stop all sounds on a specific channel.
     */
    public static void stopChannel(AudioChannel channel) {
        if (context != null) {
            context.stopChannel(channel);
        }
    }

    /**
     * Pause a specific channel.
     */
    public static void pauseChannel(AudioChannel channel) {
        if (context != null) {
            context.getMixer().pauseChannel(channel);
        }
    }

    /**
     * Resume a specific channel.
     */
    public static void resumeChannel(AudioChannel channel) {
        if (context != null) {
            context.getMixer().resumeChannel(channel);
        }
    }

    // ========================================================================
    // ENGINE ACCESS (for components)
    // ========================================================================

    /**
     * Get the audio engine for low-level operations.
     * Used by AudioSource and AudioListener components.
     */
    public static AudioEngine getEngine() {
        return context != null ? context.getEngine() : null;
    }

    /**
     * Get the audio mixer.
     */
    public static AudioMixer getMixer() {
        return context != null ? context.getMixer() : null;
    }

    // ========================================================================
    // UPDATE (called by game loop)
    // ========================================================================

    /**
     * Update audio system (called each frame).
     */
    public static void update(float deltaTime) {
        if (context != null) {
            context.update(deltaTime);
        }
    }
}
