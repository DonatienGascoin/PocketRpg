package com.pocket.rpg.dialogue;

import com.pocket.rpg.resources.loaders.DialogueEventsLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueEventsLoaderTest {

    private DialogueEventsLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new DialogueEventsLoader();
    }

    @Test
    void supportedExtensions() {
        String[] extensions = loader.getSupportedExtensions();
        assertEquals(1, extensions.length);
        assertEquals(".dialogue-events.json", extensions[0]);
    }

    @Test
    void supportsHotReload() {
        assertTrue(loader.supportsHotReload());
    }

    @Test
    void placeholderIsEmptyList() {
        DialogueEvents placeholder = loader.getPlaceholder();
        assertNotNull(placeholder);
        assertTrue(placeholder.getEvents().isEmpty());
    }

    @Test
    void parseEventList() throws IOException {
        String json = """
                {
                  "events": ["OPEN_DOOR", "GIVE_ITEM", "UNLOCK_PATH"]
                }
                """;
        DialogueEvents events = loadFromJson(json);

        assertEquals(3, events.getEvents().size());
        assertEquals("OPEN_DOOR", events.getEvents().get(0));
        assertEquals("GIVE_ITEM", events.getEvents().get(1));
        assertEquals("UNLOCK_PATH", events.getEvents().get(2));
    }

    @Test
    void parseEmptyList() throws IOException {
        String json = """
                { "events": [] }
                """;
        DialogueEvents events = loadFromJson(json);
        assertTrue(events.getEvents().isEmpty());
    }

    @Test
    void parseMissingEventsField() throws IOException {
        String json = "{}";
        DialogueEvents events = loadFromJson(json);
        assertTrue(events.getEvents().isEmpty());
    }

    @Test
    void roundtrip() throws IOException {
        DialogueEvents original = new DialogueEvents(List.of(
                "OPEN_DOOR", "GIVE_ITEM", "UNLOCK_PATH", "START_QUEST"
        ));

        Path file = tempDir.resolve("events.dialogue-events.json");
        loader.save(original, file.toString());
        DialogueEvents loaded = loader.load(file.toString());

        assertEquals(4, loaded.getEvents().size());
        assertEquals("OPEN_DOOR", loaded.getEvents().get(0));
        assertEquals("GIVE_ITEM", loaded.getEvents().get(1));
        assertEquals("UNLOCK_PATH", loaded.getEvents().get(2));
        assertEquals("START_QUEST", loaded.getEvents().get(3));
    }

    @Test
    void hotReloadMutatesExistingInstance() throws IOException {
        Path file = tempDir.resolve("events.dialogue-events.json");

        DialogueEvents original = new DialogueEvents(List.of("OLD_EVENT"));
        loader.save(original, file.toString());
        DialogueEvents loaded = loader.load(file.toString());

        // Save updated version
        DialogueEvents updated = new DialogueEvents(List.of("NEW_EVENT_1", "NEW_EVENT_2"));
        loader.save(updated, file.toString());

        DialogueEvents same = loader.reload(loaded, file.toString());
        assertSame(loaded, same);
        assertEquals(2, same.getEvents().size());
        assertEquals("NEW_EVENT_1", same.getEvents().get(0));
    }

    private DialogueEvents loadFromJson(String json) throws IOException {
        Path file = tempDir.resolve("test.dialogue-events.json");
        Files.writeString(file, json);
        return loader.load(file.toString());
    }
}
