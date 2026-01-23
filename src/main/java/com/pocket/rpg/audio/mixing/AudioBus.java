package com.pocket.rpg.audio.mixing;

import lombok.Getter;
import lombok.Setter;

/**
 * Per-channel settings container.
 * Manages volume, mute state, and fade transitions for a single channel.
 */
public class AudioBus {

    @Getter
    private float volume;

    @Getter
    @Setter
    private boolean muted = false;

    @Getter
    @Setter
    private boolean paused = false;

    // Fade state
    private float targetVolume;
    private float fadeSpeed;
    private boolean fading = false;
    private Runnable onFadeComplete;

    public AudioBus(float initialVolume) {
        this.volume = clamp(initialVolume, 0f, 1f);
        this.targetVolume = this.volume;
    }

    /**
     * Set volume immediately (cancels any fade).
     */
    public void setVolume(float volume) {
        this.volume = clamp(volume, 0f, 1f);
        this.targetVolume = this.volume;
        this.fading = false;
        this.onFadeComplete = null;
    }

    /**
     * Fade to a target volume over time.
     */
    public void fadeToVolume(float target, float duration) {
        fadeToVolume(target, duration, null);
    }

    /**
     * Fade to a target volume over time with a callback on completion.
     */
    public void fadeToVolume(float target, float duration, Runnable onComplete) {
        this.targetVolume = clamp(target, 0f, 1f);
        if (duration <= 0) {
            this.volume = this.targetVolume;
            this.fading = false;
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        this.fadeSpeed = Math.abs(targetVolume - volume) / duration;
        this.fading = true;
        this.onFadeComplete = onComplete;
    }

    /**
     * Update fade transition.
     *
     * @param deltaTime Time since last update
     */
    public void update(float deltaTime) {
        if (!fading) {
            return;
        }

        if (volume < targetVolume) {
            volume = Math.min(volume + fadeSpeed * deltaTime, targetVolume);
        } else if (volume > targetVolume) {
            volume = Math.max(volume - fadeSpeed * deltaTime, targetVolume);
        }

        if (Math.abs(volume - targetVolume) < 0.001f) {
            volume = targetVolume;
            fading = false;
            if (onFadeComplete != null) {
                onFadeComplete.run();
                onFadeComplete = null;
            }
        }
    }

    /**
     * Get effective volume considering mute state.
     */
    public float getEffectiveVolume() {
        return muted ? 0f : volume;
    }

    /**
     * @return true if currently fading
     */
    public boolean isFading() {
        return fading;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
