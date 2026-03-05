package com.pocket.rpg.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pocket.rpg.rendering.resources.Texture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpriteMetadataFilterModeTest {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void filterModeSerializesAndDeserializes() {
        SpriteMetadata meta = new SpriteMetadata();
        meta.filterMode = Texture.FilterMode.LINEAR;

        String json = gson.toJson(meta);
        assertTrue(json.contains("\"filterMode\": \"LINEAR\""));

        SpriteMetadata restored = gson.fromJson(json, SpriteMetadata.class);
        assertEquals(Texture.FilterMode.LINEAR, restored.filterMode);
    }

    @Test
    void missingFilterModeDeserializesAsNull() {
        String json = """
                {
                  "pivotX": 0.5,
                  "pivotY": 0.0
                }
                """;

        SpriteMetadata meta = gson.fromJson(json, SpriteMetadata.class);
        assertNull(meta.filterMode);
    }

    @Test
    void nullFilterModeDefaultsToNearest() {
        SpriteMetadata meta = new SpriteMetadata();
        assertNull(meta.filterMode);

        // Null means NEAREST by convention
        Texture.FilterMode effective = meta.filterMode != null ? meta.filterMode : Texture.FilterMode.NEAREST;
        assertEquals(Texture.FilterMode.NEAREST, effective);
    }

    @Test
    void isEmptyReturnsTrueWhenNoFilterMode() {
        SpriteMetadata meta = new SpriteMetadata();
        assertTrue(meta.isEmpty());
    }

    @Test
    void isEmptyReturnsFalseWhenFilterModeSet() {
        SpriteMetadata meta = new SpriteMetadata();
        meta.filterMode = Texture.FilterMode.LINEAR;
        assertFalse(meta.isEmpty());
    }

    @Test
    void isEmptyReturnsFalseForMultipleModeWithFilterMode() {
        SpriteMetadata meta = new SpriteMetadata();
        meta.spriteMode = SpriteMetadata.SpriteMode.MULTIPLE;
        meta.filterMode = Texture.FilterMode.LINEAR;
        assertFalse(meta.isEmpty());
    }

    @Test
    void nearestFilterModeSerializesCorrectly() {
        SpriteMetadata meta = new SpriteMetadata();
        meta.filterMode = Texture.FilterMode.NEAREST;

        String json = gson.toJson(meta);
        SpriteMetadata restored = gson.fromJson(json, SpriteMetadata.class);
        assertEquals(Texture.FilterMode.NEAREST, restored.filterMode);
    }
}
