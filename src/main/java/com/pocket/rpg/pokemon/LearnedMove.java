package com.pocket.rpg.pokemon;

import lombok.Getter;
import lombok.Setter;

/**
 * Entry in a species learnset — maps a level to a move.
 */
@Getter
@Setter
public class LearnedMove {
    private int level;
    private String moveId;

    public LearnedMove() {}

    public LearnedMove(int level, String moveId) {
        this.level = level;
        this.moveId = moveId;
    }
}
