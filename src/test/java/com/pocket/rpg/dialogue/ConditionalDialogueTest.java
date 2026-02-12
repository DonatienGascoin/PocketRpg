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

class ConditionalDialogueTest {

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

    // ========================================================================
    // DialogueCondition.isMet()
    // ========================================================================

    @Test
    void firedConditionMetWhenEventFired() {
        DialogueEventStore.markFired("GOT_BADGE_1");

        DialogueCondition condition = new DialogueCondition("GOT_BADGE_1",
                DialogueCondition.ExpectedState.FIRED);
        assertTrue(condition.isMet());
    }

    @Test
    void firedConditionNotMetWhenEventNotFired() {
        DialogueCondition condition = new DialogueCondition("GOT_BADGE_1",
                DialogueCondition.ExpectedState.FIRED);
        assertFalse(condition.isMet());
    }

    @Test
    void notFiredConditionMetWhenEventNotFired() {
        DialogueCondition condition = new DialogueCondition("GOT_BADGE_1",
                DialogueCondition.ExpectedState.NOT_FIRED);
        assertTrue(condition.isMet());
    }

    @Test
    void notFiredConditionNotMetWhenEventFired() {
        DialogueEventStore.markFired("GOT_BADGE_1");

        DialogueCondition condition = new DialogueCondition("GOT_BADGE_1",
                DialogueCondition.ExpectedState.NOT_FIRED);
        assertFalse(condition.isMet());
    }

    // ========================================================================
    // ConditionalDialogue.allConditionsMet()
    // ========================================================================

    @Test
    void emptyConditionsReturnsTrue() {
        ConditionalDialogue cd = new ConditionalDialogue(List.of(), "dialogues/test.dialogue.json");
        assertTrue(cd.allConditionsMet());
    }

    @Test
    void singleFiredConditionMet() {
        DialogueEventStore.markFired("GOT_BADGE_1");

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge.dialogue.json");

        assertTrue(cd.allConditionsMet());
    }

    @Test
    void singleFiredConditionNotMet() {
        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/badge.dialogue.json");

        assertFalse(cd.allConditionsMet());
    }

    @Test
    void multipleConditionsAllMet() {
        DialogueEventStore.markFired("GOT_BADGE_1");
        DialogueEventStore.markFired("TALKED_TO_RIVAL");

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED),
                new DialogueCondition("TALKED_TO_RIVAL", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/congrats.dialogue.json");

        assertTrue(cd.allConditionsMet());
    }

    @Test
    void multipleConditionsOneFails() {
        DialogueEventStore.markFired("GOT_BADGE_1");
        // TALKED_TO_RIVAL not fired

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED),
                new DialogueCondition("TALKED_TO_RIVAL", DialogueCondition.ExpectedState.FIRED)
        ), "dialogues/congrats.dialogue.json");

        assertFalse(cd.allConditionsMet());
    }

    @Test
    void mixedFiredAndNotFiredConditions() {
        DialogueEventStore.markFired("GOT_BADGE_1");
        // BOSS_DEFEATED not fired

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED),
                new DialogueCondition("BOSS_DEFEATED", DialogueCondition.ExpectedState.NOT_FIRED)
        ), "dialogues/pre_boss.dialogue.json");

        assertTrue(cd.allConditionsMet());
    }

    @Test
    void mixedConditionsFailsWhenNotFiredIsFired() {
        DialogueEventStore.markFired("GOT_BADGE_1");
        DialogueEventStore.markFired("BOSS_DEFEATED");

        ConditionalDialogue cd = new ConditionalDialogue(List.of(
                new DialogueCondition("GOT_BADGE_1", DialogueCondition.ExpectedState.FIRED),
                new DialogueCondition("BOSS_DEFEATED", DialogueCondition.ExpectedState.NOT_FIRED)
        ), "dialogues/pre_boss.dialogue.json");

        assertFalse(cd.allConditionsMet());
    }

    // ========================================================================
    // HELPERS
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
