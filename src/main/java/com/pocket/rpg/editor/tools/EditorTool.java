package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;

/**
 * Interface for editor tools (brush, eraser, fill, etc.).
 * 
 * Tools receive input events in tile coordinates and can render
 * overlays (previews, guides) on the viewport.
 */
public interface EditorTool {
    
    /**
     * Gets the tool's display name.
     */
    String getName();
    
    /**
     * Gets the keyboard shortcut key (e.g., "B" for brush).
     * Return null if no shortcut.
     */
    default String getShortcutKey() {
        return null;
    }
    
    /**
     * Called when this tool becomes active.
     */
    default void onActivate() {}
    
    /**
     * Called when this tool is deactivated.
     */
    default void onDeactivate() {}
    
    /**
     * Called when mouse button is pressed.
     * 
     * @param tileX Tile X coordinate under cursor
     * @param tileY Tile Y coordinate under cursor
     * @param button Mouse button (0 = left, 1 = right, 2 = middle)
     */
    void onMouseDown(int tileX, int tileY, int button);
    
    /**
     * Called when mouse is dragged while button is held.
     * 
     * @param tileX Current tile X coordinate
     * @param tileY Current tile Y coordinate
     * @param button Mouse button being held
     */
    void onMouseDrag(int tileX, int tileY, int button);
    
    /**
     * Called when mouse button is released.
     * 
     * @param tileX Tile X coordinate under cursor
     * @param tileY Tile Y coordinate under cursor
     * @param button Mouse button released
     */
    void onMouseUp(int tileX, int tileY, int button);
    
    /**
     * Called when mouse moves (without button pressed).
     * 
     * @param tileX Tile X coordinate under cursor
     * @param tileY Tile Y coordinate under cursor
     */
    default void onMouseMove(int tileX, int tileY) {}
    
    /**
     * Renders tool overlay (preview, guides, etc.).
     * Called after scene rendering, before ImGui.
     * 
     * @param camera Editor camera for coordinate conversion
     * @param hoveredTileX Currently hovered tile X (-1 if none)
     * @param hoveredTileY Currently hovered tile Y (-1 if none)
     */
    default void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {}
    
    /**
     * Called each frame for tool-specific updates.
     * 
     * @param deltaTime Time since last frame
     */
    default void update(float deltaTime) {}
}
