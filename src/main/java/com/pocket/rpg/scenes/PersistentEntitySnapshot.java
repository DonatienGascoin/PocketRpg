package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.components.core.PersistentEntity;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.serialization.*;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities for capturing and restoring persistent entity state across scene transitions.
 * <p>
 * Uses the same reflection-based cloning infrastructure as the editor to ensure full fidelity.
 * Snapshots include component state and child objects.
 */
public final class PersistentEntitySnapshot {

    private PersistentEntitySnapshot() {}

    /**
     * Captures a snapshot of a persistent entity's component state and children.
     * The snapshot includes all serializable components cloned via
     * reflection (same approach as editor component cloning).
     *
     * @param entity A GameObject with a PersistentEntity component
     * @return GameObjectData snapshot, or null if entity has no PersistentEntity
     */
    public static GameObjectData snapshot(GameObject entity) {
        PersistentEntity persistent = entity.getComponent(PersistentEntity.class);
        if (persistent == null) {
            return null;
        }

        GameObjectData data = snapshotGameObject(entity);
        data.setTag(persistent.getEntityTag());

        // Store sourcePrefabId in the prefabId field for snapshot metadata.
        // This is separate from the component clone because sourcePrefabId
        // is @HideInInspector (excluded from serialization/cloning).
        String prefabId = persistent.getSourcePrefabId();
        if (prefabId != null && !prefabId.isEmpty()) {
            data.setPrefabId(prefabId);
        }

        return data;
    }

    /**
     * Snapshots a single GameObject (components + children recursively).
     */
    private static GameObjectData snapshotGameObject(GameObject entity) {
        // Clone all components via reflection
        List<Component> clonedComponents = new ArrayList<>();
        for (Component comp : entity.getAllComponents()) {
            Component clone = ComponentReflectionUtils.cloneComponent(comp);
            if (clone != null) {
                clonedComponents.add(clone);
            }
        }

        GameObjectData data = new GameObjectData(
                null, // no ID needed for snapshots
                entity.getName(),
                clonedComponents
        );
        data.setActive(entity.isEnabled());

        // Snapshot children recursively
        List<GameObject> children = entity.getChildren();
        if (!children.isEmpty()) {
            List<GameObjectData> childSnapshots = new ArrayList<>();
            for (GameObject child : children) {
                childSnapshots.add(snapshotGameObject(child));
            }
            data.setChildren(childSnapshots);
        }

        return data;
    }

    /**
     * Applies a snapshot's component state to an existing entity.
     * <p>
     * For each component type in the snapshot, finds the matching component
     * on the target entity and copies field values. Skips Transform (position
     * is set by spawn point) and PersistentEntity (target keeps its own).
     * <p>
     * Components on the target that are not in the snapshot are removed
     * (except Transform and PersistentEntity, which are always kept).
     * <p>
     * Children are restored: matched by name, with missing children created
     * and extra children removed.
     *
     * @param snapshotData The snapshot to apply
     * @param target       The entity to apply state to
     */
    public static void applySnapshot(GameObjectData snapshotData, GameObject target) {
        applyComponents(snapshotData, target);
        applyChildren(snapshotData, target);
    }

    /**
     * Applies component state from snapshot to target, adding missing and removing extra.
     */
    private static void applyComponents(GameObjectData snapshotData, GameObject target) {
        List<Component> snapshotComponents = snapshotData.getComponents();
        if (snapshotComponents == null) {
            snapshotComponents = List.of();
        }

        // Track which component types are in the snapshot
        Set<Class<? extends Component>> snapshotTypes = new HashSet<>();

        for (Component snapshotComp : snapshotComponents) {
            if (snapshotComp == null) continue;

            // Skip Transform - position is set by spawn point
            if (snapshotComp instanceof Transform) continue;

            // Skip PersistentEntity - target keeps its own
            if (snapshotComp instanceof PersistentEntity) continue;

            snapshotTypes.add(snapshotComp.getClass());

            // Find matching component on target
            Component targetComp = target.getComponent(snapshotComp.getClass());

            if (targetComp != null) {
                // Copy field values from snapshot to target
                copyComponentFields(snapshotComp, targetComp);
            } else {
                // Component doesn't exist on target - add a clone
                Component clone = ComponentReflectionUtils.cloneComponent(snapshotComp);
                if (clone != null) {
                    target.addComponent(clone);
                }
            }
        }

        // Remove components on target that are not in the snapshot
        for (Component targetComp : target.getAllComponents()) {
            if (targetComp instanceof Transform) continue;
            if (targetComp instanceof PersistentEntity) continue;

            if (!snapshotTypes.contains(targetComp.getClass())) {
                target.removeComponent(targetComp);
            }
        }
    }

    /**
     * Restores child objects from the snapshot.
     * Matches children by name. Missing children are created, extra children removed.
     */
    private static void applyChildren(GameObjectData snapshotData, GameObject target) {
        List<GameObjectData> childSnapshots = snapshotData.getChildren();
        if (childSnapshots == null) {
            childSnapshots = List.of();
        }

        // Match existing children by name
        Set<String> snapshotChildNames = new HashSet<>();
        for (GameObjectData childData : childSnapshots) {
            String childName = childData.getName();
            if (!snapshotChildNames.add(childName)) {
                System.err.println("[PersistentEntitySnapshot] WARNING: Duplicate child name '"
                        + childName + "' in snapshot for '" + target.getName()
                        + "'. Name-based matching may produce unexpected results.");
            }

            GameObject existingChild = findChildByName(target, childName);
            if (existingChild != null) {
                // Apply snapshot to existing child
                applyComponents(childData, existingChild);
                applyChildren(childData, existingChild);
            } else {
                // Create child from snapshot
                GameObject newChild = createScratchEntity(childData);
                target.addChild(newChild);
            }
        }

        // Remove children not in the snapshot
        for (GameObject child : new ArrayList<>(target.getChildren())) {
            if (!snapshotChildNames.contains(child.getName())) {
                target.removeChild(child);
            }
        }
    }

    /**
     * Finds a direct child by name.
     */
    private static GameObject findChildByName(GameObject parent, String name) {
        if (name == null) return null;
        for (GameObject child : parent.getChildren()) {
            if (name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Creates a new GameObject from a snapshot.
     * <p>
     * If the snapshot has a sourcePrefabId, instantiates from the prefab and
     * applies overrides. Otherwise, creates a scratch entity from snapshot components.
     *
     * @param snapshotData The snapshot to create from
     * @return A new GameObject, or null if creation failed
     */
    public static GameObject createFromSnapshot(GameObjectData snapshotData) {
        // Check for sourcePrefabId
        String prefabId = getSourcePrefabId(snapshotData);

        if (prefabId != null && !prefabId.isEmpty()) {
            return createFromPrefab(snapshotData, prefabId);
        }

        return createScratchEntity(snapshotData);
    }

    /**
     * Copies serializable field values from one component to another of the same type.
     */
    private static void copyComponentFields(Component source, Component target) {
        ComponentMeta meta = ComponentReflectionUtils.getMeta(source);
        if (meta == null) return;

        for (FieldMeta fm : meta.fields()) {
            Object value = ComponentReflectionUtils.getFieldValue(source, fm.name());
            ComponentReflectionUtils.setFieldValue(target, fm.name(), value);
        }
    }

    /**
     * Extracts the sourcePrefabId from the snapshot metadata.
     * Stored in GameObjectData.prefabId during snapshot() because
     * sourcePrefabId is @HideInInspector and not cloned via reflection.
     */
    private static String getSourcePrefabId(GameObjectData snapshotData) {
        return snapshotData.getPrefabId();
    }

    /**
     * Creates a GameObject from a prefab, then applies snapshot overrides.
     */
    private static GameObject createFromPrefab(GameObjectData snapshotData, String prefabId) {
        Prefab prefab = PrefabRegistry.getInstance().getPrefab(prefabId);
        if (prefab == null) {
            System.err.println("[PersistentEntitySnapshot] Prefab not found: " + prefabId
                    + ", falling back to scratch entity");
            return createScratchEntity(snapshotData);
        }

        GameObject entity = prefab.instantiate(new Vector3f(), null);
        if (entity == null) {
            System.err.println("[PersistentEntitySnapshot] Prefab instantiation failed: " + prefabId);
            return createScratchEntity(snapshotData);
        }

        String name = snapshotData.getName();
        if (name != null && !name.isBlank()) {
            entity.setName(name);
        }
        entity.setEnabled(snapshotData.isActive());

        // Apply snapshot state on top of prefab defaults
        applySnapshot(snapshotData, entity);

        return entity;
    }

    /**
     * Creates a scratch entity from snapshot components, including children.
     */
    private static GameObject createScratchEntity(GameObjectData snapshotData) {
        String name = snapshotData.getName();
        if (name == null || name.isBlank()) {
            name = "PersistentEntity";
        }

        GameObject entity = new GameObject(name);
        entity.setEnabled(snapshotData.isActive());

        List<Component> components = snapshotData.getComponents();
        if (components != null) {
            for (Component comp : components) {
                if (comp == null) continue;

                if (comp instanceof Transform t) {
                    entity.getTransform().setPosition(new Vector3f(t.getPosition()));
                    entity.getTransform().setRotation(new Vector3f(t.getRotation()));
                    entity.getTransform().setScale(new Vector3f(t.getScale()));
                } else {
                    Component clone = ComponentReflectionUtils.cloneComponent(comp);
                    if (clone != null) {
                        entity.addComponent(clone);
                    }
                }
            }
        }

        // Recreate children
        List<GameObjectData> childSnapshots = snapshotData.getChildren();
        if (childSnapshots != null) {
            for (GameObjectData childData : childSnapshots) {
                GameObject child = createScratchEntity(childData);
                entity.addChild(child);
            }
        }

        return entity;
    }
}
