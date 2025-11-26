package com.pocket.rpg.resources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceHandleTest {

    private ResourceHandle<String> handle;

    @BeforeEach
    void setUp() {
        handle = new ResourceHandle<>("test-resource");
    }

    @Test
    void testInitialState() {
        assertEquals("test-resource", handle.getResourceId());
        assertEquals(ResourceState.UNLOADED, handle.getState());
        assertEquals(0, handle.getRefCount());
        assertFalse(handle.isRetained());
    }

    @Test
    void testSetData() {
        handle.setData("test-data");

        assertEquals("test-data", handle.get());
        assertEquals(ResourceState.READY, handle.getState());
        assertTrue(handle.isReady());
    }

    @Test
    void testRetainRelease() {
        assertEquals(0, handle.getRefCount());

        handle.retain();
        assertEquals(1, handle.getRefCount());

        handle.retain();
        assertEquals(2, handle.getRefCount());

        handle.release();
        assertEquals(1, handle.getRefCount());

        handle.release();
        assertEquals(0, handle.getRefCount());
    }

    @Test
    void testMarkRetained() {
        assertFalse(handle.isRetained());

        handle.markRetained();
        assertTrue(handle.isRetained());

        handle.unmarkRetained();
        assertFalse(handle.isRetained());
    }

    @Test
    void testIsReady() {
        assertFalse(handle.isReady());

        handle.setData("data");
        assertTrue(handle.isReady());
    }

    @Test
    void testSetError() {
        Exception error = new Exception("Test error");
        handle.setError(error);

        assertEquals(ResourceState.FAILED, handle.getState());
        assertSame(error, handle.getLoadError());
        assertTrue(handle.isFailed());
    }

    @Test
    void testOnReadyCallback() {
        final boolean[] called = {false};

        handle.onReady(h -> called[0] = true);

        assertFalse(called[0]);

        handle.setData("data");

        assertTrue(called[0]);
    }
}
