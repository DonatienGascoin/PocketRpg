package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.ComponentBrowserPopup;
import com.pocket.rpg.editor.scene.DirtyTracker;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddComponentCommand;
import com.pocket.rpg.editor.undo.commands.RemoveComponentCommand;
import com.pocket.rpg.serialization.ComponentRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;

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

            for (int i = 0; i < components.size(); i++) {
                Component comp = components.get(i);
                ImGui.pushID(i);

                String componentType = comp.getClass().getName();
                String label = comp.getClass().getSimpleName();
                if (isPrefabInstance && !entity.getOverriddenFields(componentType).isEmpty()) {
                    label += " *";
                }

                boolean open = ImGui.collapsingHeader(label, ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap);

                if (allowStructuralChanges) {
                    String dependent = findDependentComponent(entity, comp);
                    ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                    if (dependent != null) {
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
            ImGui.separator();
            if (ImGui.button(MaterialIcons.Add + " Add Component", -1, 0)) {
                componentBrowserPopup.open(meta -> {
                    Component component = ComponentRegistry.instantiateByClassName(meta.className());
                    if (component != null) {
                        UndoManager.getInstance().execute(new AddComponentCommand(entity, component));
                        dirtyTracker.markDirty();
                    }
                });
            }
        }
    }

    /**
     * Renders the ComponentBrowserPopup (must be called each frame).
     */
    public void renderPopup() {
        componentBrowserPopup.render();
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
