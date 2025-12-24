package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentRef;
import com.pocket.rpg.core.GameObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves @ComponentRef fields at runtime after the GameObject hierarchy is established.
 * <p>
 * Usage:
 * <pre>
 * // After loading a scene or instantiating a prefab:
 * for (GameObject go : allGameObjects) {
 *     ComponentRefResolver.resolveReferences(go);
 * }
 * </pre>
 */
public class ComponentRefResolver {

    /**
     * Resolves all @ComponentRef fields for all components on a GameObject.
     * Call this after the GameObject and its parent/children are fully set up.
     *
     * @param gameObject The GameObject to resolve references for
     */
    public static void resolveReferences(GameObject gameObject) {
        for (Component component : gameObject.getComponents()) {
            resolveComponentReferences(component, gameObject);
        }
    }

    /**
     * Resolves all @ComponentRef fields for a specific component.
     */
    public static void resolveComponentReferences(Component component, GameObject gameObject) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return;
        }

        for (ComponentRefMeta refMeta : meta.references()) {
            try {
                Object resolved = resolveReference(gameObject, refMeta);

                if (resolved == null && refMeta.required()) {
                    System.err.println("Warning: Required @ComponentRef not resolved: " +
                            component.getClass().getSimpleName() + "." + refMeta.name() +
                            " (looking for " + refMeta.componentType().getSimpleName() +
                            " in " + refMeta.source() + ")");
                }

                Field field = refMeta.field();
                field.setAccessible(true);
                field.set(component, resolved);

            } catch (Exception e) {
                System.err.println("Failed to resolve @ComponentRef " + refMeta.name() +
                        ": " + e.getMessage());
            }
        }
    }

    /**
     * Resolves a single component reference.
     */
    @SuppressWarnings("unchecked")
    private static Object resolveReference(GameObject gameObject, ComponentRefMeta refMeta) {
        Class<?> componentType = refMeta.componentType();

        return switch (refMeta.source()) {
            case SELF -> resolveSelf(gameObject, componentType, refMeta.isList());
            case PARENT -> resolveParent(gameObject, componentType, refMeta.isList());
            case CHILDREN -> resolveChildren(gameObject, componentType, refMeta.isList(), false);
            case CHILDREN_RECURSIVE -> resolveChildren(gameObject, componentType, refMeta.isList(), true);
        };
    }

    /**
     * Resolves from the same GameObject.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Component> Object resolveSelf(GameObject go, Class<?> type, boolean isList) {
        if (isList) {
            return go.getComponents((Class<T>) type);
        } else {
            return go.getComponent((Class<T>) type);
        }
    }

    /**
     * Resolves from parent GameObject.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Component> Object resolveParent(GameObject go, Class<?> type, boolean isList) {
        GameObject parent = go.getParent();
        if (parent == null) {
            return isList ? new ArrayList<>() : null;
        }

        if (isList) {
            return parent.getComponents((Class<T>) type);
        } else {
            return parent.getComponent((Class<T>) type);
        }
    }

    /**
     * Resolves from children GameObjects.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Component> Object resolveChildren(GameObject go, Class<?> type,
                                                                  boolean isList, boolean recursive) {
        List<T> results = new ArrayList<>();

        if (recursive) {
            collectFromDescendants(go, (Class<T>) type, results);
        } else {
            // Direct children only
            for (GameObject child : go.getChildren()) {
                T component = child.getComponent((Class<T>) type);
                if (component != null) {
                    if (!isList) {
                        return component; // Return first match
                    }
                    results.add(component);
                }
            }
        }

        if (isList) {
            return results;
        } else {
            return results.isEmpty() ? null : results.get(0);
        }
    }

    /**
     * Recursively collects components from all descendants.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Component> void collectFromDescendants(GameObject go, Class<T> type,
                                                                      List<T> results) {
        for (GameObject child : go.getChildren()) {
            T component = child.getComponent(type);
            if (component != null) {
                results.add(component);
            }
            collectFromDescendants(child, type, results);
        }
    }
}
