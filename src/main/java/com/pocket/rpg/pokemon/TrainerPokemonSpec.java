package com.pocket.rpg.pokemon;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Declarative specification of a trainer's Pokemon (species, level, optional moves).
 * <p>
 * Used by both {@link TrainerDefinition} (registry) and scene-level TrainerComponent.
 */
@Getter @Setter
public class TrainerPokemonSpec {
    private String speciesId = "";
    private int level = 5;
    private List<String> moves;

    public TrainerPokemonSpec() {}

    public TrainerPokemonSpec(String speciesId, int level, List<String> moves) {
        this.speciesId = speciesId;
        this.level = level;
        this.moves = moves;
    }

    /**
     * Deep copy for undo snapshots.
     */
    public TrainerPokemonSpec copy() {
        return new TrainerPokemonSpec(
                speciesId,
                level,
                moves != null ? new java.util.ArrayList<>(moves) : null
        );
    }
}
