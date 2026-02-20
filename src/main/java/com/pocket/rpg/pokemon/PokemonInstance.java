package com.pocket.rpg.pokemon;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A concrete Pokemon owned by a player or NPC.
 * <p>
 * Stat calculation uses the Gen III simplified formula:
 * <pre>
 *   stat = ((2 * base + iv) * level / 100 + 5) * natureModifier
 *   hp   = (2 * base + iv) * level / 100 + level + 10
 * </pre>
 */
@Getter
public class PokemonInstance {
    private String species;
    @Setter private String nickname;
    private int level;
    private int exp;
    private Nature nature;
    private Stats ivs;
    @Setter private int currentHp;
    private List<MoveSlot> moves;
    private String originalTrainer;
    private String caughtIn;
    @Getter @Setter private StatusCondition statusCondition;
    @Getter @Setter private String heldItem;

    // Cached species reference (set by Pokedex lookup, transient)
    private transient PokemonSpecies speciesData;

    public PokemonInstance() {
        this.moves = new ArrayList<>();
        this.statusCondition = StatusCondition.NONE;
    }

    public PokemonInstance(String species, String nickname, int level, int exp,
                           Nature nature, Stats ivs, List<MoveSlot> moves,
                           String originalTrainer, String caughtIn) {
        this.species = species;
        this.nickname = nickname;
        this.level = level;
        this.exp = exp;
        this.nature = nature;
        this.ivs = ivs;
        this.moves = new ArrayList<>(moves);
        this.originalTrainer = originalTrainer;
        this.caughtIn = caughtIn;
        this.statusCondition = StatusCondition.NONE;
    }

    /**
     * Sets the cached species data. Must be called before stat calculations
     * that depend on base stats (calcStat, calcMaxHp, gainExp, evolve).
     */
    public void setSpeciesData(PokemonSpecies speciesData) {
        this.speciesData = speciesData;
    }

    // ========================================================================
    // DISPLAY
    // ========================================================================

    public String getDisplayName() {
        return nickname != null ? nickname : (speciesData != null ? speciesData.getName() : species);
    }

    // ========================================================================
    // STAT CALCULATION
    // ========================================================================

    /**
     * Calculates a stat value using the Gen III simplified formula.
     * Requires {@link #speciesData} to be set.
     */
    public int calcStat(StatType type) {
        if (speciesData == null) {
            throw new IllegalStateException("speciesData not set — call setSpeciesData() first");
        }
        if (type == StatType.HP) return calcMaxHp();

        int base = speciesData.getBaseStats().get(type);
        int iv = ivs.get(type);
        float natureMod = nature.getModifier(type);

        return (int) (((2 * base + iv) * level / 100 + 5) * natureMod);
    }

    /**
     * Calculates max HP using the HP-specific formula.
     * Requires {@link #speciesData} to be set.
     */
    public int calcMaxHp() {
        if (speciesData == null) {
            throw new IllegalStateException("speciesData not set — call setSpeciesData() first");
        }
        int base = speciesData.getBaseStats().hp();
        int iv = ivs.hp();
        return (2 * base + iv) * level / 100 + level + 10;
    }

    // ========================================================================
    // HP MANAGEMENT
    // ========================================================================

    public boolean isAlive() {
        return currentHp > 0;
    }

    public boolean canFight() {
        return isAlive() && moves.stream().anyMatch(MoveSlot::hasPp);
    }

    public void heal(int amount) {
        int maxHp = calcMaxHp();
        currentHp = Math.min(currentHp + amount, maxHp);
    }

    public void damage(int amount) {
        currentHp = Math.max(currentHp - amount, 0);
    }

    public void healFull() {
        currentHp = calcMaxHp();
        cureStatus();
        for (MoveSlot slot : moves) {
            slot.restoreAllPp();
        }
    }

    // ========================================================================
    // STATUS
    // ========================================================================

    public void cureStatus() {
        statusCondition = StatusCondition.NONE;
    }

    // ========================================================================
    // HELD ITEM
    // ========================================================================

    public String removeHeldItem() {
        String item = heldItem;
        heldItem = null;
        return item;
    }

    // ========================================================================
    // MOVE MANAGEMENT
    // ========================================================================

    public int getMoveCount() {
        return moves.size();
    }

    public LearnMoveResult learnMove(MoveSlot move) {
        if (moves.size() >= 4) return LearnMoveResult.FULL;
        moves.add(move);
        return LearnMoveResult.OK;
    }

    public void replaceMove(int index, MoveSlot move) {
        moves.set(index, move);
    }

    // ========================================================================
    // EXPERIENCE & LEVELING
    // ========================================================================

    /**
     * Adds experience and handles leveling up.
     * Requires {@link #speciesData} to be set for growth rate and learnset lookup.
     *
     * @param amount exp to gain
     * @return result describing what happened (levels gained, moves learned, evolution)
     */
    public LevelUpResult gainExp(int amount) {
        if (speciesData == null) {
            throw new IllegalStateException("speciesData not set — call setSpeciesData() first");
        }

        int oldLevel = level;
        exp += amount;

        GrowthRate rate = speciesData.getGrowthRate();
        List<String> newMoves = new ArrayList<>();

        // Level up as many times as possible (cap at 100)
        while (level < 100 && exp >= rate.expForLevel(level + 1)) {
            level++;

            // Check learnset for moves at the new level
            for (LearnedMove lm : speciesData.getLearnset()) {
                if (lm.getLevel() == level) {
                    newMoves.add(lm.getMoveId());
                }
            }
        }

        // Cap exp at level 100's requirement
        if (level >= 100) {
            exp = Math.min(exp, rate.expForLevel(100));
        }

        // Check evolution
        boolean canEvolve = false;
        String evolvesInto = null;
        if (level > oldLevel && speciesData.getEvolutionMethod() == EvolutionMethod.LEVEL
                && level >= speciesData.getEvolutionLevel()
                && speciesData.getEvolvesInto() != null) {
            canEvolve = true;
            evolvesInto = speciesData.getEvolvesInto();
        }

        return new LevelUpResult(
                level > oldLevel,
                oldLevel,
                level,
                newMoves,
                canEvolve,
                evolvesInto
        );
    }

    /**
     * Returns the exp needed to reach the next level from the current exp.
     */
    public int getExpToNextLevel() {
        if (speciesData == null) {
            throw new IllegalStateException("speciesData not set — call setSpeciesData() first");
        }
        if (level >= 100) return 0;
        return speciesData.getGrowthRate().expForLevel(level + 1) - exp;
    }

    // ========================================================================
    // EVOLUTION
    // ========================================================================

    /**
     * Creates an evolved form of this Pokemon.
     * The caller is responsible for replacing this instance with the returned one.
     *
     * @param pokedex the Pokedex to look up the evolution target species
     * @return a new PokemonInstance of the evolved species
     */
    public PokemonInstance evolve(Pokedex pokedex) {
        if (speciesData == null || speciesData.getEvolvesInto() == null) {
            throw new IllegalStateException("This Pokemon cannot evolve");
        }

        PokemonSpecies targetSpecies = pokedex.getSpecies(speciesData.getEvolvesInto());
        if (targetSpecies == null) {
            throw new IllegalStateException("Evolution target not found: " + speciesData.getEvolvesInto());
        }

        PokemonInstance evolved = new PokemonInstance(
                targetSpecies.getSpeciesId(),
                nickname,
                level,
                exp,
                nature,
                ivs,
                moves,
                originalTrainer,
                caughtIn
        );
        evolved.setSpeciesData(targetSpecies);
        evolved.setHeldItem(heldItem);

        // Adjust HP proportionally to new max
        int oldMaxHp = calcMaxHp();
        int newMaxHp = evolved.calcMaxHp();
        if (oldMaxHp > 0) {
            evolved.setCurrentHp(currentHp * newMaxHp / oldMaxHp);
        } else {
            evolved.setCurrentHp(newMaxHp);
        }
        // Ensure at least 1 HP if was alive
        if (isAlive() && evolved.getCurrentHp() <= 0) {
            evolved.setCurrentHp(1);
        }

        evolved.cureStatus();
        return evolved;
    }

    // ========================================================================
    // SERIALIZATION
    // ========================================================================

    public Map<String, Object> toSaveData() {
        Map<String, Object> data = new HashMap<>();
        data.put("species", species);
        data.put("nickname", nickname);
        data.put("level", level);
        data.put("exp", exp);
        data.put("nature", nature.name());
        data.put("ivs", Map.of(
                "hp", ivs.hp(), "atk", ivs.atk(), "def", ivs.def(),
                "spAtk", ivs.spAtk(), "spDef", ivs.spDef(), "spd", ivs.spd()
        ));
        data.put("currentHp", currentHp);
        data.put("statusCondition", statusCondition.name());
        data.put("heldItem", heldItem);
        data.put("moves", moves.stream().map(MoveSlot::toSaveData).toList());
        data.put("originalTrainer", originalTrainer);
        data.put("caughtIn", caughtIn);
        return data;
    }

    @SuppressWarnings("unchecked")
    public static PokemonInstance fromSaveData(Map<String, Object> data) {
        PokemonInstance p = new PokemonInstance();
        p.species = (String) data.get("species");
        p.nickname = (String) data.get("nickname");
        p.level = ((Number) data.get("level")).intValue();
        p.exp = ((Number) data.get("exp")).intValue();
        p.nature = Nature.valueOf((String) data.get("nature"));

        Map<String, Object> ivMap = (Map<String, Object>) data.get("ivs");
        p.ivs = new Stats(
                ((Number) ivMap.get("hp")).intValue(),
                ((Number) ivMap.get("atk")).intValue(),
                ((Number) ivMap.get("def")).intValue(),
                ((Number) ivMap.get("spAtk")).intValue(),
                ((Number) ivMap.get("spDef")).intValue(),
                ((Number) ivMap.get("spd")).intValue()
        );

        p.currentHp = ((Number) data.get("currentHp")).intValue();
        p.statusCondition = StatusCondition.valueOf((String) data.get("statusCondition"));
        p.heldItem = (String) data.get("heldItem");

        List<Map<String, Object>> movesData = (List<Map<String, Object>>) data.get("moves");
        p.moves = new ArrayList<>();
        if (movesData != null) {
            for (Map<String, Object> moveData : movesData) {
                p.moves.add(MoveSlot.fromSaveData(moveData));
            }
        }

        p.originalTrainer = (String) data.get("originalTrainer");
        p.caughtIn = (String) data.get("caughtIn");
        return p;
    }
}
