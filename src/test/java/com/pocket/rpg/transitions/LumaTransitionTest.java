package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.MockOverlayRenderer;
import com.pocket.rpg.scenes.transitions.LumaTransition;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LumaTransition.
 * Tests cutoff progression, midpoint detection, rendering, and completion.
 */
class LumaTransitionTest {

    private MockOverlayRenderer overlayRenderer;
    private static final int TEST_TEXTURE_ID = 42;

    @BeforeEach
    void setUp() {
        overlayRenderer = new MockOverlayRenderer();
    }

    @Test
    @DisplayName("Constructor from config creates valid transition")
    void testConstructorFromConfig() {
        TransitionConfig config = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(1.0f)
                .fadeColor(new Vector4f(0, 0, 0, 1))
                .build();

        LumaTransition transition = new LumaTransition(config, TEST_TEXTURE_ID);

        assertNotNull(transition);
        assertEquals(TEST_TEXTURE_ID, transition.getLumaTextureId());
        assertFalse(transition.isComplete());
        assertFalse(transition.isAtMidpoint());
        assertEquals(0.0f, transition.getProgress(), 0.01f);
    }

    @Test
    @DisplayName("Constructor with parameters creates valid transition")
    void testConstructorWithParams() {
        LumaTransition transition = new LumaTransition(
                0.5f, 0.5f, new Vector4f(1, 1, 1, 1), TEST_TEXTURE_ID);

        assertNotNull(transition);
        assertEquals(TEST_TEXTURE_ID, transition.getLumaTextureId());
    }

    @Test
    @DisplayName("Render calls drawLumaWipe on overlay renderer")
    void testRenderCallsDrawLumaWipe() {
        LumaTransition transition = new LumaTransition(
                1.0f, 1.0f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        // Advance past 0 so cutoff > 0
        transition.update(0.5f);
        transition.render(overlayRenderer);

        assertEquals(1, overlayRenderer.getLumaWipeCallCount());
        assertEquals(TEST_TEXTURE_ID, overlayRenderer.getLastLumaTextureId());
        assertTrue(overlayRenderer.getLastLumaCutoff() > 0.0f);
        assertTrue(overlayRenderer.getLastLumaCutoff() <= 1.0f);
    }

    @Test
    @DisplayName("Cutoff increases during fade out phase")
    void testCutoffIncreasesDuringFadeOut() {
        LumaTransition transition = new LumaTransition(
                1.0f, 1.0f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        // At start: cutoff should be near 0
        transition.update(0.01f);
        transition.render(overlayRenderer);
        float cutoffEarly = overlayRenderer.getLastLumaCutoff();

        overlayRenderer.reset();

        // At mid-fade-out: cutoff should be higher
        transition.update(0.49f); // now at 0.5s
        transition.render(overlayRenderer);
        float cutoffMid = overlayRenderer.getLastLumaCutoff();

        assertTrue(cutoffMid > cutoffEarly, "Cutoff should increase during fade out");
    }

    @Test
    @DisplayName("Cutoff reaches 1.0 at midpoint")
    void testCutoffReachesFull() {
        LumaTransition transition = new LumaTransition(
                1.0f, 1.0f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        // Update to exactly midpoint
        transition.update(1.0f);
        transition.render(overlayRenderer);

        // At midpoint, cutoff should be 1.0 (before fade-in starts decreasing)
        assertEquals(1.0f, overlayRenderer.getLastLumaCutoff(), 0.01f);
    }

    @Test
    @DisplayName("Cutoff decreases during fade in phase")
    void testCutoffDecreasesDuringFadeIn() {
        LumaTransition transition = new LumaTransition(
                1.0f, 1.0f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        // Cross midpoint
        transition.update(1.0f);
        overlayRenderer.reset();

        // Early fade-in: cutoff should be near 1.0
        transition.update(0.1f);
        transition.render(overlayRenderer);
        float cutoffEarlyFadeIn = overlayRenderer.getLastLumaCutoff();

        overlayRenderer.reset();

        // Later in fade-in: cutoff should be lower
        transition.update(0.5f);
        transition.render(overlayRenderer);
        float cutoffLateFadeIn = overlayRenderer.getLastLumaCutoff();

        assertTrue(cutoffLateFadeIn < cutoffEarlyFadeIn,
                "Cutoff should decrease during fade in");
    }

    @Test
    @DisplayName("Midpoint is detected correctly")
    void testMidpointDetection() {
        LumaTransition transition = new LumaTransition(
                1.0f, 1.0f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        // Before midpoint
        transition.update(0.5f);
        assertFalse(transition.isAtMidpoint());

        // At midpoint
        transition.update(0.5f);
        assertTrue(transition.isAtMidpoint());

        // After midpoint (should not report again)
        assertFalse(transition.isAtMidpoint());
    }

    @Test
    @DisplayName("Transition completes after total duration")
    void testCompletion() {
        LumaTransition transition = new LumaTransition(
                0.5f, 0.5f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        assertFalse(transition.isComplete());

        // Cross midpoint
        transition.update(0.5f);
        assertFalse(transition.isComplete());

        // Complete fade-in
        transition.update(0.6f);
        assertTrue(transition.isComplete());
    }

    @Test
    @DisplayName("Reset returns transition to initial state")
    void testReset() {
        LumaTransition transition = new LumaTransition(
                1.0f, 1.0f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        // Progress the transition
        transition.update(1.5f);

        // Reset
        transition.reset();

        assertEquals(0.0f, transition.getAlpha());
        assertFalse(transition.isComplete());
        assertFalse(transition.isAtMidpoint());
        assertEquals(0.0f, transition.getProgress(), 0.01f);
    }

    @Test
    @DisplayName("No render when cutoff is zero")
    void testNoRenderWhenCutoffZero() {
        LumaTransition transition = new LumaTransition(
                1.0f, 1.0f, new Vector4f(0, 0, 0, 1), TEST_TEXTURE_ID);

        // At time 0, cutoff should be 0 - nothing to render
        transition.render(overlayRenderer);
        assertEquals(0, overlayRenderer.getLumaWipeCallCount());
    }
}
