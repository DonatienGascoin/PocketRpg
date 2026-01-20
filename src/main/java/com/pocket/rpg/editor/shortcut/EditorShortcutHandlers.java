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

    // ========================================================================
    // MODES
    // ========================================================================

    void onModeTilemap();
    void onModeCollision();
    void onModeEntity();

    // ========================================================================
    // TILEMAP TOOLS
    // ========================================================================

    void onToolTileBrush();
    void onToolTileEraser();
    void onToolTileFill();
    void onToolTileRectangle();
    void onToolTilePicker();

    // ========================================================================
    // COLLISION TOOLS
    // ========================================================================

    void onToolCollisionBrush();
    void onToolCollisionEraser();
    void onToolCollisionFill();
    void onToolCollisionRectangle();
    void onToolCollisionPicker();

    // ========================================================================
    // ENTITY TOOLS
    // ========================================================================

    void onToolSelection();
    void onToolEntityPlacer();
    void onEntityDelete();
    void onEntityCancel();

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
}
