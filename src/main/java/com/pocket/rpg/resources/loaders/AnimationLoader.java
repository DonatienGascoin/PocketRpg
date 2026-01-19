package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.components.AnimationComponent;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.EditorPanel;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.AssetLoader;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Asset loader for Animation files (.anim, .anim.json).
 * Handles JSON serialization and editor integration.
 */
public class AnimationLoader implements AssetLoader<Animation> {

    private static final String[] EXTENSIONS = {".anim", ".anim.json"};
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Animation placeholder;

    // ========================================================================
    // LOADING
    // ========================================================================

    @Override
    public Animation load(String path) throws IOException {
        try {
            String jsonContent = Files.readString(Paths.get(path));
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            return fromJSON(json, path);
        } catch (Exception e) {
            throw new IOException("Failed to load animation: " + path, e);
        }
    }

    private Animation fromJSON(JsonObject json, String path) throws IOException {
        Animation anim = new Animation();

        // Required: name
        if (!json.has("name") || json.get("name").isJsonNull()) {
            throw new IOException("Animation missing required field 'name': " + path);
        }
        anim.setName(json.get("name").getAsString());

        // Required: looping
        if (!json.has("looping")) {
            throw new IOException("Animation missing required field 'looping': " + path);
        }
        anim.setLooping(json.get("looping").getAsBoolean());

        // Required: frames (must have at least one)
        if (!json.has("frames") || !json.get("frames").isJsonArray()) {
            throw new IOException("Animation missing required field 'frames': " + path);
        }

        JsonArray framesArray = json.getAsJsonArray("frames");
        if (framesArray.isEmpty()) {
            throw new IOException("Animation must have at least one frame: " + path);
        }

        for (int i = 0; i < framesArray.size(); i++) {
            JsonObject frameJson = framesArray.get(i).getAsJsonObject();

            // Required: sprite
            if (!frameJson.has("sprite")) {
                throw new IOException("Frame " + i + " missing required field 'sprite': " + path);
            }
            String spritePath = frameJson.get("sprite").getAsString();

            // Required: duration
            if (!frameJson.has("duration")) {
                throw new IOException("Frame " + i + " missing required field 'duration': " + path);
            }
            float duration = frameJson.get("duration").getAsFloat();

            anim.addFrame(new AnimationFrame(spritePath, duration));
        }

        return anim;
    }

    // ========================================================================
    // SAVING
    // ========================================================================

    @Override
    public void save(Animation animation, String path) throws IOException {
        try {
            JsonObject json = toJSON(animation);
            String jsonString = gson.toJson(json);

            Path filePath = Paths.get(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(filePath, jsonString);
        } catch (Exception e) {
            throw new IOException("Failed to save animation: " + path, e);
        }
    }

    private JsonObject toJSON(Animation anim) {
        JsonObject json = new JsonObject();

        json.addProperty("name", anim.getName());
        json.addProperty("looping", anim.isLooping());

        JsonArray framesArray = new JsonArray();
        for (AnimationFrame frame : anim.getFrames()) {
            JsonObject frameJson = new JsonObject();
            frameJson.addProperty("sprite", frame.spritePath());
            frameJson.addProperty("duration", frame.duration());
            framesArray.add(frameJson);
        }
        json.add("frames", framesArray);

        return json;
    }

    // ========================================================================
    // ASSET LOADER INTERFACE
    // ========================================================================

    @Override
    public Animation getPlaceholder() {
        if (placeholder == null) {
            placeholder = new Animation("placeholder");
            placeholder.setLooping(true);
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
    public Animation reload(Animation existing, String path) throws IOException {
        existing.invalidateCache();
        Animation reloaded = load(path);
        existing.copyFrom(reloaded);
        return existing;
    }

    // ========================================================================
    // EDITOR INTEGRATION
    // ========================================================================

    @Override
    public boolean canInstantiate() {
        return true;
    }

    @Override
    public EditorGameObject instantiate(Animation asset, String assetPath, Vector3f position) {
        String baseName = asset.getName() != null ? asset.getName() : extractEntityName(assetPath);

        EditorGameObject entity = new EditorGameObject(baseName, position, false);

        // Add SpriteRenderer with first frame
        SpriteRenderer spriteRenderer = new SpriteRenderer();
        if (asset.getFrameCount() > 0) {
            spriteRenderer.setSprite(asset.getFrameSprite(0));
        }
        entity.addComponent(spriteRenderer);

        // Add AnimationComponent
        AnimationComponent animComponent = new AnimationComponent();
        animComponent.setAnimation(asset);
        animComponent.setAutoPlay(true);
        entity.addComponent(animComponent);

        return entity;
    }

    @Override
    public Sprite getPreviewSprite(Animation asset) {
        if (asset != null && asset.getFrameCount() > 0) {
            return asset.getFrameSprite(0);
        }
        return null;
    }

    @Override
    public String getIconCodepoint() {
        return FontAwesomeIcons.Film;
    }

    @Override
    public EditorPanel getEditorPanel() {
        return EditorPanel.ANIMATION_EDITOR;
    }

    private String extractEntityName(String path) {
        String name = Paths.get(path).getFileName().toString();
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        return name;
    }
}
