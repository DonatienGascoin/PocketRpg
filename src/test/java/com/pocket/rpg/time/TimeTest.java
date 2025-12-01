// TimeTest.java
// Location: src/test/java/com/pocket/rpg/time/TimeTest.java
package com.pocket.rpg.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Time service locator.
 * Tests static API and context management.
 */
@DisplayName("Time Service Locator Tests")
class TimeTest {

    @BeforeEach
    void setUp() {
        // Initialize with mock context for predictable testing
        // Use EXACT 60 FPS (1/60 seconds per frame)
        MockTimeContext mockContext = new MockTimeContext(1.0f / 60.0f);
        Time.initialize(mockContext);
    }

    @AfterEach
    void tearDown() {
        // Clean up to avoid affecting other tests
        Time.setContext(null);
    }

    // ========================================
    // Initialization Tests
    // ========================================

    @Test
    @DisplayName("Should initialize with context")
    void shouldInitializeWithContext() {
        MockTimeContext context = new MockTimeContext();
        Time.initialize(context);

        assertNotNull(Time.getContext());
        assertEquals(context, Time.getContext());
    }

    @Test
    @DisplayName("Should throw exception when initializing with null context")
    void shouldThrowExceptionWhenInitializingWithNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            Time.initialize(null);
        });
    }

    @Test
    @DisplayName("Should throw exception when accessing uninitialized context")
    void shouldThrowExceptionWhenAccessingUninitializedContext() {
        Time.setContext(null);

        assertThrows(IllegalStateException.class, () -> {
            Time.deltaTime();
        });
    }

    @Test
    @DisplayName("Should allow context swapping")
    void shouldAllowContextSwapping() {
        MockTimeContext context1 = new MockTimeContext(0.016f);
        MockTimeContext context2 = new MockTimeContext(0.033f);

        Time.setContext(context1);
        assertEquals(0.016f, Time.deltaTime(), 0.001f);

        Time.setContext(context2);
        assertEquals(0.033f, Time.deltaTime(), 0.001f);
    }

    // ========================================
    // Static API Tests
    // ========================================

    @Test
    @DisplayName("Should delegate update to context")
    void shouldDelegateUpdateToContext() {
        MockTimeContext context = (MockTimeContext) Time.getContext();

        long initialFrameCount = Time.frameCount();

        Time.update();

        assertEquals(initialFrameCount + 1, Time.frameCount());
    }

    @Test
    @DisplayName("Should delegate deltaTime to context")
    void shouldDelegateDeltaTimeToContext() {
        // Use EXACT 30 FPS
        MockTimeContext context = new MockTimeContext(1.0f / 30.0f);
        Time.setContext(context);

        assertEquals(1.0f / 30.0f, Time.deltaTime(), 0.0001f);
    }

    @Test
    @DisplayName("Should delegate unscaledDeltaTime to context")
    void shouldDelegateUnscaledDeltaTimeToContext() {
        // Use EXACT 60 FPS
        MockTimeContext context = new MockTimeContext(1.0f / 60.0f);
        context.setTimeScale(0.5f);
        Time.setContext(context);

        float expectedDelta = 1.0f / 60.0f;

        // Scaled should be halved
        assertEquals(expectedDelta * 0.5f, Time.deltaTime(), 0.0001f);

        // Unscaled should be original
        assertEquals(expectedDelta, Time.unscaledDeltaTime(), 0.0001f);
    }

    @Test
    @DisplayName("Should delegate time to context")
    void shouldDelegateTimeToContext() {
        MockTimeContext context = new MockTimeContext();
        context.setTime(10.5f);
        Time.setContext(context);

        assertEquals(10.5f, Time.time(), 0.001f);
    }

    @Test
    @DisplayName("Should delegate frameCount to context")
    void shouldDelegateFrameCountToContext() {
        MockTimeContext context = new MockTimeContext();
        Time.setContext(context);

        assertEquals(0, Time.frameCount());

        Time.update();
        assertEquals(1, Time.frameCount());

        Time.update();
        assertEquals(2, Time.frameCount());
    }

    @Test
    @DisplayName("Should delegate timeScale to context")
    void shouldDelegateTimeScaleToContext() {
        assertEquals(1.0f, Time.timeScale(), 0.001f);

        Time.setTimeScale(0.5f);
        assertEquals(0.5f, Time.timeScale(), 0.001f);
    }

    @Test
    @DisplayName("Should delegate fps to context")
    void shouldDelegateFpsToContext() {
        // Use EXACT 60 FPS
        MockTimeContext context = new MockTimeContext(1.0f / 60.0f);
        Time.setContext(context);

        // Should be EXACTLY 60 FPS
        assertEquals(60.0f, Time.fps(), 0.0001f);
    }

    @Test
    @DisplayName("Should delegate frameTimeMs to context")
    void shouldDelegateFrameTimeMsToContext() {
        MockTimeContext context = new MockTimeContext();
        context.setFrameTimeMs(20.0f);
        Time.setContext(context);

        assertEquals(20.0f, Time.frameTimeMs(), 0.001f);
    }

    @Test
    @DisplayName("Should delegate avgFrameTimeMs to context")
    void shouldDelegateAvgFrameTimeMsToContext() {
        MockTimeContext context = new MockTimeContext();
        context.setAvgFrameTimeMs(18.5f);
        Time.setContext(context);

        assertEquals(18.5f, Time.avgFrameTimeMs(), 0.001f);
    }

    @Test
    @DisplayName("Should delegate reset to context")
    void shouldDelegateResetToContext() {
        MockTimeContext context = new MockTimeContext();
        context.setTime(10.0f);
        context.update();
        context.update();
        Time.setContext(context);

        assertTrue(Time.time() > 0);
        assertTrue(Time.frameCount() > 0);

        Time.reset();

        assertEquals(0.0f, Time.time(), 0.001f);
        assertEquals(0, Time.frameCount());
    }

    // ========================================
    // Context Swapping Scenarios
    // ========================================

    @Test
    @DisplayName("Should switch between real and mock context")
    void shouldSwitchBetweenRealAndMockContext() {
        // Start with real context
        DefaultTimeContext realContext = new DefaultTimeContext();
        realContext.init();
        Time.setContext(realContext);

        // Should work with real time
        assertNotNull(Time.getContext());
        assertTrue(Time.deltaTime() >= 0);

        // Switch to mock for testing
        MockTimeContext mockContext = new MockTimeContext(0.1f);
        Time.setContext(mockContext);

        // Should use mock values
        assertEquals(0.1f, Time.deltaTime(), 0.001f);
    }

    @Test
    @DisplayName("Should maintain independence between contexts")
    void shouldMaintainIndependenceBetweenContexts() {
        MockTimeContext context1 = new MockTimeContext();
        MockTimeContext context2 = new MockTimeContext();

        // Modify context1
        Time.setContext(context1);
        Time.update();
        Time.update();
        assertEquals(2, Time.frameCount());

        // Switch to context2
        Time.setContext(context2);

        // Should have its own state
        assertEquals(0, Time.frameCount());
    }

    // ========================================
    // Integration with Components
    // ========================================

    @Test
    @DisplayName("Should work with game component pattern")
    void shouldWorkWithGameComponentPattern() {
        // Simulate component update
        class MockComponent {
            float position = 0.0f;
            float velocity = 10.0f; // units per second

            void update() {
                position += velocity * Time.deltaTime();
            }
        }

        MockComponent component = new MockComponent();

        // Simulate 60 frames at 60 FPS (1 second)
        for (int i = 0; i < 60; i++) {
            Time.update();
            component.update();
        }

        // Should have moved ~10 units
        assertEquals(10.0f, component.position, 0.5f);
    }

    @Test
    @DisplayName("Should handle time-based animations")
    void shouldHandleTimeBasedAnimations() {
        class Animator {
            float elapsed = 0.0f;
            final float duration = 1.0f;

            void update() {
                elapsed += Time.deltaTime();
            }

            float getProgress() {
                return Math.min(1.0f, elapsed / duration);
            }
        }

        Animator animator = new Animator();

        // With EXACT 60 FPS (1/60 seconds per frame)
        // 60 frames = EXACTLY 1.0 second
        for (int i = 0; i < 60; i++) {
            Time.update();
            animator.update();
        }

        // Should be complete within floating-point precision
        // Allow for accumulated rounding error: ~60 * 1.2e-7 ≈ 7e-6
        float progress = animator.getProgress();
        assertTrue(progress >= 0.9999f,
                "Animation should be at least 99.99% complete after 60 frames at 60 FPS. Got: " + progress);
        assertTrue(progress <= 1.0001f,
                "Animation progress should not exceed 100%. Got: " + progress);

        // Progress should be very close to 1.0 (within 0.01%)
        assertEquals(1.0f, progress, 0.0001f, "Progress should be approximately 1.0");
    }

    @Test
    @DisplayName("Should support slow motion gameplay")
    void shouldSupportSlowMotionGameplay() {
        class Player {
            float speed = 5.0f;
            float distance = 0.0f;

            void update() {
                distance += speed * Time.deltaTime();
            }
        }

        Player player = new Player();

        // Normal speed for 30 frames
        for (int i = 0; i < 30; i++) {
            Time.update();
            player.update();
        }
        float normalDistance = player.distance;

        // Slow motion (50% speed) for 30 frames
        Time.setTimeScale(0.5f);
        for (int i = 0; i < 30; i++) {
            Time.update();
            player.update();
        }
        float slowMotionDistance = player.distance - normalDistance;

        // Slow motion movement should be roughly half
        assertTrue(slowMotionDistance < normalDistance);
        assertEquals(normalDistance * 0.5f, slowMotionDistance, 0.2f);
    }

    @Test
    @DisplayName("Should support pause and resume")
    void shouldSupportPauseAndResume() {
        class GameState {
            float score = 0.0f;

            void update() {
                score += 10.0f * Time.deltaTime(); // Score increases over time
            }
        }

        GameState state = new GameState();

        // Play normally
        for (int i = 0; i < 30; i++) {
            Time.update();
            state.update();
        }
        float scoreBeforePause = state.score;

        // Pause
        Time.setTimeScale(0.0f);
        for (int i = 0; i < 30; i++) {
            Time.update();
            state.update();
        }

        // Score should not change during pause
        assertEquals(scoreBeforePause, state.score, 0.001f);

        // Resume
        Time.setTimeScale(1.0f);
        for (int i = 0; i < 30; i++) {
            Time.update();
            state.update();
        }

        // Score should increase again
        assertTrue(state.score > scoreBeforePause);
    }

    // ========================================
    // Testing Scenarios
    // ========================================

    @Test
    @DisplayName("Should support unit test time manipulation")
    void shouldSupportUnitTestTimeManipulation() {
        // Setup mock for testing
        MockTimeContext mockTime = new MockTimeContext(0.016f);
        Time.setContext(mockTime);

        // Test component behavior at specific time
        mockTime.setTime(5.0f);

        // Verify state at 5 seconds
        assertEquals(5.0f, Time.time(), 0.001f);

        // Advance by exactly 1 second
        mockTime.advanceTime(1.0f);

        // Verify state at 6 seconds
        assertEquals(6.0f, Time.time(), 0.001f);
    }

    @Test
    @DisplayName("Should support performance testing")
    void shouldSupportPerformanceTesting() {
        MockTimeContext mockTime = new MockTimeContext();
        Time.setContext(mockTime);

        // Simulate slow frame
        mockTime.setFrameTimeMs(50.0f); // 20 FPS

        // Verify performance detection
        assertTrue(Time.frameTimeMs() > 33.33f, "Should detect slow frame");
        assertTrue(Time.fps() < 30.0f, "Should show low FPS");

        // Simulate fast frame
        mockTime.setFrameTimeMs(8.33f); // 120 FPS

        // Verify fast frame
        assertTrue(Time.frameTimeMs() < 16.67f, "Should detect fast frame");
        assertTrue(Time.fps() > 100.0f, "Should show high FPS");
    }

    @Test
    @DisplayName("Should work with fixed time step simulation")
    void shouldWorkWithFixedTimeStepSimulation() {
        MockTimeContext mockTime = new MockTimeContext();
        Time.setContext(mockTime);

        // Fixed time step (e.g., physics at 50 Hz)
        float fixedDeltaTime = 0.02f; // 20ms
        float accumulator = 0.0f;
        int physicsSteps = 0;

        // Simulate variable frame times
        float[] frameTimes = {0.016f, 0.032f, 0.010f, 0.050f, 0.016f};

        for (float frameTime : frameTimes) {
            mockTime.setDeltaTime(frameTime);
            Time.update();

            accumulator += Time.deltaTime();

            // Process fixed time steps
            while (accumulator >= fixedDeltaTime) {
                // Physics update here
                physicsSteps++;
                accumulator -= fixedDeltaTime;
            }
        }

        // Should have executed correct number of physics steps
        float totalTime = 0.016f + 0.032f + 0.010f + 0.050f + 0.016f;
        int expectedSteps = (int) (totalTime / fixedDeltaTime);

        assertEquals(expectedSteps, physicsSteps);
    }

    // ========================================
    // Error Handling Tests
    // ========================================

    @Test
    @DisplayName("Should handle null context gracefully")
    void shouldHandleNullContextGracefully() {
        Time.setContext(null);

        // All methods should throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> Time.update());
        assertThrows(IllegalStateException.class, () -> Time.deltaTime());
        assertThrows(IllegalStateException.class, () -> Time.time());
        assertThrows(IllegalStateException.class, () -> Time.frameCount());
        assertThrows(IllegalStateException.class, () -> Time.fps());
        assertThrows(IllegalStateException.class, () -> Time.setTimeScale(1.0f));
    }

    @Test
    @DisplayName("Should provide helpful error message when not initialized")
    void shouldProvideHelpfulErrorMessageWhenNotInitialized() {
        Time.setContext(null);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            Time.deltaTime();
        });

        assertTrue(exception.getMessage().contains("Time not initialized"));
        assertTrue(exception.getMessage().contains("initialize()"));
    }

    // ========================================
    // Thread Safety Tests (Basic)
    // ========================================

    @Test
    @DisplayName("Should handle rapid consecutive calls")
    void shouldHandleRapidConsecutiveCalls() {
        // Rapid calls to Time methods
        for (int i = 0; i < 1000; i++) {
            float dt = Time.deltaTime();
            float t = Time.time();
            long fc = Time.frameCount();

            assertTrue(dt >= 0);
            assertTrue(t >= 0);
            assertTrue(fc >= 0);
        }
    }

    @Test
    @DisplayName("Should maintain consistent state during updates")
    void shouldMaintainConsistentStateDuringUpdates() {
        for (int i = 0; i < 100; i++) {
            long frameCountBefore = Time.frameCount();
            float timeBefore = Time.time();

            Time.update();

            long frameCountAfter = Time.frameCount();
            float timeAfter = Time.time();

            // Frame count should increment by exactly 1
            assertEquals(frameCountBefore + 1, frameCountAfter);

            // Time should always increase (or stay same if paused)
            assertTrue(timeAfter >= timeBefore);
        }
    }
}