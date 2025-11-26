package com.pocket.rpg.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceStateTest {

    @Test
    void testAllStates() {
        assertNotNull(ResourceState.UNLOADED);
        assertNotNull(ResourceState.LOADING);
        assertNotNull(ResourceState.READY);
        assertNotNull(ResourceState.FAILED);
        assertNotNull(ResourceState.EVICTED);
    }

    @Test
    void testStateCount() {
        assertEquals(5, ResourceState.values().length);
    }
}
