package com.pocket.rpg.pokemon;

/**
 * The 25 Pokemon natures. Each nature boosts one stat by 10% and
 * reduces another by 10%. Five natures are neutral (same stat boosted
 * and hindered, so all modifiers are 1.0).
 */
public enum Nature {
    // Neutral natures
    HARDY  (StatType.ATK,    StatType.ATK),
    DOCILE (StatType.DEF,    StatType.DEF),
    SERIOUS(StatType.SPD,    StatType.SPD),
    BASHFUL(StatType.SP_ATK, StatType.SP_ATK),
    QUIRKY (StatType.SP_DEF, StatType.SP_DEF),

    // +Atk
    LONELY (StatType.ATK, StatType.DEF),
    ADAMANT(StatType.ATK, StatType.SP_ATK),
    NAUGHTY(StatType.ATK, StatType.SP_DEF),
    BRAVE  (StatType.ATK, StatType.SPD),

    // +Def
    BOLD   (StatType.DEF, StatType.ATK),
    IMPISH (StatType.DEF, StatType.SP_ATK),
    LAX    (StatType.DEF, StatType.SP_DEF),
    RELAXED(StatType.DEF, StatType.SPD),

    // +SpAtk
    MODEST (StatType.SP_ATK, StatType.ATK),
    MILD   (StatType.SP_ATK, StatType.DEF),
    RASH   (StatType.SP_ATK, StatType.SP_DEF),
    QUIET  (StatType.SP_ATK, StatType.SPD),

    // +SpDef
    CALM   (StatType.SP_DEF, StatType.ATK),
    GENTLE (StatType.SP_DEF, StatType.DEF),
    CAREFUL(StatType.SP_DEF, StatType.SP_ATK),
    SASSY  (StatType.SP_DEF, StatType.SPD),

    // +Spd
    TIMID  (StatType.SPD, StatType.ATK),
    HASTY  (StatType.SPD, StatType.DEF),
    JOLLY  (StatType.SPD, StatType.SP_ATK),
    NAIVE  (StatType.SPD, StatType.SP_DEF);

    private final StatType boosted;
    private final StatType hindered;

    Nature(StatType boosted, StatType hindered) {
        this.boosted = boosted;
        this.hindered = hindered;
    }

    public StatType getBoostedStat() {
        return boosted;
    }

    public StatType getHinderedStat() {
        return hindered;
    }

    public boolean isNeutral() {
        return boosted == hindered;
    }

    /**
     * Returns the nature modifier for the given stat.
     *
     * @return 1.1 if boosted, 0.9 if hindered, 1.0 otherwise (or if neutral)
     */
    public float getModifier(StatType stat) {
        if (isNeutral()) return 1.0f;
        if (stat == boosted) return 1.1f;
        if (stat == hindered) return 0.9f;
        return 1.0f;
    }

    public float atkModifier()   { return getModifier(StatType.ATK); }
    public float defModifier()   { return getModifier(StatType.DEF); }
    public float spAtkModifier() { return getModifier(StatType.SP_ATK); }
    public float spDefModifier() { return getModifier(StatType.SP_DEF); }
    public float spdModifier()   { return getModifier(StatType.SPD); }
}
