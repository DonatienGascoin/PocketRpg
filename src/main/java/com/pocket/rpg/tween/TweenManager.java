package com.pocket.rpg.tween;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages and updates all active tweens.
 * <p>
 * Call {@link #update(float)} every frame from your game loop:
 * <pre>
 * public void update(float deltaTime) {
 *     TweenManager.update(deltaTime);
 *     // ... rest of game update
 * }
 * </pre>
 */
public class TweenManager {

    private static final List<Tween<?>> activeTweens = new ArrayList<>();
    private static final List<Tween<?>> tweensToAdd = new ArrayList<>();
    private static boolean isUpdating = false;

    private TweenManager() {
        // Static utility class
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    /**
     * Updates all active tweens.
     * Call this every frame from your game loop.
     *
     * @param deltaTime Time since last frame in seconds
     */
    public static void update(float deltaTime) {
        // Add pending tweens
        if (!tweensToAdd.isEmpty()) {
            activeTweens.addAll(tweensToAdd);
            tweensToAdd.clear();
        }

        // Update tweens
        isUpdating = true;
        Iterator<Tween<?>> iterator = activeTweens.iterator();
        while (iterator.hasNext()) {
            Tween<?> tween = iterator.next();
            boolean alive = tween.update(deltaTime);
            if (!alive) {
                iterator.remove();
            }
        }
        isUpdating = false;
    }

    // ========================================================================
    // MANAGEMENT
    // ========================================================================

    /**
     * Adds a tween to be managed.
     * Called automatically by Tween constructor.
     */
    public static void add(Tween<?> tween) {
        if (isUpdating) {
            tweensToAdd.add(tween);
        } else {
            activeTweens.add(tween);
        }
    }

    /**
     * Removes a tween from management.
     */
    public static void remove(Tween<?> tween) {
        activeTweens.remove(tween);
        tweensToAdd.remove(tween);
    }

    /**
     * Kills all tweens associated with a target object.
     *
     * @param target The target object
     * @param complete If true, tweens jump to end value before killing
     */
    public static void kill(Object target, boolean complete) {
        Iterator<Tween<?>> iterator = activeTweens.iterator();
        while (iterator.hasNext()) {
            Tween<?> tween = iterator.next();
            if (tween.getTarget() == target) {
                if (complete) {
                    tween.complete();
                }
                iterator.remove();
            }
        }
    }

    /**
     * Kills all tweens with a specific ID.
     *
     * @param id The tween ID
     * @param complete If true, tweens jump to end value before killing
     */
    public static void kill(String id, boolean complete) {
        if (id == null) return;

        Iterator<Tween<?>> iterator = activeTweens.iterator();
        while (iterator.hasNext()) {
            Tween<?> tween = iterator.next();
            if (id.equals(tween.getId())) {
                if (complete) {
                    tween.complete();
                }
                iterator.remove();
            }
        }
    }

    /**
     * Kills all active tweens.
     *
     * @param complete If true, tweens jump to end value before killing
     */
    public static void killAll(boolean complete) {
        if (complete) {
            for (Tween<?> tween : activeTweens) {
                tween.complete();
            }
        }
        activeTweens.clear();
        tweensToAdd.clear();
    }

    /**
     * Pauses all tweens for a target.
     */
    public static void pause(Object target) {
        for (Tween<?> tween : activeTweens) {
            if (tween.getTarget() == target) {
                tween.pause();
            }
        }
    }

    /**
     * Resumes all tweens for a target.
     */
    public static void resume(Object target) {
        for (Tween<?> tween : activeTweens) {
            if (tween.getTarget() == target) {
                tween.resume();
            }
        }
    }

    /**
     * Pauses all active tweens.
     */
    public static void pauseAll() {
        for (Tween<?> tween : activeTweens) {
            tween.pause();
        }
    }

    /**
     * Resumes all active tweens.
     */
    public static void resumeAll() {
        for (Tween<?> tween : activeTweens) {
            tween.resume();
        }
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    /**
     * Gets the count of active tweens.
     */
    public static int getActiveCount() {
        return activeTweens.size();
    }

    /**
     * Checks if a target has any active tweens.
     */
    public static boolean hasActiveTweens(Object target) {
        for (Tween<?> tween : activeTweens) {
            if (tween.getTarget() == target && !tween.isCompleted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any tweens are active.
     */
    public static boolean hasActiveTweens() {
        return !activeTweens.isEmpty();
    }
}