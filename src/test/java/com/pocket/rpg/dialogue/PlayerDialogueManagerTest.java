package com.pocket.rpg.dialogue;

import com.pocket.rpg.components.dialogue.PlayerDialogueManager;
import com.pocket.rpg.components.player.InputMode;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.testing.MockInputTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDialogueManagerTest {

    @TempDir
    Path tempDir;

    private MockInputTesting mockInput;
    private PlayerInput playerInput;
    private PlayerDialogueManager manager;
    private GameObject playerGo;
    private TestScene scene;

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

    // ========================================================================
    // BASIC STATE MACHINE
    // ========================================================================

    @Nested
    class StateMachine {

        @Test
        void initiallyNotActive() {
            assertFalse(manager.isActive());
        }

        @Test
        void startDialogueSetsActive() {
            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());
            assertTrue(manager.isActive());
        }

        @Test
        void startDialogueSwitchesToDialogueMode() {
            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());
            assertEquals(InputMode.DIALOGUE, playerInput.getMode());
        }

        @Test
        void endDialogueRestoresOverworldMode() {
            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());
            manager.endDialogue();
            assertEquals(InputMode.OVERWORLD, playerInput.getMode());
        }

        @Test
        void endDialogueSetsInactive() {
            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());
            manager.endDialogue();
            assertFalse(manager.isActive());
        }

        @Test
        void advanceThroughMultipleLines() {
            Dialogue dialogue = dialogueWithLines("Line 1", "Line 2", "Line 3");
            manager.startDialogue(dialogue, Map.of());

            assertEquals("Line 1", manager.getFullText());
            assertEquals(0, manager.getCurrentEntryIndex());

            // Skip typewriter + advance to line 2
            pressInteract();
            pressInteract();

            assertEquals("Line 2", manager.getFullText());
            assertEquals(1, manager.getCurrentEntryIndex());

            // Advance to line 3
            pressInteract();
            pressInteract();

            assertEquals("Line 3", manager.getFullText());
            assertEquals(2, manager.getCurrentEntryIndex());

            // Advance past last line → dialogue ends
            pressInteract();
            pressInteract();

            assertFalse(manager.isActive());
        }

        @Test
        void singleLineDialogueEndsAfterAdvance() {
            Dialogue dialogue = dialogueWithLines("Only line");
            manager.startDialogue(dialogue, Map.of());

            pressInteract(); // skip typewriter
            pressInteract(); // advance past → end

            assertFalse(manager.isActive());
        }
    }

    // ========================================================================
    // TYPEWRITER EFFECT
    // ========================================================================

    @Nested
    class Typewriter {

        @Test
        void initiallyNotFullyRevealed() {
            Dialogue dialogue = dialogueWithLines("Hello world");
            manager.startDialogue(dialogue, Map.of());
            assertFalse(manager.isTextFullyRevealed());
        }

        @Test
        void textRevealsOverTime() {
            manager.setCharsPerSecond(10f);
            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());

            // After 0.3s at 10 cps → 3 chars visible
            manager.update(0.3f);

            assertEquals("Hel", manager.getVisibleText());
            assertFalse(manager.isTextFullyRevealed());
        }

        @Test
        void textFullyRevealsOverTime() {
            manager.setCharsPerSecond(100f);
            Dialogue dialogue = dialogueWithLines("Hi");
            manager.startDialogue(dialogue, Map.of());

            manager.update(1.0f); // 100 chars in 1s, "Hi" is 2

            assertTrue(manager.isTextFullyRevealed());
            assertEquals("Hi", manager.getVisibleText());
        }

        @Test
        void interactSkipsToFullText() {
            manager.setCharsPerSecond(1f); // very slow
            Dialogue dialogue = dialogueWithLines("Hello world");
            manager.startDialogue(dialogue, Map.of());

            manager.update(0.001f); // reveal ~0 chars

            // Press interact → skip to full
            pressInteract();

            assertTrue(manager.isTextFullyRevealed());
            assertEquals("Hello world", manager.getVisibleText());
        }

        @Test
        void interactOnFullTextAdvancesToNext() {
            manager.setCharsPerSecond(1000f);
            Dialogue dialogue = dialogueWithLines("Line 1", "Line 2");
            manager.startDialogue(dialogue, Map.of());

            manager.update(1.0f); // fully reveal
            assertTrue(manager.isTextFullyRevealed());

            // Advance to next
            pressInteract();

            assertEquals("Line 2", manager.getFullText());
            assertEquals(1, manager.getCurrentEntryIndex());
        }
    }

    // ========================================================================
    // VARIABLE SUBSTITUTION
    // ========================================================================

    @Nested
    class VariableSubstitution {

        @Test
        void knownVariableReplaced() {
            Dialogue dialogue = dialogueWithLines("Hello [PLAYER_NAME]!");
            manager.startDialogue(dialogue, Map.of("PLAYER_NAME", "Red"));

            assertEquals("Hello Red!", manager.getFullText());
        }

        @Test
        void unknownVariableStaysLiteral() {
            Dialogue dialogue = dialogueWithLines("Hello [UNKNOWN]!");
            manager.startDialogue(dialogue, Map.of());

            assertEquals("Hello [UNKNOWN]!", manager.getFullText());
        }

        @Test
        void multipleVariablesReplaced() {
            Dialogue dialogue = dialogueWithLines("[TRAINER] wants to battle [POKEMON]!");
            manager.startDialogue(dialogue, Map.of("TRAINER", "Brock", "POKEMON", "Onix"));

            assertEquals("Brock wants to battle Onix!", manager.getFullText());
        }

        @Test
        void noVariablesNoChange() {
            Dialogue dialogue = dialogueWithLines("Plain text");
            manager.startDialogue(dialogue, Map.of());

            assertEquals("Plain text", manager.getFullText());
        }

        @Test
        void mergeOrderAutoStaticRuntime() {
            // Register auto variable
            manager.getVariableResolver().register("VAR", () -> "auto");

            // Static overrides auto, runtime overrides static
            Dialogue dialogue = dialogueWithLines("[VAR]");
            manager.startDialogue(dialogue,
                    Map.of("VAR", "static"),
                    Map.of("VAR", "runtime"));

            assertEquals("runtime", manager.getFullText());
        }
    }

    // ========================================================================
    // CHOICES
    // ========================================================================

    @Nested
    class Choices {

        @Test
        void showsChoicesAtEndOfLines() {
            Dialogue dialogue = dialogueWithChoices("Pick one",
                    List.of(
                            new Choice("Option A", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                            new Choice("Option B", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
                    ));

            manager.startDialogue(dialogue, Map.of());

            // Skip typewriter → auto-advances to choices (next entry is CHOICES)
            pressInteract();

            assertTrue(manager.isShowingChoices());
            assertEquals(0, manager.getSelectedChoice());
        }

        @Test
        void navigateChoicesDown() {
            Dialogue dialogue = dialogueWithChoices("Pick",
                    List.of(
                            new Choice("A", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                            new Choice("B", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                            new Choice("C", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
                    ));

            manager.startDialogue(dialogue, Map.of());
            pressInteract(); // skip typewriter → auto-advances to choices

            assertEquals(0, manager.getSelectedChoice());

            pressDown();
            assertEquals(1, manager.getSelectedChoice());

            pressDown();
            assertEquals(2, manager.getSelectedChoice());

            // Can't go past last
            pressDown();
            assertEquals(2, manager.getSelectedChoice());
        }

        @Test
        void navigateChoicesUp() {
            Dialogue dialogue = dialogueWithChoices("Pick",
                    List.of(
                            new Choice("A", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                            new Choice("B", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
                    ));

            manager.startDialogue(dialogue, Map.of());
            pressInteract(); // skip typewriter → auto-advances to choices

            // Go down then back up
            pressDown();
            assertEquals(1, manager.getSelectedChoice());

            pressUp();
            assertEquals(0, manager.getSelectedChoice());

            // Can't go above 0
            pressUp();
            assertEquals(0, manager.getSelectedChoice());
        }

        @Test
        void selectChoiceWithInteract() {
            Dialogue dialogue = dialogueWithChoices("Pick",
                    List.of(
                            new Choice("End", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
                    ));

            manager.startDialogue(dialogue, Map.of());
            pressInteract(); // skip typewriter → auto-advances to choices

            assertTrue(manager.isShowingChoices());

            // Select choice → END_CONVERSATION → endDialogue
            pressInteract();

            assertFalse(manager.isActive());
        }

        @Test
        void hasChoicesFalseSkipsChoices() {
            List<DialogueEntry> entries = new ArrayList<>();
            entries.add(new DialogueLine("Hello"));
            DialogueChoiceGroup group = new DialogueChoiceGroup(false, List.of(
                    new Choice("Won't see this", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
            ));
            entries.add(group);

            Dialogue dialogue = new Dialogue("test", entries);
            manager.startDialogue(dialogue, Map.of());

            // Skip typewriter → auto-advance to choice group with hasChoices=false → ends
            pressInteract();

            assertFalse(manager.isActive());
            assertFalse(manager.isShowingChoices());
        }

        @Test
        void emptyChoicesWithHasChoicesTrueEndsDialogue() {
            List<DialogueEntry> entries = new ArrayList<>();
            entries.add(new DialogueLine("Hello"));
            DialogueChoiceGroup group = new DialogueChoiceGroup(true, List.of());
            entries.add(group);

            Dialogue dialogue = new Dialogue("test", entries);
            manager.startDialogue(dialogue, Map.of());

            // Skip typewriter → auto-advance to choice group with empty choices → ends
            pressInteract();

            assertFalse(manager.isActive());
        }
    }

    // ========================================================================
    // RUNTIME VALIDATION
    // ========================================================================

    @Nested
    class RuntimeValidation {

        @Test
        void nullDialogueDoesNotCrash() {
            manager.startDialogue(null, Map.of());
            assertFalse(manager.isActive());
        }

        @Test
        void emptyEntriesDoesNotCrash() {
            Dialogue dialogue = new Dialogue("empty");
            manager.startDialogue(dialogue, Map.of());
            assertFalse(manager.isActive());
        }

        @Test
        void nullDialogueDuringChainEndsDialogue() {
            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());
            assertTrue(manager.isActive());

            // Simulate chain with null
            manager.startDialogue(null, Map.of());
            assertFalse(manager.isActive());
        }

        @Test
        void emptyDialogueDuringChainEndsDialogue() {
            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());
            assertTrue(manager.isActive());

            manager.startDialogue(new Dialogue("empty"), Map.of());
            assertFalse(manager.isActive());
        }
    }

    // ========================================================================
    // LINE-LEVEL onCompleteEvent
    // ========================================================================

    @Nested
    class LineEvents {

        @Test
        void onCompleteEventFiresWhenAdvancingPastLine() {
            List<String> firedEvents = new ArrayList<>();

            DialogueLine line = new DialogueLine("Hello", DialogueEventRef.custom("PLAY_SOUND"));
            Dialogue dialogue = new Dialogue("test", List.of(line));

            manager.addEventListenerCallback("PLAY_SOUND", () -> firedEvents.add("PLAY_SOUND"));
            manager.startDialogue(dialogue, Map.of());

            assertTrue(firedEvents.isEmpty());

            // Advance past the line
            pressInteract(); pressInteract();

            assertTrue(firedEvents.contains("PLAY_SOUND"));
        }

        @Test
        void onCompleteEventDoesNotFireBeforeAdvancing() {
            List<String> firedEvents = new ArrayList<>();

            DialogueLine line = new DialogueLine("Hello", DialogueEventRef.custom("EVENT"));
            Dialogue dialogue = new Dialogue("test", List.of(line, new DialogueLine("World")));

            manager.addEventListenerCallback("EVENT", () -> firedEvents.add("EVENT"));
            manager.startDialogue(dialogue, Map.of());

            // Only skip typewriter, don't advance
            pressInteract();

            assertTrue(firedEvents.isEmpty());
        }

        @Test
        void noOnCompleteEventDoesNotCrash() {
            DialogueLine line = new DialogueLine("Hello");
            assertNull(line.getOnCompleteEvent());

            Dialogue dialogue = new Dialogue("test", List.of(line));
            manager.startDialogue(dialogue, Map.of());

            // Advance past — should not crash
            pressInteract(); pressInteract();

            assertFalse(manager.isActive());
        }
    }

    // ========================================================================
    // CUSTOM EVENT DISPATCH
    // ========================================================================

    @Nested
    class CustomEventDispatch {

        @Test
        void customEventChoiceActionDispatchesToListeners() {
            List<String> firedEvents = new ArrayList<>();

            Dialogue dialogue = dialogueWithChoices("Pick",
                    List.of(new Choice("Fire it", ChoiceAction.customEvent("OPEN_DOOR"))));

            manager.addEventListenerCallback("OPEN_DOOR", () -> firedEvents.add("OPEN_DOOR"));
            manager.startDialogue(dialogue, Map.of());

            pressInteract(); // skip typewriter → auto-advances to choices
            pressInteract(); // select choice

            assertTrue(firedEvents.contains("OPEN_DOOR"));
            assertFalse(manager.isActive());
        }
    }

    // ========================================================================
    // PAUSE / RESUME
    // ========================================================================

    @Nested
    class PauseResume {

        @Test
        void startDialoguePausesPausables() {
            TestPausable pausable = addPausableToScene();

            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());

            assertTrue(pausable.paused);
        }

        @Test
        void endDialogueResumesPausables() {
            TestPausable pausable = addPausableToScene();

            Dialogue dialogue = dialogueWithLines("Hello");
            manager.startDialogue(dialogue, Map.of());
            manager.endDialogue();

            assertFalse(pausable.paused);
        }

        private TestPausable addPausableToScene() {
            GameObject npc = new GameObject("NPC");
            TestPausable pausable = new TestPausable();
            npc.addComponent(pausable);
            scene.addGameObject(npc);
            return pausable;
        }
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

    private void pressDown() {
        mockInput.pressKey(KeyCode.S);
        mockInput.endFrame();
        mockInput.releaseKey(KeyCode.S);
        manager.update(0.016f);  // choice navigation triggers on key-up
        mockInput.endFrame();
    }

    private void pressUp() {
        mockInput.pressKey(KeyCode.W);
        mockInput.endFrame();
        mockInput.releaseKey(KeyCode.W);
        manager.update(0.016f);  // choice navigation triggers on key-up
        mockInput.endFrame();
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

    private Dialogue dialogueWithLines(String... lines) {
        List<DialogueEntry> entries = new ArrayList<>();
        for (String text : lines) {
            entries.add(new DialogueLine(text));
        }
        return new Dialogue("test_dialogue", entries);
    }

    private Dialogue dialogueWithChoices(String lineText, List<Choice> choices) {
        List<DialogueEntry> entries = new ArrayList<>();
        entries.add(new DialogueLine(lineText));
        entries.add(new DialogueChoiceGroup(true, choices));
        return new Dialogue("test_dialogue", entries);
    }

    // ========================================================================
    // TEST DOUBLES
    // ========================================================================

    private static class TestScene extends Scene {
        public TestScene(String name) { super(name); }
        @Override public void onLoad() {}
    }

    private static class TestPausable extends com.pocket.rpg.components.Component
            implements com.pocket.rpg.IPausable {
        boolean paused = false;
        @Override public void onPause() { paused = true; }
        @Override public void onResume() { paused = false; }
    }
}
