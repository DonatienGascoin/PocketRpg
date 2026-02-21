package com.pocket.rpg.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pocket.rpg.dialogue.DialogueEvents;
import com.pocket.rpg.editor.core.MaterialIcons;

import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for DialogueEvents files ({@code .dialogue-events.json}).
 * <p>
 * Convention path: {@code dialogues/events.dialogue-events.json}
 */
public class DialogueEventsLoader extends JsonAssetLoader<DialogueEvents> {

    private static final String[] EXTENSIONS = {".dialogue-events.json"};

    @Override
    protected DialogueEvents fromJson(JsonObject json, String path) {
        List<String> events = new ArrayList<>();

        if (json.has("events") && json.get("events").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("events")) {
                events.add(elem.getAsString());
            }
        }

        return new DialogueEvents(events);
    }

    @Override
    protected JsonObject toJson(DialogueEvents dialogueEvents) {
        JsonObject json = new JsonObject();
        JsonArray eventsArray = new JsonArray();

        for (String event : dialogueEvents.getEvents()) {
            eventsArray.add(event);
        }

        json.add("events", eventsArray);
        return json;
    }

    @Override
    protected DialogueEvents createPlaceholder() {
        return new DialogueEvents();
    }

    @Override
    protected String[] extensions() {
        return EXTENSIONS;
    }

    @Override
    protected String iconCodepoint() {
        return MaterialIcons.Bolt;
    }

    @Override
    protected void copyInto(DialogueEvents existing, DialogueEvents fresh) {
        existing.copyFrom(fresh);
    }
}
