package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.dialogue.DialogueEvents;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for DialogueEvents files ({@code .dialogue-events.json}).
 * <p>
 * Convention path: {@code dialogues/events.dialogue-events.json}
 */
public class DialogueEventsLoader implements AssetLoader<DialogueEvents> {

    private static final String[] EXTENSIONS = {".dialogue-events.json"};
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static DialogueEvents placeholder;

    // ========================================================================
    // LOADING
    // ========================================================================

    @Override
    public DialogueEvents load(String path) throws IOException {
        try {
            String jsonContent = Files.readString(Paths.get(path));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            return fromJSON(json);
        } catch (Exception e) {
            throw new IOException("Failed to load dialogue events: " + path, e);
        }
    }

    private DialogueEvents fromJSON(JsonObject json) {
        List<String> events = new ArrayList<>();

        if (json.has("events") && json.get("events").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("events")) {
                events.add(elem.getAsString());
            }
        }

        return new DialogueEvents(events);
    }

    // ========================================================================
    // SAVING
    // ========================================================================

    @Override
    public void save(DialogueEvents dialogueEvents, String path) throws IOException {
        try {
            JsonObject json = toJSON(dialogueEvents);
            String jsonString = gson.toJson(json);

            Path filePath = Paths.get(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(filePath, jsonString);
        } catch (Exception e) {
            throw new IOException("Failed to save dialogue events: " + path, e);
        }
    }

    private JsonObject toJSON(DialogueEvents dialogueEvents) {
        JsonObject json = new JsonObject();
        JsonArray eventsArray = new JsonArray();

        for (String event : dialogueEvents.getEvents()) {
            eventsArray.add(event);
        }

        json.add("events", eventsArray);
        return json;
    }

    // ========================================================================
    // ASSET LOADER INTERFACE
    // ========================================================================

    @Override
    public DialogueEvents getPlaceholder() {
        if (placeholder == null) {
            placeholder = new DialogueEvents();
        }
        return placeholder;
    }

    @Override
    public String[] getSupportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public DialogueEvents reload(DialogueEvents existing, String path) throws IOException {
        DialogueEvents reloaded = load(path);
        existing.copyFrom(reloaded);
        return existing;
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.Bolt;
    }
}
