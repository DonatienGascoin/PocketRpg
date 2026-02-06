package com.pocket.rpg.components.animations;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationPlayer;
import com.pocket.rpg.components.*;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import lombok.Getter;
import lombok.Setter;

/**
 * Component that plays animations on a GameObject.
 * Requires a SpriteRenderer component on the same GameObject.
 * <p>
 * The Animation field is automatically serialized as a path string
 * via AssetReferenceTypeAdapterFactory, and can be selected using
 * the asset picker in the inspector.
 * <p>
 * Internally uses {@link AnimationPlayer} for playback logic.
 */
@ComponentMeta(category = "Animation")
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
    private final AnimationPlayer player = new AnimationPlayer();

    // ========================================================================
    // COMPONENT REFERENCE (auto-resolved, not serialized)
    // ========================================================================

    @ComponentReference(source = Source.SELF)
    private SpriteRenderer spriteRenderer;

    // ========================================================================
    // STATE ENUM (for API compatibility)
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
        // Sync animation to player
        if (animation != null) {
            player.setAnimationWithoutPlaying(animation);
            player.setSpeed(speed);
        }

        if (autoPlay && animation != null && animation.getFrameCount() > 0) {
            play();
        }
    }

    @Override
    public void update(float deltaTime) {
        if (spriteRenderer == null) return;

        boolean frameChanged = player.update(deltaTime);
        if (frameChanged) {
            Sprite sprite = player.getCurrentSprite();
            if (sprite != null) {
                spriteRenderer.setSprite(sprite);
            }
        }
    }

    // ========================================================================
    // PLAYBACK CONTROL
    // ========================================================================

    /**
     * Starts or restarts the animation from the beginning.
     */
    public void play() {
        if (animation == null || animation.getFrameCount() == 0) return;

        player.setAnimation(animation);
        player.setSpeed(speed);

        // Set initial frame
        if (spriteRenderer != null) {
            Sprite sprite = player.getCurrentSprite();
            if (sprite != null) {
                spriteRenderer.setSprite(sprite);
            }
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
        player.setAnimation(path);
        this.animation = player.getAnimation();
    }

    /**
     * Pauses playback at current frame.
     */
    public void pause() {
        player.pause();
    }

    /**
     * Resumes playback from current position.
     */
    public void resume() {
        player.resume();
    }

    /**
     * Stops playback and resets to first frame.
     */
    public void stop() {
        player.stop();

        if (spriteRenderer != null) {
            Sprite sprite = player.getCurrentSprite();
            if (sprite != null) {
                spriteRenderer.setSprite(sprite);
            }
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
        return player.isPlaying();
    }

    public boolean isPaused() {
        return player.isPaused();
    }

    public boolean isFinished() {
        return player.isFinished();
    }

    public int getCurrentFrameIndex() {
        return player.getCurrentFrame();
    }

    public float getTimer() {
        return player.getTimer();
    }

    /**
     * Gets the current playback state.
     */
    public AnimationState getState() {
        return switch (player.getState()) {
            case STOPPED -> AnimationState.STOPPED;
            case PLAYING -> AnimationState.PLAYING;
            case PAUSED -> AnimationState.PAUSED;
            case FINISHED -> AnimationState.FINISHED;
        };
    }

    /**
     * Gets normalized progress (0.0 to 1.0) through the animation.
     */
    public float getProgress() {
        return player.getProgress();
    }

    /**
     * Gets the underlying AnimationPlayer for direct access.
     */
    public AnimationPlayer getPlayer() {
        return player;
    }

    // ========================================================================
    // PROPERTIES
    // ========================================================================

    public void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
        player.setSpeed(this.speed);
    }
}
