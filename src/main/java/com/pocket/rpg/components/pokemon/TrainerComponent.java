package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.dialogue.Dialogue;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.save.ISaveable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NPC trainer — references a {@link TrainerDefinition} in the {@link TrainerRegistry}
 * via {@link #trainerId}.
 * <p>
 * At runtime, {@link #getParty()} lazily creates {@link PokemonInstance} objects
 * from the definition's party specs via {@link PokemonFactory#createTrainer}.
 * <p>
 * Uses {@link ISaveable} to persist only the {@code defeated} flag.
 */
@ComponentMeta(category = "Pokemon")
public class TrainerComponent extends Component implements ISaveable {

    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";
    private static final String REGISTRY_PATH = "data/pokemon/trainers.trainers.json";

    @Getter @Setter
    private String trainerId = "";

    private boolean defeated = false;

    private transient TrainerDefinition cachedDefinition;
    private transient List<PokemonInstance> party;

    /**
     * Resolves the {@link TrainerDefinition} from the registry.
     */
    public TrainerDefinition getDefinition() {
        if (cachedDefinition == null && trainerId != null && !trainerId.isEmpty()) {
            TrainerRegistry registry = Assets.load(REGISTRY_PATH, TrainerRegistry.class);
            if (registry != null) {
                cachedDefinition = registry.getTrainer(trainerId);
            }
        }
        return cachedDefinition;
    }

    public String getTrainerName() {
        TrainerDefinition def = getDefinition();
        return def != null ? def.getTrainerName() : "";
    }

    public int getDefeatMoney() {
        TrainerDefinition def = getDefinition();
        return def != null ? def.getDefeatMoney() : 0;
    }

    public Dialogue getPreDialogue() {
        TrainerDefinition def = getDefinition();
        return def != null ? def.getPreDialogue() : null;
    }

    public Dialogue getPostDialogue() {
        TrainerDefinition def = getDefinition();
        return def != null ? def.getPostDialogue() : null;
    }

    /**
     * Returns the trainer's party, lazily creating {@link PokemonInstance} objects.
     */
    public List<PokemonInstance> getParty() {
        if (party == null) {
            party = new ArrayList<>();
            TrainerDefinition def = getDefinition();
            if (def != null) {
                Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
                if (pokedex != null) {
                    for (TrainerPokemonSpec spec : def.getParty()) {
                        if (spec == null || spec.getSpeciesId() == null || spec.getSpeciesId().isEmpty()) {
                            continue;
                        }
                        List<String> moveIds = (spec.getMoves() != null && !spec.getMoves().isEmpty())
                                ? spec.getMoves() : null;
                        party.add(PokemonFactory.createTrainer(
                                pokedex, spec.getSpeciesId(), spec.getLevel(), moveIds, def.getTrainerName()));
                    }
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

    /**
     * Returns the dialogue static variables map for this trainer.
     * Passes TRAINER_NAME so dialogues can use [TRAINER_NAME] substitution.
     */
    public Map<String, String> getDialogueVariables() {
        return Map.of("TRAINER_NAME", getTrainerName());
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
}
