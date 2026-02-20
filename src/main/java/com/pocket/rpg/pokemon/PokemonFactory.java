package com.pocket.rpg.pokemon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Factory for creating Pokemon instances with proper initialization.
 * <p>
 * The {@code trainerName} parameter is the Original Trainer name stamped on the Pokemon:
 * <ul>
 *   <li>Player Pokemon: {@code PlayerData.load().playerName}</li>
 *   <li>NPC trainer Pokemon: {@code TrainerComponent.trainerName}</li>
 *   <li>Wild Pokemon: hardcoded "Wild"</li>
 * </ul>
 */
public final class PokemonFactory {

    private static final Random RANDOM = new Random();
    private static final int MAX_MOVES = 4;
    private static final int MAX_IV = 31;

    private PokemonFactory() {}

    /**
     * Creates a wild Pokemon at the given level.
     * Random IVs, random Nature, level-appropriate moves from learnset.
     */
    public static PokemonInstance createWild(Pokedex pokedex, String speciesId, int level) {
        PokemonSpecies species = requireSpecies(pokedex, speciesId);

        PokemonInstance p = new PokemonInstance(
                speciesId,
                null,
                level,
                species.getGrowthRate().expForLevel(level),
                randomNature(),
                randomIvs(),
                selectMovesForLevel(pokedex, species, level),
                "Wild",
                null
        );
        p.setSpeciesData(species);
        p.setCurrentHp(p.calcMaxHp());
        return p;
    }

    /**
     * Creates a starter Pokemon for the player.
     * Same as wild but with the player as original trainer.
     */
    public static PokemonInstance createStarter(Pokedex pokedex, String speciesId, int level, String trainerName) {
        PokemonSpecies species = requireSpecies(pokedex, speciesId);

        PokemonInstance p = new PokemonInstance(
                speciesId,
                null,
                level,
                species.getGrowthRate().expForLevel(level),
                randomNature(),
                randomIvs(),
                selectMovesForLevel(pokedex, species, level),
                trainerName,
                "pokeball"
        );
        p.setSpeciesData(species);
        p.setCurrentHp(p.calcMaxHp());
        return p;
    }

    /**
     * Creates a trainer-owned Pokemon with specific moves.
     * If {@code moveIds} is null, uses level-appropriate learnset moves.
     */
    public static PokemonInstance createTrainer(Pokedex pokedex, String speciesId, int level,
                                                 List<String> moveIds, String trainerName) {
        PokemonSpecies species = requireSpecies(pokedex, speciesId);

        List<MoveSlot> moves;
        if (moveIds != null && !moveIds.isEmpty()) {
            moves = new ArrayList<>();
            for (String moveId : moveIds) {
                Move move = pokedex.getMove(moveId);
                if (move != null) {
                    moves.add(new MoveSlot(moveId, move.getPp()));
                }
            }
        } else {
            moves = selectMovesForLevel(pokedex, species, level);
        }

        PokemonInstance p = new PokemonInstance(
                speciesId,
                null,
                level,
                species.getGrowthRate().expForLevel(level),
                randomNature(),
                randomIvs(),
                moves,
                trainerName,
                "pokeball"
        );
        p.setSpeciesData(species);
        p.setCurrentHp(p.calcMaxHp());
        return p;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static PokemonSpecies requireSpecies(Pokedex pokedex, String speciesId) {
        PokemonSpecies species = pokedex.getSpecies(speciesId);
        if (species == null) {
            throw new IllegalArgumentException("Unknown species: " + speciesId);
        }
        return species;
    }

    private static Nature randomNature() {
        Nature[] natures = Nature.values();
        return natures[RANDOM.nextInt(natures.length)];
    }

    private static Stats randomIvs() {
        return new Stats(
                RANDOM.nextInt(MAX_IV + 1),
                RANDOM.nextInt(MAX_IV + 1),
                RANDOM.nextInt(MAX_IV + 1),
                RANDOM.nextInt(MAX_IV + 1),
                RANDOM.nextInt(MAX_IV + 1),
                RANDOM.nextInt(MAX_IV + 1)
        );
    }

    /**
     * Selects the last {@link #MAX_MOVES} moves from the learnset at or below the given level.
     */
    private static List<MoveSlot> selectMovesForLevel(Pokedex pokedex, PokemonSpecies species, int level) {
        List<LearnedMove> eligible = new ArrayList<>();
        for (LearnedMove lm : species.getLearnset()) {
            if (lm.getLevel() <= level) {
                eligible.add(lm);
            }
        }

        // Take the last MAX_MOVES entries (most recent moves learned)
        int start = Math.max(0, eligible.size() - MAX_MOVES);
        List<MoveSlot> moves = new ArrayList<>();
        for (int i = start; i < eligible.size(); i++) {
            String moveId = eligible.get(i).getMoveId();
            Move move = pokedex.getMove(moveId);
            int pp = move != null ? move.getPp() : 10;
            moves.add(new MoveSlot(moveId, pp));
        }

        return moves;
    }
}
