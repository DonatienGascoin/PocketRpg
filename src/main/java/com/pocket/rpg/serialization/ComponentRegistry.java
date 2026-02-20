package com.pocket.rpg.serialization;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.HideInInspector;
import com.pocket.rpg.components.RequiredComponent;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.pocket.rpg.logging.Log;

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

    // ========================================================================
    // MIGRATION MAP - for renamed/moved component classes
    // ========================================================================

    private static final Map<String, String> migrationMap = new HashMap<>();

    // Register legacy migrations here when refactoring components.
    // These run before initialize() due to static block ordering.
    static {
        // Example: addMigration("com.pocket.rpg.old.OldComponent", "com.pocket.rpg.components.NewComponent");
    }

    private static void addMigration(String oldClassName, String newClassName) {
        migrationMap.put(oldClassName, newClassName);
    }

    /**
     * Looks up a migration mapping for the given old class name.
     * @return The new class name, or null if no migration exists
     */
    public static String getMigration(String oldClassName) {
        return migrationMap.get(oldClassName);
    }

    // ========================================================================
    // FALLBACK RESOLUTION TRACKING
    // ========================================================================

    /**
     * Tracks fallback resolutions during deserialization.
     * Uses Set to deduplicate (e.g., 50 entities with same stale Transform → 1 entry).
     *
     * <p><b>Threading:</b> Assumes scene loading is single-threaded (main thread).
     * If background loading is ever added, this would need synchronization.
     */
    private static final ThreadLocal<Set<String>> fallbackResolutions =
        ThreadLocal.withInitial(LinkedHashSet::new);  // Preserves insertion order

    /** Called by ComponentTypeAdapterFactory when fallback resolution is used. */
    public static void recordFallbackResolution(String oldName, String newName) {
        fallbackResolutions.get().add(oldName + "|" + newName);
    }

    /** Call before deserializing a scene to reset tracking state. */
    public static void resetFallbackTracking() {
        fallbackResolutions.get().clear();
    }

    /** Get the list of fallback resolutions (empty if none). Returns a copy. */
    public static List<String> getFallbackResolutions() {
        return new ArrayList<>(fallbackResolutions.get());
    }

    /** Check if any fallback resolution was used. */
    public static boolean wasFallbackUsed() {
        return !fallbackResolutions.get().isEmpty();
    }

    /** Returns the current number of unique fallback resolutions recorded. */
    public static int getFallbackResolutionCount() {
        return fallbackResolutions.get().size();
    }

    /**
     * Initializes the registry by scanning the components package.
     * Call once at startup.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        System.out.println("ComponentRegistry: Scanning for components...");

        // Log any registered migrations
        if (!migrationMap.isEmpty()) {
            Log.info("ComponentRegistry", migrationMap.size() + " migration(s) registered");
            migrationMap.forEach((old, newName) ->
                Log.info("ComponentRegistry", "  " + old + " -> " + newName));
        }

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

                    // Check for duplicate simple names (simple name fallback won't work for these)
                    ComponentMeta existing = bySimpleName.get(meta.simpleName());
                    if (existing != null && !existing.className().equals(meta.className())) {
                        Log.error("ComponentRegistry", "Duplicate component simple name '" + meta.simpleName() +
                                "': " + existing.className() + " and " + meta.className() +
                                " — simple name fallback will not work for these. Add explicit migrations to ComponentRegistry's static block.");
                    }
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

                    validateRequiredComponents(clazz);
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
     * Clears all cached component metadata and re-scans the classpath.
     * <p>
     * Must only be called from the main thread.
     * <p>
     * On a standard JVM (without DCEVM), this only discovers NEW classes.
     * Modified classes remain loaded with their old definitions. With DCEVM,
     * structural changes are applied by the JVM before this re-scan.
     */
    public static void reinitialize() {
        bySimpleName.clear();
        byFullName.clear();
        allComponents.clear();
        categories.clear();
        initialized = false;
        initialize();
        System.out.println("ComponentRegistry reinitialized: " + allComponents.size() + " components");
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

    /**
     * Validates that all @RequiredComponent targets have a no-arg constructor
     * so they can be auto-instantiated at runtime.
     */
    private static void validateRequiredComponents(Class<? extends Component> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Component.class && current != Object.class) {
            RequiredComponent[] requirements = current.getDeclaredAnnotationsByType(RequiredComponent.class);
            for (RequiredComponent req : requirements) {
                Class<? extends Component> target = req.value();
                try {
                    target.getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    System.err.println("  WARNING: " + clazz.getSimpleName() +
                            " declares @RequiredComponent(" + target.getSimpleName() +
                            ") but " + target.getSimpleName() + " has no no-arg constructor");
                }
            }
            current = current.getSuperclass();
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
        List<ComponentReferenceMeta> componentReferences = new ArrayList<>();
        collectFields(clazz, fields, componentReferences);

        return new ComponentMeta(
                className,
                simpleName,
                displayName,
                clazz,
                fields,
                componentReferences,
                hasNoArgConstructor
        );
    }

    private static void collectFields(Class<?> clazz, List<FieldMeta> fields,
                                      List<ComponentReferenceMeta> componentReferences) {
        if (clazz == null || clazz == Component.class || clazz == Object.class) {
            return;
        }

        collectFields(clazz.getSuperclass(), fields, componentReferences);

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            // Check for @ComponentReference
            ComponentReference refAnnotation = field.getAnnotation(ComponentReference.class);
            if (refAnnotation != null) {
                ComponentReferenceMeta refMeta = buildReferenceMeta(field, refAnnotation);
                if (refMeta != null) {
                    componentReferences.add(refMeta);

                    // KEY source fields are serialized — add to fields list with String.class override
                    if (refMeta.isKeySource()) {
                        if (refMeta.isList()) {
                            // List KEY: serialized as List<String>
                            fields.add(new FieldMeta(field.getName(), List.class, field, List.of(), String.class));
                        } else {
                            // Single KEY: serialized as String
                            fields.add(new FieldMeta(field.getName(), String.class, field, ""));
                        }
                    }
                }
                continue;
            }

            String name = field.getName();
            if (name.equals("gameObject") || name.equals("started") || name.equals("enabled")) {
                continue;
            }

            if (field.isAnnotationPresent(HideInInspector.class)) {
                continue;
            }

            Object defaultValue = getDefaultValue(field.getType());

            // Extract generic type arguments for List and Map fields
            Class<?> elementType = null;
            Class<?> keyType = null;
            Class<?> valueType = null;
            if (List.class.isAssignableFrom(field.getType())) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType pt) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> et) {
                        elementType = et;
                    }
                }
            } else if (Map.class.isAssignableFrom(field.getType())) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType pt) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length >= 2) {
                        if (typeArgs[0] instanceof Class<?> kt) keyType = kt;
                        if (typeArgs[1] instanceof Class<?> vt) valueType = vt;
                    }
                }
            }

            fields.add(new FieldMeta(name, field.getType(), field, defaultValue, elementType, keyType, valueType));
        }
    }

    @SuppressWarnings("unchecked")
    private static ComponentReferenceMeta buildReferenceMeta(Field field, ComponentReference annotation) {
        Class<?> fieldType = field.getType();
        Class<? extends Component> componentType;
        boolean isList = false;

        if (List.class.isAssignableFrom(fieldType)) {
            isList = true;
            Type genericType = field.getGenericType();

            if (genericType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> typeArg) {
                    if (Component.class.isAssignableFrom(typeArg)) {
                        componentType = (Class<? extends Component>) typeArg;
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
            componentType = (Class<? extends Component>) fieldType;
        } else {
            return null;
        }

        return new ComponentReferenceMeta(
                field,
                field.getName(),
                componentType,
                annotation.source(),
                annotation.required(),
                isList
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
