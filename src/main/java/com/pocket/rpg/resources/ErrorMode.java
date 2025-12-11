package com.pocket.rpg.resources;

/**
 * Error handling modes for asset loading.
 */
public enum ErrorMode {
    /**
     * Use placeholder resources when loading fails.
     * Game continues running with visible indicators (e.g., magenta textures).
     */
    USE_PLACEHOLDER,
    
    /**
     * Throw exceptions when loading fails.
     * Useful for development to catch missing assets early.
     */
    THROW_EXCEPTION
}
