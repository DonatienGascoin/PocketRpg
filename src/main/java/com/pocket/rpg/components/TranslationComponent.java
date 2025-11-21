package com.pocket.rpg.components;

import org.joml.Vector3f;

/**
 * Component that automatically moves a GameObject from point A to point B.
 * Supports various easing functions for smooth movement.
 */
public class TranslationComponent extends Component {

    public enum EasingType {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        SMOOTH_STEP,
        SMOOTHER_STEP
    }

    private Vector3f startPosition;
    private Vector3f endPosition;
    private float duration; // Total time to complete movement
    private EasingType easingType;
    private boolean loop;
    private boolean pingPong; // Reverse direction when reaching end

    private float elapsedTime;
    private boolean movingForward = true;
    private boolean isComplete = false;

    /**
     * Creates a translation component with linear movement.
     *
     * @param endPosition The destination position
     * @param duration    Time in seconds to reach the destination
     */
    public TranslationComponent(Vector3f endPosition, float duration) {
        this(null, endPosition, duration, EasingType.LINEAR, false, false);
    }

    /**
     * Creates a translation component with custom start and end positions.
     *
     * @param startPosition The starting position (null to use current position)
     * @param endPosition   The destination position
     * @param duration      Time in seconds to reach the destination
     */
    public TranslationComponent(Vector3f startPosition, Vector3f endPosition, float duration) {
        this(startPosition, endPosition, duration, EasingType.LINEAR, false, false);
    }

    /**
     * Creates a translation component with easing.
     *
     * @param startPosition The starting position (null to use current position)
     * @param endPosition   The destination position
     * @param duration      Time in seconds to reach the destination
     * @param easingType    The easing function to use
     */
    public TranslationComponent(Vector3f startPosition, Vector3f endPosition, float duration, EasingType easingType) {
        this(startPosition, endPosition, duration, easingType, false, false);
    }

    /**
     * Full constructor with all options.
     *
     * @param startPosition The starting position (null to use current position)
     * @param endPosition   The destination position
     * @param duration      Time in seconds to reach the destination
     * @param easingType    The easing function to use
     * @param loop          Whether to restart from beginning when complete
     * @param pingPong      Whether to reverse direction when reaching end
     */
    public TranslationComponent(Vector3f startPosition, Vector3f endPosition, float duration,
                                EasingType easingType, boolean loop, boolean pingPong) {
        this.startPosition = startPosition != null ? new Vector3f(startPosition) : null;
        this.endPosition = new Vector3f(endPosition);
        this.duration = duration;
        this.easingType = easingType;
        this.loop = loop;
        this.pingPong = pingPong;
        this.elapsedTime = 0f;
    }

    @Override
    public void start() {
        // If no start position specified, use current position
        if (startPosition == null && gameObject != null) {
            startPosition = new Vector3f(gameObject.getTransform().getPosition());
        }
    }

    @Override
    public void update(float deltaTime) {
        if (isComplete || gameObject == null || startPosition == null) {
            return;
        }

        // Update elapsed time
        elapsedTime += deltaTime;

        // Calculate progress (0 to 1)
        float progress = Math.min(elapsedTime / duration, 1.0f);

        // Apply easing function
        float easedProgress = applyEasing(progress);

        // Calculate current position
        Vector3f current;
        if (movingForward) {
            current = lerp(startPosition, endPosition, easedProgress);
        } else {
            current = lerp(endPosition, startPosition, easedProgress);
        }

        // Update GameObject position
        gameObject.getTransform().setPosition(current);

        // Check if movement is complete
        if (progress >= 1.0f) {
            handleMovementComplete();
        }
    }

    /**
     * Handles what happens when movement reaches the end.
     */
    private void handleMovementComplete() {
        if (pingPong) {
            // Reverse direction
            movingForward = !movingForward;
            elapsedTime = 0f;
        } else if (loop) {
            // Restart from beginning
            elapsedTime = 0f;
            movingForward = true;
        } else {
            // Stop movement
            isComplete = true;
        }
    }

    /**
     * Applies the easing function to the progress value.
     */
    private float applyEasing(float t) {
        switch (easingType) {
            case LINEAR:
                return t;

            case EASE_IN:
                return t * t;

            case EASE_OUT:
                return t * (2 - t);

            case EASE_IN_OUT:
                return t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;

            case SMOOTH_STEP:
                return t * t * (3 - 2 * t);

            case SMOOTHER_STEP:
                return t * t * t * (t * (t * 6 - 15) + 10);

            default:
                return t;
        }
    }

    /**
     * Linear interpolation between two Vector3f points.
     */
    private Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    // Public API

    /**
     * Resets the translation to start over.
     */
    public void reset() {
        elapsedTime = 0f;
        movingForward = true;
        isComplete = false;

        if (gameObject != null && startPosition != null) {
            gameObject.getTransform().setPosition(startPosition);
        }
    }

    /**
     * Pauses the translation by marking it complete.
     */
    public void pause() {
        isComplete = true;
    }

    /**
     * Resumes the translation.
     */
    public void resume() {
        isComplete = false;
    }

    /**
     * Checks if the translation has completed.
     */
    public boolean isComplete() {
        return isComplete;
    }

    /**
     * Gets the current progress (0 to 1).
     */
    public float getProgress() {
        return Math.min(elapsedTime / duration, 1.0f);
    }

    // Getters and Setters

    public Vector3f getStartPosition() {
        return new Vector3f(startPosition);
    }

    public void setStartPosition(Vector3f startPosition) {
        this.startPosition = new Vector3f(startPosition);
    }

    public Vector3f getEndPosition() {
        return new Vector3f(endPosition);
    }

    public void setEndPosition(Vector3f endPosition) {
        this.endPosition = new Vector3f(endPosition);
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public EasingType getEasingType() {
        return easingType;
    }

    public void setEasingType(EasingType easingType) {
        this.easingType = easingType;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean isPingPong() {
        return pingPong;
    }

    public void setPingPong(boolean pingPong) {
        this.pingPong = pingPong;
    }

    @Override
    public String toString() {
        return String.format("TranslationComponent[progress=%.2f, easing=%s, loop=%s, pingPong=%s]",
                getProgress(), easingType, loop, pingPong);
    }
}