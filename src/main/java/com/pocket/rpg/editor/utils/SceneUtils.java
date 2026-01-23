package com.pocket.rpg.editor.utils;

import com.pocket.rpg.collision.trigger.SpawnPointData;
import com.pocket.rpg.collision.trigger.TriggerData;
import com.pocket.rpg.collision.trigger.TriggerDataMap;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.LoadOptions;
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
     * Results are cached for performance.
     *
     * @param sceneName Scene name (without .scene extension)
     * @return List of spawn point IDs, or empty list if scene not found
     */
    public static List<String> getSpawnPoints(String sceneName) {
        if (sceneName == null || sceneName.isBlank()) {
            return Collections.emptyList();
        }

        // Check cache first
        if (spawnPointCache.containsKey(sceneName)) {
            return spawnPointCache.get(sceneName);
        }

        List<String> spawnPoints = loadSpawnPointsFromScene(sceneName);
        spawnPointCache.put(sceneName, spawnPoints);
        return spawnPoints;
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
     */
    private static List<String> loadSpawnPointsFromScene(String sceneName) {
        List<String> spawnPoints = new ArrayList<>();

        String path = SCENES_DIRECTORY + "/" + sceneName + SCENE_EXTENSION;
        File file = new File(path);
        if (!file.exists()) {
            return spawnPoints;
        }

        try {
            // Load scene data without caching (we only need spawn points)
            SceneData sceneData = Assets.load(path, LoadOptions.rawUncached());
            if (sceneData == null) {
                return spawnPoints;
            }

            // Extract spawn points from trigger data
            if (sceneData.getTriggerData() != null) {
                TriggerDataMap triggerMap = new TriggerDataMap();
                triggerMap.fromSerializableMap(sceneData.getTriggerData());

                for (var entry : triggerMap.getAll().entrySet()) {
                    TriggerData data = entry.getValue();
                    if (data instanceof SpawnPointData spawn) {
                        String id = spawn.id();
                        if (id != null && !id.isBlank()) {
                            spawnPoints.add(id);
                        }
                    }
                }
            }

            Collections.sort(spawnPoints);
        } catch (Exception e) {
            System.err.println("Failed to load spawn points from scene '" + sceneName + "': " + e.getMessage());
        }

        return spawnPoints;
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
