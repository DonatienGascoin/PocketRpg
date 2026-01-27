package com.pocket.rpg.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GridMovementAnimator.
 * Note: These tests verify configuration and API without component refs.
 */
class GridMovementAnimatorTest {

    private GridMovementAnimator animator;

    @BeforeEach
    void setUp() {
        animator = new GridMovementAnimator();
    }

    @Test
    void testDefaultConfiguration() {
        assertEquals("isMoving", animator.getMovingParam());
        assertEquals("isSliding", animator.getSlidingParam());
        assertNull(animator.getJumpTrigger());
        assertTrue(animator.isSyncDirection());
    }

    @Test
    void testCustomMovingParam() {
        GridMovementAnimator custom = new GridMovementAnimator("walking");

        assertEquals("walking", custom.getMovingParam());
    }

    @Test
    void testSetMovingParam() {
        animator.setMovingParam("moving");

        assertEquals("moving", animator.getMovingParam());
    }

    @Test
    void testSetSlidingParam() {
        animator.setSlidingParam("sliding");

        assertEquals("sliding", animator.getSlidingParam());
    }

    @Test
    void testSetSlidingParamNull() {
        animator.setSlidingParam(null);

        assertNull(animator.getSlidingParam());
    }

    @Test
    void testSetJumpTrigger() {
        animator.setJumpTrigger("jump");

        assertEquals("jump", animator.getJumpTrigger());
    }

    @Test
    void testSetSyncDirection() {
        assertTrue(animator.isSyncDirection());

        animator.setSyncDirection(false);

        assertFalse(animator.isSyncDirection());
    }

    @Test
    void testUpdateWithNullRefs() {
        // Should not throw when component refs are null
        animator.update(0.016f);
    }

    @Test
    void testRefreshWithNullRefs() {
        // Should not throw when component refs are null
        animator.refresh();
    }
}
