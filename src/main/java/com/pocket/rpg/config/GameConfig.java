package com.pocket.rpg.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameConfig {

    @Builder.Default
    private String title = "Pocket Rpg";

    // ===== WINDOW RESOLUTION (physical window size) =====
    /**
     * The initial physical window width in pixels.
     */
    @Builder.Default
    private int windowWidth = 1280;

    /**
     * The initial physical window height in pixels.
     */
    @Builder.Default
    private int windowHeight = 960;

    // ===== GAME RESOLUTION (fixed internal resolution) =====
    /**
     * The fixed internal game resolution width.
     * All game logic and rendering happens at this resolution.
     * This is then scaled to fit the window.
     */
    @Builder.Default
    private int gameWidth = 640;

    /**
     * The fixed internal game resolution height.
     * All game logic and rendering happens at this resolution.
     * This is then scaled to fit the window.
     */
    @Builder.Default
    private int gameHeight = 480;

    @Builder.Default
    private boolean fullscreen = false;
    @Builder.Default
    private boolean vsync = false;

    /**
     * Default hover tint for UI buttons.
     * When a button is hovered and no custom onHover callback is set,
     * the button color is darkened by this factor.
     * <p>
     * 0.0 = no darkening
     * 0.1 = 10% darker (default)
     * 0.2 = 20% darker
     * 1.0 = completely black
     */
    private float uiButtonHoverTint = 0.1f;

    /**
     * Default pressed tint for UI buttons.
     * When a button is pressed and no custom pressedTint is set,
     * the button color is darkened by this factor.
     * <p>
     * 0.0 = no darkening
     * 0.2 = 20% darker (default)
     * 1.0 = completely black
     */
    private float uiButtonPressedTint = 0.2f;

    /**
     * The scene to load when the game starts.
     * Must match a scene file name (without .scene extension) in gameData/scenes/.
     */
    @Builder.Default
    private String startScene = "";
}
