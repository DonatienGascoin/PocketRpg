package com.pocket.rpg.tween;

import java.util.function.Consumer;

/**
 * Represents an animation that interpolates values over time.
 * <p>
 * Use the static factory methods in {@link Tweens} to create tweens for UI components,
 * or create custom tweens by extending this class.
 * <p>
 * Example:
 * <pre>
 * Tweens.offsetX(panel.getUITransform(), 100f, 0.3f)
 *       .setEase(Ease.OUT_BACK)
 *       .setDelay(0.1f)
 *       .onComplete(() -> System.out.println("Done!"));
 * </pre>
 *
 * @param <T> The type of value being tweened
 */
public class Tween<T> {

    // Target state
    protected final T startValue;
    protected final T endValue;
    protected final float duration;
    protected final TweenInterpolator<T> interpolator;
    protected final Consumer<T> setter;

    // Timing
    protected float delay = 0f;
    protected float elapsed = 0f;
    protected boolean started = false;
    protected boolean completed = false;
    protected boolean paused = false;

    // Options
    protected Ease ease = Ease.OUT_QUAD;
    protected int loopCount = 0;  // 0 = no loop, -1 = infinite
    protected int currentLoop = 0;
    protected boolean yoyo = false;  // Reverse on each loop
    protected boolean isReversed = false;

    // Callbacks
    protected Runnable onStart;
    protected Runnable onComplete;
    protected Consumer<T> onUpdate;

    // Identity for killing
    protected Object target;
    protected String id;

    /**
     * Creates a new tween.
     *
     * @param startValue   Starting value
     * @param endValue     Ending value
     * @param duration     Duration in seconds
     * @param interpolator How to interpolate between values
     * @param setter       Function to apply the current value
     */
    public Tween(T startValue, T endValue, float duration,
                 TweenInterpolator<T> interpolator, Consumer<T> setter) {
        this.startValue = startValue;
        this.endValue = endValue;
        this.duration = Math.max(0.001f, duration);
        this.interpolator = interpolator;
        this.setter = setter;

        // Auto-register with TweenManager
        TweenManager.add(this);
    }

    // ========================================================================
    // FLUENT CONFIGURATION
    // ========================================================================

    /**
     * Sets the easing function.
     */
    public Tween<T> setEase(Ease ease) {
        this.ease = ease;
        return this;
    }

    /**
     * Sets a delay before the tween starts.
     */
    public Tween<T> setDelay(float delay) {
        this.delay = Math.max(0, delay);
        return this;
    }

    /**
     * Sets the number of times to loop (0 = no loop, -1 = infinite).
     */
    public Tween<T> setLoops(int count) {
        this.loopCount = count;
        return this;
    }

    /**
     * Enables yoyo mode (reverse direction on each loop).
     */
    public Tween<T> setYoyo(boolean yoyo) {
        this.yoyo = yoyo;
        return this;
    }

    /**
     * Sets a callback for when the tween starts (after delay).
     */
    public Tween<T> onStart(Runnable callback) {
        this.onStart = callback;
        return this;
    }

    /**
     * Sets a callback for when the tween completes.
     */
    public Tween<T> onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    /**
     * Sets a callback for each update with the current value.
     */
    public Tween<T> onUpdate(Consumer<T> callback) {
        this.onUpdate = callback;
        return this;
    }

    /**
     * Associates this tween with a target object (for killing by target).
     */
    public Tween<T> setTarget(Object target) {
        this.target = target;
        return this;
    }

    /**
     * Sets a unique ID for this tween (for killing by ID).
     */
    public Tween<T> setId(String id) {
        this.id = id;
        return this;
    }

    // ========================================================================
    // CONTROL
    // ========================================================================

    /**
     * Pauses the tween.
     */
    public Tween<T> pause() {
        this.paused = true;
        return this;
    }

    /**
     * Resumes the tween.
     */
    public Tween<T> resume() {
        this.paused = false;
        return this;
    }

    /**
     * Immediately completes the tween, jumping to end value.
     */
    public void complete() {
        if (completed) return;

        setter.accept(endValue);
        completed = true;

        if (onComplete != null) {
            onComplete.run();
        }
    }

    /**
     * Kills the tween without completing it.
     */
    public void kill() {
        completed = true;
        TweenManager.remove(this);
    }

    // ========================================================================
    // UPDATE (called by TweenManager)
    // ========================================================================

    /**
     * Updates the tween. Called by TweenManager each frame.
     *
     * @param deltaTime Time since last frame in seconds
     * @return true if tween is still active, false if completed
     */
    public boolean update(float deltaTime) {
        if (completed || paused) {
            return !completed;
        }

        // Handle delay
        if (delay > 0) {
            delay -= deltaTime;
            return true;
        }

        // Fire start callback
        if (!started) {
            started = true;
            if (onStart != null) {
                onStart.run();
            }
        }

        // Update elapsed time
        elapsed += deltaTime;
        float t = Math.min(1f, elapsed / duration);

        // Apply easing
        float easedT = ease.apply(isReversed ? 1 - t : t);

        // Interpolate and apply value
        T currentValue = interpolator.interpolate(startValue, endValue, easedT);
        setter.accept(currentValue);

        // Fire update callback
        if (onUpdate != null) {
            onUpdate.accept(currentValue);
        }

        // Check completion
        if (t >= 1f) {
            return handleLoopOrComplete();
        }

        return true;
    }

    private boolean handleLoopOrComplete() {
        // Check for looping
        if (loopCount == -1 || currentLoop < loopCount) {
            currentLoop++;
            elapsed = 0f;

            if (yoyo) {
                isReversed = !isReversed;
            }

            return true;
        }

        // Complete
        completed = true;
        if (onComplete != null) {
            onComplete.run();
        }

        return false;
    }

    // ========================================================================
    // GETTERS
    // ========================================================================

    public boolean isCompleted() {
        return completed;
    }

    public boolean isPaused() {
        return paused;
    }

    public Object getTarget() {
        return target;
    }

    public String getId() {
        return id;
    }

    public float getProgress() {
        return Math.min(1f, elapsed / duration);
    }

    /**
     * Functional interface for interpolating between values.
     */
    @FunctionalInterface
    public interface TweenInterpolator<T> {
        T interpolate(T start, T end, float t);
    }
}