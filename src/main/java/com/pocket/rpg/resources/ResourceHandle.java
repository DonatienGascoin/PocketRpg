package com.pocket.rpg.resources;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Type-safe handle for managed resources.
 * Provides reference counting, lifecycle tracking, and async loading support.
 *
 * Uses weak references internally to allow garbage collection when appropriate,
 * while reference counting prevents premature eviction of actively used resources.
 *
 * @param <T> The type of resource this handle references
 */
public class ResourceHandle<T> {

    private final String resourceId;
    private final AtomicInteger refCount;
    private WeakReference<T> data;
    private ResourceState state;
    private long lastAccessed;
    private boolean retained;
    private Throwable loadError;
    private final List<Consumer<ResourceHandle<T>>> callbacks;

    /**
     * Creates a new resource handle.
     *
     * @param resourceId Unique identifier for this resource
     */
    public ResourceHandle(String resourceId) {
        this(resourceId, ResourceState.UNLOADED);
    }

    /**
     * Creates a new resource handle with an initial state.
     *
     * @param resourceId Unique identifier for this resource
     * @param state      Initial state
     */
    public ResourceHandle(String resourceId, ResourceState state) {
        this.resourceId = resourceId;
        this.refCount = new AtomicInteger(0);
        this.data = new WeakReference<>(null);
        this.state = state;
        this.lastAccessed = System.currentTimeMillis();
        this.retained = false;
        this.loadError = null;
        this.callbacks = new ArrayList<>();
    }

    /**
     * Gets the resource data.
     * Returns null if loading or if resource has been garbage collected.
     * Updates last accessed time.
     *
     * @return The resource, or null if not available
     */
    public T get() {
        lastAccessed = System.currentTimeMillis();

        if (state == ResourceState.FAILED) {
            System.err.println("WARNING: Attempted to get failed resource: " + resourceId);
            if (loadError != null) {
                System.err.println("Error was: " + loadError.getMessage());
            }
            return null;
        }

        if (state == ResourceState.LOADING) {
            // Resource still loading - caller can handle null or wait
            return null;
        }

        if (state == ResourceState.UNLOADED || state == ResourceState.EVICTED) {
            System.err.println("WARNING: Attempted to get unloaded resource: " + resourceId);
            return null;
        }

        // Try to get the actual data
        T resource = data.get();

        if (resource == null && state == ResourceState.READY) {
            // Resource was ready but got garbage collected
            System.err.println("WARNING: Resource was garbage collected: " + resourceId);
            state = ResourceState.EVICTED;
        }

        return resource;
    }

    /**
     * Gets the resource data, throwing an exception if not ready.
     *
     * @return The resource data
     * @throws IllegalStateException if resource is not in READY state
     */
    public T getOrThrow() {
        if (state != ResourceState.READY) {
            throw new IllegalStateException(
                    "Resource not ready: " + resourceId + " (state: " + state + ")"
            );
        }

        T resource = data.get();
        if (resource == null) {
            throw new IllegalStateException(
                    "Resource was garbage collected: " + resourceId
            );
        }

        lastAccessed = System.currentTimeMillis();
        return resource;
    }

    /**
     * Sets the resource data and marks it as ready.
     * Called by the loading system when loading completes.
     *
     * @param resource The loaded resource
     */
    public void setData(T resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Cannot set null resource data");
        }

        this.data = new WeakReference<>(resource);
        this.state = ResourceState.READY;
        this.loadError = null;
        this.lastAccessed = System.currentTimeMillis();

        // Execute any pending callbacks
        executeCallbacks();
    }

    /**
     * Sets the resource state.
     *
     * @param state New state
     */
    public void setState(ResourceState state) {
        this.state = state;

        if (state == ResourceState.READY) {
            executeCallbacks();
        }
    }

    /**
     * Sets the load error and marks resource as failed.
     *
     * @param error The error that occurred during loading
     */
    public void setError(Throwable error) {
        this.loadError = error;
        this.state = ResourceState.FAILED;

        // Still execute callbacks so consumers know loading failed
        executeCallbacks();
    }

    /**
     * Increments the reference count.
     * Resources with refCount > 0 won't be automatically evicted.
     */
    public void retain() {
        refCount.incrementAndGet();
    }

    /**
     * Decrements the reference count.
     * When refCount reaches 0, resource becomes eligible for eviction.
     */
    public void release() {
        int count = refCount.decrementAndGet();
        if (count < 0) {
            System.err.println("WARNING: Negative refCount for resource: " + resourceId);
            refCount.set(0);
        }
    }

    /**
     * Marks this resource as retained.
     * Retained resources are never automatically evicted, regardless of refCount.
     * Use for critical resources like UI atlases, core game assets.
     */
    public void markRetained() {
        this.retained = true;
    }

    /**
     * Unmarks this resource as retained.
     * Resource becomes eligible for normal eviction rules.
     */
    public void unmarkRetained() {
        this.retained = false;
    }

    /**
     * Registers a callback to be invoked when resource becomes ready.
     * If resource is already ready, callback is invoked immediately.
     *
     * @param callback Function to call when resource is ready
     */
    public void onReady(Consumer<ResourceHandle<T>> callback) {
        if (callback == null) return;

        if (state == ResourceState.READY || state == ResourceState.FAILED) {
            // Already finished loading - invoke immediately
            callback.accept(this);
        } else {
            // Still loading - queue for later
            synchronized (callbacks) {
                callbacks.add(callback);
            }
        }
    }

    /**
     * Executes all pending callbacks.
     * Called when resource transitions to READY or FAILED state.
     */
    private void executeCallbacks() {
        List<Consumer<ResourceHandle<T>>> toExecute;

        synchronized (callbacks) {
            if (callbacks.isEmpty()) return;

            toExecute = new ArrayList<>(callbacks);
            callbacks.clear();
        }

        for (Consumer<ResourceHandle<T>> callback : toExecute) {
            try {
                callback.accept(this);
            } catch (Exception e) {
                System.err.println("Error executing callback for resource " + resourceId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Checks if the resource is currently valid and accessible.
     *
     * @return true if resource is ready and data is accessible
     */
    public boolean isValid() {
        return state == ResourceState.READY && data.get() != null;
    }

    /**
     * Checks if the resource is in the READY state.
     *
     * @return true if resource finished loading successfully
     */
    public boolean isReady() {
        return state == ResourceState.READY;
    }

    /**
     * Checks if the resource is currently loading.
     *
     * @return true if resource is in LOADING state
     */
    public boolean isLoading() {
        return state == ResourceState.LOADING;
    }

    /**
     * Checks if the resource failed to load.
     *
     * @return true if resource is in FAILED state
     */
    public boolean isFailed() {
        return state == ResourceState.FAILED;
    }

    // Getters

    public String getResourceId() {
        return resourceId;
    }

    public int getRefCount() {
        return refCount.get();
    }

    public ResourceState getState() {
        return state;
    }

    public long getLastAccessed() {
        return lastAccessed;
    }

    public boolean isRetained() {
        return retained;
    }

    public Throwable getLoadError() {
        return loadError;
    }

    /**
     * Gets time since last access in milliseconds.
     *
     * @return milliseconds since last access
     */
    public long getTimeSinceLastAccess() {
        return System.currentTimeMillis() - lastAccessed;
    }

    @Override
    public String toString() {
        return String.format("ResourceHandle[id=%s, state=%s, refCount=%d, retained=%s, valid=%s]",
                resourceId, state, refCount.get(), retained, isValid());
    }
}