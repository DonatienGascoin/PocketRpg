package com.pocket.rpg.dialogue;

import com.pocket.rpg.save.SaveManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the conditional dialogue selection pattern:
 * iterate top-to-bottom, first match wins, no match returns default.
 * <p>
 * This logic will live in {@code DialogueComponent.selectDialogue()} at runtime.
 * Here we test the pattern independently using the data model classes.
 */
class DialogueSelectionTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        resetSaveManager();
        initSaveManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSaveManager();
    }

    @Test
    void emptyListReturnsDefault() {
        int match = selectIndex(List.of());
        assertEquals(-1, match);
    }

    @Test
    void noMatchReturnsDefault() {
        // Condition requires GOT_BADGE_1 fired, but it hasn't been
        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge.dialogue.json");

        int match = selectIndex(List.of(cd));
        assertEquals(-1, match);
    }

    @Test
    void firstMatchWins() {
        DialogueEventStore.markFired("GOT_BADGE_1");

        // Both conditions match GOT_BADGE_1 FIRED, but first one should win
        ConditionalDialogue cd0 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge1.dialogue.json");
        ConditionalDialogue cd1 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge2.dialogue.json");

        int match = selectIndex(List.of(cd0, cd1));
        assertEquals(0, match);
    }

    @Test
    void secondMatchWhenFirstFails() {
        DialogueEventStore.markFired("GOT_BADGE_1");
        // TALKED_TO_RIVAL not fired

        // Entry 0: requires GOT_BADGE_1 + TALKED_TO_RIVAL — fails
        ConditionalDialogue cd0 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED),
                new DialogueCondition("TALKED_TO_RIVAL", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/congrats.dialogue.json");

        // Entry 1: requires GOT_BADGE_1 only — matches
        ConditionalDialogue cd1 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge_only.dialogue.json");

        int match = selectIndex(List.of(cd0, cd1));
        assertEquals(1, match);
    }

    @Test
    void orderMattersMoreSpecificFirst() {
        DialogueEventStore.markFired("GOT_BADGE_1");
        DialogueEventStore.markFired("TALKED_TO_RIVAL");

        // More specific first — requires both events
        ConditionalDialogue cd0 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED),
                new DialogueCondition("TALKED_TO_RIVAL", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/congrats.dialogue.json");

        // Less specific second — requires one event
        ConditionalDialogue cd1 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge_only.dialogue.json");

        int match = selectIndex(List.of(cd0, cd1));
        assertEquals(0, match);
    }

    @Test
    void emptyConditionsAlwaysMatch() {
        ConditionalDialogue cd = new ConditionalDialogue(List.of(), "dialogues/always.dialogue.json");

        int match = selectIndex(List.of(cd));
        assertEquals(0, match);
    }

    @Test
    void stateChangesAffectSelection() {
        ConditionalDialogue cd0 = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge.dialogue.json");

        // Before firing — no match
        assertEquals(-1, selectIndex(List.of(cd0)));

        // After firing — matches
        DialogueEventStore.markFired("GOT_BADGE_1");
        assertEquals(0, selectIndex(List.of(cd0)));
    }

    // ========================================================================
    // SELECTION HELPER (mirrors DialogueComponent.selectDialogue())
    // ========================================================================

    /**
     * Returns the index of the first matching ConditionalDialogue, or -1 if
     * none match (meaning the default fallback should be used).
     */
    private int selectIndex(List<ConditionalDialogue> conditionals) {
        for (int i = 0; i < conditionals.size(); i++) {
            if (conditionals.get(i).allConditionsMet()) {
                return i;
            }
        }
        return -1;
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
