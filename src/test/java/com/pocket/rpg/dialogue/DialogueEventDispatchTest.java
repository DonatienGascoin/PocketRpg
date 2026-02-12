package com.pocket.rpg.dialogue;

import com.pocket.rpg.IPausable;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.dialogue.DialogueEventListener;
import com.pocket.rpg.components.dialogue.DialogueInteractable;
import com.pocket.rpg.components.dialogue.DialogueReaction;
import com.pocket.rpg.components.dialogue.PlayerDialogueManager;
import com.pocket.rpg.components.interaction.TriggerZone;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for dialogue event dispatch:
 * dialogue → choice action / line event → listener reacts.
 */
class DialogueEventDispatchTest {

    @TempDir
    Path tempDir;

    private MockInputTesting mockInput;
    private PlayerInput playerInput;
    private PlayerDialogueManager manager;
    private TestScene scene;
    private GameObject playerGo;

    @BeforeEach
    void setUp() throws Exception {
        resetSaveManager();
        initSaveManager();

        mockInput = new MockInputTesting();
        Input.setContext(mockInput);

        scene = new TestScene("TestScene");

        playerGo = new GameObject("Player");
        playerInput = new PlayerInput();
        manager = new PlayerDialogueManager();
        playerGo.addComponent(playerInput);
        playerGo.addComponent(manager);
        scene.addGameObject(playerGo);
        playerGo.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        Input.setContext(null);
        resetSaveManager();
    }

    @Test
    void customEventChoiceActionDispatchesToSceneListener() {
        // Door listener in scene
        GameObject door = createListenerObject("door", "OPEN_DOOR", DialogueReaction.DISABLE_GAME_OBJECT);
        door.start();
        assertTrue(door.isEnabled());

        // Dialogue with choice that fires OPEN_DOOR
        Dialogue dialogue = dialogueWithChoices("Open the door?",
                List.of(new Choice("Yes", ChoiceAction.customEvent("OPEN_DOOR"))));

        manager.startDialogue(dialogue, Map.of());
        pressInteract(); pressInteract(); // advance to choices
        pressInteract(); // select choice → fires event → endDialogue

        assertFalse(door.isEnabled());
        assertFalse(manager.isActive());
    }

    @Test
    void lineOnCompleteEventDispatchesToSceneListener() {
        GameObject obj = createListenerObject("obj", "STEP_DONE", DialogueReaction.DISABLE_GAME_OBJECT);
        obj.start();
        assertTrue(obj.isEnabled());

        DialogueLine line = new DialogueLine("Doing the thing...", DialogueEventRef.custom("STEP_DONE"));
        Dialogue dialogue = new Dialogue("test", List.of(line));

        manager.startDialogue(dialogue, Map.of());

        // Advance past the line → fires onCompleteEvent
        pressInteract(); pressInteract();

        assertFalse(obj.isEnabled());
    }

    @Test
    void lineEventFiresInChainedDialogues() {
        GameObject obj1 = createListenerObject("obj1", "EVENT_A", DialogueReaction.DISABLE_GAME_OBJECT);
        GameObject obj2 = createListenerObject("obj2", "EVENT_B", DialogueReaction.DISABLE_GAME_OBJECT);
        obj1.start();
        obj2.start();

        DialogueLine lineA = new DialogueLine("Line A", DialogueEventRef.custom("EVENT_A"));
        Dialogue dialogueA = new Dialogue("A", List.of(lineA));

        DialogueLine lineB = new DialogueLine("Line B", DialogueEventRef.custom("EVENT_B"));
        Dialogue dialogueB = new Dialogue("B", List.of(lineB));

        // Start A, then chain to B
        manager.startDialogue(dialogueA, Map.of());
        manager.startDialogue(dialogueB, Map.of()); // chain — A's line wasn't advanced past

        assertTrue(obj1.isEnabled()); // EVENT_A not fired (line A wasn't advanced)

        // Advance past line B → fires EVENT_B
        pressInteract(); pressInteract();

        assertFalse(obj2.isEnabled());
    }

    @Test
    void builtInEventEndConversationEndsDialogue() {
        Dialogue dialogue = dialogueWithChoices("Done?",
                List.of(new Choice("End", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))));

        manager.startDialogue(dialogue, Map.of());
        pressInteract(); pressInteract(); // advance to choices
        pressInteract(); // select END_CONVERSATION

        assertFalse(manager.isActive());
    }

    @Test
    void customEventPersistedViaDialogueEventStore() {
        Dialogue dialogue = dialogueWithChoices("Fire it?",
                List.of(new Choice("Fire", ChoiceAction.customEvent("PERSISTED_EVENT"))));

        assertFalse(DialogueEventStore.hasFired("PERSISTED_EVENT"));

        manager.startDialogue(dialogue, Map.of());
        pressInteract(); pressInteract();
        pressInteract(); // select choice → fires + persists

        assertTrue(DialogueEventStore.hasFired("PERSISTED_EVENT"));
    }

    @Test
    void onConversationEndFiresOnceAfterChaining() {
        List<String> firedEvents = new ArrayList<>();
        manager.addEventListenerCallback("NPC_DONE", () -> firedEvents.add("NPC_DONE"));

        // Set up DialogueInteractable with onConversationEnd
        GameObject npc = new GameObject("NPC");
        npc.addComponent(new TriggerZone());
        DialogueInteractable interactable = new DialogueInteractable();
        interactable.setDialogue(new Dialogue("A", List.of(new DialogueLine("A line"))));
        interactable.setOnConversationEnd(DialogueEventRef.custom("NPC_DONE"));
        npc.addComponent(interactable);
        scene.addGameObject(npc);
        npc.start();

        // Interact → starts dialogue
        interactable.interact(playerGo);
        assertTrue(manager.isActive());
        assertTrue(firedEvents.isEmpty());

        // Chain to dialogue B
        Dialogue dialogueB = new Dialogue("B", List.of(new DialogueLine("B line")));
        manager.startDialogue(dialogueB, Map.of());

        // Still no onConversationEnd (chaining, not ending)
        assertTrue(firedEvents.isEmpty());

        // Advance past B's line → dialogue ends → onConversationEnd fires
        pressInteract(); pressInteract();

        assertFalse(manager.isActive());
        assertEquals(1, firedEvents.size());
        assertEquals("NPC_DONE", firedEvents.get(0));
    }

    @Test
    void onConversationEndDoesNotFireDuringChaining() {
        List<String> firedEvents = new ArrayList<>();
        manager.addEventListenerCallback("NPC_DONE", () -> firedEvents.add("NPC_DONE"));

        GameObject npc = new GameObject("NPC");
        npc.addComponent(new TriggerZone());
        DialogueInteractable interactable = new DialogueInteractable();
        interactable.setDialogue(new Dialogue("A", List.of(new DialogueLine("A"))));
        interactable.setOnConversationEnd(DialogueEventRef.custom("NPC_DONE"));
        npc.addComponent(interactable);
        scene.addGameObject(npc);
        npc.start();

        interactable.interact(playerGo);

        // Chain multiple times
        manager.startDialogue(new Dialogue("B", List.of(new DialogueLine("B"))), Map.of());
        manager.startDialogue(new Dialogue("C", List.of(new DialogueLine("C"))), Map.of());

        // No event during chaining
        assertTrue(firedEvents.isEmpty());
        assertTrue(manager.isActive());
    }

    @Test
    void enableEventReachesDisabledGameObject() {
        // NPC starts disabled — ENABLE_GAME_OBJECT should find it via scene query
        GameObject hiddenNpc = createListenerObject("hidden", "SHOW_NPC", DialogueReaction.ENABLE_GAME_OBJECT);
        hiddenNpc.start();
        hiddenNpc.setEnabled(false);
        assertFalse(hiddenNpc.isEnabled());

        Dialogue dialogue = dialogueWithChoices("Reveal?",
                List.of(new Choice("Yes", ChoiceAction.customEvent("SHOW_NPC"))));

        manager.startDialogue(dialogue, Map.of());
        pressInteract(); pressInteract();
        pressInteract();

        assertTrue(hiddenNpc.isEnabled());
    }

    @Test
    void multipleListenersReactToSameEvent() {
        GameObject obj1 = createListenerObject("obj1", "SHARED_EVENT", DialogueReaction.DISABLE_GAME_OBJECT);
        GameObject obj2 = createListenerObject("obj2", "SHARED_EVENT", DialogueReaction.DISABLE_GAME_OBJECT);
        obj1.start();
        obj2.start();

        Dialogue dialogue = dialogueWithChoices("Fire?",
                List.of(new Choice("Go", ChoiceAction.customEvent("SHARED_EVENT"))));

        manager.startDialogue(dialogue, Map.of());
        pressInteract(); pressInteract();
        pressInteract();

        assertFalse(obj1.isEnabled());
        assertFalse(obj2.isEnabled());
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void pressInteract() {
        mockInput.pressKey(KeyCode.E);
        playerInput.update(0.016f);
        manager.update(0.016f);
        mockInput.endFrame();
    }

    private GameObject createListenerObject(String name, String eventName, DialogueReaction reaction) {
        GameObject go = new GameObject(name);
        DialogueEventListener listener = new DialogueEventListener();
        listener.setEventName(eventName);
        listener.setReaction(reaction);
        go.addComponent(listener);
        scene.addGameObject(go);
        return go;
    }

    private Dialogue dialogueWithChoices(String lineText, List<Choice> choices) {
        List<DialogueEntry> entries = new ArrayList<>();
        entries.add(new DialogueLine(lineText));
        entries.add(new DialogueChoiceGroup(true, choices));
        return new Dialogue("test", entries);
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
