package com.pocket.rpg.serialization;

import com.pocket.rpg.resources.AssetManager;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.prefabs.PrefabRegistry;
import com.pocket.rpg.scenes.RuntimeScene;
import com.pocket.rpg.scenes.Scene;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Game-side scene loader that converts SceneData into a fully instantiated Scene.
 * 
 * Handles:
 * - Creating GameObjects from serialized data
 * - Reconstructing parent-child hierarchy
 * - Re-wiring component references (gameObject)
 * - Resolving prefab instances (if PrefabRegistry provided)
 * 
 * Usage:
 * <pre>
 * SceneLoader loader = new SceneLoader(assetManager, prefabRegistry);
 * Scene scene = loader.load(Path.of("gameData/scenes/village.scene"), viewportConfig, renderingConfig);
 * </pre>
 */
public class SceneLoader {
    
    private final AssetManager assetManager;
    private final PrefabRegistry prefabRegistry;
    private final SceneSerializer serializer;

    /**
     * Creates a SceneLoader with asset and prefab support.
     * 
     * @param assetManager AssetManager for loading assets
     * @param prefabRegistry PrefabRegistry for instantiating prefabs (can be null)
     */
    public SceneLoader(AssetManager assetManager, PrefabRegistry prefabRegistry) {
        this.assetManager = assetManager;
        this.prefabRegistry = prefabRegistry;
        this.serializer = new SceneSerializer(assetManager);
    }

    /**
     * Creates a SceneLoader without prefab support.
     */
    public SceneLoader(AssetManager assetManager) {
        this(assetManager, null);
    }

    /**
     * Loads a scene from a .scene file.
     * 
     * @param path Path to the scene file
     * @param viewportConfig Viewport configuration for the scene
     * @param renderingConfig Rendering configuration
     * @return Fully instantiated Scene
     * @throws IOException if file cannot be read
     */
    public Scene load(Path path, ViewportConfig viewportConfig, RenderingConfig renderingConfig) 
            throws IOException {
        SceneData data = serializer.load(path);
        return instantiate(data, viewportConfig, renderingConfig);
    }

    /**
     * Instantiates a Scene from SceneData.
     * 
     * @param data SceneData to instantiate
     * @param viewportConfig Viewport configuration
     * @param renderingConfig Rendering configuration
     * @return Fully instantiated Scene
     */
    public Scene instantiate(SceneData data, ViewportConfig viewportConfig, RenderingConfig renderingConfig) {
        // Create the scene using RuntimeScene (concrete implementation)
        RuntimeScene scene = new RuntimeScene(data.getName());
        
        // Instantiate all root-level GameObjects BEFORE initialization
        // This ensures they get proper start() calls during initialize()
        for (GameObjectData goData : data.getGameObjects()) {
            GameObject go = instantiateGameObject(goData, null);
            scene.addGameObject(go);
        }
        
        // Initialize scene (creates camera, calls start() on all GameObjects)
        scene.initialize(viewportConfig, renderingConfig);
        
        // Apply camera settings if present
        if (data.getCamera() != null) {
            applyCameraSettings(scene, data.getCamera());
        }
        
        return scene;
    }

    /**
     * Instantiates a GameObject from GameObjectData, recursively creating children.
     * 
     * @param data GameObjectData to instantiate
     * @param parent Parent GameObject (null for root objects)
     * @return Instantiated GameObject
     */
    private GameObject instantiateGameObject(GameObjectData data, GameObject parent) {
        GameObject go = new GameObject(data.getName());
        go.setEnabled(data.isActive());
        
        // Set parent first (so Transform hierarchy works)
        if (parent != null) {
            go.setParent(parent);
        }
        
        // Add all components (skip Transform as it's auto-created)
        for (Component component : data.getComponents()) {
            // Skip Transform - GameObject creates its own
            if (component instanceof com.pocket.rpg.components.Transform) {
                // Copy transform values to the existing transform
                com.pocket.rpg.components.Transform srcTransform = 
                    (com.pocket.rpg.components.Transform) component;
                go.getTransform().setPosition(srcTransform.getPosition());
                go.getTransform().setScale(srcTransform.getScale());
                go.getTransform().setRotation(srcTransform.getRotation());
                continue;
            }
            
            // Clone the component to avoid sharing between GameObjects
            // For now, directly add (components from deserialization are unique)
            wireComponent(go, component);
            go.addComponent(component);
        }
        
        // Recursively instantiate children
        for (GameObjectData childData : data.getChildren()) {
            instantiateGameObject(childData, go);
            // Child's setParent() already adds it to go's children
        }
        
        return go;
    }

    /**
     * Wires transient references in a component after deserialization.
     */
    private void wireComponent(GameObject go, Component component) {
        // Component.gameObject is set by addComponent(), but we ensure it here
        // in case the component needs pre-add wiring
    }

    /**
     * Applies camera settings from SceneData to the scene.
     */
    private void applyCameraSettings(Scene scene, SceneData.CameraData cameraData) {
        if (scene.getCamera() != null && cameraData != null) {
            float[] pos = cameraData.getPosition();
            if (pos != null && pos.length >= 2) {
                scene.getCamera().setPosition(pos[0], pos[1]);
            }
            scene.getCamera().setOrthographicSize(cameraData.getOrthographicSize());
        }
    }
}
