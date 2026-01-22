package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Field editor for AudioClip assets with play/stop preview button.
 */
public final class AudioClipFieldEditor {

    private static final AssetPickerPopup assetPicker = new AssetPickerPopup();

    // State for async callback
    private static Component assetPickerTargetComponent = null;
    private static String assetPickerFieldName = null;

    private AudioClipFieldEditor() {}

    /**
     * Draws an AudioClip field with asset picker and play/stop preview button.
     */
    public static boolean drawAudioClip(String label, Component component, String fieldName,
                                         EditorGameObject entity) {
        AudioClip clip = (AudioClip) ComponentReflectionUtils.getFieldValue(component, fieldName);
        String display = FieldEditorUtils.getAssetDisplayName(clip);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        try {
            FieldEditorUtils.inspectorRow(label, () -> {
                // Play/Stop button
                drawPlayButton(clip);
                ImGui.sameLine();

                // Asset name display
                if (clip != null) {
                    ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, display);
                } else {
                    ImGui.textDisabled(display);
                }

                // Asset picker button
                ImGui.sameLine();
                if (ImGui.smallButton("...")) {
                    assetPickerTargetComponent = component;
                    assetPickerFieldName = fieldName;
                    Object oldValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
                    String currentPath = oldValue != null ? Assets.getPathForResource(oldValue) : null;

                    assetPicker.open(AudioClip.class, currentPath, selectedAsset -> {
                        UndoManager.getInstance().execute(
                                new SetComponentFieldCommand(
                                        assetPickerTargetComponent,
                                        assetPickerFieldName,
                                        oldValue,
                                        selectedAsset,
                                        FieldEditorContext.getEntity()
                                )
                        );
                        FieldEditorContext.markFieldOverridden(assetPickerFieldName, selectedAsset);
                    });
                }
            });

            FieldEditorContext.popOverrideStyle(fieldName);
            FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);

        } finally {
            ImGui.popID();
        }
        return false;
    }

    /**
     * Draws an AudioClip field using getter/setter pattern with undo support.
     */
    public static boolean drawAudioClip(String label, String key,
                                         Supplier<AudioClip> getter, Consumer<AudioClip> setter) {
        AudioClip clip = getter.get();
        String display = FieldEditorUtils.getAssetDisplayName(clip);

        ImGui.pushID(key);

        try {
            FieldEditorUtils.inspectorRow(label, () -> {
                // Play/Stop button
                drawPlayButton(clip);
                ImGui.sameLine();

                // Asset name display
                if (clip != null) {
                    ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, display);
                } else {
                    ImGui.textDisabled(display);
                }

                // Asset picker button
                ImGui.sameLine();
                if (ImGui.smallButton("...")) {
                    AudioClip oldValue = getter.get();
                    String currentPath = oldValue != null ? Assets.getPathForResource(oldValue) : null;

                    assetPicker.open(AudioClip.class, currentPath, selectedAsset -> {
                        @SuppressWarnings("unchecked")
                        AudioClip typedAsset = (AudioClip) selectedAsset;

                        UndoManager.getInstance().execute(
                                new SetterUndoCommand<>(setter, oldValue, typedAsset, "Change " + label)
                        );
                    });
                }
            });

        } finally {
            ImGui.popID();
        }
        return false;
    }

    /**
     * Draws a play/stop button for the given clip.
     */
    private static void drawPlayButton(AudioClip clip) {
        if (clip == null) {
            // Disabled play button when no clip
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.3f, 0.3f, 1.0f);
            ImGui.smallButton(MaterialIcons.PlayArrow);
            ImGui.popStyleColor(3);
            return;
        }

        boolean isPlaying = EditorAudio.isPreviewingClip(clip);

        if (isPlaying) {
            // Stop button (red)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.4f, 0.4f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.2f, 0.2f, 1.0f);

            if (ImGui.smallButton(MaterialIcons.Stop)) {
                EditorAudio.stopPreview(clip);
            }

            ImGui.popStyleColor(3);
        } else {
            // Play button (green)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 0.4f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.2f, 1.0f);

            if (ImGui.smallButton(MaterialIcons.PlayArrow)) {
                EditorAudio.playPreview(clip);
            }

            ImGui.popStyleColor(3);
        }

        // Tooltip with clip info
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            if (isPlaying) {
                ImGui.text("Click to stop preview");
            } else {
                ImGui.text("Click to preview");
            }
            ImGui.separator();
            ImGui.textDisabled(String.format("Duration: %.2fs", clip.getDuration()));
            ImGui.textDisabled(String.format("Channels: %s", clip.isMono() ? "Mono" : "Stereo"));
            ImGui.textDisabled(String.format("Sample Rate: %d Hz", clip.getSampleRate()));
            ImGui.endTooltip();
        }
    }

    /**
     * Renders the asset picker popup. Call once per frame.
     */
    public static void renderAssetPicker() {
        assetPicker.render();
    }
}
