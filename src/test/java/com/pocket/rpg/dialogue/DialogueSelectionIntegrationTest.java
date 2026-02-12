package com.pocket.rpg.dialogue;

import com.pocket.rpg.components.dialogue.DialogueInteractable;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DialogueInteractable's conditional dialogue selection
 * through the full interact() → PlayerDialogueManager.startDialogue() flow.
 */
class DialogueSelectionIntegrationTest {

    @TempDir
    Path tempDir;

    private MockInputTesting mockInput;
    private PlayerDialogueManager manager;
    private DialogueInteractable dialogueComponent;
    private GameObject playerGo;
    private TestScene scene;

    @BeforeEach
    void setUp() throws Exception {
        resetSaveManager();
        initSaveManager();

        mockInput = new MockInputTesting();
        Input.setContext(mockInput);

        scene = new TestScene("TestScene");

        // Player with PlayerInput + PlayerDialogueManager
        playerGo = new GameObject("Player");
        PlayerInput playerInput = new PlayerInput();
        manager = new PlayerDialogueManager();
        playerGo.addComponent(playerInput);
        playerGo.addComponent(manager);
        scene.addGameObject(playerGo);

        // NPC with TriggerZone + DialogueInteractable
        GameObject npc = new GameObject("NPC");
        npc.addComponent(new TriggerZone());
        dialogueComponent = new DialogueInteractable();
        npc.addComponent(dialogueComponent);
        scene.addGameObject(npc);

        playerGo.start();
        npc.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        Input.setContext(null);
        resetSaveManager();
    }

    @Test
    void noConditionsUsesDefaultDialogue() {
        Dialogue defaultDialogue = dialogueWithLines("Default greeting");
        dialogueComponent.setDialogue(defaultDialogue);

        dialogueComponent.interact(playerGo);

        assertTrue(manager.isActive());
        assertEquals("Default greeting", manager.getFullText());
    }

    @Test
    void firstConditionMatchesUsesConditionalDialogue() {
        Dialogue defaultDialogue = dialogueWithLines("Default");
        Dialogue badgeDialogue = dialogueWithLines("Got the badge!");

        dialogueComponent.setDialogue(defaultDialogue);

        // Condition: GOT_BADGE_1 must be FIRED
        DialogueEventStore.markFired("GOT_BADGE_1");
        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), badgeDialogue);

        dialogueComponent.setConditionalDialogues(List.of(cd));
        dialogueComponent.interact(playerGo);

        assertTrue(manager.isActive());
        assertEquals("Got the badge!", manager.getFullText());
    }

    @Test
    void multipleConditionsFirstMatchWins() {
        Dialogue defaultDialogue = dialogueWithLines("Default");
        Dialogue specificDialogue = dialogueWithLines("Very specific");
        Dialogue lessSpecificDialogue = dialogueWithLines("Less specific");

        dialogueComponent.setDialogue(defaultDialogue);

        DialogueEventStore.markFired("GOT_BADGE_1");
        DialogueEventStore.markFired("TALKED_TO_RIVAL");

        // Entry 0: requires both events → matches first
        ConditionalDialogue cd0 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED),
                new DialogueCondition("TALKED_TO_RIVAL", DialogueCondition.ExpectedState.FIRED)
        ), specificDialogue);

        // Entry 1: requires one event → also matches, but second
        ConditionalDialogue cd1 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), lessSpecificDialogue);

        dialogueComponent.setConditionalDialogues(List.of(cd0, cd1));
        dialogueComponent.interact(playerGo);

        assertTrue(manager.isActive());
        assertEquals("Very specific", manager.getFullText());
    }

    @Test
    void eventsFiredChangesSelection() {
        Dialogue defaultDialogue = dialogueWithLines("Default greeting");
        Dialogue postBadgeDialogue = dialogueWithLines("Congrats on the badge!");

        dialogueComponent.setDialogue(defaultDialogue);

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), postBadgeDialogue);

        dialogueComponent.setConditionalDialogues(List.of(cd));

        // Before event → default
        dialogueComponent.interact(playerGo);
        assertTrue(manager.isActive());
        assertEquals("Default greeting", manager.getFullText());

        // End current dialogue
        manager.endDialogue();
        assertFalse(manager.isActive());

        // Fire event → conditional now matches
        DialogueEventStore.markFired("GOT_BADGE_1");

        dialogueComponent.interact(playerGo);
        assertTrue(manager.isActive());
        assertEquals("Congrats on the badge!", manager.getFullText());
    }

    @Test
    void interactWhileActiveIsNoOp() {
        Dialogue dialogue = dialogueWithLines("Hello");
        dialogueComponent.setDialogue(dialogue);

        dialogueComponent.interact(playerGo);
        assertTrue(manager.isActive());
        assertEquals("Hello", manager.getFullText());

        // Set a different dialogue and try to interact again
        dialogueComponent.setDialogue(dialogueWithLines("Different"));
        dialogueComponent.interact(playerGo);

        // Should still show the original dialogue
        assertEquals("Hello", manager.getFullText());
    }

    @Test
    void variablesPassedToManager() {
        Dialogue dialogue = dialogueWithLines("Hello [NPC_NAME]!");
        dialogueComponent.setDialogue(dialogue);
        dialogueComponent.setVariables(Map.of("NPC_NAME", "Professor Oak"));

        dialogueComponent.interact(playerGo);

        assertTrue(manager.isActive());
        assertEquals("Hello Professor Oak!", manager.getFullText());
    }

    @Test
    void setsSourceComponentOnManager() {
        Dialogue dialogue = dialogueWithLines("Hello");
        dialogueComponent.setDialogue(dialogue);

        dialogueComponent.interact(playerGo);

        assertSame(dialogueComponent, manager.getSourceComponent());
    }

    @Test
    void onConversationEndDispatchedWhenDialogueEnds() {
        List<String> firedEvents = new ArrayList<>();
        manager.addEventListenerCallback("TRAINER_DONE", () -> firedEvents.add("TRAINER_DONE"));

        Dialogue dialogue = dialogueWithLines("Ready to battle?");
        dialogueComponent.setDialogue(dialogue);
        dialogueComponent.setOnConversationEnd(DialogueEventRef.custom("TRAINER_DONE"));

        dialogueComponent.interact(playerGo);
        assertTrue(firedEvents.isEmpty());

        manager.endDialogue();
        assertTrue(firedEvents.contains("TRAINER_DONE"));
    }

    @Test
    void sourceComponentClearedAfterEndDialogue() {
        Dialogue dialogue = dialogueWithLines("Hello");
        dialogueComponent.setDialogue(dialogue);

        dialogueComponent.interact(playerGo);
        assertNotNull(manager.getSourceComponent());

        manager.endDialogue();
        assertNull(manager.getSourceComponent());
    }

    @Test
    void nullDialogueDoesNotStartDialogue() {
        // No default dialogue set
        dialogueComponent.setDialogue(null);

        dialogueComponent.interact(playerGo);

        assertFalse(manager.isActive());
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Dialogue dialogueWithLines(String... lines) {
        List<DialogueEntry> entries = new ArrayList<>();
        for (String text : lines) {
            entries.add(new DialogueLine(text));
        }
        return new Dialogue("test_dialogue", entries);
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
