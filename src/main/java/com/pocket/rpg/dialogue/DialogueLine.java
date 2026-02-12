package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

/**
 * A single text line in a dialogue. Supports {@code [VARIABLE]} tags
 * that are substituted at runtime.
 * <p>
 * Optionally has an {@link #onCompleteEvent} that fires when the player
 * advances past this line.
 */
@Getter
@Setter
public final class DialogueLine implements DialogueEntry {

    private String text;

    /** Optional event fired when the player advances past this line. */
    private DialogueEventRef onCompleteEvent;

    public DialogueLine() {
        this.text = "";
    }

    public DialogueLine(String text) {
        this.text = text;
    }

    public DialogueLine(String text, DialogueEventRef onCompleteEvent) {
        this.text = text;
        this.onCompleteEvent = onCompleteEvent;
    }
}
