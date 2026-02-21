package com.pocket.rpg.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for Dialogue files ({@code .dialogue.json}).
 * <p>
 * Manual JSON parsing with type discrimination for polymorphic {@link DialogueEntry} types.
 */
public class DialogueLoader extends JsonAssetLoader<Dialogue> {

    private static final String[] EXTENSIONS = {".dialogue.json"};
    private static final Logger LOG = Log.getLogger(DialogueLoader.class);

    // ========================================================================
    // JSON PARSING
    // ========================================================================

    @Override
    protected Dialogue fromJson(JsonObject json, String path) {
        Dialogue dialogue = new Dialogue();
        List<DialogueEntry> entries = new ArrayList<>();

        if (json.has("entries") && json.get("entries").isJsonArray()) {
            JsonArray entriesJson = json.getAsJsonArray("entries");
            for (JsonElement elem : entriesJson) {
                JsonObject entryObj = elem.getAsJsonObject();
                String type = entryObj.has("type") ? entryObj.get("type").getAsString() : "";
                switch (type) {
                    case "LINE" -> entries.add(parseLine(entryObj));
                    case "CHOICES" -> entries.add(parseChoiceGroup(entryObj));
                    default -> LOG.warn("Unknown entry type: " + type + " — skipping");
                }
            }
        }

        dialogue.setEntries(entries);
        return dialogue;
    }

    private DialogueLine parseLine(JsonObject json) {
        DialogueLine line = new DialogueLine();
        if (json.has("text") && !json.get("text").isJsonNull()) {
            line.setText(json.get("text").getAsString());
        }
        if (json.has("onCompleteEvent") && !json.get("onCompleteEvent").isJsonNull()) {
            line.setOnCompleteEvent(parseEventRef(json.getAsJsonObject("onCompleteEvent")));
        }
        return line;
    }

    private DialogueChoiceGroup parseChoiceGroup(JsonObject json) {
        boolean hasChoices = json.has("hasChoices") && json.get("hasChoices").getAsBoolean();

        List<Choice> choices = new ArrayList<>();
        if (json.has("choices") && json.get("choices").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("choices")) {
                choices.add(parseChoice(elem.getAsJsonObject()));
            }
        }

        return new DialogueChoiceGroup(hasChoices, choices);
    }

    private Choice parseChoice(JsonObject json) {
        Choice choice = new Choice();
        if (json.has("text") && !json.get("text").isJsonNull()) {
            choice.setText(json.get("text").getAsString());
        }
        if (json.has("action") && !json.get("action").isJsonNull()) {
            choice.setAction(parseChoiceAction(json.getAsJsonObject("action")));
        }
        return choice;
    }

    private ChoiceAction parseChoiceAction(JsonObject json) {
        ChoiceAction action = new ChoiceAction();
        if (!json.has("type") || json.get("type").isJsonNull()) {
            return action;
        }

        String typeStr = json.get("type").getAsString();
        try {
            action.setType(ChoiceActionType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown choice action type: " + typeStr);
            return action;
        }

        switch (action.getType()) {
            case DIALOGUE -> {
                if (json.has("dialogue") && !json.get("dialogue").isJsonNull()) {
                    action.setDialoguePath(json.get("dialogue").getAsString());
                }
            }
            case BUILT_IN_EVENT -> {
                if (json.has("builtInEvent") && !json.get("builtInEvent").isJsonNull()) {
                    try {
                        action.setBuiltInEvent(DialogueEvent.valueOf(json.get("builtInEvent").getAsString()));
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Unknown built-in event: " + json.get("builtInEvent").getAsString());
                    }
                }
            }
            case CUSTOM_EVENT -> {
                if (json.has("customEvent") && !json.get("customEvent").isJsonNull()) {
                    action.setCustomEvent(json.get("customEvent").getAsString());
                }
            }
        }

        return action;
    }

    private DialogueEventRef parseEventRef(JsonObject json) {
        DialogueEventRef ref = new DialogueEventRef();
        if (!json.has("category") || json.get("category").isJsonNull()) {
            return ref;
        }

        String categoryStr = json.get("category").getAsString();
        try {
            ref.setCategory(DialogueEventRef.Category.valueOf(categoryStr));
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown event ref category: " + categoryStr);
            return ref;
        }

        switch (ref.getCategory()) {
            case BUILT_IN -> {
                if (json.has("builtInEvent") && !json.get("builtInEvent").isJsonNull()) {
                    try {
                        ref.setBuiltInEvent(DialogueEvent.valueOf(json.get("builtInEvent").getAsString()));
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Unknown built-in event: " + json.get("builtInEvent").getAsString());
                    }
                }
            }
            case CUSTOM -> {
                if (json.has("customEvent") && !json.get("customEvent").isJsonNull()) {
                    ref.setCustomEvent(json.get("customEvent").getAsString());
                }
            }
        }

        return ref;
    }

    // ========================================================================
    // JSON SERIALIZATION
    // ========================================================================

    @Override
    protected JsonObject toJson(Dialogue dialogue) {
        JsonObject json = new JsonObject();

        JsonArray entriesJson = new JsonArray();
        for (DialogueEntry entry : dialogue.getEntries()) {
            switch (entry) {
                case DialogueLine line -> entriesJson.add(lineToJSON(line));
                case DialogueChoiceGroup group -> entriesJson.add(choiceGroupToJSON(group));
            }
        }
        json.add("entries", entriesJson);

        return json;
    }

    private JsonObject lineToJSON(DialogueLine line) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "LINE");
        json.addProperty("text", line.getText());
        if (line.getOnCompleteEvent() != null) {
            json.add("onCompleteEvent", eventRefToJSON(line.getOnCompleteEvent()));
        }
        return json;
    }

    private JsonObject choiceGroupToJSON(DialogueChoiceGroup group) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CHOICES");
        json.addProperty("hasChoices", group.isHasChoices());

        JsonArray choicesJson = new JsonArray();
        for (Choice choice : group.getChoices()) {
            choicesJson.add(choiceToJSON(choice));
        }
        json.add("choices", choicesJson);

        return json;
    }

    private JsonObject choiceToJSON(Choice choice) {
        JsonObject json = new JsonObject();
        json.addProperty("text", choice.getText());
        if (choice.getAction() != null && choice.getAction().getType() != null) {
            json.add("action", choiceActionToJSON(choice.getAction()));
        }
        return json;
    }

    private JsonObject choiceActionToJSON(ChoiceAction action) {
        JsonObject json = new JsonObject();
        json.addProperty("type", action.getType().name());

        switch (action.getType()) {
            case DIALOGUE -> {
                if (action.getDialoguePath() != null) {
                    json.addProperty("dialogue", action.getDialoguePath());
                }
            }
            case BUILT_IN_EVENT -> {
                if (action.getBuiltInEvent() != null) {
                    json.addProperty("builtInEvent", action.getBuiltInEvent().name());
                }
            }
            case CUSTOM_EVENT -> {
                if (action.getCustomEvent() != null) {
                    json.addProperty("customEvent", action.getCustomEvent());
                }
            }
        }

        return json;
    }

    private JsonObject eventRefToJSON(DialogueEventRef ref) {
        JsonObject json = new JsonObject();
        if (ref.getCategory() != null) {
            json.addProperty("category", ref.getCategory().name());
        }
        if (ref.getBuiltInEvent() != null) {
            json.addProperty("builtInEvent", ref.getBuiltInEvent().name());
        }
        if (ref.getCustomEvent() != null) {
            json.addProperty("customEvent", ref.getCustomEvent());
        }
        return json;
    }

    // ========================================================================
    // JsonAssetLoader CONFIGURATION
    // ========================================================================

    @Override
    protected void afterLoad(Dialogue dialogue, String path) {
        dialogue.setName(deriveNameFromPath(path));
    }

    @Override
    protected Dialogue createPlaceholder() {
        Dialogue placeholder = new Dialogue("placeholder");
        placeholder.getEntries().add(new DialogueLine(""));
        return placeholder;
    }

    @Override
    protected String[] extensions() {
        return EXTENSIONS;
    }

    @Override
    protected String iconCodepoint() {
        return MaterialIcons.ChatBubble;
    }

    @Override
    protected void copyInto(Dialogue existing, Dialogue fresh) {
        existing.copyFrom(fresh);
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Derives the display name from a file path.
     * e.g. "dialogues/professor_greeting.dialogue.json" -> "professor_greeting"
     */
    static String deriveNameFromPath(String path) {
        String fileName = Paths.get(path).getFileName().toString();
        // Strip ".dialogue.json" extension
        if (fileName.endsWith(".dialogue.json")) {
            return fileName.substring(0, fileName.length() - ".dialogue.json".length());
        }
        // Fallback: strip last extension
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
