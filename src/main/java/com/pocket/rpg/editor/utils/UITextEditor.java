package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.ui.text.Font;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.Map;

/**
 * Custom editor for UIText component.
 * Organizes fields into logical sections.
 */
public class UITextEditor implements CustomComponentEditor {

    private static final String[] H_ALIGNMENTS = {"LEFT", "CENTER", "RIGHT"};
    private static final String[] V_ALIGNMENTS = {"TOP", "MIDDLE", "BOTTOM"};

    private final ImString textBuffer = new ImString(1024);

    @Override
    public boolean draw(ComponentData data, EditorEntity entity) {
        Map<String, Object> fields = data.getFields();
        boolean changed = false;

        // === CONTENT SECTION ===
        ImGui.text(FontAwesomeIcons.Font + " Content");
        ImGui.separator();

        // Font
        ImGui.spacing();
        changed |= FieldEditors.drawAsset("Font", fields, "font", Font.class, data, entity);

        // Text (multiline)
        ImGui.spacing();
        ImGui.text("Text");
        String currentText = fields.get("text") != null ? fields.get("text").toString() : "";
        textBuffer.set(currentText);
        
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputTextMultiline("##text", textBuffer, -1, 60, ImGuiInputTextFlags.AllowTabInput)) {
            fields.put("text", textBuffer.get());
            changed = true;
        }

        // === APPEARANCE SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.Palette + " Appearance");
        ImGui.separator();

        // Color
        ImGui.spacing();
        changed |= FieldEditors.drawColor("Color", fields, "color");

        // === ALIGNMENT SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.AlignCenter + " Alignment");
        ImGui.separator();

        // Horizontal alignment
        ImGui.spacing();
        ImGui.text("Horizontal");
        ImGui.sameLine(100);
        
        String hAlign = getEnumValue(fields, "horizontalAlignment", "LEFT");
        int hIndex = indexOf(H_ALIGNMENTS, hAlign);
        
        ImGui.setNextItemWidth(120);
        ImInt hSelected = new ImInt(hIndex);
        if (ImGui.combo("##hAlign", hSelected, H_ALIGNMENTS)) {
            fields.put("horizontalAlignment", H_ALIGNMENTS[hSelected.get()]);
            changed = true;
        }

        // Quick alignment buttons
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.AlignLeft)) {
            fields.put("horizontalAlignment", "LEFT");
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.AlignCenter)) {
            fields.put("horizontalAlignment", "CENTER");
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.AlignRight)) {
            fields.put("horizontalAlignment", "RIGHT");
            changed = true;
        }

        // Vertical alignment
        ImGui.text("Vertical");
        ImGui.sameLine(100);
        
        String vAlign = getEnumValue(fields, "verticalAlignment", "TOP");
        int vIndex = indexOf(V_ALIGNMENTS, vAlign);
        
        ImGui.setNextItemWidth(120);
        ImInt vSelected = new ImInt(vIndex);
        if (ImGui.combo("##vAlign", vSelected, V_ALIGNMENTS)) {
            fields.put("verticalAlignment", V_ALIGNMENTS[vSelected.get()]);
            changed = true;
        }

        // Word wrap
        ImGui.spacing();
        boolean wordWrap = getBool(fields, "wordWrap", false);
        if (ImGui.checkbox("Word Wrap", wordWrap)) {
            fields.put("wordWrap", !wordWrap);
            changed = true;
        }

        // === AUTO-FIT SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.ExpandAlt + " Auto-Fit");
        ImGui.separator();

        ImGui.spacing();
        boolean autoFit = getBool(fields, "autoFit", false);
        if (ImGui.checkbox("Enable Auto-Fit", autoFit)) {
            fields.put("autoFit", !autoFit);
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
            
            float minScale = FieldEditors.getFloat(fields, "minScale", 0.5f);
            float maxScale = FieldEditors.getFloat(fields, "maxScale", 1.0f);
            float[] range = {minScale, maxScale};
            
            ImGui.setNextItemWidth(-1);
            if (ImGui.dragFloat2("##scaleRange", range, 0.01f, 0.1f, 5.0f, "%.2f")) {
                fields.put("minScale", Math.max(0.1f, range[0]));
                fields.put("maxScale", Math.max(range[0], range[1]));
                changed = true;
            }

            // Maintain aspect ratio
            boolean maintainAspect = getBool(fields, "maintainAspectRatio", true);
            if (ImGui.checkbox("Maintain Aspect Ratio", maintainAspect)) {
                fields.put("maintainAspectRatio", !maintainAspect);
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
        boolean shadow = getBool(fields, "shadow", false);
        if (ImGui.checkbox("Enable Shadow", shadow)) {
            fields.put("shadow", !shadow);
            changed = true;
            shadow = !shadow;
        }

        if (shadow) {
            ImGui.indent();

            // Shadow color
            ImGui.text("Color");
            ImGui.sameLine(100);
            changed |= FieldEditors.drawColor("##shadowColor", fields, "shadowColor");

            // Shadow offset
            ImGui.text("Offset");
            ImGui.sameLine(100);
            changed |= FieldEditors.drawVector2f("##shadowOffset", fields, "shadowOffset", 0.5f);

            // Quick presets
            ImGui.spacing();
            ImGui.text("Presets:");
            ImGui.sameLine();
            
            if (ImGui.smallButton("Subtle")) {
                fields.put("shadowOffset", new org.joml.Vector2f(1, 1));
                fields.put("shadowColor", new org.joml.Vector4f(0, 0, 0, 0.3f));
                changed = true;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Normal")) {
                fields.put("shadowOffset", new org.joml.Vector2f(2, 2));
                fields.put("shadowColor", new org.joml.Vector4f(0, 0, 0, 0.5f));
                changed = true;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Strong")) {
                fields.put("shadowOffset", new org.joml.Vector2f(3, 3));
                fields.put("shadowColor", new org.joml.Vector4f(0, 0, 0, 0.8f));
                changed = true;
            }

            ImGui.unindent();
        }

        return changed;
    }

    private String getEnumValue(Map<String, Object> fields, String key, String defaultValue) {
        Object value = fields.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Enum<?> e) return e.name();
        return value.toString();
    }

    private boolean getBool(Map<String, Object> fields, String key, boolean defaultValue) {
        Object value = fields.get(key);
        if (value instanceof Boolean b) return b;
        return defaultValue;
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return 0;
    }
}
