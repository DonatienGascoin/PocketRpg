package com.pocket.rpg.resources;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simple cache with hard references.
 * Stores loaded resources in a thread-safe map.
 */
public class ResourceCache {
    
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final CacheStats stats = new CacheStats();
    private int maxSize = 5000;
    
    /**
     * Gets a resource from the cache.
     * 
     * @param path Resource path
     * @param <T> Resource type
     * @return Cached resource, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        Object resource = cache.get(path);
        if (resource != null) {
            stats.recordHit();
        } else {
            stats.recordMiss();
        }
        return (T) resource;
    }
    
    /**
     * Stores a resource in the cache.
     * 
     * @param path Resource path (key)
     * @param resource Resource to cache
     */
    public void put(String path, Object resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Cannot cache null resource");
        }
        
        // Simple eviction: if at max size, don't add more
        // In a real LRU implementation, we'd evict oldest
        if (cache.size() >= maxSize && !cache.containsKey(path)) {
            System.err.println("WARNING: Cache full (" + maxSize + " items). Consider increasing cache size.");
            return;
        }
        
        cache.put(path, resource);
    }
    
    /**
     * Checks if a resource is cached.
     * 
     * @param path Resource path
     * @return true if resource is in cache
     */
    public boolean contains(String path) {
        return cache.containsKey(path);
    }
    
    /**
     * Gets all resources of a specific type.
     * 
     * @param type Resource type class
     * @param <T> Resource type
     * @return List of all cached resources of that type
     */
    public <T> List<T> getAllOfType(Class<T> type) {
        return cache.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all cached resource paths.
     * 
     * @return Set of all cached paths
     */
    public Set<String> getPaths() {
        return new HashSet<>(cache.keySet());
    }
    
    /**
     * Removes a resource from the cache.
     * 
     * @param path Resource path
     * @return The removed resource, or null if not found
     */
    public Object remove(String path) {
        return cache.remove(path);
    }
    
    /**
     * Clears all cached resources.
     */
    public void clear() {
        cache.clear();
        stats.reset();
    }
    
    /**
     * Gets the current cache size.
     * 
     * @return Number of cached resources
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Gets the maximum cache size.
     * 
     * @return Maximum number of resources that can be cached
     */
    public int getMaxSize() {
        return maxSize;
    }
    
    /**
     * Sets the maximum cache size.
     * 
     * @param maxSize Maximum cache size (0 = unlimited)
     */
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
    
    /**
     * Gets cache statistics.
     * 
     * @return Cache statistics
     */
    public CacheStats getStats() {
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("ResourceCache[size=%d/%d, %s]", 
                size(), maxSize, stats);
    }
}
