package com.pocket.rpg.dialogue;

/**
 * The type of action a dialogue choice triggers.
 */
public enum ChoiceActionType {
    /** Chain to another dialogue asset. */
    DIALOGUE,
    /** Trigger a built-in engine event (e.g. END_CONVERSATION). */
    BUILT_IN_EVENT,
    /** Trigger a custom game event (validated against DialogueEvents asset). */
    CUSTOM_EVENT
}
