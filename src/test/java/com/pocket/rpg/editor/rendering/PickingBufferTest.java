package com.pocket.rpg.editor.rendering;

import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests entity ID encode/decode round-trip for GPU color picking.
 * These tests are headless (no OpenGL context required) — they verify the
 * encoding math that converts entity IDs to/from RGB float colors.
 */
class PickingBufferTest {

    /**
     * Simulates what the GPU does: clamp float to [0,1], multiply by 255, round.
     */
    private static int floatToByte(float value) {
        return Math.round(Math.clamp(value, 0f, 1f) * 255f);
    }

    /**
     * Encodes an entity ID to RGB floats, simulates GPU quantization, decodes back.
     */
    private static int roundTripEncodeDecode(int entityId) {
        Vector4f color = PickingBuffer.encodeEntityId(entityId);
        int r = floatToByte(color.x);
        int g = floatToByte(color.y);
        int b = floatToByte(color.z);
        return (r & 0xFF) | ((g & 0xFF) << 8) | ((b & 0xFF) << 16);
    }

    @Test
    void encodeDecodeRoundTrip_basicValues() {
        assertEquals(1, roundTripEncodeDecode(1));
        assertEquals(127, roundTripEncodeDecode(127));
        assertEquals(128, roundTripEncodeDecode(128));
        assertEquals(255, roundTripEncodeDecode(255));
    }

    @Test
    void encodeDecodeRoundTrip_multiByteValues() {
        assertEquals(256, roundTripEncodeDecode(256));
        assertEquals(65535, roundTripEncodeDecode(65535));
    }

    @Test
    void encodeDecodeRoundTrip_maxValue() {
        assertEquals(16777215, roundTripEncodeDecode(16777215));
    }

    @Test
    void encodeDecodeRoundTrip_allSingleByteValues() {
        for (int i = 1; i <= 255; i++) {
            assertEquals(i, roundTripEncodeDecode(i),
                    "Round-trip failed for entity ID " + i);
        }
    }

    @Test
    void encodeDecodeRoundTrip_selectedMultiByteValues() {
        int[] testValues = {256, 512, 1000, 4096, 32768, 65535, 65536,
                100000, 1000000, 8388608, 16777214, 16777215};
        for (int id : testValues) {
            assertEquals(id, roundTripEncodeDecode(id),
                    "Round-trip failed for entity ID " + id);
        }
    }

    @Test
    void encodeEntityId_alphaAlwaysOne() {
        Vector4f color = PickingBuffer.encodeEntityId(1);
        assertEquals(1.0f, color.w);

        color = PickingBuffer.encodeEntityId(16777215);
        assertEquals(1.0f, color.w);
    }

    @Test
    void encodeEntityId_zeroEncodesAsBlack() {
        // Entity ID 0 encodes to near-zero (the +0.5 centering means it's ~0.002)
        // But readback through GPU should produce byte 0
        Vector4f color = PickingBuffer.encodeEntityId(0);
        assertEquals(0, floatToByte(color.x));
        assertEquals(0, floatToByte(color.y));
        assertEquals(0, floatToByte(color.z));
    }
}
