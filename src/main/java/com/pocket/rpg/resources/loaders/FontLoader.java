package com.pocket.rpg.resources.loaders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.AssetLoader;
import com.pocket.rpg.ui.text.Font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loader for font definitions from JSON files.
 * <p>
 * JSON Format:
 * <pre>
 * {
 *   "file": "fonts/arial.ttf",
 *   "size": 24
 * }
 * </pre>
 * <p>
 * Optional fields:
 * <pre>
 * {
 *   "file": "fonts/arial.ttf",
 *   "size": 24,
 *   "name": "Arial 24px"
 * }
 * </pre>
 * <p>
 * The font file path is resolved relative to the JSON file's directory,
 * or as an absolute path.
 * Multiple .font.json files can reference the same TTF at different sizes.
 */
public class FontLoader implements AssetLoader<Font> {

    private static final int DEFAULT_SIZE = 24;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public Font load(String path) throws IOException {
        String lower = path.toLowerCase();

        // Handle raw TTF/OTF files directly
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            return new Font(path, DEFAULT_SIZE);
        }

        // Handle JSON definition files
        String jsonContent = new String(Files.readAllBytes(Paths.get(path)));
        JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();

        // Parse font file path
        if (!json.has("file")) {
            throw new IOException("Font definition missing 'file' field: " + path);
        }
        String fontFile = json.get("file").getAsString();

        // Resolve font file path relative to JSON file's directory
        String resolvedPath = resolveFontPath(path, fontFile);

        // Parse size (default to 16 if not specified)
        int size = json.has("size") ? json.get("size").getAsInt() : DEFAULT_SIZE;

        // Create and return font
        return new Font(resolvedPath, size);
    }

    /**
     * Resolves the font file path.
     * First tries as absolute/asset-root-relative path.
     * Then tries relative to the JSON file's directory.
     */
    private String resolveFontPath(String jsonPath, String fontFile) {
        // Try as-is first (absolute or relative to working directory)
        if (Files.exists(Paths.get(fontFile))) {
            return fontFile;
        }

        // Try relative to JSON file's directory
        Path jsonDir = Paths.get(jsonPath).getParent();
        if (jsonDir != null) {
            Path relativePath = jsonDir.resolve(fontFile);
            if (Files.exists(relativePath)) {
                return relativePath.toString();
            }
        }

        // Return original (Font constructor will throw if not found)
        return fontFile;
    }

    @Override
    public void save(Font font, String path) throws IOException {
        String lower = path.toLowerCase();

        // Cannot save to raw TTF/OTF files - need a JSON definition file
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            // Change extension to .font.json
            path = path.substring(0, path.lastIndexOf('.')) + ".font.json";
        }

        JsonObject json = new JsonObject();

        // Get font file path (relative if possible)
        String fontFile = getFontFilePath(font, path);
        json.addProperty("file", fontFile);

        // Add size
        json.addProperty("size", font.getSize());

        // Write to file
        String jsonString = gson.toJson(json);
        Path filePath = Paths.get(path);

        // Create parent directories if they don't exist
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.write(filePath, jsonString.getBytes());
    }

    @Override
    public Font getPlaceholder() {
        return null;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".font", ".font.json", ".ttf", ".otf"};
    }

    @Override
    public boolean supportsHotReload() {
        return true;
    }

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.TextFields;
    }

    @Override
    public Font reload(Font existing, String path) throws IOException {
        // Destroy old font resources
        if (existing != null) {
            existing.destroy();
        }
        // Load fresh
        return load(path);
    }

    /**
     * Gets the font file path for saving.
     * Attempts to make it relative to the JSON file's directory.
     */
    private String getFontFilePath(Font font, String jsonPath) {
        String fontPath = font.getPath();
        if (fontPath == null) {
            return "font.ttf";
        }

        // Try to make relative to JSON file's directory
        try {
            Path jsonDir = Paths.get(jsonPath).getParent();
            if (jsonDir != null) {
                Path fontAbsolute = Paths.get(fontPath).toAbsolutePath();
                Path jsonAbsolute = jsonDir.toAbsolutePath();
                Path relative = jsonAbsolute.relativize(fontAbsolute);
                return relative.toString().replace('\\', '/');
            }
        } catch (Exception e) {
            // Fall back to original path
        }

        return fontPath;
    }
}