package com.pocket.rpg.animation.animator;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.resources.Assets;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines a state in an AnimatorController.
 * <p>
 * States can be:
 * - SIMPLE: single animation
 * - DIRECTIONAL: 4 animations (one per direction)
 */
@Getter
@Setter
public class AnimatorState {

    private String name;
    private StateType type = StateType.SIMPLE;

    /**
     * Animation path for SIMPLE states.
     */
    private String animation;

    /**
     * Animation paths for DIRECTIONAL states.
     */
    private Map<Direction, String> directionalAnimations;

    /**
     * Default constructor for serialization.
     */
    public AnimatorState() {
        this.name = "";
        this.type = StateType.SIMPLE;
    }

    /**
     * Creates a simple state.
     */
    public AnimatorState(String name, String animationPath) {
        this.name = name;
        this.type = StateType.SIMPLE;
        this.animation = animationPath;
    }

    /**
     * Creates a state with specified type.
     */
    public AnimatorState(String name, StateType type) {
        this.name = name;
        this.type = type;
        if (type == StateType.DIRECTIONAL) {
            this.directionalAnimations = new EnumMap<>(Direction.class);
        }
    }

    // ========================================================================
    // DIRECTIONAL ANIMATIONS
    // ========================================================================

    /**
     * Sets a directional animation. Automatically switches type to DIRECTIONAL.
     */
    public void setDirectionalAnimation(Direction direction, String animationPath) {
        if (type != StateType.DIRECTIONAL) {
            type = StateType.DIRECTIONAL;
        }
        if (directionalAnimations == null) {
            directionalAnimations = new EnumMap<>(Direction.class);
        }
        directionalAnimations.put(direction, animationPath);
    }

    /**
     * Gets the animation path for a direction.
     */
    public String getDirectionalAnimation(Direction direction) {
        if (directionalAnimations == null) return null;
        return directionalAnimations.get(direction);
    }

    /**
     * Checks if all 4 directional animations are set.
     */
    public boolean hasAllDirections() {
        if (directionalAnimations == null) return false;
        for (Direction dir : Direction.values()) {
            if (!directionalAnimations.containsKey(dir) ||
                directionalAnimations.get(dir) == null ||
                directionalAnimations.get(dir).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Counts how many directional animations are set.
     */
    public int countSetDirections() {
        if (directionalAnimations == null) return 0;
        int count = 0;
        for (String path : directionalAnimations.values()) {
            if (path != null && !path.isBlank()) {
                count++;
            }
        }
        return count;
    }

    // ========================================================================
    // ANIMATION LOADING
    // ========================================================================

    /**
     * Loads and returns the animation for this state.
     * For SIMPLE states, returns the single animation.
     * For DIRECTIONAL states, returns the animation for the given direction.
     *
     * @param direction The direction (used for DIRECTIONAL states)
     * @return The loaded Animation, or null if path is not set or assets unavailable
     */
    public Animation loadAnimation(Direction direction) {
        String path = getAnimationPath(direction);
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return Assets.load(path, Animation.class);
        } catch (IllegalStateException e) {
            // Assets not initialized (common in unit tests)
            return null;
        }
    }

    /**
     * Gets the animation path for the given direction.
     * For SIMPLE states, direction is ignored and the single animation is returned.
     */
    public String getAnimationPath(Direction direction) {
        if (type == StateType.SIMPLE) {
            return animation;
        } else {
            return getDirectionalAnimation(direction);
        }
    }

    // ========================================================================
    // COPY
    // ========================================================================

    /**
     * Creates a deep copy of this state.
     */
    public AnimatorState copy() {
        AnimatorState copy = new AnimatorState();
        copy.name = this.name;
        copy.type = this.type;
        copy.animation = this.animation;
        if (this.directionalAnimations != null) {
            copy.directionalAnimations = new EnumMap<>(this.directionalAnimations);
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnimatorState that = (AnimatorState) o;
        return Objects.equals(name, that.name) &&
               type == that.type &&
               Objects.equals(animation, that.animation) &&
               Objects.equals(directionalAnimations, that.directionalAnimations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, animation, directionalAnimations);
    }

    @Override
    public String toString() {
        return "AnimatorState{" +
               "name='" + name + '\'' +
               ", type=" + type +
               '}';
    }
}
