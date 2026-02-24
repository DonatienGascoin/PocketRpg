package com.pocket.rpg.items;

/**
 * Effects that items can have when used.
 *
 * <p>Battle-only effects (CAPTURE, BOOST_*, REPEL) are handled by the battle system,
 * not by {@link ItemUseService}. Overworld-usable effects (HEAL_*, REVIVE, TEACH_MOVE,
 * EVOLUTION_ITEM) are executed by {@link ItemUseService}.
 *
 * <p>For {@code HEAL_STATUS}, the {@code targetStatus} field on {@link ItemDefinition}
 * specifies which status to cure as a string name (e.g. {@code "POISON"}, {@code "BURN"}).
 * Null or empty means cure all statuses. The {@code effectValue} field is no longer used
 * for status encoding.
 */
public enum ItemEffect {
    NONE,
    HEAL_HP,
    HEAL_FULL,
    HEAL_STATUS,
    HEAL_FULL_RESTORE,
    REVIVE,
    BOOST_ATK,
    BOOST_DEF,
    BOOST_SP_ATK,
    BOOST_SP_DEF,
    BOOST_SPD,
    BOOST_ACCURACY,
    BOOST_CRIT,
    CAPTURE,
    TEACH_MOVE,
    EVOLUTION_ITEM,
    TOGGLE_BICYCLE,
    REPEL
}
