package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.MockOverlayRenderer;
import com.pocket.rpg.scenes.MockSceneManager;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SceneTransition static API.
 * Tests initialization, delegation to TransitionManager, and state queries.
 */
class SceneTransitionTest {

    private MockSceneManager sceneManager;
    private MockOverlayRenderer overlayRenderer;
    private TransitionConfig defaultConfig;
    private TransitionManager transitionManager;

    @BeforeEach
    void setUp() {
        // Reset static state before each test
        resetSceneTransitionStaticState();

        sceneManager = new MockSceneManager();
        overlayRenderer = new MockOverlayRenderer();

        defaultConfig = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(1.0f)
                .fadeColor(new Vector4f(0, 0, 0, 1))
                .build();

        transitionManager = new TransitionManager(sceneManager, overlayRenderer, defaultConfig);
    }

    @AfterEach
    void tearDown() {
        // Clean up static state after each test
        resetSceneTransitionStaticState();
    }

    /**
     * Resets the static state of SceneTransition using reflection.
     * Necessary because static state persists between tests.
     */
    private void resetSceneTransitionStaticState() {
        try {
            Field instanceField = SceneTransition.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset SceneTransition static state", e);
        }
    }

    @Test
    @DisplayName("initialize sets up static API")
    void testInitialize() {
        assertFalse(SceneTransition.isInitialized());

        SceneTransition.initialize(transitionManager);

        assertTrue(SceneTransition.isInitialized());
    }

    @Test
    @DisplayName("initialize rejects null manager")
    void testInitializeRejectsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                SceneTransition.initialize(null));
    }

    @Test
    @DisplayName("initialize throws if already initialized")
    void testInitializeThrowsIfAlreadyInitialized() {
        SceneTransition.initialize(transitionManager);

        TransitionManager anotherManager = new TransitionManager(
                sceneManager, overlayRenderer, defaultConfig);

        assertThrows(IllegalStateException.class, () ->
                SceneTransition.initialize(anotherManager));
    }

    @Test
    @DisplayName("loadScene throws if not initialized")
    void testLoadSceneThrowsIfNotInitialized() {
        assertFalse(SceneTransition.isInitialized());

        assertThrows(IllegalStateException.class, () ->
                SceneTransition.loadScene("TestScene"));
    }

    @Test
    @DisplayName("loadScene with default config delegates to manager")
    void testLoadSceneWithDefault() {
        SceneTransition.initialize(transitionManager);

        SceneTransition.loadScene("TestScene");

        assertTrue(transitionManager.isTransitioning());
        assertEquals("TestScene", transitionManager.getTargetScene());
    }

    @Test
    @DisplayName("loadScene with custom config delegates to manager")
    void testLoadSceneWithCustomConfig() {
        SceneTransition.initialize(transitionManager);

        TransitionConfig customConfig = TransitionConfig.builder()
                .fadeOutDuration(2.0f)
                .fadeInDuration(2.0f)
                .build();

        SceneTransition.loadScene("TestScene", customConfig);

        assertTrue(transitionManager.isTransitioning());
        assertEquals("TestScene", transitionManager.getTargetScene());
    }

    @Test
    @DisplayName("loadScene rejects null config")
    void testLoadSceneRejectsNullConfig() {
        SceneTransition.initialize(transitionManager);

        assertThrows(IllegalArgumentException.class, () ->
                SceneTransition.loadScene("TestScene", null));
    }

    @Test
    @DisplayName("loadSceneInstant delegates to scene manager")
    void testLoadSceneInstant() {
        SceneTransition.initialize(transitionManager);

        SceneTransition.loadSceneInstant("TestScene");

        assertTrue(sceneManager.wasSceneLoaded("TestScene"));
        assertFalse(transitionManager.isTransitioning());
    }

    @Test
    @DisplayName("isTransitioning delegates to manager")
    void testIsTransitioning() {
        SceneTransition.initialize(transitionManager);

        assertFalse(SceneTransition.isTransitioning());

        transitionManager.startTransition("TestScene");

        assertTrue(SceneTransition.isTransitioning());
    }

    @Test
    @DisplayName("isTransitioning returns false when not initialized")
    void testIsTransitioningWhenNotInitialized() {
        assertFalse(SceneTransition.isInitialized());
        assertFalse(SceneTransition.isTransitioning());
    }

    @Test
    @DisplayName("getProgress delegates to manager")
    void testGetProgress() {
        SceneTransition.initialize(transitionManager);

        assertEquals(0.0f, SceneTransition.getProgress());

        transitionManager.startTransition("TestScene");
        transitionManager.update(0.5f);

        assertTrue(SceneTransition.getProgress() > 0.0f);
    }

    @Test
    @DisplayName("getProgress returns 0 when not initialized")
    void testGetProgressWhenNotInitialized() {
        assertFalse(SceneTransition.isInitialized());
        assertEquals(0.0f, SceneTransition.getProgress());
    }

    @Test
    @DisplayName("isFadingOut delegates to manager")
    void testIsFadingOut() {
        SceneTransition.initialize(transitionManager);

        assertFalse(SceneTransition.isFadingOut());

        transitionManager.startTransition("TestScene");

        assertTrue(SceneTransition.isFadingOut());
    }

    @Test
    @DisplayName("isFadingOut returns false when not initialized")
    void testIsFadingOutWhenNotInitialized() {
        assertFalse(SceneTransition.isInitialized());
        assertFalse(SceneTransition.isFadingOut());
    }

    @Test
    @DisplayName("isFadingIn delegates to manager")
    void testIsFadingIn() {
        SceneTransition.initialize(transitionManager);

        assertFalse(SceneTransition.isFadingIn());

        // Start transition and cross the midpoint
        transitionManager.startTransition("TestScene");
        transitionManager.update(1.0f); // This crosses midpoint and enters fade in

        assertTrue(SceneTransition.isFadingIn());
    }

    @Test
    @DisplayName("isFadingIn returns false when not initialized")
    void testIsFadingInWhenNotInitialized() {
        assertFalse(SceneTransition.isInitialized());
        assertFalse(SceneTransition.isFadingIn());
    }

    @Test
    @DisplayName("getTargetScene delegates to manager")
    void testGetTargetScene() {
        SceneTransition.initialize(transitionManager);

        assertNull(SceneTransition.getTargetScene());

        transitionManager.startTransition("TestScene");

        assertEquals("TestScene", SceneTransition.getTargetScene());
    }

    @Test
    @DisplayName("getTargetScene returns null when not initialized")
    void testGetTargetSceneWhenNotInitialized() {
        assertFalse(SceneTransition.isInitialized());
        assertNull(SceneTransition.getTargetScene());
    }

    @Test
    @DisplayName("getDefaultConfig delegates to manager")
    void testGetDefaultConfig() {
        SceneTransition.initialize(transitionManager);

        TransitionConfig retrieved = SceneTransition.getDefaultConfig();

        assertNotNull(retrieved);
        assertEquals(1.0f, retrieved.getFadeOutDuration());
    }

    @Test
    @DisplayName("getDefaultConfig throws if not initialized")
    void testGetDefaultConfigThrowsIfNotInitialized() {
        assertFalse(SceneTransition.isInitialized());

        assertThrows(IllegalStateException.class, () ->
                SceneTransition.getDefaultConfig());
    }

    @Test
    @DisplayName("cancelTransition delegates to manager")
    void testCancelTransition() {
        SceneTransition.initialize(transitionManager);

        transitionManager.startTransition("TestScene");
        assertTrue(SceneTransition.isTransitioning());

        SceneTransition.cancelTransition();

        assertFalse(SceneTransition.isTransitioning());
    }

    @Test
    @DisplayName("cancelTransition throws if not initialized")
    void testCancelTransitionThrowsIfNotInitialized() {
        assertFalse(SceneTransition.isInitialized());

        assertThrows(IllegalStateException.class, () ->
                SceneTransition.cancelTransition());
    }

    @Test
    @DisplayName("getManager returns underlying manager")
    void testGetManager() {
        SceneTransition.initialize(transitionManager);

        TransitionManager retrieved = SceneTransition.getManager();

        assertSame(transitionManager, retrieved);
    }

    @Test
    @DisplayName("getManager throws if not initialized")
    void testGetManagerThrowsIfNotInitialized() {
        assertFalse(SceneTransition.isInitialized());

        assertThrows(IllegalStateException.class, () ->
                SceneTransition.getManager());
    }

    @Test
    @DisplayName("Cannot instantiate SceneTransition")
    void testCannotInstantiate() {
        // The private constructor throws AssertionError, but reflection wraps it
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            java.lang.reflect.Constructor<?> constructor =
                    SceneTransition.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }

    @Test
    @DisplayName("Full workflow: initialize, load, query, complete")
    void testFullWorkflow() {
        // Initialize
        SceneTransition.initialize(transitionManager);
        assertTrue(SceneTransition.isInitialized());

        // Load scene
        SceneTransition.loadScene("TestScene");
        assertTrue(SceneTransition.isTransitioning());
        assertEquals("TestScene", SceneTransition.getTargetScene());
        assertTrue(SceneTransition.isFadingOut());

        // Progress through fade out (still in fade out at 0.5s)
        transitionManager.update(0.5f);
        assertTrue(SceneTransition.getProgress() > 0.0f);
        assertTrue(SceneTransition.isFadingOut());

        // Cross midpoint (scene loads in this update call at 1.0s)
        transitionManager.update(0.5f);

        // Scene should be loaded NOW
        assertTrue(sceneManager.wasSceneLoaded("TestScene"));

        // Should be in fade in phase NOW
        assertTrue(SceneTransition.isFadingIn());

        // Complete fade in (need > 1.0 to complete)
        transitionManager.update(1.1f);
        assertFalse(SceneTransition.isTransitioning());
        // After completion, getProgress returns 0.0 (currentTransition is null)
        assertEquals(0.0f, SceneTransition.getProgress(), 0.01f);
    }

    @Test
    @DisplayName("Multiple scene loads work correctly")
    void testMultipleSceneLoads() {
        SceneTransition.initialize(transitionManager);

        // First load
        SceneTransition.loadScene("Scene1");
        transitionManager.update(1.0f); // Cross midpoint
        transitionManager.update(1.1f); // Complete it
        assertTrue(sceneManager.wasSceneLoaded("Scene1"));

        // Second load
        SceneTransition.loadScene("Scene2");
        transitionManager.update(1.0f); // Cross midpoint
        transitionManager.update(1.1f); // Complete it
        assertTrue(sceneManager.wasSceneLoaded("Scene2"));

        // Both should be loaded
        assertEquals(2, sceneManager.getLoadSceneCallCount());
    }

    @Test
    @DisplayName("Instant load bypasses transition")
    void testInstantLoadBypassesTransition() {
        SceneTransition.initialize(transitionManager);

        SceneTransition.loadSceneInstant("TestScene");

        // Scene should be loaded immediately
        assertTrue(sceneManager.wasSceneLoaded("TestScene"));

        // No transition should be active
        assertFalse(SceneTransition.isTransitioning());
    }
}