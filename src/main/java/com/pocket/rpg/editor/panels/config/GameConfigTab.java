package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.widgets.SceneDropdown;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * Configuration tab for game settings.
 * Uses live editing model - edits apply directly to the live GameConfig.
 */
public class GameConfigTab implements ConfigTab {

    private final EditorContext context;
    private final Runnable markDirty;

    public GameConfigTab(EditorContext context, Runnable markDirty) {
        this.context = context;
        this.markDirty = markDirty;
    }

    @Override
    public String getTabName() {
        return "Game";
    }

    @Override
    public void save() {
        ConfigLoader.saveConfigToFile(context.getGameConfig(), ConfigLoader.ConfigType.GAME);
    }

    @Override
    public void revert() {
        ConfigLoader.loadAllConfigs();
    }

    @Override
    public void resetToDefaults() {
        GameConfig config = context.getGameConfig();
        GameConfig defaults = new GameConfig();

        config.setTitle(defaults.getTitle());
        config.setWindowWidth(defaults.getWindowWidth());
        config.setWindowHeight(defaults.getWindowHeight());
        config.setGameWidth(defaults.getGameWidth());
        config.setGameHeight(defaults.getGameHeight());
        config.setFullscreen(defaults.isFullscreen());
        config.setVsync(defaults.isVsync());
        config.setUiButtonHoverTint(defaults.getUiButtonHoverTint());
        config.setStartScene(defaults.getStartScene());
    }

    @Override
    public void renderContent() {
        ImGui.pushID("GameTab");

        renderGeneralContent();

        ImGui.popID();
    }

    private void renderGeneralContent() {
        GameConfig config = context.getGameConfig();

        // Game section (start scene)
        if (ImGui.collapsingHeader("Game", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            String newScene = SceneDropdown.draw("Start Scene", "startScene",
                    config.getStartScene(), false, "(none)");
            if (newScene != null) {
                String oldScene = config.getStartScene();
                UndoManager.getInstance().execute(new SetterUndoCommand<>(
                        v -> { config.setStartScene(v); markDirty.run(); },
                        oldScene, newScene, "Change start scene"));
            }
            tooltip("The scene loaded when the game starts");

            ImGui.unindent();
        }

        // Window section
        if (ImGui.collapsingHeader("Window", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawString("Title", "gameTitle",
                    config::getTitle,
                    v -> { config.setTitle(v); markDirty.run(); });

            FieldEditors.drawInt("Window Width", "windowWidth",
                    config::getWindowWidth,
                    v -> { config.setWindowWidth(Math.max(1, v)); markDirty.run(); });
            tooltip("Physical window width in pixels. Changes apply on restart.");

            FieldEditors.drawInt("Window Height", "windowHeight",
                    config::getWindowHeight,
                    v -> { config.setWindowHeight(Math.max(1, v)); markDirty.run(); });
            tooltip("Physical window height in pixels. Changes apply on restart.");

            ImGui.unindent();
        }

        // Game Resolution section
        if (ImGui.collapsingHeader("Game Resolution", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawInt("Game Width", "gameWidth",
                    config::getGameWidth,
                    v -> { config.setGameWidth(Math.max(1, v)); markDirty.run(); });
            tooltip("Internal game resolution width. Changes apply on restart.");

            FieldEditors.drawInt("Game Height", "gameHeight",
                    config::getGameHeight,
                    v -> { config.setGameHeight(Math.max(1, v)); markDirty.run(); });
            tooltip("Internal game resolution height. Changes apply on restart.");

            ImGui.unindent();
        }

        // Display section
        if (ImGui.collapsingHeader("Display", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawBoolean("Fullscreen", "fullscreen",
                    config::isFullscreen,
                    v -> { config.setFullscreen(v); markDirty.run(); });
            tooltip("Enable fullscreen mode");

            FieldEditors.drawBoolean("VSync", "vsync",
                    config::isVsync,
                    v -> { config.setVsync(v); markDirty.run(); });
            tooltip("Synchronize frame rate with monitor refresh rate");

            ImGui.unindent();
        }

        // UI section
        if (ImGui.collapsingHeader("UI", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawFloatSlider("Button Hover Tint", "buttonHoverTint",
                    () -> (double) config.getUiButtonHoverTint(),
                    v -> { config.setUiButtonHoverTint((float) v); markDirty.run(); },
                    0.0f, 1.0f);
            tooltip("How much buttons darken when hovered");

            ImGui.unindent();
        }
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
