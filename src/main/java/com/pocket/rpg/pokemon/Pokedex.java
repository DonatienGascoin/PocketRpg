package com.pocket.rpg.pokemon;

import lombok.Getter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of all Pokemon species and move definitions.
 * Loaded via {@code AssetLoader<Pokedex>} pipeline.
 */
public class Pokedex {
    @Getter
    private Map<String, PokemonSpecies> species = new HashMap<>();
    @Getter
    private Map<String, Move> moves = new HashMap<>();

    public Pokedex() {}

    public PokemonSpecies getSpecies(String id) {
        return species.get(id);
    }

    public Move getMove(String id) {
        return moves.get(id);
    }

    public Collection<PokemonSpecies> getAllSpecies() {
        return species.values();
    }

    public Collection<Move> getAllMoves() {
        return moves.values();
    }

    public void addSpecies(PokemonSpecies sp) {
        species.put(sp.getSpeciesId(), sp);
    }

    public void addMove(Move move) {
        moves.put(move.getMoveId(), move);
    }

    /**
     * Mutates this instance in place to match the other.
     * Required by the hot-reload contract.
     */
    public void copyFrom(Pokedex other) {
        this.species.clear();
        this.species.putAll(other.species);
        this.moves.clear();
        this.moves.putAll(other.moves);
    }
}
