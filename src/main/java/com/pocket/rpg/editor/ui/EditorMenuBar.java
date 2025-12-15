package com.pocket.rpg.editor.ui;

import com.pocket.rpg.editor.core.FileDialogs;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.shortcuts.commands.*;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import lombok.Setter;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main menu bar for the Scene Editor.
 * Handles File menu operations (New, Open, Save, Save As, Exit).
 */
public class EditorMenuBar {

    // Callbacks for menu actions
    @Setter
    private Consumer<String> onOpenScene;

    // Reference to current scene (for dirty checking, file path)
    @Setter
    private EditorScene currentScene;

    // Confirmation dialog state
    @Setter
    private boolean showUnsavedChangesDialog = false;
    @Setter
    private Runnable pendingAction = null;

    // Recent files (placeholder for future implementation)
    private String[] recentFiles = new String[0];

    // Commands
    @Setter
    private SaveCommand saveCommand;
    @Setter
    private SaveAsCommand saveAsCommand;
    @Setter
    private OpenSceneCommand openSceneCommand;
    @Setter
    private NewSceneCommand newSceneCommand;
    @Setter
    private ExitCommand exitCommand;

    /**
     * Renders the main menu bar.
     * Returns true if menu is active (for input blocking).
     */
    public boolean render() {
        boolean menuActive = false;

        if (ImGui.beginMainMenuBar()) {
            menuActive = true;

            renderFileMenu();
            renderEditMenu();
            renderViewMenu();
            renderHelpMenu();

            // Right-aligned scene info
            renderSceneInfo();

            ImGui.endMainMenuBar();
        }

        // Render dialogs
        renderUnsavedChangesDialog();

        return menuActive;
    }

    private void renderFileMenu() {
        if (ImGui.beginMenu("File")) {
            // New Scene (Ctrl+N)
            if (ImGui.menuItem("New Scene", "Ctrl+N")) {
                newSceneCommand.execute();
            }

            // Open Scene (Ctrl+O)
            if (ImGui.menuItem("Open Scene...", "Ctrl+O")) {
                openSceneCommand.execute();
            }

            // Recent Files submenu
            if (ImGui.beginMenu("Open Recent", recentFiles.length > 0)) {
                for (String file : recentFiles) {
                    if (ImGui.menuItem(file)) { // TODO: Replace by command !
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

            // Save (Ctrl+S)
            boolean canSave = currentScene != null && currentScene.isDirty();
            if (ImGui.menuItem("Save", "Ctrl+S", false, canSave)) {
                saveCommand.execute();
            }

            // Save As (Ctrl+Shift+S)
            if (ImGui.menuItem("Save As...", "Ctrl+Shift+S")) {
                saveAsCommand.execute();
            }

            ImGui.separator();

            // Exit
            // TODO: Would it better to have the label name and shortcut name stored with the command ? So the menu item can't be wrong
            if (ImGui.menuItem("Exit", "Alt+F4")) {
                exitCommand.execute();
            }

            ImGui.endMenu();
        }
    }

    private void renderEditMenu() {
        if (ImGui.beginMenu("Edit")) {
            // Placeholder for future edit operations
            if (ImGui.menuItem("Undo", "Ctrl+Z", false, false)) {
                // TODO: Implement undo
            }
            if (ImGui.menuItem("Redo", "Ctrl+Y", false, false)) {
                // TODO: Implement redo
            }

            ImGui.separator();

            if (ImGui.menuItem("Cut", "Ctrl+X", false, false)) {
                // TODO: Implement cut
            }
            if (ImGui.menuItem("Copy", "Ctrl+C", false, false)) {
                // TODO: Implement copy
            }
            if (ImGui.menuItem("Paste", "Ctrl+V", false, false)) {
                // TODO: Implement paste
            }
            if (ImGui.menuItem("Delete", "Delete", false, false)) {
                // TODO: Implement delete
            }

            ImGui.separator();

            if (ImGui.menuItem("Select All", "Ctrl+A", false, false)) {
                // TODO: Implement select all
            }

            ImGui.endMenu();
        }
    }

    private void renderViewMenu() {
        if (ImGui.beginMenu("View")) {
            // Toggle panels (placeholder)
            if (ImGui.menuItem("Scene Hierarchy", "", true)) {
                // TODO: Toggle hierarchy panel
            }
            if (ImGui.menuItem("Inspector", "", true)) {
                // TODO: Toggle inspector panel
            }
            if (ImGui.menuItem("Tileset Palette", "", true)) {
                // TODO: Toggle tileset panel
            }
            if (ImGui.menuItem("Layers", "", true)) {
                // TODO: Toggle layers panel
            }

            ImGui.separator();

            if (ImGui.menuItem("Reset Layout")) {
                // TODO: Reset panel layout
            }

            ImGui.separator();

            // Grid options
            if (ImGui.beginMenu("Grid")) {
                if (ImGui.menuItem("Show Grid", "", true)) {
                    // TODO: Toggle grid
                }
                if (ImGui.menuItem("Snap to Grid", "", true)) {
                    // TODO: Toggle snap
                }
                ImGui.endMenu();
            }

            // Zoom options
            if (ImGui.beginMenu("Zoom")) {
                if (ImGui.menuItem("Zoom In", "+")) {
                    // TODO: Zoom in
                }
                if (ImGui.menuItem("Zoom Out", "-")) {
                    // TODO: Zoom out
                }
                if (ImGui.menuItem("Reset Zoom", "0")) {
                    // TODO: Reset zoom
                }
                if (ImGui.menuItem("Fit Scene")) {
                    // TODO: Fit scene in view
                }
                ImGui.endMenu();
            }

            ImGui.endMenu();
        }
    }

    private void renderHelpMenu() {
        if (ImGui.beginMenu("Help")) {
            if (ImGui.menuItem("Keyboard Shortcuts")) {
                // TODO: Show shortcuts dialog
            }
            if (ImGui.menuItem("Documentation")) {
                // TODO: Open docs
            }

            ImGui.separator();

            if (ImGui.menuItem("About")) {
                // TODO: Show about dialog
            }

            ImGui.endMenu();
        }
    }

    private void renderSceneInfo() {
        if (currentScene == null) return;

        // Calculate position for right alignment
        String sceneInfo = currentScene.getDisplayName();
        float textWidth = ImGui.calcTextSize(sceneInfo).x;
        float availableWidth = ImGui.getWindowWidth();
        float padding = 20.0f;

        ImGui.setCursorPosX(availableWidth - textWidth - padding);
        ImGui.text(sceneInfo);
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
                saveCommand.execute();
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
    // KEYBOARD SHORTCUTS
    // ========================================================================

    /**
     * Processes keyboard shortcuts using ImGui's input system.
     * Call this each frame.
     */
    // TODO: REMOVE SHORTCUTS FROM MENU BAR
    public void processShortcuts() {
        // Check modifier states using ImGuiKey (1.90.0+)
        boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
        boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

        // Ctrl+N - New Scene
        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.N)) {
//            handleNewScene();
        }
        // Ctrl+O - Open Scene
        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.O)) {
//            handleOpenScene();
        }
        // Ctrl+S - Save
        if (ctrl && !shift && ImGui.isKeyPressed(ImGuiKey.S)) {
//            handleSave();
        }
        // Ctrl+Shift+S - Save As
        if (ctrl && shift && ImGui.isKeyPressed(ImGuiKey.S)) {
//            handleSaveAs();
        }
    }

    // ========================================================================
    // SETTERS
    // ========================================================================

    public void setRecentFiles(String[] files) {
        this.recentFiles = files != null ? files : new String[0];
    }
}
