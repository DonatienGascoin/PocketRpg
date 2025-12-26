package com.pocket.rpg.prefab;

import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentData;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A prefab loaded from a JSON file.
 * <p>
 * JSON prefabs are data-driven and can be created/modified without recompiling.
 * They store a list of components with their default field values.
 * <p>
 * JSON format:
 * <pre>
 * {
 *   "id": "chest",
 *   "displayName": "Treasure Chest",
 *   "category": "Interactables",
 *   "previewSpritePath": "sprites/chest.png",
 *   "components": [
 *     {
 *       "type": "com.pocket.rpg.components.SpriteRenderer",
 *       "fields": {
 *         "spritePath": "sprites/chest.png",
 *         "zIndex": 10
 *       }
 *     }
 *   ]
 * }
 * </pre>
 */
@Getter
@Setter
public class JsonPrefab implements Prefab {

    // Serialized fields (loaded from JSON)
    private String id;
    private String displayName;
    private String category;
    private String previewSpritePath;
    private List<ComponentData> components = new ArrayList<>();

    // Runtime fields (not serialized)
    private transient Sprite cachedPreviewSprite;
    private transient boolean previewLoaded = false;
    private transient String sourcePath;  // Path to the JSON file

    /**
     * Default constructor for Gson deserialization.
     */
    public JsonPrefab() {
    }

    /**
     * Creates a new JSON prefab with the given ID.
     */
    public JsonPrefab(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public List<ComponentData> getComponents() {
        return components;
    }

    @Override
    public Sprite getPreviewSprite() {
        if (!previewLoaded) {
            loadPreviewSprite();
        }
        return cachedPreviewSprite;
    }

    private void loadPreviewSprite() {
        previewLoaded = true;

        if (previewSpritePath != null && !previewSpritePath.isEmpty()) {
            try {
                cachedPreviewSprite = Assets.load(previewSpritePath, Sprite.class);
            } catch (Exception e) {
                System.err.println("Failed to load preview sprite for prefab " + id + ": " + e.getMessage());
                cachedPreviewSprite = null;
            }
        }

        // Fallback: try to get sprite from SpriteRenderer component
        if (cachedPreviewSprite == null && components != null) {
            for (ComponentData comp : components) {
                if (comp.getSimpleName().equals("SpriteRenderer")) {
                    Object spritePath = comp.getFields().get("spritePath");
                    if (spritePath instanceof String path && !path.isEmpty()) {
                        try {
                            cachedPreviewSprite = Assets.load(path, Sprite.class);
                        } catch (Exception ignored) {
                        }
                    }
                    break;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "JsonPrefab[id=" + id + ", displayName=" + displayName + 
               ", components=" + (components != null ? components.size() : 0) + "]";
    }
}
