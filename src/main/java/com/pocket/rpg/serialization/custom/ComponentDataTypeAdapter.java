package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.SpriteReference;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;

import java.io.IOException;
import java.util.Map;

/**
 * Gson TypeAdapter for ComponentData that uses the new serialization format.
 *
 * This adapter allows gradual migration: the codebase continues to use
 * ComponentData internally, but JSON uses the new format:
 * - "properties" instead of "fields"
 * - "ClassName:path" for asset references
 *
 * Register with Gson:
 *   new GsonBuilder()
 *       .registerTypeAdapter(ComponentData.class, new ComponentDataTypeAdapter(assetContext))
 *       .create();
 */
public class ComponentDataTypeAdapter extends TypeAdapter<ComponentData> {

    private static final String TYPE_FIELD = "type";
    private static final String PROPERTIES_FIELD = "properties";
    private static final String LEGACY_FIELDS_KEY = "fields";
    private static final String ASSET_DELIMITER = ":";

    private final AssetContext context;
    private final Gson delegateGson;

    public ComponentDataTypeAdapter(AssetContext context) {
        this.context = context;
        // Gson without this adapter to avoid recursion
        this.delegateGson = new GsonBuilder().create();
    }

    @Override
    public void write(JsonWriter out, ComponentData value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        out.beginObject();
        out.name(TYPE_FIELD).value(value.getType());
        out.name(PROPERTIES_FIELD);

        writeProperties(out, value);

        out.endObject();
    }

    private void writeProperties(JsonWriter out, ComponentData data) throws IOException {
        out.beginObject();

        ComponentMeta meta = ComponentRegistry.getByClassName(data.getType());
        Map<String, Object> fields = data.getFields();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            out.name(fieldName);
            writeFieldValue(out, value, fieldName, meta);
        }

        out.endObject();
    }

    private void writeFieldValue(JsonWriter out, Object value, String fieldName, ComponentMeta meta) throws IOException {
        // Check if value is an asset (has a registered path)
        if (context != null) {
            String assetPath = context.getPathForResource(value);
            if (assetPath != null) {
                String className = value.getClass().getName();
                out.value(className + ASSET_DELIMITER + assetPath);
                return;
            }
        }

        // Check if value is a Sprite (might not be in context yet)
        if (value instanceof Sprite sprite) {
            String path = SpriteReference.toPath(sprite);
            if (path != null) {
                out.value(Sprite.class.getName() + ASSET_DELIMITER + path);
                return;
            }
        }

        // Delegate to Gson for other types
        delegateGson.toJson(value, value.getClass(), out);
    }

    @Override
    public ComponentData read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        in.beginObject();
        String componentType = null;
        JsonObject properties = null;

        while (in.hasNext()) {
            String name = in.nextName();
            if (TYPE_FIELD.equals(name)) {
                componentType = in.nextString();
            } else if (PROPERTIES_FIELD.equals(name) || LEGACY_FIELDS_KEY.equals(name)) {
                properties = JsonParser.parseReader(in).getAsJsonObject();
            } else {
                in.skipValue();
            }
        }
        in.endObject();

        if (componentType == null) {
            throw new JsonParseException("Missing 'type' field in ComponentData");
        }

        ComponentData data = new ComponentData(componentType);

        if (properties != null) {
            readProperties(data, properties);
        }

        return data;
    }

    private void readProperties(ComponentData data, JsonObject json) {
        ComponentMeta meta = ComponentRegistry.getByClassName(data.getType());
        Map<String, Object> fields = data.getFields();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String fieldName = entry.getKey();
            JsonElement element = entry.getValue();

            if (element.isJsonNull()) {
                continue;
            }

            // Determine target type from meta
            Class<?> targetType = Object.class;
            if (meta != null) {
                for (FieldMeta fm : meta.fields()) {
                    if (fm.name().equals(fieldName)) {
                        targetType = fm.type();
                        break;
                    }
                }
            }

            Object value = readFieldValue(element, targetType);
            fields.put(fieldName, value);
        }
    }

    private Object readFieldValue(JsonElement element, Class<?> targetType) {
        // Check for asset reference string "full.class.Name:path"
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String stringValue = element.getAsString();

            int delimiterIndex = stringValue.indexOf(ASSET_DELIMITER);
            if (delimiterIndex > 0) {
                String className = stringValue.substring(0, delimiterIndex);
                String path = stringValue.substring(delimiterIndex + 1);

                Class<?> assetType = resolveClass(className);
                if (assetType != null) {
                    return loadAsset(path, assetType);
                }
            }

            // Legacy format: plain string for known asset types
            if (Sprite.class.equals(targetType)) {
                return loadAsset(stringValue, Sprite.class);
            }
            if (isAssetType(targetType)) {
                return loadAsset(stringValue, targetType);
            }
        }

        // Delegate to Gson
        return delegateGson.fromJson(element, targetType);
    }

    private Class<?> resolveClass(String fullName) {
        try {
            return Class.forName(fullName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private boolean isAssetType(Class<?> type) {
        if (type == null) return false;
        // Check if type has a path in context (indicating it's an asset type)
        // Or check known asset base types
        return Sprite.class.isAssignableFrom(type)
                || type.getName().equals("com.pocket.rpg.rendering.Texture")
                || type.getName().equals("com.pocket.rpg.ui.text.Font");
    }

    private Object loadAsset(String path, Class<?> type) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        try {
            if (Sprite.class.equals(type)) {
                return SpriteReference.fromPath(path);
            }
            if (context != null) {
                return context.load(path, type);
            }
        } catch (Exception e) {
            System.err.println("Failed to load asset: " + path + " - " + e.getMessage());
        }

        // Return path string as fallback (for editor display)
        return path;
    }
}
