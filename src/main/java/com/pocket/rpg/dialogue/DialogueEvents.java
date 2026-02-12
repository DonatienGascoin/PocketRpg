package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Global asset holding all custom event names used across dialogues.
 * <p>
 * Loaded from a well-known convention path:
 * {@code gameData/assets/dialogues/events.dialogue-events.json}
 * <p>
 * Accessed via {@code Assets.load("dialogues/events.dialogue-events.json")}.
 * Custom events are dispatched to {@code DialogueEventListener} components in the scene.
 */
@Getter
@Setter
public class DialogueEvents {

    private List<String> events;

    public DialogueEvents() {
        this.events = new ArrayList<>();
    }

    public DialogueEvents(List<String> events) {
        this.events = events != null ? new ArrayList<>(events) : new ArrayList<>();
    }

    /**
     * Supports hot-reload by copying data from another instance.
     */
    public void copyFrom(DialogueEvents other) {
        this.events.clear();
        this.events.addAll(other.events);
    }
}
