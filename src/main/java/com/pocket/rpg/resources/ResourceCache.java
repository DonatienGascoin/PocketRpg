package com.pocket.rpg.resources;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for resources with LRU eviction and weak references.
 *
 * Key features:
 * - Weak references allow GC when no external references exist
 * - Reference counting prevents premature eviction of active resources
 * - LRU eviction removes oldest unused resources when cache is full
 * - Retained resources are never automatically evicted
 */
public class ResourceCache {

    // Cache storage: resourceId -> weak reference to handle
    private final Map<String, WeakReference<ResourceHandle<?>>> cache;

    // Reference counts: resourceId -> count
    private final Map<String, Integer> refCounts;

    // Retained resources that should never be evicted
    private final Set<String> retained;

    // LRU tracking: most recently used at front, oldest at back
    private final LinkedList<String> lruList;

    // Cache size limit
    private int maxCacheSize;

    // Statistics
    private long cacheHits;
    private long cacheMisses;
    private long evictions;

    /**
     * Creates a resource cache with default size (1000 entries).
     */
    public ResourceCache() {
        this(1000);
    }

    /**
     * Creates a resource cache with specified maximum size.
     *
     * @param maxCacheSize Maximum number of cached resources
     */
    public ResourceCache(int maxCacheSize) {
        this.cache = new ConcurrentHashMap<>();
        this.refCounts = new ConcurrentHashMap<>();
        this.retained = Collections.synchronizedSet(new HashSet<>());
        this.lruList = new LinkedList<>();
        this.maxCacheSize = Math.max(10, maxCacheSize);
        this.cacheHits = 0;
        this.cacheMisses = 0;
        this.evictions = 0;
    }

    /**
     * Gets a resource from the cache.
     * Updates LRU tracking and statistics.
     *
     * @param resourceId Resource identifier
     * @param <T>        Resource type
     * @return The cached handle, or null if not found
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> ResourceHandle<T> get(String resourceId) {
        WeakReference<ResourceHandle<?>> weakRef = cache.get(resourceId);

        if (weakRef != null) {
            ResourceHandle<?> handle = weakRef.get();

            if (handle != null) {
                // Cache hit - update LRU
                moveToFront(resourceId);
                cacheHits++;
                return (ResourceHandle<T>) handle;
            } else {
                // Weak reference is dead - clean up
                remove(resourceId);
            }
        }

        // Cache miss
        cacheMisses++;
        return null;
    }

    /**
     * Stores a resource in the cache.
     *
     * @param resourceId Resource identifier
     * @param handle     Resource handle to cache
     * @param <T>        Resource type
     */
    public synchronized <T> void store(String resourceId, ResourceHandle<T> handle) {
        // Remove old entry if exists
        if (cache.containsKey(resourceId)) {
            remove(resourceId);
        }

        // Add to cache
        cache.put(resourceId, new WeakReference<>(handle));
        refCounts.put(resourceId, 0);

        // Add to front of LRU list
        lruList.addFirst(resourceId);

        // Evict if over capacity
        if (lruList.size() > maxCacheSize) {
            evictOldest();
        }
    }

    /**
     * Marks a resource as retained (never auto-evicted).
     *
     * @param resourceId Resource identifier
     */
    public synchronized void markRetained(String resourceId) {
        retained.add(resourceId);

        ResourceHandle<?> handle = getHandle(resourceId);
        if (handle != null) {
            handle.markRetained();
        }
    }

    /**
     * Unmarks a resource as retained.
     *
     * @param resourceId Resource identifier
     */
    public synchronized void unmarkRetained(String resourceId) {
        retained.remove(resourceId);

        ResourceHandle<?> handle = getHandle(resourceId);
        if (handle != null) {
            handle.unmarkRetained();
        }
    }

    /**
     * Increments reference count for a resource.
     *
     * @param resourceId Resource identifier
     */
    public synchronized void incrementRefCount(String resourceId) {
        refCounts.merge(resourceId, 1, Integer::sum);
    }

    /**
     * Decrements reference count for a resource.
     *
     * @param resourceId Resource identifier
     */
    public synchronized void decrementRefCount(String resourceId) {
        Integer count = refCounts.get(resourceId);
        if (count != null && count > 0) {
            refCounts.put(resourceId, count - 1);
        }
    }

    /**
     * Gets current reference count for a resource.
     *
     * @param resourceId Resource identifier
     * @return Current reference count
     */
    public synchronized int getRefCount(String resourceId) {
        return refCounts.getOrDefault(resourceId, 0);
    }

    /**
     * Cleans up unused resources based on reference counts and access time.
     * Should be called periodically (e.g., once per frame).
     *
     * @param evictionThresholdMs Time in ms since last access before eviction
     */
    public synchronized void cleanupUnused(long evictionThresholdMs) {
        List<String> toRemove = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (String resourceId : cache.keySet()) {
            // Skip retained resources
            if (retained.contains(resourceId)) {
                continue;
            }

            WeakReference<ResourceHandle<?>> weakRef = cache.get(resourceId);
            if (weakRef == null) continue;

            ResourceHandle<?> handle = weakRef.get();

            // Remove if weak reference is dead
            if (handle == null) {
                toRemove.add(resourceId);
                continue;
            }

            // Check if eligible for eviction
            int refCount = refCounts.getOrDefault(resourceId, 0);
            long timeSinceAccess = now - handle.getLastAccessed();

            if (refCount == 0 && timeSinceAccess > evictionThresholdMs) {
                toRemove.add(resourceId);
            }
        }

        // Remove marked resources
        for (String resourceId : toRemove) {
            remove(resourceId);
            evictions++;
        }
    }

    /**
     * Evicts the oldest (least recently used) resource if it's not actively used.
     */
    public synchronized void evictOldest() {
        if (lruList.isEmpty()) return;

        // Try to evict from the back (oldest)
        for (int i = lruList.size() - 1; i >= 0; i--) {
            String resourceId = lruList.get(i);

            // Don't evict retained or actively referenced resources
            if (retained.contains(resourceId)) continue;
            if (refCounts.getOrDefault(resourceId, 0) > 0) continue;

            // Found evictable resource
            remove(resourceId);
            evictions++;
            return;
        }

        // All resources are retained or referenced - can't evict
        // This is actually fine - cache can temporarily exceed max size
    }

    /**
     * Removes a resource from the cache.
     *
     * @param resourceId Resource identifier
     */
    public synchronized void remove(String resourceId) {
        cache.remove(resourceId);
        refCounts.remove(resourceId);
        retained.remove(resourceId);
        lruList.remove(resourceId);
    }

    /**
     * Clears all cached resources.
     * Warning: Does not call unload() on resources.
     */
    public synchronized void clear() {
        cache.clear();
        refCounts.clear();
        retained.clear();
        lruList.clear();
        cacheHits = 0;
        cacheMisses = 0;
        evictions = 0;
    }

    /**
     * Checks if a resource is cached.
     *
     * @param resourceId Resource identifier
     * @return true if resource is in cache
     */
    public synchronized boolean contains(String resourceId) {
        WeakReference<ResourceHandle<?>> weakRef = cache.get(resourceId);
        if (weakRef == null) return false;
        return weakRef.get() != null;
    }

    /**
     * Moves a resource to the front of the LRU list (most recently used).
     *
     * @param resourceId Resource identifier
     */
    private void moveToFront(String resourceId) {
        lruList.remove(resourceId);
        lruList.addFirst(resourceId);
    }

    /**
     * Gets the actual handle without updating LRU.
     * Used internally.
     *
     * @param resourceId Resource identifier
     * @return The handle, or null
     */
    private ResourceHandle<?> getHandle(String resourceId) {
        WeakReference<ResourceHandle<?>> weakRef = cache.get(resourceId);
        if (weakRef == null) return null;
        return weakRef.get();
    }

    // Getters for configuration and statistics

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = Math.max(10, maxCacheSize);
    }

    public synchronized int getCurrentSize() {
        return lruList.size();
    }

    public long getCacheHits() {
        return cacheHits;
    }

    public long getCacheMisses() {
        return cacheMisses;
    }

    public long getEvictions() {
        return evictions;
    }

    public double getHitRate() {
        long total = cacheHits + cacheMisses;
        if (total == 0) return 0.0;
        return (double) cacheHits / total;
    }

    /**
     * Gets all currently cached resource IDs.
     *
     * @return List of resource IDs
     */
    public synchronized List<String> getAllResourceIds() {
        return new ArrayList<>(lruList);
    }

    @Override
    public String toString() {
        return String.format(
                "ResourceCache[size=%d/%d, hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d]",
                getCurrentSize(), maxCacheSize, cacheHits, cacheMisses, getHitRate() * 100, evictions
        );
    }
}