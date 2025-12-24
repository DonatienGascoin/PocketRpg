package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.TilemapRenderer;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Batch command for tile painting/erasing operations.
 * Captures all tile changes during a single drag operation.
 * 
 * Supports proper undo AND redo.
 */
public class BatchTileCommand implements EditorCommand {

    private final TilemapLayer layer;
    private final Map<Long, TilemapRenderer.Tile> beforeTiles = new HashMap<>();
    private final Map<Long, TilemapRenderer.Tile> afterTiles = new HashMap<>();
    private final String description;
    
    // Track if this is the first execute call (during initial drag, changes already applied)
    private boolean changesAlreadyApplied = true;

    public BatchTileCommand(TilemapLayer layer, String description) {
        this.layer = layer;
        this.description = description;
    }

    /**
     * Records a tile change. Call this BEFORE modifying the tile.
     */
    public void recordChange(int x, int y, TilemapRenderer.Tile newTile) {
        long key = key(x, y);
        
        // Only capture the original state once
        if (!beforeTiles.containsKey(key)) {
            TilemapRenderer.Tile oldTile = layer.getTilemap().get(x, y);
            beforeTiles.put(key, oldTile); // May be null
        }
        
        // Always update the final state
        afterTiles.put(key, newTile);
    }

    /**
     * Checks if any changes were recorded.
     */
    public boolean hasChanges() {
        return !afterTiles.isEmpty();
    }

    @Override
    public void execute() {
        // On first call from UndoManager.execute(), changes are already applied during drag
        // On subsequent calls (redo), we need to actually apply the changes
        if (changesAlreadyApplied) {
            changesAlreadyApplied = false;
            return;
        }
        
        // Redo: Apply all "after" tiles
        for (Map.Entry<Long, TilemapRenderer.Tile> entry : afterTiles.entrySet()) {
            int[] coords = fromKey(entry.getKey());
            TilemapRenderer.Tile tile = entry.getValue();
            
            if (tile == null) {
                layer.getTilemap().clear(coords[0], coords[1]);
            } else {
                layer.getTilemap().set(coords[0], coords[1], tile);
            }
        }
    }

    @Override
    public void undo() {
        // Restore all "before" tiles
        for (Map.Entry<Long, TilemapRenderer.Tile> entry : beforeTiles.entrySet()) {
            int[] coords = fromKey(entry.getKey());
            TilemapRenderer.Tile tile = entry.getValue();
            
            if (tile == null) {
                layer.getTilemap().clear(coords[0], coords[1]);
            } else {
                layer.getTilemap().set(coords[0], coords[1], tile);
            }
        }
    }

    @Override
    public String getDescription() {
        return description + " (" + afterTiles.size() + " tiles)";
    }

    private static long key(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    private static int[] fromKey(long key) {
        return new int[]{(int) (key >> 32), (int) key};
    }
}
