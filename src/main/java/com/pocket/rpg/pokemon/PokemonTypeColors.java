package com.pocket.rpg.pokemon;

/**
 * Standard Pokemon type colors for editor UI.
 * <p>
 * Each color is an RGBA float[4] suitable for ImGui's color methods
 * ({@code textColored}, {@code pushStyleColor}, {@code colorButton}, etc.).
 * <p>
 * Colors are based on the standard Pokemon type palette, slightly
 * desaturated for readability on a dark editor background.
 */
public final class PokemonTypeColors {

    private PokemonTypeColors() {}

    // @formatter:off
    private static final float[][] COLORS = new float[PokemonType.values().length][];

    static {
        COLORS[PokemonType.NORMAL.ordinal()]   = rgba(0.66f, 0.66f, 0.47f);
        COLORS[PokemonType.FIRE.ordinal()]     = rgba(0.94f, 0.50f, 0.19f);
        COLORS[PokemonType.WATER.ordinal()]    = rgba(0.41f, 0.56f, 0.94f);
        COLORS[PokemonType.GRASS.ordinal()]    = rgba(0.47f, 0.78f, 0.31f);
        COLORS[PokemonType.ELECTRIC.ordinal()] = rgba(0.97f, 0.82f, 0.19f);
        COLORS[PokemonType.ICE.ordinal()]      = rgba(0.60f, 0.85f, 0.85f);
        COLORS[PokemonType.FIGHTING.ordinal()] = rgba(0.75f, 0.19f, 0.16f);
        COLORS[PokemonType.POISON.ordinal()]   = rgba(0.63f, 0.25f, 0.63f);
        COLORS[PokemonType.GROUND.ordinal()]   = rgba(0.88f, 0.75f, 0.41f);
        COLORS[PokemonType.FLYING.ordinal()]   = rgba(0.66f, 0.56f, 0.94f);
        COLORS[PokemonType.PSYCHIC.ordinal()]  = rgba(0.97f, 0.35f, 0.53f);
        COLORS[PokemonType.BUG.ordinal()]      = rgba(0.66f, 0.72f, 0.13f);
        COLORS[PokemonType.ROCK.ordinal()]     = rgba(0.72f, 0.63f, 0.22f);
        COLORS[PokemonType.GHOST.ordinal()]    = rgba(0.44f, 0.35f, 0.60f);
        COLORS[PokemonType.DRAGON.ordinal()]   = rgba(0.44f, 0.22f, 0.97f);
        COLORS[PokemonType.DARK.ordinal()]     = rgba(0.44f, 0.35f, 0.28f);
        COLORS[PokemonType.STEEL.ordinal()]    = rgba(0.72f, 0.72f, 0.82f);
        COLORS[PokemonType.FAIRY.ordinal()]    = rgba(0.93f, 0.60f, 0.67f);
    }
    // @formatter:on

    /**
     * Returns the RGBA float[4] color for the given type.
     */
    public static float[] get(PokemonType type) {
        if (type == null) return rgba(0.5f, 0.5f, 0.5f);
        return COLORS[type.ordinal()];
    }

    private static float[] rgba(float r, float g, float b) {
        return new float[]{r, g, b, 1.0f};
    }
}
