package com.pocket.rpg.editor.serialization;

// ============================================================================
// Add these fields and methods to your existing EntityData class
// ============================================================================

import lombok.Getter;
import lombok.Setter;

/**
 * ADDITIONS TO EntityData for hierarchy support.
 * Merge these into your existing EntityData class.
 */
public class EntityDataAdditions {

    /**
     * Parent entity ID for hierarchy (null for root entities).
     */
    @Getter
    @Setter
    private String parentId;

    /**
     * Sibling order (lower = earlier in list).
     */
    @Getter
    @Setter
    private int order;

    // These fields should serialize automatically with Gson.
    // Make sure your EntityData class includes them:
    //
    // private String parentId;
    // private int order;
    //
    // And add getters/setters (or use Lombok @Getter @Setter).
}
