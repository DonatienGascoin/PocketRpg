package com.pocket.rpg.ui;

import com.pocket.rpg.components.ui.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for UI components.
 * <p>
 * Components register themselves using a unique key (set in editor via uiKey field).
 * Game code retrieves components by key to update them.
 * <p>
 * Example usage:
 * <pre>
 * // Get and update
 * UIText scoreText = UIManager.getText("score");
 * if (scoreText != null) {
 *     scoreText.setText("Score: " + score);
 * }
 *
 * // Get panel for tweening
 * UIPanel menu = UIManager.getPanel("main_menu");
 * if (menu != null) {
 *     Tween.offset(menu, new Vector2f(0, 0), 0.3f).setEase(Ease.OUT_BACK);
 * }
 * </pre>
 */
public class UIManager {

    private static final Map<String, UIComponent> registry = new HashMap<>();
    private static final Map<String, UITransform> transformRegistry = new HashMap<>();

    private UIManager() {
        // Static utility class
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers a UI component with a key.
     * Also caches the UITransform from the same GameObject if present.
     * Called automatically by UIComponent.onStart() if uiKey is set.
     */
    public static void register(String key, UIComponent component) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (registry.containsKey(key)) {
            System.err.println("[UIManager] Warning: Overwriting existing key '" + key + "'");
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
     * Unregisters a UI component.
     * Called automatically by UIComponent.onDestroy().
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
    public static <T extends UIComponent> T get(String key, Class<T> type) {
        UIComponent component = registry.get(key);
        if (component == null) {
            return null;
        }
        if (type.isInstance(component)) {
            return (T) component;
        }
        System.err.println("[UIManager] Type mismatch for '" + key + "': expected " +
                type.getSimpleName() + ", got " + component.getClass().getSimpleName());
        return null;
    }

    /**
     * Gets any UIComponent by key.
     */
    public static UIComponent get(String key) {
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
     * The transform is automatically cached when a UIComponent registers.
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
        System.out.println("[UIManager] Registered components (" + registry.size() + "):");
        for (Map.Entry<String, UIComponent> entry : registry.entrySet()) {
            String key = entry.getKey();
            boolean hasTransform = transformRegistry.containsKey(key);
            System.out.println("  - " + key + " -> " +
                    entry.getValue().getClass().getSimpleName() +
                    (hasTransform ? " [+Transform]" : ""));
        }
    }
}