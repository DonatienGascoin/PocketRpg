package com.pocket.rpg.serialization.custom;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.resources.AssetContext;

import java.io.IOException;
import java.util.*;

/**
 * Generic TypeAdapterFactory that automatically handles asset serialization.
 * <p>
 * For any object loaded through Assets, serializes as path reference.
 * For non-assets, delegates to default Gson serialization.
 * <p>
 * Performance optimizations:
 * - Returns null for primitives and common non-asset types (Gson uses default handling)
 * - Lazy delegate adapter creation
 * - Early type filtering to avoid unnecessary HashMap lookups
 * <p>
 * IMPORTANT: Must be registered FIRST in Gson builder, before other adapters,
 * so it can delegate to them when needed.
 */
public class AssetReferenceTypeAdapterFactory implements TypeAdapterFactory {

    private final AssetContext assetContext;

    // Common non-asset types to skip entirely
    private static final Set<Class<?>> SKIP_TYPES = Set.of(
            // Primitives wrappers
            Boolean.class, Byte.class, Short.class, Integer.class,
            Long.class, Float.class, Double.class, Character.class,
            // Common types
            String.class,
            // Collections
            List.class, ArrayList.class, LinkedList.class,
            Set.class, HashSet.class, LinkedHashSet.class, TreeSet.class,
            Map.class, HashMap.class, LinkedHashMap.class, TreeMap.class,
            // Sprites are handled by their own adapter to accommodate for spritesheets
            Sprite.class
    );

    public AssetReferenceTypeAdapterFactory(AssetContext assetContext) {
        this.assetContext = assetContext;
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> rawType = type.getRawType();

        // PERFORMANCE: Skip primitives and common types entirely
        if (rawType.isPrimitive() || SKIP_TYPES.contains(rawType)) {
            return null; // Let Gson handle these with default adapters
        }

        // Skip enums - let Gson handle them
        if (rawType.isEnum()) {
            return null;
        }

        // PERFORMANCE: Skip java.* and javax.* packages (unlikely to be assets)
        String packageName = rawType.getPackage() != null ? rawType.getPackage().getName() : "";
        if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
            return null;
        }

        // PERFORMANCE: Lazy delegate creation - only create when first needed
        return new TypeAdapter<T>() {
            private TypeAdapter<T> delegate;

            private TypeAdapter<T> getDelegate() {
                if (delegate == null) {
                    delegate = gson.getDelegateAdapter(AssetReferenceTypeAdapterFactory.this, type);
                }
                return delegate;
            }

            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }

                // PERFORMANCE: Single HashMap lookup to check if this is an asset
                String path = assetContext.getPathForResource(value);

                if (path != null) {
                    // Serialize as path reference (relative path)
                    out.value(path);
                } else {
                    // Not an asset - use default serialization
                    getDelegate().write(out, value);
                }
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }

                // PERFORMANCE: Only try asset loading for STRING tokens
                if (in.peek() == JsonToken.STRING) {
                    String stringValue = in.nextString();

                    // PERFORMANCE: Quick check - if target type is String, don't try loading as asset
                    if (rawType == String.class) {
                        @SuppressWarnings("unchecked")
                        T result = (T) stringValue;
                        return result;
                    }

                    try {
                        // Try to load as asset path
                        @SuppressWarnings("unchecked")
                        T asset = (T) assetContext.load(stringValue, rawType);

                        if (asset != null) {
                            return asset;
                        }
                    } catch (Exception e) {
                        // Not a valid asset path - this is an error for non-String types
                        throw new IOException("Failed to load asset from path: " + stringValue, e);
                    }
                }

                // Not a string token - use default deserialization
                return getDelegate().read(in);
            }
        };
    }
}