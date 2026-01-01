package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.LayerVisibilityMode;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders tilemap layers inspector.
 */
public class TilemapLayersInspector {

    @Setter
    private EditorScene scene;

    private final float[] floatBuffer = new float[4];
    private final ImString stringBuffer = new ImString(256);
    private final ImInt intBuffer = new ImInt();
    private int renamingLayerIndex = -1;
    private final ImString layerRenameBuffer = new ImString(64);

    private static final int ENTITY_Z_LEVEL = 0;

    public void render() {
        ImGui.text(IconUtils.getLayersIcon() + " Tilemap Layers");
        ImGui.separator();

        renderLayerControls();
        ImGui.separator();
        renderLayerList();

        TilemapLayer activeLayer = scene.getActiveLayer();
        if (activeLayer != null) {
            ImGui.separator();
            renderActiveLayerDetails(activeLayer);
        }
    }

    private void renderLayerControls() {
        if (ImGui.button(FontAwesomeIcons.Plus + " Add Layer")) {
            scene.addLayer("Layer " + scene.getLayerCount());
        }

        ImGui.sameLine();

        boolean canRemove = scene.getActiveLayer() != null;
        if (!canRemove) ImGui.beginDisabled();
        if (ImGui.button(FontAwesomeIcons.Trash + " Remove")) {
            int activeIndex = scene.getActiveLayerIndex();
            if (activeIndex >= 0) scene.removeLayer(activeIndex);
        }
        if (!canRemove) ImGui.endDisabled();

        ImGui.spacing();
        ImGui.text("Visibility Mode:");

        LayerVisibilityMode current = scene.getVisibilityMode();
        int mode = current.ordinal();

        if (ImGui.radioButton("All", mode == LayerVisibilityMode.ALL.ordinal())) {
            mode = LayerVisibilityMode.ALL.ordinal();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("All layers visible");
        ImGui.sameLine();

        if (ImGui.radioButton("Selected", mode == LayerVisibilityMode.SELECTED_ONLY.ordinal())) {
            mode = LayerVisibilityMode.SELECTED_ONLY.ordinal();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Selected layer only");
        ImGui.sameLine();

        if (ImGui.radioButton("Dimmed", mode == LayerVisibilityMode.SELECTED_DIMMED.ordinal())) {
            mode = LayerVisibilityMode.SELECTED_DIMMED.ordinal();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Non-selected layers dimmed");

        LayerVisibilityMode newMode = LayerVisibilityMode.values()[mode];
        if (newMode != current) scene.setVisibilityMode(newMode);

        if (scene.getVisibilityMode() == LayerVisibilityMode.SELECTED_DIMMED) {
            ImGui.setNextItemWidth(100);
            floatBuffer[0] = scene.getDimmedOpacity();
            if (ImGui.sliderFloat("Opacity", floatBuffer, 0.1f, 1f)) {
                scene.setDimmedOpacity(floatBuffer[0]);
            }
        }
    }

    private void renderLayerList() {
        List<TilemapLayer> layers = scene.getLayers();
        if (layers.isEmpty()) {
            ImGui.textDisabled("No layers. Click 'Add Layer' to create one.");
            return;
        }

        List<LayerEntry> sortedLayers = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            sortedLayers.add(new LayerEntry(i, layers.get(i)));
        }
        sortedLayers.sort(Comparator.comparingInt((LayerEntry e) -> e.layer.getZIndex()).reversed());

        boolean entitySeparatorRendered = false;
        int activeIndex = scene.getActiveLayerIndex();

        for (LayerEntry entry : sortedLayers) {
            if (!entitySeparatorRendered && entry.layer.getZIndex() <= ENTITY_Z_LEVEL) {
                renderEntitySeparator();
                entitySeparatorRendered = true;
            }
            renderLayerItem(entry.originalIndex, entry.layer, activeIndex);
        }

        if (!entitySeparatorRendered) renderEntitySeparator();
    }

    private void renderEntitySeparator() {
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.8f, 0.6f, 1.0f);
        String text = FontAwesomeIcons.User + " -- Entities (z: 0) --";
        float indent = (ImGui.getContentRegionAvailX() - ImGui.calcTextSize(text).x) / 2;
        if (indent > 0) ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        ImGui.text(text);
        ImGui.popStyleColor();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Entities render at Z-index 0");
        ImGui.spacing();
    }

    private void renderLayerItem(int index, TilemapLayer layer, int activeIndex) {
        boolean isActive = index == activeIndex;
        boolean isRenaming = index == renamingLayerIndex;
        int zIndex = layer.getZIndex();
        boolean atEntityLevel = zIndex == ENTITY_Z_LEVEL;

        ImGui.pushID(index);

        boolean visible = layer.isVisible();
        if (ImGui.smallButton((visible ? FontAwesomeIcons.Eye : FontAwesomeIcons.EyeSlash) + "##vis")) {
            layer.setVisible(!visible);
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(visible ? "Hide" : "Show");

        ImGui.sameLine();

        boolean locked = layer.isLocked();
        if (ImGui.smallButton((locked ? FontAwesomeIcons.Lock : FontAwesomeIcons.LockOpen) + "##lock")) {
            layer.setLocked(!locked);
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(locked ? "Unlock" : "Lock");

        ImGui.sameLine();

        String zDisplay = atEntityLevel ? "(z: 0 " + FontAwesomeIcons.ExclamationCircle + ")" : "(z: " + zIndex + ")";

        if (isRenaming) {
            ImGui.setNextItemWidth(100);
            ImGui.setKeyboardFocusHere();
            if (ImGui.inputText("##rename", layerRenameBuffer, ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll)) {
                String newName = layerRenameBuffer.get().trim();
                if (!newName.isEmpty()) scene.renameLayer(index, newName);
                renamingLayerIndex = -1;
            }
            if (ImGui.isKeyPressed(ImGuiKey.Escape) || (!ImGui.isItemActive() && ImGui.isMouseClicked(0))) {
                renamingLayerIndex = -1;
            }
            ImGui.sameLine();
            ImGui.textDisabled(zDisplay);
        } else {
            if (atEntityLevel) ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.2f, 1.0f);

            float availWidth = ImGui.getContentRegionAvailX() - 55;
            if (ImGui.selectable(layer.getName() + " " + zDisplay, isActive, ImGuiSelectableFlags.AllowDoubleClick | ImGuiSelectableFlags.AllowItemOverlap, availWidth, 0f)) {
                scene.setActiveLayer(index);
                if (ImGui.isMouseDoubleClicked(0)) {
                    renamingLayerIndex = index;
                    layerRenameBuffer.set(layer.getName());
                }
            }

            if (atEntityLevel) ImGui.popStyleColor();
            if (atEntityLevel && ImGui.isItemHovered()) ImGui.setTooltip("Same Z-index as entities!");
        }

        if (ImGui.beginPopupContextItem("layer_context_" + index)) {
            if (ImGui.menuItem(FontAwesomeIcons.Edit + " Rename")) {
                renamingLayerIndex = index;
                layerRenameBuffer.set(layer.getName());
            }
            ImGui.separator();
            if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete")) scene.removeLayer(index);
            ImGui.endPopup();
        }

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 45);

        if (ImGui.smallButton(FontAwesomeIcons.ChevronUp + "##up")) {
            layer.setZIndex(zIndex + 1);
            scene.markDirty();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.ChevronDown + "##down")) {
            layer.setZIndex(zIndex - 1);
            scene.markDirty();
        }

        ImGui.popID();
    }

    private void renderActiveLayerDetails(TilemapLayer layer) {
        ImGui.text(FontAwesomeIcons.Edit + " Active Layer");

        stringBuffer.set(layer.getName());
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##layerName", stringBuffer)) {
            String newName = stringBuffer.get().trim();
            if (!newName.isEmpty()) {
                layer.setName(newName);
                if (layer.getGameObject() != null) layer.getGameObject().setName(newName);
                scene.markDirty();
            }
        }

        intBuffer.set(layer.getZIndex());
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Z-Index", intBuffer)) {
            layer.setZIndex(intBuffer.get());
            scene.markDirty();
        }
        ImGui.sameLine();
        ImGui.textDisabled("(higher = front)");
    }

    private record LayerEntry(int originalIndex, TilemapLayer layer) {
    }
}
