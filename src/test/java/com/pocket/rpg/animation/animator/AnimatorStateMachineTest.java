package com.pocket.rpg.animation.animator;

import com.pocket.rpg.animation.Animation;
import com.pocket.rpg.animation.AnimationFrame;
import com.pocket.rpg.animation.AnimationPlayer;
import com.pocket.rpg.collision.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AnimatorStateMachine.
 * Note: These tests don't load actual animation assets - they test the state machine logic.
 */
class AnimatorStateMachineTest {

    private AnimatorController controller;
    private AnimationPlayer player;
    private AnimatorStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        // Create a simple controller with idle and walk states
        controller = new AnimatorController("test");

        AnimatorState idle = new AnimatorState("idle", "animations/idle.anim");
        AnimatorState walk = new AnimatorState("walk", "animations/walk.anim");
        AnimatorState attack = new AnimatorState("attack", "animations/attack.anim");

        controller.addState(idle);
        controller.addState(walk);
        controller.addState(attack);

        // Parameters
        controller.addParameter(new AnimatorParameter("isMoving", false));
        controller.addParameter(AnimatorParameter.trigger("attackTrigger"));

        // Transitions
        AnimatorTransition idleToWalk = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        idleToWalk.addCondition(new TransitionCondition("isMoving", true));
        controller.addTransition(idleToWalk);

        AnimatorTransition walkToIdle = new AnimatorTransition("walk", "idle", TransitionType.INSTANT);
        walkToIdle.addCondition(new TransitionCondition("isMoving", false));
        controller.addTransition(walkToIdle);

        // Any state to attack
        AnimatorTransition anyToAttack = new AnimatorTransition("*", "attack", TransitionType.INSTANT);
        anyToAttack.addCondition(new TransitionCondition("attackTrigger", true));
        controller.addTransition(anyToAttack);

        // Attack back to previous
        AnimatorTransition attackBack = new AnimatorTransition("attack", "*", TransitionType.WAIT_FOR_COMPLETION);
        controller.addTransition(attackBack);

        // Create player and state machine
        player = new AnimationPlayer();
        stateMachine = new AnimatorStateMachine(controller, player);
    }

    @Test
    void testInitialState() {
        assertEquals("idle", stateMachine.getCurrentState());
        assertNull(stateMachine.getPreviousState());
        assertEquals(Direction.DOWN, stateMachine.getCurrentDirection());
    }

    @Test
    void testSetBoolParameter() {
        assertFalse(stateMachine.getBool("isMoving"));

        stateMachine.setBool("isMoving", true);

        assertTrue(stateMachine.getBool("isMoving"));
    }

    @Test
    void testAutomaticTransition() {
        assertEquals("idle", stateMachine.getCurrentState());

        stateMachine.setBool("isMoving", true);
        stateMachine.update(0.016f);

        assertEquals("walk", stateMachine.getCurrentState());
        assertEquals("idle", stateMachine.getPreviousState());
    }

    @Test
    void testTransitionBackward() {
        // Start in idle
        stateMachine.setBool("isMoving", true);
        stateMachine.update(0.016f);
        assertEquals("walk", stateMachine.getCurrentState());

        // Go back to idle
        stateMachine.setBool("isMoving", false);
        stateMachine.update(0.016f);
        assertEquals("idle", stateMachine.getCurrentState());
    }

    @Test
    void testTriggerParameter() {
        assertEquals("idle", stateMachine.getCurrentState());

        stateMachine.setTrigger("attackTrigger");
        stateMachine.update(0.016f);

        assertEquals("attack", stateMachine.getCurrentState());

        // Trigger should be consumed
        assertFalse(stateMachine.getBool("attackTrigger"));
    }

    @Test
    void testFromAnyState() {
        // First go to walk state
        stateMachine.setBool("isMoving", true);
        stateMachine.update(0.016f);
        assertEquals("walk", stateMachine.getCurrentState());

        // Attack from walk (using any state transition)
        stateMachine.setTrigger("attackTrigger");
        stateMachine.update(0.016f);

        assertEquals("attack", stateMachine.getCurrentState());
        assertEquals("walk", stateMachine.getPreviousState());
    }

    @Test
    void testForceState() {
        stateMachine.forceState("walk");

        assertEquals("walk", stateMachine.getCurrentState());
    }

    @Test
    void testForceStateNonexistent() {
        stateMachine.forceState("nonexistent");

        assertEquals("idle", stateMachine.getCurrentState()); // Unchanged
    }

    @Test
    void testIsInState() {
        assertTrue(stateMachine.isInState("idle"));
        assertFalse(stateMachine.isInState("walk"));

        stateMachine.forceState("walk");

        assertFalse(stateMachine.isInState("idle"));
        assertTrue(stateMachine.isInState("walk"));
    }

    @Test
    void testSetDirection() {
        assertEquals(Direction.DOWN, stateMachine.getCurrentDirection());

        stateMachine.setDirection(Direction.UP);

        assertEquals(Direction.UP, stateMachine.getCurrentDirection());
    }

    @Test
    void testReset() {
        stateMachine.setBool("isMoving", true);
        stateMachine.update(0.016f);
        stateMachine.setDirection(Direction.LEFT);

        stateMachine.reset();

        assertEquals("idle", stateMachine.getCurrentState());
        assertNull(stateMachine.getPreviousState());
        assertFalse(stateMachine.getBool("isMoving")); // Reset to default
    }

    @Test
    void testNoTransitionWithoutConditions() {
        // Create transition with no conditions (manual trigger only)
        AnimatorTransition manualTrans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        // No conditions added

        AnimatorController simpleController = new AnimatorController("simple");
        simpleController.addState(new AnimatorState("idle", "idle.anim"));
        simpleController.addState(new AnimatorState("walk", "walk.anim"));
        simpleController.addTransition(manualTrans);

        AnimatorStateMachine sm = new AnimatorStateMachine(simpleController, new AnimationPlayer());

        sm.update(0.016f);

        assertEquals("idle", sm.getCurrentState()); // No automatic transition
    }

    @Test
    void testWaitForCompletionTransition() {
        // Create a controller with wait-for-completion transition
        AnimatorController ctrl = new AnimatorController("test");
        ctrl.addState(new AnimatorState("state1", "state1.anim"));
        ctrl.addState(new AnimatorState("state2", "state2.anim"));
        ctrl.addParameter(new AnimatorParameter("go", false));

        AnimatorTransition trans = new AnimatorTransition("state1", "state2", TransitionType.WAIT_FOR_COMPLETION);
        trans.addCondition(new TransitionCondition("go", true));
        ctrl.addTransition(trans);

        AnimationPlayer p = new AnimationPlayer();
        // Set up a non-looping animation that's still playing
        Animation anim = new Animation("test");
        anim.addFrame(new AnimationFrame("test.png", 1.0f));
        anim.setLooping(false);
        p.setAnimation(anim);

        AnimatorStateMachine sm = new AnimatorStateMachine(ctrl, p);

        sm.setBool("go", true);
        sm.update(0.016f);

        // Should not transition yet because animation isn't finished
        assertTrue(sm.hasPendingTransition());

        // Make animation finish
        p.update(1.5f);
        sm.update(0.016f);

        assertFalse(sm.hasPendingTransition());
        assertEquals("state2", sm.getCurrentState());
    }

    @Test
    void testCancelPendingTransition() {
        AnimatorController ctrl = new AnimatorController("test");
        ctrl.addState(new AnimatorState("state1", "state1.anim"));
        ctrl.addState(new AnimatorState("state2", "state2.anim"));
        ctrl.addParameter(new AnimatorParameter("go", false));

        AnimatorTransition trans = new AnimatorTransition("state1", "state2", TransitionType.WAIT_FOR_COMPLETION);
        trans.addCondition(new TransitionCondition("go", true));
        ctrl.addTransition(trans);

        AnimationPlayer p = new AnimationPlayer();
        Animation anim = new Animation("test");
        anim.addFrame(new AnimationFrame("test.png", 1.0f));
        anim.setLooping(false);
        p.setAnimation(anim);

        AnimatorStateMachine sm = new AnimatorStateMachine(ctrl, p);

        sm.setBool("go", true);
        sm.update(0.016f);
        assertTrue(sm.hasPendingTransition());

        sm.cancelPendingTransition();
        assertFalse(sm.hasPendingTransition());
    }

    @Test
    void testGetParameterValue() {
        assertFalse((Boolean) stateMachine.getParameterValue("isMoving"));

        stateMachine.setBool("isMoving", true);

        assertTrue((Boolean) stateMachine.getParameterValue("isMoving"));
    }

    @Test
    void testResetTrigger() {
        stateMachine.setTrigger("attackTrigger");
        assertTrue(stateMachine.getBool("attackTrigger"));

        stateMachine.resetTrigger("attackTrigger");
        assertFalse(stateMachine.getBool("attackTrigger"));
    }

    @Test
    void testGetPlayer() {
        assertSame(player, stateMachine.getPlayer());
    }
}
