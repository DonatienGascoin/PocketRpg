package com.pocket.rpg.dialogue;

import com.pocket.rpg.save.SaveManager;

/**
 * Thin wrapper over {@link SaveManager} for dialogue event persistence.
 * <p>
 * All event persistence goes through this class — no raw namespace strings
 * passed to SaveManager anywhere else. Events are stored as boolean flags
 * in the {@code "dialogue_events"} namespace of global state.
 * <p>
 * Events survive scene transitions and save/load cycles.
 */
public class DialogueEventStore {

    private static final String NAMESPACE = "dialogue_events";

    private DialogueEventStore() {
    }

    /**
     * Record that a custom event has been fired. Persists across scenes and save/load.
     */
    public static void markFired(String eventName) {
        SaveManager.setGlobal(NAMESPACE, eventName, true);
    }

    /**
     * Check if a custom event has ever been fired this playthrough.
     */
    public static boolean hasFired(String eventName) {
        return SaveManager.getGlobal(NAMESPACE, eventName, false);
    }
}
