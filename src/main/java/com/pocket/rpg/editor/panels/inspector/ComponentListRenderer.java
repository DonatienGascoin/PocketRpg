package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.ComponentBrowserPopup;
import com.pocket.rpg.editor.scene.DirtyTracker;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddComponentCommand;
import com.pocket.rpg.editor.undo.commands.RemoveComponentCommand;
import com.pocket.rpg.editor.undo.commands.ToggleComponentEnabledCommand;
import com.pocket.rpg.editor.ui.inspectors.ComponentKeyField;
import com.pocket.rpg.editor.undo.commands.SwapTransformCommand;
import com.pocket.rpg.editor.utils.TransformSwapHelper;
import com.pocket.rpg.serialization.ComponentRegistry;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;

import java.util.List;

/**
 * Renders a component list with add/remove functionality.
 * Used by both EntityInspector (scene editing) and PrefabInspector (prefab editing).
 */
public class ComponentListRenderer {

    private final ComponentFieldEditor fieldEditor;
    private final ComponentBrowserPopup componentBrowserPopup;

    public ComponentListRenderer(ComponentFieldEditor fieldEditor,
                                 ComponentBrowserPopup componentBrowserPopup) {
        this.fieldEditor = fieldEditor;
        this.componentBrowserPopup = componentBrowserPopup;
    }

    /**
     * Renders the component list for an entity.
     *
     * @param entity                The entity whose components to render
     * @param isPrefabInstance      Whether the entity is a prefab instance (affects override display)
     * @param allowStructuralChanges Whether add/remove component buttons are shown
     * @param dirtyTracker          Called when any change is made
     */
    public void render(EditorGameObject entity, boolean isPrefabInstance,
                       boolean allowStructuralChanges, DirtyTracker dirtyTracker) {
        List<Component> components = entity.getComponents();

        if (components.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled("No components");
            ImGui.spacing();
        } else {
            Component toRemove = null;

            float padding = ImGui.getStyle().getWindowPaddingX();

            for (int i = 0; i < components.size(); i++) {
                Component comp = components.get(i);
                ImGui.pushID(i);

                String componentType = comp.getClass().getName();
                String label = comp.getClass().getSimpleName();
                if (isPrefabInstance && !entity.getOverriddenFields(componentType).isEmpty()) {
                    label += " *";
                }

                boolean compEnabled = comp.isOwnEnabled();
                boolean isTransform = comp instanceof Transform;

                // Mute disabled component headers
                if (!compEnabled) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.7f);
                }

                // Pad label to leave room for checkbox on the left
                boolean showEnabledCheckbox = !isTransform && allowStructuralChanges;
                String headerLabel = showEnabledCheckbox ? "     " + label : label;

                // Extend header to left window edge
                ImGui.indent(-padding);
                boolean open = ImGui.collapsingHeader(headerLabel, ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap);
                ImGui.indent(padding);

                if (!compEnabled) {
                    ImGui.popStyleColor();
                }

                if (allowStructuralChanges) {
                    String dependent = findDependentComponent(entity, comp);

                    // Enabled checkbox overlaid on left of header (not for Transform)
                    if (showEnabledCheckbox) {
                        float headerH = ImGui.getItemRectMaxY() - ImGui.getItemRectMinY();
                        ImGui.sameLine(padding + 16);
                        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 0, 0);
                        float checkboxH = ImGui.getFrameHeight();
                        float curY = ImGui.getCursorPosY();
                        ImGui.setCursorPosY(curY + (headerH - checkboxH) * 0.5f);
                        ImBoolean enabledRef = new ImBoolean(compEnabled);
                        if (ImGui.checkbox("##compEnabled", enabledRef)) {
                            UndoManager.getInstance().execute(
                                    new ToggleComponentEnabledCommand(comp, enabledRef.get()));
                            dirtyTracker.markDirty();
                        }
                        ImGui.popStyleVar();
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(compEnabled ? "Disable component" : "Enable component");
                        }
                    }

                    // Calculate button positions (right to left: remove, key, swap)
                    float removeButtonX = ImGui.getContentRegionAvailX() - 20;
                    float keyButtonX = removeButtonX - 25;
                    float swapButtonX = keyButtonX - 25;

                    // Render swap button for Transform components
                    if (isTransform) {
                        renderTransformSwapButton(entity, comp, swapButtonX, dirtyTracker);
                    }

                    // Render key toggle button
                    ImGui.sameLine(keyButtonX);
                    ComponentKeyField.drawHeaderButton(comp);

                    // Render remove button
                    ImGui.sameLine(removeButtonX);
                    if (isTransform) {
                        // Transform cannot be removed
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
                        ImGui.smallButton(MaterialIcons.Close + "##remove");
                        ImGui.popStyleColor(2);
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("Transform cannot be removed");
                        }
                    } else if (dependent != null) {
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
                        ImGui.smallButton(MaterialIcons.Close + "##remove");
                        ImGui.popStyleColor(2);
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("Required by " + dependent);
                        }
                    } else {
                        ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
                        if (ImGui.smallButton(MaterialIcons.Close + "##remove")) toRemove = comp;
                        ImGui.popStyleColor();
                        if (ImGui.isItemHovered()) ImGui.setTooltip("Remove component");
                    }
                }

                if (open) {
                    if (fieldEditor.renderComponentFields(entity, comp, isPrefabInstance)) {
                        dirtyTracker.markDirty();
                    }
                }
                ImGui.popID();
            }

            if (toRemove != null) {
                UndoManager.getInstance().execute(new RemoveComponentCommand(entity, toRemove));
                dirtyTracker.markDirty();
            }
        }

        if (allowStructuralChanges) {
            ImGui.spacing();
            float y = ImGui.getCursorScreenPosY();
            float x1 = ImGui.getWindowPosX();
            float x2 = x1 + ImGui.getWindowSizeX();
            ImGui.getWindowDrawList().addLine(x1, y, x2, y, ImGui.getColorU32(ImGuiCol.Separator), 2.0f);
            ImGui.dummy(0, 1);

            ImGui.dummy(new ImVec2(0.0f, 10.0f));
            ImGui.setCursorPosX((ImGui.getContentRegionAvailX() / 2) - 75f);
            if (ImGui.button("Add Component", 150, 30)) {
                componentBrowserPopup.open(meta -> {
                    Component component = ComponentRegistry.instantiateByClassName(meta.className());
                    if (component != null) {
                        UndoManager.getInstance().execute(new AddComponentCommand(entity, component));
                        dirtyTracker.markDirty();
                    }
                });
            }
            ImGui.dummy(new ImVec2(0.0f, 10.0f));
        }
    }


    /**
     * Renders the ComponentBrowserPopup (must be called each frame).
     */
    public void renderPopup() {
        componentBrowserPopup.render();
    }

    /**
     * Renders the transform swap button (Transform <-> UITransform).
     * Button is red when the transform is problematic (wrong type for context).
     */
    private void renderTransformSwapButton(EditorGameObject entity, Component comp,
                                           float buttonX, DirtyTracker dirtyTracker) {
        boolean isUITransform = comp instanceof UITransform;
        boolean isProblematic = TransformSwapHelper.hasProblematicTransform(entity);
        boolean toUITransform = !isUITransform;

        // Check if swap is allowed
        boolean canSwap = TransformSwapHelper.canSwapTransform(entity, toUITransform);

        ImGui.sameLine(buttonX);

        if (!canSwap) {
            // Cannot swap - grayed out button
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
            ImGui.smallButton(MaterialIcons.SwapHoriz + "##swap");
            ImGui.popStyleColor(2);
            if (ImGui.isItemHovered()) {
                if (TransformSwapHelper.hasUIComponentsRequiringUITransform(entity)) {
                    ImGui.setTooltip("Cannot swap: UI components require UITransform");
                } else {
                    ImGui.setTooltip("Cannot swap transform type");
                }
            }
        } else if (isProblematic) {
            // Problematic transform - red button to indicate swap is recommended
            ImGui.pushStyleColor(ImGuiCol.Button, 0.7f, 0.2f, 0.2f, 1f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.8f, 0.3f, 0.3f, 1f);
            if (ImGui.smallButton(MaterialIcons.SwapHoriz + "##swap")) {
                UndoManager.getInstance().execute(new SwapTransformCommand(entity, toUITransform));
                dirtyTracker.markDirty();
            }
            ImGui.popStyleColor(2);
            if (ImGui.isItemHovered()) {
                String targetType = toUITransform ? "UITransform" : "Transform";
                ImGui.setTooltip("Swap to " + targetType + " (Recommended!)");
            }
        } else {
            // Normal state - regular button
            if (ImGui.smallButton(MaterialIcons.SwapHoriz + "##swap")) {
                UndoManager.getInstance().execute(new SwapTransformCommand(entity, toUITransform));
                dirtyTracker.markDirty();
            }
            if (ImGui.isItemHovered()) {
                String targetType = toUITransform ? "UITransform" : "Transform";
                ImGui.setTooltip("Swap to " + targetType);
            }
        }
    }

    /**
     * Returns the name of a component on this entity that requires the given component
     * via @RequiredComponent, or null if no such dependency exists.
     */
    private String findDependentComponent(EditorGameObject entity, Component target) {
        Class<?> targetType = target.getClass();
        for (Component comp : entity.getComponents()) {
            if (comp == target) continue;
            Class<?> clazz = comp.getClass();
            while (clazz != null && clazz != Component.class && clazz != Object.class) {
                RequiredComponent[] requirements = clazz.getDeclaredAnnotationsByType(RequiredComponent.class);
                for (RequiredComponent req : requirements) {
                    if (req.value().isAssignableFrom(targetType)) {
                        return comp.getClass().getSimpleName();
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
