package com.pocket.rpg.editor.utils;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.LoadOptions;
import com.pocket.rpg.serialization.GameObjectData;
import com.pocket.rpg.serialization.SceneData;

import java.io.File;
import java.util.*;

/**
 * Utility methods for working with scenes in the editor.
 * Provides scene discovery and cross-scene data access.
 */
public final class SceneUtils {

    private static final String SCENES_DIRECTORY = "gameData/scenes";
    private static final String SCENE_EXTENSION = ".scene";

    // Cache for spawn points per scene (cleared when scene is modified)
    private static final Map<String, List<String>> spawnPointCache = new HashMap<>();

    private SceneUtils() {
        // Utility class
    }

    /**
     * Gets all available scene names from the scenes directory.
     * Names are returned without the .scene extension.
     * Uses Assets.scanAll for consistent asset discovery.
     *
     * @return List of scene names, sorted alphabetically
     */
    public static List<String> getAvailableSceneNames() {
        List<String> scenes = new ArrayList<>();

        try {
            // Use Assets.scanAll to find all loadable files in the scenes directory
            List<String> files = Assets.scanAll(SCENES_DIRECTORY);

            for (String file : files) {
                if (file.endsWith(SCENE_EXTENSION)) {
                    // Remove .scene extension and any path prefix
                    String name = file;
                    int lastSlash = name.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        name = name.substring(lastSlash + 1);
                    }
                    name = name.substring(0, name.length() - SCENE_EXTENSION.length());
                    scenes.add(name);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to scan scenes directory: " + e.getMessage());
        }

        Collections.sort(scenes);
        return scenes;
    }

    /**
     * Gets the spawn point IDs from a scene.
     * Always loads fresh to avoid stale data when spawn points are added/removed.
     *
     * @param sceneName Scene name (without .scene extension)
     * @return List of spawn point IDs, or empty list if scene not found
     */
    public static List<String> getSpawnPoints(String sceneName) {
        if (sceneName == null || sceneName.isBlank()) {
            return Collections.emptyList();
        }

        // Always load fresh - spawn points may have been added/removed
        return loadSpawnPointsFromScene(sceneName);
    }

    /**
     * Clears the spawn point cache for a specific scene.
     * Call this when a scene is saved.
     *
     * @param sceneName Scene name to invalidate
     */
    public static void invalidateCache(String sceneName) {
        spawnPointCache.remove(sceneName);
    }

    /**
     * Clears all cached data.
     */
    public static void clearCache() {
        spawnPointCache.clear();
    }

    /**
     * Loads spawn points directly from a scene file.
     * Scans entity-based SpawnPoint components.
     */
    private static List<String> loadSpawnPointsFromScene(String sceneName) {
        Set<String> spawnPoints = new HashSet<>();

        String path = SCENES_DIRECTORY + "/" + sceneName + SCENE_EXTENSION;
        File file = new File(path);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try {
            // Load scene data without caching (we only need spawn points)
            SceneData sceneData = Assets.load(path, LoadOptions.rawUncached());
            if (sceneData == null) {
                return new ArrayList<>();
            }

            // Extract spawn points from entity-based SpawnPoint components
            if (sceneData.getGameObjects() != null) {
                for (GameObjectData gameObjectData : sceneData.getGameObjects()) {
                    String spawnId = extractSpawnPointId(gameObjectData);
                    if (spawnId != null && !spawnId.isBlank()) {
                        spawnPoints.add(spawnId);
                    }
                }
            }

            List<String> sorted = new ArrayList<>(spawnPoints);
            Collections.sort(sorted);
            return sorted;
        } catch (Exception e) {
            System.err.println("Failed to load spawn points from scene '" + sceneName + "': " + e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * Extracts SpawnPoint ID from a GameObjectData.
     * Handles both scratch entities (components list) and prefab instances (overrides + prefab defaults).
     */
    private static String extractSpawnPointId(GameObjectData data) {
        if (data.isScratchEntity()) {
            // Check components list for SpawnPoint
            SpawnPoint spawn = data.getComponent(SpawnPoint.class);
            if (spawn != null) {
                return spawn.getSpawnId();
            }
        } else {
            // Prefab instance: check overrides first, then prefab default
            String spawnPointType = "com.pocket.rpg.components.interaction.SpawnPoint";

            // Check overrides
            var overrides = data.getComponentOverrides();
            if (overrides != null) {
                var spawnOverrides = overrides.get(spawnPointType);
                if (spawnOverrides != null && spawnOverrides.containsKey("spawnId")) {
                    Object id = spawnOverrides.get("spawnId");
                    if (id instanceof String s) {
                        return s;
                    }
                }
            }

            // Fall back to prefab default
            String prefabId = data.getPrefabId();
            if (prefabId != null) {
                Prefab prefab = PrefabRegistry.getInstance().getPrefab(prefabId);
                if (prefab != null) {
                    for (Component comp : prefab.getComponents()) {
                        if (comp instanceof SpawnPoint spawn) {
                            return spawn.getSpawnId();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks if a scene exists.
     *
     * @param sceneName Scene name (without .scene extension)
     * @return true if the scene file exists
     */
    public static boolean sceneExists(String sceneName) {
        if (sceneName == null || sceneName.isBlank()) {
            return false;
        }
        String path = SCENES_DIRECTORY + "/" + sceneName + SCENE_EXTENSION;
        return new File(path).exists();
    }

    /**
     * Gets the full path to a scene file.
     *
     * @param sceneName Scene name (without .scene extension)
     * @return Full path to the scene file
     */
    public static String getScenePath(String sceneName) {
        return SCENES_DIRECTORY + "/" + sceneName + SCENE_EXTENSION;
    }
}
