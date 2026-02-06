package com.pocket.rpg.serialization.custom;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.rendering.TilemapRenderer;
import com.pocket.rpg.components.rendering.TilemapRenderer.LedgeDirection;
import com.pocket.rpg.components.rendering.TilemapRenderer.Tile;
import com.pocket.rpg.components.rendering.TilemapRenderer.TileChunk;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.resources.SpriteReference;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentReferenceMeta;
import com.pocket.rpg.serialization.ComponentReferenceResolver;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Gson TypeAdapterFactory for Component serialization/deserialization.
 * <p>
 * Handles all components generically via reflection, with special binary
 * encoding for TilemapRenderer.
 * <p>
 * Asset fields are serialized as "ClassName:path" format for type safety.
 * Legacy plain string format is supported for backwards compatibility.
 */
public class ComponentTypeAdapterFactory implements TypeAdapterFactory {

    private static final String TYPE_FIELD = "type";
    private static final String PROPERTIES_FIELD = "properties";
    private static final String LEGACY_FIELDS_KEY = "fields"; // Backwards compatibility
    private static final String ASSET_DELIMITER = ":";

    private final AssetContext context;

    public ComponentTypeAdapterFactory(AssetContext context) {
        this.context = context;
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!Component.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }

                Component component = (Component) value;
                out.beginObject();
                out.name(TYPE_FIELD).value(component.getClass().getCanonicalName());
                out.name(PROPERTIES_FIELD);

                if (component instanceof TilemapRenderer tilemap) {
                    writeTilemapBinary(out, tilemap);
                } else {
                    writeComponentProperties(out, component, gson);
                }

                out.endObject();
            }

            @Override
            @SuppressWarnings("unchecked")
            public T read(JsonReader in) throws IOException {
                if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }

                in.beginObject();
                String componentType = null;
                JsonElement properties = null;

                while (in.hasNext()) {
                    String name = in.nextName();
                    if (TYPE_FIELD.equals(name)) {
                        componentType = in.nextString();
                    } else if (PROPERTIES_FIELD.equals(name) || LEGACY_FIELDS_KEY.equals(name)) {
                        properties = JsonParser.parseReader(in);
                    } else {
                        in.skipValue();
                    }
                }
                in.endObject();

                if (componentType == null) {
                    throw new JsonParseException("Missing 'type' field in component");
                }

                Class<?> clazz;
                try {
                    clazz = Class.forName(componentType);
                } catch (ClassNotFoundException e) {
                    // Step 1: Check migration map for explicit old->new mapping
                    String migratedName = ComponentRegistry.getMigration(componentType);
                    if (migratedName != null) {
                        try {
                            clazz = Class.forName(migratedName);
                            Log.warn("ComponentRegistry", "Component class migrated: " + componentType +
                                    " -> " + migratedName + " (via migration map)");
                            ComponentRegistry.recordFallbackResolution(componentType, migratedName);
                        } catch (ClassNotFoundException e2) {
                            throw new JsonParseException(
                                "Migration target not found: " + migratedName +
                                " (migrated from " + componentType + ")", e2);
                        }
                    } else {
                        // Step 2: Try simple name fallback
                        String simpleName = componentType.substring(componentType.lastIndexOf('.') + 1);
                        ComponentMeta meta = ComponentRegistry.getBySimpleName(simpleName);
                        if (meta == null) {
                            throw new JsonParseException(
                                "Unknown component type: " + componentType + "\n" +
                                "Not found by full name, migration map, or simple name '" + simpleName + "'.\n" +
                                "If the class was renamed, add a migration to ComponentRegistry's static block:\n" +
                                "  addMigration(\"" + componentType + "\", \"com.pocket.rpg.components.NewName\");",
                                e);
                        }
                        Log.warn("ComponentRegistry", "Component class moved: " + componentType +
                                " -> " + meta.className() + " (resolved by simple name)");
                        ComponentRegistry.recordFallbackResolution(componentType, meta.className());
                        clazz = meta.componentClass();
                    }
                }

                // Deserialize based on component type
                try {
                    if (TilemapRenderer.class.isAssignableFrom(clazz)) {
                        if (properties.isJsonPrimitive() && properties.getAsJsonPrimitive().isString()) {
                            return (T) readTilemapBinary(properties.getAsString());
                        } else {
                            return (T) readLegacyTilemap(properties, gson);
                        }
                    } else {
                        return (T) readComponentProperties(properties.getAsJsonObject(), clazz, gson);
                    }
                } catch (Exception deserializeEx) {
                    throw new JsonParseException("Failed to deserialize component: " + componentType, deserializeEx);
                }
            }
        };
    }

    // ========================================================================
    // GENERIC COMPONENT SERIALIZATION
    // ========================================================================

    private void writeComponentProperties(JsonWriter out, Component component, Gson gson) throws IOException {
        ComponentMeta meta = ComponentRegistry.getByClassName(component.getClass().getName());

        out.beginObject();

        // Write componentKey (defined on Component.class, excluded from meta.fields())
        String componentKey = component.getComponentKey();
        if (componentKey != null && !componentKey.isEmpty()) {
            out.name("componentKey");
            out.value(componentKey);
        }

        if (meta != null) {
            for (FieldMeta fieldMeta : meta.fields()) {
                Field field = fieldMeta.field();
                field.setAccessible(true);

                try {
                    // @ComponentReference(source=KEY) fields: write the pending key string
                    ComponentReferenceMeta keyRef = findKeyRef(meta, fieldMeta.name());
                    if (keyRef != null) {
                        if (keyRef.isList()) {
                            List<String> keys = ComponentReferenceResolver.getPendingKeyList(component, fieldMeta.name());
                            if (!keys.isEmpty()) {
                                out.name(fieldMeta.name());
                                out.beginArray();
                                for (String k : keys) {
                                    out.value(k);
                                }
                                out.endArray();
                            }
                        } else {
                            String key = ComponentReferenceResolver.getPendingKey(component, fieldMeta.name());
                            if (key != null && !key.isEmpty()) {
                                out.name(fieldMeta.name());
                                out.value(key);
                            }
                        }
                        continue;
                    }

                    Object value = field.get(component);
                    if (value == null) {
                        continue;
                    }

                    out.name(fieldMeta.name());

                    // Special handling for List fields with asset element types
                    if (fieldMeta.isList() && value instanceof List<?> list) {
                        writeListField(out, list, fieldMeta.elementType(), gson);
                    } else {
                        writeFieldValue(out, value, gson);
                    }
                } catch (IllegalAccessException e) {
                    System.err.println("Failed to read field " + fieldMeta.name() + ": " + e.getMessage());
                }
            }
        } else {
            // Fallback: component not in registry, use default Gson
            System.err.println("Component not in registry: " + component.getClass().getName());
            gson.toJson(component, component.getClass(), out);
            return;
        }

        out.endObject();
    }

    private void writeFieldValue(JsonWriter out, Object value, Gson gson) throws IOException {
        // Check if value is an asset
        String assetPath = context.getPathForResource(value);
        if (assetPath != null) {
            // Serialize as "full.class.Name:path"
            String className = value.getClass().getName();
            out.value(className + ASSET_DELIMITER + assetPath);
            return;
        }

        // Delegate to Gson for non-asset types
        gson.toJson(value, value.getClass(), out);
    }

    /**
     * Serializes a List field with proper type prefixes for asset elements.
     */
    private void writeListField(JsonWriter out, List<?> list, Class<?> elementType, Gson gson) throws IOException {
        out.beginArray();

        for (Object element : list) {
            if (element == null) {
                out.nullValue();
                continue;
            }

            // Check if element is an asset
            String assetPath = context.getPathForResource(element);
            if (assetPath != null) {
                // Serialize as "full.class.Name:path" for type safety
                String className = element.getClass().getName();
                out.value(className + ASSET_DELIMITER + assetPath);
            } else {
                // Delegate to Gson for non-asset elements
                gson.toJson(element, elementType, out);
            }
        }

        out.endArray();
    }

    private Component readComponentProperties(JsonObject json, Class<?> clazz, Gson gson) {
        ComponentMeta meta = ComponentRegistry.getByClassName(clazz.getName());

        if (meta == null) {
            // Component not in registry - this is an error condition
            // We cannot use gson.fromJson here as it would recursively call this adapter
            System.err.println("WARNING: Component not in registry: " + clazz.getName() +
                    " - attempting manual instantiation");
            try {
                Component component = (Component) clazz.getDeclaredConstructor().newInstance();
                // Can't populate fields without metadata, just return empty instance
                return component;
            } catch (Exception e) {
                throw new JsonParseException("Failed to instantiate unregistered component: " + clazz.getName(), e);
            }
        }

        Component component = ComponentRegistry.instantiateByClassName(clazz.getName());
        if (component == null) {
            throw new JsonParseException("Failed to instantiate component: " + clazz.getName());
        }

        // Read componentKey (defined on Component.class, excluded from meta.fields())
        JsonElement keyElement = json.get("componentKey");
        if (keyElement != null && keyElement.isJsonPrimitive()) {
            component.setComponentKey(keyElement.getAsString());
        }

        for (FieldMeta fieldMeta : meta.fields()) {
            JsonElement element = json.get(fieldMeta.name());
            if (element == null || element.isJsonNull()) {
                continue;
            }

            Field field = fieldMeta.field();
            field.setAccessible(true);

            try {
                // @ComponentReference(source=KEY) fields: read key(s) and store as pending
                ComponentReferenceMeta keyRef = findKeyRef(meta, fieldMeta.name());
                if (keyRef != null) {
                    if (keyRef.isList() && element.isJsonArray()) {
                        List<String> keys = new ArrayList<>();
                        for (var elem : element.getAsJsonArray()) {
                            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                                keys.add(elem.getAsString());
                            }
                        }
                        ComponentReferenceResolver.storePendingKeyList(component, fieldMeta.name(), keys);
                    } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        String key = element.getAsString();
                        ComponentReferenceResolver.storePendingKey(component, fieldMeta.name(), key);
                    }
                    // Leave the Component field null — resolver fills it at runtime
                    continue;
                }

                Object value;
                // Special handling for List fields with known element type
                if (fieldMeta.isList() && element.isJsonArray()) {
                    value = readListField(element.getAsJsonArray(), fieldMeta.elementType(), gson);
                } else {
                    value = readFieldValue(element, fieldMeta.type(), gson);
                }
                field.set(component, value);
            } catch (Exception e) {
                System.err.println("Failed to set field " + fieldMeta.name() + ": " + e.getMessage());
            }
        }

        // Reset transient fields to their default values (field initializers)
        // This ensures caches and other transient state are properly initialized
        ComponentRegistry.resetTransientFields(component);

        return component;
    }

    private Object readFieldValue(JsonElement element, Class<?> targetType, Gson gson) {
        // Check for asset reference string
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String stringValue = element.getAsString();

            // Check for typed asset format "full.class.Name:path"
            int delimiterIndex = stringValue.indexOf(ASSET_DELIMITER);
            if (delimiterIndex > 0) {
                String className = stringValue.substring(0, delimiterIndex);
                String path = stringValue.substring(delimiterIndex + 1);

                // Resolve asset type from full class name
                Class<?> assetType = resolveClass(className);
                if (assetType != null && targetType.isAssignableFrom(assetType)) {
                    return loadAsset(path, assetType);
                }
            }
        }

        // Default: delegate to Gson
        return gson.fromJson(element, targetType);
    }

    /**
     * Deserializes a List field with proper element type handling.
     * Supports asset element types that may be serialized as plain paths.
     */
    private List<Object> readListField(JsonArray jsonArray, Class<?> elementType, Gson gson) {
        List<Object> result = new ArrayList<>();

        for (JsonElement elem : jsonArray) {
            if (elem.isJsonNull()) {
                result.add(null);
                continue;
            }

            // Try to load as asset if element is a string and elementType is an asset type
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                String stringValue = elem.getAsString();

                // Check for typed format first: "full.class.Name:path"
                int delimiterIndex = stringValue.indexOf(ASSET_DELIMITER);
                if (delimiterIndex > 0) {
                    String className = stringValue.substring(0, delimiterIndex);
                    String path = stringValue.substring(delimiterIndex + 1);
                    Class<?> assetType = resolveClass(className);
                    if (assetType != null && elementType.isAssignableFrom(assetType)) {
                        result.add(loadAsset(path, assetType));
                        continue;
                    }
                }

                // For asset element types, try loading plain path string as asset
                if (context.isAssetType(elementType)) {
                    Object asset = loadAsset(stringValue, elementType);
                    if (asset != null) {
                        result.add(asset);
                        continue;
                    }
                }
            }

            // Default: delegate to Gson with element type
            result.add(gson.fromJson(elem, elementType));
        }

        return result;
    }

    private Class<?> resolveClass(String fullName) {
        try {
            return Class.forName(fullName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Object loadAsset(String path, Class<?> type) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        try {
            // Special handling for Sprite via SpriteReference (handles spritesheet#index)
            if (Sprite.class.equals(type)) {
                return SpriteReference.fromPath(path);
            }
            return context.load(path, type);
        } catch (Exception e) {
            System.err.println("Failed to load asset: " + path + " as " + type.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Finds the ComponentReferenceMeta for a KEY source field, or null if not a key reference.
     */
    private static ComponentReferenceMeta findKeyRef(ComponentMeta meta, String fieldName) {
        for (ComponentReferenceMeta ref : meta.componentReferences()) {
            if (ref.isKeySource() && ref.fieldName().equals(fieldName)) {
                return ref;
            }
        }
        return null;
    }

    // ========================================================================
    // TILEMAP BINARY SERIALIZATION
    // ========================================================================

    private void writeTilemapBinary(JsonWriter out, TilemapRenderer tilemap) throws IOException {
        List<Tile> palette = new ArrayList<>();
        Map<Tile, Integer> tileToIndex = new HashMap<>();

        for (TileChunk chunk : tilemap.allChunks()) {
            for (int x = 0; x < TileChunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < TileChunk.CHUNK_SIZE; y++) {
                    Tile t = chunk.get(x, y);
                    if (t != null && !tileToIndex.containsKey(t)) {
                        tileToIndex.put(t, palette.size());
                        palette.add(t);
                    }
                }
            }
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeFloat(tilemap.getTileSize());
            dos.writeInt(tilemap.getZIndex());

            dos.writeInt(palette.size());
            for (Tile t : palette) {
                dos.writeUTF(t.name() != null ? t.name() : "");
                dos.writeUTF(serializeSpriteRef(t.sprite()));
                dos.writeBoolean(t.solid());
                dos.writeByte(t.ledgeDirection().ordinal());
            }

            Collection<TileChunk> chunks = tilemap.allChunks();
            dos.writeInt(chunks.size());
            for (TileChunk chunk : chunks) {
                dos.writeInt(chunk.getChunkX());
                dos.writeInt(chunk.getChunkY());
                dos.writeInt(chunk.getTileCount());

                for (int x = 0; x < TileChunk.CHUNK_SIZE; x++) {
                    for (int y = 0; y < TileChunk.CHUNK_SIZE; y++) {
                        Tile t = chunk.get(x, y);
                        if (t != null) {
                            dos.writeByte(x);
                            dos.writeByte(y);
                            dos.writeInt(tileToIndex.get(t));
                        }
                    }
                }
            }
            out.value(Base64.getEncoder().encodeToString(baos.toByteArray()));
        }
    }

    private TilemapRenderer readTilemapBinary(String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            float size = dis.readFloat();
            int z = dis.readInt();
            TilemapRenderer tilemap = new TilemapRenderer(size);
            tilemap.setZIndex(z);

            int palSize = dis.readInt();
            Tile[] palette = new Tile[palSize];
            for (int i = 0; i < palSize; i++) {
                String name = dis.readUTF();
                String spriteName = dis.readUTF();
                boolean solid = dis.readBoolean();
                LedgeDirection ledge = LedgeDirection.values()[dis.readByte()];
                palette[i] = new Tile(name, resolveSpriteRef(spriteName), solid, ledge);
            }

            int chunkCount = dis.readInt();
            for (int i = 0; i < chunkCount; i++) {
                int cx = dis.readInt();
                int cy = dis.readInt();
                int tCount = dis.readInt();
                for (int j = 0; j < tCount; j++) {
                    int tx = dis.readByte();
                    int ty = dis.readByte();
                    int palIdx = dis.readInt();
                    tilemap.set(cx * TileChunk.CHUNK_SIZE + tx, cy * TileChunk.CHUNK_SIZE + ty, palette[palIdx]);
                }
            }
            return tilemap;
        }
    }

    private TilemapRenderer readLegacyTilemap(JsonElement props, Gson gson) {
        JsonObject json = props.getAsJsonObject();
        TilemapRenderer tm = new TilemapRenderer(json.has("tileSize") ? json.get("tileSize").getAsFloat() : 1.0f);
        tm.setZIndex(json.has("zIndex") ? json.get("zIndex").getAsInt() : 0);

        if (json.has("chunks")) {
            JsonObject chunks = json.getAsJsonObject("chunks");
            java.lang.reflect.Type type = new TypeToken<Map<String, Tile>>(){}.getType();
            for (Map.Entry<String, JsonElement> entry : chunks.entrySet()) {
                String[] c = entry.getKey().split(",");
                int cx = Integer.parseInt(c[0]), cy = Integer.parseInt(c[1]);
                Map<String, Tile> tiles = gson.fromJson(entry.getValue(), type);
                tiles.forEach((pos, tile) -> {
                    String[] p = pos.split(",");
                    tm.set(cx * 32 + Integer.parseInt(p[0]), cy * 32 + Integer.parseInt(p[1]), tile);
                });
            }
        }
        return tm;
    }

    private String serializeSpriteRef(Sprite sprite) {
        if (sprite == null) return "";
        String path = SpriteReference.toPath(sprite);
        return path != null ? path : "";
    }

    private Sprite resolveSpriteRef(String ref) {
        return SpriteReference.fromPath(ref);
    }
}
