package com.pocket.rpg.shop;

import com.pocket.rpg.resources.loaders.ShopRegistryLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShopRegistryLoaderTest {

    private static final Path SAMPLE_PATH = Path.of("gameData/assets/data/shops/shops.shops.json");

    @Test
    @DisplayName("supported extensions")
    void supportedExtensions() {
        ShopRegistryLoader loader = new ShopRegistryLoader();
        assertArrayEquals(new String[]{".shops.json"}, loader.getSupportedExtensions());
    }

    @Test
    @DisplayName("supports hot reload")
    void supportsHotReload() {
        assertTrue(new ShopRegistryLoader().supportsHotReload());
    }

    @Test
    @DisplayName("placeholder is empty registry")
    void placeholderEmpty() {
        ShopRegistryLoader loader = new ShopRegistryLoader();
        ShopRegistry placeholder = loader.getPlaceholder();
        assertNotNull(placeholder);
        assertTrue(placeholder.getAll().isEmpty());
    }

    @Test
    @DisplayName("loads sample shops.shops.json")
    void loadsSampleFile() throws IOException {
        if (!Files.exists(SAMPLE_PATH)) return; // skip if not found (CI)

        ShopRegistryLoader loader = new ShopRegistryLoader();
        ShopRegistry registry = loader.load(SAMPLE_PATH.toString());

        ShopInventory viridian = registry.getShop("viridian_pokemart");
        assertNotNull(viridian);
        assertEquals("Viridian City Pokemart", viridian.getShopName());
        assertEquals(4, viridian.getItems().size());

        // Check unlimited stock item
        ShopInventory.ShopEntry potionEntry = viridian.getItems().stream()
                .filter(e -> e.getItemId().equals("potion"))
                .findFirst().orElse(null);
        assertNotNull(potionEntry);
        assertTrue(potionEntry.isUnlimitedStock());

        // Check limited stock item
        ShopInventory.ShopEntry repelEntry = viridian.getItems().stream()
                .filter(e -> e.getItemId().equals("repel"))
                .findFirst().orElse(null);
        assertNotNull(repelEntry);
        assertFalse(repelEntry.isUnlimitedStock());
        assertEquals(10, repelEntry.getStock());

        ShopInventory pewter = registry.getShop("pewter_pokemart");
        assertNotNull(pewter);
        assertEquals(5, pewter.getItems().size());
    }

    @Test
    @DisplayName("reload mutates existing instance")
    void reloadMutatesInPlace() throws IOException {
        if (!Files.exists(SAMPLE_PATH)) return;

        ShopRegistryLoader loader = new ShopRegistryLoader();
        ShopRegistry original = loader.load(SAMPLE_PATH.toString());
        ShopRegistry reloaded = loader.reload(original, SAMPLE_PATH.toString());

        assertSame(original, reloaded); // same reference
        assertNotNull(reloaded.getShop("viridian_pokemart"));
    }

    @Test
    @DisplayName("save and re-load round-trip")
    void saveRoundTrip() throws IOException {
        if (!Files.exists(SAMPLE_PATH)) return;

        ShopRegistryLoader loader = new ShopRegistryLoader();
        ShopRegistry original = loader.load(SAMPLE_PATH.toString());

        Path tempFile = Files.createTempFile("shops_test_", ".shops.json");
        try {
            loader.save(original, tempFile.toString());
            ShopRegistry reloaded = loader.load(tempFile.toString());

            assertEquals(original.getAll().size(), reloaded.getAll().size());
            assertNotNull(reloaded.getShop("viridian_pokemart"));
            assertEquals(
                    original.getShop("viridian_pokemart").getShopName(),
                    reloaded.getShop("viridian_pokemart").getShopName()
            );
            assertEquals(
                    original.getShop("viridian_pokemart").getItems().size(),
                    reloaded.getShop("viridian_pokemart").getItems().size()
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
