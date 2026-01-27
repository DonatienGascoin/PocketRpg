package com.pocket.rpg.editor.panels;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.ui.fields.AssetEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.resources.loaders.AnimatorControllerLoader;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiFocusedFlags;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Editor panel for creating and editing AnimatorController assets.
 * <p>
 * Features:
 * - Controller list browser
 * - States list with add/remove
 * - Parameters list with add/remove
 * - Transitions list with add/remove
 * - Properties editor for selected items
 */
public class AnimatorEditorPanel extends EditorPanel {

    public AnimatorEditorPanel() {
        super(EditorShortcuts.PanelIds.ANIMATOR_EDITOR, true);
    }

    // ========================================================================
    // STATE
    // ========================================================================

    private final List<ControllerEntry> controllers = new ArrayList<>();
    private ControllerEntry selectedEntry = null;
    private AnimatorController editingController = null;
    private boolean hasUnsavedChanges = false;

    // Selection
    private int selectedStateIndex = -1;
    private int selectedParamIndex = -1;
    private int selectedTransitionIndex = -1;

    // Dialogs
    private boolean showNewDialog = false;
    private boolean showDeleteConfirmDialog = false;
    private boolean showUnsavedChangesDialog = false;
    private ControllerEntry pendingControllerSwitch = null;
    private boolean pendingNewController = false;

    // New controller dialog
    private final ImString newControllerName = new ImString(64);

    // Editing inputs
    private final ImString stateNameInput = new ImString(64);
    private final ImString paramNameInput = new ImString(64);
    private final ImString transFromInput = new ImString(64);
    private final ImString transToInput = new ImString(64);
    private final ImInt transTypeIndex = new ImInt(0);
    private final ImInt paramTypeIndex = new ImInt(0);

    // Refresh tracking
    private boolean needsRefresh = true;

    // Undo/Redo
    private static final int MAX_UNDO_HISTORY = 50;
    private final java.util.Deque<ControllerState> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<ControllerState> redoStack = new java.util.ArrayDeque<>();

    // Status callback
    private java.util.function.Consumer<String> statusCallback;

    // ========================================================================
    // ENTRY CLASS
    // ========================================================================

    private static class ControllerEntry {
        String path;
        String filename;
        AnimatorController controller;

        ControllerEntry(String path, String filename, AnimatorController controller) {
            this.path = path;
            this.filename = filename;
            this.controller = controller;
        }
    }

    private static class ControllerState {
        final AnimatorController snapshot;
        final int selectedState;
        final int selectedParam;
        final int selectedTransition;

        ControllerState(AnimatorController ctrl, int state, int param, int trans) {
            this.snapshot = ctrl.copy();
            this.selectedState = state;
            this.selectedParam = param;
            this.selectedTransition = trans;
        }
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    public void initialize() {
        refresh();
    }

    public void refresh() {
        controllers.clear();

        List<String> paths = Assets.scanByType(AnimatorController.class);

        for (String path : paths) {
            try {
                AnimatorController ctrl = Assets.load(path, AnimatorController.class);
                int lastSlash = path.lastIndexOf('/');
                String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                controllers.add(new ControllerEntry(path, filename, ctrl));
            } catch (Exception e) {
                System.err.println("[AnimatorEditorPanel] Failed to load: " + path + " - " + e.getMessage());
            }
        }

        controllers.sort(Comparator.comparing(e -> e.filename.toLowerCase()));
        needsRefresh = false;
    }

    // ========================================================================
    // MAIN RENDER
    // ========================================================================

    @Override
    public void render() {
        if (!isOpen()) return;

        if (needsRefresh) {
            refresh();
        }

        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        boolean visible = ImGui.begin("Animator Editor", flags);
        setContentVisible(visible);
        setFocused(ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows));

        if (visible) {
            renderToolbar();
            ImGui.separator();
            renderMainContent();
        }

        ImGui.end();

        // Dialogs
        if (showNewDialog) {
            renderNewDialog();
        }
        if (showDeleteConfirmDialog) {
            renderDeleteConfirmDialog();
        }
        if (showUnsavedChangesDialog) {
            renderUnsavedChangesDialog();
        }

        // Asset picker (shared via AssetEditor)
        AssetEditor.renderAssetPicker();
    }

    // ========================================================================
    // PUBLIC API FOR SHORTCUTS
    // ========================================================================

    /**
     * Saves the current controller.
     */
    public void save() {
        if (selectedEntry != null && hasUnsavedChanges) {
            saveCurrentController();
        }
    }

    /**
     * Checks if there are unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    /**
     * Opens the new controller dialog.
     */
    public void openNewDialog() {
        if (hasUnsavedChanges) {
            pendingNewController = true;
            showUnsavedChangesDialog = true;
        } else {
            showNewDialog = true;
            newControllerName.set("new_controller");
        }
    }

    /**
     * Undoes the last action.
     */
    public void undo() {
        undoInternal();
    }

    /**
     * Redoes the last undone action.
     */
    public void redo() {
        redoInternal();
    }

    // ========================================================================
    // TOOLBAR
    // ========================================================================

    private void renderToolbar() {
        if (ImGui.button(MaterialIcons.Add + " New")) {
            openNewDialog();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create new animator controller (Ctrl+N)");
        }

        ImGui.sameLine();

        boolean canDelete = selectedEntry != null;
        if (!canDelete) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Delete + " Delete")) {
            showDeleteConfirmDialog = true;
        }
        if (!canDelete) ImGui.endDisabled();

        ImGui.sameLine();

        boolean canSave = selectedEntry != null && hasUnsavedChanges;
        if (canSave) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.5f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.6f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.7f, 0.0f, 1.0f);
        } else {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Save + " Save")) {
            saveCurrentController();
        }
        if (canSave) {
            ImGui.popStyleColor(3);
        } else {
            ImGui.endDisabled();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save changes (Ctrl+S)");
        }

        ImGui.sameLine();

        if (ImGui.button(MaterialIcons.Sync)) {
            needsRefresh = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh list (F5)");
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        boolean canUndo = !undoStack.isEmpty();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Undo)) {
            undo();
        }
        if (!canUndo) ImGui.endDisabled();

        ImGui.sameLine();

        boolean canRedo = !redoStack.isEmpty();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Redo)) {
            redo();
        }
        if (!canRedo) ImGui.endDisabled();

        // Controller dropdown on the right
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 250);
        renderControllerDropdown();
    }

    private void renderControllerDropdown() {
        String label;
        if (selectedEntry != null) {
            label = selectedEntry.filename;
            if (hasUnsavedChanges) {
                label += " *";
            }
        } else {
            label = "Select Controller...";
        }

        if (hasUnsavedChanges) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.4f, 0.35f, 0.0f, 1.0f);
        }

        ImGui.setNextItemWidth(250);
        boolean open = ImGui.beginCombo("##ControllerDropdown", label);

        if (hasUnsavedChanges) {
            ImGui.popStyleColor();
        }

        if (open) {
            for (ControllerEntry entry : controllers) {
                boolean isSelected = entry == selectedEntry;
                if (ImGui.selectable(entry.filename, isSelected)) {
                    requestControllerSwitch(entry);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }

            if (controllers.isEmpty()) {
                ImGui.textDisabled("No controllers found");
            }

            ImGui.endCombo();
        }
    }

    private void requestControllerSwitch(ControllerEntry entry) {
        if (entry == selectedEntry) return;

        if (hasUnsavedChanges) {
            pendingControllerSwitch = entry;
            showUnsavedChangesDialog = true;
        } else {
            selectController(entry);
        }
    }

    // ========================================================================
    // MAIN CONTENT
    // ========================================================================

    private void renderMainContent() {
        if (editingController == null) {
            ImGui.textDisabled("No controller selected");
            return;
        }

        float availHeight = ImGui.getContentRegionAvailY();

        if (ImGui.beginTable("MainContent", 3, ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV)) {
            ImGui.tableSetupColumn("States", ImGuiTableColumnFlags.WidthStretch, 1.0f);
            ImGui.tableSetupColumn("Parameters", ImGuiTableColumnFlags.WidthStretch, 1.0f);
            ImGui.tableSetupColumn("Transitions", ImGuiTableColumnFlags.WidthStretch, 1.5f);

            ImGui.tableNextRow();

            ImGui.tableNextColumn();
            ImGui.beginChild("StatesChild", 0, availHeight - 10, false);
            renderStatesPanel();
            ImGui.endChild();

            ImGui.tableNextColumn();
            ImGui.beginChild("ParamsChild", 0, availHeight - 10, false);
            renderParametersPanel();
            ImGui.endChild();

            ImGui.tableNextColumn();
            ImGui.beginChild("TransChild", 0, availHeight - 10, false);
            renderTransitionsPanel();
            ImGui.endChild();

            ImGui.endTable();
        }
    }

    // ========================================================================
    // STATES PANEL
    // ========================================================================

    private void renderStatesPanel() {
        ImGui.text("States");
        ImGui.separator();

        // List
        if (ImGui.beginChild("StatesList", 0, ImGui.getContentRegionAvailY() - 120, true)) {
            for (int i = 0; i < editingController.getStateCount(); i++) {
                AnimatorState state = editingController.getState(i);
                String label = state.getName();
                if (i == 0) {
                    label += " (default)";
                }

                boolean isSelected = i == selectedStateIndex;
                if (ImGui.selectable(label + "##state" + i, isSelected)) {
                    selectedStateIndex = i;
                    stateNameInput.set(state.getName());
                }
            }
        }
        ImGui.endChild();

        ImGui.separator();

        // Add button
        if (ImGui.button(MaterialIcons.Add + " Add State")) {
            captureUndoState();
            AnimatorState newState = new AnimatorState("new_state", "");
            editingController.addState(newState);
            selectedStateIndex = editingController.getStateCount() - 1;
            stateNameInput.set("new_state");
            markModified();
        }

        ImGui.sameLine();

        // Remove button
        boolean canRemove = selectedStateIndex >= 0 && editingController.getStateCount() > 1;
        if (!canRemove) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Delete + "##RemoveState")) {
            captureUndoState();
            AnimatorState state = editingController.getState(selectedStateIndex);
            editingController.removeState(state.getName());
            selectedStateIndex = Math.min(selectedStateIndex, editingController.getStateCount() - 1);
            markModified();
        }
        if (!canRemove) ImGui.endDisabled();

        // State editor
        if (selectedStateIndex >= 0 && selectedStateIndex < editingController.getStateCount()) {
            ImGui.spacing();
            AnimatorState state = editingController.getState(selectedStateIndex);

            ImGui.setNextItemWidth(-1);
            if (ImGui.inputText("Name##StateName", stateNameInput)) {
                captureUndoState();
                state.setName(stateNameInput.get());
                markModified();
            }

            // Animation asset field
            renderAnimationField("Animation", state);
        }
    }

    /**
     * Renders an animation asset field with picker.
     */
    private void renderAnimationField(String label, AnimatorState state) {
        String currentPath = state.getAnimation();
        Animation currentAnim = null;
        if (currentPath != null && !currentPath.isBlank()) {
            try {
                currentAnim = Assets.load(currentPath, Animation.class);
            } catch (Exception ignored) {
                // Asset not found
            }
        }

        String display = currentAnim != null ? FieldEditorUtils.getAssetDisplayName(currentAnim) : "(none)";

        ImGui.text(label);
        ImGui.sameLine();

        // Picker button
        if (ImGui.smallButton("...##PickAnim")) {
            AssetEditor.openPicker(Animation.class, currentPath, selected -> {
                if (selected != null) {
                    String path = Assets.getPathForResource(selected);
                    if (path != null) {
                        captureUndoState();
                        state.setAnimation(path);
                        markModified();
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

    // ========================================================================
    // PARAMETERS PANEL
    // ========================================================================

    private void renderParametersPanel() {
        ImGui.text("Parameters");
        ImGui.separator();

        // List
        if (ImGui.beginChild("ParamsList", 0, ImGui.getContentRegionAvailY() - 90, true)) {
            for (int i = 0; i < editingController.getParameterCount(); i++) {
                AnimatorParameter param = editingController.getParameter(i);
                String label = param.getName() + " (" + param.getType().name().toLowerCase() + ")";

                boolean isSelected = i == selectedParamIndex;
                if (ImGui.selectable(label + "##param" + i, isSelected)) {
                    selectedParamIndex = i;
                    paramNameInput.set(param.getName());
                    paramTypeIndex.set(param.getType().ordinal());
                }
            }
        }
        ImGui.endChild();

        ImGui.separator();

        // Add buttons
        if (ImGui.button("+ Bool")) {
            captureUndoState();
            editingController.addParameter(new AnimatorParameter("new_bool", false));
            selectedParamIndex = editingController.getParameterCount() - 1;
            markModified();
        }
        ImGui.sameLine();
        if (ImGui.button("+ Trigger")) {
            captureUndoState();
            editingController.addParameter(AnimatorParameter.trigger("new_trigger"));
            selectedParamIndex = editingController.getParameterCount() - 1;
            markModified();
        }
        ImGui.sameLine();
        if (ImGui.button("+ Dir")) {
            captureUndoState();
            editingController.addParameter(new AnimatorParameter("direction", Direction.DOWN));
            selectedParamIndex = editingController.getParameterCount() - 1;
            markModified();
        }

        // Remove button
        boolean canRemove = selectedParamIndex >= 0;
        if (!canRemove) ImGui.beginDisabled();
        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.Delete + "##RemoveParam")) {
            captureUndoState();
            editingController.removeParameter(selectedParamIndex);
            selectedParamIndex = Math.min(selectedParamIndex, editingController.getParameterCount() - 1);
            markModified();
        }
        if (!canRemove) ImGui.endDisabled();

        // Parameter editor (name only - type is immutable)
        if (selectedParamIndex >= 0 && selectedParamIndex < editingController.getParameterCount()) {
            ImGui.spacing();
            AnimatorParameter param = editingController.getParameter(selectedParamIndex);

            ImGui.setNextItemWidth(-1);
            if (ImGui.inputText("Name##ParamName", paramNameInput)) {
                captureUndoState();
                param.setName(paramNameInput.get());
                markModified();
            }
        }
    }

    // ========================================================================
    // TRANSITIONS PANEL
    // ========================================================================

    private void renderTransitionsPanel() {
        ImGui.text("Transitions");
        ImGui.separator();

        // List
        if (ImGui.beginChild("TransList", 0, ImGui.getContentRegionAvailY() - 120, true)) {
            for (int i = 0; i < editingController.getTransitionCount(); i++) {
                AnimatorTransition trans = editingController.getTransition(i);
                String label = trans.getFrom() + " -> " + trans.getTo() + " [" + trans.getType().name() + "]";
                if (trans.hasConditions()) {
                    label += " (" + trans.getConditions().size() + " conditions)";
                }

                boolean isSelected = i == selectedTransitionIndex;
                if (ImGui.selectable(label + "##trans" + i, isSelected)) {
                    selectedTransitionIndex = i;
                    transFromInput.set(trans.getFrom());
                    transToInput.set(trans.getTo());
                    transTypeIndex.set(trans.getType().ordinal());
                }
            }
        }
        ImGui.endChild();

        ImGui.separator();

        // Add button
        if (ImGui.button(MaterialIcons.Add + " Add Transition")) {
            captureUndoState();
            String fromState = editingController.getStateCount() > 0 ? editingController.getState(0).getName() : "idle";
            String toState = editingController.getStateCount() > 1 ? editingController.getState(1).getName() : fromState;
            AnimatorTransition newTrans = new AnimatorTransition(fromState, toState, TransitionType.INSTANT);
            editingController.addTransition(newTrans);
            selectedTransitionIndex = editingController.getTransitionCount() - 1;
            transFromInput.set(fromState);
            transToInput.set(toState);
            transTypeIndex.set(0);
            markModified();
        }

        ImGui.sameLine();

        // Remove button
        boolean canRemove = selectedTransitionIndex >= 0;
        if (!canRemove) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Delete + "##RemoveTrans")) {
            captureUndoState();
            editingController.removeTransition(selectedTransitionIndex);
            selectedTransitionIndex = Math.min(selectedTransitionIndex, editingController.getTransitionCount() - 1);
            markModified();
        }
        if (!canRemove) ImGui.endDisabled();

        // Transition editor
        if (selectedTransitionIndex >= 0 && selectedTransitionIndex < editingController.getTransitionCount()) {
            ImGui.spacing();
            AnimatorTransition trans = editingController.getTransition(selectedTransitionIndex);

            ImGui.setNextItemWidth(100);
            if (ImGui.inputText("From##TransFrom", transFromInput)) {
                captureUndoState();
                trans.setFrom(transFromInput.get());
                markModified();
            }
            ImGui.sameLine();
            ImGui.text("->");
            ImGui.sameLine();
            ImGui.setNextItemWidth(100);
            if (ImGui.inputText("To##TransTo", transToInput)) {
                captureUndoState();
                trans.setTo(transToInput.get());
                markModified();
            }

            String[] typeNames = {"INSTANT", "WAIT_FOR_COMPLETION", "WAIT_FOR_LOOP"};
            ImGui.setNextItemWidth(-1);
            if (ImGui.combo("Type##TransType", transTypeIndex, typeNames)) {
                captureUndoState();
                trans.setType(TransitionType.values()[transTypeIndex.get()]);
                markModified();
            }

            // Conditions section
            ImGui.spacing();
            ImGui.text("Conditions:");
            renderConditionsEditor(trans);
        }
    }

    private void renderConditionsEditor(AnimatorTransition trans) {
        if (ImGui.beginChild("ConditionsChild", 0, 60, true)) {
            for (int i = 0; i < trans.getConditions().size(); i++) {
                TransitionCondition cond = trans.getCondition(i);
                ImGui.text(cond.getParameter() + " == " + cond.getValue());
                ImGui.sameLine();
                if (ImGui.smallButton("X##cond" + i)) {
                    captureUndoState();
                    trans.removeCondition(i);
                    markModified();
                }
            }
        }
        ImGui.endChild();

        // Add condition dropdown (parameters)
        if (editingController.getParameterCount() > 0) {
            String[] paramNames = new String[editingController.getParameterCount()];
            for (int i = 0; i < paramNames.length; i++) {
                paramNames[i] = editingController.getParameter(i).getName();
            }
            ImInt condParamIdx = new ImInt(0);
            ImGui.setNextItemWidth(100);
            ImGui.combo("##AddCondParam", condParamIdx, paramNames);
            ImGui.sameLine();
            if (ImGui.button("+ Condition")) {
                captureUndoState();
                AnimatorParameter param = editingController.getParameter(condParamIdx.get());
                Object value = param.getType() == ParameterType.BOOL || param.getType() == ParameterType.TRIGGER ? true : Direction.DOWN;
                trans.addCondition(new TransitionCondition(param.getName(), value));
                markModified();
            }
        }
    }

    // ========================================================================
    // CONTROLLER OPERATIONS
    // ========================================================================

    private void selectController(ControllerEntry entry) {
        selectedEntry = entry;
        editingController = entry != null ? entry.controller : null;
        hasUnsavedChanges = false;
        selectedStateIndex = editingController != null && editingController.getStateCount() > 0 ? 0 : -1;
        selectedParamIndex = -1;
        selectedTransitionIndex = -1;
        undoStack.clear();
        redoStack.clear();
    }

    private void saveCurrentController() {
        if (selectedEntry == null || editingController == null) return;

        try {
            AnimatorControllerLoader loader = new AnimatorControllerLoader();
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), selectedEntry.path);
            loader.save(editingController, filePath.toString());
            hasUnsavedChanges = false;
            showStatus("Saved: " + selectedEntry.filename);
        } catch (IOException e) {
            System.err.println("[AnimatorEditorPanel] Failed to save: " + e.getMessage());
            showStatus("Error saving: " + e.getMessage());
        }
    }

    private void markModified() {
        hasUnsavedChanges = true;
    }

    // ========================================================================
    // UNDO/REDO
    // ========================================================================

    private void captureUndoState() {
        if (editingController == null) return;

        redoStack.clear();
        undoStack.push(new ControllerState(editingController, selectedStateIndex, selectedParamIndex, selectedTransitionIndex));

        while (undoStack.size() > MAX_UNDO_HISTORY) {
            undoStack.removeLast();
        }
    }

    private void undoInternal() {
        if (undoStack.isEmpty() || editingController == null) return;

        redoStack.push(new ControllerState(editingController, selectedStateIndex, selectedParamIndex, selectedTransitionIndex));
        ControllerState state = undoStack.pop();
        editingController.copyFrom(state.snapshot);
        selectedStateIndex = Math.min(state.selectedState, editingController.getStateCount() - 1);
        selectedParamIndex = Math.min(state.selectedParam, editingController.getParameterCount() - 1);
        selectedTransitionIndex = Math.min(state.selectedTransition, editingController.getTransitionCount() - 1);
        hasUnsavedChanges = true;
    }

    private void redoInternal() {
        if (redoStack.isEmpty() || editingController == null) return;

        undoStack.push(new ControllerState(editingController, selectedStateIndex, selectedParamIndex, selectedTransitionIndex));
        ControllerState state = redoStack.pop();
        editingController.copyFrom(state.snapshot);
        selectedStateIndex = Math.min(state.selectedState, editingController.getStateCount() - 1);
        selectedParamIndex = Math.min(state.selectedParam, editingController.getParameterCount() - 1);
        selectedTransitionIndex = Math.min(state.selectedTransition, editingController.getTransitionCount() - 1);
        hasUnsavedChanges = true;
    }

    // ========================================================================
    // DIALOGS
    // ========================================================================

    private void renderNewDialog() {
        ImGui.openPopup("New Animator Controller");
        if (ImGui.beginPopupModal("New Animator Controller", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Controller Name:");
            ImGui.setNextItemWidth(200);
            ImGui.inputText("##NewCtrlName", newControllerName);

            ImGui.spacing();

            if (ImGui.button("Create", 100, 0)) {
                createNewController(newControllerName.get());
                showNewDialog = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                showNewDialog = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void createNewController(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "new_controller";
        }

        String baseName = name.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = baseName + ".animator.json";
        String path = "animators/" + filename;
        java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), path);

        int counter = 1;
        while (Files.exists(filePath)) {
            filename = baseName + "_" + counter + ".animator.json";
            path = "animators/" + filename;
            filePath = Paths.get(Assets.getAssetRoot(), path);
            counter++;
        }

        try {
            Files.createDirectories(filePath.getParent());

            AnimatorController newCtrl = new AnimatorController(baseName);
            newCtrl.addState(new AnimatorState("idle", ""));

            AnimatorControllerLoader loader = new AnimatorControllerLoader();
            loader.save(newCtrl, filePath.toString());

            // Notify asset browser of new asset
            EditorEventBus.get().publish(new AssetChangedEvent(path, AssetChangedEvent.ChangeType.CREATED));

            needsRefresh = true;
            refresh();

            final String finalPath = path;
            for (ControllerEntry entry : controllers) {
                if (entry.path.equals(finalPath)) {
                    selectController(entry);
                    break;
                }
            }
            showStatus("Created: " + filename);
        } catch (IOException e) {
            System.err.println("[AnimatorEditorPanel] Failed to create: " + e.getMessage());
            showStatus("Error creating: " + e.getMessage());
        }
    }

    private void renderDeleteConfirmDialog() {
        ImGui.openPopup("Delete Controller?");
        if (ImGui.beginPopupModal("Delete Controller?", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Are you sure you want to delete this controller?");
            if (selectedEntry != null) {
                ImGui.textColored(1f, 0.8f, 0.4f, 1f, selectedEntry.filename);
            }
            ImGui.text("This action cannot be undone.");

            ImGui.spacing();

            if (ImGui.button("Delete", 100, 0)) {
                deleteCurrentController();
                showDeleteConfirmDialog = false;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                showDeleteConfirmDialog = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void deleteCurrentController() {
        if (selectedEntry == null) return;

        String filename = selectedEntry.filename;
        String path = selectedEntry.path;
        try {
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), path);
            Files.deleteIfExists(filePath);
            controllers.remove(selectedEntry);
            selectController(null);

            // Notify asset browser of deleted asset
            EditorEventBus.get().publish(new AssetChangedEvent(path, AssetChangedEvent.ChangeType.DELETED));

            showStatus("Deleted: " + filename);
        } catch (IOException e) {
            System.err.println("[AnimatorEditorPanel] Failed to delete: " + e.getMessage());
            showStatus("Error deleting: " + e.getMessage());
        }
    }

    private void renderUnsavedChangesDialog() {
        ImGui.openPopup("Unsaved Changes");
        if (ImGui.beginPopupModal("Unsaved Changes", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes.");
            ImGui.text("What would you like to do?");

            ImGui.spacing();

            if (ImGui.button("Save", 100, 0)) {
                saveCurrentController();
                showUnsavedChangesDialog = false;
                ImGui.closeCurrentPopup();
                proceedAfterUnsavedDialog();
            }
            ImGui.sameLine();
            if (ImGui.button("Discard", 100, 0)) {
                showUnsavedChangesDialog = false;
                ImGui.closeCurrentPopup();
                proceedAfterUnsavedDialog();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                pendingControllerSwitch = null;
                pendingNewController = false;
                showUnsavedChangesDialog = false;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void proceedAfterUnsavedDialog() {
        if (pendingControllerSwitch != null) {
            selectController(pendingControllerSwitch);
            pendingControllerSwitch = null;
        } else if (pendingNewController) {
            pendingNewController = false;
            showNewDialog = true;
            newControllerName.set("new_controller");
        }
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    public void setStatusCallback(java.util.function.Consumer<String> callback) {
        this.statusCallback = callback;
    }

    public void selectControllerByPath(String path) {
        ImGui.setWindowFocus("Animator Editor");
        refresh();
        for (ControllerEntry entry : controllers) {
            if (entry.path.equals(path)) {
                if (hasUnsavedChanges) {
                    pendingControllerSwitch = entry;
                    showUnsavedChangesDialog = true;
                } else {
                    selectController(entry);
                }
                return;
            }
        }
        showStatus("Controller not found: " + path);
    }

    private void showStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }

    public void destroy() {
        // Cleanup if needed
    }
}
