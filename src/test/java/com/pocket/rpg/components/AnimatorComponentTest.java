package com.pocket.rpg.components;

import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorParameter;
import com.pocket.rpg.animation.animator.AnimatorState;
import com.pocket.rpg.animation.animator.AnimatorTransition;
import com.pocket.rpg.animation.animator.TransitionCondition;
import com.pocket.rpg.animation.animator.TransitionType;
import com.pocket.rpg.collision.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AnimatorComponent.
 * Note: These tests don't load actual animation assets - they test state machine logic.
 */
class AnimatorComponentTest {

    private AnimatorController controller;
    private AnimatorComponent component;

    @BeforeEach
    void setUp() {
        // Create a simple controller
        controller = new AnimatorController("test");

        controller.addState(new AnimatorState("idle", "idle.anim"));
        controller.addState(new AnimatorState("walk", "walk.anim"));
        controller.addState(new AnimatorState("attack", "attack.anim"));

        controller.addParameter(new AnimatorParameter("isMoving", false));
        controller.addParameter(AnimatorParameter.trigger("attackTrigger"));
        controller.addParameter(new AnimatorParameter("direction", Direction.DOWN));

        AnimatorTransition idleToWalk = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        idleToWalk.addCondition(new TransitionCondition("isMoving", true));
        controller.addTransition(idleToWalk);

        AnimatorTransition walkToIdle = new AnimatorTransition("walk", "idle", TransitionType.INSTANT);
        walkToIdle.addCondition(new TransitionCondition("isMoving", false));
        controller.addTransition(walkToIdle);

        AnimatorTransition anyToAttack = new AnimatorTransition("*", "attack", TransitionType.INSTANT);
        anyToAttack.addCondition(new TransitionCondition("attackTrigger", true));
        controller.addTransition(anyToAttack);

        // Create component (note: we're testing without GameObject context)
        component = new AnimatorComponent(controller);
    }

    @Test
    void testCreation() {
        assertNotNull(component);
        assertEquals(controller, component.getController());
    }

    @Test
    void testSetBoolParameter() {
        // Initialize state machine by simulating start
        simulateStart();

        assertFalse(component.getBool("isMoving"));

        component.setBool("isMoving", true);

        assertTrue(component.getBool("isMoving"));
    }

    @Test
    void testSetDirection() {
        simulateStart();

        assertEquals(Direction.DOWN, component.getDirection("direction"));

        component.setDirection("direction", Direction.UP);

        assertEquals(Direction.UP, component.getDirection("direction"));
    }

    @Test
    void testSetTrigger() {
        simulateStart();

        assertFalse(component.getBool("attackTrigger"));

        component.setTrigger("attackTrigger");

        assertTrue(component.getBool("attackTrigger"));
    }

    @Test
    void testResetTrigger() {
        simulateStart();

        component.setTrigger("attackTrigger");
        assertTrue(component.getBool("attackTrigger"));

        component.resetTrigger("attackTrigger");
        assertFalse(component.getBool("attackTrigger"));
    }

    @Test
    void testGetParameterValue() {
        simulateStart();

        assertEquals(false, component.getParameterValue("isMoving"));

        component.setBool("isMoving", true);

        assertEquals(true, component.getParameterValue("isMoving"));
    }

    @Test
    void testInitialState() {
        simulateStart();

        assertEquals("idle", component.getCurrentState());
        assertNull(component.getPreviousState());
    }

    @Test
    void testStateTransition() {
        simulateStart();

        assertEquals("idle", component.getCurrentState());

        component.setBool("isMoving", true);
        component.update(0.016f);

        assertEquals("walk", component.getCurrentState());
        assertEquals("idle", component.getPreviousState());
    }

    @Test
    void testIsInState() {
        simulateStart();

        assertTrue(component.isInState("idle"));
        assertFalse(component.isInState("walk"));

        component.setBool("isMoving", true);
        component.update(0.016f);

        assertFalse(component.isInState("idle"));
        assertTrue(component.isInState("walk"));
    }

    @Test
    void testForceState() {
        simulateStart();

        assertEquals("idle", component.getCurrentState());

        component.forceState("walk");

        assertEquals("walk", component.getCurrentState());
    }

    @Test
    void testForceStateInvalid() {
        simulateStart();

        assertEquals("idle", component.getCurrentState());

        component.forceState("nonexistent");

        assertEquals("idle", component.getCurrentState()); // Unchanged
    }

    @Test
    void testReset() {
        simulateStart();

        // Make some changes
        component.setBool("isMoving", true);
        component.update(0.016f);
        assertEquals("walk", component.getCurrentState());

        // Reset
        component.reset();

        assertEquals("idle", component.getCurrentState());
        assertNull(component.getPreviousState());
        assertFalse(component.getBool("isMoving"));
    }

    @Test
    void testSpeed() {
        assertEquals(1.0f, component.getSpeed());

        component.setSpeed(2.0f);

        assertEquals(2.0f, component.getSpeed());
    }

    @Test
    void testSpeedMinimum() {
        component.setSpeed(0.001f);

        // Should be clamped to minimum
        assertEquals(0.01f, component.getSpeed());
    }

    @Test
    void testAutoPlay() {
        assertTrue(component.isAutoPlay());

        component.setAutoPlay(false);

        assertFalse(component.isAutoPlay());
    }

    @Test
    void testSetController() {
        simulateStart();

        assertEquals("idle", component.getCurrentState());

        // Create new controller with different states
        AnimatorController newController = new AnimatorController("new");
        newController.addState(new AnimatorState("run", "run.anim"));
        newController.addState(new AnimatorState("jump", "jump.anim"));

        component.setController(newController);

        assertEquals("run", component.getCurrentState()); // New default state
        assertEquals(newController, component.getController());
    }

    @Test
    void testSetControllerNull() {
        simulateStart();

        assertNotNull(component.getStateMachine());

        component.setController(null);

        assertNull(component.getStateMachine());
        assertNull(component.getPlayer());
    }

    @Test
    void testGetPlayer() {
        simulateStart();

        assertNotNull(component.getPlayer());
    }

    @Test
    void testGetStateMachine() {
        simulateStart();

        assertNotNull(component.getStateMachine());
    }

    @Test
    void testHasPendingTransition() {
        simulateStart();

        assertFalse(component.hasPendingTransition());
    }

    @Test
    void testCancelPendingTransition() {
        simulateStart();

        // No error even if there's no pending transition
        component.cancelPendingTransition();

        assertFalse(component.hasPendingTransition());
    }

    @Test
    void testNullControllerOperations() {
        // Component with no controller
        AnimatorComponent emptyComponent = new AnimatorComponent();

        // These should not throw
        emptyComponent.setBool("test", true);
        emptyComponent.setDirection("direction", Direction.UP);
        emptyComponent.setTrigger("test");
        emptyComponent.resetTrigger("test");
        emptyComponent.forceState("state");
        emptyComponent.reset();
        emptyComponent.cancelPendingTransition();

        // These should return safe defaults
        assertFalse(emptyComponent.getBool("test"));
        assertEquals(Direction.DOWN, emptyComponent.getDirection("direction"));
        assertNull(emptyComponent.getParameterValue("test"));
        assertNull(emptyComponent.getCurrentState());
        assertNull(emptyComponent.getPreviousState());
        assertFalse(emptyComponent.isInState("state"));
        assertFalse(emptyComponent.hasPendingTransition());
    }

    /**
     * Simulates component initialization (onStart lifecycle).
     * In real usage, this is called by the engine.
     */
    private void simulateStart() {
        // Use reflection or direct call to initialize state machine
        // For testing, we call setController which initializes the state machine
        component.setController(controller);
    }
}
