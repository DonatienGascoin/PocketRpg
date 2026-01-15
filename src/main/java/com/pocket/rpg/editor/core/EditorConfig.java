package com.pocket.rpg.editor.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joml.Vector4f;

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
