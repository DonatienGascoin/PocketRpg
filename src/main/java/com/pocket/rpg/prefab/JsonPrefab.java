package com.pocket.rpg.prefab;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A prefab loaded from a JSON file.
 * <p>
 * Unlike Java prefabs, JSON prefabs are data-driven and can be
 * created/modified without recompiling.
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
    private List<JsonPropertyDefinition> properties = new ArrayList<>();

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
    public List<PropertyDefinition> getEditableProperties() {
        List<PropertyDefinition> result = new ArrayList<>();

        if (properties != null) {
            for (JsonPropertyDefinition jsonProp : properties) {
                result.add(jsonProp.toPropertyDefinition());
            }
        }

        return result;
    }

    @Override
    public GameObject instantiate(Vector3f position, Map<String, Object> overrides) {
        String name = displayName != null ? displayName : id;
        GameObject gameObject = new GameObject(name, position);

        // Instantiate each component
        if (components != null) {
            for (ComponentData compData : components) {
                Component component = compData.toComponent();
                if (component != null) {
                    // Apply property overrides to component fields
                    applyOverrides(component, compData, overrides);
                    gameObject.addComponent(component);
                }
            }
        }

        return gameObject;
    }

    /**
     * Applies property overrides to a component.
     * Maps prefab property names to component field names.
     */
    private void applyOverrides(Component component, ComponentData compData, Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }

        // For now, overrides are applied by matching property names to field names
        // A more sophisticated system could map property names to specific component fields
        var meta = ComponentRegistry.getByClassName(compData.getType());
        if (meta == null) {
            return;
        }

        for (var fieldMeta : meta.fields()) {
            Object override = overrides.get(fieldMeta.name());
            if (override != null) {
                try {
                    var field = fieldMeta.field();
                    field.setAccessible(true);
                    field.set(component, override);
                } catch (Exception e) {
                    System.err.println("Failed to apply override for " + fieldMeta.name() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Gets a copy of the component data list.
     */
    public List<ComponentData> getComponentsCopy() {
        List<ComponentData> copy = new ArrayList<>();
        if (components != null) {
            for (ComponentData comp : components) {
                ComponentData compCopy = new ComponentData(comp.getType());
                compCopy.getFields().putAll(comp.getFields());
                copy.add(compCopy);
            }
        }
        return copy;
    }

    /**
     * Gets the default value for a property.
     */
    public Object getPropertyDefault(String propertyName) {
        if (properties == null) {
            return null;
        }

        for (JsonPropertyDefinition prop : properties) {
            if (prop.getName().equals(propertyName)) {
                return prop.getDefaultValue();
            }
        }

        return null;
    }

    /**
     * Gets all default property values as a map.
     */
    public Map<String, Object> getDefaultProperties() {
        Map<String, Object> defaults = new java.util.HashMap<>();

        if (properties != null) {
            for (JsonPropertyDefinition prop : properties) {
                defaults.put(prop.getName(), prop.getDefaultValue());
            }
        }

        return defaults;
    }

    @Override
    public String toString() {
        return "JsonPrefab[id=" + id + ", displayName=" + displayName + "]";
    }
}

/**
 * JSON-serializable property definition.
 */
@Getter
@Setter
class JsonPropertyDefinition {
    private String name;
    private String type;  // STRING, INT, FLOAT, BOOLEAN, VECTOR2, VECTOR3, ASSET_REF
    private Object defaultValue;
    private String tooltip;

    public PropertyDefinition toPropertyDefinition() {
        PropertyType propType;
        try {
            propType = PropertyType.valueOf(type);
        } catch (Exception e) {
            propType = PropertyType.STRING;
        }

        return new PropertyDefinition(name, propType, defaultValue, tooltip);
    }
}