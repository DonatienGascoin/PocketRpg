package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.scenes.transitions.WipeTransition;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

/**
 * Configuration tab for transition settings.
 * Uses live editing model - edits apply directly to the live TransitionConfig.
 */
public class TransitionConfigTab implements ConfigTab {

    private final EditorContext context;
    private final Runnable markDirty;

    public TransitionConfigTab(EditorContext context, Runnable markDirty) {
        this.context = context;
        this.markDirty = markDirty;
    }

    @Override
    public String getTabName() {
        return "Transition";
    }

    @Override
    public void save() {
        // TransitionConfig is part of GameConfig, so save that
        ConfigLoader.saveConfigToFile(context.getGameConfig(), ConfigLoader.ConfigType.GAME);
    }

    @Override
    public void revert() {
        ConfigLoader.loadAllConfigs();
    }

    @Override
    public void resetToDefaults() {
        TransitionConfig config = context.getGameConfig().getDefaultTransitionConfig();
        TransitionConfig defaults = new TransitionConfig();

        config.setFadeOutDuration(defaults.getFadeOutDuration());
        config.setFadeInDuration(defaults.getFadeInDuration());
        config.getFadeColor().set(defaults.getFadeColor());
        config.setTransitionText(defaults.getTransitionText());
        config.setType(defaults.getType());
        config.setWipeDirection(defaults.getWipeDirection());
    }

    @Override
    public void renderContent() {
        ImGui.pushID("TransitionTab");

        TransitionConfig config = context.getGameConfig().getDefaultTransitionConfig();

        // Timing section
        if (ImGui.collapsingHeader("Timing", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawFloat("Fade Out Duration", "fadeOut",
                    () -> (double) config.getFadeOutDuration(),
                    v -> { config.setFadeOutDuration(Math.max(0, (float) v)); markDirty.run(); },
                    0.01f, 0.0f, 5.0f, "%.2f s");
            tooltip("Duration of the fade-out phase in seconds");

            FieldEditors.drawFloat("Fade In Duration", "fadeIn",
                    () -> (double) config.getFadeInDuration(),
                    v -> { config.setFadeInDuration(Math.max(0, (float) v)); markDirty.run(); },
                    0.01f, 0.0f, 5.0f, "%.2f s");
            tooltip("Duration of the fade-in phase in seconds");

            ImGui.unindent();
        }

        // Appearance section
        if (ImGui.collapsingHeader("Appearance", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawColor("Fade Color", "fadeColor",
                    config::getFadeColor,
                    v -> { config.getFadeColor().set(v); markDirty.run(); });
            tooltip("Color of the transition overlay");

            FieldEditors.drawString("Transition Text", "transitionText",
                    config::getTransitionText,
                    v -> { config.setTransitionText(v); markDirty.run(); });
            tooltip("Optional text shown during transition");

            ImGui.unindent();
        }

        // Type section
        if (ImGui.collapsingHeader("Type", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();

            FieldEditors.drawEnum("Transition Type", "transitionType",
                    config::getType,
                    v -> { config.setType(v); markDirty.run(); },
                    TransitionConfig.TransitionType.class);
            tooltip("Type of transition animation");

            ImGui.textDisabled(getTypeDescription(config.getType()));

            if (isWipeType(config.getType())) {
                ImGui.spacing();
                FieldEditors.drawEnum("Wipe Direction", "wipeDirection",
                        config::getWipeDirection,
                        v -> { config.setWipeDirection(v); markDirty.run(); },
                        WipeTransition.WipeDirection.class);
                tooltip("Direction of the wipe animation");
            }

            ImGui.unindent();
        }

        // Preview section
        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled(String.format("Total duration: %.2f seconds", config.getTotalDuration()));

        ImGui.popID();
    }

    private String getTypeDescription(TransitionConfig.TransitionType type) {
        return switch (type) {
            case FADE -> "Simple fade to color and back";
            case FADE_WITH_TEXT -> "Fade with text overlay";
            case WIPE_LEFT -> "Wipe from left to right";
            case WIPE_RIGHT -> "Wipe from right to left";
            case WIPE_UP -> "Wipe from top to bottom";
            case WIPE_DOWN -> "Wipe from bottom to top";
            case WIPE_CIRCLE_IN -> "Circle expanding from center";
            case WIPE_CIRCLE_OUT -> "Circle contracting to center";
        };
    }

    private boolean isWipeType(TransitionConfig.TransitionType type) {
        return type.name().startsWith("WIPE_");
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
