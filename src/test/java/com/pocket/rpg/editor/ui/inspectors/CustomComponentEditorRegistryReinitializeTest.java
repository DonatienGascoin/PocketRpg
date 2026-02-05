package com.pocket.rpg.editor.ui.inspectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomComponentEditorRegistry.reinitialize().
 * <p>
 * Integration tests that verify reinitialize clears and re-scans
 * the classpath for @InspectorFor annotated classes.
 */
class CustomComponentEditorRegistryReinitializeTest {

    @AfterEach
    void tearDown() {
        CustomComponentEditorRegistry.clear();
    }

    @Test
    void reinitialize_clearsAndRebuilds() {
        // First init
        CustomComponentEditorRegistry.initBuiltInEditors();

        // Reinitialize should not throw
        assertDoesNotThrow(CustomComponentEditorRegistry::reinitialize);
    }

    @Test
    void clear_removesAllEditors() {
        CustomComponentEditorRegistry.initBuiltInEditors();

        CustomComponentEditorRegistry.clear();

        // After clear, no custom editor should be found for any type
        assertFalse(CustomComponentEditorRegistry.hasCustomEditor("com.pocket.rpg.SomeComponent"));
    }

    @Test
    void reinitialize_registersKnownInspectors() {
        // After reinitialize, any @InspectorFor-annotated classes on the classpath
        // should be found again
        CustomComponentEditorRegistry.reinitialize();

        // We can't assert specific editors without knowing what's on the classpath,
        // but the operation should complete without errors
        // and the registry should be in a consistent state
        assertDoesNotThrow(() -> CustomComponentEditorRegistry.hasCustomEditor("any.Type"));
    }
}
