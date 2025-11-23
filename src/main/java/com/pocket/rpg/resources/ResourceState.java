package com.pocket.rpg.resources;

/**
 * Represents the lifecycle state of a resource.
 * Resources progress through these states during loading and unloading.
 */
public enum ResourceState {
    /**
     * Resource has not been loaded yet.
     * Initial state before any loading begins.
     */
    UNLOADED,

    /**
     * Resource is currently being loaded asynchronously.
     * During this state, get() may return a placeholder.
     */
    LOADING,

    /**
     * Resource has been successfully loaded and is ready to use.
     * get() will return the actual resource.
     */
    READY,

    /**
     * Resource loading failed due to an error.
     * get() may return a placeholder or throw an exception.
     */
    FAILED,

    /**
     * Resource has been evicted from the cache.
     * May transition back to UNLOADED or LOADING if needed again.
     */
    EVICTED
}