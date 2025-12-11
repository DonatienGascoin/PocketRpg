package com.pocket.rpg.editor.tileset;

import com.pocket.rpg.rendering.SpriteSheet;
import com.pocket.rpg.rendering.Texture;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Dialog for creating new spritesheet definition files.
 * <p>
 * Allows user to:
 * - Select a source texture (PNG)
 * - Set sprite dimensions
 * - Set spacing and offset
 * - Preview the result
 * - Save as .spritesheet file
 */
public class CreateSpritesheetDialog {

    /**
     * Whether the dialog is currently open
     */
    @Getter
    private boolean open = false;

    /**
     * Callback when a new spritesheet is created
     */
    @Setter
    private Runnable onCreated;

    // Dialog state
    private List<String> availableTextures;
    private int selectedTextureIndex = 0;
    private ImString spritesheetName = new ImString("new_tileset", 64);

    // Spritesheet settings
    private ImInt spriteWidth = new ImInt(16);
    private ImInt spriteHeight = new ImInt(16);
    private ImInt spacingX = new ImInt(0);
    private ImInt spacingY = new ImInt(0);
    private ImInt offsetX = new ImInt(0);
    private ImInt offsetY = new ImInt(0);

    // Preview
    private Texture previewTexture;
    private int previewColumns;
    private int previewRows;
    private int previewTotalTiles;

    // Output path
    private static final String SPRITESHEET_DIR = "spritesheets/";

    /**
     * Opens the dialog.
     */
    public void open() {
        open = true;

        // Scan for available textures
        availableTextures = Assets.scanByType(Texture.class);
        selectedTextureIndex = 0;

        // Reset fields
        spritesheetName.set("new_tileset");
        spriteWidth.set(16);
        spriteHeight.set(16);
        spacingX.set(0);
        spacingY.set(0);
        offsetX.set(0);
        offsetY.set(0);

        previewTexture = null;

        System.out.println("CreateSpritesheetDialog: Found " + availableTextures.size() + " textures");
    }

    /**
     * Closes the dialog.
     */
    public void close() {
        open = false;
        previewTexture = null;
    }

    /**
     * Renders the dialog. Call every frame.
     */
    public void render() {
        if (!open) return;

        ImGui.openPopup("Create Spritesheet");

        // Center the popup
        ImGui.setNextWindowSize(700, 600);

        if (ImGui.beginPopupModal("Create Spritesheet")) {
            renderContent();
            ImGui.endPopup();
        }
    }

    private void renderContent() {
        // Name input
        ImGui.text("Spritesheet Name:");
        ImGui.inputText("##name", spritesheetName);

        ImGui.separator();

        // Texture selection
        ImGui.text("Source Texture:");
        if (availableTextures.isEmpty()) {
            ImGui.textDisabled("No textures found in assets");
        } else {
            String currentTexture = selectedTextureIndex < availableTextures.size()
                    ? extractFilename(availableTextures.get(selectedTextureIndex))
                    : "Select...";

            if (ImGui.beginCombo("##texture", currentTexture)) {
                for (int i = 0; i < availableTextures.size(); i++) {
                    String path = availableTextures.get(i);
                    String displayName = extractFilename(path);

                    boolean isSelected = (i == selectedTextureIndex);
                    if (ImGui.selectable(displayName, isSelected)) {
                        selectedTextureIndex = i;
                        updatePreview();
                    }

                    if (isSelected) {
                        ImGui.setItemDefaultFocus();
                    }

                    // Tooltip with full path
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(path);
                    }
                }
                ImGui.endCombo();
            }
        }

        ImGui.separator();

        // Sprite dimensions
        ImGui.text("Sprite Size:");
        ImGui.pushItemWidth(80);

        boolean changed = false;

        if (ImGui.inputInt("Width##sw", spriteWidth)) {
            if (spriteWidth.get() < 1) spriteWidth.set(1);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.inputInt("Height##sh", spriteHeight)) {
            if (spriteHeight.get() < 1) spriteHeight.set(1);
            changed = true;
        }

        // Presets
        ImGui.sameLine();
        if (ImGui.smallButton("8##preset")) {
            spriteWidth.set(8);
            spriteHeight.set(8);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("16##preset")) {
            spriteWidth.set(16);
            spriteHeight.set(16);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("32##preset")) {
            spriteWidth.set(32);
            spriteHeight.set(32);
            changed = true;
        }

        ImGui.separator();

        // Spacing
        ImGui.text("Spacing (between sprites):");
        if (ImGui.inputInt("X##spacingX", spacingX)) {
            if (spacingX.get() < 0) spacingX.set(0);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.inputInt("Y##spacingY", spacingY)) {
            if (spacingY.get() < 0) spacingY.set(0);
            changed = true;
        }

        // Offset
        ImGui.text("Offset (from texture edge):");
        if (ImGui.inputInt("X##offsetX", offsetX)) {
            if (offsetX.get() < 0) offsetX.set(0);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.inputInt("Y##offsetY", offsetY)) {
            if (offsetY.get() < 0) offsetY.set(0);
            changed = true;
        }

        ImGui.popItemWidth();

        if (changed) {
            updatePreview();
        }

        ImGui.separator();

        // Preview info
        renderPreviewInfo();

        ImGui.separator();

        // Buttons
        boolean canCreate = !availableTextures.isEmpty() &&
                !spritesheetName.get().trim().isEmpty() &&
                previewTotalTiles > 0;

        if (!canCreate) {
            ImGui.beginDisabled();
        }

        if (ImGui.button("Create", 120, 0)) {
            createSpritesheet();
        }

        if (!canCreate) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();

        if (ImGui.button("Cancel", 120, 0) || ImGui.isKeyPressed(ImGuiKey.Escape)) {
            close();
            ImGui.closeCurrentPopup();
        }
    }

    /**
     * Updates the preview calculation.
     */
    private void updatePreview() {
        if (availableTextures.isEmpty() || selectedTextureIndex >= availableTextures.size()) {
            previewTexture = null;
            previewColumns = 0;
            previewRows = 0;
            previewTotalTiles = 0;
            return;
        }

        String texturePath = availableTextures.get(selectedTextureIndex);

        try {
            previewTexture = Assets.load(texturePath, Texture.class);

            if (previewTexture != null) {
                // Calculate grid
                int usableWidth = Math.max(0, previewTexture.getWidth() - offsetX.get());
                int usableHeight = Math.max(0, previewTexture.getHeight() - offsetY.get());

                previewColumns = computeColumns(usableWidth);
                previewRows = computeRows(usableHeight);
                previewTotalTiles = previewColumns * previewRows;
            }
        } catch (Exception e) {
            previewTexture = null;
            previewColumns = 0;
            previewRows = 0;
            previewTotalTiles = 0;
        }
    }

    private int computeColumns(int usableWidth) {
        int cols = 0;
        int x = 0;
        int sw = spriteWidth.get();
        int sx = spacingX.get();

        while (x + sw <= usableWidth) {
            cols++;
            x += sw + sx;
        }
        return cols;
    }

    private int computeRows(int usableHeight) {
        int rows = 0;
        int y = 0;
        int sh = spriteHeight.get();
        int sy = spacingY.get();

        while (y + sh <= usableHeight) {
            rows++;
            y += sh + sy;
        }
        return rows;
    }

    /**
     * Renders preview information.
     */
    /**
     * Renders preview information with visual grid overlay.
     */
    private void renderPreviewInfo() {
        ImGui.text("Preview:");

        if (previewTexture == null) {
            ImGui.textDisabled("Select a texture to preview");
            return;
        }

        ImGui.text("Texture: " + previewTexture.getWidth() + "x" + previewTexture.getHeight() + " px");
        ImGui.text("Grid: " + previewColumns + " columns x " + previewRows + " rows");
        ImGui.text("Total tiles: " + previewTotalTiles);

        if (previewTotalTiles == 0) {
            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f,
                    "Warning: No tiles fit with current settings!");
        }

        // Visual preview with grid overlay
        if (previewTexture.getTextureId() > 0) {
            renderVisualPreview();
        }
    }

    /**
     * Renders the visual preview with grid overlay showing offset, spacing, and tiles.
     */
    private void renderVisualPreview() {
        int textureId = previewTexture.getTextureId();
        int texWidth = previewTexture.getWidth();
        int texHeight = previewTexture.getHeight();

        // Calculate preview size (fit in 300x300 box)
        float maxPreviewSize = 600;
        float aspect = (float) texWidth / texHeight;
        float previewWidth, previewHeight;

        if (aspect >= 1) {
            previewWidth = Math.min(maxPreviewSize, texWidth);
            previewHeight = previewWidth / aspect;
        } else {
            previewHeight = Math.min(maxPreviewSize, texHeight);
            previewWidth = previewHeight * aspect;
        }

        // Scale factor for converting texture pixels to screen pixels
        float scaleX = previewWidth / texWidth;
        float scaleY = previewHeight / texHeight;

        // Draw background texture
        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        float startX = cursorPos.x;
        float startY = cursorPos.y;

        // Flip V coordinates for correct orientation
        ImGui.image(textureId, previewWidth, previewHeight, 0, 1, 1, 0);

        // Get draw list for overlay
        var drawList = ImGui.getWindowDrawList();

        // Colors
        int colorOffset = ImGui.colorConvertFloat4ToU32(1.0f, 0.0f, 0.0f, 0.4f);     // Red semi-transparent
        int colorSprite = ImGui.colorConvertFloat4ToU32(0.0f, 1.0f, 0.0f, 1.0f);     // Green solid
        int colorSpacing = ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 1.0f, 0.3f);    // Blue semi-transparent
        int colorText = ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f);       // White

        // Draw offset region (red rectangle)
        if (offsetX.get() > 0 || offsetY.get() > 0) {
            float offsetW = offsetX.get() * scaleX;
            float offsetH = offsetY.get() * scaleY;

            // Top-left offset rectangle
            if (offsetX.get() > 0 && offsetY.get() > 0) {
                drawList.addRectFilled(
                        startX, startY,
                        startX + offsetW, startY + offsetH,
                        colorOffset
                );
            }

            // Left edge offset
            if (offsetX.get() > 0) {
                drawList.addRectFilled(
                        startX, startY,
                        startX + offsetW, startY + previewHeight,
                        colorOffset
                );
            }

            // Top edge offset
            if (offsetY.get() > 0) {
                drawList.addRectFilled(
                        startX, startY,
                        startX + previewWidth, startY + offsetH,
                        colorOffset
                );
            }
        }

        // Draw grid cells
        int tileIndex = 0;
        for (int row = 0; row < previewRows; row++) {
            for (int col = 0; col < previewColumns; col++) {
                // Calculate tile position in texture coordinates
                int tileX = offsetX.get() + col * (spriteWidth.get() + spacingX.get());
                int tileY = offsetY.get() + row * (spriteHeight.get() + spacingY.get());

                // Convert to screen coordinates
                float screenX = startX + tileX * scaleX;
                float screenY = startY + tileY * scaleY;
                float screenW = spriteWidth.get() * scaleX;
                float screenH = spriteHeight.get() * scaleY;

                // Draw sprite cell border (green)
                drawList.addRect(
                        screenX, screenY,
                        screenX + screenW, screenY + screenH,
                        colorSprite,
                        0.0f,  // No rounding
                        0,     // No flags
                        2.0f   // Thickness
                );

                // Draw spacing area (blue semi-transparent)
                if (spacingX.get() > 0) {
                    // Right spacing
                    float spacingScreenW = spacingX.get() * scaleX;
                    drawList.addRectFilled(
                            screenX + screenW, screenY,
                            screenX + screenW + spacingScreenW, screenY + screenH,
                            colorSpacing
                    );
                }

                if (spacingY.get() > 0) {
                    // Bottom spacing
                    float spacingScreenH = spacingY.get() * scaleY;
                    drawList.addRectFilled(
                            screenX, screenY + screenH,
                            screenX + screenW, screenY + screenH + spacingScreenH,
                            colorSpacing
                    );
                }

                // Draw tile number (if tiles are large enough)
                if (screenW > 20 && screenH > 20) {
                    String indexText = String.valueOf(tileIndex);
                    float textX = screenX + 4;
                    float textY = screenY + 4;

                    // Draw text shadow for readability
                    drawList.addText(textX + 1, textY + 1,
                            ImGui.colorConvertFloat4ToU32(0, 0, 0, 0.8f), indexText);
                    drawList.addText(textX, textY, colorText, indexText);
                }

                tileIndex++;
            }
        }

        // Add legend below preview
        ImGui.spacing();
        ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "▮");
        ImGui.sameLine();
        ImGui.textDisabled("Offset");

        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();

        ImGui.textColored(0.0f, 1.0f, 0.0f, 1.0f, "▮");
        ImGui.sameLine();
        ImGui.textDisabled("Sprite boundary");

        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();

        ImGui.textColored(0.0f, 0.4f, 1.0f, 1.0f, "▮");
        ImGui.sameLine();
        ImGui.textDisabled("Spacing");
    }












    /**
     * Creates the spritesheet file.
     */
    private void createSpritesheet() {
        if (availableTextures.isEmpty() || selectedTextureIndex >= availableTextures.size()) {
            return;
        }

        String texturePath = availableTextures.get(selectedTextureIndex);
        String name = spritesheetName.get().trim();

        if (name.isEmpty()) {
            return;
        }

        try {
            // Load texture to create spritesheet
            Texture texture = Assets.load(texturePath, Texture.class);

            // Create spritesheet object
            SpriteSheet sheet = new SpriteSheet(
                    texture,
                    spriteWidth.get(),
                    spriteHeight.get(),
                    spacingX.get(),
                    spacingY.get(),
                    offsetX.get(),
                    offsetY.get()
            );

            // Generate output path
            String outputPath = SPRITESHEET_DIR + name + ".spritesheet";

            // Save using Assets
            Assets.persist(sheet, outputPath);

            System.out.println("Created spritesheet: " + outputPath);

            // Register with TilesetRegistry
            TilesetRegistry.getInstance().registerNew(outputPath);

            // Callback
            if (onCreated != null) {
                onCreated.run();
            }

            // Close dialog
            close();
            ImGui.closeCurrentPopup();

        } catch (Exception e) {
            System.err.println("Failed to create spritesheet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extracts filename from path.
     */
    private String extractFilename(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}