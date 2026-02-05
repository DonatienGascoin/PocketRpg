package com.pocket.rpg.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.serialization.custom.ComponentTypeAdapterFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for resilient component deserialization with fallback resolution.
 */
class ComponentFallbackResolutionTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        ComponentRegistry.initialize();
        ComponentRegistry.resetFallbackTracking();

        // Create Gson with ComponentTypeAdapterFactory using a minimal stub AssetContext
        AssetContext stubContext = new StubAssetContext();
        gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(Component.class, new ComponentTypeAdapterFactory(stubContext).create(
                        new GsonBuilder().create(),
                        com.google.gson.reflect.TypeToken.get(Component.class)))
                .create();
    }

    /** Minimal AssetContext stub for tests that don't need asset loading. */
    private static class StubAssetContext implements AssetContext {
        @Override public <T> T load(String path) { return null; }
        @Override public <T> T load(String path, com.pocket.rpg.resources.LoadOptions loadOptions) { return null; }
        @Override public <T> T load(String path, Class<T> type) { return null; }
        @Override public <T> T load(String path, com.pocket.rpg.resources.LoadOptions loadOptions, Class<T> type) { return null; }
        @Override public <T> T get(String path) { return null; }
        @Override public <T> java.util.List<T> getAll(Class<T> type) { return java.util.Collections.emptyList(); }
        @Override public boolean isLoaded(String path) { return false; }
        @Override public java.util.Set<String> getLoadedPaths() { return java.util.Collections.emptySet(); }
        @Override public String getPathForResource(Object resource) { return null; }
        @Override public void persist(Object resource) {}
        @Override public void persist(Object resource, String path) {}
        @Override public void persist(Object resource, String path, com.pocket.rpg.resources.LoadOptions options) {}
        @Override public com.pocket.rpg.resources.AssetsConfiguration configure() { return null; }
        @Override public com.pocket.rpg.resources.CacheStats getStats() { return null; }
        @Override public java.util.List<String> scanByType(Class<?> type) { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanByType(Class<?> type, String directory) { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanAll() { return java.util.Collections.emptyList(); }
        @Override public java.util.List<String> scanAll(String directory) { return java.util.Collections.emptyList(); }
        @Override public void setAssetRoot(String assetRoot) {}
        @Override public String getAssetRoot() { return null; }
        @Override public com.pocket.rpg.resources.ResourceCache getCache() { return null; }
        @Override public void setErrorMode(com.pocket.rpg.resources.ErrorMode errorMode) {}
        @Override public void setStatisticsEnabled(boolean enableStatistics) {}
        @Override public String getRelativePath(String fullPath) { return null; }
        @Override public com.pocket.rpg.rendering.resources.Sprite getPreviewSprite(String path, Class<?> type) { return null; }
        @Override public Class<?> getTypeForPath(String path) { return null; }
        @Override public void registerResource(Object resource, String path) {}
        @Override public void unregisterResource(Object resource) {}
        @Override public boolean isAssetType(Class<?> type) { return false; }
        @Override public boolean canInstantiate(Class<?> type) { return false; }
        @Override public com.pocket.rpg.editor.scene.EditorGameObject instantiate(String path, Class<?> type, org.joml.Vector3f position) { return null; }
        @Override public com.pocket.rpg.editor.EditorPanelType getEditorPanelType(Class<?> type) { return null; }
        @Override public java.util.Set<com.pocket.rpg.resources.EditorCapability> getEditorCapabilities(Class<?> type) { return java.util.Collections.emptySet(); }
        @Override public String getIconCodepoint(Class<?> type) { return null; }
    }

    // ========================================================================
    // FALLBACK TRACKING
    // ========================================================================

    @Nested
    class FallbackTrackingTests {

        @Test
        void trackingStartsEmpty() {
            assertFalse(ComponentRegistry.wasFallbackUsed());
            assertTrue(ComponentRegistry.getFallbackResolutions().isEmpty());
        }

        @Test
        void recordAndRetrieveResolution() {
            ComponentRegistry.recordFallbackResolution(
                    "com.old.OldComponent",
                    "com.pocket.rpg.components.NewComponent"
            );

            assertTrue(ComponentRegistry.wasFallbackUsed());
            List<String> resolutions = ComponentRegistry.getFallbackResolutions();
            assertEquals(1, resolutions.size());
            assertEquals("com.old.OldComponent|com.pocket.rpg.components.NewComponent", resolutions.get(0));
        }

        @Test
        void deduplicatesSameResolution() {
            // Record same resolution multiple times (e.g., 50 entities with same stale Transform)
            ComponentRegistry.recordFallbackResolution("com.old.Transform", "com.pocket.rpg.components.core.Transform");
            ComponentRegistry.recordFallbackResolution("com.old.Transform", "com.pocket.rpg.components.core.Transform");
            ComponentRegistry.recordFallbackResolution("com.old.Transform", "com.pocket.rpg.components.core.Transform");

            List<String> resolutions = ComponentRegistry.getFallbackResolutions();
            assertEquals(1, resolutions.size(), "Same resolution should be deduplicated");
        }

        @Test
        void resetClearsTracking() {
            ComponentRegistry.recordFallbackResolution("com.old.A", "com.new.A");
            ComponentRegistry.recordFallbackResolution("com.old.B", "com.new.B");
            assertTrue(ComponentRegistry.wasFallbackUsed());

            ComponentRegistry.resetFallbackTracking();

            assertFalse(ComponentRegistry.wasFallbackUsed());
            assertTrue(ComponentRegistry.getFallbackResolutions().isEmpty());
        }
    }

    // ========================================================================
    // MIGRATION MAP
    // ========================================================================

    @Nested
    class MigrationMapTests {

        @Test
        void getMigrationReturnsNullForUnknownClass() {
            String result = ComponentRegistry.getMigration("com.unknown.NonExistentComponent");
            assertNull(result);
        }

        // Note: Testing actual migrations would require adding test entries to the static block,
        // which isn't practical. The mechanism is simple (HashMap lookup) and implicitly tested
        // by the deserialization integration test if migrations were configured.
    }

    // ========================================================================
    // SIMPLE NAME FALLBACK DESERIALIZATION
    // ========================================================================

    @Nested
    class SimpleNameFallbackDeserializationTests {

        @Test
        void deserializesComponentWithWrongPackage() {
            // JSON with fake package path - should resolve via simple name "Transform"
            // Note: Property deserialization is tested extensively elsewhere; this test
            // focuses on the fallback resolution mechanism itself.
            String json = """
                {
                    "type": "com.pocket.rpg.components.fake.subpackage.Transform",
                    "properties": {
                        "localPosition": {"x": 0.0, "y": 0.0, "z": 0.0},
                        "localRotation": {"x": 0.0, "y": 0.0, "z": 0.0},
                        "localScale": {"x": 1.0, "y": 1.0, "z": 1.0}
                    }
                }
                """;

            ComponentRegistry.resetFallbackTracking();

            Component component = gson.fromJson(json, Component.class);

            // Verify component resolved correctly despite wrong package
            assertNotNull(component, "Should deserialize despite wrong package");
            assertInstanceOf(Transform.class, component);

            // Verify fallback was tracked
            assertTrue(ComponentRegistry.wasFallbackUsed());
            List<String> resolutions = ComponentRegistry.getFallbackResolutions();
            assertEquals(1, resolutions.size());
            assertTrue(resolutions.get(0).contains("fake.subpackage.Transform"));
            assertTrue(resolutions.get(0).contains("com.pocket.rpg.components.core.Transform"));
        }

        @Test
        void throwsHelpfulExceptionForUnknownComponent() {
            // JSON with completely unknown component (no simple name match)
            String json = """
                {
                    "type": "com.unknown.CompletelyFakeComponent",
                    "properties": {}
                }
                """;

            JsonParseException exception = assertThrows(
                    JsonParseException.class,
                    () -> gson.fromJson(json, Component.class)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("CompletelyFakeComponent"),
                    "Exception should mention the unknown component name");
            assertTrue(message.contains("addMigration"),
                    "Exception should hint about adding a migration");
        }
    }
}
