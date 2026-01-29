package com.pocket.rpg.audio.music;

import com.pocket.rpg.audio.AudioEngine;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import lombok.Getter;

/**
 * Wrapper for music playback with fade control.
 */
public class MusicTrack {

    @Getter
    private final AudioClip clip;

    private AudioHandle handle;

    private float volume = 1.0f;
    private float targetVolume = 1.0f;
    private float fadeSpeed = 0f;
    private Runnable onFadeComplete;

    public MusicTrack(AudioClip clip) {
        this.clip = clip;
    }

    /**
     * Start playback.
     */
    public void play(AudioEngine engine) {
        handle = engine.play(clip, new PlaybackSettings()
                .channel(AudioChannel.MUSIC)
                .loop(true)
                .volume(volume));
        // Remove from engine tracking - MusicPlayer manages this handle
        if (handle != null) {
            engine.removeFromTracking(handle);
        }
    }

    /**
     * Stop playback.
     */
    public void stop() {
        if (handle != null) {
            handle.stop();
            handle = null;
        }
    }

    /**
     * Pause playback.
     */
    public void pause() {
        if (handle != null) {
            handle.pause();
        }
    }

    /**
     * Resume playback.
     */
    public void resume() {
        if (handle != null) {
            handle.resume();
        }
    }

    /**
     * Set volume immediately.
     */
    public void setVolume(float vol) {
        this.volume = vol;
        this.targetVolume = vol;
        this.fadeSpeed = 0f;
        if (handle != null) {
            handle.setVolume(vol);
        }
    }

    /**
     * Get current volume.
     */
    public float getVolume() {
        return volume;
    }

    /**
     * Fade to target volume.
     */
    public void fadeTo(float target, float duration) {
        fadeTo(target, duration, null);
    }

    /**
     * Fade to target volume with callback.
     */
    public void fadeTo(float target, float duration, Runnable onComplete) {
        this.targetVolume = target;
        if (duration <= 0) {
            this.volume = target;
            this.fadeSpeed = 0f;
            if (handle != null) {
                handle.setVolume(volume);
            }
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        this.fadeSpeed = Math.abs(target - volume) / duration;
        this.onFadeComplete = onComplete;
    }

    /**
     * Update fade transition.
     */
    public void update(float deltaTime) {
        if (fadeSpeed <= 0) {
            return;
        }

        if (volume < targetVolume) {
            volume = Math.min(volume + fadeSpeed * deltaTime, targetVolume);
        } else {
            volume = Math.max(volume - fadeSpeed * deltaTime, targetVolume);
        }

        if (handle != null) {
            handle.setVolume(volume);
        }

        if (Math.abs(volume - targetVolume) < 0.001f) {
            volume = targetVolume;
            fadeSpeed = 0f;
            if (onFadeComplete != null) {
                onFadeComplete.run();
                onFadeComplete = null;
            }
        }
    }

    /**
     * @return true if playing
     */
    public boolean isPlaying() {
        return handle != null && handle.isPlaying();
    }

    /**
     * @return playback position in seconds
     */
    public float getPosition() {
        return handle != null ? handle.getPlaybackTime() : 0f;
    }

    /**
     * Set playback position.
     */
    public void setPosition(float seconds) {
        if (handle != null) {
            handle.setTime(seconds);
        }
    }
}
