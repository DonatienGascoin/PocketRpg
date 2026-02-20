package com.pocket.rpg.pokemon;

import lombok.Getter;
import lombok.Setter;

/**
 * Template for a Pokemon move. Loaded from JSON via the Pokedex.
 */
@Getter
@Setter
public class Move {
    private String moveId;
    private String name;
    private PokemonType type;
    private MoveCategory category;
    private int power;
    private int accuracy;
    private int pp;
    private String effect;
    private int effectChance;
    private int priority;

    public Move() {}

    public Move(String moveId, String name, PokemonType type, MoveCategory category,
                int power, int accuracy, int pp, String effect, int effectChance, int priority) {
        this.moveId = moveId;
        this.name = name;
        this.type = type;
        this.category = category;
        this.power = power;
        this.accuracy = accuracy;
        this.pp = pp;
        this.effect = effect;
        this.effectChance = effectChance;
        this.priority = priority;
    }
}
