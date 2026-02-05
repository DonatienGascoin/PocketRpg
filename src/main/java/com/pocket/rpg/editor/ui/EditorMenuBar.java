package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.panels.ConfigurationPanel;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.ShortcutRegistry;
import com.pocket.rpg.editor.tools.SpritesheetMigrationTool;
import com.pocket.rpg.editor.undo.UndoManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
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
    private Runnable onReloadScene;
    private Runnable onOpenSpriteEditor;
    private Runnable onToggleGizmos;
    private boolean gizmosEnabled = true;

    private EditorScene currentScene;

    private boolean showUnsavedChangesDialog = false;
    private Runnable pendingAction = null;

    private String[] recentFiles = new String[0];

    // Migration tool state
    private boolean showMigrationDialog = false;
    private SpritesheetMigrationTool.MigrationReport migrationReport = null;
    private boolean migrationInProgress = false;

    public boolean render() {
        boolean menuActive = false;

        if (ImGui.beginMainMenuBar()) {
            menuActive = true;

            renderFileMenu();
            renderEditMenu();
            renderViewMenu();
            renderToolsMenu();
            renderHelpMenu();

            renderSceneInfo();

            ImGui.endMainMenuBar();
        }

        renderUnsavedChangesDialog();
        renderMigrationDialog();

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

            if (ImGui.menuItem("Reload Scene", getShortcutLabel(EditorShortcuts.FILE_RELOAD), false, currentScene != null)) {
                if (onReloadScene != null) onReloadScene.run();
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

    public void renderToolsMenu() {
        if (ImGui.beginMenu("Tools")) {
            ImGui.textDisabled("Asset Migration");

            if (ImGui.menuItem("Migrate Spritesheets (Dry Run)...")) {
                runMigration(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Preview migration without making changes");
            }

            if (ImGui.menuItem("Migrate Spritesheets...")) {
                runMigration(false);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Convert .spritesheet files to new .meta format");
            }

            ImGui.endMenu();
        }
    }

    private void runMigration(boolean dryRun) {
        migrationInProgress = true;
        migrationReport = null;

        // Run migration in background to not block UI
        new Thread(() -> {
            try {
                SpritesheetMigrationTool tool = new SpritesheetMigrationTool();
                if (dryRun) {
                    migrationReport = tool.dryRun();
                } else {
                    migrationReport = tool.migrate(true); // Always create backup
                }
            } catch (Exception e) {
                migrationReport = new SpritesheetMigrationTool.MigrationReport();
                migrationReport.getErrors().add("Migration failed: " + e.getMessage());
                e.printStackTrace();
            } finally {
                migrationInProgress = false;
                showMigrationDialog = true;
            }
        }).start();
    }

    /**
     * Renders any dialogs managed by this menu bar (called from EditorUIController).
     */
    public void renderDialogs() {
        renderMigrationDialog();
    }

    private void renderMigrationDialog() {
        if (migrationInProgress) {
            ImGui.openPopup("Migration Progress");
        }

        if (ImGui.beginPopupModal("Migration Progress", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Migration in progress...");
            ImGui.text("Please wait.");
            ImGui.endPopup();
        }

        if (showMigrationDialog && migrationReport != null) {
            ImGui.openPopup("Migration Report");
            showMigrationDialog = false;
        }

        ImGui.setNextWindowSize(600, 500);
        if (ImGui.beginPopupModal("Migration Report", ImGuiWindowFlags.None)) {
            if (migrationReport != null) {
                // Header
                if (migrationReport.isDryRun()) {
                    ImGui.textColored(0.5f, 0.8f, 1.0f, 1.0f, "DRY RUN - No changes were made");
                } else {
                    ImGui.textColored(0.5f, 1.0f, 0.5f, 1.0f, "Migration Complete");
                }

                ImGui.separator();

                // Summary
                ImGui.text("Spritesheets found: " + migrationReport.getSpritesheetsFound().size());
                ImGui.text("Meta files created: " + migrationReport.getMetaFilesCreated().size());
                ImGui.text("Files updated: " + migrationReport.getFilesUpdated().size());

                if (migrationReport.getBackupPath() != null) {
                    ImGui.text("Backup: " + migrationReport.getBackupPath());
                }

                ImGui.separator();

                // Scrollable details
                float contentHeight = ImGui.getContentRegionAvailY() - 40;
                if (ImGui.beginChild("MigrationDetails", 0, contentHeight, true)) {
                    // Spritesheets
                    if (!migrationReport.getSpritesheetsFound().isEmpty()) {
                        if (ImGui.treeNode("Spritesheets (" + migrationReport.getSpritesheetsFound().size() + ")")) {
                            for (String ss : migrationReport.getSpritesheetsFound()) {
                                ImGui.bulletText(ss);
                            }
                            ImGui.treePop();
                        }
                    }

                    // Meta files
                    if (!migrationReport.getMetaFilesCreated().isEmpty()) {
                        if (ImGui.treeNode("Meta Files Created (" + migrationReport.getMetaFilesCreated().size() + ")")) {
                            for (String meta : migrationReport.getMetaFilesCreated()) {
                                ImGui.bulletText(meta);
                            }
                            ImGui.treePop();
                        }
                    }

                    // Files updated
                    if (!migrationReport.getFilesUpdated().isEmpty()) {
                        if (ImGui.treeNode("Files Updated (" + migrationReport.getFilesUpdated().size() + ")")) {
                            for (String file : migrationReport.getFilesUpdated()) {
                                ImGui.bulletText(file);
                            }
                            ImGui.treePop();
                        }
                    }

                    // Warnings
                    if (!migrationReport.getWarnings().isEmpty()) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.2f, 1.0f);
                        if (ImGui.treeNode("Warnings (" + migrationReport.getWarnings().size() + ")")) {
                            for (String warning : migrationReport.getWarnings()) {
                                ImGui.bulletText(warning);
                            }
                            ImGui.treePop();
                        }
                        ImGui.popStyleColor();
                    }

                    // Errors
                    if (!migrationReport.getErrors().isEmpty()) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f);
                        if (ImGui.treeNode("Errors (" + migrationReport.getErrors().size() + ")")) {
                            for (String error : migrationReport.getErrors()) {
                                ImGui.bulletText(error);
                            }
                            ImGui.treePop();
                        }
                        ImGui.popStyleColor();
                    }
                }
                ImGui.endChild();
            }

            ImGui.separator();

            float buttonWidth = 100;
            ImGui.setCursorPosX((ImGui.getContentRegionAvailX() - buttonWidth) / 2);
            if (ImGui.button("Close", buttonWidth, 0)) {
                migrationReport = null;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
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

    public void setOnReloadScene(Runnable callback) {
        this.onReloadScene = callback;
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
