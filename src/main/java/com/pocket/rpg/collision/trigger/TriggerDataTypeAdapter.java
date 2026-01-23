package com.pocket.rpg.collision.trigger;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gson TypeAdapter for TriggerData sealed interface.
 * <p>
 * Uses registry-based discovery via {@code getPermittedSubclasses()} to automatically
 * support all trigger types without maintaining a switch statement.
 * <p>
 * JSON format:
 * <pre>
 * {
 *   "type": "WarpTriggerData",
 *   "data": { ... record fields ... }
 * }
 * </pre>
 */
public class TriggerDataTypeAdapter extends TypeAdapter<TriggerData> {

    private final Gson gson;

    // Registry built automatically from sealed interface permits clause
    private static final Map<String, Class<? extends TriggerData>> TYPE_REGISTRY;

    static {
        TYPE_REGISTRY = new HashMap<>();
        for (Class<?> permitted : TriggerData.class.getPermittedSubclasses()) {
            @SuppressWarnings("unchecked")
            Class<? extends TriggerData> clazz = (Class<? extends TriggerData>) permitted;
            TYPE_REGISTRY.put(clazz.getSimpleName(), clazz);
        }
    }

    /**
     * Creates a new adapter with a Gson instance for delegating record serialization.
     */
    public TriggerDataTypeAdapter(Gson gson) {
        this.gson = gson;
    }

    /**
     * Creates a new adapter using a fresh Gson instance.
     * <p>
     * Note: If you need enum serialization or other custom adapters,
     * pass a pre-configured Gson instance instead.
     */
    public TriggerDataTypeAdapter() {
        this(new GsonBuilder()
                .enableComplexMapKeySerialization()
                .create());
    }

    @Override
    public void write(JsonWriter out, TriggerData data) throws IOException {
        if (data == null) {
            out.nullValue();
            return;
        }

        out.beginObject();

        // Write type discriminator
        out.name("type").value(data.getClass().getSimpleName());

        // Write the record data
        out.name("data");
        gson.toJson(data, data.getClass(), out);

        out.endObject();
    }

    @Override
    public TriggerData read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        in.beginObject();

        String type = null;
        JsonElement dataElement = null;

        while (in.hasNext()) {
            String name = in.nextName();
            if ("type".equals(name)) {
                type = in.nextString();
            } else if ("data".equals(name)) {
                dataElement = JsonParser.parseReader(in);
            } else {
                in.skipValue();
            }
        }

        in.endObject();

        if (type == null) {
            throw new JsonParseException("Missing 'type' field in TriggerData");
        }

        if (dataElement == null) {
            throw new JsonParseException("Missing 'data' field in TriggerData");
        }

        // Look up class from registry - no switch needed
        Class<? extends TriggerData> clazz = TYPE_REGISTRY.get(type);
        if (clazz == null) {
            throw new JsonParseException("Unknown trigger type: " + type +
                    ". Known types: " + TYPE_REGISTRY.keySet());
        }

        return gson.fromJson(dataElement, clazz);
    }

    /**
     * Returns the registered trigger type names.
     */
    public static java.util.Set<String> getRegisteredTypes() {
        return TYPE_REGISTRY.keySet();
    }

    /**
     * Returns the class for a trigger type name.
     */
    public static Class<? extends TriggerData> getTypeClass(String typeName) {
        return TYPE_REGISTRY.get(typeName);
    }
}
