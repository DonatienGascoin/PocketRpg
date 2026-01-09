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
     * Each ComponentData contains the component type and default field values.
     *
     * @return List of component data (empty if none)
     */
    List<ComponentData> getComponents();

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
     * Gets live Component instances from this prefab's component definitions.
     * <p>
     * Creates new Component instances each call - use for cloning/merging.
     *
     * @return List of Component instances with default values applied
     */
    default List<Component> getComponentInstances() {
        List<ComponentData> componentData = getComponents();
        if (componentData == null) {
            return List.of();
        }

        List<Component> result = new ArrayList<>();
        for (ComponentData data : componentData) {
            Component component = data.toComponent();
            if (component != null) {
                result.add(component);
            }
        }
        return result;
    }

    /**
     * Creates a configured GameObject at the given position.
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

            System.out.println("DEBUG: Applying overrides: " + overrides);
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
                    Object converted = ComponentData.fromSerializable(override, field.getType());
                    field.set(component, converted);
                    System.out.println("DEBUG: Set field " + field.getName() + " = " + converted);
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
     * Gets a copy of component data.
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