package com.pocket.rpg.shop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShopRegistryTest {

    private ShopRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShopRegistry();
        registry.addShop(new ShopInventory("viridian", "Viridian Pokemart", List.of(
                new ShopInventory.ShopEntry("potion", -1),
                new ShopInventory.ShopEntry("pokeball", -1),
                new ShopInventory.ShopEntry("repel", 10)
        )));
        registry.addShop(new ShopInventory("pewter", "Pewter Pokemart", List.of(
                new ShopInventory.ShopEntry("super_potion", -1),
                new ShopInventory.ShopEntry("great_ball", -1)
        )));
    }

    @Test
    @DisplayName("getShop returns correct shop for known id")
    void getShopReturnsCorrect() {
        ShopInventory shop = registry.getShop("viridian");
        assertNotNull(shop);
        assertEquals("Viridian Pokemart", shop.getShopName());
        assertEquals(3, shop.getItems().size());
    }

    @Test
    @DisplayName("getShop returns null for unknown id")
    void getShopReturnsNull() {
        assertNull(registry.getShop("nonexistent"));
    }

    @Test
    @DisplayName("getAll returns all shops")
    void getAllReturns() {
        assertEquals(2, registry.getAll().size());
    }

    @Test
    @DisplayName("getAll returns unmodifiable collection")
    void getAllUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> registry.getAll().clear());
    }

    @Test
    @DisplayName("getShops returns unmodifiable map")
    void getShopsUnmodifiable() {
        Map<String, ShopInventory> shops = registry.getShops();
        assertThrows(UnsupportedOperationException.class,
                () -> shops.put("hack", new ShopInventory()));
    }

    @Test
    @DisplayName("removeShop removes existing shop")
    void removeShop() {
        registry.removeShop("viridian");
        assertNull(registry.getShop("viridian"));
        assertEquals(1, registry.getAll().size());
    }

    @Test
    @DisplayName("removeShop does nothing for unknown")
    void removeUnknown() {
        registry.removeShop("nonexistent");
        assertEquals(2, registry.getAll().size());
    }

    @Test
    @DisplayName("copyFrom replaces all shops")
    void copyFrom() {
        ShopRegistry other = new ShopRegistry();
        other.addShop(new ShopInventory("cerulean", "Cerulean Pokemart", List.of(
                new ShopInventory.ShopEntry("ultra_ball", -1)
        )));

        registry.copyFrom(other);

        assertNull(registry.getShop("viridian"));
        assertNull(registry.getShop("pewter"));
        assertNotNull(registry.getShop("cerulean"));
        assertEquals(1, registry.getAll().size());
    }

    @Test
    @DisplayName("ShopEntry unlimited stock check")
    void shopEntryUnlimited() {
        ShopInventory.ShopEntry unlimited = new ShopInventory.ShopEntry("potion", -1);
        assertTrue(unlimited.isUnlimitedStock());

        ShopInventory.ShopEntry limited = new ShopInventory.ShopEntry("repel", 10);
        assertFalse(limited.isUnlimitedStock());
    }

    @Test
    @DisplayName("ShopInventory items list is unmodifiable")
    void shopInventoryItemsUnmodifiable() {
        ShopInventory shop = registry.getShop("viridian");
        assertThrows(UnsupportedOperationException.class,
                () -> shop.getItems().add(new ShopInventory.ShopEntry("hack", -1)));
    }
}
