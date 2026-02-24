package com.pocket.rpg.items;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    private Inventory inventory;
    private ItemRegistry registry;

    @BeforeEach
    void setUp() {
        inventory = new Inventory();
        registry = new ItemRegistry();

        registry.addItem(ItemDefinition.builder("potion", "Potion", ItemCategory.MEDICINE)
                .description("Heals 20 HP").price(200).sellPrice(100)
                .usableInBattle(true).usableOutside(true).consumable(true)
                .effect(ItemEffect.HEAL_HP).effectValue(20).build());
        registry.addItem(ItemDefinition.builder("pokeball", "Poke Ball", ItemCategory.POKEBALL)
                .description("Catches Pokemon").price(200).sellPrice(100)
                .usableInBattle(true).consumable(true)
                .effect(ItemEffect.CAPTURE).effectValue(1).build());
        registry.addItem(ItemDefinition.builder("bicycle", "Bicycle", ItemCategory.KEY_ITEM)
                .description("Fast travel").usableOutside(true).stackLimit(1)
                .effect(ItemEffect.TOGGLE_BICYCLE).build());
        registry.addItem(ItemDefinition.builder("super_potion", "Super Potion", ItemCategory.MEDICINE)
                .description("Heals 60 HP").price(700).sellPrice(350)
                .usableInBattle(true).usableOutside(true).consumable(true)
                .effect(ItemEffect.HEAL_HP).effectValue(60).build());
        registry.addItem(ItemDefinition.builder("x_attack", "X Attack", ItemCategory.BATTLE)
                .description("Boosts ATK").price(500).sellPrice(250)
                .usableInBattle(true).consumable(true)
                .effect(ItemEffect.BOOST_ATK).effectValue(1).build());
    }

    @Nested
    @DisplayName("addItem")
    class AddItem {
        @Test
        @DisplayName("new item creates stack in correct pocket")
        void newItemInCorrectPocket() {
            assertEquals(5, inventory.addItem("potion", 5, registry));
            assertEquals(5, inventory.getCount("potion"));
            assertEquals(1, inventory.getPocket(ItemCategory.MEDICINE).size());
        }

        @Test
        @DisplayName("existing item increments quantity")
        void existingItemIncrements() {
            inventory.addItem("potion", 3, registry);
            inventory.addItem("potion", 2, registry);
            assertEquals(5, inventory.getCount("potion"));
            assertEquals(1, inventory.getPocket(ItemCategory.MEDICINE).size());
        }

        @Test
        @DisplayName("quantity capped at stackLimit — returns actual added")
        void cappedAtStackLimit() {
            inventory.addItem("potion", 95, registry);
            assertEquals(4, inventory.addItem("potion", 10, registry));
            assertEquals(99, inventory.getCount("potion")); // capped at 99
        }

        @Test
        @DisplayName("returns 0 at stack limit")
        void returnsZeroAtStackLimit() {
            inventory.addItem("potion", 99, registry);
            assertEquals(0, inventory.addItem("potion", 1, registry));
        }

        @Test
        @DisplayName("key item limited to stack of 1")
        void keyItemStackOne() {
            assertEquals(1, inventory.addItem("bicycle", 1, registry));
            assertEquals(0, inventory.addItem("bicycle", 1, registry));
            assertEquals(1, inventory.getCount("bicycle"));
        }

        @Test
        @DisplayName("unknown item returns 0")
        void unknownItemReturnsZero() {
            assertEquals(0, inventory.addItem("nonexistent", 1, registry));
        }

        @Test
        @DisplayName("zero quantity returns 0")
        void zeroQuantityReturnsZero() {
            assertEquals(0, inventory.addItem("potion", 0, registry));
            assertEquals(0, inventory.getPocket(ItemCategory.MEDICINE).size());
        }

        @Test
        @DisplayName("negative quantity returns 0")
        void negativeQuantityReturnsZero() {
            assertEquals(0, inventory.addItem("potion", -5, registry));
        }

        @Test
        @DisplayName("pocket capacity enforced")
        void pocketCapacityEnforced() {
            // Fill the MEDICINE pocket with 50 unique items
            for (int i = 0; i < Inventory.POCKET_CAPACITY; i++) {
                registry.addItem(ItemDefinition.builder("med_" + i, "Med " + i, ItemCategory.MEDICINE)
                        .price(100).sellPrice(50).usableInBattle(true).usableOutside(true).consumable(true)
                        .effect(ItemEffect.HEAL_HP).effectValue(10).build());
                assertTrue(inventory.addItem("med_" + i, 1, registry) > 0);
            }
            // 51st unique item should fail
            registry.addItem(ItemDefinition.builder("med_overflow", "Overflow", ItemCategory.MEDICINE)
                    .price(100).sellPrice(50).usableInBattle(true).usableOutside(true).consumable(true)
                    .effect(ItemEffect.HEAL_HP).effectValue(10).build());
            assertEquals(0, inventory.addItem("med_overflow", 1, registry));
        }

        @Test
        @DisplayName("items go to correct category pockets")
        void correctCategoryPockets() {
            inventory.addItem("potion", 1, registry);
            inventory.addItem("pokeball", 1, registry);
            assertEquals(1, inventory.getPocket(ItemCategory.MEDICINE).size());
            assertEquals(1, inventory.getPocket(ItemCategory.POKEBALL).size());
            assertEquals(0, inventory.getPocket(ItemCategory.BATTLE).size());
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItem {
        @Test
        @DisplayName("decrements quantity")
        void decrementsQuantity() {
            inventory.addItem("potion", 5, registry);
            assertTrue(inventory.removeItem("potion", 2));
            assertEquals(3, inventory.getCount("potion"));
        }

        @Test
        @DisplayName("insufficient quantity returns false")
        void insufficientReturnsFalse() {
            inventory.addItem("potion", 2, registry);
            assertFalse(inventory.removeItem("potion", 5));
            assertEquals(2, inventory.getCount("potion"));
        }

        @Test
        @DisplayName("removing last unit removes stack")
        void removesStackWhenEmpty() {
            inventory.addItem("potion", 1, registry);
            assertTrue(inventory.removeItem("potion", 1));
            assertEquals(0, inventory.getCount("potion"));
            assertEquals(0, inventory.getPocket(ItemCategory.MEDICINE).size());
        }

        @Test
        @DisplayName("nonexistent item returns false")
        void nonexistentReturnsFalse() {
            assertFalse(inventory.removeItem("potion", 1));
        }

        @Test
        @DisplayName("zero or negative quantity returns false")
        void zeroOrNegativeReturnsFalse() {
            inventory.addItem("potion", 5, registry);
            assertFalse(inventory.removeItem("potion", 0));
            assertFalse(inventory.removeItem("potion", -1));
            assertEquals(5, inventory.getCount("potion"));
        }
    }

    @Nested
    @DisplayName("hasItem / getCount")
    class HasAndCount {
        @Test
        @DisplayName("hasItem for present item")
        void hasItemPresent() {
            inventory.addItem("potion", 3, registry);
            assertTrue(inventory.hasItem("potion"));
            assertTrue(inventory.hasItem("potion", 3));
            assertFalse(inventory.hasItem("potion", 4));
        }

        @Test
        @DisplayName("hasItem for absent item")
        void hasItemAbsent() {
            assertFalse(inventory.hasItem("potion"));
            assertFalse(inventory.hasItem("potion", 1));
        }

        @Test
        @DisplayName("getCount for absent item returns 0")
        void getCountAbsent() {
            assertEquals(0, inventory.getCount("potion"));
        }
    }

    @Nested
    @DisplayName("registered items")
    class RegisteredItems {
        @Test
        @DisplayName("register and unregister")
        void registerUnregister() {
            inventory.registerItem("bicycle");
            assertEquals(List.of("bicycle"), inventory.getRegisteredItems());

            inventory.unregisterItem("bicycle");
            assertTrue(inventory.getRegisteredItems().isEmpty());
        }

        @Test
        @DisplayName("no duplicates")
        void noDuplicates() {
            inventory.registerItem("bicycle");
            inventory.registerItem("bicycle");
            assertEquals(1, inventory.getRegisteredItems().size());
        }

        @Test
        @DisplayName("unmodifiable view")
        void unmodifiableView() {
            inventory.registerItem("bicycle");
            assertThrows(UnsupportedOperationException.class,
                    () -> inventory.getRegisteredItems().add("test"));
        }
    }

    @Nested
    @DisplayName("sorting")
    class Sorting {
        @Test
        @DisplayName("sort by name")
        void sortByName() {
            inventory.addItem("super_potion", 1, registry);
            inventory.addItem("potion", 1, registry);

            inventory.sortPocket(ItemCategory.MEDICINE, SortMode.BY_NAME, registry);

            List<ItemStack> pocket = inventory.getPocket(ItemCategory.MEDICINE);
            assertEquals("potion", pocket.get(0).getItemId());
            assertEquals("super_potion", pocket.get(1).getItemId());
        }

        @Test
        @DisplayName("sort by ID")
        void sortById() {
            inventory.addItem("super_potion", 1, registry);
            inventory.addItem("potion", 1, registry);

            inventory.sortPocket(ItemCategory.MEDICINE, SortMode.BY_ID, registry);

            List<ItemStack> pocket = inventory.getPocket(ItemCategory.MEDICINE);
            assertEquals("potion", pocket.get(0).getItemId());
            assertEquals("super_potion", pocket.get(1).getItemId());
        }

        @Test
        @DisplayName("sort by category uses secondary sort by itemId")
        void sortByCategory() {
            inventory.addItem("super_potion", 1, registry);
            inventory.addItem("potion", 1, registry);

            inventory.sortPocket(ItemCategory.MEDICINE, SortMode.BY_CATEGORY, registry);

            // Same category, so sorted by itemId as tiebreaker
            List<ItemStack> pocket = inventory.getPocket(ItemCategory.MEDICINE);
            assertEquals("potion", pocket.get(0).getItemId());
            assertEquals("super_potion", pocket.get(1).getItemId());
        }
    }

    @Nested
    @DisplayName("serialization")
    class Serialization {
        @Test
        @DisplayName("round-trip preserves all data including pocket assignment")
        void roundTrip() {
            inventory.addItem("potion", 5, registry);
            inventory.addItem("pokeball", 10, registry);
            inventory.registerItem("bicycle");

            InventoryData data = inventory.toSaveData();
            Inventory restored = Inventory.fromSaveData(data);

            assertEquals(5, restored.getCount("potion"));
            assertEquals(10, restored.getCount("pokeball"));
            assertEquals(List.of("bicycle"), restored.getRegisteredItems());

            // Verify items are in correct pockets
            assertEquals(1, restored.getPocket(ItemCategory.MEDICINE).size());
            assertEquals("potion", restored.getPocket(ItemCategory.MEDICINE).get(0).getItemId());
            assertEquals(1, restored.getPocket(ItemCategory.POKEBALL).size());
            assertEquals("pokeball", restored.getPocket(ItemCategory.POKEBALL).get(0).getItemId());
        }

        @Test
        @DisplayName("fromSaveData with null returns empty inventory")
        void fromNullData() {
            Inventory restored = Inventory.fromSaveData(null);
            assertEquals(0, restored.getAllItems().size());
        }

        @Test
        @DisplayName("unknown category in save data is skipped")
        void unknownCategorySkipped() {
            InventoryData data = new InventoryData();
            data.pockets = Map.of("NONEXISTENT", List.of());
            data.registeredItems = List.of();
            Inventory restored = Inventory.fromSaveData(data);
            assertNotNull(restored);
        }

        @Test
        @DisplayName("fromSaveData skips null stacks gracefully")
        void skipsNullStacks() {
            InventoryData data = new InventoryData();
            data.pockets = Map.of("MEDICINE", List.of(
                    new InventoryData.StackEntry("potion", 5)
            ));
            data.registeredItems = List.of();
            Inventory restored = Inventory.fromSaveData(data);
            assertEquals(5, restored.getCount("potion"));
        }

        @Test
        @DisplayName("fromSaveData enforces pocket capacity")
        void fromSaveDataEnforcesPocketCapacity() {
            java.util.List<InventoryData.StackEntry> stacks = new java.util.ArrayList<>();
            for (int i = 0; i < Inventory.POCKET_CAPACITY + 5; i++) {
                stacks.add(new InventoryData.StackEntry("item_" + i, 1));
            }
            InventoryData data = new InventoryData();
            data.pockets = Map.of("MEDICINE", stacks);
            data.registeredItems = List.of();
            Inventory restored = Inventory.fromSaveData(data);
            assertEquals(Inventory.POCKET_CAPACITY, restored.getPocket(ItemCategory.MEDICINE).size());
        }

        @Test
        @DisplayName("fromSaveData skips zero-quantity stacks")
        void fromSaveDataSkipsZeroQuantity() {
            InventoryData data = new InventoryData();
            data.pockets = Map.of("MEDICINE", List.of(
                    new InventoryData.StackEntry("potion", 0),
                    new InventoryData.StackEntry("super_potion", 3)
            ));
            data.registeredItems = List.of();
            Inventory restored = Inventory.fromSaveData(data);
            assertEquals(0, restored.getCount("potion"));
            assertEquals(3, restored.getCount("super_potion"));
            assertEquals(1, restored.getPocket(ItemCategory.MEDICINE).size());
        }
    }

    @Test
    @DisplayName("isFull reflects pocket capacity")
    void isFullReflectsCapacity() {
        assertFalse(inventory.isFull(ItemCategory.MEDICINE));
        for (int i = 0; i < Inventory.POCKET_CAPACITY; i++) {
            registry.addItem(ItemDefinition.builder("fill_" + i, "Fill " + i, ItemCategory.MEDICINE)
                    .price(100).sellPrice(50).usableInBattle(true).usableOutside(true).consumable(true)
                    .effect(ItemEffect.HEAL_HP).effectValue(10).build());
            inventory.addItem("fill_" + i, 1, registry);
        }
        assertTrue(inventory.isFull(ItemCategory.MEDICINE));
    }

    @Test
    @DisplayName("getPocket returns unmodifiable view")
    void getPocketUnmodifiable() {
        inventory.addItem("potion", 1, registry);
        assertThrows(UnsupportedOperationException.class,
                () -> inventory.getPocket(ItemCategory.MEDICINE).add(new ItemStack("x", 1)));
    }

    @Test
    @DisplayName("getAllItems returns unmodifiable list")
    void getAllItemsUnmodifiable() {
        inventory.addItem("potion", 1, registry);
        assertThrows(UnsupportedOperationException.class,
                () -> inventory.getAllItems().add(new ItemStack("x", 1)));
    }
}
