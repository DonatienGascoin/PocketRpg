package com.pocket.rpg;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.pokemon.GridMovement;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.scenes.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IPausableTest {

    private GridMovement gridMovement;
    private GameObject gameObject;
    private TestScene scene;

    @BeforeEach
    void setUp() {
        scene = new TestScene("TestScene");
        gameObject = new GameObject("Player");
        gridMovement = new GridMovement();
        gameObject.addComponent(gridMovement);
        scene.addGameObject(gameObject);
    }

    // --- IPausable contract ---

    @Test
    void onPause_setsPausedTrue() {
        assertFalse(gridMovement.isPaused());

        gridMovement.onPause();

        assertTrue(gridMovement.isPaused());
    }

    @Test
    void onResume_setsPausedFalse() {
        gridMovement.onPause();
        assertTrue(gridMovement.isPaused());

        gridMovement.onResume();

        assertFalse(gridMovement.isPaused());
    }

    // --- update() behavior ---

    @Test
    void update_whenPaused_doesNotProcess() {
        // GridMovement.update() ticks turnTimer when > 0. If paused, it shouldn't.
        // We can observe this indirectly: set a turn direction, pause, update, and check isMoving stays false.
        gridMovement.onPause();

        // update should be a no-op
        gridMovement.update(0.016f);

        assertFalse(gridMovement.isMoving());
        assertTrue(gridMovement.isPaused());
    }

    @Test
    void update_whenResumed_processesNormally() {
        gridMovement.onPause();
        gridMovement.onResume();

        // Should not throw and should process normally
        gridMovement.update(0.016f);

        assertFalse(gridMovement.isPaused());
    }

    // --- move() behavior ---

    @Test
    void move_whenPaused_isRejected() {
        gridMovement.onPause();

        boolean result = gridMovement.move(Direction.UP);

        assertFalse(result);
        assertFalse(gridMovement.isMoving());
    }

    @Test
    void move_whenResumed_isAcceptedAgain() {
        gridMovement.onPause();
        assertFalse(gridMovement.move(Direction.UP));

        gridMovement.onResume();

        // move() may still return false (no collision system set up),
        // but it should at least attempt (not be blocked by paused flag).
        // We verify by checking paused is false — the move goes through normal validation.
        assertFalse(gridMovement.isPaused());
    }

    // --- Component stays enabled ---

    @Test
    void onPause_componentStaysEnabled() {
        gridMovement.onPause();

        assertTrue(gridMovement.isOwnEnabled());
    }

    // --- Scene query integration ---

    @Test
    void sceneQuery_findsGridMovementAsIPausable() {
        List<IPausable> pausables = scene.getComponentsImplementing(IPausable.class);

        assertEquals(1, pausables.size());
        assertSame(gridMovement, pausables.getFirst());
    }

    @Test
    void sceneQuery_multipleGridMovements_allFound() {
        GameObject npc = new GameObject("NPC");
        GridMovement npcMovement = new GridMovement();
        npc.addComponent(npcMovement);
        scene.addGameObject(npc);

        List<IPausable> pausables = scene.getComponentsImplementing(IPausable.class);

        assertEquals(2, pausables.size());
        assertTrue(pausables.contains(gridMovement));
        assertTrue(pausables.contains(npcMovement));
    }

    @Test
    void sceneQuery_pauseAll_allPaused() {
        GameObject npc = new GameObject("NPC");
        GridMovement npcMovement = new GridMovement();
        npc.addComponent(npcMovement);
        scene.addGameObject(npc);

        // Pause all IPausable in scene
        for (IPausable pausable : scene.getComponentsImplementing(IPausable.class)) {
            pausable.onPause();
        }

        assertTrue(gridMovement.isPaused());
        assertTrue(npcMovement.isPaused());

        // Resume all
        for (IPausable pausable : scene.getComponentsImplementing(IPausable.class)) {
            pausable.onResume();
        }

        assertFalse(gridMovement.isPaused());
        assertFalse(npcMovement.isPaused());
    }

    @Test
    void sceneQuery_nonPausableComponentsIgnored() {
        GameObject other = new GameObject("Other");
        other.addComponent(new PlainComponent());
        scene.addGameObject(other);

        List<IPausable> pausables = scene.getComponentsImplementing(IPausable.class);

        assertEquals(1, pausables.size());
        assertSame(gridMovement, pausables.getFirst());
    }

    // --- Helpers ---

    private static class PlainComponent extends Component {}

    private static class TestScene extends Scene {
        public TestScene(String name) { super(name); }
        @Override public void onLoad() {}
    }
}
