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
    private float duration;
    private EasingType easingType;
    private boolean loop;
    private boolean pingPong;

    private float elapsedTime;
    private boolean movingForward = true;
    private boolean isComplete = false;

    /**
     * Default constructor for editor instantiation.
     * Configure via setters after construction.
     * <p>
     * Defaults:
     * - endPosition: (0, 1, 0) - moves up 1 unit
     * - duration: 1 second
     * - easing: LINEAR
     * - loop/pingPong: false
     */
    public TranslationComponent() {
        this.startPosition = null;  // Will be captured from transform on start()
        this.endPosition = new Vector3f(0, 1, 0);
        this.duration = 1f;
        this.easingType = EasingType.LINEAR;
        this.loop = false;
        this.pingPong = false;
        this.elapsedTime = 0f;
    }

    public TranslationComponent(Vector3f endPosition, float duration) {
        this(null, endPosition, duration, EasingType.LINEAR, false, false);
    }

    public TranslationComponent(Vector3f startPosition, Vector3f endPosition, float duration) {
        this(startPosition, endPosition, duration, EasingType.LINEAR, false, false);
    }

    public TranslationComponent(Vector3f startPosition, Vector3f endPosition, float duration, EasingType easingType) {
        this(startPosition, endPosition, duration, easingType, false, false);
    }

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
    public void onStart() {
        if (startPosition == null && gameObject != null) {
            startPosition = new Vector3f(gameObject.getTransform().getPosition());
        }
    }

    @Override
    public void update(float deltaTime) {
        if (isComplete || gameObject == null || startPosition == null) {
            return;
        }

        elapsedTime += deltaTime;
        float progress = Math.min(elapsedTime / duration, 1.0f);
        float easedProgress = applyEasing(progress);

        Vector3f current;
        if (movingForward) {
            current = lerp(startPosition, endPosition, easedProgress);
        } else {
            current = lerp(endPosition, startPosition, easedProgress);
        }

        gameObject.getTransform().setPosition(current);

        if (progress >= 1.0f) {
            handleMovementComplete();
        }
    }

    private void handleMovementComplete() {
        if (pingPong) {
            movingForward = !movingForward;
            elapsedTime = 0f;
        } else if (loop) {
            elapsedTime = 0f;
            movingForward = true;
        } else {
            isComplete = true;
        }
    }

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

    private Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    public void reset() {
        elapsedTime = 0f;
        movingForward = true;
        isComplete = false;
        if (gameObject != null && startPosition != null) {
            gameObject.getTransform().setPosition(startPosition);
        }
    }

    public void pause() {
        isComplete = true;
    }

    public void resume() {
        isComplete = false;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public float getProgress() {
        return Math.min(elapsedTime / duration, 1.0f);
    }

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
}