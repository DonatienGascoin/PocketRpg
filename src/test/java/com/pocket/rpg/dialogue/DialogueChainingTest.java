package com.pocket.rpg.dialogue;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.IPausable;
import com.pocket.rpg.components.dialogue.PlayerDialogueManager;
import com.pocket.rpg.components.player.InputMode;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.testing.MockInputTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests dialogue chaining via DIALOGUE choice actions.
 * Validates:
 * - Internal chain: no endDialogue between chained dialogues
 * - IPausable stays paused throughout chain
 * - Input mode stays DIALOGUE throughout chain
 * - Line-level onCompleteEvent fires in both original and chained dialogue
 */
class DialogueChainingTest {

    private MockInputTesting mockInput;
    private PlayerInput playerInput;
    private PlayerDialogueManager manager;
    private TestPausable pausable;
    private TestScene scene;

    @BeforeEach
    void setUp() {
        mockInput = new MockInputTesting();
        Input.setContext(mockInput);

        scene = new TestScene("TestScene");

        GameObject playerGo = new GameObject("Player");
        playerInput = new PlayerInput();
        manager = new PlayerDialogueManager();
        playerGo.addComponent(playerInput);
        playerGo.addComponent(manager);
        scene.addGameObject(playerGo);

        // NPC with pausable component
        GameObject npc = new GameObject("NPC");
        pausable = new TestPausable();
        npc.addComponent(pausable);
        scene.addGameObject(npc);

        playerGo.start();
    }

    @AfterEach
    void tearDown() {
        Input.setContext(null);
    }

    @Test
    void chainDoesNotCallEndDialogue() {
        // Dialogue A → choice chains to Dialogue B
        Dialogue dialogueB = new Dialogue("B", List.of(new DialogueLine("B line")));

        // We can't use Assets.load in tests, so we chain by calling startDialogue directly
        // Simulating what the choice action handler does for DIALOGUE type
        Dialogue dialogueA = new Dialogue("A", List.of(new DialogueLine("A line")));

        manager.startDialogue(dialogueA, Map.of());
        assertTrue(manager.isActive());

        // Advance past A's line
        pressInteract(); pressInteract();

        // At this point A ends (single line, no choices). Let's test explicit chaining:
        // Start fresh with a proper chain test
        mockInput.endFrame();

        // Start dialogue A
        Dialogue chainSource = new Dialogue("A", List.of(new DialogueLine("Hello from A")));
        manager.startDialogue(chainSource, Map.of());
        assertTrue(manager.isActive());
        assertTrue(pausable.paused);

        // Internal chain: call startDialogue again while active
        manager.startDialogue(dialogueB, Map.of());

        // Should still be active, still paused, still DIALOGUE mode
        assertTrue(manager.isActive());
        assertTrue(pausable.paused);
        assertEquals(InputMode.DIALOGUE, playerInput.getMode());
        assertEquals("B line", manager.getFullText());
    }

    @Test
    void chainKeepsInputModeDialogue() {
        Dialogue dialogueA = new Dialogue("A", List.of(new DialogueLine("A")));
        Dialogue dialogueB = new Dialogue("B", List.of(new DialogueLine("B")));

        manager.startDialogue(dialogueA, Map.of());
        assertEquals(InputMode.DIALOGUE, playerInput.getMode());

        // Chain to B
        manager.startDialogue(dialogueB, Map.of());
        assertEquals(InputMode.DIALOGUE, playerInput.getMode());

        // End B → restores overworld
        manager.endDialogue();
        assertEquals(InputMode.OVERWORLD, playerInput.getMode());
    }

    @Test
    void chainKeepsPausablesePaused() {
        Dialogue dialogueA = new Dialogue("A", List.of(new DialogueLine("A")));
        Dialogue dialogueB = new Dialogue("B", List.of(new DialogueLine("B")));

        manager.startDialogue(dialogueA, Map.of());
        assertTrue(pausable.paused);

        // Chain to B — still paused
        manager.startDialogue(dialogueB, Map.of());
        assertTrue(pausable.paused);

        // End B → resumes
        manager.endDialogue();
        assertFalse(pausable.paused);
    }

    @Test
    void chainDoesNotDoublePause() {
        // Track pause count to ensure onPause is not called twice
        CountingPausable counting = new CountingPausable();
        GameObject obj = new GameObject("Counter");
        obj.addComponent(counting);
        scene.addGameObject(obj);

        Dialogue dialogueA = new Dialogue("A", List.of(new DialogueLine("A")));
        Dialogue dialogueB = new Dialogue("B", List.of(new DialogueLine("B")));

        manager.startDialogue(dialogueA, Map.of());
        assertEquals(1, counting.pauseCount);

        // Chain — should not pause again
        manager.startDialogue(dialogueB, Map.of());
        assertEquals(1, counting.pauseCount);

        manager.endDialogue();
        assertEquals(1, counting.resumeCount);
    }

    @Test
    void lineEventFiresInBothDialogues() {
        List<String> firedEvents = new ArrayList<>();
        manager.addEventListenerCallback("EVENT_A", () -> firedEvents.add("EVENT_A"));
        manager.addEventListenerCallback("EVENT_B", () -> firedEvents.add("EVENT_B"));

        DialogueLine lineA = new DialogueLine("Line A", DialogueEventRef.custom("EVENT_A"));
        Dialogue dialogueA = new Dialogue("A", List.of(lineA));

        DialogueLine lineB = new DialogueLine("Line B", DialogueEventRef.custom("EVENT_B"));
        Dialogue dialogueB = new Dialogue("B", List.of(lineB));

        manager.startDialogue(dialogueA, Map.of());

        // Advance past line A → fires EVENT_A
        pressInteract(); pressInteract();

        // Dialogue A ends, but let's test with explicit chain
        // Reset and test with chain
        firedEvents.clear();
        manager.endDialogue();

        manager.startDialogue(dialogueA, Map.of());

        // Advance past line A's typewriter
        pressInteract();
        assertTrue(firedEvents.isEmpty());

        // Advance past line A → fires EVENT_A, dialogue ends
        pressInteract();
        assertTrue(firedEvents.contains("EVENT_A"));

        // Now test with explicit chain: start A, then chain to B while active
        firedEvents.clear();
        manager.startDialogue(dialogueA, Map.of());

        // Chain to B before advancing A (simulating what choice action does)
        manager.startDialogue(dialogueB, Map.of());
        assertTrue(firedEvents.isEmpty()); // No events fired yet (A wasn't advanced past)

        // Advance past line B → fires EVENT_B
        pressInteract(); pressInteract();
        assertTrue(firedEvents.contains("EVENT_B"));
    }

    @Test
    void chainWithNullDialogueEndsConversation() {
        Dialogue dialogueA = new Dialogue("A", List.of(new DialogueLine("A")));
        manager.startDialogue(dialogueA, Map.of());
        assertTrue(manager.isActive());

        // Chain with null → should end
        manager.startDialogue(null, Map.of());
        assertFalse(manager.isActive());
        assertFalse(pausable.paused);
    }

    @Test
    void chainWithEmptyDialogueEndsConversation() {
        Dialogue dialogueA = new Dialogue("A", List.of(new DialogueLine("A")));
        manager.startDialogue(dialogueA, Map.of());
        assertTrue(manager.isActive());

        // Chain with empty → should end
        manager.startDialogue(new Dialogue("empty"), Map.of());
        assertFalse(manager.isActive());
        assertFalse(pausable.paused);
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

    // ========================================================================
    // TEST DOUBLES
    // ========================================================================

    private static class TestScene extends Scene {
        public TestScene(String name) { super(name); }
        @Override public void onLoad() {}
    }

    private static class TestPausable extends Component implements IPausable {
        boolean paused = false;
        @Override public void onPause() { paused = true; }
        @Override public void onResume() { paused = false; }
    }

    private static class CountingPausable extends Component implements IPausable {
        int pauseCount = 0;
        int resumeCount = 0;
        @Override public void onPause() { pauseCount++; }
        @Override public void onResume() { resumeCount++; }
    }
}
