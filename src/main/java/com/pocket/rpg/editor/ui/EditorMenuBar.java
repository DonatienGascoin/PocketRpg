package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.panels.ConfigurationPanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.ShortcutRegistry;
import com.pocket.rpg.editor.undo.UndoManager;
import imgui.ImGui;
import lombok.Setter;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main menu bar for the Scene Editor.
 */
public class EditorMenuBar {

    @Setter
    private ConfigurationPanel configurationPanel;

    private Runnable onNewScene;
    private Consumer<String> onOpenScene;
    private Runnable onSaveScene;
    private Consumer<String> onSaveSceneAs;
    private Runnable onExit;
    private Runnable onOpenSpriteEditor;
    private Runnable onToggleGizmos;
    private boolean gizmosEnabled = true;

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
            if (ImGui.menuItem("New Scene", getShortcutLabel(EditorShortcuts.FILE_NEW))) {
                handleNewScene();
            }

            if (ImGui.menuItem("Open Scene...", getShortcutLabel(EditorShortcuts.FILE_OPEN))) {
                handleOpenScene();
            }

            if (ImGui.beginMenu("Open Recent", recentFiles.length > 0)) {
                for (String file : recentFiles) {
                    String displayLabel = formatRecentSceneLabel(file);
                    if (ImGui.menuItem(displayLabel)) {
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
                if (configurationPanel != null) {
                    configurationPanel.toggle();
                }
            }

            ImGui.separator();

            boolean canSave = currentScene != null && currentScene.isDirty();
            if (ImGui.menuItem("Save", getShortcutLabel(EditorShortcuts.FILE_SAVE), false, canSave)) {
                handleSave();
            }

            if (ImGui.menuItem("Save As...", getShortcutLabel(EditorShortcuts.FILE_SAVE_AS))) {
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
            if (ImGui.menuItem(undoLabel, getShortcutLabel(EditorShortcuts.EDIT_UNDO), false, canUndo)) {
                undo.undo();
                if (currentScene != null) {
                    currentScene.markDirty();
                }
            }

            // Redo
            String redoLabel = "Redo" + (redoDesc != null ? " " + redoDesc : "");
            if (ImGui.menuItem(redoLabel, getShortcutLabel(EditorShortcuts.EDIT_REDO), false, canRedo)) {
                undo.redo();
                if (currentScene != null) {
                    currentScene.markDirty();
                }
            }

            ImGui.separator();

            // History info
            ImGui.textDisabled("History: " + undo.getUndoCount() + " undo, " + undo.getRedoCount() + " redo");

            ImGui.separator();

            if (ImGui.menuItem("Cut", getShortcutLabel(EditorShortcuts.EDIT_CUT), false, false)) {
                // TODO
            }
            if (ImGui.menuItem("Copy", getShortcutLabel(EditorShortcuts.EDIT_COPY), false, false)) {
                // TODO
            }
            if (ImGui.menuItem("Paste", getShortcutLabel(EditorShortcuts.EDIT_PASTE), false, false)) {
                // TODO
            }
            if (ImGui.menuItem("Delete", getShortcutLabel(EditorShortcuts.EDIT_DELETE), false, false)) {
                // TODO
            }

            ImGui.separator();

            if (ImGui.menuItem("Select All", getShortcutLabel(EditorShortcuts.EDIT_SELECT_ALL), false, false)) {
                // TODO
            }

            ImGui.separator();

            if (ImGui.menuItem("Sprite Editor...", "")) {
                if (onOpenSpriteEditor != null) {
                    onOpenSpriteEditor.run();
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

            if (ImGui.menuItem("Show Gizmos", "G", gizmosEnabled)) {
                gizmosEnabled = !gizmosEnabled;
                if (onToggleGizmos != null) {
                    onToggleGizmos.run();
                }
            }

            if (ImGui.beginMenu("Zoom")) {
                if (ImGui.menuItem("Zoom In", getShortcutLabel(EditorShortcuts.VIEW_ZOOM_IN))) {
                }
                if (ImGui.menuItem("Zoom Out", getShortcutLabel(EditorShortcuts.VIEW_ZOOM_OUT))) {
                }
                if (ImGui.menuItem("Reset Zoom", getShortcutLabel(EditorShortcuts.VIEW_ZOOM_RESET))) {
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
    // SHORTCUT LABEL HELPER
    // ========================================================================

    /**
     * Gets the display string for a shortcut action from the registry.
     * Returns empty string if the action has no binding.
     */
    private String getShortcutLabel(String actionId) {
        return ShortcutRegistry.getInstance().getBindingDisplay(actionId);
    }

    /**
     * Formats a recent scene path as "SceneName (relative/path.scene)".
     */
    private String formatRecentSceneLabel(String path) {
        if (path == null || path.isEmpty()) return path;

        // Normalize separators for display
        String normalizedPath = path.replace('\\', '/');

        // Extract scene name (filename without .scene extension)
        int lastSlash = normalizedPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
        String sceneName = fileName.endsWith(".scene")
                ? fileName.substring(0, fileName.length() - 6)
                : fileName;

        // Compute relative path from gameData/scenes/ if possible
        String relativePath = normalizedPath;
        String scenesPrefix = "gameData/scenes/";
        int scenesIndex = normalizedPath.indexOf(scenesPrefix);
        if (scenesIndex >= 0) {
            relativePath = normalizedPath.substring(scenesIndex + scenesPrefix.length());
        }

        return sceneName + "  (" + relativePath + ")";
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
    // PUBLIC TRIGGER METHODS (for shortcut system)
    // ========================================================================

    /**
     * Triggers New Scene action from shortcut system.
     */
    public void triggerNewScene() {
        handleNewScene();
    }

    /**
     * Triggers Open Scene action from shortcut system.
     */
    public void triggerOpenScene() {
        handleOpenScene();
    }

    /**
     * Triggers Save Scene action from shortcut system.
     */
    public void triggerSaveScene() {
        handleSave();
    }

    /**
     * Triggers Save Scene As action from shortcut system.
     */
    public void triggerSaveSceneAs() {
        handleSaveAs();
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

    public void setOnOpenSpriteEditor(Runnable callback) {
        this.onOpenSpriteEditor = callback;
    }

    public void setOnToggleGizmos(Runnable callback) {
        this.onToggleGizmos = callback;
    }

    public void setGizmosEnabled(boolean enabled) {
        this.gizmosEnabled = enabled;
    }

    public boolean isGizmosEnabled() {
        return gizmosEnabled;
    }

    /**
     * Toggles gizmos visibility from shortcut system.
     */
    public void triggerToggleGizmos() {
        gizmosEnabled = !gizmosEnabled;
        if (onToggleGizmos != null) {
            onToggleGizmos.run();
        }
    }
}
