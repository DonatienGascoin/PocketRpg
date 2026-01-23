package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ListItemCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * Field editor for List&lt;T&gt; fields.
 * Supports String, primitives, and asset types as element types.
 */
public final class ListEditor {

    private ListEditor() {}

    /**
     * Draws a list field editor.
     *
     * @param label     Display label
     * @param component The component containing the list field
     * @param meta      Field metadata (must have elementType set)
     * @param entity    The editor entity for undo support
     * @return true if any element was changed
     */
    @SuppressWarnings("unchecked")
    public static boolean drawList(String label, Component component,
                                   FieldMeta meta, EditorGameObject entity) {
        Class<?> elementType = meta.elementType();
        if (elementType == null) {
            ImGui.textDisabled(label + ": List<?>");
            return false;
        }

        List<Object> list = (List<Object>) ComponentReflectionUtils.getFieldValue(component, meta.name());
        boolean changed = false;

        // Ensure list exists (initialize if null)
        if (list == null) {
            list = new ArrayList<>();
            ComponentReflectionUtils.setFieldValue(component, meta.name(), list);
        }

        int count = list.size();
        String headerLabel = label + " (" + count + ")";

        // Header with collapsible tree node
        ImGui.pushID(meta.name());

        int flags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap;
        boolean isOpen = ImGui.treeNodeEx(headerLabel, flags);

        // Add button on same line as header
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
        if (ImGui.smallButton(MaterialIcons.Add + "##add")) {
            changed = addItem(component, meta, list, elementType, entity);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add item");
        }

        if (isOpen) {
            if (list.isEmpty()) {
                ImGui.textDisabled("Empty");
            } else {
                // Draw elements
                int removeIndex = -1;
                List<Object> finalList = list;
                for (int i = 0; i < list.size(); i++) {
                    ImGui.pushID(i);

                    // Index label
                    ImGui.alignTextToFramePadding();
                    ImGui.text(String.valueOf(i));
                    ImGui.sameLine();

                    // Element editor (takes most of the width)
                    String stateKey = meta.name() + "[" + i + "]@" + System.identityHashCode(component);
                    float availWidth = ImGui.getContentRegionAvailX();
                    ImGui.setNextItemWidth(availWidth - 30);

                    final int idx = i;
                    changed |= drawElement(elementType, list, idx, stateKey,
                            () -> setElementWithUndo(finalList, idx, component, meta.name(), entity));

                    // Remove button
                    ImGui.sameLine();
                    if (ImGui.smallButton(MaterialIcons.Close + "##rem")) {
                        removeIndex = i;
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Remove item");
                    }

                    ImGui.popID();
                }

                // Process removal after iteration
                if (removeIndex >= 0) {
                    removeItem(component, meta, list, removeIndex, entity);
                    changed = true;
                }
            }

            ImGui.treePop();
        }

        ImGui.popID();
        return changed;
    }

    private static boolean drawElement(Class<?> type, List<Object> list, int index,
                                        String stateKey, Runnable onChanged) {
        if (type == String.class) {
            return PrimitiveEditors.drawString("##elem", stateKey,
                    () -> (String) list.get(index),
                    val -> {
                        list.set(index, val);
                        onChanged.run();
                    });
        } else if (type == int.class || type == Integer.class) {
            return PrimitiveEditors.drawInt("##elem", stateKey,
                    () -> list.get(index) != null ? (Integer) list.get(index) : 0,
                    val -> {
                        list.set(index, val);
                        onChanged.run();
                    });
        } else if (type == float.class || type == Float.class) {
            return PrimitiveEditors.drawFloat("##elem", stateKey,
                    () -> list.get(index) != null ? ((Float) list.get(index)).doubleValue() : 0.0,
                    val -> {
                        list.set(index, (float) val);
                        onChanged.run();
                    }, 0.1f);
        } else if (type == double.class || type == Double.class) {
            return PrimitiveEditors.drawFloat("##elem", stateKey,
                    () -> list.get(index) != null ? (Double) list.get(index) : 0.0,
                    val -> {
                        list.set(index, val);
                        onChanged.run();
                    }, 0.1f);
        } else if (type == boolean.class || type == Boolean.class) {
            return PrimitiveEditors.drawBoolean("##elem", stateKey,
                    () -> list.get(index) != null && (Boolean) list.get(index),
                    val -> {
                        list.set(index, val);
                        onChanged.run();
                    });
        } else if (type.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> enumClass = (Class<Enum>) type;
            return EnumEditor.drawEnum("##elem", stateKey,
                    () -> (Enum) list.get(index),
                    val -> {
                        list.set(index, val);
                        onChanged.run();
                    }, enumClass);
        } else if (Assets.isAssetType(type)) {
            @SuppressWarnings("unchecked")
            Class<Object> assetClass = (Class<Object>) type;
            return AssetEditor.drawAsset("##elem", stateKey,
                    () -> list.get(index),
                    val -> {
                        list.set(index, val);
                        onChanged.run();
                    }, assetClass);
        }

        // Unsupported type
        Object value = list.get(index);
        ImGui.textDisabled(value != null ? value.toString() : "(null)");
        return false;
    }

    private static boolean addItem(Component component, FieldMeta meta, List<Object> list,
                                    Class<?> elementType, EditorGameObject entity) {
        Object newItem = createDefaultValue(elementType);
        int index = list.size();

        UndoManager.getInstance().execute(
                new ListItemCommand(component, meta.name(),
                        ListItemCommand.Operation.ADD, index,
                        null, newItem, entity)
        );
        return true;
    }

    private static void removeItem(Component component, FieldMeta meta, List<Object> list,
                                    int index, EditorGameObject entity) {
        Object oldValue = list.get(index);

        UndoManager.getInstance().execute(
                new ListItemCommand(component, meta.name(),
                        ListItemCommand.Operation.REMOVE, index,
                        oldValue, null, entity)
        );
    }

    private static void setElementWithUndo(List<Object> list, int index,
                                            Component component, String fieldName,
                                            EditorGameObject entity) {
        // Note: The undo command for element changes is handled by the primitive editors
        // which push their own undo commands. Scene dirty tracking is handled elsewhere.
    }

    private static Object createDefaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == int.class || type == Integer.class) return 0;
        if (type == float.class || type == Float.class) return 0f;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == long.class || type == Long.class) return 0L;
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length > 0 ? constants[0] : null;
        }
        // For asset types and other objects, return null
        return null;
    }
}
