package com.pocket.rpg.items;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.resources.loaders.ItemRegistryLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ItemRegistryLoaderTest {

    private static final Path SAMPLE_PATH = Path.of("gameData/assets/data/items/items.items.json");

    @Test
    @DisplayName("supported extensions")
    void supportedExtensions() {
        ItemRegistryLoader loader = new ItemRegistryLoader();
        assertArrayEquals(new String[]{".items.json"}, loader.getSupportedExtensions());
    }

    @Test
    @DisplayName("supports hot reload")
    void supportsHotReload() {
        assertTrue(new ItemRegistryLoader().supportsHotReload());
    }

    @Test
    @DisplayName("placeholder is empty registry")
    void placeholderEmpty() {
        ItemRegistryLoader loader = new ItemRegistryLoader();
        ItemRegistry placeholder = loader.getPlaceholder();
        assertNotNull(placeholder);
        assertTrue(placeholder.getAll().isEmpty());
    }

    @Test
    @DisplayName("loads sample items.items.json")
    void loadsSampleFile() throws IOException {
        if (!Files.exists(SAMPLE_PATH)) return; // skip if not found (CI)

        ItemRegistryLoader loader = new ItemRegistryLoader();
        ItemRegistry registry = loader.load(SAMPLE_PATH.toString());

        assertNotNull(registry.get("potion"));
        assertEquals("Potion", registry.get("potion").getName());
        assertEquals(ItemCategory.MEDICINE, registry.get("potion").getCategory());
        assertEquals(ItemEffect.HEAL_HP, registry.get("potion").getEffect());
        assertEquals(20, registry.get("potion").getEffectValue());

        assertNotNull(registry.get("pokeball"));
        assertEquals(ItemCategory.POKEBALL, registry.get("pokeball").getCategory());

        assertNotNull(registry.get("bicycle"));
        assertEquals(ItemCategory.KEY_ITEM, registry.get("bicycle").getCategory());
        assertFalse(registry.get("bicycle").isConsumable());
        assertEquals(1, registry.get("bicycle").getStackLimit());
    }

    @Test
    @DisplayName("reload mutates existing instance")
    void reloadMutatesInPlace() throws IOException {
        if (!Files.exists(SAMPLE_PATH)) return;

        ItemRegistryLoader loader = new ItemRegistryLoader();
        ItemRegistry original = loader.load(SAMPLE_PATH.toString());
        ItemRegistry reloaded = loader.reload(original, SAMPLE_PATH.toString());

        assertSame(original, reloaded); // same reference
        assertNotNull(reloaded.get("potion"));
    }

    @Test
    @DisplayName("save and re-load round-trip")
    void saveRoundTrip() throws IOException {
        if (!Files.exists(SAMPLE_PATH)) return;

        ItemRegistryLoader loader = new ItemRegistryLoader();
        ItemRegistry original = loader.load(SAMPLE_PATH.toString());

        Path tempFile = Files.createTempFile("items_test_", ".items.json");
        try {
            loader.save(original, tempFile.toString());
            ItemRegistry reloaded = loader.load(tempFile.toString());

            assertEquals(original.getAll().size(), reloaded.getAll().size());
            assertNotNull(reloaded.get("potion"));
            assertEquals(original.get("potion").getName(), reloaded.get("potion").getName());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
