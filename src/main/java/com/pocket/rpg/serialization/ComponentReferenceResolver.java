package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.ui.ComponentKeyRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@code @ComponentReference} fields at runtime.
 * <p>
 * Runs two internal passes in a single {@link #resolveAll} call:
 * <ol>
 *   <li><b>Pass 1 — Hierarchy refs</b> (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE):
 *       resolved from the GameObject tree.</li>
 *   <li><b>Pass 2 — Key refs</b> (KEY): resolved from
 *       {@link ComponentKeyRegistry}.</li>
 * </ol>
 * <p>
 * Also serves as the pending key storage for the editor and serializer:
 * <ul>
 *   <li>The deserializer stores keys via {@link #storePendingKey}</li>
 *   <li>The editor reads/writes keys via {@link #getPendingKey} / {@link #storePendingKey}</li>
 *   <li>The runtime resolver consumes keys during resolution</li>
 * </ul>
 */
public class ComponentReferenceResolver {

    /**
     * Pending keys: maps "identityHash.fieldName" → key string.
     * Populated by the deserializer during read, read by the editor for display,
     * consumed by the runtime resolver to inject component references.
     */
    private static final Map<String, String> pendingKeys = new HashMap<>();

    /**
     * Pending key lists: maps "identityHash.fieldName" → list of key strings.
     * Used for List fields with KEY source.
     */
    private static final Map<String, List<String>> pendingKeyLists = new HashMap<>();

    // ========================================================================
    // PENDING KEY STORAGE
    // ========================================================================

    /**
     * Stores a pending key for a {@code @ComponentReference(source = KEY)} field.
     * Called by the deserializer when reading a component, and by the editor when
     * the user changes the dropdown selection.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @param key       The componentKey string (empty string or null to clear)
     */
    public static void storePendingKey(Component component, String fieldName, String key) {
        String mapKey = makeKey(component, fieldName);
        if (key == null || key.isEmpty()) {
            pendingKeys.remove(mapKey);
        } else {
            pendingKeys.put(mapKey, key);
        }
    }

    /**
     * Gets the pending key for a field without removing it.
     * Used by the editor to display the current key in the dropdown.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @return The stored key, or empty string if none
     */
    public static String getPendingKey(Component component, String fieldName) {
        return pendingKeys.getOrDefault(makeKey(component, fieldName), "");
    }

    /**
     * Stores a pending key list for a {@code @ComponentReference(source = KEY)} List field.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @param keys      The list of componentKey strings (null or empty to clear)
     */
    public static void storePendingKeyList(Component component, String fieldName, List<String> keys) {
        String mapKey = makeKey(component, fieldName);
        if (keys == null || keys.isEmpty()) {
            pendingKeyLists.remove(mapKey);
        } else {
            pendingKeyLists.put(mapKey, new ArrayList<>(keys));
        }
    }

    /**
     * Gets the pending key list for a List field without removing it.
     *
     * @param component The component instance
     * @param fieldName The field name
     * @return The stored key list, or empty list if none
     */
    public static List<String> getPendingKeyList(Component component, String fieldName) {
        return pendingKeyLists.getOrDefault(makeKey(component, fieldName), List.of());
    }

    /**
     * Clears all pending keys.
     * Called during scene destruction to prevent stale entries.
     */
    public static void clearPendingKeys() {
        pendingKeys.clear();
        pendingKeyLists.clear();
    }

    private static String makeKey(Component component, String fieldName) {
        return System.identityHashCode(component) + "." + fieldName;
    }

    // ========================================================================
    // RESOLUTION
    // ========================================================================

    /**
     * Resolves all {@code @ComponentReference} fields for all components on a GameObject.
     * Runs hierarchy refs first, then key refs.
     *
     * @param gameObject The GameObject to resolve references for
     */
    public static void resolveAll(GameObject gameObject) {
        for (Component component : gameObject.getComponents()) {
            resolveComponentReferences(component, gameObject);
        }
    }

    /**
     * Resolves all {@code @ComponentReference} fields for a specific component.
     */
    public static void resolveComponentReferences(Component component, GameObject gameObject) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null) {
            return;
        }

        // Pass 1: Hierarchy refs
        for (ComponentReferenceMeta refMeta : meta.componentReferences()) {
            if (refMeta.isHierarchySource()) {
                resolveHierarchyRef(component, gameObject, refMeta);
            }
        }

        // Pass 2: Key refs
        for (ComponentReferenceMeta refMeta : meta.componentReferences()) {
            if (refMeta.isKeySource()) {
                resolveKeyRef(component, refMeta);
            }
        }
    }

    // ========================================================================
    // HIERARCHY RESOLUTION
    // ========================================================================

    private static void resolveHierarchyRef(Component component, GameObject gameObject,
                                             ComponentReferenceMeta refMeta) {
        try {
            Object resolved = resolveFromHierarchy(gameObject, refMeta);

            if (resolved == null && refMeta.required()) {
                System.err.println("Warning: Required @ComponentReference not resolved: " +
                        component.getClass().getSimpleName() + "." + refMeta.fieldName() +
                        " (looking for " + refMeta.componentType().getSimpleName() +
                        " in " + refMeta.source() + ")");
            }

            Field field = refMeta.field();
            field.setAccessible(true);
            field.set(component, resolved);

        } catch (Exception e) {
            System.err.println("Failed to resolve @ComponentReference " + refMeta.fieldName() +
                    ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object resolveFromHierarchy(GameObject gameObject, ComponentReferenceMeta refMeta) {
        Class<?> componentType = refMeta.componentType();

        return switch (refMeta.source()) {
            case SELF -> resolveSelf(gameObject, componentType, refMeta.isList());
            case PARENT -> resolveParent(gameObject, componentType, refMeta.isList());
            case CHILDREN -> resolveChildren(gameObject, componentType, refMeta.isList(), false);
            case CHILDREN_RECURSIVE -> resolveChildren(gameObject, componentType, refMeta.isList(), true);
            case KEY -> throw new IllegalStateException("KEY source should not reach hierarchy resolver");
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> Object resolveSelf(GameObject go, Class<?> type, boolean isList) {
        if (isList) {
            return go.getComponents((Class<T>) type);
        } else {
            return go.getComponent((Class<T>) type);
        }
    }

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

    @SuppressWarnings("unchecked")
    private static <T extends Component> Object resolveChildren(GameObject go, Class<?> type,
                                                                  boolean isList, boolean recursive) {
        List<T> results = new ArrayList<>();

        if (recursive) {
            collectFromDescendants(go, (Class<T>) type, results);
        } else {
            for (GameObject child : go.getChildren()) {
                T component = child.getComponent((Class<T>) type);
                if (component != null) {
                    if (!isList) {
                        return component;
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

    // ========================================================================
    // KEY RESOLUTION
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static void resolveKeyRef(Component component, ComponentReferenceMeta refMeta) {
        try {
            Field field = refMeta.field();
            field.setAccessible(true);

            // Skip if already resolved
            if (field.get(component) != null) {
                return;
            }

            if (refMeta.isList()) {
                resolveKeyListRef(component, refMeta, field);
            } else {
                resolveKeySingleRef(component, refMeta, field);
            }

        } catch (Exception e) {
            System.err.println("Failed to resolve @ComponentReference(source=KEY) " +
                    refMeta.fieldName() + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void resolveKeySingleRef(Component component, ComponentReferenceMeta refMeta,
                                             Field field) throws IllegalAccessException {
        String key = getPendingKey(component, refMeta.fieldName());
        if (key.isEmpty()) {
            if (refMeta.required()) {
                System.err.println("Warning: @ComponentReference(source=KEY) field '" +
                        refMeta.fieldName() + "' has no key on " +
                        component.getClass().getSimpleName());
            }
            return;
        }

        Component resolved = ComponentKeyRegistry.get(key, refMeta.componentType());

        if (resolved == null && refMeta.required()) {
            System.err.println("Warning: @ComponentReference(source=KEY) not resolved: " +
                    component.getClass().getSimpleName() + "." + refMeta.fieldName() +
                    " (key=\"" + key + "\", expected " + refMeta.componentType().getSimpleName() + ")");
        }

        field.set(component, resolved);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> void resolveKeyListRef(Component component,
                                                                 ComponentReferenceMeta refMeta,
                                                                 Field field) throws IllegalAccessException {
        List<String> keys = getPendingKeyList(component, refMeta.fieldName());
        if (keys.isEmpty()) {
            field.set(component, new ArrayList<>());
            return;
        }

        List<T> results = new ArrayList<>();
        Class<T> type = (Class<T>) refMeta.componentType();

        for (String key : keys) {
            T resolved = ComponentKeyRegistry.get(key, type);
            if (resolved != null) {
                results.add(resolved);
            } else if (refMeta.required()) {
                System.err.println("Warning: @ComponentReference(source=KEY) list entry not resolved: " +
                        component.getClass().getSimpleName() + "." + refMeta.fieldName() +
                        " (key=\"" + key + "\", expected " + type.getSimpleName() + ")");
            }
        }

        field.set(component, results);
    }
}
