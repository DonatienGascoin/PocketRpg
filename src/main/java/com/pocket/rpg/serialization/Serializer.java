package com.pocket.rpg.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pocket.rpg.collision.trigger.TriggerData;
import com.pocket.rpg.collision.trigger.TriggerDataTypeAdapter;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.custom.AssetReferenceTypeAdapterFactory;
import com.pocket.rpg.serialization.custom.ComponentTypeAdapterFactory;
import com.pocket.rpg.serialization.custom.PostEffectTypeAdapter;
import com.pocket.rpg.serialization.custom.SpriteTypeAdapter;

public class Serializer {
    private static Serializer instance;

    private final Gson defaultConfig;
    private final Gson prettyPrintConfig;

    public Serializer(AssetContext context) {
        GsonBuilder builder = new GsonBuilder()
                .enableComplexMapKeySerialization()
                // Handles asset serialization. Must be registered FIRST in Gson builder, before other adapters, so it can delegate to them when needed.
                .registerTypeAdapterFactory(new AssetReferenceTypeAdapterFactory(context))
                // Component polymorphism
                .registerTypeAdapterFactory(new ComponentTypeAdapterFactory(context))
                // Sprite with object fallback (for programmatic sprites)
                .registerTypeAdapter(Sprite.class, new SpriteTypeAdapter(Assets.getContext()))
                // Others
                .registerTypeHierarchyAdapter(PostEffect.class, new PostEffectTypeAdapter())
                // Trigger data (sealed interface with registry-based discovery)
                .registerTypeAdapter(TriggerData.class, new TriggerDataTypeAdapter());


        defaultConfig = builder.create();
        prettyPrintConfig = builder
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    public static void init(AssetContext context) {
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
        return toJson(obj, false);
    }

    public static String toJson(Object obj, boolean prettyPrint) {
        if (prettyPrint) {
            return getPrettyPrintGson().toJson(obj);
        }
        return getGson().toJson(obj);
    }

    public static String toPrettyJson(Object obj) {
        return getPrettyPrintGson().toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return getGson().fromJson(json, clazz);
    }
}