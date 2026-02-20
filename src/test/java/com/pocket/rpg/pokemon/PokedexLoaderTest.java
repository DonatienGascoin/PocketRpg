package com.pocket.rpg.pokemon;

import com.pocket.rpg.resources.loaders.PokedexLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PokedexLoaderTest {

    @TempDir
    Path tempDir;

    private static final String SAMPLE_JSON = """
            {
              "species": [
                {
                  "speciesId": "bulbasaur",
                  "name": "Bulbasaur",
                  "type": "GRASS",
                  "baseStats": { "hp": 45, "atk": 49, "def": 49, "spAtk": 65, "spDef": 65, "spd": 45 },
                  "learnset": [
                    { "level": 1, "moveId": "tackle" },
                    { "level": 7, "moveId": "vine_whip" }
                  ],
                  "baseExpYield": 64,
                  "catchRate": 45,
                  "growthRate": "MEDIUM_SLOW",
                  "spriteId": "sprites/pokemon/bulbasaur",
                  "evolutionMethod": "LEVEL",
                  "evolutionLevel": 16,
                  "evolutionItem": null,
                  "evolvesInto": "ivysaur"
                }
              ],
              "moves": [
                {
                  "moveId": "tackle",
                  "name": "Tackle",
                  "type": "NORMAL",
                  "category": "PHYSICAL",
                  "power": 40,
                  "accuracy": 100,
                  "pp": 35,
                  "effect": "",
                  "effectChance": 0,
                  "priority": 0
                },
                {
                  "moveId": "vine_whip",
                  "name": "Vine Whip",
                  "type": "GRASS",
                  "category": "PHYSICAL",
                  "power": 45,
                  "accuracy": 100,
                  "pp": 25,
                  "effect": "",
                  "effectChance": 0,
                  "priority": 0
                }
              ]
            }
            """;

    private Path writeJson(String content) throws IOException {
        Path file = tempDir.resolve("test.pokedex.json");
        Files.writeString(file, content);
        return file;
    }

    @Test
    @DisplayName("loads species from JSON")
    void loadsSpecies() throws IOException {
        PokedexLoader loader = new PokedexLoader();
        Pokedex pokedex = loader.load(writeJson(SAMPLE_JSON).toString());

        PokemonSpecies bulbasaur = pokedex.getSpecies("bulbasaur");
        assertNotNull(bulbasaur);
        assertEquals("Bulbasaur", bulbasaur.getName());
        assertEquals(PokemonType.GRASS, bulbasaur.getType());
        assertEquals(45, bulbasaur.getBaseStats().hp());
        assertEquals(2, bulbasaur.getLearnset().size());
        assertEquals("ivysaur", bulbasaur.getEvolvesInto());
        assertEquals(EvolutionMethod.LEVEL, bulbasaur.getEvolutionMethod());
        assertEquals(16, bulbasaur.getEvolutionLevel());
    }

    @Test
    @DisplayName("loads moves from JSON")
    void loadsMoves() throws IOException {
        PokedexLoader loader = new PokedexLoader();
        Pokedex pokedex = loader.load(writeJson(SAMPLE_JSON).toString());

        Move tackle = pokedex.getMove("tackle");
        assertNotNull(tackle);
        assertEquals("Tackle", tackle.getName());
        assertEquals(PokemonType.NORMAL, tackle.getType());
        assertEquals(MoveCategory.PHYSICAL, tackle.getCategory());
        assertEquals(40, tackle.getPower());
        assertEquals(35, tackle.getPp());
    }

    @Test
    @DisplayName("returns null for unknown species/move")
    void returnsNullForUnknown() throws IOException {
        PokedexLoader loader = new PokedexLoader();
        Pokedex pokedex = loader.load(writeJson(SAMPLE_JSON).toString());

        assertNull(pokedex.getSpecies("pikachu"));
        assertNull(pokedex.getMove("thunderbolt"));
    }

    @Test
    @DisplayName("hot-reload mutates existing instance")
    void hotReloadMutatesExisting() throws IOException {
        PokedexLoader loader = new PokedexLoader();
        assertTrue(loader.supportsHotReload());

        Path file = writeJson(SAMPLE_JSON);
        Pokedex original = loader.load(file.toString());
        assertNotNull(original.getSpecies("bulbasaur"));

        // Write updated JSON with different species
        String updatedJson = """
                {
                  "species": [
                    {
                      "speciesId": "pikachu",
                      "name": "Pikachu",
                      "type": "ELECTRIC",
                      "baseStats": { "hp": 35, "atk": 55, "def": 40, "spAtk": 50, "spDef": 50, "spd": 90 },
                      "learnset": [],
                      "baseExpYield": 112,
                      "catchRate": 190,
                      "growthRate": "MEDIUM_FAST",
                      "evolutionMethod": "NONE"
                    }
                  ],
                  "moves": []
                }
                """;
        Files.writeString(file, updatedJson);

        Pokedex reloaded = loader.reload(original, file.toString());
        assertSame(original, reloaded, "reload must return same reference");
        assertNull(reloaded.getSpecies("bulbasaur"), "old species should be gone");
        assertNotNull(reloaded.getSpecies("pikachu"), "new species should be present");
    }

    @Test
    @DisplayName("save and reload round-trip")
    void saveAndReload() throws IOException {
        PokedexLoader loader = new PokedexLoader();
        Path file = writeJson(SAMPLE_JSON);
        Pokedex pokedex = loader.load(file.toString());

        // Save to new file
        Path savedFile = tempDir.resolve("saved.pokedex.json");
        loader.save(pokedex, savedFile.toString());

        // Reload
        Pokedex reloaded = loader.load(savedFile.toString());
        assertNotNull(reloaded.getSpecies("bulbasaur"));
        assertEquals("Bulbasaur", reloaded.getSpecies("bulbasaur").getName());
        assertNotNull(reloaded.getMove("tackle"));
        assertNotNull(reloaded.getMove("vine_whip"));
    }

    @Test
    @DisplayName("supported extensions")
    void supportedExtensions() {
        PokedexLoader loader = new PokedexLoader();
        assertArrayEquals(new String[]{".pokedex.json"}, loader.getSupportedExtensions());
    }

    @Test
    @DisplayName("placeholder returns empty Pokedex")
    void placeholderIsEmpty() {
        PokedexLoader loader = new PokedexLoader();
        Pokedex placeholder = loader.getPlaceholder();
        assertNotNull(placeholder);
        assertTrue(placeholder.getAllSpecies().isEmpty());
        assertTrue(placeholder.getAllMoves().isEmpty());
    }
}
