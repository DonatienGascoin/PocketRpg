package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.AssetEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * Inspector for editing AnimatorState properties.
 * Used by InspectorPanel when a state is selected in the Animator Editor.
 */
public class AnimatorStateInspector {

    private AnimatorState state;
    private AnimatorController controller;
    private Runnable onModified;

    private final ImString nameInput = new ImString(64);

    /**
     * Sets the current selection.
     */
    public void setSelection(AnimatorState state, AnimatorController controller, Runnable onModified) {
        this.state = state;
        this.controller = controller;
        this.onModified = onModified;

        if (state != null) {
            nameInput.set(state.getName());
        }
    }

    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        this.state = null;
        this.controller = null;
        this.onModified = null;
    }

    /**
     * Returns true if a state is currently selected.
     */
    public boolean hasSelection() {
        return state != null && controller != null;
    }

    /**
     * Renders the state inspector UI.
     */
    public void render() {
        if (!hasSelection()) {
            ImGui.textDisabled("No state selected");
            return;
        }

        boolean isDefault = state.getName().equals(controller.getDefaultState());

        // Header
        ImGui.text(MaterialIcons.Circle + " State");
        ImGui.separator();

        // Name field
        ImGui.text("Name:");
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##StateName", nameInput)) {
            String newName = nameInput.get().trim();
            if (!newName.isEmpty() && !newName.equals(state.getName())) {
                // Check for duplicate
                if (!controller.hasState(newName)) {
                    notifyModified();
                    String oldName = state.getName();
                    state.setName(newName);
                    // Update default state reference if this was default
                    if (oldName.equals(controller.getDefaultState())) {
                        controller.setDefaultState(newName);
                    }
                    // Update transition references
                    updateTransitionReferences(oldName, newName);
                }
            }
        }

        ImGui.spacing();

        // Type selector
        ImGui.text("Type:");
        String[] typeNames = {"Simple", "Directional"};
        ImInt typeIdx = new ImInt(state.getType().ordinal());
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##StateType", typeIdx, typeNames)) {
            notifyModified();
            state.setType(StateType.values()[typeIdx.get()]);
        }

        ImGui.spacing();

        // Animation field(s) based on type
        if (state.getType() == StateType.SIMPLE) {
            renderAnimationField("Animation:", null);
        } else {
            // Direction parameter selector
            renderDirectionParameterSelector();

            ImGui.spacing();
            ImGui.text("Animations:");
            for (Direction dir : Direction.values()) {
                renderAnimationField(dir.name() + ":", dir);
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Default state checkbox
        if (ImGui.checkbox("Default State", isDefault)) {
            if (!isDefault) {
                notifyModified();
                controller.setDefaultState(state.getName());
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Outgoing transitions list
        ImGui.text("Transitions Out:");
        boolean hasOutgoing = false;
        for (int i = 0; i < controller.getTransitionCount(); i++) {
            AnimatorTransition trans = controller.getTransition(i);
            if (trans.getFrom().equals(state.getName())) {
                hasOutgoing = true;
                String label = MaterialIcons.ArrowForward + " " + trans.getTo() + " (" + trans.getType().name() + ")";
                ImGui.text(label);
            }
        }
        if (!hasOutgoing) {
            ImGui.textDisabled("No outgoing transitions");
        }
    }

    private void renderDirectionParameterSelector() {
        // Collect all direction parameters from controller
        java.util.List<String> dirParams = new java.util.ArrayList<>();
        for (int i = 0; i < controller.getParameterCount(); i++) {
            AnimatorParameter param = controller.getParameter(i);
            if (param.getType() == ParameterType.DIRECTION) {
                dirParams.add(param.getName());
            }
        }

        if (dirParams.isEmpty()) {
            ImGui.textColored(1f, 0.6f, 0.2f, 1f, MaterialIcons.Warning + " No direction parameters!");
            return;
        }

        ImGui.text("Direction Parameter:");

        // Find current selection index
        String currentParam = state.getDirectionParameter();
        int selectedIdx = 0;
        if (currentParam != null) {
            int idx = dirParams.indexOf(currentParam);
            if (idx >= 0) {
                selectedIdx = idx;
            }
        }

        // Auto-select first for display if not explicitly set.
        // The runtime state machine handles null by falling back to the first
        // direction parameter, so this is only a display convenience — don't
        // mark the controller as modified.
        if (currentParam == null && !dirParams.isEmpty()) {
            selectedIdx = 0;
        }

        String[] paramNames = dirParams.toArray(new String[0]);
        ImInt paramIdx = new ImInt(selectedIdx);
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##DirParam", paramIdx, paramNames)) {
            notifyModified();
            state.setDirectionParameter(dirParams.get(paramIdx.get()));
        }
    }

    private void renderAnimationField(String label, Direction direction) {
        String currentPath;
        if (direction != null) {
            currentPath = state.getDirectionalAnimation(direction);
        } else {
            currentPath = state.getAnimation();
        }

        Animation currentAnim = null;
        if (currentPath != null && !currentPath.isBlank()) {
            try {
                currentAnim = Assets.load(currentPath, Animation.class);
            } catch (Exception ignored) {
                // Asset not found
            }
        }

        String display = currentAnim != null ? FieldEditorUtils.getAssetDisplayName(currentAnim) : "(none)";

        // Use unique ID suffix based on direction
        String idSuffix = direction != null ? direction.name() : "simple";

        ImGui.text(label);
        ImGui.sameLine();

        // Picker button
        if (ImGui.smallButton("...##PickAnim" + idSuffix)) {
            AssetEditor.openPicker(Animation.class, currentPath, selected -> {
                if (selected != null) {
                    String path = Assets.getPathForResource(selected);
                    if (path != null) {
                        notifyModified();
                        if (direction != null) {
                            state.setDirectionalAnimation(direction, path);
                        } else {
                            state.setAnimation(path);
                        }
                    }
                }
            });
        }

        ImGui.sameLine();
        if (currentAnim != null) {
            ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, display);
        } else {
            ImGui.textDisabled(display);
        }
    }

    private void updateTransitionReferences(String oldName, String newName) {
        for (int i = 0; i < controller.getTransitionCount(); i++) {
            AnimatorTransition trans = controller.getTransition(i);
            if (trans.getFrom().equals(oldName)) {
                trans.setFrom(newName);
            }
            if (trans.getTo().equals(oldName)) {
                trans.setTo(newName);
            }
        }
    }

    private void notifyModified() {
        if (onModified != null) {
            onModified.run();
        }
    }
}
