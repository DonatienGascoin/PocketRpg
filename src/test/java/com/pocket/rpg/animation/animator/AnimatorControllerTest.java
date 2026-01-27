package com.pocket.rpg.animation.animator;

import com.pocket.rpg.collision.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimatorControllerTest {

    private AnimatorController controller;

    @BeforeEach
    void setUp() {
        controller = new AnimatorController("test_controller");
    }

    // ========================================================================
    // STATE TESTS
    // ========================================================================

    @Test
    void testAddState() {
        AnimatorState idle = new AnimatorState("idle", "idle.anim");
        controller.addState(idle);

        assertEquals(1, controller.getStateCount());
        assertEquals("idle", controller.getDefaultState()); // First state becomes default
        assertTrue(controller.hasState("idle"));
    }

    @Test
    void testAddMultipleStates() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));
        controller.addState(new AnimatorState("run", "run.anim"));

        assertEquals(3, controller.getStateCount());
        assertEquals("idle", controller.getDefaultState()); // First state stays default
    }

    @Test
    void testRemoveState() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));
        controller.addTransition(new AnimatorTransition("idle", "walk", TransitionType.INSTANT));

        controller.removeState("idle");

        assertEquals(1, controller.getStateCount());
        assertFalse(controller.hasState("idle"));
        assertEquals("walk", controller.getDefaultState()); // Default moves to remaining state
        assertEquals(0, controller.getTransitionCount()); // Transition removed
    }

    @Test
    void testGetStateByName() {
        AnimatorState idle = new AnimatorState("idle", "idle.anim");
        controller.addState(idle);

        assertSame(idle, controller.getState("idle"));
        assertNull(controller.getState("nonexistent"));
    }

    @Test
    void testGetStateByIndex() {
        controller.addState(new AnimatorState("a", "a.anim"));
        controller.addState(new AnimatorState("b", "b.anim"));

        assertEquals("a", controller.getState(0).getName());
        assertEquals("b", controller.getState(1).getName());
        assertNull(controller.getState(-1));
        assertNull(controller.getState(5));
    }

    @Test
    void testGetStateIndex() {
        controller.addState(new AnimatorState("a", "a.anim"));
        controller.addState(new AnimatorState("b", "b.anim"));

        assertEquals(0, controller.getStateIndex("a"));
        assertEquals(1, controller.getStateIndex("b"));
        assertEquals(-1, controller.getStateIndex("nonexistent"));
    }

    // ========================================================================
    // TRANSITION TESTS
    // ========================================================================

    @Test
    void testAddTransition() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));

        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        controller.addTransition(trans);

        assertEquals(1, controller.getTransitionCount());
        assertTrue(controller.hasTransition("idle", "walk"));
    }

    @Test
    void testRemoveTransition() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));
        controller.addTransition(new AnimatorTransition("idle", "walk", TransitionType.INSTANT));

        controller.removeTransition(0);

        assertEquals(0, controller.getTransitionCount());
    }

    @Test
    void testGetTransitionsFrom() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));
        controller.addState(new AnimatorState("attack", "attack.anim"));

        controller.addTransition(new AnimatorTransition("idle", "walk", TransitionType.INSTANT));
        controller.addTransition(new AnimatorTransition("idle", "attack", TransitionType.INSTANT));
        controller.addTransition(new AnimatorTransition("*", "hurt", TransitionType.INSTANT)); // Any state

        List<AnimatorTransition> fromIdle = controller.getTransitionsFrom("idle");

        assertEquals(3, fromIdle.size()); // 2 from idle + 1 from any
    }

    // ========================================================================
    // PARAMETER TESTS
    // ========================================================================

    @Test
    void testAddParameter() {
        controller.addParameter(new AnimatorParameter("isMoving", false));

        assertEquals(1, controller.getParameterCount());
        assertTrue(controller.hasParameter("isMoving"));
    }

    @Test
    void testRemoveParameterByIndex() {
        controller.addParameter(new AnimatorParameter("a", false));
        controller.addParameter(new AnimatorParameter("b", false));

        controller.removeParameter(0);

        assertEquals(1, controller.getParameterCount());
        assertFalse(controller.hasParameter("a"));
        assertTrue(controller.hasParameter("b"));
    }

    @Test
    void testRemoveParameterByName() {
        controller.addParameter(new AnimatorParameter("a", false));
        controller.addParameter(new AnimatorParameter("b", false));

        controller.removeParameter("b");

        assertEquals(1, controller.getParameterCount());
        assertTrue(controller.hasParameter("a"));
        assertFalse(controller.hasParameter("b"));
    }

    @Test
    void testGetParameter() {
        AnimatorParameter param = new AnimatorParameter("speed", Direction.DOWN);
        controller.addParameter(param);

        assertSame(param, controller.getParameter("speed"));
        assertSame(param, controller.getParameter(0));
        assertNull(controller.getParameter("nonexistent"));
    }

    // ========================================================================
    // COPY TESTS
    // ========================================================================

    @Test
    void testCopy() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));
        controller.addTransition(new AnimatorTransition("idle", "walk", TransitionType.INSTANT));
        controller.addParameter(new AnimatorParameter("isMoving", false));

        AnimatorController copy = controller.copy();

        assertEquals(controller.getName(), copy.getName());
        assertEquals(controller.getDefaultState(), copy.getDefaultState());
        assertEquals(controller.getStateCount(), copy.getStateCount());
        assertEquals(controller.getTransitionCount(), copy.getTransitionCount());
        assertEquals(controller.getParameterCount(), copy.getParameterCount());

        // Ensure deep copy
        copy.getState(0).setName("modified");
        assertNotEquals(controller.getState(0).getName(), copy.getState(0).getName());
    }

    @Test
    void testCopyFrom() {
        controller.addState(new AnimatorState("idle", "idle.anim"));

        AnimatorController other = new AnimatorController("other");
        other.addState(new AnimatorState("walk", "walk.anim"));
        other.addState(new AnimatorState("run", "run.anim"));

        controller.copyFrom(other);

        assertEquals("other", controller.getName());
        assertEquals(2, controller.getStateCount());
        assertTrue(controller.hasState("walk"));
        assertFalse(controller.hasState("idle"));
    }

    // ========================================================================
    // VALIDATION TESTS
    // ========================================================================

    @Test
    void testValidateEmpty() {
        AnimatorController empty = new AnimatorController();

        List<String> issues = empty.validate();

        assertTrue(issues.stream().anyMatch(s -> s.contains("no name")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("no states")));
    }

    @Test
    void testValidateInvalidDefaultState() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.setDefaultState("nonexistent");

        List<String> issues = controller.validate();

        assertTrue(issues.stream().anyMatch(s -> s.contains("Default state") && s.contains("does not exist")));
    }

    @Test
    void testValidateInvalidTransition() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addTransition(new AnimatorTransition("idle", "nonexistent", TransitionType.INSTANT));

        List<String> issues = controller.validate();

        assertTrue(issues.stream().anyMatch(s -> s.contains("unknown state")));
    }

    @Test
    void testValidateInvalidCondition() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));

        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        trans.addCondition(new TransitionCondition("nonexistentParam", true));
        controller.addTransition(trans);

        List<String> issues = controller.validate();

        assertTrue(issues.stream().anyMatch(s -> s.contains("unknown parameter")));
    }

    @Test
    void testValidateValid() {
        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));
        controller.addParameter(new AnimatorParameter("isMoving", false));

        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        trans.addCondition(new TransitionCondition("isMoving", true));
        controller.addTransition(trans);

        assertTrue(controller.isValid());
        assertTrue(controller.validate().isEmpty());
    }
}
