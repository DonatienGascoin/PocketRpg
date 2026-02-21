package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.animation.AnimatorLayoutData;
import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.*;
import com.pocket.rpg.editor.panels.AssetCreationInfo;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.panels.animator.AnimatorGraphEditor;
import com.pocket.rpg.editor.panels.animator.AnimatorPreviewPanel;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.ui.fields.AssetEditor;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.AnimatorControllerLoader;
import com.pocket.rpg.resources.loaders.AnimatorLayoutLoader;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

/**
 * Content implementation for editing .animator.json assets in the unified AssetEditorPanel.
 * <p>
 * Renders the visual node graph editor, parameters list, and optional preview panel.
 * The hamburger sidebar handles controller file selection.
 */
@EditorContentFor(com.pocket.rpg.animation.animator.AnimatorController.class)
public class AnimatorEditorContent implements AssetEditorContent {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    private static final float PARAMETERS_PANEL_WIDTH = 200f;
    private static final float PREVIEW_PANEL_WIDTH = 220f;
    private static final AssetCreationInfo CREATION_INFO = new AssetCreationInfo("animators/", ".animator.json");

    // ========================================================================
    // STATE
    // ========================================================================

    private AnimatorController editingController;
    private AssetEditorShell shell;

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

    // Undo — deferred snapshot pattern
    private ControllerState pendingBeforeSnapshot = null;

    // Selection manager (obtained from shell in onAssetLoaded)
    private EditorSelectionManager selectionManager;

    // Event subscription (for cleanup)
    private Consumer<SelectionChangedEvent> selectionChangedHandler;

    // ========================================================================
    // INNER TYPES
    // ========================================================================

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
    // CONSTRUCTOR
    // ========================================================================

    public AnimatorEditorContent() {
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void initialize() {
        graphEditor = new AnimatorGraphEditor();
        graphEditor.initialize();
        setupGraphEditorCallbacks();

        previewPanel = new AnimatorPreviewPanel();

        // Subscribe to selection changes to clear graph selection when focus moves away
        selectionChangedHandler = event -> {
            boolean wasAnimator = event.previousType() == EditorSelectionManager.SelectionType.ANIMATOR_STATE
                    || event.previousType() == EditorSelectionManager.SelectionType.ANIMATOR_TRANSITION;
            boolean isAnimator = event.selectionType() == EditorSelectionManager.SelectionType.ANIMATOR_STATE
                    || event.selectionType() == EditorSelectionManager.SelectionType.ANIMATOR_TRANSITION;
            if (wasAnimator && !isAnimator) {
                selectedStateIndex = -1;
                selectedTransitionIndex = -1;
                if (graphEditor != null) {
                    graphEditor.clearSelection();
                }
            }
        };
        EditorEventBus.get().subscribe(SelectionChangedEvent.class, selectionChangedHandler);
    }

    @Override
    public void destroy() {
        // Unsubscribe from events
        if (selectionChangedHandler != null) {
            EditorEventBus.get().unsubscribe(SelectionChangedEvent.class, selectionChangedHandler);
            selectionChangedHandler = null;
        }

        if (graphEditor != null) {
            graphEditor.destroy();
            graphEditor = null;
        }
        if (previewPanel != null) {
            previewPanel.destroy();
            previewPanel = null;
        }
    }

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        // Clear inspector selection from previous controller
        clearInspectorSelection();

        this.editingController = (AnimatorController) asset;
        this.shell = shell;
        this.selectionManager = shell.getSelectionManager();

        selectedStateIndex = editingController.getStateCount() > 0 ? 0 : -1;
        selectedParamIndex = -1;
        selectedTransitionIndex = -1;
        pendingBeforeSnapshot = null;

        // Load layout data
        String fullPath = Paths.get(Assets.getAssetRoot(), path).toString();
        layoutData = layoutLoader.load(fullPath);

        // Reset graph editor for new controller
        if (graphEditor != null) {
            graphEditor.reset();

            // Apply loaded layout positions
            if (layoutData != null) {
                for (int i = 0; i < editingController.getStateCount(); i++) {
                    String stateName = editingController.getState(i).getName();
                    AnimatorLayoutData.NodeLayout nodeLayout = layoutData.getNodeLayout(stateName);
                    if (nodeLayout != null) {
                        graphEditor.setNodePosition(stateName, nodeLayout.getX(), nodeLayout.getY());
                    }
                }
            }

            // Select first state (triggers callback which publishes event)
            if (editingController.getStateCount() > 0) {
                graphEditor.selectState(editingController.getState(0).getName());
            }
        }

        // Update preview panel
        if (previewPanel != null) {
            previewPanel.setController(editingController);
        }
    }

    @Override
    public void onAssetUnloaded() {
        clearInspectorSelection();
        editingController = null;
        selectedStateIndex = -1;
        selectedParamIndex = -1;
        selectedTransitionIndex = -1;
        pendingBeforeSnapshot = null;

        if (previewPanel != null) {
            previewPanel.stopAndReset();
        }
    }

    @Override
    public AssetCreationInfo getCreationInfo() {
        return CREATION_INFO;
    }

    @Override
    public Class<?> getAssetClass() {
        return AnimatorController.class;
    }

    // ========================================================================
    // CONTENT INTERFACE
    // ========================================================================

    @Override
    public void render() {
        if (editingController == null) return;

        flushPendingUndo();
        renderMainContent();
    }

    @Override
    public void renderToolbarExtras() {
        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // New
        if (ImGui.button(MaterialIcons.Add + " New##ctrlNew")) {
            openNewDialog();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create new animator controller (Ctrl+N)");
        }

        ImGui.sameLine();

        // Delete
        boolean canDelete = editingController != null;
        if (!canDelete) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Delete + "##ctrlDelete")) {
            showDeleteConfirmDialog = true;
        }
        if (!canDelete) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Delete current controller");
        }

        ImGui.sameLine();

        // Refresh
        if (ImGui.button(MaterialIcons.Sync + "##ctrlRefresh")) {
            if (shell != null) shell.requestSidebarRefresh();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh asset list (F5)");
        }

        ImGui.sameLine();

        // Preview panel toggle
        boolean wasOpen = previewPanelOpen;
        if (wasOpen) {
            EditorColors.pushSuccessButton();
        }
        if (ImGui.button(MaterialIcons.Preview + "##preview")) {
            previewPanelOpen = !previewPanelOpen;
            if (previewPanelOpen && editingController != null) {
                previewPanel.setController(editingController);
            } else if (!previewPanelOpen) {
                previewPanel.stopAndReset();
            }
        }
        if (wasOpen) {
            EditorColors.popButtonColors();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(previewPanelOpen ? "Hide Preview Panel" : "Show Preview Panel");
        }
    }

    @Override
    public void renderPopups() {
        if (showNewDialog) {
            renderNewDialog();
        }
        if (showDeleteConfirmDialog) {
            renderDeleteConfirmDialog();
        }

        // Asset picker (shared via AssetEditor)
        AssetEditor.renderAssetPicker();
    }

    @Override
    public List<ShortcutAction> provideExtraShortcuts(KeyboardLayout layout) {
        return List.of();
    }

    @Override
    public void onNewRequested() {
        openNewDialog();
    }

    @Override
    public boolean hasCustomSave() {
        return true;
    }

    @Override
    public void customSave(String path) {
        if (editingController == null) return;
        try {
            AnimatorControllerLoader loader = new AnimatorControllerLoader();
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), path);
            loader.save(editingController, filePath.toString());

            // Save layout data
            saveLayoutData(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save animator controller: " + e.getMessage(), e);
        }
    }

    private void saveLayoutData(String path) {
        if (graphEditor == null || layoutData == null || editingController == null) return;

        // Update layout data from graph editor positions
        for (int i = 0; i < editingController.getStateCount(); i++) {
            String stateName = editingController.getState(i).getName();
            float[] pos = graphEditor.getNodePosition(stateName);
            if (pos != null) {
                layoutData.setNodeLayout(stateName, pos[0], pos[1]);
            }
        }

        try {
            String fullPath = Paths.get(Assets.getAssetRoot(), path).toString();
            layoutLoader.save(fullPath, layoutData);
        } catch (IOException e) {
            System.err.println("[AnimatorEditorContent] Failed to save layout: " + e.getMessage());
        }
    }

    // ========================================================================
    // GRAPH EDITOR CALLBACKS
    // ========================================================================

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

        graphEditor.setOnAutoLayout(() -> graphEditor.requestLayout());

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
                state, editingController, this::onInspectorModified));
    }

    private void publishTransitionSelected(AnimatorTransition transition) {
        if (selectionManager != null) {
            selectionManager.selectAnimatorTransition(transition, editingController, this::onInspectorModified);
        }
        EditorEventBus.get().publish(new AnimatorTransitionSelectedEvent(
                transition, editingController, this::onInspectorModified));
    }

    private void clearInspectorSelection() {
        if (selectionManager != null && (selectionManager.isAnimatorStateSelected()
                || selectionManager.isAnimatorTransitionSelected())) {
            selectionManager.clearSelection();
        }
        EditorEventBus.get().publish(new AnimatorSelectionClearedEvent());
    }

    private void onInspectorModified() {
        captureUndoState();
        markModified();
    }

    // ========================================================================
    // MAIN CONTENT
    // ========================================================================

    private void renderMainContent() {
        // Update preview panel
        if (previewPanelOpen && previewPanel != null) {
            previewPanel.update(ImGui.getIO().getDeltaTime());
            graphEditor.setActiveState(previewPanel.getActiveStateName());
            graphEditor.setPendingTransitionTarget(previewPanel.getPendingTransitionTarget());
        } else {
            graphEditor.clearPreviewHighlighting();
        }

        float availHeight = ImGui.getContentRegionAvailY();
        int columnCount = previewPanelOpen ? 3 : 2;

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

        for (int i = 0; i < editingController.getParameterCount(); i++) {
            AnimatorParameter param = editingController.getParameter(i);

            boolean isSelected = i == selectedParamIndex;
            boolean isRenaming = i == renamingParamIndex;

            String icon = switch (param.getType()) {
                case BOOL -> MaterialIcons.CheckBox;
                case TRIGGER -> MaterialIcons.Bolt;
                case DIRECTION -> MaterialIcons.Explore;
            };

            if (isRenaming) {
                ImGui.text(icon);
                ImGui.sameLine();
                ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                ImGui.setKeyboardFocusHere();
                if (ImGui.inputText("##rename" + i, renameInput,
                        imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue | imgui.flag.ImGuiInputTextFlags.AutoSelectAll)) {
                    finishParameterRename(param);
                }
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
                    renamingParamIndex = -1;
                } else if (!ImGui.isItemActive() && ImGui.isMouseClicked(0)) {
                    finishParameterRename(param);
                }
            } else if (previewPanelOpen && previewPanel != null) {
                ImGui.text(icon + " " + param.getName());
                ImGui.sameLine(ImGui.getContentRegionAvailX() - 85);
                previewPanel.renderParameterValue(param);
            } else {
                String label = icon + " " + param.getName();
                if (ImGui.selectable(label + "##param" + i, isSelected)) {
                    selectedParamIndex = i;
                    paramNameInput.set(param.getName());
                }

                if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                    renamingParamIndex = i;
                    renameInput.set(param.getName());
                }
            }

            if (!isRenaming && ImGui.isItemHovered()) {
                ImGui.setTooltip(param.getType().name().toLowerCase() + " - Double-click to rename, Right-click to delete");
            }

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

        if (newName.isEmpty() || newName.equals(oldName)) return;

        if (editingController.hasParameter(newName)) {
            if (shell != null) shell.showStatus("Parameter name already exists: " + newName);
            return;
        }

        captureUndoState();
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
        if (shell != null) shell.showStatus("Renamed parameter: " + oldName + " -> " + newName);
    }

    // ========================================================================
    // OPERATIONS
    // ========================================================================

    private void markModified() {
        if (shell != null) shell.markDirty();
    }

    private String generateUniqueStateName(String baseName) {
        if (editingController == null || !editingController.hasState(baseName)) return baseName;
        int counter = 1;
        String name;
        do {
            name = baseName + "_" + counter;
            counter++;
        } while (editingController.hasState(name));
        return name;
    }

    private String generateUniqueParamName(String baseName) {
        if (editingController == null || !editingController.hasParameter(baseName)) return baseName;
        int counter = 1;
        String name;
        do {
            name = baseName + "_" + counter;
            counter++;
        } while (editingController.hasParameter(name));
        return name;
    }

    // ========================================================================
    // UNDO/REDO (deferred snapshot pattern)
    // ========================================================================

    private void captureUndoState() {
        flushPendingUndo();
        if (editingController == null) return;
        pendingBeforeSnapshot = new ControllerState(editingController, selectedStateIndex, selectedParamIndex, selectedTransitionIndex);
    }

    private void flushPendingUndo() {
        if (pendingBeforeSnapshot == null || editingController == null) return;
        ControllerState beforeSnapshot = pendingBeforeSnapshot;
        ControllerState afterSnapshot = new ControllerState(editingController, selectedStateIndex, selectedParamIndex, selectedTransitionIndex);
        pendingBeforeSnapshot = null;

        UndoManager um = UndoManager.getInstance();
        um.push(new SnapshotCommand<>(editingController, beforeSnapshot, afterSnapshot,
                (target, snapshot) -> {
                    ControllerState state = (ControllerState) snapshot;
                    target.copyFrom(state.snapshot);
                    selectedStateIndex = Math.min(state.selectedState, target.getStateCount() - 1);
                    selectedParamIndex = Math.min(state.selectedParam, target.getParameterCount() - 1);
                    selectedTransitionIndex = Math.min(state.selectedTransition, target.getTransitionCount() - 1);
                    markModified();
                },
                "Edit animator"));
    }

    // ========================================================================
    // DIALOGS
    // ========================================================================

    private void openNewDialog() {
        if (shell != null) {
            shell.requestDirtyGuard(() -> {
                showNewDialog = true;
                newControllerName.set("new_controller");
            });
        } else {
            showNewDialog = true;
            newControllerName.set("new_controller");
        }
    }

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
        if (name == null || name.trim().isEmpty()) name = "new_controller";
        AnimatorController ctrl = new AnimatorController(name.trim());
        ctrl.addState(new AnimatorState("idle", ""));
        if (shell != null) shell.createAsset(name, ctrl);
    }

    private void renderDeleteConfirmDialog() {
        ImGui.openPopup("Delete Controller?");
        if (ImGui.beginPopupModal("Delete Controller?", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Are you sure you want to delete this controller?");
            if (shell != null && shell.getEditingPath() != null) {
                EditorColors.textColored(EditorColors.WARNING, extractFilename(shell.getEditingPath()));
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
        if (shell == null || shell.getEditingPath() == null) return;

        String path = shell.getEditingPath();
        String filename = extractFilename(path);
        try {
            java.nio.file.Path filePath = Paths.get(Assets.getAssetRoot(), path);
            Files.deleteIfExists(filePath);

            EditorEventBus.get().publish(new AssetChangedEvent(path, AssetChangedEvent.ChangeType.DELETED));

            shell.showStatus("Deleted: " + filename);
        } catch (IOException e) {
            System.err.println("[AnimatorEditorContent] Failed to delete: " + e.getMessage());
            shell.showStatus("Error deleting: " + e.getMessage());
            return;
        }
        shell.clearEditingAsset();
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
