package com.pocket.rpg.components;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

/**
 * Component that plays animations on a GameObject.
 * Requires a SpriteRenderer component on the same GameObject.
 * <p>
 * The Animation field is automatically serialized as a path string
 * via AssetReferenceTypeAdapterFactory, and can be selected using
 * the asset picker in the inspector.
 */
@ComponentMeta(category = "Rendering")
public class AnimationComponent extends Component {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /**
     * The animation asset to play.
     * Serialized as path string (e.g., "animations/player_walk.anim")
     * via AssetReferenceTypeAdapterFactory.
     */
    @Getter
    @Setter
    private Animation animation;

    /**
     * Whether to start playing automatically when scene loads.
     */
    @Getter
    @Setter
    private boolean autoPlay = true;

    /**
     * Playback speed multiplier (1.0 = normal speed).
     */
    @Getter
    private float speed = 1.0f;

    // ========================================================================
    // RUNTIME STATE (not serialized)
    // ========================================================================

    @HideInInspector
    private int currentFrame = 0;

    @HideInInspector
    private float timer = 0;

    @HideInInspector
    @Getter
    private AnimationState state = AnimationState.STOPPED;

    // ========================================================================
    // COMPONENT REFERENCE (auto-resolved, not serialized)
    // ========================================================================

    @ComponentRef
    private SpriteRenderer spriteRenderer;

    // ========================================================================
    // STATE ENUM
    // ========================================================================

    public enum AnimationState {
        STOPPED,    // No animation, timer at 0
        PLAYING,    // Advancing frames
        PAUSED,     // Timer frozen, current frame visible
        FINISHED    // Non-looping animation completed (stays on last frame)
    }

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public AnimationComponent() {
    }

    public AnimationComponent(Animation animation) {
        this.animation = animation;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        if (autoPlay && animation != null && animation.getFrameCount() > 0) {
            play();
        }
    }

    @Override
    public void update(float deltaTime) {
        if (state != AnimationState.PLAYING) return;
        if (animation == null || animation.getFrameCount() == 0) return;
        if (spriteRenderer == null) return;

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
                    state = AnimationState.FINISHED;
                    return;
                }
            }

            frame = animation.getFrame(currentFrame);
        }

        // Update sprite
        spriteRenderer.setSprite(animation.getFrameSprite(currentFrame));
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
        state = AnimationState.PLAYING;

        // Set initial frame
        if (spriteRenderer != null) {
            spriteRenderer.setSprite(animation.getFrameSprite(0));
        }
    }

    /**
     * Plays a different animation.
     */
    public void playAnimation(Animation animation) {
        this.animation = animation;
        play();
    }

    /**
     * Plays an animation by path.
     */
    public void playAnimation(String path) {
        if (path == null || path.isBlank()) {
            stop();
            return;
        }
        this.animation = Assets.load(path, Animation.class);
        play();
    }

    /**
     * Pauses playback at current frame.
     */
    public void pause() {
        if (state == AnimationState.PLAYING) {
            state = AnimationState.PAUSED;
        }
    }

    /**
     * Resumes playback from current position.
     */
    public void resume() {
        if (state == AnimationState.PAUSED) {
            state = AnimationState.PLAYING;
        }
    }

    /**
     * Stops playback and resets to first frame.
     */
    public void stop() {
        currentFrame = 0;
        timer = 0;
        state = AnimationState.STOPPED;

        if (animation != null && animation.getFrameCount() > 0 && spriteRenderer != null) {
            spriteRenderer.setSprite(animation.getFrameSprite(0));
        }
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
        return state == AnimationState.PLAYING;
    }

    public boolean isPaused() {
        return state == AnimationState.PAUSED;
    }

    public boolean isFinished() {
        return state == AnimationState.FINISHED;
    }

    public int getCurrentFrameIndex() {
        return currentFrame;
    }

    public float getTimer() {
        return timer;
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

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    public void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
    }
}
