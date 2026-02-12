package com.pocket.rpg.dialogue;

import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A branching point in a dialogue. Must be the last entry.
 * <p>
 * When {@link #hasChoices} is false, the choices list is hidden in the editor
 * and ignored at runtime — the dialogue ends after the last line.
 * <p>
 * Maximum {@value #MAX_CHOICES} choices per group. Enforced in the constructor,
 * setter, and editor. Excess choices are silently dropped with a warning.
 */
@Getter
public final class DialogueChoiceGroup implements DialogueEntry {

    public static final int MAX_CHOICES = 4;

    private static final Logger LOG = Log.getLogger(DialogueChoiceGroup.class);

    private boolean hasChoices;
    private List<Choice> choices;

    public DialogueChoiceGroup() {
        this.hasChoices = true;
        this.choices = new ArrayList<>();
    }

    public DialogueChoiceGroup(boolean hasChoices, List<Choice> choices) {
        this.hasChoices = hasChoices;
        this.choices = clampChoices(choices != null ? new ArrayList<>(choices) : new ArrayList<>());
    }

    public void setHasChoices(boolean hasChoices) {
        this.hasChoices = hasChoices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = clampChoices(choices != null ? new ArrayList<>(choices) : new ArrayList<>());
    }

    private List<Choice> clampChoices(List<Choice> list) {
        if (list.size() > MAX_CHOICES) {
            LOG.warn("Choice group has " + list.size() + " choices, clamping to " + MAX_CHOICES);
            return new ArrayList<>(list.subList(0, MAX_CHOICES));
        }
        return list;
    }
}
