package com.pocket.rpg.editor.panels;

import com.pocket.rpg.animation.AnimatorLayoutData;
import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AnimatorSelectionClearedEvent;
import com.pocket.rpg.editor.events.AnimatorStateSelectedEvent;
import com.pocket.rpg.editor.events.AnimatorTransitionSelectedEvent;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.animator.AnimatorGraphEditor;
import com.pocket.rpg.editor.panels.animator.AnimatorPreviewPanel;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.shortcut.ShortcutBinding;
import com.pocket.rpg.editor.ui.fields.AssetEditor;
import imgui.flag.ImGuiKey;
import com.pocket.rpg.resources.loaders.AnimatorControllerLoader;
import com.pocket.rpg.resources.loaders.AnimatorLayoutLoader;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.ImVec2;
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
 * - Visual node graph editor for states and transitions
 * - Side inspector for editing selected items
 * - Parameters bar at bottom
 * - Undo/redo support
 */
public class AnimatorEditorPanel extends EditorPanel {

    public AnimatorEditorPanel() {
        super(EditorShortcuts.PanelIds.ANIMATOR_EDITOR, true);
    }

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    private static final float PARAMETERS_PANEL_WIDTH = 200f;
    private static final float PREVIEW_PANEL_WIDTH = 220f;

    // ========================================================================
    // STATE
    // ========================================================================

    private final List<ControllerEntry> controllers = new ArrayList<>();
    private ControllerEntry selectedEntry = null;
    private AnimatorController editingController = null;
    private boolean hasUnsavedChanges = false;

    // Graph editor
    private AnimatorGraphEditor graphEditor;
    private AnimatorLayoutData layoutData;
    private final AnimatorLayoutLoader layoutLoader = new AnimatorLayoutLoader();

    // Preview panel
    private AnimatorPreviewPanel previewPanel;
    private boolean previewPanelOpen = false;

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

    // Parameter rename editing
    private int renamingParamIndex = -1;
    private final ImString renameInput = new ImString(64);

    // Refresh tracking
    private boolean needsRefresh = true;

    // Undo/Redo
    private static final int MAX_UNDO_HISTORY = 50;
    private final java.util.Deque<ControllerState> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<ControllerState> redoStack = new java.util.ArrayDeque<>();

    // Status callback
    private java.util.function.Consumer<String> statusCallback;

    // Selection manager
    private com.pocket.rpg.editor.EditorSelectionManager selectionManager;

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
        // Initialize graph editor
        graphEditor = new AnimatorGraphEditor();
        graphEditor.initialize();
        setupGraphEditorCallbacks();

        // Initialize preview panel
        previewPanel = new AnimatorPreviewPanel();

        refresh();
    }

    @Override
    public java.util.List<ShortcutAction> provideShortcuts(KeyboardLayout layout) {
        ShortcutBinding undoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrl(ImGuiKey.W)
                : ShortcutBinding.ctrl(ImGuiKey.Z);
        ShortcutBinding redoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrlShift(ImGuiKey.W)
                : ShortcutBinding.ctrlShift(ImGuiKey.Z);

        return java.util.List.of(
                panelShortcut()
                        .id("editor.animator.save")
                        .displayName("Save Animator")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.S))
                        .allowInInput(true)
                        .handler(this::save)
                        .build(),
                panelShortcut()
                        .id("editor.animator.new")
                        .displayName("New Animator")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.N))
                        .handler(this::openNewDialog)
                        .build(),
                panelShortcut()
                        .id("editor.animator.undo")
                        .displayName("Animator Undo")
                        .defaultBinding(undoBinding)
                        .allowInInput(true)
                        .handler(this::undo)
                        .build(),
                panelShortcut()
                        .id("editor.animator.redo")
                        .displayName("Animator Redo")
                        .defaultBinding(redoBinding)
                        .allowInInput(true)
                        .handler(this::redo)
                        .build(),
                panelShortcut()
                        .id("editor.animator.refresh")
                        .displayName("Refresh Animator List")
                        .defaultBinding(ShortcutBinding.key(ImGuiKey.F5))
                        .handler(this::refresh)
                        .build()
        );
    }

    private void setupGraphEditorCallbacks() {
        graphEditor.setOnStateSelected(stateName -> {
            int index = editingController != null ? editingController.getStateIndex(stateName) : -1;
            if (index >= 0) {
                selectedStateIndex = index;
                selectedTransitionIndex = -1;
                AnimatorState state = editingController.getState(index);
                stateNameInput.set(state.getName());
                publishStateSelected(state);
            }
        });

        graphEditor.setOnTransitionSelected(transIndex -> {
            if (transIndex >= 0 && editingController != null && transIndex < editingController.getTransitionCount()) {
                selectedTransitionIndex = transIndex;
                selectedStateIndex = -1;
                AnimatorTransition trans = editingController.getTransition(transIndex);
                transFromInput.set(trans.getFrom());
                transToInput.set(trans.getTo());
                transTypeIndex.set(trans.getType().ordinal());
                publishTransitionSelected(trans);
            }
        });

        graphEditor.setOnSelectionCleared(() -> {
            selectedStateIndex = -1;
            selectedTransitionIndex = -1;
            clearInspectorSelection();
        });

        graphEditor.setOnAddState(() -> {
            captureUndoState();
            String newName = generateUniqueStateName("new_state");
            AnimatorState newState = new AnimatorState(newName, "");
            editingController.addState(newState);

            // Position near context menu
            ImVec2 pos = graphEditor.getNewNodePosition();
            graphEditor.setNodePosition(newName, pos.x, pos.y);

            selectedStateIndex = editingController.getStateCount() - 1;
            selectedTransitionIndex = -1;
            stateNameInput.set(newName);
            markModified();
            publishStateSelected(newState);
        });

        graphEditor.setOnEditState(stateName -> {
            int index = editingController.getStateIndex(stateName);
            if (index >= 0) {
                selectedStateIndex = index;
                selectedTransitionIndex = -1;
                stateNameInput.set(stateName);
                publishStateSelected(editingController.getState(index));
            }
        });

        graphEditor.setOnDeleteState(stateName -> {
            editingController.removeState(stateName);
            selectedStateIndex = Math.min(selectedStateIndex, editingController.getStateCount() - 1);
            markModified();
            clearInspectorSelection();
        });

        graphEditor.setOnSetDefaultState(stateName -> {
            editingController.setDefaultState(stateName);
            markModified();
        });

        graphEditor.setOnCreateTransition(indices -> {
            int fromIndex = indices[0];
            int toIndex = indices[1];
            String fromState = editingController.getState(fromIndex).getName();
            String toState = editingController.getState(toIndex).getName();

            AnimatorTransition newTrans = new AnimatorTransition(fromState, toState, TransitionType.INSTANT);
            editingController.addTransition(newTrans);

            selectedTransitionIndex = editingController.getTransitionCount() - 1;
            selectedStateIndex = -1;
            transFromInput.set(fromState);
            transToInput.set(toState);
            transTypeIndex.set(0);
            markModified();
            publishTransitionSelected(newTrans);
        });

        graphEditor.setOnEditTransition(transIndex -> {
            if (transIndex >= 0 && transIndex < editingController.getTransitionCount()) {
                selectedTransitionIndex = transIndex;
                selectedStateIndex = -1;
                AnimatorTransition trans = editingController.getTransition(transIndex);
                transFromInput.set(trans.getFrom());
                transToInput.set(trans.getTo());
                transTypeIndex.set(trans.getType().ordinal());
                publishTransitionSelected(trans);
            }
        });

        graphEditor.setOnDeleteTransition(transIndex -> {
            editingController.removeTransition(transIndex);
            selectedTransitionIndex = Math.min(selectedTransitionIndex, editingController.getTransitionCount() - 1);
            markModified();
            clearInspectorSelection();
        });

        graphEditor.setOnAutoLayout(() -> {
            graphEditor.requestLayout();
        });

        graphEditor.setOnCaptureUndo(this::captureUndoState);
    }

    // ========================================================================
    // INSPECTOR SELECTION EVENTS
    // ========================================================================

    private void publishStateSelected(AnimatorState state) {
        if (selectionManager != null) {
            selectionManager.selectAnimatorState(state, editingController, this::onInspectorModified);
        }
        EditorEventBus.get().publish(new AnimatorStateSelectedEvent(
            state,
            editingController,
            this::onInspectorModified
        ));
    }

    private void publishTransitionSelected(AnimatorTransition transition) {
        if (selectionManager != null) {
            selectionManager.selectAnimatorTransition(transition, editingController, this::onInspectorModified);
        }
        EditorEventBus.get().publish(new AnimatorTransitionSelectedEvent(
            transition,
            editingController,
            this::onInspectorModified
        ));
    }

    private void clearInspectorSelection() {
        if (selectionManager != null && (selectionManager.isAnimatorStateSelected() || selectionManager.isAnimatorTransitionSelected())) {
            selectionManager.clearSelection();
        }
        EditorEventBus.get().publish(new AnimatorSelectionClearedEvent());
    }

    /**
     * Called by Inspector when it modifies state/transition data.
     * Captures undo state and marks the controller as modified.
     */
    private void onInspectorModified() {
        captureUndoState();
        markModified();
    }

    private String generateUniqueStateName(String baseName) {
        if (editingController == null || !editingController.hasState(baseName)) {
            return baseName;
        }
        int counter = 1;
        String name;
        do {
            name = baseName + "_" + counter;
            counter++;
        } while (editingController.hasState(name));
        return name;
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
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 290);
        renderControllerDropdown();

        // Hamburger menu for preview panel (after dropdown)
        ImGui.sameLine();
        boolean wasOpen = previewPanelOpen;
        if (wasOpen) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.5f, 0.3f, 1.0f);
        }
        if (ImGui.button(MaterialIcons.Menu + "##preview")) {
            previewPanelOpen = !previewPanelOpen;
            if (previewPanelOpen && editingController != null) {
                previewPanel.setController(editingController);
            } else if (!previewPanelOpen) {
                previewPanel.stopAndReset();
            }
        }
        if (wasOpen) {
            ImGui.popStyleColor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(previewPanelOpen ? "Hide Preview Panel" : "Show Preview Panel");
        }
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

        // Update preview panel
        if (previewPanelOpen && previewPanel != null) {
            previewPanel.update(ImGui.getIO().getDeltaTime());
            // Update graph editor highlighting
            graphEditor.setActiveState(previewPanel.getActiveStateName());
            graphEditor.setPendingTransitionTarget(previewPanel.getPendingTransitionTarget());
        } else {
            graphEditor.clearPreviewHighlighting();
        }

        float availHeight = ImGui.getContentRegionAvailY();
        int columnCount = previewPanelOpen ? 3 : 2;

        // Main layout: Parameters list | Graph canvas | (Preview panel)
        if (ImGui.beginTable("MainContent", columnCount, ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV)) {
            ImGui.tableSetupColumn("Parameters", ImGuiTableColumnFlags.WidthFixed, PARAMETERS_PANEL_WIDTH);
            ImGui.tableSetupColumn("Graph", ImGuiTableColumnFlags.WidthStretch);
            if (previewPanelOpen) {
                ImGui.tableSetupColumn("Preview", ImGuiTableColumnFlags.WidthFixed, PREVIEW_PANEL_WIDTH);
            }

            ImGui.tableNextRow();

            // Parameters list (left column)
            ImGui.tableNextColumn();
            ImGui.beginChild("ParametersChild", 0, availHeight, false);
            renderParametersList();
            ImGui.endChild();

            // Graph canvas (center column)
            // No beginChild wrapper - ImNodes creates its own internal child window
            // and an extra wrapper prevents scroll events from reaching it (breaks zoom)
            ImGui.tableNextColumn();
            graphEditor.render(editingController);

            // Preview panel (right column, when open)
            if (previewPanelOpen) {
                ImGui.tableNextColumn();
                ImGui.beginChild("PreviewChild", 0, availHeight, false);
                previewPanel.render(editingController);
                ImGui.endChild();
            }

            ImGui.endTable();
        }
    }

    // ========================================================================
    // PARAMETERS LIST (Left Panel)
    // ========================================================================

    private void renderParametersList() {
        ImGui.text("Parameters");
        ImGui.separator();

        // Parameter list
        for (int i = 0; i < editingController.getParameterCount(); i++) {
            AnimatorParameter param = editingController.getParameter(i);

            boolean isSelected = i == selectedParamIndex;
            boolean isRenaming = i == renamingParamIndex;

            // Type icon
            String icon = switch (param.getType()) {
                case BOOL -> MaterialIcons.CheckBox;
                case TRIGGER -> MaterialIcons.Bolt;
                case DIRECTION -> MaterialIcons.Explore;
            };

            // Renaming mode
            if (isRenaming) {
                ImGui.text(icon);
                ImGui.sameLine();
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.setKeyboardFocusHere();
                if (ImGui.inputText("##rename" + i, renameInput,
                        imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue | imgui.flag.ImGuiInputTextFlags.AutoSelectAll)) {
                    // Enter pressed - confirm rename
                    finishParameterRename(param);
                }
                // Cancel on Escape or click elsewhere
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
                    renamingParamIndex = -1;
                } else if (!ImGui.isItemActive() && ImGui.isMouseClicked(0)) {
                    finishParameterRename(param);
                }
            } else if (previewPanelOpen && previewPanel != null) {
                // Preview mode - show editable values inline
                ImGui.text(icon + " " + param.getName());
                ImGui.sameLine(ImGui.getContentRegionAvailX() - 85);
                previewPanel.renderParameterValue(param);
            } else {
                // Normal selectable mode
                String label = icon + " " + param.getName();
                if (ImGui.selectable(label + "##param" + i, isSelected)) {
                    selectedParamIndex = i;
                    paramNameInput.set(param.getName());
                }

                // Double-click to rename
                if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                    renamingParamIndex = i;
                    renameInput.set(param.getName());
                }
            }

            // Type hint on hover (when not renaming)
            if (!isRenaming && ImGui.isItemHovered()) {
                ImGui.setTooltip(param.getType().name().toLowerCase() + " - Double-click to rename, Right-click to delete");
            }

            // Right-click context menu
            if (ImGui.beginPopupContextItem("param_ctx_" + i)) {
                if (ImGui.menuItem("Rename")) {
                    renamingParamIndex = i;
                    renameInput.set(param.getName());
                }
                if (ImGui.menuItem("Delete")) {
                    captureUndoState();
                    editingController.removeParameter(i);
                    if (selectedParamIndex == i) {
                        selectedParamIndex = -1;
                    } else if (selectedParamIndex > i) {
                        selectedParamIndex--;
                    }
                    if (renamingParamIndex == i) {
                        renamingParamIndex = -1;
                    } else if (renamingParamIndex > i) {
                        renamingParamIndex--;
                    }
                    markModified();
                }
                ImGui.endPopup();
            }
        }

        if (editingController.getParameterCount() == 0) {
            ImGui.textDisabled("No parameters");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Add parameter buttons (vertical layout)
        float buttonWidth = ImGui.getContentRegionAvailX();
        if (ImGui.button(MaterialIcons.Add + " Bool", buttonWidth, 0)) {
            captureUndoState();
            String name = generateUniqueParamName("new_bool");
            editingController.addParameter(new AnimatorParameter(name, false));
            selectedParamIndex = editingController.getParameterCount() - 1;
            markModified();
        }
        if (ImGui.button(MaterialIcons.Add + " Trigger", buttonWidth, 0)) {
            captureUndoState();
            String name = generateUniqueParamName("new_trigger");
            editingController.addParameter(AnimatorParameter.trigger(name));
            selectedParamIndex = editingController.getParameterCount() - 1;
            markModified();
        }
        if (ImGui.button(MaterialIcons.Add + " Direction", buttonWidth, 0)) {
            captureUndoState();
            String name = generateUniqueParamName("direction");
            editingController.addParameter(new AnimatorParameter(name, Direction.DOWN));
            selectedParamIndex = editingController.getParameterCount() - 1;
            markModified();
        }
    }

    private void finishParameterRename(AnimatorParameter param) {
        String newName = renameInput.get().trim();
        String oldName = param.getName();
        renamingParamIndex = -1;

        if (newName.isEmpty() || newName.equals(oldName)) {
            return; // No change or empty name
        }

        if (editingController.hasParameter(newName)) {
            showStatus("Parameter name already exists: " + newName);
            return;
        }

        captureUndoState();

        // Update parameter name
        param.setName(newName);

        // Update all transition conditions that reference this parameter
        for (int i = 0; i < editingController.getTransitionCount(); i++) {
            AnimatorTransition trans = editingController.getTransition(i);
            for (TransitionCondition cond : trans.getConditions()) {
                if (cond.getParameter().equals(oldName)) {
                    cond.setParameter(newName);
                }
            }
        }

        // Update all directional states that reference this parameter
        for (int i = 0; i < editingController.getStateCount(); i++) {
            AnimatorState state = editingController.getState(i);
            if (oldName.equals(state.getDirectionParameter())) {
                state.setDirectionParameter(newName);
            }
        }

        markModified();
        showStatus("Renamed parameter: " + oldName + " -> " + newName);
    }

    private String generateUniqueParamName(String baseName) {
        if (editingController == null || !editingController.hasParameter(baseName)) {
            return baseName;
        }
        int counter = 1;
        String name;
        do {
            name = baseName + "_" + counter;
            counter++;
        } while (editingController.hasParameter(name));
        return name;
    }

    // ========================================================================
    // CONTROLLER OPERATIONS
    // ========================================================================

    private void selectController(ControllerEntry entry) {
        // Clear inspector selection first
        clearInspectorSelection();

        selectedEntry = entry;
        editingController = entry != null ? entry.controller : null;
        hasUnsavedChanges = false;
        selectedStateIndex = editingController != null && editingController.getStateCount() > 0 ? 0 : -1;
        selectedParamIndex = -1;
        selectedTransitionIndex = -1;
        undoStack.clear();
        redoStack.clear();

        // Load layout data
        if (entry != null) {
            String fullPath = Paths.get(Assets.getAssetRoot(), entry.path).toString();
            layoutData = layoutLoader.load(fullPath);
        } else {
            layoutData = new AnimatorLayoutData();
        }

        // Reset graph editor for new controller
        if (graphEditor != null) {
            graphEditor.reset();

            // Apply loaded layout positions
            if (layoutData != null && editingController != null) {
                for (int i = 0; i < editingController.getStateCount(); i++) {
                    String stateName = editingController.getState(i).getName();
                    AnimatorLayoutData.NodeLayout nodeLayout = layoutData.getNodeLayout(stateName);
                    if (nodeLayout != null) {
                        graphEditor.setNodePosition(stateName, nodeLayout.getX(), nodeLayout.getY());
                    }
                }
            }

            // Select first state (triggers callback which publishes event)
            if (editingController != null && editingController.getStateCount() > 0) {
                graphEditor.selectState(editingController.getState(0).getName());
            }
        }

        // Update preview panel
        if (previewPanel != null) {
            previewPanel.setController(editingController);
        }
    }

    private void saveCurrentController() {
        if (selectedEntry == null || editingController == null) return;

        try {
            AnimatorControllerLoader loader = new AnimatorControllerLoader();
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), selectedEntry.path);
            loader.save(editingController, filePath.toString());

            // Save layout data
            saveLayoutData();

            hasUnsavedChanges = false;
            showStatus("Saved: " + selectedEntry.filename);
        } catch (IOException e) {
            System.err.println("[AnimatorEditorPanel] Failed to save: " + e.getMessage());
            showStatus("Error saving: " + e.getMessage());
        }
    }

    private void saveLayoutData() {
        if (selectedEntry == null || graphEditor == null || layoutData == null) return;

        // Update layout data from graph editor positions
        for (int i = 0; i < editingController.getStateCount(); i++) {
            String stateName = editingController.getState(i).getName();
            float[] pos = graphEditor.getNodePosition(stateName);
            if (pos != null) {
                layoutData.setNodeLayout(stateName, pos[0], pos[1]);
            }
        }

        // Save to file
        try {
            String fullPath = Paths.get(Assets.getAssetRoot(), selectedEntry.path).toString();
            layoutLoader.save(fullPath, layoutData);
        } catch (IOException e) {
            System.err.println("[AnimatorEditorPanel] Failed to save layout: " + e.getMessage());
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

    public void setSelectionManager(com.pocket.rpg.editor.EditorSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    /**
     * Clears the panel's internal selection state and the graph editor's ImNodes selection.
     * Called when an external selection (e.g. entity in hierarchy) takes over,
     * so that re-clicking the same node will fire the selection callback again.
     */
    public void clearGraphSelection() {
        selectedStateIndex = -1;
        selectedTransitionIndex = -1;
        if (graphEditor != null) {
            graphEditor.clearSelection();
        }
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
        if (graphEditor != null) {
            graphEditor.destroy();
            graphEditor = null;
        }
        if (previewPanel != null) {
            previewPanel.destroy();
            previewPanel = null;
        }
    }
}
