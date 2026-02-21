package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.Deque;

/**
 * Shell API exposed to {@link AssetEditorContent} implementations.
 * Provides access to the unified panel's shared infrastructure
 * without exposing the full AssetEditorPanel.
 */
public interface AssetEditorShell {

    /** Marks the current asset as dirty (unsaved changes). */
    void markDirty();

    /** Returns whether the current asset has unsaved changes. */
    boolean isDirty();

    /**
     * Requests resolution of the dirty state before running an action.
     * If not dirty, runs the action immediately. If dirty, shows the unsaved
     * changes popup and runs the action after Save or Discard (not Cancel).
     */
    void requestDirtyGuard(Runnable afterResolved);

    /** Returns the path of the currently editing asset, or null. */
    String getEditingPath();

    /** Returns the panel's undo stack (for direct manipulation if needed). */
    Deque<EditorCommand> getUndoStack();

    /** Returns the panel's redo stack (for direct manipulation if needed). */
    Deque<EditorCommand> getRedoStack();

    /** Shows a status message in the editor status bar. */
    void showStatus(String message);

    /** Requests a refresh of the hamburger sidebar asset list. */
    void requestSidebarRefresh();

    /** Navigates the shell to a different asset by path. */
    void selectAssetByPath(String path);

    /** Clears the current asset from the editor (e.g., after deletion). */
    void clearEditingAsset();

    /**
     * Creates a new asset file and navigates to it.
     * Handles: name sanitization, collision resolution, directory creation,
     * disk write (via Assets.persist), event publishing, and asset selection.
     *
     * @param name         Raw name from user input
     * @param defaultAsset Fully-constructed default asset object
     * @return The relative path of the created file, or null on failure
     */
    String createAsset(String name, Object defaultAsset);

    /** Returns the editor selection manager for panel-specific selection operations. */
    EditorSelectionManager getSelectionManager();
}
