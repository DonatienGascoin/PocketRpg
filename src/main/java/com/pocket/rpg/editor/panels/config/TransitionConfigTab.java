package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.editor.EditorContext;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.scenes.transitions.WipeTransition;
import imgui.ImGui;
import imgui.type.ImString;
import lombok.RequiredArgsConstructor;
import org.joml.Vector4f;

@RequiredArgsConstructor
public class TransitionConfigTab implements ConfigTab {

    private final EditorContext context;

    private TransitionConfig working;
    private TransitionConfig original;
    private boolean dirty = false;

    private final ImString transitionTextBuffer = new ImString(256);
    private final float[] fadeColorBuffer = new float[4];

    @Override
    public void initialize() {
        working = cloneConfig(context.getGameConfig().getDefaultTransitionConfig());
        original = cloneConfig(context.getGameConfig().getDefaultTransitionConfig());
        syncBuffers();
        dirty = false;
    }

    @Override
    public String getTabName() {
        return "Transition";
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void save() {
        applyToLive();
        ConfigLoader.saveConfigToFile(context.getGameConfig(), ConfigLoader.ConfigType.GAME);
        original = cloneConfig(context.getGameConfig().getDefaultTransitionConfig());
        dirty = false;
    }

    @Override
    public void resetToDefaults() {
        working = new TransitionConfig();
        original = new TransitionConfig(); // Reset original too!
        syncBuffers();
        dirty = false; // Explicitly set to false
    }

    @Override
    public void renderContent() {
        // Scope ALL IDs to this tab
        ImGui.pushID("TransitionTab");

        if (ImGui.button(FontAwesomeIcons.Undo + " Reset to Defaults")) {
            resetToDefaults();
        }

        ImGui.separator();

        if (ImGui.beginChild("Content", 0, 0, false)) {
            renderDurationSection();
            renderAppearanceSection();
            renderTypeSection();
            renderPreviewSection();
            ImGui.endChild();
        }

        ImGui.popID();
    }

    private void renderDurationSection() {
        ImGui.text("Timing");
        ImGui.indent();

        float[] fadeOut = {working.getFadeOutDuration()};
        ImGui.setNextItemWidth(150);
        if (ImGui.dragFloat("Fade Out Duration", fadeOut, 0.01f, 0.0f, 5.0f, "%.2f s")) {
            working.setFadeOutDuration(Math.max(0, fadeOut[0]));
            updateDirtyFlag();
        }
        tooltip("Duration of the fade-out phase in seconds");

        float[] fadeIn = {working.getFadeInDuration()};
        ImGui.setNextItemWidth(150);
        if (ImGui.dragFloat("Fade In Duration", fadeIn, 0.01f, 0.0f, 5.0f, "%.2f s")) {
            working.setFadeInDuration(Math.max(0, fadeIn[0]));
            updateDirtyFlag();
        }
        tooltip("Duration of the fade-in phase in seconds");

        ImGui.unindent();
    }

    private void renderAppearanceSection() {
        ImGui.spacing();
        ImGui.text("Appearance");
        ImGui.indent();

        if (ImGui.colorEdit4("Fade Color", fadeColorBuffer)) {
            working.getFadeColor().set(
                    fadeColorBuffer[0],
                    fadeColorBuffer[1],
                    fadeColorBuffer[2],
                    fadeColorBuffer[3]
            );
            updateDirtyFlag();
        }
        tooltip("Color of the transition overlay");

        ImGui.setNextItemWidth(200);
        if (ImGui.inputText("Transition Text", transitionTextBuffer)) {
            working.setTransitionText(transitionTextBuffer.get());
            updateDirtyFlag();
        }
        tooltip("Optional text shown during transition");

        ImGui.unindent();
    }

    private void renderTypeSection() {
        ImGui.spacing();
        ImGui.text("Type");
        ImGui.indent();

        ImGui.setNextItemWidth(200);
        if (ImGui.beginCombo("Transition Type", working.getType().name())) {
            for (TransitionConfig.TransitionType type : TransitionConfig.TransitionType.values()) {
                boolean selected = type == working.getType();
                if (ImGui.selectable(type.name(), selected)) {
                    working.setType(type);
                    updateDirtyFlag();
                }
            }
            ImGui.endCombo();
        }
        tooltip("Type of transition animation");

        ImGui.textDisabled(getTypeDescription(working.getType()));

        if (isWipeType(working.getType())) {
            ImGui.spacing();
            ImGui.setNextItemWidth(200);
            if (ImGui.beginCombo("Wipe Direction", working.getWipeDirection().name())) {
                for (WipeTransition.WipeDirection dir : WipeTransition.WipeDirection.values()) {
                    boolean selected = dir == working.getWipeDirection();
                    if (ImGui.selectable(dir.name(), selected)) {
                        working.setWipeDirection(dir);
                        updateDirtyFlag();
                    }
                }
                ImGui.endCombo();
            }
            tooltip("Direction of the wipe animation");
        }

        ImGui.unindent();
    }

    private void renderPreviewSection() {
        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled(String.format("Total duration: %.2f seconds", working.getTotalDuration()));
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

    private void syncBuffers() {
        transitionTextBuffer.set(working.getTransitionText());
        Vector4f color = working.getFadeColor();
        fadeColorBuffer[0] = color.x;
        fadeColorBuffer[1] = color.y;
        fadeColorBuffer[2] = color.z;
        fadeColorBuffer[3] = color.w;
    }

    private void updateDirtyFlag() {
        dirty = !isConfigEqual(working, original);
    }

    private boolean isConfigEqual(TransitionConfig a, TransitionConfig b) {
        return a.getFadeOutDuration() == b.getFadeOutDuration()
                && a.getFadeInDuration() == b.getFadeInDuration()
                && a.getFadeColor().equals(b.getFadeColor())
                && a.getTransitionText().equals(b.getTransitionText())
                && a.getType() == b.getType()
                && a.getWipeDirection() == b.getWipeDirection();
    }

    private void applyToLive() {
        TransitionConfig live = context.getGameConfig().getDefaultTransitionConfig();
        live.setFadeOutDuration(working.getFadeOutDuration());
        live.setFadeInDuration(working.getFadeInDuration());
        live.getFadeColor().set(working.getFadeColor());
        live.setTransitionText(working.getTransitionText());
        live.setType(working.getType());
        live.setWipeDirection(working.getWipeDirection());
    }

    private TransitionConfig cloneConfig(TransitionConfig source) {
        return new TransitionConfig(source);
    }

    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }
}
