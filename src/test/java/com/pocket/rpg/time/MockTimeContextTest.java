// MockTimeContextTest.java
// Location: src/test/java/com/pocket/rpg/time/MockTimeContextTest.java
package com.pocket.rpg.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MockTimeContext.
 * Tests mock behavior and manual time control.
 */
@DisplayName("MockTimeContext Tests")
class MockTimeContextTest {

    private MockTimeContext timeContext;

    @BeforeEach
    void setUp() {
        timeContext = new MockTimeContext();
        timeContext.init();
    }

    // ========================================
    // Constructor Tests
    // ========================================

    @Test
    @DisplayName("Should initialize with default 60 FPS")
    void shouldInitializeWithDefault60FPS() {
        MockTimeContext context = new MockTimeContext();

        // Should be EXACTLY 1/60 seconds
        assertEquals(1.0f / 60.0f, context.getDeltaTime(), 0.0001f);
        // Should be EXACTLY 60 FPS
        assertEquals(60.0f, context.getFPS(), 0.0001f);
    }

    @Test
    @DisplayName("Should initialize with custom delta time")
    void shouldInitializeWithCustomDeltaTime() {
        // Use EXACT 30 FPS delta time
        MockTimeContext context = new MockTimeContext(1.0f / 30.0f);

        assertEquals(1.0f / 30.0f, context.getDeltaTime(), 0.0001f);
        // Should be EXACTLY 30 FPS
        assertEquals(30.0f, context.getFPS(), 0.0001f);
    }

    // ========================================
    // Basic Time Tests
    // ========================================

    @Test
    @DisplayName("Should start with zero time")
    void shouldStartWithZeroTime() {
        assertEquals(0.0f, timeContext.getTime(), 0.001f);
    }

    @Test
    @DisplayName("Should start with zero frame count")
    void shouldStartWithZeroFrameCount() {
        assertEquals(0, timeContext.getFrameCount());
    }

    @Test
    @DisplayName("Should start with time scale of 1.0")
    void shouldStartWithNormalTimeScale() {
        assertEquals(1.0f, timeContext.getTimeScale(), 0.001f);
    }

    // ========================================
    // Update Tests
    // ========================================

    @Test
    @DisplayName("Should advance time on update")
    void shouldAdvanceTimeOnUpdate() {
        assertEquals(0.0f, timeContext.getTime(), 0.0001f);

        timeContext.update();
        // After 1 update: time = 1/60
        assertEquals(1.0f / 60.0f, timeContext.getTime(), 0.0001f);

        timeContext.update();
        // After 2 updates: time = 2/60
        assertEquals(2.0f / 60.0f, timeContext.getTime(), 0.0001f);
    }

    @Test
    @DisplayName("Should increment frame count on update")
    void shouldIncrementFrameCountOnUpdate() {
        assertEquals(0, timeContext.getFrameCount());

        timeContext.update();
        assertEquals(1, timeContext.getFrameCount());

        timeContext.update();
        assertEquals(2, timeContext.getFrameCount());

        timeContext.update();
        assertEquals(3, timeContext.getFrameCount());
    }

    @Test
    @DisplayName("Should advance time by delta time on each update")
    void shouldAdvanceTimeByDeltaTimeOnEachUpdate() {
        timeContext.setDeltaTime(0.1f);

        timeContext.update();
        assertEquals(0.1f, timeContext.getTime(), 0.001f);

        timeContext.update();
        assertEquals(0.2f, timeContext.getTime(), 0.001f);

        timeContext.update();
        assertEquals(0.3f, timeContext.getTime(), 0.001f);
    }

    // ========================================
    // Manual Time Control Tests
    // ========================================

    @Test
    @DisplayName("Should advance time manually")
    void shouldAdvanceTimeManually() {
        assertEquals(0.0f, timeContext.getTime(), 0.001f);

        timeContext.advanceTime(1.5f);
        assertEquals(1.5f, timeContext.getTime(), 0.001f);

        timeContext.advanceTime(0.5f);
        assertEquals(2.0f, timeContext.getTime(), 0.001f);
    }

    @Test
    @DisplayName("Should increment frame count on manual advance")
    void shouldIncrementFrameCountOnManualAdvance() {
        assertEquals(0, timeContext.getFrameCount());

        timeContext.advanceTime(1.0f);
        assertEquals(1, timeContext.getFrameCount());

        timeContext.advanceTime(2.0f);
        assertEquals(2, timeContext.getFrameCount());
    }

    @Test
    @DisplayName("Should set delta time")
    void shouldSetDeltaTime() {
        // Set to EXACT 30 FPS
        timeContext.setDeltaTime(1.0f / 30.0f);

        assertEquals(1.0f / 30.0f, timeContext.getDeltaTime(), 0.0001f);
        // Should be EXACTLY 30 FPS
        assertEquals(30.0f, timeContext.getFPS(), 0.0001f);
    }

    @Test
    @DisplayName("Should set time directly")
    void shouldSetTimeDirectly() {
        timeContext.setTime(10.5f);

        assertEquals(10.5f, timeContext.getTime(), 0.001f);
    }

    @Test
    @DisplayName("Should set frame time in milliseconds")
    void shouldSetFrameTimeInMilliseconds() {
        timeContext.setFrameTimeMs(33.33f); // 30 FPS

        assertEquals(33.33f, timeContext.getFrameTimeMs(), 0.001f);
        assertEquals(0.033f, timeContext.getDeltaTime(), 0.001f);
    }

    @Test
    @DisplayName("Should set average frame time")
    void shouldSetAverageFrameTime() {
        timeContext.setAvgFrameTimeMs(20.0f);

        assertEquals(20.0f, timeContext.getAvgFrameTimeMs(), 0.001f);
    }

    // ========================================
    // Time Scale Tests
    // ========================================

    @Test
    @DisplayName("Should scale delta time with time scale")
    void shouldScaleDeltaTimeWithTimeScale() {
        timeContext.setDeltaTime(0.016f); // 60 FPS

        // Normal speed
        assertEquals(0.016f, timeContext.getDeltaTime(), 0.001f);

        // Half speed
        timeContext.setTimeScale(0.5f);
        assertEquals(0.008f, timeContext.getDeltaTime(), 0.001f);

        // Double speed
        timeContext.setTimeScale(2.0f);
        assertEquals(0.032f, timeContext.getDeltaTime(), 0.001f);
    }

    @Test
    @DisplayName("Should return unscaled delta time regardless of time scale")
    void shouldReturnUnscaledDeltaTime() {
        timeContext.setDeltaTime(0.016f);

        // Normal speed
        assertEquals(0.016f, timeContext.getUnscaledDeltaTime(), 0.001f);

        // Change time scale
        timeContext.setTimeScale(0.5f);

        // Unscaled should still be 0.016
        assertEquals(0.016f, timeContext.getUnscaledDeltaTime(), 0.001f);

        // But scaled delta should be different
        assertEquals(0.008f, timeContext.getDeltaTime(), 0.001f);
    }

    @Test
    @DisplayName("Should pause when time scale is zero")
    void shouldPauseWhenTimeScaleIsZero() {
        timeContext.setTimeScale(0.0f);

        float timeBefore = timeContext.getTime();

        timeContext.update();

        float timeAfter = timeContext.getTime();

        // Time should not advance
        assertEquals(timeBefore, timeAfter, 0.001f);

        // But frame count should increment
        assertEquals(1, timeContext.getFrameCount());
    }

    @Test
    @DisplayName("Should reject negative time scale")
    void shouldRejectNegativeTimeScale() {
        timeContext.setTimeScale(1.0f);

        timeContext.setTimeScale(-1.0f);

        // Should clamp to zero instead of going negative
        assertEquals(0.0f, timeContext.getTimeScale(), 0.001f);
    }

    @Test
    @DisplayName("Should apply time scale to update")
    void shouldApplyTimeScaleToUpdate() {
        timeContext.setDeltaTime(0.1f);
        timeContext.setTimeScale(0.5f); // Half speed

        timeContext.update();

        // Should advance by 0.1 * 0.5 = 0.05
        assertEquals(0.05f, timeContext.getTime(), 0.001f);
    }

    // ========================================
    // FPS Tests
    // ========================================

    @Test
    @DisplayName("Should calculate FPS from delta time")
    void shouldCalculateFPSFromDeltaTime() {
        // 60 FPS - use EXACT delta time
        timeContext.setDeltaTime(1.0f / 60.0f);
        assertEquals(60.0f, timeContext.getFPS(), 0.0001f);

        // 30 FPS - use EXACT delta time
        timeContext.setDeltaTime(1.0f / 30.0f);
        assertEquals(30.0f, timeContext.getFPS(), 0.0001f);

        // 120 FPS - use EXACT delta time
        timeContext.setDeltaTime(1.0f / 120.0f);
        assertEquals(120.0f, timeContext.getFPS(), 0.0001f);
    }

    @Test
    @DisplayName("Should handle zero delta time in FPS calculation")
    void shouldHandleZeroDeltaTimeInFPS() {
        timeContext.setDeltaTime(0.0f);

        // Should return default FPS instead of infinity
        float fps = timeContext.getFPS();
        assertEquals(60.0f, fps, 0.1f);
    }

    // ========================================
    // Performance Metrics Tests
    // ========================================

    @Test
    @DisplayName("Should return frame time in milliseconds")
    void shouldReturnFrameTimeInMilliseconds() {
        timeContext.setFrameTimeMs(16.67f);

        assertEquals(16.67f, timeContext.getFrameTimeMs(), 0.001f);
    }

    @Test
    @DisplayName("Should convert delta time to frame time")
    void shouldConvertDeltaTimeToFrameTime() {
        timeContext.setDeltaTime(0.016f);

        // Should be 16ms
        assertEquals(16.0f, timeContext.getFrameTimeMs(), 0.001f);
    }

    @Test
    @DisplayName("Should return average frame time")
    void shouldReturnAverageFrameTime() {
        timeContext.setAvgFrameTimeMs(20.0f);

        assertEquals(20.0f, timeContext.getAvgFrameTimeMs(), 0.001f);
    }

    // ========================================
    // Reset Tests
    // ========================================

    @Test
    @DisplayName("Should reset all values")
    void shouldResetAllValues() {
        // Change values
        timeContext.setDeltaTime(0.1f);
        timeContext.setTimeScale(0.5f);
        timeContext.setTime(5.0f);
        timeContext.setFrameTimeMs(100.0f);
        timeContext.setAvgFrameTimeMs(50.0f);
        timeContext.update();
        timeContext.update();

        // Reset
        timeContext.reset();

        // Verify reset
        assertEquals(0.016f, timeContext.getDeltaTime(), 0.001f);
        assertEquals(0.0f, timeContext.getTime(), 0.001f);
        assertEquals(0, timeContext.getFrameCount());
        assertEquals(1.0f, timeContext.getTimeScale(), 0.001f);
        assertEquals(16.67f, timeContext.getFrameTimeMs(), 0.01f);
        assertEquals(16.67f, timeContext.getAvgFrameTimeMs(), 0.01f);
    }

    // ========================================
    // Testing Scenarios
    // ========================================

    @Test
    @DisplayName("Should simulate 60 FPS gameplay")
    void shouldSimulate60FPSGameplay() {
        timeContext.setDeltaTime(0.016f); // 60 FPS

        // Simulate 60 frames (1 second of gameplay)
        for (int i = 0; i < 60; i++) {
            timeContext.update();
        }

        assertEquals(60, timeContext.getFrameCount());
        assertEquals(0.96f, timeContext.getTime(), 0.01f); // ~1 second
    }

    @Test
    @DisplayName("Should simulate slow motion")
    void shouldSimulateSlowMotion() {
        timeContext.setDeltaTime(0.016f); // 60 FPS
        timeContext.setTimeScale(0.3f); // 30% speed

        // Run for 60 frames
        for (int i = 0; i < 60; i++) {
            timeContext.update();
        }

        // Should advance by 0.96 * 0.3 = 0.288 seconds
        assertEquals(0.288f, timeContext.getTime(), 0.01f);
        assertEquals(60, timeContext.getFrameCount());
    }

    @Test
    @DisplayName("Should simulate frame time spike")
    void shouldSimulateFrameTimeSpike() {
        // Normal frames
        timeContext.setFrameTimeMs(16.67f);
        timeContext.setAvgFrameTimeMs(16.67f);

        for (int i = 0; i < 10; i++) {
            timeContext.update();
        }

        // Simulate spike
        timeContext.setFrameTimeMs(100.0f);
        timeContext.update();

        // Can detect spike
        assertTrue(timeContext.getFrameTimeMs() > timeContext.getAvgFrameTimeMs());
    }

    @Test
    @DisplayName("Should simulate pause and resume")
    void shouldSimulatePauseAndResume() {
        timeContext.setDeltaTime(0.016f);

        // Run normally
        for (int i = 0; i < 10; i++) {
            timeContext.update();
        }

        float timeBeforePause = timeContext.getTime();

        // Pause
        timeContext.setTimeScale(0.0f);
        for (int i = 0; i < 10; i++) {
            timeContext.update();
        }

        // Time should not advance during pause
        assertEquals(timeBeforePause, timeContext.getTime(), 0.001f);

        // Resume
        timeContext.setTimeScale(1.0f);
        for (int i = 0; i < 10; i++) {
            timeContext.update();
        }

        // Time should advance again
        assertTrue(timeContext.getTime() > timeBeforePause);
    }

    @Test
    @DisplayName("Should simulate fast forward")
    void shouldSimulateFastForward() {
        timeContext.setDeltaTime(0.016f);
        timeContext.setTimeScale(5.0f); // 5x speed

        // Run for 60 frames
        for (int i = 0; i < 60; i++) {
            timeContext.update();
        }

        // Should advance by 0.96 * 5 = 4.8 seconds
        assertEquals(4.8f, timeContext.getTime(), 0.01f);
    }

    @Test
    @DisplayName("Should jump to specific time")
    void shouldJumpToSpecificTime() {
        // Normal gameplay
        for (int i = 0; i < 60; i++) {
            timeContext.update();
        }

        // Jump to specific time (e.g., checkpoint)
        timeContext.setTime(100.0f);

        assertEquals(100.0f, timeContext.getTime(), 0.001f);

        // Continue from there
        timeContext.update();
        assertTrue(timeContext.getTime() > 100.0f);
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    @DisplayName("Should handle very small delta times")
    void shouldHandleVerySmallDeltaTimes() {
        timeContext.setDeltaTime(0.0001f); // 10,000 FPS

        timeContext.update();

        assertEquals(0.0001f, timeContext.getTime(), 0.00001f);
        assertTrue(timeContext.getFPS() > 1000);
    }

    @Test
    @DisplayName("Should handle very large delta times")
    void shouldHandleVeryLargeDeltaTimes() {
        timeContext.setDeltaTime(1.0f); // 1 FPS

        timeContext.update();

        assertEquals(1.0f, timeContext.getTime(), 0.001f);
        assertEquals(1.0f, timeContext.getFPS(), 0.1f);
    }

    @Test
    @DisplayName("Should handle many updates")
    void shouldHandleManyUpdates() {
        // Simulate thousands of frames
        for (int i = 0; i < 10000; i++) {
            timeContext.update();
        }

        assertEquals(10000, timeContext.getFrameCount());
        assertTrue(timeContext.getTime() > 0);
    }

    @Test
    @DisplayName("Should maintain precision over long time")
    void shouldMaintainPrecisionOverLongTime() {
        // Test that time advances correctly even with large values
        // Note: Float precision degrades with very large numbers
        // At 1 million, precision is ~0.0625, so deltas smaller than that get lost

        // Set to a large but not extreme value (10000 seconds = ~2.7 hours)
        timeContext.setTime(10000.0f);
        float timeBefore = timeContext.getTime();

        timeContext.update();

        float timeAfter = timeContext.getTime();
        float deltaTime = timeContext.getUnscaledDeltaTime();

        // Should advance by exactly delta time (with minimal float precision tolerance)
        assertEquals(timeBefore + deltaTime, timeAfter, 0.001f);

        // FPS should still be exactly 60
        assertEquals(60.0f, timeContext.getFPS(), 0.0001f);
    }
}