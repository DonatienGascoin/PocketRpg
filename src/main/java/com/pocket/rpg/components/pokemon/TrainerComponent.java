package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.pokemon.Pokedex;
import com.pocket.rpg.pokemon.PokemonFactory;
import com.pocket.rpg.pokemon.PokemonInstance;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.ISaveable;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NPC trainer with a fixed party of Pokemon.
 * <p>
 * The party is defined as declarative {@link TrainerPokemonSpec} entries for easy
 * scene authoring in the inspector. At runtime, {@link #getParty()} lazily creates
 * {@link PokemonInstance} objects from the specs via {@link PokemonFactory#createTrainer}.
 * <p>
 * Uses {@link ISaveable} to persist only the {@code defeated} flag — party specs are
 * authored in the scene file and never modified at runtime.
 */
@ComponentMeta(category = "Pokemon")
public class TrainerComponent extends Component implements ISaveable {

    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    @Getter
    private String trainerName = "";
    @Getter
    private List<TrainerPokemonSpec> partySpecs = new ArrayList<>();
    @Getter
    private int defeatMoney = 0;
    @Getter
    private String preDialogue = "";
    @Getter
    private String postDialogue = "";

    private boolean defeated = false;

    private transient List<PokemonInstance> party;

    /**
     * Declarative specification of a trainer's Pokemon, for scene authoring.
     */
    public static class TrainerPokemonSpec {
        @Getter
        private String speciesId = "";
        @Getter
        private int level = 5;
        @Getter
        private List<String> moves;
    }

    /**
     * Returns the trainer's party, lazily creating {@link PokemonInstance} objects
     * from {@link #partySpecs} on first call.
     */
    public List<PokemonInstance> getParty() {
        if (party == null) {
            party = new ArrayList<>();
            Pokedex pokedex = getPokedex();
            if (pokedex != null) {
                for (TrainerPokemonSpec spec : partySpecs) {
                    List<String> moveIds = (spec.getMoves() != null && !spec.getMoves().isEmpty())
                            ? spec.getMoves() : null;
                    party.add(PokemonFactory.createTrainer(
                            pokedex, spec.getSpeciesId(), spec.getLevel(), moveIds, trainerName));
                }
            }
        }
        return party;
    }

    /**
     * Returns the first alive trainer Pokemon, or null if all fainted.
     */
    public PokemonInstance getFirstAlive() {
        for (PokemonInstance p : getParty()) {
            if (p.isAlive()) return p;
        }
        return null;
    }

    public boolean isDefeated() {
        return defeated;
    }

    public void markDefeated() {
        defeated = true;
    }

    // --- ISaveable ---

    @Override
    public Map<String, Object> getSaveState() {
        return Map.of("defeated", defeated);
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state != null) {
            defeated = (boolean) state.getOrDefault("defeated", false);
        }
    }

    @Override
    public boolean hasSaveableState() {
        return defeated;
    }

    private Pokedex getPokedex() {
        return Assets.load(POKEDEX_PATH, Pokedex.class);
    }
}
