package com.pocket.rpg.dialogue;

import com.pocket.rpg.save.SaveManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DialogueEventStoreTest {

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
    void unfiredEventReturnsFalse() {
        assertFalse(DialogueEventStore.hasFired("OPEN_DOOR"));
    }

    @Test
    void markFiredThenHasFiredReturnsTrue() {
        DialogueEventStore.markFired("OPEN_DOOR");
        assertTrue(DialogueEventStore.hasFired("OPEN_DOOR"));
    }

    @Test
    void differentEventsAreIndependent() {
        DialogueEventStore.markFired("OPEN_DOOR");

        assertTrue(DialogueEventStore.hasFired("OPEN_DOOR"));
        assertFalse(DialogueEventStore.hasFired("GIVE_ITEM"));
    }

    @Test
    void markFiredMultipleEvents() {
        DialogueEventStore.markFired("OPEN_DOOR");
        DialogueEventStore.markFired("GIVE_ITEM");
        DialogueEventStore.markFired("UNLOCK_PATH");

        assertTrue(DialogueEventStore.hasFired("OPEN_DOOR"));
        assertTrue(DialogueEventStore.hasFired("GIVE_ITEM"));
        assertTrue(DialogueEventStore.hasFired("UNLOCK_PATH"));
        assertFalse(DialogueEventStore.hasFired("START_QUEST"));
    }

    @Test
    void markFiredIsIdempotent() {
        DialogueEventStore.markFired("OPEN_DOOR");
        DialogueEventStore.markFired("OPEN_DOOR");
        assertTrue(DialogueEventStore.hasFired("OPEN_DOOR"));
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
