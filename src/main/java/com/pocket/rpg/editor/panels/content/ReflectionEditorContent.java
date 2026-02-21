package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.ui.fields.AssetFieldCollector;
import com.pocket.rpg.editor.ui.fields.ReflectionAssetEditor;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;

import java.util.List;

/**
 * Default content implementation that renders any asset's fields
 * via reflection-based field discovery.
 * <p>
 * Used as the fallback when no specific content implementation
 * is registered for an asset type.
 */
public class ReflectionEditorContent implements AssetEditorContent {

    private Object asset;
    private String path;
    private Class<?> assetType;
    private List<FieldMeta> fields;
    private AssetEditorShell shell;

    @Override
    public void render() {
        if (asset == null || fields == null) return;

        ImGui.beginChild("AssetFields", 0, 0, false);

        boolean changed = ReflectionAssetEditor.drawObject(
                asset, fields, "asset." + path);
        if (changed) {
            shell.markDirty();
        }

        ImGui.endChild();
    }

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.path = path;
        this.asset = asset;
        this.assetType = asset.getClass();
        this.fields = AssetFieldCollector.getFields(assetType);
        this.shell = shell;
    }

    @Override
    public void onAssetUnloaded() {
        this.asset = null;
        this.path = null;
        this.assetType = null;
        this.fields = null;
    }

    @Override
    public Class<?> getAssetClass() {
        return null; // Default — handles any type
    }
}
