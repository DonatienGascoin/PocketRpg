package com.pocket.rpg.serialization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ComponentRegistry.reinitialize().
 * <p>
 * These are integration tests that rely on actual classpath scanning
 * via Reflections. They verify that reinitialize() clears and rebuilds
 * the registry without errors.
 */
class ComponentRegistryReinitializeTest {

    @Test
    void reinitialize_rebuildsRegistry() {
        // First init
        ComponentRegistry.initialize();
        int initialCount = ComponentRegistry.getAll().size();
        assertTrue(initialCount > 0, "Registry should find at least one component on the classpath");

        // Reinitialize
        ComponentRegistry.reinitialize();
        int afterCount = ComponentRegistry.getAll().size();

        assertEquals(initialCount, afterCount,
                "Reinitialize should find the same number of components");
    }

    @Test
    void reinitialize_categoriesAreRebuilt() {
        ComponentRegistry.initialize();
        int initialCategories = ComponentRegistry.getCategories().size();

        ComponentRegistry.reinitialize();
        int afterCategories = ComponentRegistry.getCategories().size();

        assertEquals(initialCategories, afterCategories,
                "Reinitialize should rebuild the same categories");
    }

    @Test
    void reinitialize_lookupBySimpleNameStillWorks() {
        ComponentRegistry.initialize();

        // Pick the first component
        var allComponents = ComponentRegistry.getAll();
        assertFalse(allComponents.isEmpty());
        String simpleName = allComponents.getFirst().simpleName();

        ComponentRegistry.reinitialize();

        ComponentMeta found = ComponentRegistry.getBySimpleName(simpleName);
        assertNotNull(found, "Should find component by simple name after reinitialize");
        assertEquals(simpleName, found.simpleName());
    }
}
