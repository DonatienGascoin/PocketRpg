package com.pocket.rpg.editor.assets;

import com.pocket.rpg.ui.text.Font;
import imgui.ImGui;

/**
 * Custom preview renderer for Font assets.
 * <p>
 * Shows font metrics (size, line height, ascent/descent) and atlas preview.
 */
public class FontPreviewRenderer implements AssetPreviewRenderer<Font> {

    @Override
    public void render(Font font, float maxSize) {
        if (font == null) {
            ImGui.textDisabled("No font");
            return;
        }

        // Font metrics
        ImGui.text("Size: " + font.getSize() + " px");
        ImGui.text("Line Height: " + font.getLineHeight() + " px");
        ImGui.text("Ascent: " + font.getAscent() + " / Descent: " + font.getDescent());

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Atlas preview
        ImGui.text("Atlas:");

        int atlasId = font.getAtlasTextureId();
        int atlasWidth = font.getAtlasWidth();
        int atlasHeight = font.getAtlasHeight();

        if (atlasId != 0 && atlasWidth > 0 && atlasHeight > 0) {
            // Scale to fit
            float scale = Math.min(maxSize / atlasWidth, maxSize / atlasHeight);
            if (scale > 1) scale = 1;

            int displayWidth = (int) (atlasWidth * scale);
            int displayHeight = (int) (atlasHeight * scale);

            ImGui.image(atlasId, displayWidth, displayHeight);
            ImGui.textDisabled(atlasWidth + "x" + atlasHeight);
        } else {
            ImGui.textDisabled("(No atlas available)");
        }
    }

    @Override
    public Class<Font> getAssetType() {
        return Font.class;
    }
}
