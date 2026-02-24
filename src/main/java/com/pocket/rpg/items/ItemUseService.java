package com.pocket.rpg.items;

import com.pocket.rpg.pokemon.PokemonInstance;
import com.pocket.rpg.pokemon.StatusCondition;

/**
 * Executes item effects on a target Pokemon.
 *
 * <p>Called by the bag UI, battle system, or any system that needs to "use" an item.
 * After {@link #useItem} returns {@link ItemUseResult#SUCCESS}, the caller is responsible
 * for removing the item from inventory if {@link ItemDefinition#isConsumable()} is true.
 *
 * <p>Battle-only effects (CAPTURE, BOOST_*, REPEL) are not handled here — they are
 * managed by the battle system directly.
 */
public final class ItemUseService {

    private ItemUseService() {}

    public enum ItemUseResult {
        SUCCESS,
        NO_EFFECT,
        INVALID_TARGET
    }

    /**
     * Check whether an item can be used on a target Pokemon.
     */
    public static boolean canUse(ItemDefinition item, PokemonInstance target) {
        if (item == null || target == null) return false;
        return useItemInternal(item, target, true) == ItemUseResult.SUCCESS;
    }

    /**
     * Use an item on a target Pokemon.
     *
     * @return the result of the use attempt
     */
    public static ItemUseResult useItem(ItemDefinition item, PokemonInstance target) {
        if (item == null || target == null) return ItemUseResult.INVALID_TARGET;
        return useItemInternal(item, target, false);
    }

    private static ItemUseResult useItemInternal(ItemDefinition item, PokemonInstance target, boolean dryRun) {
        if (item.getEffect() == null) return ItemUseResult.NO_EFFECT;

        return switch (item.getEffect()) {
            case HEAL_HP -> healHp(target, item.getEffectValue(), dryRun);
            case HEAL_FULL -> healFull(target, dryRun);
            case HEAL_STATUS -> healStatus(target, item.getTargetStatus(), dryRun);
            case HEAL_FULL_RESTORE -> healFullRestore(target, dryRun);
            case REVIVE -> revive(target, item.getEffectValue(), dryRun);
            case TEACH_MOVE, EVOLUTION_ITEM -> ItemUseResult.SUCCESS;
            case CAPTURE, BOOST_ATK, BOOST_DEF, BOOST_SP_ATK, BOOST_SP_DEF,
                 BOOST_SPD, BOOST_ACCURACY, BOOST_CRIT, REPEL,
                 TOGGLE_BICYCLE, NONE -> ItemUseResult.NO_EFFECT;
        };
    }

    private static ItemUseResult healHp(PokemonInstance target, int amount, boolean dryRun) {
        if (!target.isAlive()) return ItemUseResult.INVALID_TARGET;
        if (target.getCurrentHp() >= target.calcMaxHp()) return ItemUseResult.NO_EFFECT;
        if (!dryRun) target.heal(amount);
        return ItemUseResult.SUCCESS;
    }

    private static ItemUseResult healFull(PokemonInstance target, boolean dryRun) {
        if (!target.isAlive()) return ItemUseResult.INVALID_TARGET;
        if (target.getCurrentHp() >= target.calcMaxHp()) return ItemUseResult.NO_EFFECT;
        if (!dryRun) target.heal(target.calcMaxHp());
        return ItemUseResult.SUCCESS;
    }

    private static ItemUseResult healStatus(PokemonInstance target, String targetStatus, boolean dryRun) {
        if (!target.isAlive()) return ItemUseResult.INVALID_TARGET;
        StatusCondition current = target.getStatusCondition();
        if (current == StatusCondition.NONE) return ItemUseResult.NO_EFFECT;

        // If targetStatus is specified, only cure that specific status
        if (targetStatus != null && !targetStatus.isEmpty()) {
            try {
                StatusCondition required = StatusCondition.valueOf(targetStatus);
                if (current != required) return ItemUseResult.INVALID_TARGET;
            } catch (IllegalArgumentException e) {
                return ItemUseResult.NO_EFFECT;
            }
        }

        if (!dryRun) target.cureStatus();
        return ItemUseResult.SUCCESS;
    }

    private static ItemUseResult healFullRestore(PokemonInstance target, boolean dryRun) {
        if (!target.isAlive()) return ItemUseResult.INVALID_TARGET;
        boolean needsHealing = target.getCurrentHp() < target.calcMaxHp();
        boolean needsStatusCure = target.getStatusCondition() != StatusCondition.NONE;
        if (!needsHealing && !needsStatusCure) return ItemUseResult.NO_EFFECT;
        if (!dryRun) {
            target.heal(target.calcMaxHp());
            target.cureStatus();
        }
        return ItemUseResult.SUCCESS;
    }

    private static ItemUseResult revive(PokemonInstance target, int hpPercent, boolean dryRun) {
        if (target.isAlive()) return ItemUseResult.NO_EFFECT;
        if (!dryRun) {
            int restoreHp = target.calcMaxHp() * hpPercent / 100;
            target.heal(Math.max(restoreHp, 1));
        }
        return ItemUseResult.SUCCESS;
    }
}
