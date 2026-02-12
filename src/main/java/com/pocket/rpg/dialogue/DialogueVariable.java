package com.pocket.rpg.dialogue;

import lombok.Getter;
import lombok.Setter;

/**
 * A single variable definition in the global {@link DialogueVariables} asset.
 * <p>
 * The {@link #type} determines how the variable's value is provided:
 * <ul>
 *   <li>{@code AUTO} — Resolved from game state (e.g. PLAYER_NAME, MONEY). Always available.</li>
 *   <li>{@code STATIC} — Set per-NPC in the DialogueInteractable inspector (e.g. TRAINER_NAME).</li>
 *   <li>{@code RUNTIME} — Provided programmatically at dialogue start (e.g. POKEMON_NAME).</li>
 * </ul>
 */
@Getter
@Setter
public class DialogueVariable {

    public enum Type {
        AUTO,
        STATIC,
        RUNTIME
    }

    private String name;
    private Type type;

    public DialogueVariable() {
        this.name = "";
        this.type = Type.STATIC;
    }

    public DialogueVariable(String name, Type type) {
        this.name = name;
        this.type = type;
    }
}
