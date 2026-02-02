package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.MockOverlayRenderer;
import com.pocket.rpg.scenes.MockSceneManager;
import com.pocket.rpg.scenes.transitions.TransitionManager;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for spawnId threading through TransitionManager.
 */
class TransitionManagerSpawnTest {

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
    @DisplayName("startTransition with spawnId starts transition")
    void startTransitionWithSpawnId() {
        transitionManager.startTransition("Cave", "entrance");

        assertTrue(transitionManager.isTransitioning());
        assertEquals("Cave", transitionManager.getTargetScene());
    }

    @Test
    @DisplayName("startTransition with spawnId and config starts transition")
    void startTransitionWithSpawnIdAndConfig() {
        TransitionConfig customConfig = TransitionConfig.builder()
                .fadeOutDuration(0.5f)
                .fadeInDuration(0.5f)
                .build();

        transitionManager.startTransition("Cave", "entrance", customConfig);

        assertTrue(transitionManager.isTransitioning());
        assertEquals("Cave", transitionManager.getTargetScene());
    }

    @Test
    @DisplayName("startTransition with null spawnId works like normal transition")
    void startTransitionWithNullSpawnId() {
        transitionManager.startTransition("Cave", (String) null);

        assertTrue(transitionManager.isTransitioning());
        assertEquals("Cave", transitionManager.getTargetScene());

        // Complete transition
        transitionManager.update(1.0f); // midpoint
        transitionManager.update(1.1f); // complete

        assertFalse(transitionManager.isTransitioning());
        assertTrue(sceneManager.wasSceneLoaded("Cave"));
    }

    @Test
    @DisplayName("spawnId is cleared after transition completes")
    void spawnIdClearedAfterCompletion() {
        transitionManager.startTransition("Cave", "entrance");

        // Complete full transition
        transitionManager.update(1.0f); // midpoint - scene loads
        transitionManager.update(1.1f); // complete

        assertFalse(transitionManager.isTransitioning());

        // Start a new transition without spawnId
        transitionManager.startTransition("Town");
        transitionManager.update(1.0f); // midpoint

        // Should have loaded Town (spawnId from previous transition should not leak)
        assertTrue(sceneManager.wasSceneLoaded("Town"));
    }

    @Test
    @DisplayName("transition with spawnId loads scene at midpoint")
    void transitionWithSpawnIdLoadsScene() {
        transitionManager.startTransition("Cave", "entrance");

        // Before midpoint
        transitionManager.update(0.5f);
        assertFalse(sceneManager.wasSceneLoaded("Cave"));

        // At midpoint
        transitionManager.update(0.5f);
        assertTrue(sceneManager.wasSceneLoaded("Cave"));
    }

    @Test
    @DisplayName("cancelTransition clears spawnId — P0 regression")
    void cancelTransitionClearsSpawnId() {
        transitionManager.startTransition("Cave", "entrance");
        assertTrue(transitionManager.isTransitioning());

        // Cancel mid-transition
        transitionManager.cancelTransition();
        assertFalse(transitionManager.isTransitioning());

        // Start new transition without spawnId
        transitionManager.startTransition("Town");

        // Complete it
        transitionManager.update(1.0f); // midpoint
        transitionManager.update(1.1f); // complete

        // Town should be loaded, and the old spawnId "entrance" should NOT have leaked
        assertTrue(sceneManager.wasSceneLoaded("Town"));
        assertNull(sceneManager.getLastSpawnId(),
                "SpawnId from cancelled transition should not leak to next transition");
    }

    @Test
    @DisplayName("spawnId not set when transition rejected (already in progress) — P1 regression")
    void spawnIdNotSetWhenTransitionRejected() {
        // Start first transition
        transitionManager.startTransition("Cave", "entrance");
        assertTrue(transitionManager.isTransitioning());

        // Attempt second transition while first is active — should be rejected
        transitionManager.startTransition("Town", "market");

        // Complete the original transition
        transitionManager.update(1.0f); // midpoint — loads Cave
        transitionManager.update(1.1f); // complete

        // Cave should have been loaded with "entrance", not "market"
        assertTrue(sceneManager.wasSceneLoaded("Cave"));
        assertEquals("entrance", sceneManager.getLastSpawnId(),
                "Rejected transition's spawnId should not overwrite the active one");
    }

    @Test
    @DisplayName("spawnId passes through to SceneManager.loadScene at midpoint")
    void spawnIdPassedToSceneManager() {
        transitionManager.startTransition("Cave", "entrance");

        // Complete to midpoint where scene loads
        transitionManager.update(1.0f);

        assertTrue(sceneManager.wasSceneLoaded("Cave"));
        assertEquals("entrance", sceneManager.getLastSpawnId(),
                "SpawnId should be passed through to SceneManager.loadScene");
    }
}
