package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry for custom component editors.
 * <p>
 * Custom editors are looked up by component type (full class name).
 * If no custom editor is registered, the default ReflectionFieldEditor is used.
 * <p>
 * The registry manages inspector lifecycle through bind/unbind to cache
 * component casts and avoid per-frame overhead.
 */
public class CustomComponentEditorRegistry {

    private static final Map<String, CustomComponentInspector<?>> editors = new HashMap<>();

    // Track currently bound inspector to properly unbind when selection changes
    private static CustomComponentInspector<?> currentInspector = null;
    private static Component currentComponent = null;

    /**
     * Registers a custom editor using the component class.
     *
     * @param componentClass The component class
     * @param editor         The custom editor implementation
     */
    public static void register(Class<?> componentClass, CustomComponentInspector<?> editor) {
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
     * Handles bind/unbind automatically when component changes.
     *
     * @param component The component
     * @param entity    The owning entity (may be null)
     * @return true if a field changed, false if no custom editor or no change
     */
    public static boolean drawCustomEditor(Component component, EditorGameObject entity) {
        CustomComponentInspector<?> editor = editors.get(component.getClass().getName());
        if (editor == null) {
            // No custom editor - unbind any previous inspector
            if (currentInspector != null) {
                currentInspector.unbind();
                currentInspector = null;
                currentComponent = null;
            }
            return false;
        }

        // Check if component changed - need to rebind
        if (currentInspector != editor || currentComponent != component) {
            // Unbind previous inspector
            if (currentInspector != null) {
                currentInspector.unbind();
            }

            // Bind new inspector
            editor.bind(component, entity);
            currentInspector = editor;
            currentComponent = component;
        }

        // Set up context for @Required and override styling
        // Preserve existing scene context if set
        FieldEditorContext.begin(entity, component, FieldEditorContext.getCurrentScene());
        try {
            // Draw the inspector
            return editor.draw();
        } finally {
            FieldEditorContext.end();
        }
    }

    /**
     * Unbinds the current inspector.
     * Call when selection is cleared or inspector panel is closed.
     */
    public static void unbindCurrent() {
        if (currentInspector != null) {
            currentInspector.unbind();
            currentInspector = null;
            currentComponent = null;
        }
    }

    /**
     * Initializes built-in custom editors.
     * Call once at editor startup.
     * <p>
     * Automatically discovers and registers all inspectors annotated with {@link InspectorFor}.
     */
    public static void initBuiltInEditors() {
        scanAndRegisterInspectors();
    }

    /**
     * Scans the entire classpath for classes annotated with @InspectorFor
     * and registers them automatically.
     * <p>
     * Custom inspectors can be defined in any package under com.pocket.rpg.
     */
    private static void scanAndRegisterInspectors() {
        try {
            // Scan all packages under com.pocket.rpg for @InspectorFor annotations
            Reflections reflections = new Reflections("com.pocket.rpg");
            Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(InspectorFor.class);

            for (Class<?> clazz : annotatedClasses) {
                registerIfAnnotated(clazz);
            }

            System.out.println("Inspector scanning complete. Found " + annotatedClasses.size() + " custom inspectors.");

        } catch (Exception e) {
            System.err.println("Error scanning for inspectors: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Registers a class if it has the @InspectorFor annotation.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerIfAnnotated(Class<?> clazz) {
        InspectorFor annotation = clazz.getAnnotation(InspectorFor.class);
        if (annotation == null) return;

        if (!CustomComponentInspector.class.isAssignableFrom(clazz)) {
            System.err.println("@InspectorFor class must extend CustomComponentInspector: " + clazz.getName());
            return;
        }

        Class<? extends Component> componentClass = annotation.value();

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            CustomComponentInspector<?> inspector = (CustomComponentInspector<?>) constructor.newInstance();
            register(componentClass, inspector);
            System.out.println("Registered inspector: " + clazz.getSimpleName() + " for " + componentClass.getSimpleName());
        } catch (NoSuchMethodException e) {
            System.err.println("@InspectorFor class must have a no-arg constructor: " + clazz.getName());
        } catch (Exception e) {
            System.err.println("Failed to instantiate inspector: " + clazz.getName() + " - " + e.getMessage());
        }
    }

    /**
     * Clears all registered editors.
     * Mainly for testing.
     */
    public static void clear() {
        unbindCurrent();
        editors.clear();
    }
}
