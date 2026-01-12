package com.pocket.rpg.editor.assets;

import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteSheet;
import com.pocket.rpg.rendering.resources.Texture;
import imgui.ImGui;

/**
 * Default preview renderer that uses sprites for preview.
 * <p>
 * Works for any asset type where the loader provides a preview sprite
 * via {@link com.pocket.rpg.resources.AssetLoader#getPreviewSprite(Object)}.
 * <p>
 * This handles: Sprite, Texture, SpriteSheet, and any other asset with preview sprites.
 */
public class SpriteBasedPreviewRenderer implements AssetPreviewRenderer<Object> {

    @Override
    public void render(Object asset, float maxSize) {
        if (asset == null) {
            ImGui.textDisabled("No asset");
            return;
        }

        // Extract texture and dimensions based on asset type
        Texture texture = null;
        float width = 0;
        float height = 0;
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;

        if (asset instanceof Sprite sprite) {
            texture = sprite.getTexture();
            width = sprite.getWidth();
            height = sprite.getHeight();
            u0 = sprite.getU0();
            v0 = sprite.getV0();
            u1 = sprite.getU1();
            v1 = sprite.getV1();
        } else if (asset instanceof Texture tex) {
            texture = tex;
            width = tex.getWidth();
            height = tex.getHeight();
        } else if (asset instanceof SpriteSheet sheet) {
            texture = sheet.getTexture();
            if (texture != null) {
                width = texture.getWidth();
                height = texture.getHeight();
            }
        }

        if (texture == null) {
            ImGui.textDisabled("No preview available");
            return;
        }

        // Scale to fit
        float scale = Math.min(maxSize / width, maxSize / height);
        if (scale > 1) scale = 1;  // Don't upscale

        int displayWidth = (int) (width * scale);
        int displayHeight = (int) (height * scale);

        // Flip V for OpenGL
        ImGui.image(texture.getTextureId(), displayWidth, displayHeight, u0, v1, u1, v0);

        // Show dimensions
        ImGui.text((int) width + " x " + (int) height + " px");
    }

    @Override
    public Class<Object> getAssetType() {
        return Object.class;  // Default handler
    }
}
