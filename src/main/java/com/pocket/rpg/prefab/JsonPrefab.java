package com.pocket.rpg.prefab;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
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
 *       "properties": {
 *         "sprite": "com.pocket.rpg.rendering.resources.Sprite:sprites/chest.png",
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
    private List<Component> components = new ArrayList<>();

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
    public List<Component> getComponents() {
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
            for (Component comp : components) {
                if (comp instanceof SpriteRenderer sr) {
                    Sprite sprite = sr.getSprite();
                    if (sprite != null) {
                        cachedPreviewSprite = sprite;
                        break;
                    }
                } else if (comp.getClass().getSimpleName().equals("SpriteRenderer")) {
                    // Fallback using reflection if not the exact class
                    Object sprite = ComponentReflectionUtils.getFieldValue(comp, "sprite");
                    if (sprite instanceof Sprite s) {
                        cachedPreviewSprite = s;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Adds a component to this prefab.
     */
    public void addComponent(Component component) {
        if (components == null) {
            components = new ArrayList<>();
        }
        components.add(component);
    }

    @Override
    public String toString() {
        return "JsonPrefab[id=" + id + ", displayName=" + displayName + 
               ", components=" + (components != null ? components.size() : 0) + "]";
    }
}
