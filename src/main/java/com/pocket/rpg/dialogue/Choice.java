package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

/**
 * A single choice within a {@link DialogueChoiceGroup}.
 * Has display text and an action to execute when selected.
 */
@Getter
@Setter
public class Choice {

    private String text;
    private ChoiceAction action;

    public Choice() {
        this.text = "";
        this.action = new ChoiceAction();
    }

    public Choice(String text, ChoiceAction action) {
        this.text = text;
        this.action = action;
    }
}
