package com.pocket.rpg.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.scenes.Scene;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Handles serialization and deserialization of scenes to/from JSON files.
 * 
 * Usage:
 * <pre>
 * // Save a scene
 * SceneSerializer serializer = new SceneSerializer(assetManager);
 * serializer.save(scene, Path.of("gameData/scenes/village.scene"));
 * 
 * // Load scene data (for editor)
 * SceneData data = serializer.load(Path.of("gameData/scenes/village.scene"));
 * 
 * // Or use SceneLoader for full game-side instantiation
 * </pre>
 */
public class SceneSerializer {
    
    private final Gson gson;
    private final AssetManager assetManager;

    /**
     * Creates a SceneSerializer with the given AssetManager.
     * 
     * @param assetManager AssetManager for resolving asset paths during load
     */
    public SceneSerializer(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.gson = createGson(assetManager);
    }

    /**
     * Creates a SceneSerializer without AssetManager (for save-only or editor use).
     */
    public SceneSerializer() {
        this(null);
    }

    /**
     * Creates a configured Gson instance with all necessary type adapters.
     */
    public static Gson createGson(AssetManager assetManager) {
        return new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            // Component polymorphism
            .registerTypeAdapter(Component.class, new ComponentSerializer())
            .registerTypeAdapter(Component.class, new ComponentDeserializer())
            // Asset types
            .registerTypeAdapter(Sprite.class, new SpriteTypeAdapter(assetManager))
            .registerTypeAdapter(Texture.class, new TextureTypeAdapter(assetManager))
            // JOML vectors
            .registerTypeAdapter(Vector2f.class, new Vector2fTypeAdapter())
            .registerTypeAdapter(Vector3f.class, new Vector3fTypeAdapter())
            .registerTypeAdapter(Vector4f.class, new Vector4fTypeAdapter())
            .create();
    }

    // ========================================================================
    // SAVE
    // ========================================================================

    /**
     * Saves a scene to a JSON file.
     * 
     * @param scene Scene to save
     * @param path  Target file path
     * @throws IOException if file cannot be written
     */
    public void save(Scene scene, Path path) throws IOException {
        SceneData data = toSceneData(scene);
        save(data, path);
    }

    /**
     * Saves SceneData to a JSON file.
     * 
     * @param data SceneData to save
     * @param path Target file path
     * @throws IOException if file cannot be written
     */
    public void save(SceneData data, Path path) throws IOException {
        String json = gson.toJson(data);
        
        // Ensure parent directories exist
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        
        Files.writeString(path, json);
    }

    /**
     * Converts a Scene to SceneData for serialization.
     */
    public SceneData toSceneData(Scene scene) {
        SceneData data = new SceneData(scene.getName());
        
        // Convert root-level GameObjects (those without parents)
        for (GameObject go : scene.getGameObjects()) {
            if (go.getParent() == null) {
                data.addGameObject(toGameObjectData(go));
            }
        }
        
        // TODO: Add camera data from scene's main camera
        
        return data;
    }

    /**
     * Converts a GameObject to GameObjectData, including children recursively.
     */
    private GameObjectData toGameObjectData(GameObject go) {
        GameObjectData data = new GameObjectData(go.getName());
//        data.setActive(go.isActive());
//        data.setTag(go.getTag());
        
        // Add all components
        for (Component component : go.getComponents()) {
            data.addComponent(component);
        }
        
        // Recursively add children
        for (GameObject child : go.getChildren()) {
            data.addChild(toGameObjectData(child));
        }
        
        return data;
    }

    // ========================================================================
    // LOAD
    // ========================================================================

    /**
     * Loads SceneData from a JSON file.
     * 
     * @param path Path to the .scene file
     * @return Loaded SceneData
     * @throws IOException if file cannot be read
     */
    public SceneData load(Path path) throws IOException {
        String json = Files.readString(path);
        return gson.fromJson(json, SceneData.class);
    }

    /**
     * Loads SceneData from a JSON string.
     * 
     * @param json JSON string
     * @return Loaded SceneData
     */
    public SceneData loadFromString(String json) {
        return gson.fromJson(json, SceneData.class);
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Returns the Gson instance for direct use if needed.
     */
    public Gson getGson() {
        return gson;
    }

    /**
     * Serializes SceneData to a JSON string (without saving to file).
     */
    public String toJson(SceneData data) {
        return gson.toJson(data);
    }

    /**
     * Serializes a Scene to a JSON string (without saving to file).
     */
    public String toJson(Scene scene) {
        return toJson(toSceneData(scene));
    }
}
