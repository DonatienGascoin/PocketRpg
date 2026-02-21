package com.pocket.rpg.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pocket.rpg.dialogue.DialogueVariable;
import com.pocket.rpg.dialogue.DialogueVariables;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for DialogueVariables files ({@code .dialogue-vars.json}).
 * <p>
 * Convention path: {@code dialogues/variables.dialogue-vars.json}
 */
public class DialogueVariablesLoader extends JsonAssetLoader<DialogueVariables> {

    private static final String[] EXTENSIONS = {".dialogue-vars.json"};
    private static final Logger LOG = Log.getLogger(DialogueVariablesLoader.class);

    @Override
    protected DialogueVariables fromJson(JsonObject json, String path) {
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

    @Override
    protected JsonObject toJson(DialogueVariables variables) {
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

    @Override
    protected DialogueVariables createPlaceholder() {
        return new DialogueVariables();
    }

    @Override
    protected String[] extensions() {
        return EXTENSIONS;
    }

    @Override
    protected String iconCodepoint() {
        return MaterialIcons.Code;
    }

    @Override
    protected void copyInto(DialogueVariables existing, DialogueVariables fresh) {
        existing.copyFrom(fresh);
    }
}
