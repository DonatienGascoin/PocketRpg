package com.pocket.rpg.dialogue;

import com.pocket.rpg.resources.loaders.DialogueLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueLoaderTest {

    private DialogueLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new DialogueLoader();
    }

    // ========================================================================
    // ASSET LOADER INTERFACE
    // ========================================================================

    @Nested
    class AssetLoaderInterfaceTests {

        @Test
        void supportedExtensions() {
            String[] extensions = loader.getSupportedExtensions();
            assertEquals(1, extensions.length);
            assertEquals(".dialogue.json", extensions[0]);
        }

        @Test
        void supportsHotReload() {
            assertTrue(loader.supportsHotReload());
        }

        @Test
        void placeholderHasOneEmptyLine() {
            Dialogue placeholder = loader.getPlaceholder();

            assertNotNull(placeholder);
            assertEquals("placeholder", placeholder.getName());
            assertEquals(1, placeholder.getEntries().size());
            assertInstanceOf(DialogueLine.class, placeholder.getEntries().get(0));
            assertEquals("", ((DialogueLine) placeholder.getEntries().get(0)).getText());
        }

        @Test
        void editorPanelType() {
            assertNotNull(loader.getEditorPanelType());
        }
    }

    // ========================================================================
    // LOADING — LINE entries
    // ========================================================================

    @Nested
    class LoadLineTests {

        @Test
        void parseSimpleLines() throws IOException {
            String json = """
                    {
                      "entries": [
                        { "type": "LINE", "text": "Hello!" },
                        { "type": "LINE", "text": "How are you?" }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "greeting.dialogue.json");

            assertEquals("greeting", dialogue.getName());
            assertEquals(2, dialogue.getEntries().size());
            assertInstanceOf(DialogueLine.class, dialogue.getEntries().get(0));
            assertEquals("Hello!", ((DialogueLine) dialogue.getEntries().get(0)).getText());
            assertEquals("How are you?", ((DialogueLine) dialogue.getEntries().get(1)).getText());
        }

        @Test
        void parseLineWithOnCompleteEvent() throws IOException {
            String json = """
                    {
                      "entries": [
                        {
                          "type": "LINE",
                          "text": "Did you hear that?",
                          "onCompleteEvent": {
                            "category": "CUSTOM",
                            "customEvent": "PLAY_RUMBLE"
                          }
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "rumble.dialogue.json");

            DialogueLine line = (DialogueLine) dialogue.getEntries().get(0);
            assertNotNull(line.getOnCompleteEvent());
            assertTrue(line.getOnCompleteEvent().isCustom());
            assertEquals("PLAY_RUMBLE", line.getOnCompleteEvent().getCustomEvent());
        }

        @Test
        void parseLineWithBuiltInOnCompleteEvent() throws IOException {
            String json = """
                    {
                      "entries": [
                        {
                          "type": "LINE",
                          "text": "Farewell!",
                          "onCompleteEvent": {
                            "category": "BUILT_IN",
                            "builtInEvent": "END_CONVERSATION"
                          }
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "farewell.dialogue.json");

            DialogueLine line = (DialogueLine) dialogue.getEntries().get(0);
            assertNotNull(line.getOnCompleteEvent());
            assertTrue(line.getOnCompleteEvent().isBuiltIn());
            assertEquals(DialogueEvent.END_CONVERSATION, line.getOnCompleteEvent().getBuiltInEvent());
        }

        @Test
        void parseLineWithoutOnCompleteEvent() throws IOException {
            String json = """
                    {
                      "entries": [
                        { "type": "LINE", "text": "Normal line." }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "normal.dialogue.json");

            DialogueLine line = (DialogueLine) dialogue.getEntries().get(0);
            assertNull(line.getOnCompleteEvent());
        }
    }

    // ========================================================================
    // LOADING — CHOICES entries
    // ========================================================================

    @Nested
    class LoadChoicesTests {

        @Test
        void parseChoicesWithDialogueAction() throws IOException {
            String json = """
                    {
                      "entries": [
                        {
                          "type": "CHOICES",
                          "hasChoices": true,
                          "choices": [
                            {
                              "text": "Tell me more",
                              "action": { "type": "DIALOGUE", "dialogue": "dialogues/lore.dialogue.json" }
                            }
                          ]
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "branch.dialogue.json");

            DialogueChoiceGroup group = (DialogueChoiceGroup) dialogue.getEntries().get(0);
            assertTrue(group.isHasChoices());
            assertEquals(1, group.getChoices().size());

            Choice choice = group.getChoices().get(0);
            assertEquals("Tell me more", choice.getText());
            assertEquals(ChoiceActionType.DIALOGUE, choice.getAction().getType());
            assertEquals("dialogues/lore.dialogue.json", choice.getAction().getDialoguePath());
        }

        @Test
        void parseChoicesWithBuiltInEventAction() throws IOException {
            String json = """
                    {
                      "entries": [
                        {
                          "type": "CHOICES",
                          "hasChoices": true,
                          "choices": [
                            {
                              "text": "Goodbye",
                              "action": { "type": "BUILT_IN_EVENT", "builtInEvent": "END_CONVERSATION" }
                            }
                          ]
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "bye.dialogue.json");

            Choice choice = ((DialogueChoiceGroup) dialogue.getEntries().get(0)).getChoices().get(0);
            assertEquals(ChoiceActionType.BUILT_IN_EVENT, choice.getAction().getType());
            assertEquals(DialogueEvent.END_CONVERSATION, choice.getAction().getBuiltInEvent());
        }

        @Test
        void parseChoicesWithCustomEventAction() throws IOException {
            String json = """
                    {
                      "entries": [
                        {
                          "type": "CHOICES",
                          "hasChoices": true,
                          "choices": [
                            {
                              "text": "Open the door",
                              "action": { "type": "CUSTOM_EVENT", "customEvent": "OPEN_DOOR" }
                            }
                          ]
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "door.dialogue.json");

            Choice choice = ((DialogueChoiceGroup) dialogue.getEntries().get(0)).getChoices().get(0);
            assertEquals(ChoiceActionType.CUSTOM_EVENT, choice.getAction().getType());
            assertEquals("OPEN_DOOR", choice.getAction().getCustomEvent());
        }

        @Test
        void parseHasChoicesFalse() throws IOException {
            String json = """
                    {
                      "entries": [
                        {
                          "type": "CHOICES",
                          "hasChoices": false,
                          "choices": []
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "nochoice.dialogue.json");

            DialogueChoiceGroup group = (DialogueChoiceGroup) dialogue.getEntries().get(0);
            assertFalse(group.isHasChoices());
            assertTrue(group.getChoices().isEmpty());
        }

        @Test
        void parseEmptyChoicesList() throws IOException {
            String json = """
                    {
                      "entries": [
                        {
                          "type": "CHOICES",
                          "hasChoices": true,
                          "choices": []
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "empty_choices.dialogue.json");

            DialogueChoiceGroup group = (DialogueChoiceGroup) dialogue.getEntries().get(0);
            assertTrue(group.isHasChoices());
            assertTrue(group.getChoices().isEmpty());
        }
    }

    // ========================================================================
    // LOADING — mixed entries
    // ========================================================================

    @Nested
    class LoadMixedTests {

        @Test
        void parseMixedLinesAndChoices() throws IOException {
            String json = """
                    {
                      "entries": [
                        { "type": "LINE", "text": "Hello!" },
                        { "type": "LINE", "text": "What will you do?" },
                        {
                          "type": "CHOICES",
                          "hasChoices": true,
                          "choices": [
                            {
                              "text": "Leave",
                              "action": { "type": "BUILT_IN_EVENT", "builtInEvent": "END_CONVERSATION" }
                            }
                          ]
                        }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "mixed.dialogue.json");

            assertEquals(3, dialogue.getEntries().size());
            assertInstanceOf(DialogueLine.class, dialogue.getEntries().get(0));
            assertInstanceOf(DialogueLine.class, dialogue.getEntries().get(1));
            assertInstanceOf(DialogueChoiceGroup.class, dialogue.getEntries().get(2));
        }

        @Test
        void unknownEntryTypeIsSkipped() throws IOException {
            String json = """
                    {
                      "entries": [
                        { "type": "LINE", "text": "Before" },
                        { "type": "UNKNOWN_TYPE", "data": "ignored" },
                        { "type": "LINE", "text": "After" }
                      ]
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "unknown.dialogue.json");

            assertEquals(2, dialogue.getEntries().size());
            assertEquals("Before", ((DialogueLine) dialogue.getEntries().get(0)).getText());
            assertEquals("After", ((DialogueLine) dialogue.getEntries().get(1)).getText());
        }

        @Test
        void emptyEntriesArray() throws IOException {
            String json = """
                    {
                      "entries": []
                    }
                    """;
            Dialogue dialogue = loadFromJson(json, "empty.dialogue.json");

            assertTrue(dialogue.getEntries().isEmpty());
        }

        @Test
        void missingEntriesField() throws IOException {
            String json = "{}";
            Dialogue dialogue = loadFromJson(json, "noentries.dialogue.json");

            assertTrue(dialogue.getEntries().isEmpty());
        }
    }

    // ========================================================================
    // NAME DERIVATION (tested via load — name is derived from file path)
    // ========================================================================

    @Nested
    class NameDerivationTests {

        @Test
        void nameFromFilename() throws IOException {
            String json = """
                    { "entries": [{ "type": "LINE", "text": "Hi" }] }
                    """;
            Dialogue dialogue = loadFromJson(json, "professor_greeting.dialogue.json");
            assertEquals("professor_greeting", dialogue.getName());
        }

        @Test
        void nameDerivedFromDifferentFilename() throws IOException {
            String json = """
                    { "entries": [{ "type": "LINE", "text": "Hi" }] }
                    """;
            Dialogue dialogue = loadFromJson(json, "shop_welcome.dialogue.json");
            assertEquals("shop_welcome", dialogue.getName());
        }
    }

    // ========================================================================
    // SAVE → LOAD ROUNDTRIP
    // ========================================================================

    @Nested
    class RoundtripTests {

        @Test
        void roundtripSimpleLines() throws IOException {
            Dialogue original = new Dialogue("test", List.of(
                    new DialogueLine("Hello!"),
                    new DialogueLine("Goodbye.")
            ));

            Dialogue loaded = saveAndReload(original);

            assertEquals(2, loaded.getEntries().size());
            assertEquals("Hello!", ((DialogueLine) loaded.getEntries().get(0)).getText());
            assertEquals("Goodbye.", ((DialogueLine) loaded.getEntries().get(1)).getText());
        }

        @Test
        void roundtripWithOnCompleteEvent() throws IOException {
            DialogueLine line = new DialogueLine("Rumble!", DialogueEventRef.custom("PLAY_RUMBLE"));
            Dialogue original = new Dialogue("test", List.of(line));

            Dialogue loaded = saveAndReload(original);

            DialogueLine loadedLine = (DialogueLine) loaded.getEntries().get(0);
            assertNotNull(loadedLine.getOnCompleteEvent());
            assertTrue(loadedLine.getOnCompleteEvent().isCustom());
            assertEquals("PLAY_RUMBLE", loadedLine.getOnCompleteEvent().getCustomEvent());
        }

        @Test
        void roundtripWithBuiltInOnCompleteEvent() throws IOException {
            DialogueLine line = new DialogueLine("End.", DialogueEventRef.builtIn(DialogueEvent.END_CONVERSATION));
            Dialogue original = new Dialogue("test", List.of(line));

            Dialogue loaded = saveAndReload(original);

            DialogueLine loadedLine = (DialogueLine) loaded.getEntries().get(0);
            assertTrue(loadedLine.getOnCompleteEvent().isBuiltIn());
            assertEquals(DialogueEvent.END_CONVERSATION, loadedLine.getOnCompleteEvent().getBuiltInEvent());
        }

        @Test
        void roundtripWithChoices() throws IOException {
            Dialogue original = new Dialogue("test", List.of(
                    new DialogueLine("Pick one:"),
                    new DialogueChoiceGroup(true, List.of(
                            new Choice("Branch", ChoiceAction.dialogue("dialogues/branch.dialogue.json")),
                            new Choice("End", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                            new Choice("Custom", ChoiceAction.customEvent("OPEN_DOOR"))
                    ))
            ));

            Dialogue loaded = saveAndReload(original);

            assertEquals(2, loaded.getEntries().size());
            DialogueChoiceGroup group = (DialogueChoiceGroup) loaded.getEntries().get(1);
            assertTrue(group.isHasChoices());
            assertEquals(3, group.getChoices().size());

            // DIALOGUE action
            assertEquals(ChoiceActionType.DIALOGUE, group.getChoices().get(0).getAction().getType());
            assertEquals("dialogues/branch.dialogue.json", group.getChoices().get(0).getAction().getDialoguePath());

            // BUILT_IN_EVENT action
            assertEquals(ChoiceActionType.BUILT_IN_EVENT, group.getChoices().get(1).getAction().getType());
            assertEquals(DialogueEvent.END_CONVERSATION, group.getChoices().get(1).getAction().getBuiltInEvent());

            // CUSTOM_EVENT action
            assertEquals(ChoiceActionType.CUSTOM_EVENT, group.getChoices().get(2).getAction().getType());
            assertEquals("OPEN_DOOR", group.getChoices().get(2).getAction().getCustomEvent());
        }

        @Test
        void roundtripHasChoicesFalse() throws IOException {
            Dialogue original = new Dialogue("test", List.of(
                    new DialogueLine("End of dialogue."),
                    new DialogueChoiceGroup(false, List.of())
            ));

            Dialogue loaded = saveAndReload(original);

            DialogueChoiceGroup group = (DialogueChoiceGroup) loaded.getEntries().get(1);
            assertFalse(group.isHasChoices());
            assertTrue(group.getChoices().isEmpty());
        }

        @Test
        void roundtripPreservesName() throws IOException {
            Dialogue original = new Dialogue("professor_greeting", List.of(
                    new DialogueLine("Hello!")
            ));

            Dialogue loaded = saveAndReload(original);
            assertEquals("professor_greeting", loaded.getName());
        }
    }

    // ========================================================================
    // HOT RELOAD
    // ========================================================================

    @Nested
    class HotReloadTests {

        @Test
        void reloadMutatesExistingInstance() throws IOException {
            // Save initial version
            Path file = tempDir.resolve("reload_test.dialogue.json");
            Dialogue original = new Dialogue("test", List.of(new DialogueLine("Original")));
            loader.save(original, file.toString());

            // Load it
            Dialogue loaded = loader.load(file.toString());
            assertEquals("Original", ((DialogueLine) loaded.getEntries().get(0)).getText());

            // Save updated version
            Dialogue updated = new Dialogue("test", List.of(new DialogueLine("Updated")));
            loader.save(updated, file.toString());

            // Reload in place
            Dialogue same = loader.reload(loaded, file.toString());
            assertSame(loaded, same);
            assertEquals("Updated", ((DialogueLine) same.getEntries().get(0)).getText());
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private Dialogue loadFromJson(String json, String filename) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, json);
        return loader.load(file.toString());
    }

    private Dialogue saveAndReload(Dialogue dialogue) throws IOException {
        String filename = dialogue.getName() + ".dialogue.json";
        Path file = tempDir.resolve(filename);
        loader.save(dialogue, file.toString());
        return loader.load(file.toString());
    }
}
