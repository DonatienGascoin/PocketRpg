package com.pocket.rpg.prefab;

import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntitiesCommand;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
import com.pocket.rpg.serialization.GameObjectData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for expanding, capturing, and reconciling prefab hierarchies
 * in the editor scene.
 */
public final class PrefabHierarchyHelper {

    private PrefabHierarchyHelper() {}

    /**
     * Creates child EditorGameObject instances from the prefab hierarchy
     * and parents them to the root entity.
     *
     * @param root   the root EditorGameObject (already created)
     * @param prefab the prefab with hierarchy data
     * @return flat list of descendants in parent-first order (excludes root)
     */
    public static List<EditorGameObject> expandChildren(EditorGameObject root, Prefab prefab) {
        List<GameObjectData> nodes = prefab.getGameObjects();
        if (nodes == null || nodes.size() <= 1) {
            return List.of();
        }

        GameObjectData rootNode = prefab.getRootNode();
        if (rootNode == null) {
            return List.of();
        }

        String prefabId = prefab.getId();
        List<EditorGameObject> descendants = new ArrayList<>();
        Map<String, EditorGameObject> nodeToEntity = new HashMap<>();
        nodeToEntity.put(rootNode.getId(), root);

        for (GameObjectData node : nodes) {
            if (node == rootNode) continue;
            if (node.getParentId() == null) continue;

            EditorGameObject parentEntity = nodeToEntity.get(node.getParentId());
            if (parentEntity == null) {
                System.err.println("Expand: node '" + node.getName() +
                        "' references unknown parent '" + node.getParentId() + "'");
                continue;
            }

            Vector3f position = new Vector3f();
            Transform transform = node.getComponent(Transform.class);
            if (transform != null) {
                position.set(transform.getPosition());
            }

            EditorGameObject child = new EditorGameObject(prefabId, node.getId(), position);
            String childName = node.getName() != null ? node.getName() : "Child";
            child.setName(childName + "_" + child.getId().substring(0, 4));
            child.setParent(parentEntity);
            child.setOrder(node.getOrder());

            nodeToEntity.put(node.getId(), child);
            descendants.add(child);
        }

        return descendants;
    }

    /**
     * Captures the hierarchy rooted at the given entity as a flat list
     * of GameObjectData nodes suitable for prefab serialization.
     *
     * @param root the root entity
     * @return flat list with parentId references, root first
     */
    public static List<GameObjectData> captureHierarchy(EditorGameObject root) {
        List<GameObjectData> result = new ArrayList<>();
        captureNode(root, null, 0, result);
        return result;
    }

    private static void captureNode(EditorGameObject entity, String parentId,
                                    int order, List<GameObjectData> result) {
        GameObjectData data = entity.toData();
        data.setParentId(parentId);
        data.setOrder(order);
        result.add(data);

        List<? extends EditorGameObject> children = entity.getChildren();
        for (int i = 0; i < children.size(); i++) {
            captureNode(children.get(i), entity.getId(), i, result);
        }
    }

    /**
     * Collects the root entity and all descendants in parent-first order.
     * Used for AddEntitiesCommand so undo/redo processes entities correctly.
     *
     * @param root the root entity
     * @return flat list: root, then descendants in depth-first parent-first order
     */
    public static List<EditorGameObject> collectAll(EditorGameObject root) {
        List<EditorGameObject> result = new ArrayList<>();
        result.add(root);
        collectDescendants(root, result);
        return result;
    }

    private static void collectDescendants(EditorGameObject parent, List<EditorGameObject> result) {
        for (EditorGameObject child : parent.getChildren()) {
            result.add(child);
            collectDescendants(child, result);
        }
    }

    /**
     * Reconciles a prefab instance's scene children against the prefab definition.
     * Creates missing children and flags orphans.
     *
     * @param root   the root prefab instance entity
     * @param prefab the prefab definition
     * @param scene  the editor scene (for adding auto-created entities)
     */
    public static void reconcileInstance(EditorGameObject root, Prefab prefab, EditorScene scene) {
        List<GameObjectData> nodes = prefab.getGameObjects();
        if (nodes == null || nodes.size() <= 1) return;

        GameObjectData rootNode = prefab.getRootNode();
        if (rootNode == null) return;

        // Build map of existing children by prefabNodeId
        Map<String, EditorGameObject> existingByNodeId = new HashMap<>();
        collectByNodeId(root, existingByNodeId);

        // Map nodeId -> entity for parent resolution
        Map<String, EditorGameObject> nodeToEntity = new HashMap<>();
        nodeToEntity.put(rootNode.getId(), root);
        nodeToEntity.putAll(existingByNodeId);

        // Create missing nodes
        for (GameObjectData node : nodes) {
            if (node == rootNode) continue;
            if (node.getParentId() == null) continue;
            if (existingByNodeId.containsKey(node.getId())) continue;

            EditorGameObject parentEntity = nodeToEntity.get(node.getParentId());
            if (parentEntity == null) continue;

            Vector3f position = new Vector3f();
            Transform transform = node.getComponent(Transform.class);
            if (transform != null) {
                position.set(transform.getPosition());
            }

            EditorGameObject child = new EditorGameObject(prefab.getId(), node.getId(), position);
            String childName = node.getName() != null ? node.getName() : "Child";
            child.setName(childName + "_" + child.getId().substring(0, 4));
            child.setParent(parentEntity);
            child.setOrder(node.getOrder());

            scene.addEntity(child);
            nodeToEntity.put(node.getId(), child);

            System.out.println("Reconcile: auto-created missing child '" + childName +
                    "' (nodeId=" + node.getId() + ") under " + parentEntity.getName());
        }

        // Flag orphans
        for (Map.Entry<String, EditorGameObject> entry : existingByNodeId.entrySet()) {
            if (prefab.findNode(entry.getKey()) == null) {
                System.err.println("Reconcile: orphaned prefab child node '" + entry.getKey() +
                        "' on entity " + entry.getValue().getName() +
                        " — prefab no longer defines this node");
            }
        }
    }

    private static void collectByNodeId(EditorGameObject entity, Map<String, EditorGameObject> map) {
        for (EditorGameObject child : entity.getChildren()) {
            if (child.isPrefabChildNode()) {
                map.put(child.getPrefabNodeId(), child);
            }
            collectByNodeId(child, map);
        }
    }

    /**
     * Adds an entity (and its children if any) to the scene with undo support.
     * Convenience method for drop targets.
     */
    public static void addToSceneWithUndo(EditorScene scene, EditorGameObject entity) {
        if (entity.hasChildren()) {
            UndoManager.getInstance().execute(new AddEntitiesCommand(scene, collectAll(entity)));
        } else {
            UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));
        }
    }
}
