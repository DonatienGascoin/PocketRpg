package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

/**
 * Wraps a reference to either a built-in or custom dialogue event.
 * <p>
 * Used by {@link DialogueLine#getOnCompleteEvent()}, {@link ChoiceAction},
 * and DialogueComponent's onConversationEnd field.
 */
@Getter
@Setter
public class DialogueEventRef {

    public enum Category {
        BUILT_IN,
        CUSTOM
    }

    private Category category;

    /** The built-in event, if {@code category == BUILT_IN}. */
    private DialogueEvent builtInEvent;

    /** The custom event name, if {@code category == CUSTOM}. Validated against DialogueEvents asset. */
    private String customEvent;

    public DialogueEventRef() {
    }

    public static DialogueEventRef builtIn(DialogueEvent event) {
        DialogueEventRef ref = new DialogueEventRef();
        ref.category = Category.BUILT_IN;
        ref.builtInEvent = event;
        return ref;
    }

    public static DialogueEventRef custom(String eventName) {
        DialogueEventRef ref = new DialogueEventRef();
        ref.category = Category.CUSTOM;
        ref.customEvent = eventName;
        return ref;
    }

    public boolean isBuiltIn() {
        return category == Category.BUILT_IN;
    }

    public boolean isCustom() {
        return category == Category.CUSTOM;
    }
}
