package com.pocket.rpg.config;

import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TransitionConfig.
 * Tests configuration validation, copy constructor, and builder pattern.
 */
class TransitionConfigTest {

    @Test
    @DisplayName("Builder creates valid config with defaults")
    void testBuilderDefaults() {
        TransitionConfig config = TransitionConfig.builder().build();

        assertNotNull(config);
        assertEquals(0.5f, config.getFadeOutDuration());
        assertEquals(0.5f, config.getFadeInDuration());
        assertNotNull(config.getFadeColor());
        assertEquals(0, config.getFadeColor().x);
        assertEquals(0, config.getFadeColor().y);
        assertEquals(0, config.getFadeColor().z);
        assertEquals(1, config.getFadeColor().w);
        assertEquals("", config.getTransitionText());
        assertEquals(TransitionConfig.TransitionType.FADE, config.getType());
    }

    @Test
    @DisplayName("Builder creates valid config with custom values")
    void testBuilderCustomValues() {
        Vector4f customColor = new Vector4f(1, 0, 0, 1);
        TransitionConfig config = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(2.0f)
                .fadeColor(customColor)
                .transitionText("Loading...")
                .type(TransitionConfig.TransitionType.FADE_WITH_TEXT)
                .build();

        assertEquals(1.0f, config.getFadeOutDuration());
        assertEquals(2.0f, config.getFadeInDuration());
        assertEquals(customColor, config.getFadeColor());
        assertEquals("Loading...", config.getTransitionText());
        assertEquals(TransitionConfig.TransitionType.FADE_WITH_TEXT, config.getType());
    }

    @Test
    @DisplayName("Copy constructor creates independent copy")
    void testCopyConstructor() {
        Vector4f originalColor = new Vector4f(1, 0, 0, 1);
        TransitionConfig original = TransitionConfig.builder()
                .fadeOutDuration(1.0f)
                .fadeInDuration(2.0f)
                .fadeColor(originalColor)
                .transitionText("Original")
                .type(TransitionConfig.TransitionType.FADE_WITH_TEXT)
                .build();

        TransitionConfig copy = new TransitionConfig(original);

        // Values should be equal
        assertEquals(original.getFadeOutDuration(), copy.getFadeOutDuration());
        assertEquals(original.getFadeInDuration(), copy.getFadeInDuration());
        assertEquals(original.getTransitionText(), copy.getTransitionText());
        assertEquals(original.getType(), copy.getType());

        // Color values should be equal
        assertEquals(original.getFadeColor().x, copy.getFadeColor().x);
        assertEquals(original.getFadeColor().y, copy.getFadeColor().y);
        assertEquals(original.getFadeColor().z, copy.getFadeColor().z);
        assertEquals(original.getFadeColor().w, copy.getFadeColor().w);

        // But color should be a different object (defensive copy)
        assertNotSame(original.getFadeColor(), copy.getFadeColor());

        // Modifying copy shouldn't affect original
        copy.setFadeOutDuration(5.0f);
        copy.getFadeColor().set(0, 1, 0, 1);

        assertEquals(1.0f, original.getFadeOutDuration());
        assertEquals(1, original.getFadeColor().x);
        assertEquals(0, original.getFadeColor().y);
    }

    @Test
    @DisplayName("getTotalDuration returns sum of fade durations")
    void testGetTotalDuration() {
        TransitionConfig config = TransitionConfig.builder()
                .fadeOutDuration(0.5f)
                .fadeInDuration(0.3f)
                .build();

        assertEquals(0.8f, config.getTotalDuration(), 0.001f);
    }

    @Test
    @DisplayName("validate accepts valid configuration")
    void testValidateAcceptsValidConfig() {
        TransitionConfig config = TransitionConfig.builder()
                .fadeOutDuration(0.5f)
                .fadeInDuration(0.5f)
                .fadeColor(new Vector4f(0, 0, 0, 1))
                .type(TransitionConfig.TransitionType.FADE)
                .build();

        assertDoesNotThrow(config::validate);
    }

    @Test
    @DisplayName("validate accepts zero durations")
    void testValidateAcceptsZeroDurations() {
        TransitionConfig config = TransitionConfig.builder()
                .fadeOutDuration(0.0f)
                .fadeInDuration(0.0f)
                .build();

        assertDoesNotThrow(config::validate);
    }

    @Test
    @DisplayName("validate rejects negative fadeOutDuration")
    void testValidateRejectsNegativeFadeOut() {
        TransitionConfig config = TransitionConfig.builder()
                .fadeOutDuration(-0.5f)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
        );
        assertTrue(exception.getMessage().contains("fadeOutDuration"));
    }

    @Test
    @DisplayName("validate rejects negative fadeInDuration")
    void testValidateRejectsNegativeFadeIn() {
        TransitionConfig config = TransitionConfig.builder()
                .fadeInDuration(-0.5f)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
        );
        assertTrue(exception.getMessage().contains("fadeInDuration"));
    }

    @Test
    @DisplayName("validate rejects null fadeColor")
    void testValidateRejectsNullColor() {
        TransitionConfig config = TransitionConfig.builder()
                .fadeColor(null)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
        );
        assertTrue(exception.getMessage().contains("fadeColor"));
    }

    @Test
    @DisplayName("validate rejects null type")
    void testValidateRejectsNullType() {
        TransitionConfig config = TransitionConfig.builder()
                .type(null)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
        );
        assertTrue(exception.getMessage().contains("type"));
    }

    @Test
    @DisplayName("No-args constructor creates valid instance")
    void testNoArgsConstructor() {
        TransitionConfig config = new TransitionConfig();

        assertNotNull(config);
        // Lombok @NoArgsConstructor doesn't set @Builder.Default values
        // so we just verify it doesn't crash
    }

    @Test
    @DisplayName("All-args constructor sets all fields")
    void testAllArgsConstructor() {
        Vector4f color = new Vector4f(1, 0, 0, 1);
        TransitionConfig config = new TransitionConfig(
                1.0f,
                2.0f,
                color,
                "Loading",
                TransitionConfig.TransitionType.FADE_WITH_TEXT
        );

        assertEquals(1.0f, config.getFadeOutDuration());
        assertEquals(2.0f, config.getFadeInDuration());
        assertEquals(color, config.getFadeColor());
        assertEquals("Loading", config.getTransitionText());
        assertEquals(TransitionConfig.TransitionType.FADE_WITH_TEXT, config.getType());
    }

    @Test
    @DisplayName("Different transition types are distinct")
    void testTransitionTypes() {
        assertNotEquals(
                TransitionConfig.TransitionType.FADE,
                TransitionConfig.TransitionType.FADE_WITH_TEXT
        );
    }

    @Test
    @DisplayName("Empty transition text is valid")
    void testEmptyTransitionText() {
        TransitionConfig config = TransitionConfig.builder()
                .transitionText("")
                .build();

        assertDoesNotThrow(config::validate);
        assertEquals("", config.getTransitionText());
    }

    @Test
    @DisplayName("Null transition text is allowed by builder")
    void testNullTransitionText() {
        TransitionConfig config = TransitionConfig.builder()
                .transitionText(null)
                .build();

        // Null is allowed - the implementation should handle it gracefully
        // This test just verifies no exception is thrown during creation
        assertDoesNotThrow(config::validate);
    }
}