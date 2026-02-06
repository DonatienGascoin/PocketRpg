package com.pocket.rpg.ui;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for components identified by a {@code componentKey}.
 * <p>
 * Any component with a non-null, non-blank {@code componentKey} is registered here
 * during scene loading. Game code retrieves components by key to interact with them.
 * <p>
 * Replaces the previous UIManager, which only supported UIComponent subclasses.
 * <p>
 * Example usage:
 * <pre>
 * // Get any component by key + type
 * AlphaGroup alpha = ComponentKeyRegistry.get("playerAlpha", AlphaGroup.class);
 *
 * // Convenience methods for UI components
 * UIText scoreText = ComponentKeyRegistry.getText("score");
 * if (scoreText != null) {
 *     scoreText.setText("Score: " + score);
 * }
 * </pre>
 */
public class ComponentKeyRegistry {

    private static final Map<String, Component> registry = new HashMap<>();
    private static final Map<String, UITransform> transformRegistry = new HashMap<>();

    private ComponentKeyRegistry() {
        // Static utility class
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers a component with a key.
     * Also caches the UITransform from the same GameObject if present.
     * Called automatically by Scene during component registration if componentKey is set.
     */
    public static void register(String key, Component component) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (registry.containsKey(key)) {
            System.err.println("[ComponentKeyRegistry] Warning: Overwriting existing key '" + key + "'");
        }
        registry.put(key, component);

        // Also cache UITransform from the same GameObject
        if (component.getGameObject() != null) {
            UITransform transform = component.getGameObject().getComponent(UITransform.class);
            if (transform != null) {
                transformRegistry.put(key, transform);
            }
        }
    }

    /**
     * Unregisters a component.
     */
    public static void unregister(String key) {
        if (key != null) {
            registry.remove(key);
            transformRegistry.remove(key);
        }
    }

    /**
     * Clears all registered components.
     * Call on scene unload.
     */
    public static void clear() {
        registry.clear();
        transformRegistry.clear();
    }

    // ========================================================================
    // RETRIEVAL
    // ========================================================================

    /**
     * Gets a component by key with type checking.
     *
     * @param key  The registered key
     * @param type Expected component type
     * @return The component, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public static <T extends Component> T get(String key, Class<T> type) {
        Component component = registry.get(key);
        if (component == null) {
            return null;
        }
        if (type.isInstance(component)) {
            return (T) component;
        }
        System.err.println("[ComponentKeyRegistry] Type mismatch for '" + key + "': expected " +
                type.getSimpleName() + ", got " + component.getClass().getSimpleName());
        return null;
    }

    /**
     * Gets any component by key.
     */
    public static Component get(String key) {
        return registry.get(key);
    }

    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================

    /**
     * Gets a UIText component by key.
     */
    public static UIText getText(String key) {
        return get(key, UIText.class);
    }

    /**
     * Gets a UIImage component by key.
     */
    public static UIImage getImage(String key) {
        return get(key, UIImage.class);
    }

    /**
     * Gets a UIPanel component by key.
     */
    public static UIPanel getPanel(String key) {
        return get(key, UIPanel.class);
    }

    /**
     * Gets a UIButton component by key.
     */
    public static UIButton getButton(String key) {
        return get(key, UIButton.class);
    }

    /**
     * Gets a UICanvas component by key.
     */
    public static UICanvas getCanvas(String key) {
        return get(key, UICanvas.class);
    }

    /**
     * Gets the cached UITransform for a key.
     * The transform is automatically cached when a component registers.
     */
    public static UITransform getUITransform(String key) {
        return transformRegistry.get(key);
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Checks if a key is registered.
     */
    public static boolean exists(String key) {
        return registry.containsKey(key);
    }

    /**
     * Gets count of registered components.
     */
    public static int getCount() {
        return registry.size();
    }

    /**
     * Gets count of registered transforms.
     */
    public static int getTransformCount() {
        return transformRegistry.size();
    }

    /**
     * Debug: prints all registered keys.
     */
    public static void debugPrint() {
        System.out.println("[ComponentKeyRegistry] Registered components (" + registry.size() + "):");
        for (Map.Entry<String, Component> entry : registry.entrySet()) {
            String key = entry.getKey();
            boolean hasTransform = transformRegistry.containsKey(key);
            System.out.println("  - " + key + " -> " +
                    entry.getValue().getClass().getSimpleName() +
                    (hasTransform ? " [+Transform]" : ""));
        }
    }
}
