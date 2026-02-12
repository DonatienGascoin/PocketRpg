package com.pocket.rpg.dialogue;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VariableMergeTest {

    @Test
    void autoOnly() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(
                Map.of("PLAYER_NAME", "Red", "MONEY", "5000"),
                null,
                null
        );

        assertEquals(2, result.size());
        assertEquals("Red", result.get("PLAYER_NAME"));
        assertEquals("5000", result.get("MONEY"));
    }

    @Test
    void staticOverridesAuto() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(
                Map.of("PLAYER_NAME", "AutoRed"),
                Map.of("PLAYER_NAME", "StaticBlue"),
                null
        );

        assertEquals("StaticBlue", result.get("PLAYER_NAME"));
    }

    @Test
    void runtimeOverridesStatic() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(
                null,
                Map.of("POKEMON_NAME", "StaticPikachu"),
                Map.of("POKEMON_NAME", "RuntimeCharizard")
        );

        assertEquals("RuntimeCharizard", result.get("POKEMON_NAME"));
    }

    @Test
    void runtimeOverridesAuto() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(
                Map.of("PLAYER_NAME", "AutoRed"),
                null,
                Map.of("PLAYER_NAME", "RuntimeBlue")
        );

        assertEquals("RuntimeBlue", result.get("PLAYER_NAME"));
    }

    @Test
    void fullMergeOrder() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(
                Map.of("SHARED", "auto", "AUTO_ONLY", "auto_val"),
                Map.of("SHARED", "static", "STATIC_ONLY", "static_val"),
                Map.of("SHARED", "runtime", "RUNTIME_ONLY", "runtime_val")
        );

        assertEquals("runtime", result.get("SHARED"));
        assertEquals("auto_val", result.get("AUTO_ONLY"));
        assertEquals("static_val", result.get("STATIC_ONLY"));
        assertEquals("runtime_val", result.get("RUNTIME_ONLY"));
        assertEquals(4, result.size());
    }

    @Test
    void allNullLayers() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(null, null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyMapsAtEachLayer() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(
                Map.of(),
                Map.of(),
                Map.of()
        );
        assertTrue(result.isEmpty());
    }

    @Test
    void staticAddsNewKeys() {
        Map<String, String> result = DialogueVariableResolver.mergeVariables(
                Map.of("AUTO", "a"),
                Map.of("STATIC", "s"),
                null
        );

        assertEquals(2, result.size());
        assertEquals("a", result.get("AUTO"));
        assertEquals("s", result.get("STATIC"));
    }
}
