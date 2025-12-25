package com.pocket.rpg.editor.ui;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.PlayModeController.PlayState;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

/**
 * ImGui panel that displays the game during Play Mode.
 * <p>
 * Features:
 * - Play/Pause/Stop toolbar
 * - Pillarboxed game display maintaining aspect ratio
 * - FPS display during play
 */
public class GameViewPanel {

    private final EditorContext context;
    private final PlayModeController playController;
    private final GameConfig gameConfig;

    // Cached aspect ratio
    private float aspectRatio;

    public GameViewPanel(EditorContext context, PlayModeController playController, GameConfig gameConfig) {
        this.context = context;
        this.playController = playController;
        this.gameConfig = gameConfig;
        this.aspectRatio = (float) gameConfig.getGameWidth() / gameConfig.getGameHeight();
    }

    /**
     * Renders the Game View panel.
     */
    public void render() {
        // Don't render if controller is not set
        if (playController == null) {
            return;
        }

        int windowFlags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;

        ImGui.begin("Game", windowFlags);

        renderToolbar();
        ImGui.separator();

        PlayState state = playController.getState();

        if (state != PlayState.STOPPED) {
            renderGameView();
        } else {
            renderPlaceholder();
        }

        ImGui.end();
    }

    /**
     * Renders the Play/Pause/Stop toolbar.
     */
    private void renderToolbar() {
        PlayState state = playController.getState();

        if (state == PlayState.STOPPED) {
            // Play button
            if (ImGui.button("▶ Play")) {
                playController.play();
            }

            ImGui.sameLine();
            ImGui.textDisabled("Scene: " + getSceneName());
        } else {
            // Pause/Resume button
            if (state == PlayState.PLAYING) {
                if (ImGui.button("⏸ Pause")) {
                    playController.pause();
                }
            } else {
                if (ImGui.button("▶ Resume")) {
                    playController.resume();
                }
            }

            ImGui.sameLine();

            // Stop button
            if (ImGui.button("⏹ Stop")) {
                playController.stop();
            }

            ImGui.sameLine();

            // State indicator
            String stateText = state == PlayState.PLAYING ? "Playing" : "Paused";
            ImGui.textColored(0.4f, 0.8f, 0.4f, 1.0f, stateText);

            ImGui.sameLine();
            ImGui.text("| FPS: " + (int) ImGui.getIO().getFramerate());
        }
    }

    /**
     * Renders the game view with pillarboxing.
     */
    private void renderGameView() {
        // Get available content region
        ImVec2 available = new ImVec2();
        ImGui.getContentRegionAvail(available);

        if (available.x <= 0 || available.y <= 0) {
            return;
        }

        // Calculate display size maintaining aspect ratio
        float panelRatio = available.x / available.y;
        float displayWidth, displayHeight;

        if (panelRatio > aspectRatio) {
            // Panel is wider - pillarbox (black bars on sides)
            displayHeight = available.y;
            displayWidth = displayHeight * aspectRatio;
        } else {
            // Panel is taller - letterbox (black bars top/bottom)
            displayWidth = available.x;
            displayHeight = displayWidth / aspectRatio;
        }

        // Center the image
        float offsetX = (available.x - displayWidth) / 2f;
        float offsetY = (available.y - displayHeight) / 2f;

        ImVec2 cursorPos = new ImVec2();
        ImGui.getCursorPos(cursorPos);
        ImGui.setCursorPos(cursorPos.x + offsetX, cursorPos.y + offsetY);

        // Render game texture (flip UV vertically for OpenGL)
        int textureId = playController.getOutputTexture();
        ImGui.image(textureId, displayWidth, displayHeight, 0, 1, 1, 0);

        // Handle mouse input on game view
        if (ImGui.isItemHovered()) {
            // Future: route mouse to game
        }
    }

    /**
     * Renders a placeholder when not playing.
     */
    private void renderPlaceholder() {
        ImVec2 available = new ImVec2();
        ImGui.getContentRegionAvail(available);

        // Center text
        String text = "Press Play to test the scene";
        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, text);

        float offsetX = (available.x - textSize.x) / 2f;
        float offsetY = (available.y - textSize.y) / 2f;

        ImVec2 cursorPos = new ImVec2();
        ImGui.getCursorPos(cursorPos);
        ImGui.setCursorPos(cursorPos.x + offsetX, cursorPos.y + offsetY);

        ImGui.textDisabled(text);
    }

    /**
     * Gets the current scene name for display.
     */
    private String getSceneName() {
        if (context.getCurrentScene() != null) {
            return context.getCurrentScene().getName();
        }
        return "No Scene";
    }

    /**
     * Updates aspect ratio if GameConfig changes.
     */
    public void updateAspectRatio() {
        aspectRatio = (float) gameConfig.getGameWidth() / gameConfig.getGameHeight();
    }
}
