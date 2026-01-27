package com.pocket.rpg.editor.panels.animator;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages unique IDs for nodes, pins, and links in the animator graph editor.
 * <p>
 * The ImNodes library requires unique int IDs for all elements.
 * This class provides a consistent mapping between state names and their visual
 * representation IDs.
 */
public class AnimatorIdManager {

    // ID ranges to avoid collisions
    private static final int NODE_ID_BASE = 1_000;
    private static final int INPUT_PIN_BASE = 100_000;
    private static final int OUTPUT_PIN_BASE = 200_000;
    private static final int LINK_ID_BASE = 300_000;

    /**
     * Special node ID for the "Any State" wildcard node.
     */
    public static final int ANY_STATE_NODE_ID = 999;

    private final Map<String, Integer> stateNameToNodeId = new HashMap<>();
    private final Map<Integer, String> nodeIdToStateName = new HashMap<>();
    private final Map<Integer, Integer> linkIdToTransitionIndex = new HashMap<>();
    private final Map<Integer, Integer> transitionIndexToLinkId = new HashMap<>();

    private int nextNodeId = NODE_ID_BASE;
    private int nextLinkId = LINK_ID_BASE;

    /**
     * Gets or creates a node ID for the given state name.
     */
    public long getNodeId(String stateName) {
        if ("*".equals(stateName)) {
            return ANY_STATE_NODE_ID;
        }
        return stateNameToNodeId.computeIfAbsent(stateName, name -> {
            int id = nextNodeId++;
            nodeIdToStateName.put(id, name);
            return id;
        });
    }

    /**
     * Gets the input pin ID for the given state (left side of node).
     */
    public long getInputPinId(String stateName) {
        int nodeId = (int) getNodeId(stateName);
        if (nodeId == ANY_STATE_NODE_ID) {
            return ANY_STATE_NODE_ID - NODE_ID_BASE + INPUT_PIN_BASE;
        }
        return nodeId - NODE_ID_BASE + INPUT_PIN_BASE;
    }

    /**
     * Gets the output pin ID for the given state (right side of node).
     */
    public long getOutputPinId(String stateName) {
        int nodeId = (int) getNodeId(stateName);
        if (nodeId == ANY_STATE_NODE_ID) {
            return ANY_STATE_NODE_ID - NODE_ID_BASE + OUTPUT_PIN_BASE;
        }
        return nodeId - NODE_ID_BASE + OUTPUT_PIN_BASE;
    }

    /**
     * Gets the next available link ID and registers it for a transition.
     */
    public long getLinkId(int transitionIndex) {
        Integer existing = transitionIndexToLinkId.get(transitionIndex);
        if (existing != null) {
            return existing;
        }
        int id = nextLinkId++;
        linkIdToTransitionIndex.put(id, transitionIndex);
        transitionIndexToLinkId.put(transitionIndex, id);
        return id;
    }

    /**
     * Gets the state name for the given node ID.
     */
    public String getStateName(long nodeId) {
        if (nodeId == ANY_STATE_NODE_ID) {
            return "*";
        }
        return nodeIdToStateName.get((int) nodeId);
    }

    /**
     * Gets the state name from an input pin ID.
     */
    public String getStateNameFromInputPin(long pinId) {
        int nodeId = (int) (pinId - INPUT_PIN_BASE + NODE_ID_BASE);
        return getStateName(nodeId);
    }

    /**
     * Gets the state name from an output pin ID.
     */
    public String getStateNameFromOutputPin(long pinId) {
        int nodeId = (int) (pinId - OUTPUT_PIN_BASE + NODE_ID_BASE);
        return getStateName(nodeId);
    }

    /**
     * Gets the transition index for the given link ID.
     */
    public Integer getTransitionIndex(long linkId) {
        return linkIdToTransitionIndex.get((int) linkId);
    }

    /**
     * Removes a state from the ID mappings.
     */
    public void removeState(String stateName) {
        Integer nodeId = stateNameToNodeId.remove(stateName);
        if (nodeId != null) {
            nodeIdToStateName.remove(nodeId);
        }
    }

    /**
     * Renames a state, preserving its node ID.
     */
    public void renameState(String oldName, String newName) {
        Integer nodeId = stateNameToNodeId.remove(oldName);
        if (nodeId != null) {
            stateNameToNodeId.put(newName, nodeId);
            nodeIdToStateName.put(nodeId, newName);
        }
    }

    /**
     * Clears all ID mappings. Call when switching controllers.
     */
    public void clear() {
        stateNameToNodeId.clear();
        nodeIdToStateName.clear();
        linkIdToTransitionIndex.clear();
        transitionIndexToLinkId.clear();
        nextNodeId = NODE_ID_BASE;
        nextLinkId = LINK_ID_BASE;
    }

    /**
     * Clears only link IDs. Call before re-rendering links each frame.
     */
    public void clearLinks() {
        linkIdToTransitionIndex.clear();
        transitionIndexToLinkId.clear();
        nextLinkId = LINK_ID_BASE;
    }

    /**
     * Checks if a node ID exists for the given state name.
     */
    public boolean hasNodeId(String stateName) {
        return "*".equals(stateName) || stateNameToNodeId.containsKey(stateName);
    }
}
