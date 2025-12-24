package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.editor.undo.EditorCommand;

import java.util.HashMap;
import java.util.Map;

/**
 * Batch command for collision painting/erasing operations.
 * Captures all collision changes during a single drag operation.
 * 
 * Supports proper undo AND redo.
 */
public class BatchCollisionCommand implements EditorCommand {

    private final CollisionMap collisionMap;
    private final int zLevel;
    private final Map<Long, CollisionType> beforeTypes = new HashMap<>();
    private final Map<Long, CollisionType> afterTypes = new HashMap<>();
    private final String description;
    
    // Track if this is the first execute call (during initial drag, changes already applied)
    private boolean changesAlreadyApplied = true;

    public BatchCollisionCommand(CollisionMap collisionMap, int zLevel, String description) {
        this.collisionMap = collisionMap;
        this.zLevel = zLevel;
        this.description = description;
    }

    /**
     * Records a collision change. Call this BEFORE modifying the collision.
     */
    public void recordChange(int x, int y, CollisionType newType) {
        long key = key(x, y);
        
        // Only capture the original state once
        if (!beforeTypes.containsKey(key)) {
            CollisionType oldType = collisionMap.get(x, y, zLevel);
            beforeTypes.put(key, oldType);
        }
        
        // Always update the final state
        afterTypes.put(key, newType);
    }

    /**
     * Checks if any changes were recorded.
     */
    public boolean hasChanges() {
        return !afterTypes.isEmpty();
    }

    @Override
    public void execute() {
        // On first call from UndoManager.execute(), changes are already applied during drag
        // On subsequent calls (redo), we need to actually apply the changes
        if (changesAlreadyApplied) {
            changesAlreadyApplied = false;
            return;
        }
        
        // Redo: Apply all "after" types
        for (Map.Entry<Long, CollisionType> entry : afterTypes.entrySet()) {
            int[] coords = fromKey(entry.getKey());
            collisionMap.set(coords[0], coords[1], zLevel, entry.getValue());
        }
    }

    @Override
    public void undo() {
        // Restore all "before" types
        for (Map.Entry<Long, CollisionType> entry : beforeTypes.entrySet()) {
            int[] coords = fromKey(entry.getKey());
            collisionMap.set(coords[0], coords[1], zLevel, entry.getValue());
        }
    }

    @Override
    public String getDescription() {
        return description + " (" + afterTypes.size() + " cells)";
    }

    private static long key(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }

    private static int[] fromKey(long key) {
        return new int[]{(int) (key >> 32), (int) key};
    }
}
