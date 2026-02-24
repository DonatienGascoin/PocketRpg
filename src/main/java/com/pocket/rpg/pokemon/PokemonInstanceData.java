package com.pocket.rpg.pokemon;

import java.util.ArrayList;
import java.util.List;

/**
 * Gson-friendly serialization form of {@link PokemonInstance}.
 * <p>
 * Used by {@link com.pocket.rpg.save.PlayerData} to persist the player's party and PC storage.
 * All fields are public for direct Gson access. Enums serialize by name automatically.
 */
public class PokemonInstanceData {
    public String species;
    public String nickname;
    public int level;
    public int exp;
    public Nature nature;
    public Stats ivs;
    public int currentHp;
    public StatusCondition statusCondition;
    public String heldItem;
    public List<MoveSlotData> moves;
    public String originalTrainer;
    public String caughtIn;

    /**
     * Gson-friendly serialization of {@link MoveSlot}.
     */
    public static class MoveSlotData {
        public String moveId;
        public int maxPp;
        public int currentPp;

        public MoveSlotData() {}

        public MoveSlotData(String moveId, int maxPp, int currentPp) {
            this.moveId = moveId;
            this.maxPp = maxPp;
            this.currentPp = currentPp;
        }
    }

    public PokemonInstanceData() {}

    /**
     * Converts a runtime {@link PokemonInstance} to this serialization form.
     */
    public static PokemonInstanceData fromPokemonInstance(PokemonInstance p) {
        PokemonInstanceData data = new PokemonInstanceData();
        data.species = p.getSpecies();
        data.nickname = p.getNickname();
        data.level = p.getLevel();
        data.exp = p.getExp();
        data.nature = p.getNature();
        data.ivs = p.getIvs();
        data.currentHp = p.getCurrentHp();
        data.statusCondition = p.getStatusCondition();
        data.heldItem = p.getHeldItem();
        data.originalTrainer = p.getOriginalTrainer();
        data.caughtIn = p.getCaughtIn();

        data.moves = new ArrayList<>();
        for (MoveSlot slot : p.getMoves()) {
            data.moves.add(new MoveSlotData(slot.getMoveId(), slot.getMaxPp(), slot.getCurrentPp()));
        }

        return data;
    }

    /**
     * Converts this serialization form back to a runtime {@link PokemonInstance}.
     * <p>
     * Note: the returned instance does NOT have {@code speciesData} set.
     * The caller must call {@code setSpeciesData()} via a {@link Pokedex} lookup
     * before using stat calculations.
     */
    public PokemonInstance toPokemonInstance() {
        List<MoveSlot> moveSlots = new ArrayList<>();
        if (moves != null) {
            for (MoveSlotData md : moves) {
                MoveSlot slot = new MoveSlot(md.moveId, md.maxPp);
                // Restore current PP (constructor sets currentPp = maxPp, so adjust)
                int ppDiff = md.maxPp - md.currentPp;
                for (int i = 0; i < ppDiff; i++) {
                    slot.usePp();
                }
                moveSlots.add(slot);
            }
        }

        PokemonInstance p = new PokemonInstance(
                species,
                nickname,
                level,
                exp,
                nature != null ? nature : Nature.HARDY,
                ivs != null ? ivs : new Stats(0, 0, 0, 0, 0, 0),
                moveSlots,
                originalTrainer,
                caughtIn
        );
        p.setCurrentHp(currentHp);
        p.setStatusCondition(statusCondition != null ? statusCondition : StatusCondition.NONE);
        p.setHeldItem(heldItem);
        return p;
    }
}
