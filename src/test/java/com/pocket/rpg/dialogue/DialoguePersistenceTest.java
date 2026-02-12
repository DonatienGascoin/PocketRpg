package com.pocket.rpg.dialogue;

import com.pocket.rpg.components.dialogue.DialogueEventListener;
import com.pocket.rpg.components.dialogue.DialogueInteractable;
import com.pocket.rpg.components.dialogue.DialogueReaction;
import com.pocket.rpg.components.dialogue.PlayerDialogueManager;
import com.pocket.rpg.components.interaction.TriggerZone;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.testing.MockInputTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests cross-scene event persistence:
 * markFired persists across components, and conditional dialogue
 * selection changes after events are fired.
 */
class DialoguePersistenceTest {

    @TempDir
    Path tempDir;

    private MockInputTesting mockInput;

    @BeforeEach
    void setUp() throws Exception {
        resetSaveManager();
        initSaveManager();
        mockInput = new MockInputTesting();
        Input.setContext(mockInput);
    }

    @AfterEach
    void tearDown() throws Exception {
        Input.setContext(null);
        resetSaveManager();
    }

    @Test
    void markFiredVisibleAcrossComponents() {
        // Fire event in one context
        DialogueEventStore.markFired("GOT_BADGE_1");

        // Create a new listener (simulating a different scene) — should see the event
        TestScene scene = new TestScene("SceneB");
        GameObject door = new GameObject("door");
        DialogueEventListener listener = new DialogueEventListener();
        listener.setEventName("GOT_BADGE_1");
        listener.setReaction(DialogueReaction.DISABLE_GAME_OBJECT);
        door.addComponent(listener);
        scene.addGameObject(door);

        assertTrue(door.isEnabled());

        // onStart checks hasFired → reacts
        door.start();

        assertFalse(door.isEnabled());
    }

    @Test
    void conditionalDialogueChangesAfterEventFired() {
        Dialogue defaultDialogue = new Dialogue("default", List.of(new DialogueLine("Hello")));
        Dialogue badgeDialogue = new Dialogue("badge", List.of(new DialogueLine("You got the badge!")));

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), badgeDialogue);

        // Scene with NPC + player
        TestScene scene = new TestScene("TestScene");

        GameObject playerGo = new GameObject("Player");
        playerGo.addComponent(new PlayerInput());
        PlayerDialogueManager manager = new PlayerDialogueManager();
        playerGo.addComponent(manager);
        scene.addGameObject(playerGo);

        GameObject npc = new GameObject("NPC");
        npc.addComponent(new TriggerZone());
        DialogueInteractable interactable = new DialogueInteractable();
        interactable.setDialogue(defaultDialogue);
        interactable.setConditionalDialogues(List.of(cd));
        npc.addComponent(interactable);
        scene.addGameObject(npc);

        playerGo.start();
        npc.start();

        // Before event → default
        interactable.interact(playerGo);
        assertTrue(manager.isActive());
        assertEquals("Hello", manager.getFullText());
        manager.endDialogue();

        // Fire event
        DialogueEventStore.markFired("GOT_BADGE_1");

        // After event → conditional
        interactable.interact(playerGo);
        assertTrue(manager.isActive());
        assertEquals("You got the badge!", manager.getFullText());
    }

    @Test
    void unfiredEventDoesNotAffectSelection() {
        Dialogue defaultDialogue = new Dialogue("default", List.of(new DialogueLine("Default")));
        Dialogue conditionalDialogue = new Dialogue("cond", List.of(new DialogueLine("Conditional")));

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("NEVER_FIRED", DialogueCondition.ExpectedState.FIRED)
        ), conditionalDialogue);

        TestScene scene = new TestScene("TestScene");

        GameObject playerGo = new GameObject("Player");
        playerGo.addComponent(new PlayerInput());
        PlayerDialogueManager manager = new PlayerDialogueManager();
        playerGo.addComponent(manager);
        scene.addGameObject(playerGo);

        GameObject npc = new GameObject("NPC");
        npc.addComponent(new TriggerZone());
        DialogueInteractable interactable = new DialogueInteractable();
        interactable.setDialogue(defaultDialogue);
        interactable.setConditionalDialogues(List.of(cd));
        npc.addComponent(interactable);
        scene.addGameObject(npc);

        playerGo.start();
        npc.start();

        interactable.interact(playerGo);
        assertEquals("Default", manager.getFullText());
    }

    // ========================================================================
    // TEST INFRASTRUCTURE
    // ========================================================================

    private static class TestScene extends Scene {
        public TestScene(String name) { super(name); }
        @Override public void onLoad() {}
    }

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
