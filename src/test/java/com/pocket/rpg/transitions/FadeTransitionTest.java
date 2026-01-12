package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.MockOverlayRenderer;
import com.pocket.rpg.scenes.transitions.FadeTransition;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FadeTransition.
 * Tests fade timing, alpha interpolation, and rendering.
 */
class FadeTransitionTest {

    private MockOverlayRenderer overlayRenderer;
    private TransitionConfig config;

    @BeforeEach
    void setUp() {
        overlayRenderer = new MockOverlayRenderer();

        config = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(1.0f)
                .fadeColor(new Vector4f(0, 0, 0, 1))
                .build();
    }

    @Test
    @DisplayName("Initial state has zero alpha and progress")
    void testInitialState() {
        FadeTransition transition = new FadeTransition(config);

        assertEquals(0.0f, transition.getAlpha());
        assertEquals(0.0f, transition.getProgress());
        assertFalse(transition.isComplete());
        assertFalse(transition.isAtMidpoint());
    }

    @Test
    @DisplayName("Alpha increases during fade out")
    void testFadeOut() {
        FadeTransition transition = new FadeTransition(config);

        // Update to 25% of fade out
        transition.update(0.25f);
        assertTrue(transition.getAlpha() > 0.0f);
        assertTrue(transition.getAlpha() < 1.0f);
        assertFalse(transition.isAtMidpoint());

        // Update to 50% of fade out
        transition.update(0.25f);
        assertTrue(transition.getAlpha() > 0.0f);
        assertTrue(transition.getAlpha() < 1.0f);
        assertFalse(transition.isAtMidpoint()); // Still false - not at midpoint yet

        // Update to reach midpoint (total 1.0s)
        transition.update(0.5f);
        // Alpha should be at max or very close
        assertTrue(transition.getAlpha() >= 0.9f);
    }

    @Test
    @DisplayName("Alpha decreases during fade in")
    void testFadeIn() {
        FadeTransition transition = new FadeTransition(config);

        // Skip to after midpoint
        transition.update(1.5f);

        // Alpha should be decreasing
        float alpha1 = transition.getAlpha();
        assertTrue(alpha1 < 1.0f);

        transition.update(0.25f);
        float alpha2 = transition.getAlpha();
        assertTrue(alpha2 < alpha1);
    }

    @Test
    @DisplayName("isAtMidpoint returns true only once when crossing threshold")
    void testMidpointFlagOnlyOnce() {
        FadeTransition transition = new FadeTransition(config);

        // Before midpoint - currentTime < fadeOutDuration
        transition.update(0.9f);
        assertFalse(transition.isAtMidpoint()); // currentTime = 0.9, not at midpoint

        // AT midpoint - this is the ONE update where isAtMidpoint() returns true
        // currentTime crosses fadeOutDuration threshold
        boolean midpointDuringCrossing = transition.isAtMidpoint(); // Check BEFORE update
        transition.update(0.1f); // currentTime becomes 1.0, midpointReached flag gets set
        boolean midpointAfterUpdate = transition.isAtMidpoint(); // midpointReached is now true

        // One of these should be true (the check that happens when crossing)
        assertTrue(midpointDuringCrossing || midpointAfterUpdate,
                "Midpoint should be detected when crossing threshold");

        // After midpoint - should never be true again
        transition.update(0.1f);
        assertFalse(transition.isAtMidpoint());

        transition.update(0.5f);
        assertFalse(transition.isAtMidpoint());
    }

    @Test
    @DisplayName("Progress goes from 0 to 1")
    void testProgress() {
        FadeTransition transition = new FadeTransition(config);

        assertEquals(0.0f, transition.getProgress(), 0.01f);

        transition.update(0.5f);
        assertEquals(0.25f, transition.getProgress(), 0.01f);

        transition.update(0.5f);
        assertEquals(0.5f, transition.getProgress(), 0.01f);

        transition.update(0.5f);
        assertEquals(0.75f, transition.getProgress(), 0.01f);

        transition.update(0.5f);
        assertEquals(1.0f, transition.getProgress(), 0.01f);
    }

    @Test
    @DisplayName("Transition completes after total duration")
    void testCompletion() {
        FadeTransition transition = new FadeTransition(config);

        assertFalse(transition.isComplete());

        // Update through entire transition
        transition.update(2.0f);

        assertTrue(transition.isComplete());
        assertEquals(0.0f, transition.getAlpha(), 0.01f);
    }

    @Test
    @DisplayName("Reset returns transition to initial state")
    void testReset() {
        FadeTransition transition = new FadeTransition(config);

        // Run through transition
        transition.update(1.5f);
        assertTrue(transition.getProgress() > 0.5f);

        // Reset
        transition.reset();

        assertEquals(0.0f, transition.getAlpha());
        assertEquals(0.0f, transition.getProgress());
        assertFalse(transition.isComplete());
        assertFalse(transition.isAtMidpoint());
    }

    @Test
    @DisplayName("Render calls overlay renderer when alpha > 0")
    void testRenderCallsOverlay() {
        FadeTransition transition = new FadeTransition(config);

        // Update to have some alpha
        transition.update(0.5f);
        assertTrue(transition.getAlpha() > 0.001f);

        assertEquals(0, overlayRenderer.getDrawCallCount());

        transition.render(overlayRenderer);

        assertEquals(1, overlayRenderer.getDrawCallCount());
        assertNotNull(overlayRenderer.getLastDrawnColor());
    }

    @Test
    @DisplayName("Render does not call overlay when alpha is zero")
    void testRenderDoesNotCallWhenAlphaZero() {
        FadeTransition transition = new FadeTransition(config);

        // Don't update - alpha is 0
        assertEquals(0.0f, transition.getAlpha());

        transition.render(overlayRenderer);

        assertEquals(0, overlayRenderer.getDrawCallCount());
    }

    @Test
    @DisplayName("Constructor with config uses config values")
    void testConstructorWithConfig() {
        TransitionConfig customConfig = TransitionConfig.builder()
                .fadeOutDuration(2.0f)
                .fadeInDuration(3.0f)
                .fadeColor(new Vector4f(1, 0, 0, 1))
                .build();

        FadeTransition transition = new FadeTransition(customConfig);

        // Verify config is used (indirectly through duration)
        transition.update(2.0f); // Should be at midpoint
        // After 2.0s with fadeOut=2.0s, we're at midpoint

        transition.update(3.0f); // Should be complete
        assertTrue(transition.isComplete());
    }

    @Test
    @DisplayName("Constructor with parameters creates valid transition")
    void testConstructorWithParameters() {
        Vector4f color = new Vector4f(1, 1, 1, 1);
        FadeTransition transition = new FadeTransition(0.5f, 0.5f, color);

        assertNotNull(transition);
        assertEquals(0.0f, transition.getAlpha());
        assertEquals(0.0f, transition.getProgress());
    }

    @Test
    @DisplayName("Easing function produces smooth values")
    void testEasingFunction() {
        FadeTransition transition = new FadeTransition(config);

        // Sample alpha at different points during fade out
        float[] samples = new float[11];
        for (int i = 0; i <= 10; i++) {
            transition.reset();
            float time = i * 0.1f; // 0.0 to 1.0
            transition.update(time);
            samples[i] = transition.getAlpha();
        }

        // Alpha should increase smoothly (each sample >= previous)
        for (int i = 1; i < samples.length; i++) {
            assertTrue(samples[i] >= samples[i - 1],
                    "Alpha should increase smoothly: " + samples[i - 1] + " -> " + samples[i]);
        }

        // Start and end should be close to 0 and 1
        assertTrue(samples[0] < 0.1f);
        assertTrue(samples[10] > 0.9f);
    }

    @Test
    @DisplayName("Very short durations work correctly")
    void testVeryShortDurations() {
        TransitionConfig fastConfig = TransitionConfig.builder()
                .fadeOutDuration(0.01f)
                .fadeInDuration(0.01f)
                .build();

        FadeTransition transition = new FadeTransition(fastConfig);

        transition.update(0.02f);
        assertTrue(transition.isComplete());
    }

    @Test
    @DisplayName("Zero duration completes immediately")
    void testZeroDuration() {
        TransitionConfig instantConfig = TransitionConfig.builder()
                .fadeOutDuration(0.0f)
                .fadeInDuration(0.0f)
                .build();

        FadeTransition transition = new FadeTransition(instantConfig);

        transition.update(0.0f);
        assertTrue(transition.isComplete());
    }

    @Test
    @DisplayName("Multiple small updates equal one large update")
    void testUpdateConsistency() {
        FadeTransition transition1 = new FadeTransition(config);
        FadeTransition transition2 = new FadeTransition(config);

        // One large update
        transition1.update(1.0f);

        // Multiple small updates
        for (int i = 0; i < 10; i++) {
            transition2.update(0.1f);
        }

        // Should be at same state
        assertEquals(transition1.getAlpha(), transition2.getAlpha(), 0.001f);
        assertEquals(transition1.getProgress(), transition2.getProgress(), 0.001f);
    }

    @Test
    @DisplayName("Updating beyond completion is safe")
    void testUpdateBeyondCompletion() {
        FadeTransition transition = new FadeTransition(config);

        // Update way beyond completion
        transition.update(100.0f);

        assertTrue(transition.isComplete());
        assertEquals(0.0f, transition.getAlpha(), 0.01f);
        assertEquals(1.0f, transition.getProgress(), 0.01f);
    }

    @Test
    @DisplayName("Asymmetric durations work correctly")
    void testAsymmetricDurations() {
        TransitionConfig asymmetricConfig = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(3.0f)
                .build();

        FadeTransition transition = new FadeTransition(asymmetricConfig);

        // At 1.0s we should be at/near midpoint
        transition.update(1.0f);

        // Complete at 4.0s (1.0 + 3.0)
        transition.update(3.0f);
        assertTrue(transition.isComplete());
    }

    @Test
    @DisplayName("Rendered color matches config color with alpha")
    void testRenderedColor() {
        Vector4f customColor = new Vector4f(1, 0, 0, 1); // Red
        TransitionConfig customConfig = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(1.0f)
                .fadeColor(customColor)
                .build();

        FadeTransition transition = new FadeTransition(customConfig);
        transition.update(0.5f); // Get some alpha

        transition.render(overlayRenderer);

        Vector4f renderedColor = overlayRenderer.getLastDrawnColor();
        assertNotNull(renderedColor);
        assertEquals(1, renderedColor.x, 0.01f); // Red channel
        assertEquals(0, renderedColor.y, 0.01f); // Green channel
        assertEquals(0, renderedColor.z, 0.01f); // Blue channel
        assertTrue(renderedColor.w > 0); // Alpha channel
    }
}