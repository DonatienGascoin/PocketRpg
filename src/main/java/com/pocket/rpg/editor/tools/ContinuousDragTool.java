package com.pocket.rpg.editor.tools;

/**
 * Marker interface for tools that need continuous drag updates.
 * <p>
 * By default, ToolManager only calls onMouseDrag when the hovered tile changes.
 * Tools implementing this interface will receive onMouseDrag on every frame
 * while the mouse button is held, regardless of tile changes.
 * <p>
 * Use this for tools that operate in screen/world space rather than tile space
 * (e.g., transform tools like Move, Rotate, Scale).
 */
public interface ContinuousDragTool {
    // Marker interface - no methods required
}
