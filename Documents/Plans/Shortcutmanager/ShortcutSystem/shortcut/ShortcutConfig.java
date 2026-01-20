package com.pocket.rpg.editor.shortcut;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration file for custom shortcut bindings.
 * Stored as JSON in editor/editorShortcuts.json
 */
@Slf4j
@Getter
@Setter
public class ShortcutConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ShortcutBinding.class, new ShortcutBindingAdapter())
            .create();

    private Map<String, ShortcutBinding> bindings = new HashMap<>();

    /**
     * Loads config from file, returns null if file doesn't exist or is invalid.
     */
    public static ShortcutConfig load(String path) {
        Path filePath = Path.of(path);

        if (!Files.exists(filePath)) {
            log.debug("Shortcut config not found at {}, using defaults", path);
            return new ShortcutConfig();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            ShortcutConfig config = GSON.fromJson(reader, ShortcutConfig.class);
            return config != null ? config : new ShortcutConfig();
        } catch (Exception e) {
            log.error("Failed to load shortcut config from {}", path, e);
            return new ShortcutConfig();
        }
    }

    /**
     * Saves config to file.
     */
    public void save(String path) {
        Path filePath = Path.of(path);

        try {
            // Ensure parent directory exists
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Writer writer = Files.newBufferedWriter(filePath)) {
                GSON.toJson(this, writer);
            }

            log.debug("Saved shortcut config to {}", path);
        } catch (Exception e) {
            log.error("Failed to save shortcut config to {}", path, e);
        }
    }

    /**
     * Custom Gson adapter for ShortcutBinding.
     * Serializes to compact string format.
     */
    private static class ShortcutBindingAdapter implements JsonSerializer<ShortcutBinding>, JsonDeserializer<ShortcutBinding> {

        @Override
        public JsonElement serialize(ShortcutBinding binding, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(binding.toConfigString());
        }

        @Override
        public ShortcutBinding deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            if (json.isJsonNull()) {
                return null;
            }

            String str = json.getAsString();
            ShortcutBinding binding = ShortcutBinding.fromConfigString(str);

            if (binding == null) {
                throw new JsonParseException("Invalid shortcut binding: " + str);
            }

            return binding;
        }
    }
}
