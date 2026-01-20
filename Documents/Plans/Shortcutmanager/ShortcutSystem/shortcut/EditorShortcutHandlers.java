package com.pocket.rpg.editor.shortcut;

/**
 * Interface for binding shortcut handlers.
 * Implemented by the editor controller or a dedicated handler class.
 */
public interface EditorShortcutHandlers {

    // File
    void onNewScene();
    void onOpenScene();
    void onSaveScene();
    void onSaveSceneAs();
    void onOpenConfiguration();

    // Edit
    void onUndo();
    void onRedo();
    void onCut();
    void onCopy();
    void onPaste();
    void onDelete();
    void onSelectAll();
    void onDuplicate();

    // View
    void onZoomIn();
    void onZoomOut();
    void onZoomReset();
    void onFitScene();
    void onToggleGrid();

    // Tools
    void onToolSelect();
    void onToolMove();
    void onToolBrush();
    void onToolEraser();
    void onToolFill();
    void onToolPicker();

    // Modes
    void onModeTilemap();
    void onModeEntity();
    void onModeCollision();

    // Play
    void onPlayToggle();
    void onPlayStop();

    // Navigation
    void onPanUp();
    void onPanDown();
    void onPanLeft();
    void onPanRight();

    // Popup
    void onPopupConfirm();
    void onPopupCancel();
}
