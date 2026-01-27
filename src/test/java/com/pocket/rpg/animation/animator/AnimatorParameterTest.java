package com.pocket.rpg.animation.animator;

import com.pocket.rpg.collision.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimatorParameterTest {

    @Test
    void testBoolParameter() {
        AnimatorParameter param = new AnimatorParameter("isMoving", true);

        assertEquals("isMoving", param.getName());
        assertEquals(ParameterType.BOOL, param.getType());
        assertEquals(true, param.getDefaultValue());
    }

    @Test
    void testDirectionParameter() {
        AnimatorParameter param = new AnimatorParameter("facing", Direction.UP);

        assertEquals("facing", param.getName());
        assertEquals(ParameterType.DIRECTION, param.getType());
        assertEquals(Direction.UP, param.getDefaultValue());
    }

    @Test
    void testTriggerParameter() {
        AnimatorParameter param = AnimatorParameter.trigger("attack");

        assertEquals("attack", param.getName());
        assertEquals(ParameterType.TRIGGER, param.getType());
        assertEquals(false, param.getDefaultValue());
    }

    @Test
    void testCopy() {
        AnimatorParameter original = new AnimatorParameter("test", Direction.LEFT);
        AnimatorParameter copy = original.copy();

        assertEquals(original.getName(), copy.getName());
        assertEquals(original.getType(), copy.getType());
        assertEquals(original.getDefaultValue(), copy.getDefaultValue());

        // Ensure it's a deep copy
        copy.setName("modified");
        assertNotEquals(original.getName(), copy.getName());
    }

    @Test
    void testEquals() {
        AnimatorParameter p1 = new AnimatorParameter("test", true);
        AnimatorParameter p2 = new AnimatorParameter("test", true);
        AnimatorParameter p3 = new AnimatorParameter("test", false);

        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
    }

    @Test
    void testDefaultConstructor() {
        AnimatorParameter param = new AnimatorParameter();

        assertEquals("", param.getName());
        assertEquals(ParameterType.BOOL, param.getType());
        assertEquals(false, param.getDefaultValue());
    }
}
