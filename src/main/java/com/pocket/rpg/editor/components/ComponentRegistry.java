package com.pocket.rpg.editor.components;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.HideInInspector;
import com.pocket.rpg.components.Transform;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;

/**
 * Registry of all available components.
 * Scans the components package at startup and caches metadata.
 * <p>
 * Usage:
 * ComponentRegistry.initialize();
 * List<ComponentMeta> all = ComponentRegistry.getAll();
 * Component instance = ComponentRegistry.instantiate("SpriteRenderer");
 */
public class ComponentRegistry {

    private static final String COMPONENTS_PACKAGE = "com.pocket.rpg.components";

    private static Map<String, ComponentMeta> bySimpleName = new HashMap<>();
    private static Map<String, ComponentMeta> byFullName = new HashMap<>();
    private static List<ComponentMeta> allComponents = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * Initializes the registry by scanning the components package.
     * Call once at editor startup.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        System.out.println("ComponentRegistry: Scanning for components...");

        try {
            List<Class<? extends Component>> classes = scanComponentClasses();

            for (Class<? extends Component> clazz : classes) {
                // Skip abstract classes and Transform (special case)
                if (Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }
                if (clazz == Transform.class) {
                    continue; // Transform is auto-added, not user-addable
                }
                if (clazz == Component.class) {
                    continue;
                }

                ComponentMeta meta = buildMeta(clazz);
                bySimpleName.put(meta.simpleName(), meta);
                byFullName.put(meta.className(), meta);
                allComponents.add(meta);

                System.out.println("  Registered: " + meta.simpleName() +
                        " (" + meta.fields().size() + " fields" +
                        (meta.hasNoArgConstructor() ? "" : ", NO DEFAULT CONSTRUCTOR") + ")");
            }

            // Sort alphabetically
            allComponents.sort(Comparator.comparing(ComponentMeta::simpleName));

            initialized = true;
            System.out.println("ComponentRegistry: Found " + allComponents.size() + " components");

        } catch (Exception e) {
            System.err.println("ComponentRegistry: Failed to scan components");
            e.printStackTrace();
            initialized = true; // Don't retry
        }
    }

    /**
     * Gets all registered components.
     */
    public static List<ComponentMeta> getAll() {
        return Collections.unmodifiableList(allComponents);
    }

    /**
     * Gets all components that can be instantiated (have no-arg constructor).
     */
    public static List<ComponentMeta> getInstantiable() {
        return allComponents.stream()
                .filter(ComponentMeta::hasNoArgConstructor)
                .toList();
    }

    /**
     * Gets component metadata by simple name (e.g., "SpriteRenderer").
     */
    public static ComponentMeta getBySimpleName(String simpleName) {
        return bySimpleName.get(simpleName);
    }

    /**
     * Gets component metadata by full class name.
     */
    public static ComponentMeta getByClassName(String className) {
        return byFullName.get(className);
    }

    /**
     * Creates a new instance of a component by simple name.
     * Returns null if component not found or can't be instantiated.
     */
    public static Component instantiate(String simpleName) {
        ComponentMeta meta = bySimpleName.get(simpleName);
        if (meta == null || !meta.hasNoArgConstructor()) {
            return null;
        }

        try {
            Constructor<? extends Component> constructor =
                    meta.componentClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            System.err.println("Failed to instantiate " + simpleName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new instance from a full class name.
     */
    public static Component instantiateByClassName(String className) {
        ComponentMeta meta = byFullName.get(className);
        if (meta == null) {
            return null;
        }
        return instantiate(meta.simpleName());
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Scans the components package for Component subclasses.
     */
    private static List<Class<? extends Component>> scanComponentClasses() throws Exception {
        List<Class<? extends Component>> result = new ArrayList<>();

        String packagePath = COMPONENTS_PACKAGE.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        Enumeration<URL> resources = classLoader.getResources(packagePath);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File directory = new File(resource.toURI());

            if (directory.exists()) {
                scanDirectory(directory, COMPONENTS_PACKAGE, result);
            }
        }

        return result;
    }

    /**
     * Recursively scans a directory for .class files.
     */
    @SuppressWarnings("unchecked")
    private static void scanDirectory(File directory, String packageName,
                                      List<Class<? extends Component>> result) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Recurse into subdirectory
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                // Load class
                String className = packageName + "." +
                        file.getName().substring(0, file.getName().length() - 6);

                try {
                    Class<?> clazz = Class.forName(className);
                    if (Component.class.isAssignableFrom(clazz) && clazz != Component.class) {
                        result.add((Class<? extends Component>) clazz);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // Skip classes that can't be loaded
                }
            }
        }
    }

    /**
     * Builds metadata for a component class.
     */
    private static ComponentMeta buildMeta(Class<? extends Component> clazz) {
        String className = clazz.getName();
        String simpleName = clazz.getSimpleName();
        String displayName = ComponentMeta.toDisplayName(simpleName);

        // Check for no-arg constructor
        boolean hasNoArgConstructor = false;
        try {
            clazz.getDeclaredConstructor();
            hasNoArgConstructor = true;
        } catch (NoSuchMethodException e) {
            // No no-arg constructor
        }

        // Collect editable fields
        List<FieldMeta> fields = new ArrayList<>();
        collectFields(clazz, fields);

        return new ComponentMeta(
                className,
                simpleName,
                displayName,
                clazz,
                fields,
                hasNoArgConstructor
        );
    }

    /**
     * Collects all editable fields from a class and its superclasses.
     * Stops at Component base class.
     */
    private static void collectFields(Class<?> clazz, List<FieldMeta> fields) {
        if (clazz == null || clazz == Component.class || clazz == Object.class) {
            return;
        }

        // Process superclass first (parent fields appear first)
        collectFields(clazz.getSuperclass(), fields);

        // Process this class's fields
        for (Field field : clazz.getDeclaredFields()) {
            // Skip static fields
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            // Skip transient fields
            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            // Skip @HideInInspector fields
            if (field.isAnnotationPresent(HideInInspector.class)) {
                continue;
            }
            // Skip fields named "gameObject" or "started" (from Component base)
            if (field.getName().equals("gameObject") ||
                    field.getName().equals("started") ||
                    field.getName().equals("enabled")) {
                continue;
            }

            // Get default value if possible
            Object defaultValue = getDefaultValue(field.getType());

            fields.add(new FieldMeta(
                    field.getName(),
                    field.getType(),
                    field,
                    defaultValue
            ));
        }
    }

    /**
     * Gets a sensible default value for a field type.
     */
    private static Object getDefaultValue(Class<?> type) {
        if (type == int.class || type == Integer.class) return 0;
        if (type == float.class || type == Float.class) return 0f;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == String.class) return "";
        return null;
    }
}