package com.pocket.rpg.dialogue;

import com.pocket.rpg.components.animations.AnimationComponent;
import com.pocket.rpg.components.dialogue.DialogueEventListener;
import com.pocket.rpg.components.dialogue.DialogueReaction;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests DialogueEventListener reactions and lifecycle behavior.
 */
class DialogueEventListenerTest {

    @TempDir
    Path tempDir;

    private TestScene scene;

    @BeforeEach
    void setUp() throws Exception {
        resetSaveManager();
        initSaveManager();
        scene = new TestScene("TestScene");
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSaveManager();
    }

    // ========================================================================
    // REACTION TYPES
    // ========================================================================

    @Test
    void disableGameObjectReaction() {
        GameObject door = createListenerObject("door", "OPEN_DOOR", DialogueReaction.DISABLE_GAME_OBJECT);
        assertTrue(door.isEnabled());

        door.getComponent(DialogueEventListener.class).onDialogueEvent();

        assertFalse(door.isEnabled());
    }

    @Test
    void enableGameObjectReaction() {
        GameObject npc = createListenerObject("npc", "RESCUE_NPC", DialogueReaction.ENABLE_GAME_OBJECT);
        npc.setEnabled(false);
        assertFalse(npc.isEnabled());

        npc.getComponent(DialogueEventListener.class).onDialogueEvent();

        assertTrue(npc.isEnabled());
    }

    @Test
    void destroyGameObjectReaction() {
        GameObject boulder = createListenerObject("boulder", "CLEAR_PATH", DialogueReaction.DESTROY_GAME_OBJECT);
        assertTrue(scene.getGameObjects().contains(boulder));

        boulder.getComponent(DialogueEventListener.class).onDialogueEvent();

        assertFalse(scene.getGameObjects().contains(boulder));
    }

    @Test
    void runAnimationWithAnimationComponent() {
        GameObject obj = createListenerObject("chest", "OPEN_CHEST", DialogueReaction.RUN_ANIMATION);
        TrackingAnimationComponent anim = new TrackingAnimationComponent();
        obj.addComponent(anim);

        obj.getComponent(DialogueEventListener.class).onDialogueEvent();

        assertTrue(anim.played);
    }

    @Test
    void runAnimationWithoutAnimationComponentDoesNotCrash() {
        GameObject obj = createListenerObject("noAnim", "SOME_EVENT", DialogueReaction.RUN_ANIMATION);
        // No AnimationComponent added — should warn but not crash

        assertDoesNotThrow(() ->
                obj.getComponent(DialogueEventListener.class).onDialogueEvent());
    }

    // ========================================================================
    // LIFECYCLE — onStart
    // ========================================================================

    @Test
    void alreadyFiredEventReactsOnStart() {
        DialogueEventStore.markFired("OPEN_DOOR");

        GameObject door = createListenerObject("door", "OPEN_DOOR", DialogueReaction.DISABLE_GAME_OBJECT);
        assertTrue(door.isEnabled());

        // start() checks hasFired → reacts immediately
        door.start();

        assertFalse(door.isEnabled());
    }

    @Test
    void unfiredEventDoesNotReactOnStart() {
        // OPEN_DOOR not fired
        GameObject door = createListenerObject("door", "OPEN_DOOR", DialogueReaction.DISABLE_GAME_OBJECT);
        door.start();

        assertTrue(door.isEnabled());
    }

    @Test
    void nullEventNameSkipsOnStart() {
        GameObject obj = createListenerObject("obj", null, DialogueReaction.DISABLE_GAME_OBJECT);
        obj.start();

        // Should not crash, object stays enabled
        assertTrue(obj.isEnabled());
    }

    @Test
    void blankEventNameSkipsOnStart() {
        GameObject obj = createListenerObject("obj", "  ", DialogueReaction.DISABLE_GAME_OBJECT);
        obj.start();

        assertTrue(obj.isEnabled());
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private GameObject createListenerObject(String name, String eventName, DialogueReaction reaction) {
        GameObject go = new GameObject(name);
        DialogueEventListener listener = new DialogueEventListener();
        listener.setEventName(eventName);
        listener.setReaction(reaction);
        go.addComponent(listener);
        scene.addGameObject(go);
        return go;
    }

    // ========================================================================
    // TEST DOUBLES
    // ========================================================================

    private static class TestScene extends Scene {
        public TestScene(String name) { super(name); }
        @Override public void onLoad() {}
    }

    /**
     * Minimal AnimationComponent subclass that tracks play() calls.
     * We extend the real class so getComponent(AnimationComponent.class) finds it.
     */
    private static class TrackingAnimationComponent extends AnimationComponent {
        boolean played = false;

        @Override
        public void play() {
            played = true;
        }
    }

    // ========================================================================
    // SAVE MANAGER HELPERS
    // ========================================================================

    private void initSaveManager() throws Exception {
        Constructor<SaveManager> ctor = SaveManager.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        SaveManager instance = ctor.newInstance(tempDir);

        Field instanceField = SaveManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, instance);
    }

    private static void resetSaveManager() throws Exception {
        Field instanceField = SaveManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
