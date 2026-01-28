package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Inspector for editing AnimatorTransition properties.
 * Used by InspectorPanel when a transition is selected in the Animator Editor.
 */
public class AnimatorTransitionInspector {

    private AnimatorTransition transition;
    private AnimatorController controller;
    private Runnable onModified;

    private final ImInt typeIndex = new ImInt(0);
    private final ImInt addCondParamIndex = new ImInt(0);

    /**
     * Sets the current selection.
     */
    public void setSelection(AnimatorTransition transition, AnimatorController controller, Runnable onModified) {
        this.transition = transition;
        this.controller = controller;
        this.onModified = onModified;

        if (transition != null) {
            typeIndex.set(transition.getType().ordinal());
        }
        addCondParamIndex.set(0);
    }

    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        this.transition = null;
        this.controller = null;
        this.onModified = null;
    }

    /**
     * Returns true if a transition is currently selected.
     */
    public boolean hasSelection() {
        return transition != null && controller != null;
    }

    /**
     * Renders the transition inspector UI.
     */
    public void render() {
        if (!hasSelection()) {
            ImGui.textDisabled("No transition selected");
            return;
        }

        // Header
        ImGui.text(MaterialIcons.ArrowForward + " Transition");
        ImGui.separator();

        // From/To display
        ImGui.text("From: " + transition.getFrom());
        ImGui.text("To: " + transition.getTo());

        ImGui.spacing();

        // Type selector
        ImGui.text("Type:");
        String[] typeNames = {"INSTANT", "WAIT_FOR_COMPLETION", "WAIT_FOR_LOOP"};
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##TransType", typeIndex, typeNames)) {
            notifyModified();
            transition.setType(TransitionType.values()[typeIndex.get()]);
        }

        // Type explanation
        ImGui.spacing();
        switch (transition.getType()) {
            case INSTANT -> ImGui.textDisabled("Switches immediately when conditions are met");
            case WAIT_FOR_COMPLETION -> ImGui.textDisabled("Waits for animation to finish first");
            case WAIT_FOR_LOOP -> ImGui.textDisabled("Waits for animation loop point");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Conditions
        ImGui.text("Conditions:");
        if (ImGui.beginChild("ConditionsList", 0, 120, true)) {
            for (int i = 0; i < transition.getConditions().size(); i++) {
                TransitionCondition cond = transition.getCondition(i);
                renderCondition(cond, i);
            }
            if (transition.getConditions().isEmpty()) {
                ImGui.textDisabled("No conditions");
                ImGui.textDisabled("(transition won't fire automatically)");
            }
        }
        ImGui.endChild();

        // Add condition
        if (controller.getParameterCount() > 0) {
            ImGui.spacing();
            renderAddCondition();
        } else {
            ImGui.spacing();
            ImGui.textDisabled("Add parameters to create conditions");
        }
    }

    private void renderCondition(TransitionCondition cond, int index) {
        AnimatorParameter param = controller.getParameter(cond.getParameter());
        if (param == null) {
            ImGui.textColored(1f, 0.5f, 0.2f, 1f, cond.getParameter() + " (missing)");
            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.Close + "##cond" + index)) {
                notifyModified();
                transition.removeCondition(index);
            }
            return;
        }

        // Parameter name
        ImGui.text(cond.getParameter());
        ImGui.sameLine();
        ImGui.textDisabled("==");
        ImGui.sameLine();

        // Value display/edit based on type
        String valueId = "##condVal" + index;
        switch (param.getType()) {
            case BOOL, TRIGGER -> {
                boolean boolVal = cond.getValue() instanceof Boolean b ? b : true;
                String[] boolOptions = {"true", "false"};
                ImInt boolIdx = new ImInt(boolVal ? 0 : 1);
                ImGui.setNextItemWidth(60);
                if (ImGui.combo(valueId, boolIdx, boolOptions)) {
                    notifyModified();
                    cond.setValue(boolIdx.get() == 0);
                }
            }
            case DIRECTION -> {
                Direction dirVal = cond.getValue() instanceof Direction d ? d : Direction.DOWN;
                String[] dirOptions = {"UP", "DOWN", "LEFT", "RIGHT"};
                ImInt dirIdx = new ImInt(dirVal.ordinal());
                ImGui.setNextItemWidth(80);
                if (ImGui.combo(valueId, dirIdx, dirOptions)) {
                    notifyModified();
                    cond.setValue(Direction.values()[dirIdx.get()]);
                }
            }
        }

        ImGui.sameLine();
        if (ImGui.smallButton(MaterialIcons.Close + "##condDel" + index)) {
            notifyModified();
            transition.removeCondition(index);
        }
    }

    private void renderAddCondition() {
        String[] paramNames = new String[controller.getParameterCount()];
        for (int i = 0; i < paramNames.length; i++) {
            paramNames[i] = controller.getParameter(i).getName();
        }

        // Clamp index if parameters were removed
        if (addCondParamIndex.get() >= paramNames.length) {
            addCondParamIndex.set(0);
        }

        ImGui.setNextItemWidth(120);
        ImGui.combo("##AddCondParam", addCondParamIndex, paramNames);
        ImGui.sameLine();
        if (ImGui.button("+ Add Condition")) {
            notifyModified();
            AnimatorParameter param = controller.getParameter(addCondParamIndex.get());
            Object value = switch (param.getType()) {
                case BOOL, TRIGGER -> true;
                case DIRECTION -> Direction.DOWN;
            };
            transition.addCondition(new TransitionCondition(param.getName(), value));
        }
    }

    private void notifyModified() {
        if (onModified != null) {
            onModified.run();
        }
    }
}
