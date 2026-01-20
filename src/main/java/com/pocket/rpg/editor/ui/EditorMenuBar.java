package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.panels.ConfigPanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import lombok.Setter;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main menu bar for the Scene Editor.
 */
public class EditorMenuBar {

    @Setter
    private ConfigPanel configPanel;

    private Runnable onNewScene;
    private Consumer<String> onOpenScene;
    private Runnable onSaveScene;
    private Consumer<String> onSaveSceneAs;
    private Runnable onExit;
    private Runnable onOpenPivotEditor;

    private EditorScene currentScene;

    private boolean showUnsavedChangesDialog = false;
    private Runnable pendingAction = null;

    private String[] recentFiles = new String[0];

    public boolean render() {
        boolean menuActive = false;

        if (ImGui.beginMainMenuBar()) {
            menuActive = true;

            renderFileMenu();
            renderEditMenu();
            renderViewMenu();
            renderHelpMenu();

            renderSceneInfo();

            ImGui.endMainMenuBar();
        }

        renderUnsavedChangesDialog();

        return menuActive;
    }

    public void renderFileMenu() {
        renderUnsavedChangesDialog();
        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("New Scene", "Ctrl+N")) {
                handleNewScene();
            }

            if (ImGui.menuItem("Open Scene...", "Ctrl+O")) {
                handleOpenScene();
            }

            if (ImGui.beginMenu("Open Recent", recentFiles.length > 0)) {
                for (String file : recentFiles) {
                    if (ImGui.menuItem(file)) {
                        checkUnsavedChanges(() -> {
                            if (onOpenScene != null) {
                                onOpenScene.accept(file);
                            }
                        });
                    }
                }
                ImGui.separator();
                if (ImGui.menuItem("Clear Recent")) {
                    recentFiles = new String[0];
                }
                ImGui.endMenu();
            }

            ImGui.separator();

            if (ImGui.menuItem("Configuration...", "")) {
                if (configPanel != null) {
                    configPanel.openModal();
                }
            }

            ImGui.separator();

            boolean canSave = currentScene != null && currentScene.isDirty();
            if (ImGui.menuItem("Save", "Ctrl+S", false, canSave)) {
                handleSave();
            }

            if (ImGui.menuItem("Save As...", "Ctrl+Shift+S")) {
                handleSaveAs();
            }

            ImGui.separator();

            if (ImGui.menuItem("Exit", "Alt+F4")) {
                handleExit();
            }

            ImGui.endMenu();
        }
    }

    public void renderEditMenu() {
        if (ImGui.beginMenu("Edit")) {
            UndoManager undo = UndoManager.getInstance();

            String undoDesc = undo.getUndoDescription();
            String redoDesc = undo.getRedoDescription();

            boolean canUndo = undo.canUndo();
            boolean canRedo = undo.canRedo();

            // Undo
            String undoLabel = "Undo" + (undoDesc != null ? " " + undoDesc : "");
            if (ImGui.menuItem(undoLabel, "Ctrl+Z", false, canUndo)) {
                undo.undo();
                if (currentScene != null) {
                    currentScene.markDirty();
                }
            }

            // Redo
            String redoLabel = "Redo" + (redoDesc != null ? " " + redoDesc : "");
            if (ImGui.menuItem(redoLabel, "Ctrl+Shift+Z", false, canRedo)) {
                undo.redo();
                if (currentScene != null) {
                    currentScene.markDirty();
                }
            }

            ImGui.separator();

            // History info
            ImGui.textDisabled("History: " + undo.getUndoCount() + " undo, " + undo.getRedoCount() + " redo");

            ImGui.separator();

            if (ImGui.menuItem("Cut", "Ctrl+X", false, false)) {
                // TODO
            }
            if (ImGui.menuItem("Copy", "Ctrl+C", false, false)) {
                // TODO
            }
            if (ImGui.menuItem("Paste", "Ctrl+V", false, false)) {
                // TODO
            }
            if (ImGui.menuItem("Delete", "Delete", false, false)) {
                // TODO
            }

            ImGui.separator();

            if (ImGui.menuItem("Select All", "Ctrl+A", false, false)) {
                // TODO
            }

            ImGui.separator();

            if (ImGui.menuItem("Pivot Editor...", "")) {
                if (onOpenPivotEditor != null) {
                    onOpenPivotEditor.run();
                }
            }

            ImGui.endMenu();
        }
    }

    private void renderViewMenu() {
        if (ImGui.beginMenu("View")) {
            if (ImGui.menuItem("Scene Hierarchy", "", true)) {
            }
            if (ImGui.menuItem("Inspector", "", true)) {
            }
            if (ImGui.menuItem("Tileset Palette", "", true)) {
            }
            if (ImGui.menuItem("Layers", "", true)) {
            }

            ImGui.separator();

            if (ImGui.menuItem("Reset Layout")) {
            }

            ImGui.separator();

            if (ImGui.beginMenu("Grid")) {
                if (ImGui.menuItem("Show Grid", "", true)) {
                }
                if (ImGui.menuItem("Snap to Grid", "", true)) {
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Zoom")) {
                if (ImGui.menuItem("Zoom In", "+")) {
                }
                if (ImGui.menuItem("Zoom Out", "-")) {
                }
                if (ImGui.menuItem("Reset Zoom", "0")) {
                }
                if (ImGui.menuItem("Fit Scene")) {
                }
                ImGui.endMenu();
            }

            ImGui.endMenu();
        }
    }

    private void renderHelpMenu() {
        if (ImGui.beginMenu("Help")) {
            if (ImGui.menuItem("Keyboard Shortcuts")) {
            }
            if (ImGui.menuItem("Documentation")) {
            }

            ImGui.separator();

            if (ImGui.menuItem("About")) {
            }

            ImGui.endMenu();
        }
    }

    private void renderSceneInfo() {
        if (currentScene == null) return;

        String sceneInfo = currentScene.getDisplayName();
        float textWidth = ImGui.calcTextSize(sceneInfo).x;
        float availableWidth = ImGui.getWindowWidth();
        float padding = 20.0f;

        ImGui.setCursorPosX(availableWidth - textWidth - padding);
        ImGui.text(sceneInfo);
    }

    // ========================================================================
    // ACTION HANDLERS
    // ========================================================================

    private void handleNewScene() {
        checkUnsavedChanges(() -> {
            if (onNewScene != null) {
                onNewScene.run();
            }
        });
    }

    private void handleOpenScene() {
        checkUnsavedChanges(() -> {
            Optional<String> path = FileDialogs.openSceneFile(FileDialogs.getScenesDirectory());
            path.ifPresent(p -> {
                if (onOpenScene != null) {
                    onOpenScene.accept(p);
                }
            });
        });
    }

    private void handleSave() {
        if (currentScene == null) return;

        if (currentScene.getFilePath() != null) {
            if (onSaveScene != null) {
                onSaveScene.run();
            }
        } else {
            handleSaveAs();
        }
    }

    private void handleSaveAs() {
        String defaultName = currentScene != null ? currentScene.getName() : "scene";
        Optional<String> path = FileDialogs.saveSceneFile(
                FileDialogs.getScenesDirectory(),
                defaultName + ".scene"
        );

        path.ifPresent(p -> {
            if (onSaveSceneAs != null) {
                onSaveSceneAs.accept(p);
            }
        });
    }

    private void handleExit() {
        checkUnsavedChanges(() -> {
            if (onExit != null) {
                onExit.run();
            }
        });
    }

    // ========================================================================
    // UNSAVED CHANGES DIALOG
    // ========================================================================

    private void checkUnsavedChanges(Runnable action) {
        if (currentScene != null && currentScene.isDirty()) {
            showUnsavedChangesDialog = true;
            pendingAction = action;
        } else {
            action.run();
        }
    }

    private void renderUnsavedChangesDialog() {
        if (!showUnsavedChangesDialog) return;

        ImGui.openPopup("Unsaved Changes");

        if (ImGui.beginPopupModal("Unsaved Changes")) {
            ImGui.text("You have unsaved changes.");
            ImGui.text("Do you want to save before continuing?");

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            if (ImGui.button("Save", 100, 0)) {
                handleSave();
                showUnsavedChangesDialog = false;
                if (pendingAction != null) {
                    pendingAction.run();
                    pendingAction = null;
                }
                ImGui.closeCurrentPopup();
            }

            ImGui.sameLine();

            if (ImGui.button("Don't Save", 100, 0)) {
                showUnsavedChangesDialog = false;
                if (pendingAction != null) {
                    pendingAction.run();
                    pendingAction = null;
                }
                ImGui.closeCurrentPopup();
            }

            ImGui.sameLine();

            if (ImGui.button("Cancel", 100, 0)) {
                showUnsavedChangesDialog = false;
                pendingAction = null;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    // ========================================================================
    // KEYBOARD SHORTCUTS - Call at START of frame, before any ImGui windows
    // ========================================================================

    /**
     * Processes global keyboard shortcuts.
     * MUST be called at the start of the frame, before ImGui windows capture input.
     */
    public void processShortcuts() {
        // Skip if typing in a text field
        if (ImGui.getIO().getWantTextInput()) {
            return;
        }

        // Skip if a popup/modal is open (except for undo/redo which should still work)
        boolean popupOpen = ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopup);

        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

        // Undo/Redo work even with popups (unless typing)
        // Support both QWERTY (Z) and AZERTY (W)
        if (ctrl && (ImGui.isKeyPressed(ImGuiKey.Z, false) || ImGui.isKeyPressed(ImGuiKey.W, false))) {
            if (shift) {
                // Ctrl+Shift+Z/W = Redo
                if (UndoManager.getInstance().redo()) {
                    if (currentScene != null) {
                        currentScene.markDirty();
                    }
                }
            } else {
                // Ctrl+Z/W = Undo
                if (UndoManager.getInstance().undo()) {
                    if (currentScene != null) {
                        currentScene.markDirty();
                    }
                }
            }
            return; // Consume the input
        }

        // Ctrl+Y for Redo (alternative)
        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.Y, false)) {
            if (UndoManager.getInstance().redo()) {
                if (currentScene != null) {
                    currentScene.markDirty();
                }
            }
            return;
        }

        // File shortcuts - skip if popup is open
        if (popupOpen) {
            return;
        }

        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.N)) {
            handleNewScene();
        }
        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.O)) {
            handleOpenScene();
        }
        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.S)) {
            handleSave();
        }
        if (ctrl && shift && ImGui.isKeyPressed(ImGuiKey.S)) {
            handleSaveAs();
        }
    }

    // ========================================================================
    // SETTERS
    // ========================================================================

    public void setCurrentScene(EditorScene scene) {
        this.currentScene = scene;
    }

    public void setOnNewScene(Runnable callback) {
        this.onNewScene = callback;
    }

    public void setOnOpenScene(Consumer<String> callback) {
        this.onOpenScene = callback;
    }

    public void setOnSaveScene(Runnable callback) {
        this.onSaveScene = callback;
    }

    public void setOnSaveSceneAs(Consumer<String> callback) {
        this.onSaveSceneAs = callback;
    }

    public void setOnExit(Runnable callback) {
        this.onExit = callback;
    }

    public void setRecentFiles(String[] files) {
        this.recentFiles = files != null ? files : new String[0];
    }

    public void setOnOpenPivotEditor(Runnable callback) {
        this.onOpenPivotEditor = callback;
    }
}
