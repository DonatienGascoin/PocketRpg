package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.ui.text.Font;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Custom editor for UIText component.
 * Organizes fields into logical sections.
 */
public class UITextInspector implements CustomComponentInspector {

    private static final String[] H_ALIGNMENTS = {"LEFT", "CENTER", "RIGHT"};
    private static final String[] V_ALIGNMENTS = {"TOP", "MIDDLE", "BOTTOM"};

    private final ImString textBuffer = new ImString(1024);

    @Override
    public boolean draw(Component component, EditorGameObject entity) {
        boolean changed = false;

        // === CONTENT SECTION ===
        ImGui.text(FontAwesomeIcons.Font + " Content");
        ImGui.separator();

        // Font
        ImGui.spacing();
        changed |= FieldEditors.drawAsset("Font", component, "font", Font.class, entity);

        // Text (multiline)
        ImGui.spacing();
        ImGui.text("Text");
        String currentText = ComponentReflectionUtils.getString(component, "text", "");
        textBuffer.set(currentText);

        ImGui.setNextItemWidth(-1);
        if (ImGui.inputTextMultiline("##text", textBuffer, -1, 60, ImGuiInputTextFlags.AllowTabInput)) {
            ComponentReflectionUtils.setFieldValue(component, "text", textBuffer.get());
            changed = true;
        }

        // === APPEARANCE SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.Palette + " Appearance");
        ImGui.separator();

        // Color
        ImGui.spacing();
        changed |= FieldEditors.drawColor("Color", component, "color");

        // === ALIGNMENT SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.AlignCenter + " Alignment");
        ImGui.separator();

        // Horizontal alignment
        ImGui.spacing();
        ImGui.text("Horizontal");
        ImGui.sameLine(100);

        String hAlign = getEnumValue(component, "horizontalAlignment", "LEFT");
        int hIndex = indexOf(H_ALIGNMENTS, hAlign);

        ImGui.setNextItemWidth(120);
        ImInt hSelected = new ImInt(hIndex);
        if (ImGui.combo("##hAlign", hSelected, H_ALIGNMENTS)) {
            setEnumValue(component, "horizontalAlignment", H_ALIGNMENTS[hSelected.get()]);
            changed = true;
        }

        // Quick alignment buttons
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.AlignLeft)) {
            setEnumValue(component, "horizontalAlignment", "LEFT");
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.AlignCenter)) {
            setEnumValue(component, "horizontalAlignment", "CENTER");
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.AlignRight)) {
            setEnumValue(component, "horizontalAlignment", "RIGHT");
            changed = true;
        }

        // Vertical alignment
        ImGui.text("Vertical");
        ImGui.sameLine(100);

        String vAlign = getEnumValue(component, "verticalAlignment", "TOP");
        int vIndex = indexOf(V_ALIGNMENTS, vAlign);

        ImGui.setNextItemWidth(120);
        ImInt vSelected = new ImInt(vIndex);
        if (ImGui.combo("##vAlign", vSelected, V_ALIGNMENTS)) {
            setEnumValue(component, "verticalAlignment", V_ALIGNMENTS[vSelected.get()]);
            changed = true;
        }

        // Word wrap
        ImGui.spacing();
        boolean wordWrap = FieldEditors.getBoolean(component, "wordWrap", false);
        if (ImGui.checkbox("Word Wrap", wordWrap)) {
            ComponentReflectionUtils.setFieldValue(component, "wordWrap", !wordWrap);
            changed = true;
        }

        // === AUTO-FIT SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.ExpandAlt + " Auto-Fit");
        ImGui.separator();

        ImGui.spacing();
        boolean autoFit = FieldEditors.getBoolean(component, "autoFit", false);
        if (ImGui.checkbox("Enable Auto-Fit", autoFit)) {
            ComponentReflectionUtils.setFieldValue(component, "autoFit", !autoFit);
            changed = true;
            autoFit = !autoFit;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Scale text to fit within UITransform bounds");
        }

        if (autoFit) {
            ImGui.indent();

            // Min/Max scale
            ImGui.text("Scale Range");
            ImGui.sameLine(100);

            float minScale = FieldEditors.getFloat(component, "minScale", 0.5f);
            float maxScale = FieldEditors.getFloat(component, "maxScale", 1.0f);
            float[] range = {minScale, maxScale};

            ImGui.setNextItemWidth(-1);
            if (ImGui.dragFloat2("##scaleRange", range, 0.01f, 0.1f, 5.0f, "%.2f")) {
                ComponentReflectionUtils.setFieldValue(component, "minScale", Math.max(0.1f, range[0]));
                ComponentReflectionUtils.setFieldValue(component, "maxScale", Math.max(range[0], range[1]));
                changed = true;
            }

            // Maintain aspect ratio
            boolean maintainAspect = FieldEditors.getBoolean(component, "maintainAspectRatio", true);
            if (ImGui.checkbox("Maintain Aspect Ratio", maintainAspect)) {
                ComponentReflectionUtils.setFieldValue(component, "maintainAspectRatio", !maintainAspect);
                changed = true;
            }

            ImGui.unindent();
        }

        // === SHADOW SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.Clone + " Shadow");
        ImGui.separator();

        ImGui.spacing();
        boolean shadow = FieldEditors.getBoolean(component, "shadow", false);
        if (ImGui.checkbox("Enable Shadow", shadow)) {
            ComponentReflectionUtils.setFieldValue(component, "shadow", !shadow);
            changed = true;
            shadow = !shadow;
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
                ComponentReflectionUtils.setFieldValue(component, "shadowOffset", new Vector2f(1, 1));
                ComponentReflectionUtils.setFieldValue(component, "shadowColor", new Vector4f(0, 0, 0, 0.3f));
                changed = true;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Normal")) {
                ComponentReflectionUtils.setFieldValue(component, "shadowOffset", new Vector2f(2, 2));
                ComponentReflectionUtils.setFieldValue(component, "shadowColor", new Vector4f(0, 0, 0, 0.5f));
                changed = true;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Strong")) {
                ComponentReflectionUtils.setFieldValue(component, "shadowOffset", new Vector2f(3, 3));
                ComponentReflectionUtils.setFieldValue(component, "shadowColor", new Vector4f(0, 0, 0, 0.8f));
                changed = true;
            }

            ImGui.unindent();
        }

        return changed;
    }

    private String getEnumValue(Component component, String fieldName, String defaultValue) {
        Object value = ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (value == null) return defaultValue;
        if (value instanceof Enum<?> e) return e.name();
        return value.toString();
    }

    private void setEnumValue(Component component, String fieldName, String enumName) {
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
}
