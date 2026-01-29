package com.pocket.rpg.rendering.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpriteBatch Y-position sorting direction.
 * In a top-down RPG with OpenGL's Y-up coordinate system, objects with higher Y
 * (further from camera) should render first (behind), and objects with lower Y
 * (closer to camera) should render last (in front).
 */
class SpriteBatchSortingTest {

    /**
     * Helper to create a RenderableQuad with only the fields relevant to sorting.
     */
    private static SpriteBatch.RenderableQuad quad(int textureId, float zIndex, float yPosition) {
        return new SpriteBatch.RenderableQuad(
                textureId,
                0f, 0f,           // x, y world position
                1f, 1f,           // width, height
                0f,               // rotation
                0.5f, 0.5f,       // originX, originY
                0f, 0f, 1f, 1f,   // u0, v0, u1, v1
                zIndex,
                yPosition,
                1f, 1f, 1f, 1f    // r, g, b, a
        );
    }

    @Nested
    @DisplayName("TEXTURE_PRIORITY strategy")
    class TexturePriority {

        private final SpriteBatch batch = new SpriteBatch(SpriteBatch.SortingStrategy.TEXTURE_PRIORITY);

        @Test
        @DisplayName("Same zIndex, different Y — lower Y renders in front (sorted last)")
        void lowerYSortsLast() {
            var quads = new ArrayList<>(List.of(
                    quad(1, 0f, 5f),   // higher Y → behind
                    quad(1, 0f, 2f)    // lower Y → in front
            ));

            batch.sortQuads(quads);

            assertEquals(5f, quads.get(0).yPosition(), "Higher Y should be first (behind)");
            assertEquals(2f, quads.get(1).yPosition(), "Lower Y should be last (in front)");
        }

        @Test
        @DisplayName("Different zIndex — higher zIndex renders in front regardless of Y")
        void zIndexOverridesY() {
            var quads = new ArrayList<>(List.of(
                    quad(1, 0f, 2f),   // low Z, low Y
                    quad(1, 5f, 10f)   // high Z, high Y
            ));

            batch.sortQuads(quads);

            assertEquals(0f, quads.get(0).zIndex(), "Lower zIndex should be first (behind)");
            assertEquals(5f, quads.get(1).zIndex(), "Higher zIndex should be last (in front)");
        }

        @Test
        @DisplayName("Same zIndex, same Y — falls through to texture tiebreaker")
        void sameYFallsToTexture() {
            var quads = new ArrayList<>(List.of(
                    quad(5, 0f, 3f),
                    quad(2, 0f, 3f)
            ));

            batch.sortQuads(quads);

            assertEquals(2, quads.get(0).textureId());
            assertEquals(5, quads.get(1).textureId());
        }
    }

    @Nested
    @DisplayName("DEPTH_PRIORITY strategy")
    class DepthPriority {

        private final SpriteBatch batch = new SpriteBatch(SpriteBatch.SortingStrategy.DEPTH_PRIORITY);

        @Test
        @DisplayName("Same zIndex, different Y — lower Y renders in front (sorted last)")
        void lowerYSortsLast() {
            var quads = new ArrayList<>(List.of(
                    quad(1, 0f, 5f),
                    quad(1, 0f, 2f)
            ));

            batch.sortQuads(quads);

            assertEquals(5f, quads.get(0).yPosition(), "Higher Y should be first (behind)");
            assertEquals(2f, quads.get(1).yPosition(), "Lower Y should be last (in front)");
        }

        @Test
        @DisplayName("Different zIndex — higher zIndex renders in front regardless of Y")
        void zIndexOverridesY() {
            var quads = new ArrayList<>(List.of(
                    quad(1, 0f, 2f),
                    quad(1, 5f, 10f)
            ));

            batch.sortQuads(quads);

            assertEquals(0f, quads.get(0).zIndex());
            assertEquals(5f, quads.get(1).zIndex());
        }

        @Test
        @DisplayName("Same zIndex, same Y — falls through to texture tiebreaker")
        void sameYFallsToTexture() {
            var quads = new ArrayList<>(List.of(
                    quad(5, 0f, 3f),
                    quad(2, 0f, 3f)
            ));

            batch.sortQuads(quads);

            assertEquals(2, quads.get(0).textureId());
            assertEquals(5, quads.get(1).textureId());
        }
    }

    @Nested
    @DisplayName("BALANCED strategy")
    class Balanced {

        private final SpriteBatch batch = new SpriteBatch(SpriteBatch.SortingStrategy.BALANCED);

        @Test
        @DisplayName("Same zIndex, different Y — lower Y renders in front (sorted last)")
        void lowerYSortsLast() {
            // Y-distance > 4 to trigger depth-first branch
            var quads = new ArrayList<>(List.of(
                    quad(1, 0f, 10f),
                    quad(1, 0f, 2f)
            ));

            batch.sortQuads(quads);

            assertEquals(10f, quads.get(0).yPosition(), "Higher Y should be first (behind)");
            assertEquals(2f, quads.get(1).yPosition(), "Lower Y should be last (in front)");
        }

        @Test
        @DisplayName("Different zIndex — higher zIndex renders in front regardless of Y")
        void zIndexOverridesY() {
            var quads = new ArrayList<>(List.of(
                    quad(1, 0f, 2f),
                    quad(1, 5f, 10f)
            ));

            batch.sortQuads(quads);

            assertEquals(0f, quads.get(0).zIndex());
            assertEquals(5f, quads.get(1).zIndex());
        }

        @Test
        @DisplayName("Same zIndex, same Y — falls through to texture tiebreaker")
        void sameYFallsToTexture() {
            var quads = new ArrayList<>(List.of(
                    quad(5, 0f, 3f),
                    quad(2, 0f, 3f)
            ));

            batch.sortQuads(quads);

            assertEquals(2, quads.get(0).textureId());
            assertEquals(5, quads.get(1).textureId());
        }

        @Test
        @DisplayName("Y-distance > 4 units — sorts by Y depth, not texture")
        void farApartSortsByY() {
            // Different textures, but Y-distance > 4 means Y takes priority
            var quads = new ArrayList<>(List.of(
                    quad(1, 0f, 2f),    // lower Y, texture 1
                    quad(2, 0f, 10f)    // higher Y, texture 2
            ));

            batch.sortQuads(quads);

            // Higher Y should sort first (behind) regardless of texture
            assertEquals(10f, quads.get(0).yPosition(), "Higher Y should be first when far apart");
            assertEquals(2f, quads.get(1).yPosition(), "Lower Y should be last when far apart");
        }

        @Test
        @DisplayName("Y-distance ≤ 4 units — groups by texture first, then Y")
        void closeTogetherGroupsByTexture() {
            // Y-distance ≤ 4, different textures
            var quads = new ArrayList<>(List.of(
                    quad(5, 0f, 3f),    // texture 5, Y=3
                    quad(2, 0f, 5f),    // texture 2, Y=5 (distance=2, within 4)
                    quad(2, 0f, 3f)     // texture 2, Y=3
            ));

            batch.sortQuads(quads);

            // Texture 2 should group together, with higher Y first within group
            assertEquals(2, quads.get(0).textureId(), "Texture 2 group should come first");
            assertEquals(2, quads.get(1).textureId(), "Texture 2 group should come first");
            assertEquals(5, quads.get(2).textureId(), "Texture 5 should come after");

            // Within texture 2 group, higher Y should be first (behind)
            assertEquals(5f, quads.get(0).yPosition(), "Higher Y within group should be first");
            assertEquals(3f, quads.get(1).yPosition(), "Lower Y within group should be last");
        }
    }
}
