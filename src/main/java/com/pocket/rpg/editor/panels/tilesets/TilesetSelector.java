// TilesetSelector.java
package com.pocket.rpg.editor.panels.tilesets;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.tileset.CreateSpritesheetDialog;
import com.pocket.rpg.editor.tileset.TilesetRegistry;
import imgui.ImGui;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

public class TilesetSelector {

    private String selectedTileset = null;
    private final CreateSpritesheetDialog createDialog = new CreateSpritesheetDialog();

    @Setter
    private Consumer<String> onTilesetChanged;

    public TilesetSelector() {
        createDialog.setOnCreated(() -> {
            List<String> names = TilesetRegistry.getInstance().getTilesetNames();
            if (!names.isEmpty()) {
                setSelectedTileset(names.get(names.size() - 1));
            }
        });
    }

    public void renderSelector() {
        List<String> tilesetNames = TilesetRegistry.getInstance().getTilesetNames();

        if (tilesetNames.isEmpty()) {
            ImGui.textDisabled("No spritesheets found");
            ImGui.textDisabled("Create one or add .spritesheet files");
        }

        if (selectedTileset == null && !tilesetNames.isEmpty()) {
            selectedTileset = tilesetNames.get(0);
        }

        if (selectedTileset != null && !tilesetNames.contains(selectedTileset)) {
            selectedTileset = tilesetNames.isEmpty() ? null : tilesetNames.get(0);
        }

        ImGui.text("Tileset:");

        String displayName = selectedTileset != null ? selectedTileset : "Select...";
        if (ImGui.beginCombo("##tileset", displayName)) {
            for (String name : tilesetNames) {
                boolean isSelected = name.equals(selectedTileset);
                if (ImGui.selectable(name, isSelected)) {
                    setSelectedTileset(name);
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }

                if (ImGui.isItemHovered()) {
                    TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(name);
                    if (entry != null) {
                        ImGui.setTooltip(entry.getSpriteWidth() + "x" + entry.getSpriteHeight() +
                                " sprites, " + entry.getTotalSprites() + " tiles");
                    }
                }
            }
            ImGui.endCombo();
        }

        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.Refresh)) {
            TilesetRegistry.getInstance().reload();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Reload spritesheets");
        }

        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.Add)) {
            createDialog.open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Create new spritesheet");
        }

        if (selectedTileset != null) {
            TilesetRegistry.TilesetEntry entry = TilesetRegistry.getInstance().getTileset(selectedTileset);
            if (entry != null) {
                ImGui.textDisabled(entry.getSpriteWidth() + "x" + entry.getSpriteHeight() +
                        " px, " + entry.getTotalSprites() + " tiles");
            }
        }
    }

    public void renderDialogs() {
        createDialog.render();
    }

    public String getSelectedTileset() {
        return selectedTileset;
    }

    public void setSelectedTileset(String tileset) {
        this.selectedTileset = tileset;
        if (onTilesetChanged != null) {
            onTilesetChanged.accept(tileset);
        }
    }
}