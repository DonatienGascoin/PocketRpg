package com.pocket.rpg.editor.shortcut;

/**
 * Interface for binding shortcut handlers.
 * Implemented by the editor controller or a dedicated handler class.
 */
public interface EditorShortcutHandlers {

    // ========================================================================
    // FILE
    // ========================================================================

    void onNewScene();
    void onOpenScene();
    void onSaveScene();
    void onSaveSceneAs();
    void onOpenConfiguration();
    void onReloadScene();

    // ========================================================================
    // EDIT
    // ========================================================================

    void onUndo();
    void onRedo();
    void onCut();
    void onCopy();
    void onPaste();
    void onDelete();
    void onSelectAll();
    void onDuplicate();

    // ========================================================================
    // VIEW
    // ========================================================================

    void onZoomIn();
    void onZoomOut();
    void onZoomReset();
    void onToggleGrid();
    void onFocusSelected();

    // ========================================================================
    // PANEL TOGGLES
    // ========================================================================

    void onPanelTilesetToggle();
    void onPanelCollisionToggle();

    // ========================================================================
    // PAINT TOOLS (context-dependent: tilemap or collision)
    // ========================================================================

    void onToolBrush();
    void onToolEraser();
    void onToolFill();
    void onToolRectangle();
    void onToolPicker();

    // ========================================================================
    // ENTITY TOOLS
    // ========================================================================

    void onToolSelection();
    void onToolEntityPlacer();
    void onEntityDelete();
    void onEntityCancel();
    void onEntityToggleEnabled();

    // ========================================================================
    // TRANSFORM TOOLS
    // ========================================================================

    void onToolMove();
    void onToolRotate();
    void onToolScale();

    // ========================================================================
    // BRUSH SIZE
    // ========================================================================

    void onBrushSizeIncrease();
    void onBrushSizeDecrease();

    // ========================================================================
    // Z-LEVEL
    // ========================================================================

    void onZLevelIncrease();
    void onZLevelDecrease();

    // ========================================================================
    // PLAY MODE
    // ========================================================================

    void onPlayToggle();
    void onPlayStop();

    // NOTE: Configuration and Animator shortcuts are handled directly by their panels
}
