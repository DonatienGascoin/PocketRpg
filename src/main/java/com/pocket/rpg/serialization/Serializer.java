package com.pocket.rpg.serialization;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.serialization.custom.*;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class Serializer {
    private static Serializer instance;

    private final Gson defaultConfig;
    private final Gson prettyPrintConfig;

    public Serializer(AssetContext context) {
        GsonBuilder builder = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .registerTypeAdapter(PostEffect.class, new PostEffectTypeAdapter())
                // Component polymorphism
                .registerTypeAdapter(Component.class, new ComponentSerializer())
                .registerTypeAdapter(Component.class, new ComponentDeserializer())
                // Asset types
                .registerTypeAdapter(Sprite.class, new SpriteTypeAdapter(context))
                .registerTypeAdapter(Texture.class, new TextureTypeAdapter(context))
                // JOML vectors
                .registerTypeAdapter(Vector2f.class, new Vector2fTypeAdapter())
                .registerTypeAdapter(Vector3f.class, new Vector3fTypeAdapter())
                .registerTypeAdapter(Vector4f.class, new Vector4fTypeAdapter());


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
        return toJson(obj, false);
    }

    public static String toJson(Object obj, boolean prettyPrint) {
        if (prettyPrint) {
            return getPrettyPrintGson().toJson(obj);
        }
        return getGson().toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return getGson().fromJson(json, clazz);
    }
}
