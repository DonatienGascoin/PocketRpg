package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.ui.UIManager;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves @UiKeyReference fields at runtime after UIManager keys are registered.
 * <p>
 * Also serves as the pending key storage for the editor and serializer:
 * <ul>
 *   <li>The deserializer stores keys via {@link #storePendingKey}</li>
 *   <li>The editor reads/writes keys via {@link #getPendingKey} / {@link #storePendingKey}</li>
 *   <li>The runtime resolver consumes keys via {@link #consumePendingKey}</li>
 * </ul>
 * <p>
 * Works alongside {@link ComponentRefResolver} in the scene initialization lifecycle:
 * <ol>
 *   <li>Scene.onLoad() — components added, UIManager keys registered</li>
 *   <li>ComponentRefResolver.resolveReferences() — @ComponentRef resolved</li>
 *   <li>UiKeyRefResolver.resolveReferences() — @UiKeyReference resolved</li>
 *   <li>go.start() — onStart() called</li>
 * </ol>
 */
public class UiKeyRefResolver {

    /**
     * Pending keys: maps "identityHash.fieldName" → key string.
     * Populated by the deserializer during read, read by the editor for display,
     * consumed by the runtime resolver to inject UIComponent references.
     */
    private static final Map<String, String> pendingKeys = new HashMap<>();

    /**
     * Stores a pending key for a @UiKeyReference field.
     * Called by the deserializer when reading a component, and by the editor when the user
     * changes the dropdown selection.
     *
     * @param component The component instance
     * @param fieldName The @UiKeyReference field name
     * @param key       The UIManager key string (empty string or null to clear)
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
     * Gets the pending key for a @UiKeyReference field without removing it.
     * Used by the editor to display the current key in the dropdown.
     *
     * @param component The component instance
     * @param fieldName The @UiKeyReference field name
     * @return The stored key, or empty string if none
     */
    public static String getPendingKey(Component component, String fieldName) {
        return pendingKeys.getOrDefault(makeKey(component, fieldName), "");
    }

    /**
     * Clears all pending keys.
     * Called during scene destruction to prevent stale entries
     * when the same scene is reloaded (e.g., play mode restart).
     */
    public static void clearPendingKeys() {
        pendingKeys.clear();
    }

    private static String makeKey(Component component, String fieldName) {
        return System.identityHashCode(component) + "." + fieldName;
    }

    /**
     * Resolves all @UiKeyReference fields for all components on a GameObject.
     *
     * @param gameObject The GameObject to resolve references for
     */
    public static void resolveReferences(GameObject gameObject) {
        for (Component component : gameObject.getComponents()) {
            resolveComponentReferences(component);
        }
    }

    /**
     * Resolves all @UiKeyReference fields for a specific component.
     * <p>
     * Safe to call multiple times — uses non-destructive key read and skips
     * fields that are already resolved (non-null).
     */
    public static void resolveComponentReferences(Component component) {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());
        if (meta == null || meta.uiKeyRefs().isEmpty()) {
            return;
        }

        for (UiKeyRefMeta refMeta : meta.uiKeyRefs()) {
            try {
                // Skip if already resolved (e.g., from an earlier resolution pass)
                Field field = refMeta.field();
                field.setAccessible(true);
                if (field.get(component) != null) {
                    continue;
                }

                // Read the pending key (non-destructive — key stays for editor/serializer use)
                String key = getPendingKey(component, refMeta.fieldName());
                if (key.isEmpty()) {
                    if (refMeta.required()) {
                        System.err.println("Warning: @UiKeyReference field '" + refMeta.fieldName() +
                                "' has no key on " + component.getClass().getSimpleName());
                    }
                    continue;
                }

                // Resolve via UIManager
                UIComponent resolved = UIManager.get(key, refMeta.componentType());

                if (resolved == null && refMeta.required()) {
                    System.err.println("Warning: @UiKeyReference not resolved: " +
                            component.getClass().getSimpleName() + "." + refMeta.fieldName() +
                            " (key=\"" + key + "\", expected " + refMeta.componentType().getSimpleName() + ")");
                }

                // Inject into the target field
                field.set(component, resolved);

            } catch (Exception e) {
                System.err.println("Failed to resolve @UiKeyReference " +
                        refMeta.fieldName() + ": " + e.getMessage());
            }
        }
    }
}
