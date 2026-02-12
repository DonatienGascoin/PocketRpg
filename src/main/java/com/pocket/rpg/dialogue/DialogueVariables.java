package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Global asset holding all variable definitions used across dialogues.
 * <p>
 * Loaded from a well-known convention path:
 * {@code gameData/assets/dialogues/variables.dialogue-vars.json}
 * <p>
 * Accessed via {@code Assets.load("dialogues/variables.dialogue-vars.json")}.
 */
@Getter
@Setter
public class DialogueVariables {

    private List<DialogueVariable> variables;

    public DialogueVariables() {
        this.variables = new ArrayList<>();
    }

    public DialogueVariables(List<DialogueVariable> variables) {
        this.variables = variables != null ? new ArrayList<>(variables) : new ArrayList<>();
    }

    /**
     * Supports hot-reload by copying data from another instance.
     */
    public void copyFrom(DialogueVariables other) {
        this.variables.clear();
        this.variables.addAll(other.variables);
    }
}
