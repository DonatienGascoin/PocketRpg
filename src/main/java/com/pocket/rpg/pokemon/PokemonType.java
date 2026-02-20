package com.pocket.rpg.pokemon;

/**
 * The 18 Pokemon element types with a complete type effectiveness chart.
 * <p>
 * Effectiveness values:
 * <ul>
 *   <li>2.0 — super effective</li>
 *   <li>1.0 — normal</li>
 *   <li>0.5 — not very effective</li>
 *   <li>0.0 — no effect (immune)</li>
 * </ul>
 */
public enum PokemonType {
    NORMAL,
    FIRE,
    WATER,
    GRASS,
    ELECTRIC,
    ICE,
    FIGHTING,
    POISON,
    GROUND,
    FLYING,
    PSYCHIC,
    BUG,
    ROCK,
    GHOST,
    DRAGON,
    DARK,
    STEEL,
    FAIRY;

    // Indexed by [attacker.ordinal()][defender.ordinal()]
    // Row = attacking type, Column = defending type
    // Order: NOR FIR WAT GRA ELE ICE FIG POI GRO FLY PSY BUG ROC GHO DRA DAR STE FAI
    private static final float[][] CHART = {
        // NORMAL attacking
        { 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, .5f, 0f, 1f, 1f, .5f, 1f },
        // FIRE attacking
        { 1f, .5f, .5f, 2f, 1f, 2f, 1f, 1f, 1f, 1f, 1f, 2f, .5f, 1f, .5f, 1f, 2f, 1f },
        // WATER attacking
        { 1f, 2f, .5f, .5f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 1f, 2f, 1f, .5f, 1f, 1f, 1f },
        // GRASS attacking
        { 1f, .5f, 2f, .5f, 1f, 1f, 1f, .5f, 2f, .5f, 1f, .5f, 2f, 1f, .5f, 1f, .5f, 1f },
        // ELECTRIC attacking
        { 1f, 1f, 2f, .5f, .5f, 1f, 1f, 1f, 0f, 2f, 1f, 1f, 1f, 1f, .5f, 1f, 1f, 1f },
        // ICE attacking
        { 1f, .5f, .5f, 2f, 1f, .5f, 1f, 1f, 2f, 2f, 1f, 1f, 1f, 1f, 2f, 1f, .5f, 1f },
        // FIGHTING attacking
        { 2f, 1f, 1f, 1f, 1f, 2f, 1f, .5f, 1f, .5f, .5f, .5f, 2f, 0f, 1f, 2f, 2f, .5f },
        // POISON attacking
        { 1f, 1f, 1f, 2f, 1f, 1f, 1f, .5f, .5f, 1f, 1f, 1f, .5f, .5f, 1f, 1f, 0f, 2f },
        // GROUND attacking
        { 1f, 2f, 1f, .5f, 2f, 1f, 1f, 2f, 1f, 0f, 1f, .5f, 2f, 1f, 1f, 1f, 2f, 1f },
        // FLYING attacking
        { 1f, 1f, 1f, 2f, .5f, 1f, 2f, 1f, 1f, 1f, 1f, 2f, .5f, 1f, 1f, 1f, .5f, 1f },
        // PSYCHIC attacking
        { 1f, 1f, 1f, 1f, 1f, 1f, 2f, 2f, 1f, 1f, .5f, 1f, 1f, 1f, 1f, 0f, .5f, 1f },
        // BUG attacking
        { 1f, .5f, 1f, 2f, 1f, 1f, .5f, .5f, 1f, .5f, 2f, 1f, 1f, .5f, 1f, 2f, .5f, .5f },
        // ROCK attacking
        { 1f, 2f, 1f, 1f, 1f, 2f, .5f, 1f, .5f, 2f, 1f, 2f, 1f, 1f, 1f, 1f, .5f, 1f },
        // GHOST attacking
        { 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 2f, 1f, .5f, 1f, 1f },
        // DRAGON attacking
        { 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, .5f, 0f },
        // DARK attacking
        { 1f, 1f, 1f, 1f, 1f, 1f, .5f, 1f, 1f, 1f, 2f, 1f, 1f, 2f, 1f, .5f, .5f, .5f },
        // STEEL attacking
        { 1f, .5f, .5f, 1f, .5f, 2f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 1f, 1f, 1f, .5f, 2f },
        // FAIRY attacking
        { 1f, .5f, 1f, 1f, 1f, 1f, 2f, .5f, 1f, 1f, 1f, 1f, 1f, 1f, 2f, 2f, .5f, 1f },
    };

    /**
     * Returns the type effectiveness multiplier when this type attacks the defender type.
     *
     * @param defender the defending Pokemon's type
     * @return 0.0 (immune), 0.5 (not effective), 1.0 (normal), or 2.0 (super effective)
     */
    public float getEffectiveness(PokemonType defender) {
        return CHART[this.ordinal()][defender.ordinal()];
    }

    /**
     * Static convenience for {@link #getEffectiveness(PokemonType)}.
     */
    public static float getEffectiveness(PokemonType attacker, PokemonType defender) {
        return attacker.getEffectiveness(defender);
    }
}
