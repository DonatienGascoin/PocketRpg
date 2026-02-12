package com.pocket.rpg.dialogue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DialogueVariableResolverTest {

    private DialogueVariableResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DialogueVariableResolver();
    }

    @Test
    void noRegistrations() {
        Map<String, String> result = resolver.resolveAutoVariables();
        assertTrue(result.isEmpty());
    }

    @Test
    void singleSupplier() {
        resolver.register("PLAYER_NAME", () -> "Red");
        Map<String, String> result = resolver.resolveAutoVariables();

        assertEquals(1, result.size());
        assertEquals("Red", result.get("PLAYER_NAME"));
    }

    @Test
    void multipleSuppliers() {
        resolver.register("PLAYER_NAME", () -> "Red");
        resolver.register("MONEY", () -> "5000");
        resolver.register("BADGE_COUNT", () -> "3");

        Map<String, String> result = resolver.resolveAutoVariables();

        assertEquals(3, result.size());
        assertEquals("Red", result.get("PLAYER_NAME"));
        assertEquals("5000", result.get("MONEY"));
        assertEquals("3", result.get("BADGE_COUNT"));
    }

    @Test
    void nullSupplierValueExcluded() {
        resolver.register("PLAYER_NAME", () -> "Red");
        resolver.register("MISSING", () -> null);

        Map<String, String> result = resolver.resolveAutoVariables();

        assertEquals(1, result.size());
        assertEquals("Red", result.get("PLAYER_NAME"));
        assertFalse(result.containsKey("MISSING"));
    }

    @Test
    void suppliersEvaluatedFreshEachCall() {
        int[] counter = {0};
        resolver.register("COUNTER", () -> String.valueOf(++counter[0]));

        assertEquals("1", resolver.resolveAutoVariables().get("COUNTER"));
        assertEquals("2", resolver.resolveAutoVariables().get("COUNTER"));
        assertEquals("3", resolver.resolveAutoVariables().get("COUNTER"));
    }

    @Test
    void laterRegistrationOverridesPrevious() {
        resolver.register("PLAYER_NAME", () -> "Red");
        resolver.register("PLAYER_NAME", () -> "Blue");

        Map<String, String> result = resolver.resolveAutoVariables();
        assertEquals("Blue", result.get("PLAYER_NAME"));
    }
}
