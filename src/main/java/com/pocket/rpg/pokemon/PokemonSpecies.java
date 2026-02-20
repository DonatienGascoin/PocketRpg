package com.pocket.rpg.pokemon;

import lombok.Getter;

import java.util.List;

/**
 * Template for a Pokemon species. Loaded from JSON via the Pokedex.
 */
@Getter
public class PokemonSpecies {
    private String speciesId;
    private String name;
    private PokemonType type;
    private Stats baseStats;
    private List<LearnedMove> learnset;
    private int baseExpYield;
    private int catchRate;
    private GrowthRate growthRate;
    private String spriteId;
    private EvolutionMethod evolutionMethod;
    private int evolutionLevel;
    private String evolutionItem;
    private String evolvesInto;

    public PokemonSpecies() {}

    public PokemonSpecies(String speciesId, String name, PokemonType type, Stats baseStats,
                          List<LearnedMove> learnset, int baseExpYield, int catchRate,
                          GrowthRate growthRate, String spriteId,
                          EvolutionMethod evolutionMethod, int evolutionLevel,
                          String evolutionItem, String evolvesInto) {
        this.speciesId = speciesId;
        this.name = name;
        this.type = type;
        this.baseStats = baseStats;
        this.learnset = learnset;
        this.baseExpYield = baseExpYield;
        this.catchRate = catchRate;
        this.growthRate = growthRate;
        this.spriteId = spriteId;
        this.evolutionMethod = evolutionMethod;
        this.evolutionLevel = evolutionLevel;
        this.evolutionItem = evolutionItem;
        this.evolvesInto = evolvesInto;
    }
}
