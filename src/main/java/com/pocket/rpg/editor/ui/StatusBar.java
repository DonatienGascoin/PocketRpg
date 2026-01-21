package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import lombok.Setter;

/**
 * Status bar rendered at the bottom of the editor window.
 * <p>
 * Displays:
 * - Current message (fades over time)
 * - Camera zoom level
 * - Scene dirty indicator
 */
public class StatusBar {

    private static final float MESSAGE_DURATION = 4.0f;  // Seconds to show message
    private static final float FADE_DURATION = 1.0f;     // Fade out duration

    @Setter
    private EditorCamera camera;

    @Setter
    private EditorScene currentScene;

    // Current message
    private String message = "";
    private float messageTimer = 0;

    /**
     * Shows a temporary message in the status bar.
     */
    public void showMessage(String message) {
        this.message = message;
        this.messageTimer = MESSAGE_DURATION;
    }

    /**
     * Renders the status bar at the bottom of the window.
     *
     * @param windowHeight Total window height for positioning
     */
    public void render(int windowHeight) {
        float barHeight = 24;
        float barY = windowHeight - barHeight;

        // Fixed position at bottom
        ImGui.setNextWindowPos(0, barY);
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), barHeight);

        int flags = ImGuiWindowFlags.NoTitleBar |
                ImGuiWindowFlags.NoResize |
                ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoScrollbar |
                ImGuiWindowFlags.NoSavedSettings |
                ImGuiWindowFlags.NoDocking |
                ImGuiWindowFlags.NoFocusOnAppearing |
                ImGuiWindowFlags.NoBringToFrontOnFocus;

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 8, 4);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 0);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.15f, 0.15f, 0.15f, 0.95f);

        if (ImGui.begin("##StatusBar", flags)) {
            // Left side: Message
            renderMessage();

            // Right side: Scene info and zoom
            renderRightInfo();
        }
        ImGui.end();

        ImGui.popStyleColor();
        ImGui.popStyleVar(2);

        // Update message timer
        if (messageTimer > 0) {
            messageTimer -= ImGui.getIO().getDeltaTime();
        }
    }

    /**
     * Renders the message with fade effect.
     */
    private void renderMessage() {
        if (message.isEmpty() || messageTimer <= 0) {
            ImGui.textDisabled("Ready");
            return;
        }

        // Calculate alpha for fade
        float alpha = 1.0f;
        if (messageTimer < FADE_DURATION) {
            alpha = messageTimer / FADE_DURATION;
        }

        // Message with icon based on content
        String icon = MaterialIcons.Info;
        float r = 0.7f, g = 0.9f, b = 0.7f;

        if (message.contains("Error") || message.contains("error")) {
            icon = MaterialIcons.Warning;
            r = 1.0f; g = 0.4f; b = 0.4f;
        } else if (message.contains("Saved")) {
            icon = MaterialIcons.Check;
            r = 0.4f; g = 1.0f; b = 0.4f;
        } else if (message.contains("Opened") || message.contains("created")) {
            icon = MaterialIcons.FolderOpen;
            r = 0.4f; g = 0.8f; b = 1.0f;
        }

        ImGui.textColored(r, g, b, alpha, icon + " " + message);
    }

    /**
     * Renders right-aligned info (zoom, scene status).
     */
    private void renderRightInfo() {
        StringBuilder rightText = new StringBuilder();

        // Zoom level
        if (camera != null) {
            int zoomPercent = (int) (camera.getZoom() * 100);
            rightText.append(MaterialIcons.ZoomIn).append(" ").append(zoomPercent).append("%");
        }

        // Scene dirty indicator
        if (currentScene != null && currentScene.isDirty()) {
            rightText.append("  ").append(MaterialIcons.Circle).append(" Modified");
        }

        if (rightText.length() > 0) {
            // Calculate position for right alignment
            String text = rightText.toString();
            float textWidth = ImGui.calcTextSize(text).x;
            float windowWidth = ImGui.getWindowWidth();

            ImGui.sameLine(windowWidth - textWidth - 16);

            if (currentScene != null && currentScene.isDirty()) {
                ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, text);
            } else {
                ImGui.textDisabled(text);
            }
        }
    }
}
