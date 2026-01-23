package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentRef;
import com.pocket.rpg.components.HideInInspector;
import com.pocket.rpg.components.Transform;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.*;
import java.util.*;

/**
 * Registry of all available components.
 * Uses Reflections to discover all Component subclasses at startup.
 * <p>
 * Supports categorized access for UI menus via getCategories().
 */
public class ComponentRegistry {

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
                if (clazz == Component.class) {
                    continue;
                }

                try {
                    ComponentMeta meta = buildMeta(clazz);
                    bySimpleName.put(meta.simpleName(), meta);
                    byFullName.put(meta.className(), meta);
                    allComponents.add(meta);

                    // Add to category using @ComponentMeta annotation
                    String categoryName = extractCategory(clazz);
                    ComponentCategory category = categories.computeIfAbsent(
                            categoryName,
                            name -> new ComponentCategory(name, ComponentCategory.toDisplayName(name))
                    );
                    category.add(meta);

                    System.out.println("  Registered: " + meta.simpleName() +
                            " [" + categoryName + "] (" + meta.fields().size() + " fields)");
                } catch (Exception e) {
                    System.err.println("  Skipped: " + clazz.getSimpleName() + " - " + e.getMessage());
                }
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

    /**
     * Resets all transient fields in a component to their default values.
     * <p>
     * This is necessary after deserialization/cloning because transient fields
     * are not serialized and may not have their field-initializer values.
     * Creates a fresh instance and copies transient field values from it.
     *
     * @param component The component to reset transient fields on
     */
    public static void resetTransientFields(Component component) {
        if (component == null) return;

        String className = component.getClass().getName();
        Component freshInstance = instantiateByClassName(className);
        if (freshInstance == null) {
            return;
        }

        // Walk up the class hierarchy to get all transient fields
        Class<?> clazz = component.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isTransient(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        Object defaultValue = field.get(freshInstance);
                        field.set(component, defaultValue);
                    } catch (IllegalAccessException e) {
                        // Skip fields we can't access
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static List<Class<? extends Component>> scanComponentClasses() {
        List<Class<? extends Component>> result = new ArrayList<>();

        try {
            // Configure Reflections with explicit SubTypes scanner
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forPackage("com.pocket.rpg"))
                    .setScanners(Scanners.SubTypes));

            Set<Class<? extends Component>> classes = reflections.getSubTypesOf(Component.class);

            if (classes.isEmpty()) {
                System.err.println("ComponentRegistry: WARNING - Reflections found 0 components! " +
                        "This may indicate a classpath configuration issue.");
            }

            for (Class<? extends Component> clazz : classes) {
                if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }
                if (clazz == Component.class) {
                    continue;
                }
                result.add(clazz);
            }
        } catch (Exception e) {
            System.err.println("ComponentRegistry: Failed to scan components: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Extracts category from @ComponentMeta annotation, or returns "other" if not present.
     */
    private static String extractCategory(Class<? extends Component> clazz) {
        com.pocket.rpg.components.ComponentMeta metaAnnotation =
                clazz.getAnnotation(com.pocket.rpg.components.ComponentMeta.class);

        if (metaAnnotation != null && !metaAnnotation.category().isEmpty()) {
            return metaAnnotation.category().toLowerCase();
        }
        return "other";
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

            // Extract generic element type for List fields
            Class<?> elementType = null;
            if (List.class.isAssignableFrom(field.getType())) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType pt) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> et) {
                        elementType = et;
                    }
                }
            }

            fields.add(new FieldMeta(name, field.getType(), field, defaultValue, elementType));
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
