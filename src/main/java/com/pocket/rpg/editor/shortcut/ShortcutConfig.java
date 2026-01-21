package com.pocket.rpg.editor.shortcut;

import com.google.gson.*;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration file for shortcut bindings.
 * Stored as JSON in editor/config/editorShortcuts.json
 * <p>
 * Stores bindings for BOTH keyboard layouts. The active layout is selected
 * via the keyboardLayout field. Switching layouts automatically uses the
 * appropriate set of bindings.
 */
@Getter
@Setter
public class ShortcutConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ShortcutBinding.class, new ShortcutBindingAdapter())
            .create();

    /**
     * Currently active keyboard layout.
     */
    private KeyboardLayout keyboardLayout = KeyboardLayout.QWERTY;

    /**
     * QWERTY keyboard layout bindings.
     */
    private Map<String, ShortcutBinding> qwertyBindings = new HashMap<>();

    /**
     * AZERTY keyboard layout bindings.
     */
    private Map<String, ShortcutBinding> azertyBindings = new HashMap<>();

    /**
     * Gets the bindings for the currently active keyboard layout.
     */
    public Map<String, ShortcutBinding> getActiveBindings() {
        return keyboardLayout == KeyboardLayout.AZERTY ? azertyBindings : qwertyBindings;
    }

    /**
     * Gets the bindings for a specific keyboard layout.
     */
    public Map<String, ShortcutBinding> getBindingsForLayout(KeyboardLayout layout) {
        return layout == KeyboardLayout.AZERTY ? azertyBindings : qwertyBindings;
    }

    /**
     * Sets bindings for a specific keyboard layout.
     */
    public void setBindingsForLayout(KeyboardLayout layout, Map<String, ShortcutBinding> bindings) {
        if (layout == KeyboardLayout.AZERTY) {
            this.azertyBindings = bindings;
        } else {
            this.qwertyBindings = bindings;
        }
    }

    // Legacy compatibility - redirect to active bindings
    @Deprecated
    public Map<String, ShortcutBinding> getBindings() {
        return getActiveBindings();
    }

    @Deprecated
    public void setBindings(Map<String, ShortcutBinding> bindings) {
        setBindingsForLayout(keyboardLayout, bindings);
    }

    /**
     * Loads config from file, returns empty config if file doesn't exist or is invalid.
     * Handles migration from legacy format (single "bindings" field) to new format
     * (separate "qwertyBindings" and "azertyBindings" fields).
     */
    public static ShortcutConfig load(String path) {
        Path filePath = Path.of(path);

        if (!Files.exists(filePath)) {
            // Config file doesn't exist, using defaults (this is normal on first run)
            return new ShortcutConfig();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            // Parse as JsonObject first to detect format
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            ShortcutConfig config = new ShortcutConfig();

            // Read keyboard layout
            if (json.has("keyboardLayout")) {
                String layoutStr = json.get("keyboardLayout").getAsString();
                config.setKeyboardLayout(KeyboardLayout.valueOf(layoutStr));
            }

            // Check for new format
            if (json.has("qwertyBindings") || json.has("azertyBindings")) {
                // New format - read both layouts
                if (json.has("qwertyBindings")) {
                    config.qwertyBindings = parseBindingsMap(json.getAsJsonObject("qwertyBindings"));
                }
                if (json.has("azertyBindings")) {
                    config.azertyBindings = parseBindingsMap(json.getAsJsonObject("azertyBindings"));
                }
            } else if (json.has("bindings")) {
                // Legacy format - migrate single "bindings" field to active layout
                Map<String, ShortcutBinding> legacyBindings = parseBindingsMap(json.getAsJsonObject("bindings"));
                config.setBindingsForLayout(config.getKeyboardLayout(), legacyBindings);
                System.out.println("[ShortcutConfig] Migrated legacy config format to dual-layout format");
            }

            return config;
        } catch (Exception e) {
            System.err.println("[ShortcutConfig] Failed to load shortcut config from " + path + ": " + e.getMessage());
            return new ShortcutConfig();
        }
    }

    /**
     * Parses a JSON object as a bindings map.
     */
    private static Map<String, ShortcutBinding> parseBindingsMap(JsonObject json) {
        Map<String, ShortcutBinding> bindings = new HashMap<>();
        if (json == null) {
            return bindings;
        }

        for (String key : json.keySet()) {
            JsonElement value = json.get(key);
            if (value.isJsonNull()) {
                bindings.put(key, null);
            } else {
                ShortcutBinding binding = ShortcutBinding.fromConfigString(value.getAsString());
                bindings.put(key, binding);
            }
        }

        return bindings;
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
        } catch (Exception e) {
            System.err.println("[ShortcutConfig] Failed to save shortcut config to " + path + ": " + e.getMessage());
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
