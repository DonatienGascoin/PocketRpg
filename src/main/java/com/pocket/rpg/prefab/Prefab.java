package com.pocket.rpg.prefab;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import com.pocket.rpg.serialization.SerializationUtils;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prefab defines a template for creating configured GameObjects.
 * <p>
 * Prefabs are component-based: they define a list of components with
 * default field values. Instances can override individual field values.
 */
public interface Prefab {

    /**
     * Gets the unique identifier for this prefab.
     */
    String getId();

    /**
     * Gets the human-readable display name for the editor UI.
     */
    String getDisplayName();

    /**
     * Gets the component definitions for this prefab.
     * <p>
     * Each Component contains default field values.
     *
     * @return List of components (empty if none)
     */
    List<Component> getComponents();

    /**
     * Gets the preview sprite for editor display.
     */
    Sprite getPreviewSprite();

    /**
     * Gets the category for grouping in the prefab browser.
     */
    default String getCategory() {
        return null;
    }

    /**
     * Creates a configured GameObject at the given position.
     */
    default GameObject instantiate(Vector3f position, Map<String, Map<String, Object>> overrides) {
        String name = getDisplayName() != null ? getDisplayName() : getId();
        GameObject gameObject = new GameObject(name, position);

        List<Component> components = getComponents();
        if (components == null) {
            return gameObject;
        }

        for (Component template : components) {
            // Clone the component so each instance is independent
            Component component = ComponentReflectionUtils.cloneComponent(template);
            if (component == null) {
                System.err.println("Failed to clone component: " + template.getClass().getName());
                continue;
            }

            // Apply overrides
            if (overrides != null) {
                Map<String, Object> fieldOverrides = overrides.get(component.getClass().getName());
                if (fieldOverrides != null && !fieldOverrides.isEmpty()) {
                    applyOverrides(component, fieldOverrides);
                }
            }

            gameObject.addComponent(component);
        }

        return gameObject;
    }

    /**
     * Applies field overrides to a component instance.
     */
    private static void applyOverrides(Component component, Map<String, Object> overrides) {
        ComponentMeta meta = ComponentReflectionUtils.getMeta(component);
        if (meta == null) {
            return;
        }

        for (FieldMeta fieldMeta : meta.fields()) {
            Object override = overrides.get(fieldMeta.name());
            if (override != null) {
                try {
                    Field field = fieldMeta.field();
                    field.setAccessible(true);
                    Object converted = SerializationUtils.fromSerializable(override, field.getType());
                    field.set(component, converted);
                } catch (Exception e) {
                    System.err.println("Failed to apply override for " + fieldMeta.name() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Gets the default value for a specific field in a component.
     */
    default Object getFieldDefault(String componentType, String fieldName) {
        List<Component> components = getComponents();
        if (components == null) {
            return null;
        }

        for (Component comp : components) {
            if (comp.getClass().getName().equals(componentType)) {
                return ComponentReflectionUtils.getFieldValue(comp, fieldName);
            }
        }
        return null;
    }

    /**
     * Gets a deep copy of all components.
     */
    default List<Component> getComponentsCopy() {
        List<Component> components = getComponents();
        if (components == null) {
            return List.of();
        }

        List<Component> copies = new ArrayList<>();
        for (Component comp : components) {
            Component copy = ComponentReflectionUtils.cloneComponent(comp);
            if (copy != null) {
                copies.add(copy);
            }
        }
        return copies;
    }
}
