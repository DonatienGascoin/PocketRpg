package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Pairs a list of conditions with a dialogue asset reference.
 * <p>
 * Used by DialogueInteractable for conditional dialogue selection.
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

    /** The dialogue to use if conditions match. */
    private Dialogue dialogue;

    public ConditionalDialogue() {
        this.conditions = new ArrayList<>();
    }

    public ConditionalDialogue(List<DialogueCondition> conditions, Dialogue dialogue) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
        this.dialogue = dialogue;
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
}
