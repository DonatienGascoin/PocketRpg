package com.pocket.rpg.dialogue;

import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairs a list of conditions with a dialogue asset reference.
 * <p>
 * Used by DialogueComponent for conditional dialogue selection.
 * Conditions are evaluated with AND logic — all must be met.
 * <p>
 * Selection is top-to-bottom, first match wins. If none match,
 * the default dialogue on the component is used.
 */
@Getter
@Setter
public class ConditionalDialogue {

    /** ALL conditions must be true (AND logic). */
    private List<DialogueCondition> conditions;

    /** Asset path to the dialogue to use if conditions match. */
    private String dialoguePath;

    public ConditionalDialogue() {
        this.conditions = new ArrayList<>();
        this.dialoguePath = "";
    }

    public ConditionalDialogue(List<DialogueCondition> conditions, String dialoguePath) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
        this.dialoguePath = dialoguePath;
    }

    /**
     * Returns {@code true} if ALL conditions are met.
     * An empty conditions list returns {@code true}.
     */
    public boolean allConditionsMet() {
        for (DialogueCondition condition : conditions) {
            if (!condition.isMet()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lazily resolves the dialogue asset.
     *
     * @return the loaded Dialogue, or null if path is unset
     */
    public Dialogue getDialogue() {
        if (dialoguePath == null || dialoguePath.isBlank()) {
            return null;
        }
        return Assets.load(dialoguePath, Dialogue.class);
    }
}
