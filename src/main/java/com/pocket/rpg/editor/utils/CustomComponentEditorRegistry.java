package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for custom component editors.
 * <p>
 * Custom editors are looked up by component type (full class name).
 * If no custom editor is registered, the default ReflectionFieldEditor is used.
 */
public class CustomComponentEditorRegistry {

    private static final Map<String, CustomComponentEditor> editors = new HashMap<>();

    /**
     * Registers a custom editor for a component type.
     *
     * @param componentType Full class name (e.g., "com.pocket.rpg.components.ui.UITransform")
     * @param editor        The custom editor implementation
     */
    public static void register(String componentType, CustomComponentEditor editor) {
        editors.put(componentType, editor);
    }

    /**
     * Registers a custom editor using the component class.
     *
     * @param componentClass The component class
     * @param editor         The custom editor implementation
     */
    public static void register(Class<?> componentClass, CustomComponentEditor editor) {
        editors.put(componentClass.getName(), editor);
    }

    /**
     * Checks if a custom editor is registered for the given component type.
     *
     * @param componentType Full class name
     * @return true if a custom editor exists
     */
    public static boolean hasCustomEditor(String componentType) {
        return editors.containsKey(componentType);
    }

    /**
     * Gets the custom editor for a component type.
     *
     * @param componentType Full class name
     * @return The custom editor, or null if none registered
     */
    public static CustomComponentEditor getEditor(String componentType) {
        return editors.get(componentType);
    }

    /**
     * Draws the custom editor for a component if one exists.
     *
     * @param data   The component data
     * @param entity The owning entity (may be null)
     * @return true if a field changed, false if no custom editor or no change
     */
    public static boolean drawCustomEditor(ComponentData data, EditorEntity entity) {
        CustomComponentEditor editor = editors.get(data.getType());
        if (editor == null) {
            return false;
        }
        return editor.draw(data, entity);
    }

    /**
     * Initializes built-in custom editors.
     * Call once at editor startup.
     */
    public static void initBuiltInEditors() {
        // UI Components
        register("com.pocket.rpg.components.ui.UITransform", new UITransformEditor());
        register("com.pocket.rpg.components.ui.UICanvas", new UICanvasEditor());
        register("com.pocket.rpg.components.ui.UIImage", new UIImageEditor());
        register("com.pocket.rpg.components.ui.UIPanel", new UIPanelEditor());
        register("com.pocket.rpg.components.ui.UIButton", new UIButtonEditor());
        register("com.pocket.rpg.components.ui.UIText", new UITextEditor());
    }

    /**
     * Clears all registered editors.
     * Mainly for testing.
     */
    public static void clear() {
        editors.clear();
    }
}
