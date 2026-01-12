package com.pocket.rpg.rendering.postfx;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * Registry of available PostEffect implementations.
 * Uses reflection to safely register known effects.
 */
public class PostEffectRegistry {

    private static final List<PostEffectMeta> effects = new ArrayList<>();
    private static final Map<String, PostEffectMeta> bySimpleName = new HashMap<>();
    private static boolean initialized = false;

    // Known effect class names
    private static final String[] EFFECT_CLASSES = {
            "com.pocket.rpg.postProcessing.BloomEffect",
            "com.pocket.rpg.postProcessing.postEffects.BlurEffect",
            "com.pocket.rpg.postProcessing.postEffects.ChromaticAberrationEffect",
            "com.pocket.rpg.postProcessing.postEffects.ColorGradingEffect",
            "com.pocket.rpg.postProcessing.postEffects.DesaturationEffect",
            "com.pocket.rpg.postProcessing.postEffects.DisplacementEffect",
            "com.pocket.rpg.postProcessing.postEffects.EdgeDetectionEffect",
            "com.pocket.rpg.postProcessing.postEffects.FilmGrainEffect",
            "com.pocket.rpg.postProcessing.postEffects.MotionBlurEffect",
            "com.pocket.rpg.postProcessing.postEffects.PixelationEffect",
            "com.pocket.rpg.postProcessing.postEffects.RadialBlurEffect",
            "com.pocket.rpg.postProcessing.postEffects.ScanlinesEffect",
            "com.pocket.rpg.postProcessing.postEffects.VignetteEffect"
    };

    /**
     * Initializes the registry with all known effects.
     * Call once at startup.
     */
    public static void initialize() {
        if (initialized) return;

        // System.out.println("PostEffectRegistry: Registering effects...");

        for (String className : EFFECT_CLASSES) {
            tryRegisterEffect(className);
        }

        effects.sort(Comparator.comparing(PostEffectMeta::displayName));

        initialized = true;
        System.out.println("PostEffectRegistry: Registered " + effects.size() + " effects");
    }

    @SuppressWarnings("unchecked")
    private static void tryRegisterEffect(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (PostEffect.class.isAssignableFrom(clazz)) {
                registerEffect((Class<? extends PostEffect>) clazz);
            }
        } catch (ClassNotFoundException e) {
            // Effect class not present, skip silently
        } catch (Exception e) {
            System.err.println("  Failed to register " + className + ": " + e.getMessage());
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

        PostEffectMeta meta = new PostEffectMeta(
                className,
                simpleName,
                displayName,
                clazz,
                hasNoArgConstructor
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
