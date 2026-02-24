package com.pocket.rpg.items;

import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.items.ItemUseService.ItemUseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemUseServiceTest {

    private Pokedex pokedex;
    private PokemonInstance pokemon;

    @BeforeEach
    void setUp() {
        pokedex = new Pokedex();
        pokedex.addSpecies(new PokemonSpecies(
                "pikachu", "Pikachu", PokemonType.ELECTRIC,
                new Stats(35, 55, 40, 50, 50, 90),
                java.util.List.of(new LearnedMove(1, "thundershock")),
                112, 190, GrowthRate.MEDIUM_FAST, null,
                EvolutionMethod.ITEM, 0, "thunder_stone", "raichu"
        ));
        pokedex.addMove(new Move("thundershock", "Thundershock", PokemonType.ELECTRIC,
                MoveCategory.SPECIAL, 40, 100, 30, "", 0, 0));

        pokemon = PokemonFactory.createWild(pokedex, "pikachu", 25);
    }

    private ItemDefinition makeItem(ItemEffect effect, int effectValue) {
        return ItemDefinition.builder("test_item", "Test", ItemCategory.MEDICINE)
                .usableInBattle(true).usableOutside(true).consumable(true)
                .effect(effect).effectValue(effectValue).build();
    }

    private ItemDefinition makeStatusItem(String targetStatus) {
        return ItemDefinition.builder("test_status", "Test", ItemCategory.MEDICINE)
                .usableInBattle(true).usableOutside(true).consumable(true)
                .effect(ItemEffect.HEAL_STATUS).targetStatus(targetStatus).build();
    }

    @Nested
    @DisplayName("null safety")
    class NullSafety {
        @Test
        @DisplayName("useItem with null item returns INVALID_TARGET")
        void nullItem() {
            assertEquals(ItemUseResult.INVALID_TARGET, ItemUseService.useItem(null, pokemon));
        }

        @Test
        @DisplayName("useItem with null target returns INVALID_TARGET")
        void nullTarget() {
            assertEquals(ItemUseResult.INVALID_TARGET,
                    ItemUseService.useItem(makeItem(ItemEffect.HEAL_HP, 20), null));
        }

        @Test
        @DisplayName("canUse with null item returns false")
        void canUseNullItem() {
            assertFalse(ItemUseService.canUse(null, pokemon));
        }

        @Test
        @DisplayName("canUse with null target returns false")
        void canUseNullTarget() {
            assertFalse(ItemUseService.canUse(makeItem(ItemEffect.HEAL_HP, 20), null));
        }
    }

    @Nested
    @DisplayName("HEAL_HP")
    class HealHp {
        @Test
        @DisplayName("heals damaged pokemon")
        void healsDamaged() {
            pokemon.damage(20);
            int hpBefore = pokemon.getCurrentHp();
            ItemUseResult result = ItemUseService.useItem(makeItem(ItemEffect.HEAL_HP, 15), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(hpBefore + 15, pokemon.getCurrentHp());
        }

        @Test
        @DisplayName("healing does not exceed max HP")
        void doesNotOverheal() {
            pokemon.damage(5);
            ItemUseService.useItem(makeItem(ItemEffect.HEAL_HP, 999), pokemon);
            assertEquals(pokemon.calcMaxHp(), pokemon.getCurrentHp());
        }

        @Test
        @DisplayName("no effect on full HP")
        void noEffectFullHp() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.HEAL_HP, 20), pokemon));
        }

        @Test
        @DisplayName("invalid on fainted pokemon")
        void invalidOnFainted() {
            pokemon.damage(9999);
            assertFalse(pokemon.isAlive());
            assertEquals(ItemUseResult.INVALID_TARGET,
                    ItemUseService.useItem(makeItem(ItemEffect.HEAL_HP, 20), pokemon));
        }
    }

    @Nested
    @DisplayName("HEAL_FULL")
    class HealFull {
        @Test
        @DisplayName("restores to max HP")
        void restoresToMax() {
            pokemon.damage(30);
            ItemUseResult result = ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL, 0), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(pokemon.calcMaxHp(), pokemon.getCurrentHp());
        }

        @Test
        @DisplayName("no effect at full HP")
        void noEffectFull() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL, 0), pokemon));
        }

        @Test
        @DisplayName("invalid on fainted pokemon")
        void invalidOnFainted() {
            pokemon.damage(9999);
            assertFalse(pokemon.isAlive());
            assertEquals(ItemUseResult.INVALID_TARGET,
                    ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL, 0), pokemon));
        }
    }

    @Nested
    @DisplayName("HEAL_STATUS")
    class HealStatus {
        @Test
        @DisplayName("cure all statuses with null targetStatus")
        void cureAll() {
            pokemon.setStatusCondition(StatusCondition.BURN);
            ItemUseResult result = ItemUseService.useItem(makeStatusItem(null), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(StatusCondition.NONE, pokemon.getStatusCondition());
        }

        @Test
        @DisplayName("cure all statuses with empty targetStatus")
        void cureAllEmpty() {
            pokemon.setStatusCondition(StatusCondition.POISON);
            ItemUseResult result = ItemUseService.useItem(makeStatusItem(""), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(StatusCondition.NONE, pokemon.getStatusCondition());
        }

        @Test
        @DisplayName("cure specific status with matching targetStatus")
        void cureSpecific() {
            pokemon.setStatusCondition(StatusCondition.POISON);
            ItemUseResult result = ItemUseService.useItem(makeStatusItem("POISON"), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(StatusCondition.NONE, pokemon.getStatusCondition());
        }

        @Test
        @DisplayName("invalid target for wrong status")
        void wrongStatus() {
            pokemon.setStatusCondition(StatusCondition.BURN);
            ItemUseResult result = ItemUseService.useItem(makeStatusItem("POISON"), pokemon);
            assertEquals(ItemUseResult.INVALID_TARGET, result);
            assertEquals(StatusCondition.BURN, pokemon.getStatusCondition());
        }

        @Test
        @DisplayName("no effect on healthy pokemon")
        void noEffectHealthy() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeStatusItem(null), pokemon));
        }

        @Test
        @DisplayName("invalid on fainted pokemon")
        void invalidOnFainted() {
            pokemon.damage(9999);
            pokemon.setStatusCondition(StatusCondition.POISON);
            assertEquals(ItemUseResult.INVALID_TARGET,
                    ItemUseService.useItem(makeStatusItem(null), pokemon));
        }

        @Test
        @DisplayName("invalid targetStatus string returns NO_EFFECT")
        void invalidTargetStatusString() {
            pokemon.setStatusCondition(StatusCondition.BURN);
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeStatusItem("NONEXISTENT"), pokemon));
        }
    }

    @Nested
    @DisplayName("HEAL_FULL_RESTORE")
    class HealFullRestore {
        @Test
        @DisplayName("heals HP and cures status")
        void healsAndCures() {
            pokemon.damage(20);
            pokemon.setStatusCondition(StatusCondition.PARALYZE);
            ItemUseResult result = ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL_RESTORE, 0), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(pokemon.calcMaxHp(), pokemon.getCurrentHp());
            assertEquals(StatusCondition.NONE, pokemon.getStatusCondition());
        }

        @Test
        @DisplayName("cures status only when HP is full")
        void statusOnlyAtFullHp() {
            pokemon.setStatusCondition(StatusCondition.BURN);
            assertEquals(pokemon.calcMaxHp(), pokemon.getCurrentHp());
            ItemUseResult result = ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL_RESTORE, 0), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(StatusCondition.NONE, pokemon.getStatusCondition());
        }

        @Test
        @DisplayName("heals HP only when no status")
        void hpOnlyNoStatus() {
            pokemon.damage(20);
            assertEquals(StatusCondition.NONE, pokemon.getStatusCondition());
            ItemUseResult result = ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL_RESTORE, 0), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertEquals(pokemon.calcMaxHp(), pokemon.getCurrentHp());
        }

        @Test
        @DisplayName("no effect when both fine")
        void noEffectBothFine() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL_RESTORE, 0), pokemon));
        }

        @Test
        @DisplayName("invalid on fainted pokemon")
        void invalidOnFainted() {
            pokemon.damage(9999);
            assertEquals(ItemUseResult.INVALID_TARGET,
                    ItemUseService.useItem(makeItem(ItemEffect.HEAL_FULL_RESTORE, 0), pokemon));
        }
    }

    @Nested
    @DisplayName("REVIVE")
    class Revive {
        @Test
        @DisplayName("revives fainted pokemon to 50% HP")
        void revivesFainted() {
            int maxHp = pokemon.calcMaxHp();
            pokemon.damage(9999);
            assertFalse(pokemon.isAlive());

            ItemUseResult result = ItemUseService.useItem(makeItem(ItemEffect.REVIVE, 50), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertTrue(pokemon.isAlive());
            assertEquals(maxHp * 50 / 100, pokemon.getCurrentHp());
        }

        @Test
        @DisplayName("revive with 0% restores at least 1 HP")
        void reviveZeroPercentRestoresOne() {
            pokemon.damage(9999);
            assertFalse(pokemon.isAlive());

            ItemUseResult result = ItemUseService.useItem(makeItem(ItemEffect.REVIVE, 0), pokemon);
            assertEquals(ItemUseResult.SUCCESS, result);
            assertTrue(pokemon.isAlive());
            assertEquals(1, pokemon.getCurrentHp());
        }

        @Test
        @DisplayName("no effect on alive pokemon")
        void noEffectAlive() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.REVIVE, 50), pokemon));
        }
    }

    @Nested
    @DisplayName("battle-only effects")
    class BattleOnly {
        @Test
        @DisplayName("CAPTURE returns NO_EFFECT")
        void captureNoEffect() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.CAPTURE, 1), pokemon));
        }

        @Test
        @DisplayName("BOOST_ATK returns NO_EFFECT")
        void boostNoEffect() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.BOOST_ATK, 1), pokemon));
        }
    }

    @Test
    @DisplayName("canUse returns true/false without side effects")
    void canUseDryRun() {
        pokemon.damage(20);
        int hpBefore = pokemon.getCurrentHp();

        assertTrue(ItemUseService.canUse(makeItem(ItemEffect.HEAL_HP, 20), pokemon));
        assertEquals(hpBefore, pokemon.getCurrentHp()); // no change

        assertFalse(ItemUseService.canUse(makeItem(ItemEffect.REVIVE, 50), pokemon)); // alive
    }

    @Nested
    @DisplayName("edge effects")
    class EdgeEffects {
        @Test
        @DisplayName("null effect returns NO_EFFECT")
        void nullEffect() {
            ItemDefinition item = ItemDefinition.builder("test", "Test", ItemCategory.MEDICINE)
                    .usableInBattle(true).usableOutside(true).consumable(true)
                    .build();
            item.setEffect(null);
            assertEquals(ItemUseResult.NO_EFFECT, ItemUseService.useItem(item, pokemon));
        }

        @Test
        @DisplayName("NONE effect returns NO_EFFECT")
        void noneEffect() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.NONE, 0), pokemon));
        }

        @Test
        @DisplayName("TEACH_MOVE returns SUCCESS")
        void teachMove() {
            assertEquals(ItemUseResult.SUCCESS,
                    ItemUseService.useItem(makeItem(ItemEffect.TEACH_MOVE, 0), pokemon));
        }

        @Test
        @DisplayName("EVOLUTION_ITEM returns SUCCESS")
        void evolutionItem() {
            assertEquals(ItemUseResult.SUCCESS,
                    ItemUseService.useItem(makeItem(ItemEffect.EVOLUTION_ITEM, 0), pokemon));
        }

        @Test
        @DisplayName("TOGGLE_BICYCLE returns NO_EFFECT")
        void toggleBicycle() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.TOGGLE_BICYCLE, 0), pokemon));
        }

        @Test
        @DisplayName("REPEL returns NO_EFFECT")
        void repel() {
            assertEquals(ItemUseResult.NO_EFFECT,
                    ItemUseService.useItem(makeItem(ItemEffect.REPEL, 0), pokemon));
        }
    }
}
