package com.pocket.rpg.prefab;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import com.pocket.rpg.serialization.GameObjectData;
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
 * <p>
 * JSON prefabs support hierarchy via {@link #getGameObjects()}, where each
 * node is a {@link GameObjectData} with parentId references. Code-defined
 * prefabs typically only implement {@link #getComponents()} for the root entity.
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
     * Gets the root entity's component definitions.
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

    // ========================================================================
    // HIERARCHY SUPPORT
    // ========================================================================

    /**
     * Gets the full hierarchy as a flat list of GameObjectData nodes.
     * <p>
     * For JSON prefabs, this returns the stored {@code gameObjects} list.
     * For code-defined prefabs, returns null (they don't support hierarchy).
     */
    default List<GameObjectData> getGameObjects() {
        return null;
    }

    /**
     * Sets the hierarchy nodes. Only meaningful for JSON prefabs.
     */
    default void setGameObjects(List<GameObjectData> gameObjects) {
        // No-op for code-defined prefabs
    }

    /**
     * Returns whether this prefab has child nodes (depth > 1).
     */
    default boolean hasChildren() {
        return false;
    }

    /**
     * Returns the root node of the hierarchy.
     * For code-defined prefabs, returns null.
     */
    default GameObjectData getRootNode() {
        return null;
    }

    /**
     * Finds a node by its stable ID within the prefab hierarchy.
     *
     * @param nodeId the node's ID (from {@link GameObjectData#getId()})
     * @return the node, or null if not found
     */
    default GameObjectData findNode(String nodeId) {
        return null;
    }

    /**
     * Gets the default value for a specific field in a child node's component.
     *
     * @param nodeId        the child node's ID
     * @param componentType fully-qualified component class name
     * @param fieldName     field name
     * @return the default value, or null if not found
     */
    default Object getChildFieldDefault(String nodeId, String componentType, String fieldName) {
        GameObjectData node = findNode(nodeId);
        if (node == null || node.getComponents() == null) {
            return null;
        }
        for (Component comp : node.getComponents()) {
            if (comp.getClass().getName().equals(componentType)) {
                return ComponentReflectionUtils.getFieldValue(comp, fieldName);
            }
        }
        return null;
    }

    /**
     * Gets a deep copy of a node's components.
     *
     * @param nodeId the node's ID
     * @return cloned component list, or empty list if node not found
     */
    default List<Component> getNodeComponentsCopy(String nodeId) {
        GameObjectData node = findNode(nodeId);
        if (node == null || node.getComponents() == null) {
            return List.of();
        }
        List<Component> copies = new ArrayList<>();
        for (Component comp : node.getComponents()) {
            Component copy = ComponentReflectionUtils.cloneComponent(comp);
            if (copy != null) {
                copies.add(copy);
            }
        }
        return copies;
    }

    // ========================================================================
    // INSTANTIATION
    // ========================================================================

    /**
     * Creates a configured GameObject at the given position.
     * <p>
     * If the prefab has children, child GameObjects are also created and
     * parented to the root.
     */
    default GameObject instantiate(Vector3f position, Map<String, Map<String, Object>> overrides) {
        String name = getDisplayName() != null ? getDisplayName() : getId();
        GameObject gameObject = new GameObject(name, position);

        List<Component> components = getComponents();
        if (components == null) {
            return gameObject;
        }

        for (Component template : components) {
            // Handle Transform specially - use template directly, don't clone
            if (template instanceof Transform t) {
                Transform existing = gameObject.getTransform();
                existing.setLocalPosition(t.getLocalPosition());
                existing.setLocalRotation(t.getLocalRotation());
                existing.setLocalScale(t.getLocalScale());

                if (overrides != null) {
                    Map<String, Object> transformOverrides = overrides.get(Transform.class.getName());
                    if (transformOverrides != null && !transformOverrides.isEmpty()) {
                        applyOverrides(existing, transformOverrides);
                    }
                }
                continue;
            }

            // Clone other components
            Component component = ComponentReflectionUtils.cloneComponent(template);
            if (component == null) {
                System.err.println("Failed to clone component: " + template.getClass().getName());
                continue;
            }

            if (overrides != null) {
                Map<String, Object> fieldOverrides = overrides.get(component.getClass().getName());
                if (fieldOverrides != null && !fieldOverrides.isEmpty()) {
                    applyOverrides(component, fieldOverrides);
                }
            }

            gameObject.addComponent(component);
        }

        // Create child GameObjects if the prefab has hierarchy
        if (hasChildren()) {
            instantiateChildren(gameObject);
        }

        return gameObject;
    }

    /**
     * Creates child GameObjects from the prefab hierarchy and parents them to the root.
     */
    private void instantiateChildren(GameObject root) {
        List<GameObjectData> nodes = getGameObjects();
        if (nodes == null) return;

        GameObjectData rootNode = getRootNode();
        if (rootNode == null) return;

        // Build a map of prefab nodeId -> runtime GameObject
        java.util.Map<String, GameObject> nodeToGameObject = new java.util.HashMap<>();
        nodeToGameObject.put(rootNode.getId(), root);

        // Process nodes in order (parent-first since the list is ordered that way)
        for (GameObjectData node : nodes) {
            if (node == rootNode) continue;
            if (node.getParentId() == null) continue;

            GameObject parent = nodeToGameObject.get(node.getParentId());
            if (parent == null) {
                System.err.println("Prefab node '" + node.getName() + "' references unknown parent '" + node.getParentId() + "'");
                continue;
            }

            String childName = node.getName() != null ? node.getName() : "Child";
            GameObject child = new GameObject(childName);

            // Clone child node's components
            if (node.getComponents() != null) {
                for (Component template : node.getComponents()) {
                    if (template instanceof Transform t) {
                        Transform existing = child.getTransform();
                        existing.setLocalPosition(t.getLocalPosition());
                        existing.setLocalRotation(t.getLocalRotation());
                        existing.setLocalScale(t.getLocalScale());
                        continue;
                    }
                    Component clone = ComponentReflectionUtils.cloneComponent(template);
                    if (clone != null) {
                        child.addComponent(clone);
                    }
                }
            }

            parent.addChild(child);
            if (node.getId() != null) {
                nodeToGameObject.put(node.getId(), child);
            }
        }
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
     * Gets the default value for a specific field in the root entity's components.
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
     * Gets a deep copy of the root entity's components.
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
