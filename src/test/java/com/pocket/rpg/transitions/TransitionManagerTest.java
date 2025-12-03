package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.MockOverlayRenderer;
import com.pocket.rpg.scenes.MockSceneManager;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransitionManager.
 * Tests state management, scene switching, and lifecycle.
 */
class TransitionManagerTest {

    private MockSceneManager sceneManager;
    private MockOverlayRenderer overlayRenderer;
    private TransitionConfig defaultConfig;
    private TransitionManager transitionManager;

    @BeforeEach
    void setUp() {
        sceneManager = new MockSceneManager();
        overlayRenderer = new MockOverlayRenderer();

        defaultConfig = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(1.0f)
                .fadeColor(new Vector4f(0, 0, 0, 1))
                .build();

        transitionManager = new TransitionManager(sceneManager, overlayRenderer, defaultConfig);
    }

    @Test
    @DisplayName("Constructor validates parameters")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new TransitionManager(null, overlayRenderer, defaultConfig));

        assertThrows(IllegalArgumentException.class, () ->
                new TransitionManager(sceneManager, null, defaultConfig));

        assertThrows(IllegalArgumentException.class, () ->
                new TransitionManager(sceneManager, overlayRenderer, null));
    }

    @Test
    @DisplayName("Constructor validates default config")
    void testConstructorValidatesConfig() {
        TransitionConfig invalidConfig = TransitionConfig.builder()
                .fadeOutDuration(-1.0f)
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                new TransitionManager(sceneManager, overlayRenderer, invalidConfig));
    }

    @Test
    @DisplayName("Initial state is IDLE and not transitioning")
    void testInitialState() {
        assertFalse(transitionManager.isTransitioning());
        assertNull(transitionManager.getTargetScene());
        assertEquals(0.0f, transitionManager.getProgress());
        assertFalse(transitionManager.isFadingOut());
        assertFalse(transitionManager.isFadingIn());
    }

    @Test
    @DisplayName("startTransition with default config starts transition")
    void testStartTransitionWithDefault() {
        transitionManager.startTransition("TestScene");

        assertTrue(transitionManager.isTransitioning());
        assertEquals("TestScene", transitionManager.getTargetScene());
        assertTrue(transitionManager.isFadingOut());
    }

    @Test
    @DisplayName("startTransition with custom config uses custom")
    void testStartTransitionWithCustomConfig() {
        TransitionConfig customConfig = TransitionConfig.builder()
                .fadeOutDuration(2.0f)
                .fadeInDuration(2.0f)
                .build();

        transitionManager.startTransition("TestScene", customConfig);

        assertTrue(transitionManager.isTransitioning());
        assertEquals("TestScene", transitionManager.getTargetScene());
    }

    @Test
    @DisplayName("startTransition rejects null scene name")
    void testStartTransitionRejectsNullSceneName() {
        assertThrows(IllegalArgumentException.class, () ->
                transitionManager.startTransition(null));
    }

    @Test
    @DisplayName("startTransition rejects empty scene name")
    void testStartTransitionRejectsEmptySceneName() {
        assertThrows(IllegalArgumentException.class, () ->
                transitionManager.startTransition(""));
    }

    @Test
    @DisplayName("startTransition rejects null config")
    void testStartTransitionRejectsNullConfig() {
        assertThrows(IllegalArgumentException.class, () ->
                transitionManager.startTransition("TestScene", null));
    }

    @Test
    @DisplayName("startTransition fails if already transitioning")
    void testStartTransitionFailsIfAlreadyTransitioning() {
        transitionManager.startTransition("Scene1");

        assertThrows(IllegalStateException.class, () ->
                transitionManager.startTransition("Scene2"));
    }

    @Test
    @DisplayName("update does nothing when IDLE")
    void testUpdateDoesNothingWhenIdle() {
        assertFalse(transitionManager.isTransitioning());

        transitionManager.update(1.0f);

        assertFalse(transitionManager.isTransitioning());
        assertEquals(0, sceneManager.getLoadSceneCallCount());
    }

    @Test
    @DisplayName("update progresses through fade out")
    void testUpdateProgressesFadeOut() {
        transitionManager.startTransition("TestScene");

        assertTrue(transitionManager.isFadingOut());
        assertEquals(0.0f, transitionManager.getProgress(), 0.01f);

        transitionManager.update(0.5f);

        assertTrue(transitionManager.isTransitioning());
        assertTrue(transitionManager.getProgress() > 0.0f);
        assertTrue(transitionManager.isFadingOut()); // Still fading out at 0.5s
    }

    @Test
    @DisplayName("update triggers scene switch when reaching midpoint")
    void testUpdateTriggersSceneSwitch() {
        transitionManager.startTransition("TestScene");

        // CRITICAL: Scene loads in the SAME update() call that crosses fadeOutDuration
        // When update(1.0f) is called:
        // 1. currentTransition.update(1.0f) makes currentTime = 1.0
        // 2. isAtMidpoint() returns true (because currentTime >= 1.0 and !midpointReached)
        // 3. State switches to SWITCHING
        // 4. performSceneSwitch() loads the scene
        // 5. State switches to FADING_IN
        // All in the same update() call!

        transitionManager.update(1.0f);

        // Scene should be loaded NOW
        assertEquals(1, sceneManager.getLoadSceneCallCount());
        assertTrue(sceneManager.wasSceneLoaded("TestScene"));

        // Should be in fade in phase NOW
        assertTrue(transitionManager.isFadingIn());
        assertFalse(transitionManager.isFadingOut());
    }

    @Test
    @DisplayName("update completes transition")
    void testUpdateCompletesTransition() {
        transitionManager.startTransition("TestScene");

        // Update with exactly total duration (1.0 + 1.0 = 2.0)
        transitionManager.update(2.0f);

        // Should be back to IDLE
        assertFalse(transitionManager.isTransitioning());
        assertNull(transitionManager.getTargetScene());
        assertFalse(transitionManager.isFadingOut());
        assertFalse(transitionManager.isFadingIn());
    }

    @Test
    @DisplayName("render does nothing when IDLE")
    void testRenderDoesNothingWhenIdle() {
        assertFalse(transitionManager.isTransitioning());

        transitionManager.render();

        // Should not interact with overlay renderer
        assertEquals(0, overlayRenderer.getDrawCallCount());
    }

    @Test
    @DisplayName("render delegates to transition when active")
    void testRenderDelegatesToTransition() {
        transitionManager.startTransition("TestScene");
        transitionManager.update(0.5f);

        transitionManager.render();

        // Should have called overlay renderer (through transition)
        assertTrue(overlayRenderer.getDrawCallCount() > 0);
    }

    @Test
    @DisplayName("getProgress returns correct values")
    void testGetProgress() {
        transitionManager.startTransition("TestScene");

        assertEquals(0.0f, transitionManager.getProgress(), 0.01f);

        transitionManager.update(0.5f);
        assertTrue(transitionManager.getProgress() > 0.0f);
        assertTrue(transitionManager.getProgress() < 0.5f);

        transitionManager.update(0.5f);
        assertEquals(0.5f, transitionManager.getProgress(), 0.01f);

        transitionManager.update(1.0f);
        assertEquals(1.0f, transitionManager.getProgress(), 0.01f);
    }

    @Test
    @DisplayName("getDefaultConfig returns copy")
    void testGetDefaultConfigReturnsCopy() {
        TransitionConfig retrieved1 = transitionManager.getDefaultConfig();
        TransitionConfig retrieved2 = transitionManager.getDefaultConfig();

        assertNotSame(retrieved1, retrieved2);
        assertEquals(retrieved1.getFadeOutDuration(), retrieved2.getFadeOutDuration());
    }

    @Test
    @DisplayName("getDefaultConfig returns defensive copy")
    void testGetDefaultConfigDefensiveCopy() {
        TransitionConfig retrieved = transitionManager.getDefaultConfig();
        retrieved.setFadeOutDuration(999.0f);

        // Original should be unchanged
        TransitionConfig newRetrieval = transitionManager.getDefaultConfig();
        assertEquals(1.0f, newRetrieval.getFadeOutDuration());
    }

    @Test
    @DisplayName("cancelTransition stops active transition")
    void testCancelTransition() {
        transitionManager.startTransition("TestScene");
        assertTrue(transitionManager.isTransitioning());

        transitionManager.cancelTransition();

        assertFalse(transitionManager.isTransitioning());
        assertNull(transitionManager.getTargetScene());
    }

    @Test
    @DisplayName("cancelTransition is safe when not transitioning")
    void testCancelTransitionWhenNotTransitioning() {
        assertFalse(transitionManager.isTransitioning());

        assertDoesNotThrow(() -> transitionManager.cancelTransition());

        assertFalse(transitionManager.isTransitioning());
    }

    @Test
    @DisplayName("getSceneManager returns injected instance")
    void testGetSceneManager() {
        // Package-private method for SceneTransition static API
        assertSame(sceneManager, transitionManager.getSceneManager());
    }

    @Test
    @DisplayName("Transition with FADE_WITH_TEXT type works")
    void testFadeWithTextTransition() {
        TransitionConfig textConfig = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(1.0f)
                .transitionText("Loading...")
                .type(TransitionConfig.TransitionType.FADE_WITH_TEXT)
                .build();

        transitionManager.startTransition("TestScene", textConfig);

        assertTrue(transitionManager.isTransitioning());

        // Update and verify it progresses
        transitionManager.update(0.5f);
        assertTrue(transitionManager.getProgress() > 0.0f);
    }

    @Test
    @DisplayName("Multiple transitions in sequence work")
    void testMultipleTransitionsInSequence() {
        // First transition
        transitionManager.startTransition("Scene1");
        transitionManager.update(2.0f); // Complete first transition
        assertFalse(transitionManager.isTransitioning());

        // Second transition
        transitionManager.startTransition("Scene2");
        assertTrue(transitionManager.isTransitioning());
        assertEquals("Scene2", transitionManager.getTargetScene());

        transitionManager.update(2.0f); // Complete second transition
        assertFalse(transitionManager.isTransitioning());

        // Verify both scenes were loaded
        assertTrue(sceneManager.wasSceneLoaded("Scene1"));
        assertTrue(sceneManager.wasSceneLoaded("Scene2"));
        assertEquals(2, sceneManager.getLoadSceneCallCount());
    }

    @Test
    @DisplayName("Very fast transition completes quickly")
    void testVeryFastTransition() {
        TransitionConfig fastConfig = TransitionConfig.builder()
                .fadeOutDuration(0.01f)
                .fadeInDuration(0.01f)
                .build();

        transitionManager.startTransition("TestScene", fastConfig);

        transitionManager.update(0.02f); // Exactly the total duration

        assertFalse(transitionManager.isTransitioning());
        assertEquals(1, sceneManager.getLoadSceneCallCount());
        assertTrue(sceneManager.wasSceneLoaded("TestScene"));
    }

    @Test
    @DisplayName("Scene is loaded exactly once per transition")
    void testSceneLoadedOnlyOnce() {
        transitionManager.startTransition("TestScene");

        // Update multiple times but scene should only load once
        transitionManager.update(0.5f);  // 0.5s - still in fade out
        assertEquals(0, sceneManager.getLoadCountForScene("TestScene")); // Not yet loaded

        transitionManager.update(0.5f);  // 1.0s - crosses midpoint, scene loads
        assertEquals(1, sceneManager.getLoadCountForScene("TestScene")); // Loaded once

        transitionManager.update(0.5f);  // 1.5s - in fade in
        assertEquals(1, sceneManager.getLoadCountForScene("TestScene")); // Still only once

        transitionManager.update(0.5f);  // 2.0s - complete
        assertEquals(1, sceneManager.getLoadCountForScene("TestScene")); // Still only once
    }

    @Test
    @DisplayName("State queries return correct values through lifecycle")
    void testStateQueriesThroughLifecycle() {
        // IDLE state
        assertFalse(transitionManager.isTransitioning());
        assertFalse(transitionManager.isFadingOut());
        assertFalse(transitionManager.isFadingIn());
        assertEquals(0.0f, transitionManager.getProgress());
        assertNull(transitionManager.getTargetScene());

        // Start transition - FADING_OUT
        transitionManager.startTransition("TestScene");
        assertTrue(transitionManager.isTransitioning());
        assertTrue(transitionManager.isFadingOut());
        assertFalse(transitionManager.isFadingIn());
        assertEquals("TestScene", transitionManager.getTargetScene());

        // Progress to FADING_IN (scene loads at 1.0s mark)
        transitionManager.update(1.0f);
        assertTrue(transitionManager.isTransitioning());
        assertFalse(transitionManager.isFadingOut());
        assertTrue(transitionManager.isFadingIn());
        assertEquals("TestScene", transitionManager.getTargetScene());

        // Complete - back to IDLE
        transitionManager.update(1.0f);
        assertFalse(transitionManager.isTransitioning());
        assertFalse(transitionManager.isFadingOut());
        assertFalse(transitionManager.isFadingIn());
        assertEquals(1.0f, transitionManager.getProgress());
        assertNull(transitionManager.getTargetScene());
    }
}