package com.pocket.rpg.editor.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FuzzyMatcherTest {

    @Test
    void containsMatch() {
        assertTrue(FuzzyMatcher.matches("sprite", "Sprite Renderer"));
    }

    @Test
    void wordPrefixMatch() {
        assertTrue(FuzzyMatcher.matches("sprite r", "Sprite Renderer"));
    }

    @Test
    void wordPrefixMatchPartial() {
        assertTrue(FuzzyMatcher.matches("spr ren", "Sprite Renderer"));
    }

    @Test
    void subsequenceMatch() {
        assertTrue(FuzzyMatcher.matches("spritr", "Sprite Renderer"));
    }

    @Test
    void noMatch() {
        assertFalse(FuzzyMatcher.matches("xyz", "Sprite Renderer"));
    }

    @Test
    void caseInsensitive() {
        assertTrue(FuzzyMatcher.matches("SPRITE", "sprite renderer"));
    }

    @Test
    void exactMatch() {
        assertTrue(FuzzyMatcher.matches("Sprite Renderer", "Sprite Renderer"));
    }

    @Test
    void emptyQuery() {
        assertTrue(FuzzyMatcher.matches("", "Sprite Renderer"));
    }

    @Test
    void subsequenceWithGaps() {
        assertTrue(FuzzyMatcher.matches("srr", "Sprite Renderer"));
    }

    @Test
    void wordPrefixNotInOrder() {
        // "renderer sprite" should still match because each word is found
        assertTrue(FuzzyMatcher.matches("ren spr", "Sprite Renderer"));
    }

    @Test
    void wordPrefixPartialNoMatch() {
        // "spritex" doesn't match any word prefix
        assertFalse(FuzzyMatcher.matches("spritex r", "Sprite Renderer"));
    }
}
