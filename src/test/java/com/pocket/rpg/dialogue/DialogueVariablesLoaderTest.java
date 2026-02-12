package com.pocket.rpg.dialogue;

import com.pocket.rpg.resources.loaders.DialogueVariablesLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueVariablesLoaderTest {

    private DialogueVariablesLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new DialogueVariablesLoader();
    }

    @Test
    void supportedExtensions() {
        String[] extensions = loader.getSupportedExtensions();
        assertEquals(1, extensions.length);
        assertEquals(".dialogue-vars.json", extensions[0]);
    }

    @Test
    void supportsHotReload() {
        assertTrue(loader.supportsHotReload());
    }

    @Test
    void placeholderIsEmptyList() {
        DialogueVariables placeholder = loader.getPlaceholder();
        assertNotNull(placeholder);
        assertTrue(placeholder.getVariables().isEmpty());
    }

    @Test
    void parseAllThreeTypes() throws IOException {
        String json = """
                {
                  "variables": [
                    { "name": "PLAYER_NAME", "type": "AUTO" },
                    { "name": "TRAINER_NAME", "type": "STATIC" },
                    { "name": "POKEMON_NAME", "type": "RUNTIME" }
                  ]
                }
                """;
        DialogueVariables vars = loadFromJson(json);

        assertEquals(3, vars.getVariables().size());

        assertEquals("PLAYER_NAME", vars.getVariables().get(0).getName());
        assertEquals(DialogueVariable.Type.AUTO, vars.getVariables().get(0).getType());

        assertEquals("TRAINER_NAME", vars.getVariables().get(1).getName());
        assertEquals(DialogueVariable.Type.STATIC, vars.getVariables().get(1).getType());

        assertEquals("POKEMON_NAME", vars.getVariables().get(2).getName());
        assertEquals(DialogueVariable.Type.RUNTIME, vars.getVariables().get(2).getType());
    }

    @Test
    void parseEmptyList() throws IOException {
        String json = """
                { "variables": [] }
                """;
        DialogueVariables vars = loadFromJson(json);
        assertTrue(vars.getVariables().isEmpty());
    }

    @Test
    void parseMissingVariablesField() throws IOException {
        String json = "{}";
        DialogueVariables vars = loadFromJson(json);
        assertTrue(vars.getVariables().isEmpty());
    }

    @Test
    void roundtrip() throws IOException {
        DialogueVariables original = new DialogueVariables(List.of(
                new DialogueVariable("PLAYER_NAME", DialogueVariable.Type.AUTO),
                new DialogueVariable("MONEY", DialogueVariable.Type.AUTO),
                new DialogueVariable("TRAINER_NAME", DialogueVariable.Type.STATIC),
                new DialogueVariable("POKEMON_NAME", DialogueVariable.Type.RUNTIME)
        ));

        Path file = tempDir.resolve("variables.dialogue-vars.json");
        loader.save(original, file.toString());
        DialogueVariables loaded = loader.load(file.toString());

        assertEquals(4, loaded.getVariables().size());
        assertEquals("PLAYER_NAME", loaded.getVariables().get(0).getName());
        assertEquals(DialogueVariable.Type.AUTO, loaded.getVariables().get(0).getType());
        assertEquals("MONEY", loaded.getVariables().get(1).getName());
        assertEquals(DialogueVariable.Type.AUTO, loaded.getVariables().get(1).getType());
        assertEquals("TRAINER_NAME", loaded.getVariables().get(2).getName());
        assertEquals(DialogueVariable.Type.STATIC, loaded.getVariables().get(2).getType());
        assertEquals("POKEMON_NAME", loaded.getVariables().get(3).getName());
        assertEquals(DialogueVariable.Type.RUNTIME, loaded.getVariables().get(3).getType());
    }

    @Test
    void hotReloadMutatesExistingInstance() throws IOException {
        Path file = tempDir.resolve("vars.dialogue-vars.json");

        DialogueVariables original = new DialogueVariables(List.of(
                new DialogueVariable("OLD", DialogueVariable.Type.STATIC)
        ));
        loader.save(original, file.toString());
        DialogueVariables loaded = loader.load(file.toString());

        // Save updated version
        DialogueVariables updated = new DialogueVariables(List.of(
                new DialogueVariable("NEW1", DialogueVariable.Type.AUTO),
                new DialogueVariable("NEW2", DialogueVariable.Type.RUNTIME)
        ));
        loader.save(updated, file.toString());

        DialogueVariables same = loader.reload(loaded, file.toString());
        assertSame(loaded, same);
        assertEquals(2, same.getVariables().size());
        assertEquals("NEW1", same.getVariables().get(0).getName());
    }

    private DialogueVariables loadFromJson(String json) throws IOException {
        Path file = tempDir.resolve("test.dialogue-vars.json");
        Files.writeString(file, json);
        return loader.load(file.toString());
    }
}
