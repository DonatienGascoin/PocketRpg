package com.pocket.rpg.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.components.animations.AnimationComponent;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.editor.EditorPanelType;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Asset loader for Animation files (.anim, .anim.json).
 * Handles JSON serialization and editor integration.
 */
public class AnimationLoader extends JsonAssetLoader<Animation> {

    private static final String[] EXTENSIONS = {".anim", ".anim.json"};

    // ========================================================================
    // JSON PARSING
    // ========================================================================

    @Override
    protected Animation fromJson(JsonObject json, String path) throws IOException {
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
    // JSON SERIALIZATION
    // ========================================================================

    @Override
    protected JsonObject toJson(Animation anim) {
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
    // JsonAssetLoader CONFIGURATION
    // ========================================================================

    @Override
    protected Animation createPlaceholder() {
        Animation placeholder = new Animation("placeholder");
        placeholder.setLooping(true);
        return placeholder;
    }

    @Override
    protected String[] extensions() {
        return EXTENSIONS;
    }

    @Override
    protected String iconCodepoint() {
        return MaterialIcons.Movie;
    }

    @Override
    protected void copyInto(Animation existing, Animation fresh) {
        existing.copyFrom(fresh);
    }

    @Override
    protected void beforeReloadCopy(Animation existing) {
        existing.invalidateCache();
    }

    @Override
    protected EditorPanelType editorPanelType() {
        return EditorPanelType.ASSET_EDITOR;
    }

    // ========================================================================
    // EDITOR INSTANTIATION
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
