package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

/**
 * A single condition in a {@link ConditionalDialogue} entry.
 * <p>
 * Checks whether a named dialogue event has been fired (or not) via
 * {@link DialogueEventStore}. The {@code eventName} comes from the
 * {@link DialogueEvents} asset (set via dropdown, never typed by hand).
 */
@Getter
@Setter
public class DialogueCondition {

    public enum ExpectedState {
        FIRED,
        NOT_FIRED
    }

    /** Event name from the DialogueEvents asset. */
    private String eventName;

    /** Whether we expect the event to have been fired or not. */
    private ExpectedState expectedState;

    public DialogueCondition() {
        this.eventName = "";
        this.expectedState = ExpectedState.FIRED;
    }

    public DialogueCondition(String eventName, ExpectedState expectedState) {
        this.eventName = eventName;
        this.expectedState = expectedState;
    }

    /**
     * Evaluates this condition against the current event store state.
     */
    public boolean isMet() {
        boolean fired = DialogueEventStore.hasFired(eventName);
        return switch (expectedState) {
            case FIRED -> fired;
            case NOT_FIRED -> !fired;
        };
    }
}
