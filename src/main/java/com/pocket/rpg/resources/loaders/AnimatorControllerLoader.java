package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.AssetLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Asset loader for AnimatorController files (.animator.json).
 * Handles JSON serialization and editor integration.
 */
public class AnimatorControllerLoader implements AssetLoader<AnimatorController> {

    private static final String[] EXTENSIONS = {".animator.json"};
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static AnimatorController placeholder;

    // ========================================================================
    // LOADING
    // ========================================================================

    @Override
    public AnimatorController load(String path) throws IOException {
        try {
            String jsonContent = Files.readString(Paths.get(path));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            return fromJSON(json, path);
        } catch (Exception e) {
            throw new IOException("Failed to load animator controller: " + path, e);
        }
    }

    private AnimatorController fromJSON(JsonObject json, String path) throws IOException {
        AnimatorController controller = new AnimatorController();

        // Name
        if (json.has("name") && !json.get("name").isJsonNull()) {
            controller.setName(json.get("name").getAsString());
        }

        // Default state
        if (json.has("defaultState") && !json.get("defaultState").isJsonNull()) {
            controller.setDefaultState(json.get("defaultState").getAsString());
        }

        // Parameters
        if (json.has("parameters") && json.get("parameters").isJsonArray()) {
            JsonArray paramsArray = json.getAsJsonArray("parameters");
            for (JsonElement elem : paramsArray) {
                controller.addParameter(parseParameter(elem.getAsJsonObject()));
            }
        }

        // States
        if (json.has("states") && json.get("states").isJsonArray()) {
            JsonArray statesArray = json.getAsJsonArray("states");
            for (JsonElement elem : statesArray) {
                controller.getStates().add(parseState(elem.getAsJsonObject()));
            }
        }

        // Transitions
        if (json.has("transitions") && json.get("transitions").isJsonArray()) {
            JsonArray transArray = json.getAsJsonArray("transitions");
            for (JsonElement elem : transArray) {
                controller.getTransitions().add(parseTransition(elem.getAsJsonObject()));
            }
        }

        return controller;
    }

    private AnimatorParameter parseParameter(JsonObject json) {
        AnimatorParameter param = new AnimatorParameter();

        param.setName(json.has("name") ? json.get("name").getAsString() : "");

        String typeStr = json.has("type") ? json.get("type").getAsString().toUpperCase() : "BOOL";
        param.setType(ParameterType.valueOf(typeStr));

        // Parse default value based on type
        if (json.has("default")) {
            JsonElement defaultElem = json.get("default");
            switch (param.getType()) {
                case BOOL, TRIGGER -> param.setDefaultValue(defaultElem.getAsBoolean());
                case DIRECTION -> {
                    String dirStr = defaultElem.getAsString().toUpperCase();
                    param.setDefaultValue(Direction.valueOf(dirStr));
                }
            }
        } else {
            // Set type-appropriate default
            switch (param.getType()) {
                case BOOL, TRIGGER -> param.setDefaultValue(false);
                case DIRECTION -> param.setDefaultValue(Direction.DOWN);
            }
        }

        return param;
    }

    private AnimatorState parseState(JsonObject json) {
        AnimatorState state = new AnimatorState();

        state.setName(json.has("name") ? json.get("name").getAsString() : "");

        String typeStr = json.has("type") ? json.get("type").getAsString().toUpperCase() : "SIMPLE";
        state.setType(StateType.valueOf(typeStr));

        // Simple animation
        if (json.has("animation") && !json.get("animation").isJsonNull()) {
            state.setAnimation(json.get("animation").getAsString());
        }

        // Directional animations
        if (json.has("animations") && json.get("animations").isJsonObject()) {
            JsonObject animsObj = json.getAsJsonObject("animations");
            for (Direction dir : Direction.values()) {
                String key = dir.name();
                if (animsObj.has(key) && !animsObj.get(key).isJsonNull()) {
                    state.setDirectionalAnimation(dir, animsObj.get(key).getAsString());
                }
            }
        }

        return state;
    }

    private AnimatorTransition parseTransition(JsonObject json) {
        AnimatorTransition trans = new AnimatorTransition();

        trans.setFrom(json.has("from") ? json.get("from").getAsString() : "");
        trans.setTo(json.has("to") ? json.get("to").getAsString() : "");

        String typeStr = json.has("type") ? json.get("type").getAsString().toUpperCase() : "INSTANT";
        trans.setType(TransitionType.valueOf(typeStr));

        // Conditions
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            JsonArray condsArray = json.getAsJsonArray("conditions");
            List<TransitionCondition> conditions = new ArrayList<>();
            for (JsonElement elem : condsArray) {
                conditions.add(parseCondition(elem.getAsJsonObject()));
            }
            trans.setConditions(conditions);
        }

        return trans;
    }

    private TransitionCondition parseCondition(JsonObject json) {
        TransitionCondition cond = new TransitionCondition();

        cond.setParameter(json.has("parameter") ? json.get("parameter").getAsString() : "");

        if (json.has("value")) {
            JsonElement valueElem = json.get("value");
            if (valueElem.isJsonPrimitive()) {
                if (valueElem.getAsJsonPrimitive().isBoolean()) {
                    cond.setValue(valueElem.getAsBoolean());
                } else if (valueElem.getAsJsonPrimitive().isString()) {
                    // Could be a Direction or other string value
                    cond.setValue(valueElem.getAsString());
                } else if (valueElem.getAsJsonPrimitive().isNumber()) {
                    cond.setValue(valueElem.getAsNumber());
                }
            }
        }

        return cond;
    }

    // ========================================================================
    // SAVING
    // ========================================================================

    @Override
    public void save(AnimatorController controller, String path) throws IOException {
        try {
            JsonObject json = toJSON(controller);
            String jsonString = gson.toJson(json);

            Path filePath = Paths.get(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(filePath, jsonString);
        } catch (Exception e) {
            throw new IOException("Failed to save animator controller: " + path, e);
        }
    }

    private JsonObject toJSON(AnimatorController controller) {
        JsonObject json = new JsonObject();

        json.addProperty("name", controller.getName());
        json.addProperty("defaultState", controller.getDefaultState());

        // Parameters
        JsonArray paramsArray = new JsonArray();
        for (AnimatorParameter param : controller.getParameters()) {
            paramsArray.add(parameterToJSON(param));
        }
        json.add("parameters", paramsArray);

        // States
        JsonArray statesArray = new JsonArray();
        for (AnimatorState state : controller.getStates()) {
            statesArray.add(stateToJSON(state));
        }
        json.add("states", statesArray);

        // Transitions
        JsonArray transArray = new JsonArray();
        for (AnimatorTransition trans : controller.getTransitions()) {
            transArray.add(transitionToJSON(trans));
        }
        json.add("transitions", transArray);

        return json;
    }

    private JsonObject parameterToJSON(AnimatorParameter param) {
        JsonObject json = new JsonObject();
        json.addProperty("name", param.getName());
        json.addProperty("type", param.getType().name().toLowerCase());

        Object defaultVal = param.getDefaultValue();
        if (defaultVal instanceof Boolean) {
            json.addProperty("default", (Boolean) defaultVal);
        } else if (defaultVal instanceof Direction) {
            json.addProperty("default", ((Direction) defaultVal).name());
        } else if (defaultVal != null) {
            json.addProperty("default", defaultVal.toString());
        }

        return json;
    }

    private JsonObject stateToJSON(AnimatorState state) {
        JsonObject json = new JsonObject();
        json.addProperty("name", state.getName());
        json.addProperty("type", state.getType().name().toLowerCase());

        if (state.getType() == StateType.SIMPLE) {
            if (state.getAnimation() != null) {
                json.addProperty("animation", state.getAnimation());
            }
        } else if (state.getType() == StateType.DIRECTIONAL) {
            JsonObject animsObj = new JsonObject();
            for (Direction dir : Direction.values()) {
                String path = state.getDirectionalAnimation(dir);
                if (path != null) {
                    animsObj.addProperty(dir.name(), path);
                }
            }
            json.add("animations", animsObj);
        }

        return json;
    }

    private JsonObject transitionToJSON(AnimatorTransition trans) {
        JsonObject json = new JsonObject();
        json.addProperty("from", trans.getFrom());
        json.addProperty("to", trans.getTo());
        json.addProperty("type", trans.getType().name());

        if (trans.hasConditions()) {
            JsonArray condsArray = new JsonArray();
            for (TransitionCondition cond : trans.getConditions()) {
                condsArray.add(conditionToJSON(cond));
            }
            json.add("conditions", condsArray);
        }

        return json;
    }

    private JsonObject conditionToJSON(TransitionCondition cond) {
        JsonObject json = new JsonObject();
        json.addProperty("parameter", cond.getParameter());

        Object value = cond.getValue();
        if (value instanceof Boolean) {
            json.addProperty("value", (Boolean) value);
        } else if (value instanceof Direction) {
            json.addProperty("value", ((Direction) value).name());
        } else if (value instanceof Number) {
            json.addProperty("value", (Number) value);
        } else if (value != null) {
            json.addProperty("value", value.toString());
        }

        return json;
    }

    // ========================================================================
    // ASSET LOADER INTERFACE
    // ========================================================================

    @Override
    public AnimatorController getPlaceholder() {
        if (placeholder == null) {
            placeholder = new AnimatorController("placeholder");
            AnimatorState idle = new AnimatorState("idle", StateType.SIMPLE);
            placeholder.addState(idle);
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
    public AnimatorController reload(AnimatorController existing, String path) throws IOException {
        AnimatorController reloaded = load(path);
        existing.copyFrom(reloaded);
        return existing;
    }

    // ========================================================================
    // EDITOR INTEGRATION
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return false; // Animator controllers aren't dropped into scenes directly
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.AccountTree;
    }

    @Override
    public EditorPanelType getEditorPanelType() {
        return EditorPanelType.ANIMATOR_EDITOR;
    }
}
