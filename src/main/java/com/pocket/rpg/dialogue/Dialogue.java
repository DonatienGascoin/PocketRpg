package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A dialogue asset loaded from {@code .dialogue.json} files.
 * <p>
 * Contains an ordered list of {@link DialogueEntry} items — each either a
 * {@link DialogueLine} or a {@link DialogueChoiceGroup}. The name is derived
 * from the filename at load time (not stored in JSON).
 * <p>
 * Rules:
 * <ul>
 *   <li>Must have at least one entry (enforced by editor, loader, and runtime)</li>
 *   <li>A {@link DialogueChoiceGroup} can only appear as the last entry</li>
 *   <li>Maximum 4 choices per group</li>
 * </ul>
 */
@Getter
@Setter
public class Dialogue {

    /** Display name derived from filename (e.g. "professor_greeting"). */
    private String name;

    /** Ordered sequence of lines and/or a final choice group. */
    private List<DialogueEntry> entries;

    public Dialogue() {
        this.name = "";
        this.entries = new ArrayList<>();
    }

    public Dialogue(String name) {
        this.name = name;
        this.entries = new ArrayList<>();
    }

    public Dialogue(String name, List<DialogueEntry> entries) {
        this.name = name;
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }

    /**
     * Supports hot-reload by copying data from another instance.
     */
    public void copyFrom(Dialogue other) {
        this.name = other.name;
        this.entries.clear();
        this.entries.addAll(other.entries);
    }
}
