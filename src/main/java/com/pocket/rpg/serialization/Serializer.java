package com.pocket.rpg.serialization;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.serialization.custom.PostEffectTypeAdapter;

public class Serializer {
    private static Serializer instance;

    private final Gson defaultConfig;
    private final Gson prettyPrintConfig;

    public Serializer() {
        GsonBuilder builder = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .registerTypeAdapter(PostEffect.class, new PostEffectTypeAdapter());


        defaultConfig = builder.create();
        prettyPrintConfig = builder
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    public static void init() {
        instance = new Serializer();

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
