package com.pocket.rpg.animation.animator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimatorTransitionTest {

    @Test
    void testSimpleTransition() {
        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);

        assertEquals("idle", trans.getFrom());
        assertEquals("walk", trans.getTo());
        assertEquals(TransitionType.INSTANT, trans.getType());
        assertFalse(trans.hasConditions());
    }

    @Test
    void testTransitionWithConditions() {
        List<TransitionCondition> conditions = List.of(
            new TransitionCondition("isMoving", true)
        );
        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT, conditions);

        assertTrue(trans.hasConditions());
        assertEquals(1, trans.getConditions().size());
        assertEquals("isMoving", trans.getCondition(0).getParameter());
    }

    @Test
    void testAddCondition() {
        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);

        trans.addCondition(new TransitionCondition("isMoving", true));
        trans.addCondition(new TransitionCondition("speed", 5));

        assertEquals(2, trans.getConditions().size());
    }

    @Test
    void testRemoveCondition() {
        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        trans.addCondition(new TransitionCondition("a", true));
        trans.addCondition(new TransitionCondition("b", true));

        trans.removeCondition(0);

        assertEquals(1, trans.getConditions().size());
        assertEquals("b", trans.getCondition(0).getParameter());
    }

    @Test
    void testAnyStateWildcard() {
        AnimatorTransition fromAny = new AnimatorTransition("*", "attack", TransitionType.INSTANT);
        assertTrue(fromAny.isFromAnyState());
        assertFalse(fromAny.isToPreviousState());

        AnimatorTransition toPrevious = new AnimatorTransition("attack", "*", TransitionType.WAIT_FOR_COMPLETION);
        assertFalse(toPrevious.isFromAnyState());
        assertTrue(toPrevious.isToPreviousState());
    }

    @Test
    void testCopy() {
        AnimatorTransition original = new AnimatorTransition("idle", "walk", TransitionType.WAIT_FOR_COMPLETION);
        original.addCondition(new TransitionCondition("test", true));

        AnimatorTransition copy = original.copy();

        assertEquals(original.getFrom(), copy.getFrom());
        assertEquals(original.getTo(), copy.getTo());
        assertEquals(original.getType(), copy.getType());
        assertEquals(original.getConditions().size(), copy.getConditions().size());

        // Ensure deep copy of conditions
        copy.getCondition(0).setParameter("modified");
        assertNotEquals(original.getCondition(0).getParameter(), copy.getCondition(0).getParameter());
    }

    @Test
    void testTransitionTypes() {
        AnimatorTransition instant = new AnimatorTransition("a", "b", TransitionType.INSTANT);
        AnimatorTransition waitCompletion = new AnimatorTransition("a", "b", TransitionType.WAIT_FOR_COMPLETION);
        AnimatorTransition waitLoop = new AnimatorTransition("a", "b", TransitionType.WAIT_FOR_LOOP);

        assertEquals(TransitionType.INSTANT, instant.getType());
        assertEquals(TransitionType.WAIT_FOR_COMPLETION, waitCompletion.getType());
        assertEquals(TransitionType.WAIT_FOR_LOOP, waitLoop.getType());
    }

    @Test
    void testToString() {
        AnimatorTransition trans = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        String str = trans.toString();

        assertTrue(str.contains("idle"));
        assertTrue(str.contains("walk"));
        assertTrue(str.contains("INSTANT"));
    }
}
