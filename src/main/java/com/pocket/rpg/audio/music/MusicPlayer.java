package com.pocket.rpg.audio.music;

import com.pocket.rpg.audio.AudioEngine;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.mixing.AudioChannel;
import com.pocket.rpg.audio.mixing.AudioMixer;
import com.pocket.rpg.audio.sources.PlaybackSettings;
import lombok.Getter;
import lombok.Setter;

/**
 * Dedicated system for background music with crossfading.
 */
public class MusicPlayer {

    private final AudioEngine engine;
    private final AudioMixer mixer;

    private MusicTrack currentTrack;
    private MusicTrack nextTrack;

    @Getter
    @Setter
    private float crossfadeDuration = 2.0f;

    private CrossfadeController crossfadeController;

    @Getter
    private float volume = 1.0f;

    private boolean paused = false;

    public MusicPlayer(AudioEngine engine, AudioMixer mixer) {
        this.engine = engine;
        this.mixer = mixer;
        this.crossfadeController = new CrossfadeController();
    }

    // ========================================================================
    // BASIC PLAYBACK
    // ========================================================================

    /**
     * Play music immediately (no fade).
     */
    public void play(AudioClip music) {
        play(music, 0f);
    }

    /**
     * Play music with fade in.
     */
    public void play(AudioClip music, float fadeIn) {
        stop(0f);

        if (music == null) {
            return;
        }

        currentTrack = new MusicTrack(music);
        currentTrack.play(engine);

        if (fadeIn > 0) {
            currentTrack.setVolume(0f);
            currentTrack.fadeTo(calculateVolume(), fadeIn);
        } else {
            currentTrack.setVolume(calculateVolume());
        }
    }

    /**
     * Stop music immediately.
     */
    public void stop() {
        stop(0f);
    }

    /**
     * Stop music with fade out.
     */
    public void stop(float fadeOut) {
        if (currentTrack == null) {
            return;
        }

        if (fadeOut > 0) {
            MusicTrack trackToStop = currentTrack;
            currentTrack.fadeTo(0f, fadeOut, trackToStop::stop);
            currentTrack = null;
        } else {
            currentTrack.stop();
            currentTrack = null;
        }
    }

    /**
     * Pause music.
     */
    public void pause() {
        if (currentTrack != null) {
            currentTrack.pause();
            paused = true;
        }
    }

    /**
     * Resume music.
     */
    public void resume() {
        if (currentTrack != null && paused) {
            currentTrack.resume();
            paused = false;
        }
    }

    // ========================================================================
    // CROSSFADE
    // ========================================================================

    /**
     * Crossfade to new music.
     */
    public void crossfadeTo(AudioClip newMusic) {
        crossfadeTo(newMusic, crossfadeDuration);
    }

    /**
     * Crossfade to new music with custom duration.
     */
    public void crossfadeTo(AudioClip newMusic, float duration) {
        if (currentTrack == null) {
            play(newMusic, duration);
            return;
        }

        if (newMusic == null) {
            stop(duration);
            return;
        }

        nextTrack = new MusicTrack(newMusic);
        nextTrack.setVolume(0f);
        nextTrack.play(engine);

        crossfadeController.start(
                currentTrack,
                nextTrack,
                duration,
                (oldTrack, newTrack) -> {
                    oldTrack.stop();
                    currentTrack = newTrack;
                    this.nextTrack = null;
                }
        );
    }

    // ========================================================================
    // VOLUME
    // ========================================================================

    /**
     * Set music player volume (relative to channel volume).
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        if (currentTrack != null && !crossfadeController.isActive()) {
            currentTrack.setVolume(calculateVolume());
        }
    }

    private float calculateVolume() {
        return mixer.calculateFinalVolume(AudioChannel.MUSIC, volume);
    }

    // ========================================================================
    // STATE
    // ========================================================================

    /**
     * @return true if music is playing
     */
    public boolean isPlaying() {
        return currentTrack != null && currentTrack.isPlaying();
    }

    /**
     * @return true if music is paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * @return the currently playing clip, or null
     */
    public AudioClip getCurrentClip() {
        return currentTrack != null ? currentTrack.getClip() : null;
    }

    /**
     * @return current playback position in seconds
     */
    public float getPlaybackPosition() {
        return currentTrack != null ? currentTrack.getPosition() : 0f;
    }

    /**
     * Set playback position.
     */
    public void setPlaybackPosition(float seconds) {
        if (currentTrack != null) {
            currentTrack.setPosition(seconds);
        }
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Update music player (called each frame).
     */
    public void update(float deltaTime) {
        if (crossfadeController.isActive()) {
            crossfadeController.update(deltaTime);
        }

        if (currentTrack != null) {
            currentTrack.update(deltaTime);
        }

        if (nextTrack != null) {
            nextTrack.update(deltaTime);
        }
    }
}
