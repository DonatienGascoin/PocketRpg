package com.pocket.rpg.dialogue;

import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

/**
 * The action triggered when a dialogue choice is selected.
 * <p>
 * Depending on {@link #type}:
 * <ul>
 *   <li>{@code DIALOGUE} — chains to another dialogue via {@link #dialoguePath}</li>
 *   <li>{@code BUILT_IN_EVENT} — triggers a {@link DialogueEvent}</li>
 *   <li>{@code CUSTOM_EVENT} — triggers a custom event by name</li>
 * </ul>
 * <p>
 * Dialogue references are stored as path strings and resolved lazily via {@link Assets#load}.
 */
@Getter
@Setter
public class ChoiceAction {

    private ChoiceActionType type;

    /** Asset path for DIALOGUE type (e.g. "dialogues/professor_lore.dialogue.json"). */
    private String dialoguePath;

    /** Built-in event for BUILT_IN_EVENT type. */
    private DialogueEvent builtInEvent;

    /** Custom event name for CUSTOM_EVENT type. Validated against DialogueEvents asset. */
    private String customEvent;

    public ChoiceAction() {
    }

    public static ChoiceAction dialogue(String dialoguePath) {
        ChoiceAction action = new ChoiceAction();
        action.type = ChoiceActionType.DIALOGUE;
        action.dialoguePath = dialoguePath;
        return action;
    }

    public static ChoiceAction builtInEvent(DialogueEvent event) {
        ChoiceAction action = new ChoiceAction();
        action.type = ChoiceActionType.BUILT_IN_EVENT;
        action.builtInEvent = event;
        return action;
    }

    public static ChoiceAction customEvent(String eventName) {
        ChoiceAction action = new ChoiceAction();
        action.type = ChoiceActionType.CUSTOM_EVENT;
        action.customEvent = eventName;
        return action;
    }

    /**
     * Lazily resolves the dialogue asset for DIALOGUE type actions.
     *
     * @return the loaded Dialogue, or null if path is unset or asset not found
     */
    public Dialogue getDialogue() {
        if (type != ChoiceActionType.DIALOGUE || dialoguePath == null || dialoguePath.isBlank()) {
            return null;
        }
        return Assets.load(dialoguePath, Dialogue.class);
    }
}
