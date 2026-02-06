package com.pocket.rpg.ui;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentKeyRegistryTest {

    @BeforeEach
    void setUp() {
        ComponentKeyRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        ComponentKeyRegistry.clear();
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    @Nested
    class Registration {

        @Test
        void register_storesComponent() {
            var comp = new StubComponent();
            ComponentKeyRegistry.register("test", comp);

            assertSame(comp, ComponentKeyRegistry.get("test"));
        }

        @Test
        void register_nullKey_ignored() {
            ComponentKeyRegistry.register(null, new StubComponent());
            assertEquals(0, ComponentKeyRegistry.getCount());
        }

        @Test
        void register_blankKey_ignored() {
            ComponentKeyRegistry.register("  ", new StubComponent());
            assertEquals(0, ComponentKeyRegistry.getCount());
        }

        @Test
        void register_overwritesExistingKey() {
            var first = new StubComponent();
            var second = new StubComponent();
            ComponentKeyRegistry.register("key", first);
            ComponentKeyRegistry.register("key", second);

            assertSame(second, ComponentKeyRegistry.get("key"));
            assertEquals(1, ComponentKeyRegistry.getCount());
        }

        @Test
        void unregister_removesComponent() {
            ComponentKeyRegistry.register("key", new StubComponent());
            ComponentKeyRegistry.unregister("key");

            assertNull(ComponentKeyRegistry.get("key"));
            assertEquals(0, ComponentKeyRegistry.getCount());
        }

        @Test
        void unregister_nullKey_noError() {
            assertDoesNotThrow(() -> ComponentKeyRegistry.unregister(null));
        }

        @Test
        void clear_removesAll() {
            ComponentKeyRegistry.register("a", new StubComponent());
            ComponentKeyRegistry.register("b", new StubComponent());
            ComponentKeyRegistry.clear();

            assertEquals(0, ComponentKeyRegistry.getCount());
        }
    }

    // ========================================================================
    // TYPED RETRIEVAL
    // ========================================================================

    @Nested
    class TypedRetrieval {

        @Test
        void get_withCorrectType_returnsComponent() {
            var text = new UIText();
            ComponentKeyRegistry.register("label", text);

            assertSame(text, ComponentKeyRegistry.get("label", UIText.class));
        }

        @Test
        void get_withWrongType_returnsNull() {
            ComponentKeyRegistry.register("key", new StubComponent());

            assertNull(ComponentKeyRegistry.get("key", UIText.class));
        }

        @Test
        void get_missingKey_returnsNull() {
            assertNull(ComponentKeyRegistry.get("missing", UIText.class));
        }

        @Test
        void get_untyped_returnsComponent() {
            var comp = new StubComponent();
            ComponentKeyRegistry.register("key", comp);

            assertSame(comp, ComponentKeyRegistry.get("key"));
        }

        @Test
        void getText_convenienceMethod() {
            var text = new UIText();
            ComponentKeyRegistry.register("score", text);

            assertSame(text, ComponentKeyRegistry.getText("score"));
        }
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    @Nested
    class Utility {

        @Test
        void exists_returnsTrueForRegisteredKey() {
            ComponentKeyRegistry.register("key", new StubComponent());
            assertTrue(ComponentKeyRegistry.exists("key"));
        }

        @Test
        void exists_returnsFalseForMissingKey() {
            assertFalse(ComponentKeyRegistry.exists("missing"));
        }

        @Test
        void getCount_tracksRegistrations() {
            assertEquals(0, ComponentKeyRegistry.getCount());
            ComponentKeyRegistry.register("a", new StubComponent());
            assertEquals(1, ComponentKeyRegistry.getCount());
            ComponentKeyRegistry.register("b", new StubComponent());
            assertEquals(2, ComponentKeyRegistry.getCount());
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static class StubComponent extends Component {}
}
