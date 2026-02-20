package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.MapItemCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.*;

/**
 * Field editor for {@code Map<K, V>} fields.
 * Supports String keys with String, int, float, double, and boolean values.
 * Handles undo automatically via {@link MapItemCommand}.
 */
public final class MapEditor {

    private static final ImString keyBuffer = new ImString(256);
    private static final ImString valueStringBuffer = new ImString(256);
    private static final float[] floatBuffer = new float[1];
    private static final int[] intBuffer = new int[1];

    /** Tracks start values for drag/input undo (keyed by component+field+mapKey). */
    private static final Map<String, Object> undoStartValues = new HashMap<>();

    private MapEditor() {}

    /**
     * Draws a map field editor.
     *
     * @param label     Display label
     * @param component The component containing the map field
     * @param meta      Field metadata (must have keyType and valueType set)
     * @param entity    The editor entity for undo support
     * @return true if any entry was changed
     */
    @SuppressWarnings("unchecked")
    public static boolean drawMap(String label, Component component,
                                   FieldMeta meta, EditorGameObject entity) {
        Class<?> keyType = meta.keyType();
        Class<?> valueType = meta.valueType();
        if (keyType == null || valueType == null) {
            ImGui.textDisabled(label + ": Map<?,?>");
            return false;
        }

        // Only String keys are supported for now
        if (keyType != String.class) {
            ImGui.textDisabled(label + ": Map<" + keyType.getSimpleName() + ",?> (unsupported key type)");
            return false;
        }

        Map<String, Object> map = (Map<String, Object>) ComponentReflectionUtils.getFieldValue(component, meta.name());
        boolean changed = false;

        if (map == null) {
            map = new LinkedHashMap<>();
            ComponentReflectionUtils.setFieldValue(component, meta.name(), map);
        }

        int count = map.size();
        String headerLabel = label + " (" + count + ")";

        ImGui.pushID(meta.name());

        int flags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap;
        boolean isOpen = ImGui.treeNodeEx(headerLabel, flags);

        // Add button on same line as header
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
        if (ImGui.smallButton(MaterialIcons.Add + "##add")) {
            changed = addEntry(component, meta, map, valueType, entity);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add entry");
        }

        if (isOpen) {
            if (map.isEmpty()) {
                ImGui.textDisabled("Empty");
            } else {
                String removeKey = null;

                // Iterate over a snapshot to avoid concurrent modification
                List<Map.Entry<String, Object>> entries = new ArrayList<>(map.entrySet());
                for (Map.Entry<String, Object> entry : entries) {
                    String mapKey = entry.getKey();
                    Object mapValue = entry.getValue();

                    ImGui.pushID(mapKey);

                    // Value editor
                    String undoKey = FieldUndoTracker.undoKey(component, meta.name() + "[" + mapKey + "]");
                    changed |= drawValue(mapKey, valueType, map, mapKey, undoKey, component, meta.name(), entity);

                    // Remove button
                    ImGui.sameLine();
                    if (ImGui.smallButton(MaterialIcons.Close + "##rem")) {
                        removeKey = mapKey;
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Remove entry");
                    }

                    ImGui.popID();
                }

                // Process removal after iteration
                if (removeKey != null) {
                    removeEntry(component, meta, map, removeKey, entity);
                    changed = true;
                }
            }

            ImGui.treePop();
        }

        ImGui.popID();
        return changed;
    }

    private static boolean drawValue(String label, Class<?> valueType,
                                      Map<String, Object> map, String mapKey,
                                      String undoKey, Component component,
                                      String fieldName, EditorGameObject entity) {
        if (valueType == String.class) {
            String current = map.get(mapKey) != null ? map.get(mapKey).toString() : "";
            valueStringBuffer.set(current);

            FieldEditorUtils.inspectorRow(label, () -> {
                ImGui.inputText("##val", valueStringBuffer);
            });

            if (ImGui.isItemActivated()) {
                undoStartValues.put(undoKey, current);
            }
            if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(undoKey)) {
                String oldVal = (String) undoStartValues.remove(undoKey);
                String newVal = valueStringBuffer.get();
                if (!Objects.equals(oldVal, newVal)) {
                    map.put(mapKey, newVal);
                    if (entity != null) {
                        UndoManager.getInstance().push(
                                new MapItemCommand(component, fieldName,
                                        MapItemCommand.Operation.PUT, mapKey,
                                        oldVal, newVal, entity)
                        );
                    }
                    return true;
                }
            } else {
                // Apply live value while editing
                String bufVal = valueStringBuffer.get();
                if (!Objects.equals(current, bufVal)) {
                    map.put(mapKey, bufVal);
                    return true;
                }
            }
        } else if (valueType == int.class || valueType == Integer.class) {
            int current = map.get(mapKey) instanceof Number n ? n.intValue() : 0;
            intBuffer[0] = current;

            FieldEditorUtils.inspectorRow(label, () -> {
                ImGui.dragInt("##val", intBuffer);
            });

            if (ImGui.isItemActivated()) {
                undoStartValues.put(undoKey, current);
            }
            if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(undoKey)) {
                int oldVal = (int) undoStartValues.remove(undoKey);
                int newVal = intBuffer[0];
                if (oldVal != newVal) {
                    map.put(mapKey, newVal);
                    if (entity != null) {
                        UndoManager.getInstance().push(
                                new MapItemCommand(component, fieldName,
                                        MapItemCommand.Operation.PUT, mapKey,
                                        oldVal, newVal, entity)
                        );
                    }
                    return true;
                }
            } else if (intBuffer[0] != current) {
                map.put(mapKey, intBuffer[0]);
                return true;
            }
        } else if (valueType == float.class || valueType == Float.class
                || valueType == double.class || valueType == Double.class) {
            float current = map.get(mapKey) instanceof Number n ? n.floatValue() : 0f;
            floatBuffer[0] = current;

            FieldEditorUtils.inspectorRow(label, () -> {
                ImGui.dragFloat("##val", floatBuffer, 0.1f);
            });

            if (ImGui.isItemActivated()) {
                undoStartValues.put(undoKey, current);
            }
            if (ImGui.isItemDeactivatedAfterEdit() && undoStartValues.containsKey(undoKey)) {
                float oldVal = (float) undoStartValues.remove(undoKey);
                float newVal = floatBuffer[0];
                if (oldVal != newVal) {
                    Object boxed = valueType == double.class || valueType == Double.class
                            ? (double) newVal : newVal;
                    Object boxedOld = valueType == double.class || valueType == Double.class
                            ? (double) oldVal : oldVal;
                    map.put(mapKey, boxed);
                    if (entity != null) {
                        UndoManager.getInstance().push(
                                new MapItemCommand(component, fieldName,
                                        MapItemCommand.Operation.PUT, mapKey,
                                        boxedOld, boxed, entity)
                        );
                    }
                    return true;
                }
            } else if (floatBuffer[0] != current) {
                Object boxed = valueType == double.class || valueType == Double.class
                        ? (double) floatBuffer[0] : floatBuffer[0];
                map.put(mapKey, boxed);
                return true;
            }
        } else if (valueType == boolean.class || valueType == Boolean.class) {
            boolean current = map.get(mapKey) instanceof Boolean b && b;
            if (ImGui.checkbox(label, current)) {
                boolean newVal = !current;
                map.put(mapKey, newVal);
                if (entity != null) {
                    UndoManager.getInstance().push(
                            new MapItemCommand(component, fieldName,
                                    MapItemCommand.Operation.PUT, mapKey,
                                    current, newVal, entity)
                    );
                }
                return true;
            }
        } else {
            // Unsupported value type
            Object value = map.get(mapKey);
            FieldEditorUtils.inspectorRow(label, () -> {
                ImGui.textDisabled(value != null ? value.toString() : "(null)");
            });
        }

        return false;
    }

    private static boolean addEntry(Component component, FieldMeta meta,
                                     Map<String, Object> map, Class<?> valueType,
                                     EditorGameObject entity) {
        // Generate a unique key
        String newKey = "key" + map.size();
        int suffix = 0;
        while (map.containsKey(newKey)) {
            newKey = "key" + (map.size() + ++suffix);
        }

        Object defaultValue = createDefaultValue(valueType);

        if (entity != null) {
            UndoManager.getInstance().execute(
                    new MapItemCommand(component, meta.name(),
                            MapItemCommand.Operation.PUT, newKey,
                            null, defaultValue, entity)
            );
        } else {
            map.put(newKey, defaultValue);
        }
        return true;
    }

    private static void removeEntry(Component component, FieldMeta meta,
                                     Map<String, Object> map, String key,
                                     EditorGameObject entity) {
        Object oldValue = map.get(key);

        if (entity != null) {
            UndoManager.getInstance().execute(
                    new MapItemCommand(component, meta.name(),
                            MapItemCommand.Operation.REMOVE, key,
                            oldValue, null, entity)
            );
        } else {
            map.remove(key);
        }
    }

    private static Object createDefaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == int.class || type == Integer.class) return 0;
        if (type == float.class || type == Float.class) return 0f;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        return null;
    }
}
