package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.dialogue.DialogueVariable;
import com.pocket.rpg.dialogue.DialogueVariables;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for DialogueVariables files ({@code .dialogue-vars.json}).
 * <p>
 * Convention path: {@code dialogues/variables.dialogue-vars.json}
 */
public class DialogueVariablesLoader implements AssetLoader<DialogueVariables> {

    private static final String[] EXTENSIONS = {".dialogue-vars.json"};
    private static final Logger LOG = Log.getLogger(DialogueVariablesLoader.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static DialogueVariables placeholder;

    // ========================================================================
    // LOADING
    // ========================================================================

    @Override
    public DialogueVariables load(String path) throws IOException {
        try {
            String jsonContent = Files.readString(Paths.get(path));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            return fromJSON(json);
        } catch (Exception e) {
            throw new IOException("Failed to load dialogue variables: " + path, e);
        }
    }

    private DialogueVariables fromJSON(JsonObject json) {
        List<DialogueVariable> variables = new ArrayList<>();

        if (json.has("variables") && json.get("variables").isJsonArray()) {
            for (JsonElement elem : json.getAsJsonArray("variables")) {
                JsonObject varObj = elem.getAsJsonObject();
                variables.add(parseVariable(varObj));
            }
        }

        return new DialogueVariables(variables);
    }

    private DialogueVariable parseVariable(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : "";
        DialogueVariable.Type type = DialogueVariable.Type.STATIC;

        if (json.has("type") && !json.get("type").isJsonNull()) {
            try {
                type = DialogueVariable.Type.valueOf(json.get("type").getAsString());
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown variable type: " + json.get("type").getAsString() + " — defaulting to STATIC");
            }
        }

        return new DialogueVariable(name, type);
    }

    // ========================================================================
    // SAVING
    // ========================================================================

    @Override
    public void save(DialogueVariables variables, String path) throws IOException {
        try {
            JsonObject json = toJSON(variables);
            String jsonString = gson.toJson(json);

            Path filePath = Paths.get(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(filePath, jsonString);
        } catch (Exception e) {
            throw new IOException("Failed to save dialogue variables: " + path, e);
        }
    }

    private JsonObject toJSON(DialogueVariables variables) {
        JsonObject json = new JsonObject();
        JsonArray varsArray = new JsonArray();

        for (DialogueVariable var : variables.getVariables()) {
            JsonObject varObj = new JsonObject();
            varObj.addProperty("name", var.getName());
            varObj.addProperty("type", var.getType().name());
            varsArray.add(varObj);
        }

        json.add("variables", varsArray);
        return json;
    }

    // ========================================================================
    // ASSET LOADER INTERFACE
    // ========================================================================

    @Override
    public DialogueVariables getPlaceholder() {
        if (placeholder == null) {
            placeholder = new DialogueVariables();
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
    public DialogueVariables reload(DialogueVariables existing, String path) throws IOException {
        DialogueVariables reloaded = load(path);
        existing.copyFrom(reloaded);
        return existing;
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.Code;
    }
}
