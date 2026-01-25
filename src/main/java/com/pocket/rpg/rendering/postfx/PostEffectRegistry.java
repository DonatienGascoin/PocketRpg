package com.pocket.rpg.rendering.postfx;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Registry of available PostEffect implementations.
 * Uses Reflections to discover all PostEffect subtypes at startup.
 */
public class PostEffectRegistry {

    private static final List<PostEffectMeta> effects = new ArrayList<>();
    private static final Map<String, PostEffectMeta> bySimpleName = new HashMap<>();
    private static boolean initialized = false;

    /**
     * Initializes the registry by scanning for PostEffect implementations.
     * Call once at startup.
     */
    public static void initialize() {
        if (initialized) return;

        System.out.println("PostEffectRegistry: Scanning for effects...");

        try {
            // Configure Reflections to scan the postfx package
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forPackage("com.pocket.rpg.rendering.postfx"))
                    .setScanners(Scanners.SubTypes));

            Set<Class<? extends PostEffect>> classes = reflections.getSubTypesOf(PostEffect.class);

            if (classes.isEmpty()) {
                System.err.println("PostEffectRegistry: WARNING - Reflections found 0 effects! " +
                        "This may indicate a classpath configuration issue.");
            }

            for (Class<? extends PostEffect> clazz : classes) {
                // Skip abstract classes and interfaces
                if (Modifier.isAbstract(clazz.getModifiers()) || clazz.isInterface()) {
                    continue;
                }
                registerEffect(clazz);
            }

            effects.sort(Comparator.comparing(PostEffectMeta::displayName));

            initialized = true;
            System.out.println("PostEffectRegistry: Registered " + effects.size() + " effects");

        } catch (Exception e) {
            System.err.println("PostEffectRegistry: Failed to scan effects: " + e.getMessage());
            e.printStackTrace();
            initialized = true;
        }
    }

    private static void registerEffect(Class<? extends PostEffect> clazz) {
        String className = clazz.getName();
        String simpleName = clazz.getSimpleName();
        String displayName = PostEffectMeta.toDisplayName(simpleName);

        boolean hasNoArgConstructor = false;
        try {
            clazz.getConstructor();
            hasNoArgConstructor = true;
        } catch (NoSuchMethodException e) {
            // No no-arg constructor
        }

        // Extract description from annotation
        String description = "";
        EffectDescription descAnnotation = clazz.getAnnotation(EffectDescription.class);
        if (descAnnotation != null) {
            description = descAnnotation.value();
        }

        PostEffectMeta meta = new PostEffectMeta(
                className,
                simpleName,
                displayName,
                clazz,
                hasNoArgConstructor,
                description
        );

        effects.add(meta);
        bySimpleName.put(simpleName, meta);

//        System.out.println("  Registered: " + displayName +
//                (hasNoArgConstructor ? "" : " (requires parameters)"));
    }

    /**
     * Gets all registered effects.
     */
    public static List<PostEffectMeta> getAll() {
        return Collections.unmodifiableList(effects);
    }

    /**
     * Gets all effects that can be instantiated with no-arg constructor.
     */
    public static List<PostEffectMeta> getInstantiable() {
        return effects.stream()
                .filter(PostEffectMeta::hasNoArgConstructor)
                .toList();
    }

    /**
     * Gets effect metadata by simple name.
     */
    public static PostEffectMeta getBySimpleName(String simpleName) {
        return bySimpleName.get(simpleName);
    }

    /**
     * Creates a new instance using the no-arg constructor.
     */
    public static PostEffect instantiate(String simpleName) {
        PostEffectMeta meta = bySimpleName.get(simpleName);
        if (meta == null || !meta.hasNoArgConstructor()) {
            return null;
        }

        try {
            Constructor<? extends PostEffect> constructor =
                    meta.effectClass().getDeclaredConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            System.err.println("Failed to instantiate " + simpleName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new instance from a Class reference.
     */
    public static PostEffect instantiate(Class<? extends PostEffect> effectClass) {
        return instantiate(effectClass.getSimpleName());
    }
}
