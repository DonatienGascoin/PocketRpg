// DefaultTimeContext.java
package com.pocket.rpg.time;

/**
 * Real implementation of TimeContext using System.nanoTime().
 * This is the implementation used during normal gameplay.
 */
public class DefaultTimeContext implements TimeContext {

    // Time tracking
    private long lastFrameTime;
    private long currentFrameTime;
    private float deltaTime;
    private float unscaledDeltaTime;
    private float totalTime;

    // Frame tracking
    private long frameCount;

    // Time scaling
    private float timeScale = 1.0f;

    // FPS tracking
    private static final int FPS_SAMPLE_SIZE = 60;
    private final float[] fpsSamples = new float[FPS_SAMPLE_SIZE];
    private int fpsSampleIndex = 0;
    private float currentFPS;

    // Performance tracking (NEW!)
    private float frameTimeMs;  // Last frame time in milliseconds
    private final float[] frameTimeSamples = new float[FPS_SAMPLE_SIZE];
    private int frameTimeSampleIndex = 0;
    private float avgFrameTimeMs;

    // Configuration
    private static final float MAX_DELTA_TIME = 0.1f; // Cap at 100ms to prevent spiral of death

    public DefaultTimeContext() {
        // Initialize FPS samples
        for (int i = 0; i < FPS_SAMPLE_SIZE; i++) {
            fpsSamples[i] = 60.0f;  // Start with 60 FPS assumption
            frameTimeSamples[i] = 16.67f;  // 1000ms / 60fps = 16.67ms
        }
    }

    @Override
    public void init() {
        currentFrameTime = System.nanoTime();
        lastFrameTime = currentFrameTime;
        deltaTime = 0.0f;
        unscaledDeltaTime = 0.0f;
        totalTime = 0.0f;
        frameCount = 0;
        currentFPS = 60.0f;
        frameTimeMs = 16.67f;
        avgFrameTimeMs = 16.67f;

        System.out.println("TimeContext initialized");
    }

    @Override
    public void update() {
        // Update frame time
        lastFrameTime = currentFrameTime;
        currentFrameTime = System.nanoTime();

        // Calculate elapsed time
        long elapsedNanos = currentFrameTime - lastFrameTime;
        unscaledDeltaTime = elapsedNanos / 1_000_000_000.0f;

        // Calculate frame time in milliseconds (NEW!)
        frameTimeMs = elapsedNanos / 1_000_000.0f;

        // Cap delta time to prevent spiral of death
        if (unscaledDeltaTime > MAX_DELTA_TIME) {
            unscaledDeltaTime = MAX_DELTA_TIME;
        }

        // Calculate scaled delta time
        deltaTime = unscaledDeltaTime * timeScale;

        // Update total time (using scaled time)
        totalTime += deltaTime;

        // Update frame count
        frameCount++;

        // Update FPS and frame time averages
        updatePerformanceMetrics();
    }

    private void updatePerformanceMetrics() {
        // Calculate instantaneous FPS
        float instantFPS = (unscaledDeltaTime > 0) ? (1.0f / unscaledDeltaTime) : 60.0f;

        // Add FPS to circular buffer
        fpsSamples[fpsSampleIndex] = instantFPS;
        fpsSampleIndex = (fpsSampleIndex + 1) % FPS_SAMPLE_SIZE;

        // Calculate average FPS
        float sumFPS = 0;
        for (float sample : fpsSamples) {
            sumFPS += sample;
        }
        currentFPS = sumFPS / FPS_SAMPLE_SIZE;

        // Add frame time to circular buffer (NEW!)
        frameTimeSamples[frameTimeSampleIndex] = frameTimeMs;
        frameTimeSampleIndex = (frameTimeSampleIndex + 1) % FPS_SAMPLE_SIZE;

        // Calculate average frame time (NEW!)
        float sumFrameTime = 0;
        for (float sample : frameTimeSamples) {
            sumFrameTime += sample;
        }
        avgFrameTimeMs = sumFrameTime / FPS_SAMPLE_SIZE;
    }

    @Override
    public float getDeltaTime() {
        return deltaTime;
    }

    @Override
    public float getUnscaledDeltaTime() {
        return unscaledDeltaTime;
    }

    @Override
    public float getTime() {
        return totalTime;
    }

    @Override
    public long getFrameCount() {
        return frameCount;
    }

    @Override
    public float getTimeScale() {
        return timeScale;
    }

    @Override
    public void setTimeScale(float scale) {
        if (scale < 0) {
            System.err.println("WARNING: Time scale cannot be negative, got: " + scale);
            return;
        }
        this.timeScale = scale;

        if (scale == 0.0f) {
            System.out.println("Time paused");
        } else if (scale != 1.0f) {
            System.out.println("Time scale set to: " + scale + "x");
        }
    }

    @Override
    public float getFPS() {
        return currentFPS;
    }

    @Override
    public float getFrameTimeMs() {
        return frameTimeMs;
    }

    @Override
    public float getAvgFrameTimeMs() {
        return avgFrameTimeMs;
    }

    @Override
    public void reset() {
        currentFrameTime = System.nanoTime();
        lastFrameTime = currentFrameTime;
        deltaTime = 0.0f;
        unscaledDeltaTime = 0.0f;
        totalTime = 0.0f;
        frameCount = 0;
        timeScale = 1.0f;
        frameTimeMs = 16.67f;
        avgFrameTimeMs = 16.67f;

        // Reset FPS samples
        for (int i = 0; i < FPS_SAMPLE_SIZE; i++) {
            fpsSamples[i] = 60.0f;
            frameTimeSamples[i] = 16.67f;
        }
        fpsSampleIndex = 0;
        frameTimeSampleIndex = 0;
        currentFPS = 60.0f;

        System.out.println("TimeContext reset");
    }
}