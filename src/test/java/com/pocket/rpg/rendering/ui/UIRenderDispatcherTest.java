package com.pocket.rpg.rendering.ui;

import com.pocket.rpg.components.ui.UIComponent;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.rendering.resources.Sprite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UIRenderDispatcherTest {

    private UIRenderDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new UIRenderDispatcher();
    }

    @Nested
    class FitToAspectRatio {

        @Test
        void wideSpriteInSquareBounds_shrinksVertically() {
            UIImage image = createImage(200, 100, true);
            var bounds = new UIComponent.RenderBounds(0, 0, 100, 100, 0, 0.5f, 0.5f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertEquals(100f, result.width(), 0.01f);
            assertEquals(50f, result.height(), 0.01f);
            // Centered vertically: offset = (100 - 50) * 0.5 = 25
            assertEquals(0f, result.x(), 0.01f);
            assertEquals(25f, result.y(), 0.01f);
        }

        @Test
        void tallSpriteInWideBounds_shrinksHorizontally() {
            UIImage image = createImage(100, 200, true);
            var bounds = new UIComponent.RenderBounds(0, 0, 200, 100, 0, 0.5f, 0.5f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertEquals(50f, result.width(), 0.01f);
            assertEquals(100f, result.height(), 0.01f);
            // Centered horizontally: offset = (200 - 50) * 0.5 = 75
            assertEquals(75f, result.x(), 0.01f);
            assertEquals(0f, result.y(), 0.01f);
        }

        @Test
        void squareSpriteInSquareBounds_unchanged() {
            UIImage image = createImage(100, 100, true);
            var bounds = new UIComponent.RenderBounds(10, 20, 100, 100, 0, 0.5f, 0.5f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertEquals(10f, result.x(), 0.01f);
            assertEquals(20f, result.y(), 0.01f);
            assertEquals(100f, result.width(), 0.01f);
            assertEquals(100f, result.height(), 0.01f);
        }

        @Test
        void nullSprite_returnsOriginalBounds() {
            UIImage image = new UIImage();
            image.setPreserveAspectRatio(true);
            var bounds = new UIComponent.RenderBounds(0, 0, 100, 100, 0, 0.5f, 0.5f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertSame(bounds, result);
        }

        @Test
        void zeroSizeSprite_returnsOriginalBounds() {
            UIImage image = createImage(0, 0, true);
            var bounds = new UIComponent.RenderBounds(0, 0, 100, 100, 0, 0.5f, 0.5f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertSame(bounds, result);
        }

        @Test
        void zeroBoundsSize_returnsOriginalBounds() {
            UIImage image = createImage(200, 100, true);
            var bounds = new UIComponent.RenderBounds(0, 0, 0, 0, 0, 0.5f, 0.5f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertSame(bounds, result);
        }

        @Test
        void preservesRotationAndPivot() {
            UIImage image = createImage(200, 100, true);
            var bounds = new UIComponent.RenderBounds(0, 0, 100, 100, 45f, 0.3f, 0.7f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertEquals(45f, result.rotation(), 0.01f);
            assertEquals(0.3f, result.pivotX(), 0.01f);
            assertEquals(0.7f, result.pivotY(), 0.01f);
        }

        @Test
        void idempotent_applyingTwiceGivesSameResult() {
            UIImage image = createImage(200, 100, true);
            var bounds = new UIComponent.RenderBounds(0, 0, 100, 100, 0, 0.5f, 0.5f);

            var first = dispatcher.fitToAspectRatio(image, bounds);
            var second = dispatcher.fitToAspectRatio(image, first);

            assertEquals(first.x(), second.x(), 0.01f);
            assertEquals(first.y(), second.y(), 0.01f);
            assertEquals(first.width(), second.width(), 0.01f);
            assertEquals(first.height(), second.height(), 0.01f);
        }

        @Test
        void centeredOffset_withTopLeftPivot() {
            UIImage image = createImage(200, 100, true);
            // Pivot at top-left (0, 0) — offset is always centered regardless of pivot
            var bounds = new UIComponent.RenderBounds(0, 0, 100, 100, 0, 0f, 0f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertEquals(100f, result.width(), 0.01f);
            assertEquals(50f, result.height(), 0.01f);
            // centered offset = (100 - 50) * 0.5 = 25
            assertEquals(0f, result.x(), 0.01f);
            assertEquals(25f, result.y(), 0.01f);
        }

        @Test
        void centeredOffset_withBottomRightPivot() {
            UIImage image = createImage(200, 100, true);
            // Pivot at bottom-right (1, 1) — offset is always centered regardless of pivot
            var bounds = new UIComponent.RenderBounds(0, 0, 100, 100, 0, 1f, 1f);

            var result = dispatcher.fitToAspectRatio(image, bounds);

            assertEquals(100f, result.width(), 0.01f);
            assertEquals(50f, result.height(), 0.01f);
            // centered offset = (100 - 50) * 0.5 = 25
            assertEquals(0f, result.x(), 0.01f);
            assertEquals(25f, result.y(), 0.01f);
        }
    }

    private UIImage createImage(float spriteWidth, float spriteHeight, boolean preserveAspect) {
        UIImage image = new UIImage();
        if (spriteWidth > 0 || spriteHeight > 0) {
            Sprite sprite = new Sprite(null, spriteWidth, spriteHeight);
            image.setSprite(sprite);
        }
        image.setPreserveAspectRatio(preserveAspect);
        return image;
    }
}
