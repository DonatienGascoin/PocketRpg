package com.pocket.rpg.pokemon;

import com.pocket.rpg.dialogue.Dialogue;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of an NPC trainer: identity, party, and battle settings.
 * <p>
 * Stored in a {@link TrainerRegistry} (`.trainers.json`), referenced by
 * TrainerComponent via {@code trainerId}.
 */
@Getter @Setter
public class TrainerDefinition {
    private String trainerId;
    private String trainerName = "";
    private String tag = "";
    private String spriteId;
    private List<TrainerPokemonSpec> party = new ArrayList<>();
    private int defeatMoney = 0;
    private Dialogue preDialogue;
    private Dialogue postDialogue;

    public TrainerDefinition() {}

    public TrainerDefinition(String trainerId) {
        this.trainerId = trainerId;
    }

    /**
     * Deep copy for undo snapshots.
     */
    public TrainerDefinition copy() {
        TrainerDefinition c = new TrainerDefinition();
        c.trainerId = trainerId;
        c.trainerName = trainerName;
        c.tag = tag;
        c.spriteId = spriteId;
        c.party = new ArrayList<>();
        if (party != null) {
            for (TrainerPokemonSpec spec : party) {
                c.party.add(spec != null ? spec.copy() : null);
            }
        }
        c.defeatMoney = defeatMoney;
        c.preDialogue = preDialogue;
        c.postDialogue = postDialogue;
        return c;
    }
}
