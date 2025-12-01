// MockTimeContext.java
// Location: src/test/java/com/pocket/rpg/time/MockTimeContext.java
package com.pocket.rpg.time;

/**
 * Mock implementation of TimeContext for testing.
 * Allows manual control of time progression.
 * <p>
 * Usage in tests:
 * <pre>
 * MockTimeContext mockTime = new MockTimeContext(0.016f);
 * Time.setContext(mockTime);
 *
 * // Control time precisely
 * mockTime.setDeltaTime(0.033f);
 * mockTime.advanceTime(1.0f);
 * mockTime.setFrameTimeMs(50.0f);
 * </pre>
 */
public class MockTimeContext implements TimeContext {

    private float deltaTime = 1.0f / 60.0f;  // Exactly 60 FPS
    private float time = 0.0f;
    private long frameCount = 0;
    private float timeScale = 1.0f;
    private float frameTimeMs = 1000.0f / 60.0f;  // Exactly 16.666... ms
    private float avgFrameTimeMs = 1000.0f / 60.0f;

    /**
     * Create a mock time context with default 60 FPS.
     */
    public MockTimeContext() {
    }

    /**
     * Create a mock time context with a specific delta time.
     *
     * @param deltaTime the delta time in seconds (e.g., 0.016f for 60 FPS)
     */
    public MockTimeContext(float deltaTime) {
        this.deltaTime = deltaTime;
        this.frameTimeMs = deltaTime * 1000.0f;  // Convert to ms
        this.avgFrameTimeMs = frameTimeMs;
    }

    @Override
    public void init() {
        // No-op for mock
    }

    @Override
    public void update() {
        time += deltaTime * timeScale;
        frameCount++;
    }

    /**
     * Manually advance time by a specific amount (for testing).
     *
     * @param seconds the number of seconds to advance
     */
    public void advanceTime(float seconds) {
        time += seconds;
        frameCount++;
    }

    /**
     * Set the delta time (for testing different frame rates).
     *
     * @param dt the delta time in seconds
     */
    public void setDeltaTime(float dt) {
        this.deltaTime = dt;
        this.frameTimeMs = dt * 1000.0f;
        this.avgFrameTimeMs = frameTimeMs;
    }

    /**
     * Set the frame time in milliseconds (for testing performance scenarios).
     *
     * @param ms the frame time in milliseconds
     */
    public void setFrameTimeMs(float ms) {
        this.frameTimeMs = ms;
        this.deltaTime = ms / 1000.0f;
    }

    /**
     * Set the average frame time in milliseconds (for testing).
     *
     * @param ms the average frame time in milliseconds
     */
    public void setAvgFrameTimeMs(float ms) {
        this.avgFrameTimeMs = ms;
    }

    /**
     * Set the current time (for testing specific moments).
     *
     * @param time the time in seconds
     */
    public void setTime(float time) {
        this.time = time;
    }

    @Override
    public float getDeltaTime() {
        return deltaTime * timeScale;
    }

    @Override
    public float getUnscaledDeltaTime() {
        return deltaTime;
    }

    @Override
    public float getTime() {
        return time;
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
        this.timeScale = Math.max(0, scale);
    }

    @Override
    public float getFPS() {
        return (deltaTime > 0) ? (1.0f / deltaTime) : 60.0f;
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
        deltaTime = 1.0f / 60.0f;  // Exactly 60 FPS
        time = 0.0f;
        frameCount = 0;
        timeScale = 1.0f;
        frameTimeMs = 1000.0f / 60.0f;  // Exactly 16.666... ms
        avgFrameTimeMs = 1000.0f / 60.0f;
    }
}