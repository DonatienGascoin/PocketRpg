package com.pocket.rpg.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Centralized access to asset metadata files.
 * <p>
 * Metadata is stored separately from assets in {@code gameData/.metadata/}.
 * This keeps the asset folder clean and prevents metadata files from being
 * scanned by the Asset Browser.
 * <p>
 * Path mapping:
 * <ul>
 *   <li>{@code sprites/player.png} &rarr; {@code gameData/.metadata/sprites/player.png.meta}</li>
 *   <li>{@code sprites/characters/hero.png} &rarr; {@code gameData/.metadata/sprites/characters/hero.png.meta}</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * // Load metadata (returns null if not found)
 * SpriteMetadata meta = AssetMetadata.load("sprites/player.png", SpriteMetadata.class);
 *
 * // Load with default
 * SpriteMetadata meta = AssetMetadata.loadOrDefault("sprites/player.png",
 *     SpriteMetadata.class, SpriteMetadata::new);
 *
 * // Save metadata
 * meta.pivotX = 0.5f;
 * meta.pivotY = 0.0f;
 * AssetMetadata.save("sprites/player.png", meta);
 * </pre>
 */
public final class AssetMetadata {

    /**
     * Root directory for metadata files, relative to working directory.
     */
    private static final String METADATA_ROOT = "gameData/.metadata/";

    /**
     * Extension for metadata files.
     */
    private static final String METADATA_EXTENSION = ".meta";

    /**
     * Gson instance for serialization.
     */
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private AssetMetadata() {
        // Private constructor - static utility class
    }

    /**
     * Gets the metadata file path for an asset.
     *
     * @param assetPath Relative asset path (e.g., "sprites/player.png")
     * @return Metadata file path (e.g., "gameData/.metadata/sprites/player.png.meta")
     */
    public static String getMetadataPath(String assetPath) {
        if (assetPath == null || assetPath.isEmpty()) {
            throw new IllegalArgumentException("Asset path cannot be null or empty");
        }
        // Normalize path separators
        String normalized = assetPath.replace('\\', '/');
        return METADATA_ROOT + normalized + METADATA_EXTENSION;
    }

    /**
     * Checks if metadata exists for an asset.
     *
     * @param assetPath Relative asset path
     * @return true if metadata file exists
     */
    public static boolean exists(String assetPath) {
        return Files.exists(Path.of(getMetadataPath(assetPath)));
    }

    /**
     * Loads metadata for an asset.
     *
     * @param assetPath     Relative asset path
     * @param metadataClass The metadata class to deserialize to
     * @param <T>           Metadata type
     * @return Loaded metadata, or null if file doesn't exist
     * @throws RuntimeException if file exists but cannot be read/parsed
     */
    public static <T> T load(String assetPath, Class<T> metadataClass) {
        Path metaPath = Path.of(getMetadataPath(assetPath));

        if (!Files.exists(metaPath)) {
            return null;
        }

        try {
            String json = Files.readString(metaPath);
            return gson.fromJson(json, metadataClass);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read metadata for: " + assetPath, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse metadata for: " + assetPath, e);
        }
    }

    /**
     * Loads metadata or returns a default instance if none exists.
     *
     * @param assetPath       Relative asset path
     * @param metadataClass   The metadata class to deserialize to
     * @param defaultSupplier Supplier for default instance
     * @param <T>             Metadata type
     * @return Loaded metadata, or new default instance if not found
     */
    public static <T> T loadOrDefault(String assetPath, Class<T> metadataClass, Supplier<T> defaultSupplier) {
        T loaded = load(assetPath, metadataClass);
        return loaded != null ? loaded : defaultSupplier.get();
    }

    /**
     * Saves metadata for an asset.
     * Creates parent directories if they don't exist.
     *
     * @param assetPath Relative asset path
     * @param metadata  Metadata object to save
     * @throws IOException if saving fails
     */
    public static void save(String assetPath, Object metadata) throws IOException {
        Path metaPath = Path.of(getMetadataPath(assetPath));

        // Create parent directories if needed
        Path parentDir = metaPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Serialize and write
        String json = gson.toJson(metadata);
        Files.writeString(metaPath, json);
    }

    /**
     * Saves metadata for an asset, or deletes the metadata file if metadata is empty.
     * This keeps the metadata folder clean by not storing unnecessary files.
     *
     * @param assetPath Relative asset path
     * @param metadata  Metadata object to save (must have isEmpty() method)
     * @throws IOException if saving/deleting fails
     */
    public static void saveOrDelete(String assetPath, SpriteMetadata metadata) throws IOException {
        if (metadata == null || metadata.isEmpty()) {
            delete(assetPath);
        } else {
            save(assetPath, metadata);
        }
    }

    /**
     * Deletes metadata for an asset if it exists.
     *
     * @param assetPath Relative asset path
     * @return true if file was deleted, false if it didn't exist
     */
    public static boolean delete(String assetPath) {
        Path metaPath = Path.of(getMetadataPath(assetPath));
        try {
            return Files.deleteIfExists(metaPath);
        } catch (IOException e) {
            System.err.println("Failed to delete metadata for: " + assetPath + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the metadata root directory path.
     *
     * @return Metadata root path
     */
    public static String getMetadataRoot() {
        return METADATA_ROOT;
    }
}
