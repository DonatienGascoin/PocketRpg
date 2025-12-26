package com.pocket.rpg.prefab;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Prefab defines a template for creating configured GameObjects.
 * <p>
 * Prefabs are component-based: they define a list of components with
 * default field values. Instances can override individual field values.
 * <p>
 * Example implementation:
 * <pre>
 * public class ChestPrefab implements Prefab {
 *     public String getId() { return "chest"; }
 *     public String getDisplayName() { return "Treasure Chest"; }
 *
 *     public List&lt;ComponentData&gt; getComponents() {
 *         ComponentData renderer = new ComponentData("com.pocket.rpg.components.SpriteRenderer");
 *         renderer.getFields().put("spritePath", "sprites/chest.png");
 *         renderer.getFields().put("zIndex", 10);
 *         return List.of(renderer);
 *     }
 * }
 * </pre>
 */
public interface Prefab {

    /**
     * Gets the unique identifier for this prefab.
     * <p>
     * This ID is used for serialization and registry lookup.
     * Should be lowercase with underscores (e.g., "npc_villager", "chest_wooden").
     *
     * @return Unique prefab ID
     */
    String getId();

    /**
     * Gets the human-readable display name for the editor UI.
     *
     * @return Display name (e.g., "Villager NPC", "Wooden Chest")
     */
    String getDisplayName();

    /**
     * Gets the component definitions for this prefab.
     * <p>
     * Each ComponentData contains the component type and default field values.
     * These are used both for instantiation and for the editor inspector.
     *
     * @return List of component data (empty if none)
     */
    List<ComponentData> getComponents();

    /**
     * Gets the preview sprite for editor display.
     * <p>
     * This sprite is shown in:
     * - Prefab browser panel
     * - Entity placer tool ghost preview
     * - Hierarchy panel icon (optional)
     *
     * @return Preview sprite, or null to use default placeholder
     */
    Sprite getPreviewSprite();

    /**
     * Gets the category for grouping in the prefab browser.
     * <p>
     * Examples: "Characters", "Props", "Interactables", "Environment"
     *
     * @return Category name, or null for uncategorized
     */
    default String getCategory() {
        return null;
    }

    /**
     * Creates a configured GameObject at the given position.
     * <p>
     * Default implementation creates components from getComponents()
     * and applies any field overrides.
     *
     * @param position  World position for the entity
     * @param overrides Field overrides by component type: Map&lt;componentType, Map&lt;fieldName, value&gt;&gt;
     * @return Fully configured GameObject ready for scene
     */
    default GameObject instantiate(Vector3f position, Map<String, Map<String, Object>> overrides) {
        String name = getDisplayName() != null ? getDisplayName() : getId();
        GameObject gameObject = new GameObject(name, position);

        List<ComponentData> components = getComponents();
        if (components == null) {
            return gameObject;
        }

        for (ComponentData compData : components) {
            Component component = compData.toComponent();
            if (component == null) {
                System.err.println("Failed to instantiate component: " + compData.getType());
                continue;
            }

            // Apply overrides for this component type
            if (overrides != null) {
                Map<String, Object> fieldOverrides = overrides.get(compData.getType());
                if (fieldOverrides != null && !fieldOverrides.isEmpty()) {
                    applyOverrides(component, compData.getType(), fieldOverrides);
                }
            }

            gameObject.addComponent(component);
        }

        return gameObject;
    }

    /**
     * Applies field overrides to a component instance.
     */
    private static void applyOverrides(Component component, String componentType, Map<String, Object> overrides) {
        ComponentMeta meta = ComponentRegistry.getByClassName(componentType);
        if (meta == null) {
            return;
        }

        for (FieldMeta fieldMeta : meta.fields()) {
            Object override = overrides.get(fieldMeta.name());
            if (override != null) {
                try {
                    Field field = fieldMeta.field();
                    field.setAccessible(true);
                    field.set(component, override);
                } catch (Exception e) {
                    System.err.println("Failed to apply override for " + fieldMeta.name() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Gets the default value for a specific field in a component.
     *
     * @param componentType Full class name of the component
     * @param fieldName     Name of the field
     * @return Default value, or null if not found
     */
    default Object getFieldDefault(String componentType, String fieldName) {
        List<ComponentData> components = getComponents();
        if (components == null) {
            return null;
        }

        for (ComponentData comp : components) {
            if (comp.getType().equals(componentType)) {
                return comp.getFields().get(fieldName);
            }
        }
        return null;
    }

    /**
     * Gets a copy of component data, useful for creating instances in the editor.
     */
    default List<ComponentData> getComponentsCopy() {
        List<ComponentData> components = getComponents();
        if (components == null) {
            return List.of();
        }

        return components.stream()
                .map(comp -> {
                    ComponentData copy = new ComponentData(comp.getType());
                    copy.getFields().putAll(comp.getFields());
                    return copy;
                })
                .toList();
    }
}
