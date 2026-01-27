package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.animation.AnimatorLayoutData;
import com.pocket.rpg.animation.AnimatorLayoutData.NodeLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Loader for animator layout files (.animator.layout.json).
 * <p>
 * Layout files store node positions and view state for the animator editor
 * and are saved alongside the main .animator.json file.
 */
public class AnimatorLayoutLoader {

    private static final String LAYOUT_EXTENSION = ".animator.layout.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // ========================================================================
    // LOADING
    // ========================================================================

    /**
     * Loads layout data from the companion layout file.
     *
     * @param animatorPath Path to the main .animator.json file
     * @return Loaded layout data, or new empty layout if file doesn't exist
     */
    public AnimatorLayoutData load(String animatorPath) {
        String layoutPath = getLayoutPath(animatorPath);
        Path path = Paths.get(layoutPath);

        if (!Files.exists(path)) {
            return new AnimatorLayoutData();
        }

        try {
            String jsonContent = Files.readString(path);
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            return fromJSON(json);
        } catch (Exception e) {
            System.err.println("[AnimatorLayoutLoader] Failed to load layout: " + e.getMessage());
            return new AnimatorLayoutData();
        }
    }

    private AnimatorLayoutData fromJSON(JsonObject json) {
        AnimatorLayoutData layout = new AnimatorLayoutData();

        if (json.has("viewPanX")) {
            layout.setViewPanX(json.get("viewPanX").getAsFloat());
        }
        if (json.has("viewPanY")) {
            layout.setViewPanY(json.get("viewPanY").getAsFloat());
        }
        if (json.has("viewZoom")) {
            layout.setViewZoom(json.get("viewZoom").getAsFloat());
        }

        if (json.has("nodeLayouts") && json.get("nodeLayouts").isJsonObject()) {
            JsonObject nodesObj = json.getAsJsonObject("nodeLayouts");
            for (Map.Entry<String, JsonElement> entry : nodesObj.entrySet()) {
                String stateName = entry.getKey();
                JsonObject nodeJson = entry.getValue().getAsJsonObject();

                NodeLayout nodeLayout = new NodeLayout();
                if (nodeJson.has("x")) {
                    nodeLayout.setX(nodeJson.get("x").getAsFloat());
                }
                if (nodeJson.has("y")) {
                    nodeLayout.setY(nodeJson.get("y").getAsFloat());
                }
                if (nodeJson.has("collapsed")) {
                    nodeLayout.setCollapsed(nodeJson.get("collapsed").getAsBoolean());
                }

                layout.getNodeLayouts().put(stateName, nodeLayout);
            }
        }

        return layout;
    }

    // ========================================================================
    // SAVING
    // ========================================================================

    /**
     * Saves layout data to the companion layout file.
     *
     * @param animatorPath Path to the main .animator.json file
     * @param layout       Layout data to save
     */
    public void save(String animatorPath, AnimatorLayoutData layout) throws IOException {
        String layoutPath = getLayoutPath(animatorPath);

        try {
            JsonObject json = toJSON(layout);
            String jsonString = gson.toJson(json);

            Path filePath = Paths.get(layoutPath);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(filePath, jsonString);
        } catch (Exception e) {
            throw new IOException("Failed to save animator layout: " + layoutPath, e);
        }
    }

    private JsonObject toJSON(AnimatorLayoutData layout) {
        JsonObject json = new JsonObject();

        json.addProperty("viewPanX", layout.getViewPanX());
        json.addProperty("viewPanY", layout.getViewPanY());
        json.addProperty("viewZoom", layout.getViewZoom());

        JsonObject nodesObj = new JsonObject();
        for (Map.Entry<String, NodeLayout> entry : layout.getNodeLayouts().entrySet()) {
            JsonObject nodeJson = new JsonObject();
            NodeLayout nodeLayout = entry.getValue();
            nodeJson.addProperty("x", nodeLayout.getX());
            nodeJson.addProperty("y", nodeLayout.getY());
            nodeJson.addProperty("collapsed", nodeLayout.isCollapsed());
            nodesObj.add(entry.getKey(), nodeJson);
        }
        json.add("nodeLayouts", nodesObj);

        return json;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Gets the layout file path for a given animator file path.
     */
    public static String getLayoutPath(String animatorPath) {
        // Replace .animator.json with .animator.layout.json
        if (animatorPath.endsWith(".animator.json")) {
            return animatorPath.replace(".animator.json", LAYOUT_EXTENSION);
        }
        return animatorPath + LAYOUT_EXTENSION;
    }

    /**
     * Checks if a layout file exists for the given animator path.
     */
    public static boolean layoutExists(String animatorPath) {
        String layoutPath = getLayoutPath(animatorPath);
        return Files.exists(Paths.get(layoutPath));
    }

    /**
     * Deletes the layout file for the given animator path.
     */
    public static void deleteLayout(String animatorPath) {
        String layoutPath = getLayoutPath(animatorPath);
        try {
            Files.deleteIfExists(Paths.get(layoutPath));
        } catch (IOException e) {
            System.err.println("[AnimatorLayoutLoader] Failed to delete layout: " + e.getMessage());
        }
    }
}
