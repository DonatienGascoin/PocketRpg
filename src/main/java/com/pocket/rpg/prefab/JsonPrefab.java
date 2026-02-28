package com.pocket.rpg.prefab;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.GameObjectData;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A prefab loaded from a JSON file.
 * <p>
 * JSON prefabs are data-driven and can be created/modified without recompiling.
 * They store a hierarchy of entities via a flat {@code gameObjects} list with
 * {@code parentId} references, using the same format as scene files.
 * <p>
 * JSON format:
 * <pre>
 * {
 *   "id": "guard_tower",
 *   "displayName": "Guard Tower",
 *   "category": "Structures",
 *   "gameObjects": [
 *     {
 *       "id": "root0001",
 *       "name": "Guard Tower",
 *       "components": [...]
 *     },
 *     {
 *       "id": "guard001",
 *       "name": "Guard",
 *       "parentId": "root0001",
 *       "order": 0,
 *       "components": [...]
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
    private List<GameObjectData> gameObjects;

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
     * Initializes with a single root GameObjectData node.
     */
    public JsonPrefab(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        // Create default root node with generated ID
        GameObjectData root = new GameObjectData(UUID.randomUUID().toString().substring(0, 8), displayName, new ArrayList<>());
        this.gameObjects = new ArrayList<>();
        this.gameObjects.add(root);
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

    /**
     * Returns the root node's components for backward compatibility.
     * <p>
     * Code that only needs root-level components (e.g. code-defined prefab callers,
     * PrefabEditController.save()) can continue using this method.
     */
    @Override
    public List<Component> getComponents() {
        GameObjectData root = getRootNode();
        if (root == null) {
            return List.of();
        }
        List<Component> comps = root.getComponents();
        return comps != null ? comps : List.of();
    }

    /**
     * Sets the root node's components.
     * <p>
     * Convenience method for callers that only deal with root-level components
     * (e.g. PrefabEditController.save(), SavePrefabPopup). Preserves any
     * existing child nodes.
     */
    public void setComponents(List<Component> components) {
        GameObjectData root = getRootNode();
        if (root != null) {
            root.setComponents(components != null ? new ArrayList<>(components) : new ArrayList<>());
        } else {
            // No root node yet — create one
            root = new GameObjectData(null, getDisplayName(), components != null ? new ArrayList<>(components) : new ArrayList<>());
            if (gameObjects == null) {
                gameObjects = new ArrayList<>();
            }
            gameObjects.addFirst(root);
        }
    }

    /**
     * Adds a component to the root node.
     */
    public void addComponent(Component component) {
        GameObjectData root = getRootNode();
        if (root == null) {
            root = new GameObjectData(null, getDisplayName(), new ArrayList<>());
            if (gameObjects == null) {
                gameObjects = new ArrayList<>();
            }
            gameObjects.addFirst(root);
        }
        root.addComponent(component);
    }

    @Override
    public List<GameObjectData> getGameObjects() {
        return gameObjects;
    }

    @Override
    public void setGameObjects(List<GameObjectData> gameObjects) {
        this.gameObjects = gameObjects;
    }

    /**
     * Returns the root node (the entry with null parentId).
     */
    @Override
    public GameObjectData getRootNode() {
        if (gameObjects == null || gameObjects.isEmpty()) {
            return null;
        }
        for (GameObjectData node : gameObjects) {
            if (node.getParentId() == null || node.getParentId().isEmpty()) {
                return node;
            }
        }
        return null;
    }

    @Override
    public boolean hasChildren() {
        if (gameObjects == null || gameObjects.size() <= 1) {
            return false;
        }
        for (GameObjectData node : gameObjects) {
            if (node.getParentId() != null && !node.getParentId().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public GameObjectData findNode(String nodeId) {
        if (nodeId == null || gameObjects == null) {
            return null;
        }
        for (GameObjectData node : gameObjects) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    @Override
    public Sprite getPreviewSprite() {
        if (!previewLoaded) {
            loadPreviewSprite();
        }
        return cachedPreviewSprite;
    }

    /**
     * Clears the cached preview sprite so it will be regenerated on next access.
     */
    public void clearPreviewCache() {
        cachedPreviewSprite = null;
        previewLoaded = false;
    }

    private void loadPreviewSprite() {
        previewLoaded = true;

        // Tier 1: explicit preview sprite path
        if (previewSpritePath != null && !previewSpritePath.isEmpty()) {
            try {
                cachedPreviewSprite = Assets.load(previewSpritePath, Sprite.class);
            } catch (Exception e) {
                System.err.println("Failed to load preview sprite for prefab " + id + ": " + e.getMessage());
                cachedPreviewSprite = null;
            }
        }

        // Tier 2: search all nodes for first SpriteRenderer
        if (cachedPreviewSprite == null && gameObjects != null) {
            for (GameObjectData node : gameObjects) {
                Sprite found = findSpriteInComponents(node.getComponents());
                if (found != null) {
                    cachedPreviewSprite = found;
                    break;
                }
            }
        }
    }

    private Sprite findSpriteInComponents(List<Component> components) {
        if (components == null) return null;
        for (Component comp : components) {
            if (comp instanceof SpriteRenderer sr) {
                Sprite sprite = sr.getSprite();
                if (sprite != null) return sprite;
            } else if (comp.getClass().getSimpleName().equals("SpriteRenderer")) {
                Object sprite = ComponentReflectionUtils.getFieldValue(comp, "sprite");
                if (sprite instanceof Sprite s) return s;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        int nodeCount = gameObjects != null ? gameObjects.size() : 0;
        return "JsonPrefab[id=" + id + ", displayName=" + displayName +
               ", nodes=" + nodeCount + ", hasChildren=" + hasChildren() + "]";
    }
}
