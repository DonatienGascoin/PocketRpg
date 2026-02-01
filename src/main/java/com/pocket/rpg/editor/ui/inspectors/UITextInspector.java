package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.editor.core.EditorConfig;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.CompoundCommand;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.ui.text.Font;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom editor for UIText component.
 * Organizes fields into logical sections.
 */
@InspectorFor(UIText.class)
public class UITextInspector extends CustomComponentInspector<UIText> {

    private static final String[] H_ALIGNMENTS = {"LEFT", "CENTER", "RIGHT"};
    private static final String[] V_ALIGNMENTS = {"TOP", "MIDDLE", "BOTTOM"};

    private static List<String> cachedFontPaths = null;
    private static String[] cachedFontNames = null;

    private final ImString textBuffer = new ImString(1024);

    // Undo tracking for continuous edits
    private String textEditStartValue;
    private int minFontSizeEditStart;
    private int maxFontSizeEditStart;
    private int fontSizeEditStart;

    @Override
    public boolean draw() {
        boolean changed = false;

        // === UI KEY ===
        changed |= UIKeyField.draw(component);

        // === CONTENT SECTION ===
        ImGui.text(MaterialIcons.TextFields + " Content");
        ImGui.separator();

        // Font path dropdown
        ImGui.spacing();
        changed |= drawFontSelector();

        // Font size slider
        ImGui.spacing();
        changed |= drawFontSizeSlider();

        // Text (multiline)
        ImGui.spacing();
        ImGui.text("Text");
        String currentText = ComponentReflectionUtils.getString(component, "text", "");
        textBuffer.set(currentText);

        ImGui.setNextItemWidth(-1);
        if (ImGui.inputTextMultiline("##text", textBuffer, -1, 60, ImGuiInputTextFlags.AllowTabInput)) {
            ComponentReflectionUtils.setFieldValue(component, "text", textBuffer.get());
            component.markLayoutDirty();  // Force layout recalculation for UIDesigner
            changed = true;
        }
        if (ImGui.isItemActivated()) {
            textEditStartValue = currentText;
        }
        if (ImGui.isItemDeactivatedAfterEdit() && textEditStartValue != null) {
            String newText = ComponentReflectionUtils.getString(component, "text", "");
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "text", textEditStartValue, newText, entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }
            textEditStartValue = null;
        }

        // === APPEARANCE SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(MaterialIcons.Palette + " Appearance");
        ImGui.separator();

        // Color
        ImGui.spacing();
        changed |= FieldEditors.drawColor("Color", component, "color");

        // === ALIGNMENT SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(MaterialIcons.FormatAlignCenter + " Alignment");
        ImGui.separator();

        // Horizontal alignment
        ImGui.spacing();
        ImGui.text("Horizontal");
        ImGui.sameLine(100);

        String hAlign = getEnumValue("horizontalAlignment", "LEFT");
        int hIndex = indexOf(H_ALIGNMENTS, hAlign);

        ImGui.setNextItemWidth(120);
        ImInt hSelected = new ImInt(hIndex);
        if (ImGui.combo("##hAlign", hSelected, H_ALIGNMENTS)) {
            Object oldValue = ComponentReflectionUtils.getFieldValue(component, "horizontalAlignment");
            setEnumValue("horizontalAlignment", H_ALIGNMENTS[hSelected.get()]);
            Object newValue = ComponentReflectionUtils.getFieldValue(component, "horizontalAlignment");
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "horizontalAlignment", oldValue, newValue, entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }
            changed = true;
        }

        // Quick alignment buttons
        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.FormatAlignLeft)) {
            changed |= setEnumValueWithUndo("horizontalAlignment", "LEFT");
        }
        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.FormatAlignCenter)) {
            changed |= setEnumValueWithUndo("horizontalAlignment", "CENTER");
        }
        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.FormatAlignRight)) {
            changed |= setEnumValueWithUndo("horizontalAlignment", "RIGHT");
        }

        // Vertical alignment
        ImGui.text("Vertical");
        ImGui.sameLine(100);

        String vAlign = getEnumValue("verticalAlignment", "TOP");
        int vIndex = indexOf(V_ALIGNMENTS, vAlign);

        ImGui.setNextItemWidth(120);
        ImInt vSelected = new ImInt(vIndex);
        if (ImGui.combo("##vAlign", vSelected, V_ALIGNMENTS)) {
            Object oldValue = ComponentReflectionUtils.getFieldValue(component, "verticalAlignment");
            setEnumValue("verticalAlignment", V_ALIGNMENTS[vSelected.get()]);
            Object newValue = ComponentReflectionUtils.getFieldValue(component, "verticalAlignment");
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "verticalAlignment", oldValue, newValue, entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }
            changed = true;
        }

        // Quick vertical alignment buttons
        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.VerticalAlignTop)) {
            changed |= setEnumValueWithUndo("verticalAlignment", "TOP");
        }
        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.VerticalAlignCenter)) {
            changed |= setEnumValueWithUndo("verticalAlignment", "MIDDLE");
        }
        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.VerticalAlignBottom)) {
            changed |= setEnumValueWithUndo("verticalAlignment", "BOTTOM");
        }

        // Word wrap
        ImGui.spacing();
        boolean wordWrap = FieldEditors.getBoolean(component, "wordWrap", false);
        if (ImGui.checkbox("Word Wrap", wordWrap)) {
            boolean oldValue = wordWrap;
            boolean newValue = !wordWrap;
            ComponentReflectionUtils.setFieldValue(component, "wordWrap", newValue);
            component.markLayoutDirty();
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "wordWrap", oldValue, newValue, entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }
            changed = true;
        }

        // === AUTO-FIT SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(MaterialIcons.FitScreen + " Auto-Fit");
        ImGui.separator();

        ImGui.spacing();
        boolean autoFit = FieldEditors.getBoolean(component, "autoFit", false);
        if (ImGui.checkbox("Enable Best Fit", autoFit)) {
            boolean oldValue = autoFit;
            boolean newValue = !autoFit;
            ComponentReflectionUtils.setFieldValue(component, "autoFit", newValue);
            component.markLayoutDirty();
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "autoFit", oldValue, newValue, entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }
            changed = true;
            autoFit = newValue;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Automatically finds the largest font size that fits within bounds (Unity Best Fit)");
        }

        if (autoFit) {
            ImGui.indent();

            // Min font size
            ImGui.text("Min Size");
            ImGui.sameLine(100);
            int[] minSizeArr = { component.getMinFontSize() };
            ImGui.setNextItemWidth(-1);
            if (ImGui.sliderInt("##minFontSize", minSizeArr, 4, 72)) {
                component.setMinFontSize(minSizeArr[0]);
                component.markLayoutDirty();
                changed = true;
            }
            if (ImGui.isItemActivated()) {
                minFontSizeEditStart = component.getMinFontSize();
            }
            if (ImGui.isItemDeactivatedAfterEdit() && entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "minFontSize", minFontSizeEditStart, component.getMinFontSize(), entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }

            // Max font size
            ImGui.text("Max Size");
            ImGui.sameLine(100);
            int[] maxSizeArr = { component.getMaxFontSize() };
            ImGui.setNextItemWidth(-1);
            if (ImGui.sliderInt("##maxFontSize", maxSizeArr, 4, 72)) {
                component.setMaxFontSize(Math.max(component.getMinFontSize(), maxSizeArr[0]));
                component.markLayoutDirty();
                changed = true;
            }
            if (ImGui.isItemActivated()) {
                maxFontSizeEditStart = component.getMaxFontSize();
            }
            if (ImGui.isItemDeactivatedAfterEdit() && entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "maxFontSize", maxFontSizeEditStart, component.getMaxFontSize(), entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }

            ImGui.unindent();
        }

        // === SHADOW SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(MaterialIcons.Layers + " Shadow");
        ImGui.separator();

        ImGui.spacing();
        boolean shadow = FieldEditors.getBoolean(component, "shadow", false);
        if (ImGui.checkbox("Enable Shadow", shadow)) {
            boolean oldValue = shadow;
            boolean newValue = !shadow;
            ComponentReflectionUtils.setFieldValue(component, "shadow", newValue);
            component.markLayoutDirty();
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "shadow", oldValue, newValue, entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }
            changed = true;
            shadow = newValue;
        }

        if (shadow) {
            ImGui.indent();

            // Shadow color
            ImGui.text("Color");
            ImGui.sameLine(100);
            changed |= FieldEditors.drawColor("##shadowColor", component, "shadowColor");

            // Shadow offset
            ImGui.text("Offset");
            ImGui.sameLine(100);
            changed |= FieldEditors.drawVector2f("##shadowOffset", component, "shadowOffset", 0.5f);

            // Quick presets
            ImGui.spacing();
            ImGui.text("Presets:");
            ImGui.sameLine();

            if (ImGui.smallButton("Subtle")) {
                changed |= applyShadowPreset(new Vector2f(1, 1), new Vector4f(0, 0, 0, 0.3f));
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Normal")) {
                changed |= applyShadowPreset(new Vector2f(2, 2), new Vector4f(0, 0, 0, 0.5f));
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Strong")) {
                changed |= applyShadowPreset(new Vector2f(3, 3), new Vector4f(0, 0, 0, 0.8f));
            }

            ImGui.unindent();
        }

        return changed;
    }

    /**
     * Sets an enum field value with undo support.
     */
    private boolean setEnumValueWithUndo(String fieldName, String enumName) {
        Object oldValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
        setEnumValue(fieldName, enumName);
        Object newValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (entity != null && !java.util.Objects.equals(oldValue, newValue)) {
            UndoManager.getInstance().push(
                    new SetComponentFieldCommand(component, fieldName, oldValue, newValue, entity)
                            .withAfterApply(component::markLayoutDirty)
            );
        }
        return true;
    }

    /**
     * Applies a shadow preset with compound undo.
     */
    private boolean applyShadowPreset(Vector2f newOffset, Vector4f newColor) {
        Object oldOffset = ComponentReflectionUtils.getFieldValue(component, "shadowOffset");
        Object oldColor = ComponentReflectionUtils.getFieldValue(component, "shadowColor");
        // Copy old values before mutation
        Vector2f oldOffsetCopy = oldOffset instanceof Vector2f v ? new Vector2f(v) : new Vector2f();
        Vector4f oldColorCopy = oldColor instanceof Vector4f v ? new Vector4f(v) : new Vector4f();

        ComponentReflectionUtils.setFieldValue(component, "shadowOffset", newOffset);
        ComponentReflectionUtils.setFieldValue(component, "shadowColor", newColor);

        if (entity != null) {
            UndoManager.getInstance().push(
                    new CompoundCommand("Shadow Preset",
                            new SetComponentFieldCommand(component, "shadowOffset", oldOffsetCopy, new Vector2f(newOffset), entity),
                            new SetComponentFieldCommand(component, "shadowColor", oldColorCopy, new Vector4f(newColor), entity)
                    )
            );
        }
        return true;
    }

    private String getEnumValue(String fieldName, String defaultValue) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (value == null) return defaultValue;
        if (value instanceof Enum<?> e) return e.name();
        return value.toString();
    }

    private void setEnumValue(String fieldName, String enumName) {
        // Get the field's enum type and set the value
        var meta = ComponentReflectionUtils.getFieldMeta(component, fieldName);
        if (meta != null && meta.type().isEnum()) {
            for (Object constant : meta.type().getEnumConstants()) {
                if (constant.toString().equals(enumName)) {
                    ComponentReflectionUtils.setFieldValue(component, fieldName, constant);
                    return;
                }
            }
        }
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return 0;
    }

    private static final String FONT_NONE = "(None)";

    /**
     * Refreshes the cached list of available font paths.
     */
    private static void refreshFontList() {
        List<String> fonts = Assets.scanByType(Font.class);

        // Build paths list: null for (None), then actual fonts
        List<String> paths = new ArrayList<>();
        paths.add(null);  // Index 0: None
        paths.addAll(fonts);
        cachedFontPaths = paths;

        // Get default font from EditorConfig for labeling
        String defaultFont = null;
        try {
            EditorConfig config = ConfigLoader.getConfig(ConfigLoader.ConfigType.EDITOR);
            defaultFont = config.getDefaultUiFont();
        } catch (Exception ignored) {}

        // Build display names with "(default)" suffix where applicable
        List<String> names = new ArrayList<>();
        names.add(FONT_NONE);
        String finalDefaultFont = defaultFont;
        fonts.forEach(p -> {
            int lastSlash = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
            String displayName = lastSlash >= 0 ? p.substring(lastSlash + 1) : p;
            if (p.equals(finalDefaultFont)) {
                displayName += " (default)";
            }
            names.add(displayName);
        });
        cachedFontNames = names.toArray(new String[0]);
    }

    /**
     * Draws font path selector dropdown with refresh button.
     */
    private boolean drawFontSelector() {
        boolean changed = false;

        // Initialize cache if needed
        if (cachedFontPaths == null) {
            refreshFontList();
        }

        // Begin row highlight for missing required field
        boolean requiredHighlight = FieldEditorContext.beginRequiredRowHighlight("fontPath");

        ImGui.text("Font");
        ImGui.sameLine(100);

        // Refresh button
        if (ImGui.smallButton(MaterialIcons.Refresh + "##refreshFonts")) {
            refreshFontList();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh font list");
        }
        ImGui.sameLine();

        // Find current index
        String currentPath = component.getFontPath();
        int currentIndex = currentPath != null ? cachedFontPaths.indexOf(currentPath) : 0;
        if (currentIndex < 0) currentIndex = 0;  // Not found = show (None)

        ImGui.setNextItemWidth(-1);
        ImInt selected = new ImInt(currentIndex);
        if (ImGui.combo("##fontPath", selected, cachedFontNames)) {
            int newIndex = selected.get();
            String oldPath = component.getFontPath();
            String newPath = (newIndex == 0) ? null : cachedFontPaths.get(newIndex);

            // Apply change with undo support
            component.setFontPath(newPath);
            component.markLayoutDirty();

            // Register undo command (push, not execute — value already applied above)
            EditorGameObject entity = FieldEditorContext.getEntity();
            if (entity != null) {
                UndoManager.getInstance().push(
                    new SetComponentFieldCommand(component, "fontPath", oldPath, newPath, entity)
                            .withAfterApply(component::markLayoutDirty)
                );
            }
            changed = true;
        }

        // End row highlight
        FieldEditorContext.endRequiredRowHighlight(requiredHighlight);

        // Tooltip
        if (ImGui.isItemHovered()) {
            if (FieldEditorContext.isFieldRequiredAndMissing("fontPath")) {
                ImGui.setTooltip("Required: No font selected - text will not render");
            } else if (currentPath != null) {
                ImGui.setTooltip(currentPath);
            }
        }

        return changed;
    }

    /**
     * Draws font size slider or computed size when autoFit is enabled.
     */
    private boolean drawFontSizeSlider() {
        boolean changed = false;
        boolean autoFit = FieldEditors.getBoolean(component, "autoFit", false);

        ImGui.text("Size");
        ImGui.sameLine(100);

        if (autoFit) {
            // Show computed size as read-only when autoFit is enabled
            int computedSize = component.getComputedFontSize();
            ImGui.text(computedSize + " (auto)");
        } else {
            int[] sizeArr = { component.getFontSize() };
            ImGui.setNextItemWidth(-1);
            if (ImGui.sliderInt("##fontSize", sizeArr, 8, 72)) {
                component.setFontSize(sizeArr[0]);
                component.markLayoutDirty();
                changed = true;
            }
            if (ImGui.isItemActivated()) {
                fontSizeEditStart = component.getFontSize();
            }
            if (ImGui.isItemDeactivatedAfterEdit() && entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "fontSize", fontSizeEditStart, component.getFontSize(), entity)
                                .withAfterApply(component::markLayoutDirty)
                );
            }
        }

        return changed;
    }
}
