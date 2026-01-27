package com.pocket.rpg.animation.animator;

import com.pocket.rpg.collision.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimatorStateTest {

    @Test
    void testSimpleState() {
        AnimatorState state = new AnimatorState("idle", "animations/idle.anim");

        assertEquals("idle", state.getName());
        assertEquals(StateType.SIMPLE, state.getType());
        assertEquals("animations/idle.anim", state.getAnimation());
    }

    @Test
    void testDirectionalState() {
        AnimatorState state = new AnimatorState("walk", StateType.DIRECTIONAL);

        state.setDirectionalAnimation(Direction.UP, "walk_up.anim");
        state.setDirectionalAnimation(Direction.DOWN, "walk_down.anim");
        state.setDirectionalAnimation(Direction.LEFT, "walk_left.anim");
        state.setDirectionalAnimation(Direction.RIGHT, "walk_right.anim");

        assertEquals("walk", state.getName());
        assertEquals(StateType.DIRECTIONAL, state.getType());
        assertEquals("walk_up.anim", state.getDirectionalAnimation(Direction.UP));
        assertEquals("walk_down.anim", state.getDirectionalAnimation(Direction.DOWN));
        assertTrue(state.hasAllDirections());
        assertEquals(4, state.countSetDirections());
    }

    @Test
    void testPartialDirectionalState() {
        AnimatorState state = new AnimatorState("walk", StateType.DIRECTIONAL);

        state.setDirectionalAnimation(Direction.UP, "walk_up.anim");
        state.setDirectionalAnimation(Direction.DOWN, "walk_down.anim");

        assertFalse(state.hasAllDirections());
        assertEquals(2, state.countSetDirections());
    }

    @Test
    void testSetDirectionalAnimationAutoSwitchesType() {
        AnimatorState state = new AnimatorState("test", "simple.anim");
        assertEquals(StateType.SIMPLE, state.getType());

        state.setDirectionalAnimation(Direction.UP, "up.anim");
        assertEquals(StateType.DIRECTIONAL, state.getType());
    }

    @Test
    void testGetAnimationPath() {
        AnimatorState simpleState = new AnimatorState("idle", "idle.anim");
        assertEquals("idle.anim", simpleState.getAnimationPath(Direction.UP));
        assertEquals("idle.anim", simpleState.getAnimationPath(Direction.DOWN));

        AnimatorState dirState = new AnimatorState("walk", StateType.DIRECTIONAL);
        dirState.setDirectionalAnimation(Direction.UP, "up.anim");
        dirState.setDirectionalAnimation(Direction.DOWN, "down.anim");

        assertEquals("up.anim", dirState.getAnimationPath(Direction.UP));
        assertEquals("down.anim", dirState.getAnimationPath(Direction.DOWN));
        assertNull(dirState.getAnimationPath(Direction.LEFT));
    }

    @Test
    void testCopy() {
        AnimatorState original = new AnimatorState("walk", StateType.DIRECTIONAL);
        original.setDirectionalAnimation(Direction.UP, "up.anim");
        original.setDirectionalAnimation(Direction.DOWN, "down.anim");

        AnimatorState copy = original.copy();

        assertEquals(original.getName(), copy.getName());
        assertEquals(original.getType(), copy.getType());
        assertEquals(original.getDirectionalAnimation(Direction.UP), copy.getDirectionalAnimation(Direction.UP));

        // Ensure deep copy
        copy.setName("modified");
        assertNotEquals(original.getName(), copy.getName());
    }

    @Test
    void testEquals() {
        AnimatorState s1 = new AnimatorState("idle", "idle.anim");
        AnimatorState s2 = new AnimatorState("idle", "idle.anim");
        AnimatorState s3 = new AnimatorState("walk", "idle.anim");

        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
    }
}
