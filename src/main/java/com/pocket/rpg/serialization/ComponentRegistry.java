package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentRef;
import com.pocket.rpg.components.HideInInspector;
import com.pocket.rpg.components.Transform;

import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

/**
 * Registry of all available components.
 * Scans the components package at startup and caches metadata.
 * <p>
 * Supports categorized access for UI menus via getCategories().
 */
public class ComponentRegistry {

    private static final String COMPONENTS_PACKAGE = "com.pocket.rpg.components";

    private static Map<String, ComponentMeta> bySimpleName = new HashMap<>();
    private static Map<String, ComponentMeta> byFullName = new HashMap<>();
    private static List<ComponentMeta> allComponents = new ArrayList<>();
    private static Map<String, ComponentCategory> categories = new LinkedHashMap<>();
    private static boolean initialized = false;

    /**
     * Initializes the registry by scanning the components package.
     * Call once at startup.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        System.out.println("ComponentRegistry: Scanning for components...");

        try {
            List<Class<? extends Component>> classes = scanComponentClasses();

            for (Class<? extends Component> clazz : classes) {
                if (Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }
                if (clazz == Transform.class) {
                    continue;
                }
                if (clazz == Component.class) {
                    continue;
                }

                ComponentMeta meta = buildMeta(clazz);
                bySimpleName.put(meta.simpleName(), meta);
                byFullName.put(meta.className(), meta);
                allComponents.add(meta);

                // Add to category
                String categoryName = ComponentCategory.extractCategory(meta.className());
                ComponentCategory category = categories.computeIfAbsent(
                        categoryName,
                        name -> new ComponentCategory(name, ComponentCategory.toDisplayName(name))
                );
                category.add(meta);

                System.out.println("  Registered: " + meta.simpleName() +
                        " [" + categoryName + "] (" + meta.fields().size() + " fields)");
            }

            // Sort components within each category
            for (ComponentCategory category : categories.values()) {
                category.components().sort(Comparator.comparing(ComponentMeta::simpleName));
            }

            // Sort categories (UI first, then alphabetically, "other" last)
            List<String> sortedKeys = new ArrayList<>(categories.keySet());
            sortedKeys.sort((a, b) -> {
                if (a.equals("ui")) return -1;
                if (b.equals("ui")) return 1;
                if (a.equals("other")) return 1;
                if (b.equals("other")) return -1;
                return a.compareTo(b);
            });

            Map<String, ComponentCategory> sorted = new LinkedHashMap<>();
            for (String key : sortedKeys) {
                sorted.put(key, categories.get(key));
            }
            categories = sorted;

            allComponents.sort(Comparator.comparing(ComponentMeta::simpleName));

            initialized = true;
            System.out.println("ComponentRegistry: Found " + allComponents.size() +
                    " components in " + categories.size() + " categories");

        } catch (Exception e) {
            System.err.println("ComponentRegistry: Failed to scan components");
            e.printStackTrace();
            initialized = true;
        }
    }

    /**
     * Gets all registered components.
     */
    public static List<ComponentMeta> getAll() {
        return Collections.unmodifiableList(allComponents);
    }

    /**
     * Gets all components that can be instantiated.
     */
    public static List<ComponentMeta> getInstantiable() {
        return allComponents.stream()
                .filter(ComponentMeta::hasNoArgConstructor)
                .toList();
    }

    /**
     * Gets all categories with their components.
     * Categories are sorted: UI first, then alphabetically, "other" last.
     */
    public static Collection<ComponentCategory> getCategories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    /**
     * Gets a specific category by name.
     */
    public static ComponentCategory getCategory(String name) {
        return categories.get(name);
    }

    /**
     * Gets component metadata by simple name.
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

    @SuppressWarnings("unchecked")
    private static void scanDirectory(File directory, String packageName,
                                      List<Class<? extends Component>> result) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
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

    private static ComponentMeta buildMeta(Class<? extends Component> clazz) {
        String className = clazz.getName();
        String simpleName = clazz.getSimpleName();
        String displayName = ComponentMeta.toDisplayName(simpleName);

        boolean hasNoArgConstructor = false;
        try {
            clazz.getDeclaredConstructor();
            hasNoArgConstructor = true;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Component " + simpleName + " must have a no-arg constructor for serialization.");
        }

        List<FieldMeta> fields = new ArrayList<>();
        List<ComponentRefMeta> references = new ArrayList<>();
        collectFields(clazz, fields, references);

        return new ComponentMeta(
                className,
                simpleName,
                displayName,
                clazz,
                fields,
                references,
                hasNoArgConstructor
        );
    }

    private static void collectFields(Class<?> clazz, List<FieldMeta> fields,
                                      List<ComponentRefMeta> references) {
        if (clazz == null || clazz == Component.class || clazz == Object.class) {
            return;
        }

        collectFields(clazz.getSuperclass(), fields, references);

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            if (name.equals("gameObject") || name.equals("started") || name.equals("enabled")) {
                continue;
            }

            ComponentRef refAnnotation = field.getAnnotation(ComponentRef.class);
            if (refAnnotation != null) {
                ComponentRefMeta refMeta = buildRefMeta(field, refAnnotation);
                if (refMeta != null) {
                    references.add(refMeta);
                }
                continue;
            }

            if (field.isAnnotationPresent(HideInInspector.class)) {
                continue;
            }

            Object defaultValue = getDefaultValue(field.getType());
            fields.add(new FieldMeta(name, field.getType(), field, defaultValue));
        }
    }

    @SuppressWarnings("unchecked")
    private static ComponentRefMeta buildRefMeta(Field field, ComponentRef annotation) {
        Class<?> fieldType = field.getType();
        Class<?> componentType;
        boolean isList = false;

        if (List.class.isAssignableFrom(fieldType)) {
            isList = true;
            Type genericType = field.getGenericType();

            if (genericType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> typeArg) {
                    if (Component.class.isAssignableFrom(typeArg)) {
                        componentType = typeArg;
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else if (Component.class.isAssignableFrom(fieldType)) {
            componentType = fieldType;
        } else {
            return null;
        }

        return new ComponentRefMeta(
                field.getName(),
                componentType,
                annotation.source(),
                annotation.required(),
                isList,
                field
        );
    }

    private static Object getDefaultValue(Class<?> type) {
        if (type == int.class || type == Integer.class) return 0;
        if (type == float.class || type == Float.class) return 0f;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == String.class) return "";
        return null;
    }
}
