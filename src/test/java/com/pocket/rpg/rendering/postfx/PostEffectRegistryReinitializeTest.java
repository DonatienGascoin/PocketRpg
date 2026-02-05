package com.pocket.rpg.rendering.postfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostEffectRegistry.reinitialize().
 * <p>
 * Integration tests that rely on actual classpath scanning.
 * Verify that reinitialize() clears and rebuilds without errors.
 */
class PostEffectRegistryReinitializeTest {

    @Test
    void reinitialize_rebuildsRegistry() {
        PostEffectRegistry.initialize();
        int initialCount = PostEffectRegistry.getAll().size();
        assertTrue(initialCount > 0, "Registry should find at least one effect on the classpath");

        PostEffectRegistry.reinitialize();
        int afterCount = PostEffectRegistry.getAll().size();

        assertEquals(initialCount, afterCount,
                "Reinitialize should find the same number of effects");
    }

    @Test
    void reinitialize_lookupBySimpleNameStillWorks() {
        PostEffectRegistry.initialize();

        var allEffects = PostEffectRegistry.getAll();
        assertFalse(allEffects.isEmpty());
        String simpleName = allEffects.getFirst().simpleName();

        PostEffectRegistry.reinitialize();

        PostEffectMeta found = PostEffectRegistry.getBySimpleName(simpleName);
        assertNotNull(found, "Should find effect by simple name after reinitialize");
        assertEquals(simpleName, found.simpleName());
    }

    @Test
    void reinitialize_instantiableEffectsPreserved() {
        PostEffectRegistry.initialize();
        int initialInstantiable = PostEffectRegistry.getInstantiable().size();

        PostEffectRegistry.reinitialize();
        int afterInstantiable = PostEffectRegistry.getInstantiable().size();

        assertEquals(initialInstantiable, afterInstantiable,
                "Reinitialize should preserve the same instantiable effects");
    }
}
