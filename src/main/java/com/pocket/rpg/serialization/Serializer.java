package com.pocket.rpg.serialization;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.serialization.custom.*;

/**
 * Central serialization configuration for the engine.
 * <p>
 * Asset serialization is handled by:
 * <ul>
 *   <li>{@link AssetReferenceTypeAdapterFactory} - Generic assets (Texture, SpriteSheet, Font, etc.)</li>
 *   <li>{@link SpriteTypeAdapter} - Sprites with object fallback for programmatic sprites</li>
 *   <li>{@link ComponentTypeAdapterFactory} - Component polymorphism</li>
 * </ul>
 * <p>
 * Note: TextureTypeAdapter was removed as it's redundant with AssetReferenceTypeAdapterFactory.
 * All asset paths are managed centrally through {@link com.pocket.rpg.resources.AssetManager#getPathForResource(Object)}.
 */
public class Serializer {
    private static Serializer instance;

    private final Gson defaultConfig;
    private final Gson prettyPrintConfig;

    public Serializer(AssetContext context) {
        GsonBuilder builder = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .registerTypeAdapterFactory(new AssetReferenceTypeAdapterFactory(context))
                // Component polymorphism
                .registerTypeAdapterFactory(new ComponentTypeAdapterFactory(context))
                // Sprite with object fallback (for programmatic sprites)
                .registerTypeAdapter(Sprite.class, new SpriteTypeAdapter(context))
                // Others
                .registerTypeAdapter(PostEffect.class, new PostEffectTypeAdapter());

        // Note: TextureTypeAdapter removed - AssetReferenceTypeAdapterFactory handles Texture

        defaultConfig = builder.create();
        prettyPrintConfig = builder
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    public static void init(AssetContext context) {
        if (instance != null) {
            return;
        }
        instance = new Serializer(context);

    }

    public static Gson getGson() {
        return instance.defaultConfig;
    }

    public static Gson getPrettyPrintGson() {
        return instance.prettyPrintConfig;
    }

    public static <T> T deepCopy(T object, Class<T> clazz) {
        String json = getGson().toJson(object);
        return getGson().fromJson(json, clazz);
    }

    public static String toJson(Object obj) {
        return getGson().toJson(obj);
    }

    public static String toPrettyJson(Object obj) {
        return getPrettyPrintGson().toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return getGson().fromJson(json, clazz);
    }
}
