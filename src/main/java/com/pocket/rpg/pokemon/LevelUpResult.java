package com.pocket.rpg.pokemon;

import java.util.List;

/**
 * Result of gaining experience, returned by {@link PokemonInstance#gainExp(int)}.
 *
 * @param leveledUp   whether at least one level was gained
 * @param oldLevel    level before gaining exp
 * @param newLevel    level after gaining exp
 * @param newMoves    moveIds learned at the new level(s) from the learnset
 * @param canEvolve   whether evolution conditions are now met
 * @param evolvesInto speciesId of evolution target, null if none
 */
public record LevelUpResult(
        boolean leveledUp,
        int oldLevel,
        int newLevel,
        List<String> newMoves,
        boolean canEvolve,
        String evolvesInto
) {}
