package com.pocket.rpg.items;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemRegistryTest {

    private ItemRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ItemRegistry();
        registry.addItem(ItemDefinition.builder("potion", "Potion", ItemCategory.MEDICINE)
                .description("Heals 20 HP").price(200).sellPrice(100)
                .usableInBattle(true).usableOutside(true).consumable(true)
                .effect(ItemEffect.HEAL_HP).effectValue(20).build());
        registry.addItem(ItemDefinition.builder("pokeball", "Poke Ball", ItemCategory.POKEBALL)
                .description("Catches Pokemon").price(200).sellPrice(100)
                .usableInBattle(true).consumable(true)
                .effect(ItemEffect.CAPTURE).effectValue(1).build());
        registry.addItem(ItemDefinition.builder("super_potion", "Super Potion", ItemCategory.MEDICINE)
                .description("Heals 60 HP").price(700).sellPrice(350)
                .usableInBattle(true).usableOutside(true).consumable(true)
                .effect(ItemEffect.HEAL_HP).effectValue(60).build());
    }

    @Test
    @DisplayName("get returns correct definition")
    void getReturnsCorrect() {
        ItemDefinition def = registry.get("potion");
        assertNotNull(def);
        assertEquals("Potion", def.getName());
        assertEquals(ItemCategory.MEDICINE, def.getCategory());
    }

    @Test
    @DisplayName("get returns null for unknown")
    void getReturnsNull() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    @DisplayName("getByCategory filters correctly")
    void getByCategoryFilters() {
        List<ItemDefinition> medicine = registry.getByCategory(ItemCategory.MEDICINE);
        assertEquals(2, medicine.size());
        assertTrue(medicine.stream().allMatch(d -> d.getCategory() == ItemCategory.MEDICINE));

        List<ItemDefinition> pokeball = registry.getByCategory(ItemCategory.POKEBALL);
        assertEquals(1, pokeball.size());
        assertEquals("pokeball", pokeball.get(0).getItemId());

        List<ItemDefinition> battle = registry.getByCategory(ItemCategory.BATTLE);
        assertTrue(battle.isEmpty());
    }

    @Test
    @DisplayName("getAll returns all items")
    void getAllReturns() {
        assertEquals(3, registry.getAll().size());
    }

    @Test
    @DisplayName("getAll returns unmodifiable collection")
    void getAllUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getAll().clear());
    }

    @Test
    @DisplayName("getItems returns unmodifiable map")
    void getItemsUnmodifiable() {
        Map<String, ItemDefinition> items = registry.getItems();
        assertThrows(UnsupportedOperationException.class,
                () -> items.put("hack", new ItemDefinition()));
    }

    @Test
    @DisplayName("removeItem removes existing item")
    void removeItem() {
        registry.removeItem("potion");
        assertNull(registry.get("potion"));
        assertEquals(2, registry.getAll().size());
    }

    @Test
    @DisplayName("removeItem does nothing for unknown")
    void removeUnknown() {
        registry.removeItem("nonexistent");
        assertEquals(3, registry.getAll().size());
    }

    @Test
    @DisplayName("getByCategory returns unmodifiable list")
    void getByCategoryUnmodifiable() {
        List<ItemDefinition> medicine = registry.getByCategory(ItemCategory.MEDICINE);
        assertThrows(UnsupportedOperationException.class,
                () -> medicine.add(new ItemDefinition()));
    }

    @Test
    @DisplayName("copyFrom replaces all items")
    void copyFrom() {
        ItemRegistry other = new ItemRegistry();
        other.addItem(ItemDefinition.builder("bicycle", "Bicycle", ItemCategory.KEY_ITEM)
                .description("Fast").usableOutside(true).stackLimit(1)
                .effect(ItemEffect.TOGGLE_BICYCLE).build());

        registry.copyFrom(other);

        assertNull(registry.get("potion"));
        assertNotNull(registry.get("bicycle"));
        assertEquals(1, registry.getAll().size());
    }
}
