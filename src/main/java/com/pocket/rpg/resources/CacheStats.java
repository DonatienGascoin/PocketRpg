package com.pocket.rpg.resources;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics tracking for the resource cache.
 * Thread-safe counters for monitoring cache performance.
 */
public class CacheStats {
    
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong totalLoads = new AtomicLong(0);
    
    /**
     * Records a cache hit.
     */
    public void recordHit() {
        hits.incrementAndGet();
    }
    
    /**
     * Records a cache miss.
     */
    public void recordMiss() {
        misses.incrementAndGet();
    }
    
    /**
     * Records a load attempt.
     */
    public void recordLoad() {
        totalLoads.incrementAndGet();
    }
    
    /**
     * Gets the number of cache hits.
     * 
     * @return Cache hit count
     */
    public long getHits() {
        return hits.get();
    }
    
    /**
     * Gets the number of cache misses.
     * 
     * @return Cache miss count
     */
    public long getMisses() {
        return misses.get();
    }
    
    /**
     * Gets the total number of load attempts.
     * 
     * @return Total load count
     */
    public long getTotalLoads() {
        return totalLoads.get();
    }
    
    /**
     * Calculates the cache hit rate.
     * 
     * @return Hit rate as a value between 0.0 and 1.0, or 0.0 if no loads
     */
    public double getHitRate() {
        long total = hits.get() + misses.get();
        if (total == 0) return 0.0;
        return (double) hits.get() / total;
    }
    
    /**
     * Resets all statistics.
     */
    public void reset() {
        hits.set(0);
        misses.set(0);
        totalLoads.set(0);
    }
    
    @Override
    public String toString() {
        return String.format("CacheStats[hits=%d, misses=%d, loads=%d, hitRate=%.2f%%]",
                getHits(), getMisses(), getTotalLoads(), getHitRate() * 100);
    }
}
