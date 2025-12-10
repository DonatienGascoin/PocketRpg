package com.pocket.rpg.editor.tools;

import com.pocket.rpg.editor.camera.EditorCamera;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages editor tools and routes input to the active tool.
 * 
 * Responsibilities:
 * - Maintains list of registered tools
 * - Tracks active tool
 * - Routes mouse/keyboard events to active tool
 * - Handles tool switching via shortcuts
 */
public class ToolManager {
    
    private final List<EditorTool> tools = new ArrayList<>();
    private final Map<String, EditorTool> toolsByShortcut = new HashMap<>();
    
    @Getter
    private EditorTool activeTool;
    
    // Mouse state
    private boolean isMouseDown = false;
    private int activeButton = -1;
    private int lastTileX = Integer.MIN_VALUE;
    private int lastTileY = Integer.MIN_VALUE;
    
    /**
     * Registers a tool.
     */
    public void registerTool(EditorTool tool) {
        tools.add(tool);
        
        String shortcut = tool.getShortcutKey();
        if (shortcut != null && !shortcut.isEmpty()) {
            toolsByShortcut.put(shortcut.toUpperCase(), tool);
        }
        
        // Auto-select first tool
        if (activeTool == null) {
            setActiveTool(tool);
        }
    }
    
    /**
     * Sets the active tool.
     */
    public void setActiveTool(EditorTool tool) {
        if (activeTool == tool) return;
        
        if (activeTool != null) {
            activeTool.onDeactivate();
        }
        
        activeTool = tool;
        
        if (activeTool != null) {
            activeTool.onActivate();
        }
    }
    
    /**
     * Sets active tool by name.
     */
    public void setActiveTool(String name) {
        for (EditorTool tool : tools) {
            if (tool.getName().equalsIgnoreCase(name)) {
                setActiveTool(tool);
                return;
            }
        }
    }
    
    /**
     * Gets all registered tools.
     */
    public List<EditorTool> getTools() {
        return new ArrayList<>(tools);
    }
    
    /**
     * Gets a tool by name.
     */
    public EditorTool getTool(String name) {
        for (EditorTool tool : tools) {
            if (tool.getName().equalsIgnoreCase(name)) {
                return tool;
            }
        }
        return null;
    }
    
    // ========================================================================
    // INPUT HANDLING
    // ========================================================================
    
    /**
     * Handles mouse button press.
     * 
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param button Mouse button (0=left, 1=right, 2=middle)
     */
    public void handleMouseDown(int tileX, int tileY, int button) {
        if (activeTool == null) return;
        
        isMouseDown = true;
        activeButton = button;
        lastTileX = tileX;
        lastTileY = tileY;
        
        activeTool.onMouseDown(tileX, tileY, button);
    }
    
    /**
     * Handles mouse movement while button is held.
     * Only fires onMouseDrag when tile changes.
     * 
     * @param tileX Current tile X coordinate
     * @param tileY Current tile Y coordinate
     */
    public void handleMouseMove(int tileX, int tileY) {
        if (activeTool == null) return;
        
        if (isMouseDown) {
            // Only fire drag if tile changed
            if (tileX != lastTileX || tileY != lastTileY) {
                lastTileX = tileX;
                lastTileY = tileY;
                activeTool.onMouseDrag(tileX, tileY, activeButton);
            }
        } else {
            // Just hovering
            if (tileX != lastTileX || tileY != lastTileY) {
                lastTileX = tileX;
                lastTileY = tileY;
                activeTool.onMouseMove(tileX, tileY);
            }
        }
    }
    
    /**
     * Handles mouse button release.
     * 
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param button Mouse button released
     */
    public void handleMouseUp(int tileX, int tileY, int button) {
        if (activeTool == null) return;
        
        if (isMouseDown && activeButton == button) {
            isMouseDown = false;
            activeButton = -1;
            activeTool.onMouseUp(tileX, tileY, button);
        }
    }
    
    /**
     * Handles keyboard shortcut for tool switching.
     * 
     * @param key The key pressed (uppercase)
     * @return true if a tool was switched
     */
    public boolean handleKeyPress(String key) {
        EditorTool tool = toolsByShortcut.get(key.toUpperCase());
        if (tool != null) {
            setActiveTool(tool);
            return true;
        }
        return false;
    }
    
    // ========================================================================
    // RENDERING
    // ========================================================================
    
    /**
     * Renders the active tool's overlay.
     */
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (activeTool != null) {
            activeTool.renderOverlay(camera, hoveredTileX, hoveredTileY);
        }
    }
    
    // ========================================================================
    // UPDATE
    // ========================================================================
    
    /**
     * Updates the active tool.
     */
    public void update(float deltaTime) {
        if (activeTool != null) {
            activeTool.update(deltaTime);
        }
    }
    
    /**
     * Resets mouse state (call when focus is lost).
     */
    public void resetMouseState() {
        isMouseDown = false;
        activeButton = -1;
        lastTileX = Integer.MIN_VALUE;
        lastTileY = Integer.MIN_VALUE;
    }
}
