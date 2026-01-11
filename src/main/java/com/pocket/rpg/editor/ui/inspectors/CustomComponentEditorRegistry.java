package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.components.ui.*;
import com.pocket.rpg.editor.scene.EditorGameObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for custom component editors.
 * <p>
 * Custom editors are looked up by component type (full class name).
 * If no custom editor is registered, the default ReflectionFieldEditor is used.
 */
public class CustomComponentEditorRegistry {

    private static final Map<String, CustomComponentInspector> editors = new HashMap<>();

    /**
     * Registers a custom editor using the component class.
     *
     * @param componentClass The component class
     * @param editor         The custom editor implementation
     */
    public static void register(Class<?> componentClass, CustomComponentInspector editor) {
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
     * Draws the custom editor for a component if one exists.
     *
     * @param component The component
     * @param entity    The owning entity (may be null)
     * @return true if a field changed, false if no custom editor or no change
     */
    public static boolean drawCustomEditor(Component component, EditorGameObject entity) {
        CustomComponentInspector editor = editors.get(component.getClass().getName());
        if (editor == null) {
            return false;
        }
        return editor.draw(component, entity);
    }

    /**
     * Initializes built-in custom editors.
     * Call once at editor startup.
     */
    public static void initBuiltInEditors() {
        // UI Components
        register(UITransform.class, new UITransformInspector(true));
        register(UICanvas.class, new UICanvasInspector());
        register(UIImage.class, new UIImageInspector());
        register(UIPanel.class, new UIPanelInspector());
        register(UIButton.class, new UIButtonInspector());
        register(UIText.class, new UITextInspector());
        register(Transform.class, new TransformInspector());
    }

    /**
     * Clears all registered editors.
     * Mainly for testing.
     */
    public static void clear() {
        editors.clear();
    }
}
