package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.dialogue.DialogueVariable;
import com.pocket.rpg.dialogue.DialogueVariables;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.DialogueVariablesLoader;
import imgui.ImGui;
import imgui.type.ImString;

import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Inspector renderer for {@link DialogueVariables} assets.
 * <p>
 * Renders the variable list with name/type editing, add/remove support,
 * undo/redo, and a save button.
 */
public class DialogueVariablesInspectorRenderer implements AssetInspectorRenderer<DialogueVariables> {

    private static final int MAX_UNDO = 50;

    private final DialogueVariablesLoader loader = new DialogueVariablesLoader();
    private final ImString nameBuffer = new ImString(256);

    private String cachedPath;
    private boolean hasChanges = false;

    // Undo/redo stacks — snapshots of the full variable list
    private final Deque<List<DialogueVariable>> undoStack = new ArrayDeque<>();
    private final Deque<List<DialogueVariable>> redoStack = new ArrayDeque<>();
    private DialogueVariables currentAsset;

    @Override
    public boolean render(DialogueVariables variables, String assetPath, float maxPreviewSize) {
        if (!assetPath.equals(cachedPath)) {
            cachedPath = assetPath;
            hasChanges = false;
            undoStack.clear();
            redoStack.clear();
        }
        currentAsset = variables;

        ImGui.text("Dialogue Variables");
        ImGui.separator();

        // Undo/Redo buttons
        renderUndoRedo(variables);
        ImGui.spacing();

        List<DialogueVariable> vars = variables.getVariables();
        int removeIndex = -1;

        if (vars.isEmpty()) {
            ImGui.textDisabled("No variables defined");
        }

        for (int i = 0; i < vars.size(); i++) {
            DialogueVariable var = vars.get(i);
            ImGui.pushID("var_" + i);

            // Name input
            nameBuffer.set(var.getName() != null ? var.getName() : "");
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
            if (ImGui.inputText("##name", nameBuffer)) {
                captureUndo(variables);
                var.setName(nameBuffer.get());
                hasChanges = true;
            }

            // Type dropdown
            ImGui.sameLine();
            DialogueVariable.Type currentType = var.getType();
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.55f);
            if (ImGui.beginCombo("##type", currentType.name())) {
                for (DialogueVariable.Type type : DialogueVariable.Type.values()) {
                    if (ImGui.selectable(type.name(), type == currentType)) {
                        captureUndo(variables);
                        var.setType(type);
                        hasChanges = true;
                    }
                }
                ImGui.endCombo();
            }

            // Delete button
            ImGui.sameLine();
            EditorColors.pushDangerButton();
            if (ImGui.button(MaterialIcons.Close + "##remove")) {
                removeIndex = i;
            }
            EditorColors.popButtonColors();

            ImGui.popID();
        }

        // Remove deferred
        if (removeIndex >= 0) {
            captureUndo(variables);
            vars.remove(removeIndex);
            hasChanges = true;
        }

        ImGui.spacing();

        // Add button
        if (ImGui.button(MaterialIcons.Add + " Add Variable")) {
            captureUndo(variables);
            vars.add(new DialogueVariable("", DialogueVariable.Type.STATIC));
            hasChanges = true;
        }

        return hasChanges;
    }

    private void renderUndoRedo(DialogueVariables variables) {
        boolean canUndo = !undoStack.isEmpty();
        boolean canRedo = !redoStack.isEmpty();

        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Undo + "##undoVars")) {
            undo(variables);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Undo");
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();

        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Redo + "##redoVars")) {
            redo(variables);
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Redo");
        if (!canRedo) ImGui.endDisabled();
    }

    // ========================================================================
    // UNDO / REDO
    // ========================================================================

    private void captureUndo(DialogueVariables variables) {
        undoStack.push(snapshot(variables));
        redoStack.clear();
        if (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
    }

    @Override
    public void undo() {
        if (currentAsset != null) undo(currentAsset);
    }

    @Override
    public void redo() {
        if (currentAsset != null) redo(currentAsset);
    }

    private void undo(DialogueVariables variables) {
        if (undoStack.isEmpty()) return;
        redoStack.push(snapshot(variables));
        restore(variables, undoStack.pop());
        hasChanges = true;
    }

    private void redo(DialogueVariables variables) {
        if (redoStack.isEmpty()) return;
        undoStack.push(snapshot(variables));
        restore(variables, redoStack.pop());
        hasChanges = true;
    }

    private List<DialogueVariable> snapshot(DialogueVariables variables) {
        List<DialogueVariable> copy = new ArrayList<>();
        for (DialogueVariable v : variables.getVariables()) {
            copy.add(new DialogueVariable(v.getName(), v.getType()));
        }
        return copy;
    }

    private void restore(DialogueVariables variables, List<DialogueVariable> snapshot) {
        variables.getVariables().clear();
        variables.getVariables().addAll(snapshot);
    }

    // ========================================================================
    // ASSET INSPECTOR RENDERER
    // ========================================================================

    @Override
    public boolean hasEditableProperties() {
        return true;
    }

    @Override
    public void save(DialogueVariables variables, String assetPath) {
        try {
            String fullPath = Paths.get(Assets.getAssetRoot(), assetPath).toString();
            loader.save(variables, fullPath);
            hasChanges = false;
        } catch (Exception e) {
            System.err.println("Failed to save dialogue variables: " + e.getMessage());
        }
    }

    @Override
    public void onDeselect() {
        cachedPath = null;
        hasChanges = false;
        undoStack.clear();
        redoStack.clear();
        currentAsset = null;
    }

    @Override
    public boolean hasUnsavedChanges() {
        return hasChanges;
    }

    @Override
    public Class<DialogueVariables> getAssetType() {
        return DialogueVariables.class;
    }
}
