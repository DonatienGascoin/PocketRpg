package com.pocket.rpg.animation;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

/**
 * Stateful animation playback controller.
 * <p>
 * Handles frame timing, looping, and state management for a single animation.
 * Used by both AnimationComponent and AnimatorComponent to avoid code duplication.
 * <p>
 * This class does NOT update sprites directly - it provides the current frame
 * which the owning component uses to update a SpriteRenderer.
 */
public class AnimationPlayer {

    // ========================================================================
    // PLAYBACK STATE ENUM
    // ========================================================================

    public enum PlaybackState {
        STOPPED,    // No animation, timer at 0
        PLAYING,    // Advancing frames
        PAUSED,     // Timer frozen, current frame visible
        FINISHED    // Non-looping animation completed (stays on last frame)
    }

    // ========================================================================
    // STATE
    // ========================================================================

    @Getter
    private Animation animation;

    @Getter
    private int currentFrame = 0;

    @Getter
    private float timer = 0;

    @Getter
    private PlaybackState state = PlaybackState.STOPPED;

    @Getter
    @Setter
    private float speed = 1.0f;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public AnimationPlayer() {
    }

    public AnimationPlayer(Animation animation) {
        this.animation = animation;
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Updates the animation timer and advances frames as needed.
     *
     * @param deltaTime Time since last update in seconds
     * @return true if the frame changed this update
     */
    public boolean update(float deltaTime) {
        if (state != PlaybackState.PLAYING) return false;
        if (animation == null || animation.getFrameCount() == 0) return false;

        int previousFrame = currentFrame;

        // Advance timer
        timer += deltaTime * speed;

        AnimationFrame frame = animation.getFrame(currentFrame);

        // Check if frame duration exceeded
        while (timer >= frame.duration()) {
            timer -= frame.duration();
            currentFrame++;

            // Handle end of animation
            if (currentFrame >= animation.getFrameCount()) {
                if (animation.isLooping()) {
                    currentFrame = 0;
                } else {
                    currentFrame = animation.getFrameCount() - 1;
                    state = PlaybackState.FINISHED;
                    return currentFrame != previousFrame;
                }
            }

            frame = animation.getFrame(currentFrame);
        }

        return currentFrame != previousFrame;
    }

    // ========================================================================
    // PLAYBACK CONTROL
    // ========================================================================

    /**
     * Starts or restarts the animation from the beginning.
     */
    public void play() {
        if (animation == null || animation.getFrameCount() == 0) return;

        currentFrame = 0;
        timer = 0;
        state = PlaybackState.PLAYING;
    }

    /**
     * Plays a different animation.
     *
     * @param animation The animation to play
     */
    public void setAnimation(Animation animation) {
        this.animation = animation;
        play();
    }

    /**
     * Sets and plays an animation loaded by path.
     *
     * @param path Asset path to the animation
     */
    public void setAnimation(String path) {
        if (path == null || path.isBlank()) {
            stop();
            return;
        }
        this.animation = Assets.load(path, Animation.class);
        play();
    }

    /**
     * Sets the animation without starting playback.
     *
     * @param animation The animation to set
     */
    public void setAnimationWithoutPlaying(Animation animation) {
        this.animation = animation;
        currentFrame = 0;
        timer = 0;
        state = PlaybackState.STOPPED;
    }

    /**
     * Pauses playback at current frame.
     */
    public void pause() {
        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
        }
    }

    /**
     * Resumes playback from current position.
     */
    public void resume() {
        if (state == PlaybackState.PAUSED) {
            state = PlaybackState.PLAYING;
        }
    }

    /**
     * Stops playback and resets to first frame.
     */
    public void stop() {
        currentFrame = 0;
        timer = 0;
        state = PlaybackState.STOPPED;
    }

    /**
     * Restarts the animation from the beginning.
     */
    public void restart() {
        play();
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    public boolean isPlaying() {
        return state == PlaybackState.PLAYING;
    }

    public boolean isPaused() {
        return state == PlaybackState.PAUSED;
    }

    public boolean isFinished() {
        return state == PlaybackState.FINISHED;
    }

    public boolean isStopped() {
        return state == PlaybackState.STOPPED;
    }

    /**
     * Gets the sprite for the current frame.
     *
     * @return Current frame's sprite, or null if no animation/frames
     */
    public Sprite getCurrentSprite() {
        if (animation == null || animation.getFrameCount() == 0) {
            return null;
        }
        return animation.getFrameSprite(currentFrame);
    }

    /**
     * Gets normalized progress (0.0 to 1.0) through the animation.
     */
    public float getProgress() {
        if (animation == null || animation.getFrameCount() == 0) return 0;

        float totalDuration = animation.getTotalDuration();
        if (totalDuration <= 0) return 0;

        float elapsed = 0;
        for (int i = 0; i < currentFrame; i++) {
            elapsed += animation.getFrame(i).duration();
        }
        elapsed += timer;

        return Math.min(1.0f, elapsed / totalDuration);
    }

    /**
     * Checks if this player has an animation set.
     */
    public boolean hasAnimation() {
        return animation != null && animation.getFrameCount() > 0;
    }
}
