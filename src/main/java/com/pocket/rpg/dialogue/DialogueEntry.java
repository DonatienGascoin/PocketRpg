package com.pocket.rpg.dialogue;

/**
 * Base type for dialogue entries. A dialogue is an ordered list of entries,
 * each being either a {@link DialogueLine} or a {@link DialogueChoiceGroup}.
 */
public sealed interface DialogueEntry permits DialogueLine, DialogueChoiceGroup {
}
