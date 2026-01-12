package com.pocket.rpg.editor.panels.tilesets;

import com.pocket.rpg.editor.tileset.TileSelection;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.rendering.resources.SpriteSheet;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

public class TileSelectionManager {

    private boolean isDragging = false;
    private int dragStartDisplayX = -1;
    private int dragStartDisplayY = -1;
    private int dragEndDisplayX = -1;
    private int dragEndDisplayY = -1;

    private int selectionMinDisplayX = -1;
    private int selectionMinDisplayY = -1;
    private int selectionMaxDisplayX = -1;
    private int selectionMaxDisplayY = -1;

    private int cachedDisplayCols = -1;

    @Setter
    private Consumer<TileSelection> onSelectionCreated;

    public void startDrag(int displayX, int displayY, int displayCols) {
        isDragging = true;
        dragStartDisplayX = displayX;
        dragStartDisplayY = displayY;
        dragEndDisplayX = displayX;
        dragEndDisplayY = displayY;

        selectionMinDisplayX = displayX;
        selectionMinDisplayY = displayY;
        selectionMaxDisplayX = displayX;
        selectionMaxDisplayY = displayY;

        cachedDisplayCols = displayCols;
    }

    public void updateDrag(int displayX, int displayY) {
        dragEndDisplayX = displayX;
        dragEndDisplayY = displayY;

        selectionMinDisplayX = Math.min(dragStartDisplayX, dragEndDisplayX);
        selectionMaxDisplayX = Math.max(dragStartDisplayX, dragEndDisplayX);
        selectionMinDisplayY = Math.min(dragStartDisplayY, dragEndDisplayY);
        selectionMaxDisplayY = Math.max(dragStartDisplayY, dragEndDisplayY);
    }

    public void endDrag(String tilesetName) {
        isDragging = false;
        createSelection(tilesetName);
    }

    public boolean isTileInSelection(int tileIndex, int displayCols) {
        if (selectionMinDisplayX < 0 || cachedDisplayCols < 0) return false;

        int displayX = tileIndex % displayCols;
        int displayY = tileIndex / displayCols;

        return displayX >= selectionMinDisplayX && displayX <= selectionMaxDisplayX &&
                displayY >= selectionMinDisplayY && displayY <= selectionMaxDisplayY;
    }

    private void createSelection(String tilesetName) {
        if (tilesetName == null || selectionMinDisplayX < 0 || cachedDisplayCols < 0) return;

        TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(tilesetName);
        if (entry == null) return;

        SpriteSheet sheet = entry.getSpriteSheet();
        List<Sprite> allSprites = entry.getSprites();

        int selectionWidth = selectionMaxDisplayX - selectionMinDisplayX + 1;
        int selectionHeight = selectionMaxDisplayY - selectionMinDisplayY + 1;

        int[] indices = new int[selectionWidth * selectionHeight];
        Sprite[] selectionSprites = new Sprite[selectionWidth * selectionHeight];

        for (int displayY = selectionMinDisplayY; displayY <= selectionMaxDisplayY; displayY++) {
            for (int displayX = selectionMinDisplayX; displayX <= selectionMaxDisplayX; displayX++) {
                int tileIndex = displayY * cachedDisplayCols + displayX;

                int localX = displayX - selectionMinDisplayX;
                int localY = displayY - selectionMinDisplayY;
                int arrayIndex = localY * selectionWidth + localX;

                if (tileIndex < allSprites.size()) {
                    indices[arrayIndex] = tileIndex;
                    selectionSprites[arrayIndex] = allSprites.get(tileIndex);
                } else {
                    indices[arrayIndex] = -1;
                    selectionSprites[arrayIndex] = null;
                }
            }
        }

        TileSelection selection = new TileSelection(
                tilesetName,
                entry.getSpriteWidth(),
                entry.getSpriteHeight(),
                selectionWidth,
                selectionHeight,
                indices,
                selectionSprites
        );

        if (onSelectionCreated != null) {
            onSelectionCreated.accept(selection);
        }
    }

    public void clearSelection() {
        selectionMinDisplayX = -1;
        selectionMinDisplayY = -1;
        selectionMaxDisplayX = -1;
        selectionMaxDisplayY = -1;
        cachedDisplayCols = -1;
    }

    public void setExternalSelection(TileSelection selection) {
        if (selection.isSingleTile() && cachedDisplayCols > 0) {
            int tileIndex = selection.getFirstTileIndex();
            int displayX = tileIndex % cachedDisplayCols;
            int displayY = tileIndex / cachedDisplayCols;

            selectionMinDisplayX = displayX;
            selectionMaxDisplayX = displayX;
            selectionMinDisplayY = displayY;
            selectionMaxDisplayY = displayY;
        } else {
            clearSelection();
        }
    }

    public boolean isDragging() {
        return isDragging;
    }

    public boolean hasSelection() {
        return selectionMinDisplayX >= 0;
    }

    public String getSelectionDebugInfo() {
        if (!hasSelection()) return "";
        return "Selection: (" + selectionMinDisplayX + "," + selectionMinDisplayY +
                ") to (" + selectionMaxDisplayX + "," + selectionMaxDisplayY + ") in display";
    }
}