package com.pocket.rpg.editor.core;

import com.pocket.rpg.config.ConfigLoader;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the Scene Editor application.
 * Separate from GameConfig to allow independent editor settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditorConfig {

    // ===== WINDOW SETTINGS =====
    
    /**
     * Editor window title.
     */
    @Builder.Default
    private String title = "PocketRPG Scene Editor";

    /**
     * Whether to start in fullscreen mode.
     * When true, uses primary monitor resolution.
     */
    @Builder.Default
    private boolean fullscreen = true;

    /**
     * Windowed mode width (used when fullscreen is false).
     */
    @Builder.Default
    private int windowWidth = 1600;

    /**
     * Windowed mode height (used when fullscreen is false).
     */
    @Builder.Default
    private int windowHeight = 900;

    /**
     * Enable VSync for the editor.
     */
    @Builder.Default
    private boolean vsync = true;

    // ===== RENDERING SETTINGS =====

    /**
     * Pixels per unit for the editor viewport.
     * Matches game's PPU for accurate preview.
     */
    @Builder.Default
    private float pixelsPerUnit = 16f;

    /**
     * Editor viewport background color.
     */
    @Builder.Default
    private Vector4f clearColor = new Vector4f(0.15f, 0.15f, 0.15f, 1.0f);

    /**
     * Grid line color (semi-transparent).
     */
    @Builder.Default
    private Vector4f gridColor = new Vector4f(1.0f, 1.0f, 1.0f, 0.1f);

    /**
     * Grid major line color (every N lines).
     */
    @Builder.Default
    private Vector4f gridMajorColor = new Vector4f(1.0f, 1.0f, 1.0f, 0.25f);

    /**
     * Number of minor grid lines between major lines.
     */
    @Builder.Default
    private int gridMajorLineInterval = 8;

    // ===== CAMERA SETTINGS =====

    /**
     * Default camera zoom level.
     */
    @Builder.Default
    private float defaultZoom = 1.0f;

    /**
     * Minimum zoom level (zoomed out).
     */
    @Builder.Default
    private float minZoom = 0.1f;

    /**
     * Maximum zoom level (zoomed in).
     */
    @Builder.Default
    private float maxZoom = 10.0f;

    /**
     * Camera pan speed (world units per second at zoom 1.0).
     */
    @Builder.Default
    private float cameraPanSpeed = 10.0f;

    /**
     * Zoom speed multiplier for scroll wheel.
     */
    @Builder.Default
    private float zoomSpeed = 0.1f;

    // ===== PATHS =====

    /**
     * Default directory for scene files.
     */
    @Builder.Default
    private String scenesDirectory = "gameData/scenes";

    /**
     * Editor assets directory.
     */
    @Builder.Default
    private String editorAssetsDirectory = "editor/assets";

    /**
     * Game assets directory (for loading tilesets, sprites).
     */
    @Builder.Default
    private String gameAssetsDirectory = "gameData/assets";

    // ===== UI SETTINGS =====

    /**
     * ImGui font size.
     */
    @Builder.Default
    private float fontSize = 16.0f;

    /**
     * Whether to show the grid overlay.
     */
    @Builder.Default
    private boolean showGrid = true;

    /**
     * Whether to show tile coordinates on hover.
     */
    @Builder.Default
    private boolean showTileCoordinates = true;

    /**
     * Default font path for new UIText components.
     */
    @Builder.Default
    private String defaultUiFont = "gameData/assets/fonts/zelda.ttf";

    // ===== RECENT FILES =====

    /**
     * List of recently opened scene file paths.
     * Most recent is first. Limited to 10 entries.
     */
    @Builder.Default
    private List<String> recentScenes = new ArrayList<>();

    /**
     * Maximum number of recent scenes to track.
     */
    private static final int MAX_RECENT_SCENES = 10;

    /**
     * Adds a scene path to the recent list.
     * Moves it to the front if already present.
     * Path is normalized to forward slashes and stored relative to scenesDirectory.
     */
    public void addRecentScene(String path) {
        if (path == null || path.isEmpty()) return;

        // Normalize to forward slashes for OS-independent storage
        String normalizedPath = path.replace('\\', '/');

        // Convert to relative path from scenesDirectory
        String relativePath = toRelativePath(normalizedPath);

        // Remove if already in list (will re-add at front)
        recentScenes.remove(relativePath);

        // Add to front
        recentScenes.add(0, relativePath);

        // Trim to max size
        while (recentScenes.size() > MAX_RECENT_SCENES) {
            recentScenes.remove(recentScenes.size() - 1);
        }
    }

    /**
     * Converts an absolute or full path to a path relative to scenesDirectory.
     * If the path doesn't contain scenesDirectory, returns the original path.
     */
    private String toRelativePath(String path) {
        String scenesDir = scenesDirectory.replace('\\', '/');
        if (!scenesDir.endsWith("/")) {
            scenesDir += "/";
        }

        int index = path.indexOf(scenesDir);
        if (index >= 0) {
            return path.substring(index + scenesDir.length());
        }

        // Fallback: if path starts with scenesDirectory without trailing slash
        String scenesDirNoSlash = scenesDirectory.replace('\\', '/');
        if (path.startsWith(scenesDirNoSlash + "/")) {
            return path.substring(scenesDirNoSlash.length() + 1);
        }

        return path;
    }

    /**
     * Converts a relative scene path to a full path using scenesDirectory.
     */
    public String toFullPath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return relativePath;

        // If already contains scenesDirectory, return as-is
        String scenesDir = scenesDirectory.replace('\\', '/');
        if (relativePath.contains(scenesDir)) {
            return relativePath;
        }

        // Prepend scenesDirectory
        return scenesDir + "/" + relativePath;
    }

    /**
     * Gets the most recently opened scene path as a full path, or null if none.
     */
    public String getLastOpenedScene() {
        if (recentScenes.isEmpty()) return null;
        return toFullPath(recentScenes.get(0));
    }

    // ===== PANEL VISIBILITY =====

    /**
     * Panel visibility state - Map allows dynamic panel registration.
     * Key is the panel ID, value is whether the panel is open.
     */
    @Builder.Default
    private Map<String, Boolean> panelVisibility = new HashMap<>();

    /**
     * Gets whether a panel is open.
     *
     * @param panelId      The panel ID
     * @param defaultValue The default value if not set
     * @return true if the panel is open, false otherwise
     */
    public boolean isPanelOpen(String panelId, boolean defaultValue) {
        return panelVisibility.getOrDefault(panelId, defaultValue);
    }

    /**
     * Sets whether a panel is open and persists the config.
     *
     * @param panelId The panel ID
     * @param open    Whether the panel is open
     */
    public void setPanelOpen(String panelId, boolean open) {
        panelVisibility.put(panelId, open);
        save();
    }

    /**
     * Saves this config to disk.
     */
    public void save() {
        ConfigLoader.saveConfigToFile(this, ConfigLoader.ConfigType.EDITOR);
    }

    // ===== FACTORY METHODS =====

    /**
     * Creates default editor configuration.
     */
    public static EditorConfig createDefault() {
        return EditorConfig.builder().build();
    }

    /**
     * Creates windowed editor configuration.
     */
    public static EditorConfig createWindowed(int width, int height) {
        return EditorConfig.builder()
                .fullscreen(false)
                .windowWidth(width)
                .windowHeight(height)
                .build();
    }
}
