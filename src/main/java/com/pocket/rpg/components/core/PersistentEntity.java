package com.pocket.rpg.components.core;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.HideInInspector;
import lombok.Getter;
import lombok.Setter;

/**
 * Marks an entity as persistent across scene transitions.
 * <p>
 * Before a scene transition, entities with this component are snapshotted
 * (serialized to GameObjectData). After the new scene loads, the snapshot
 * is applied to a matching entity in the new scene (matched by entityTag),
 * or used to create one if none exists.
 * <p>
 * This keeps scenes self-contained (each can have its own player for
 * editor testing) while preserving state across transitions.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Add to player entity
 * PersistentEntity pe = new PersistentEntity();
 * pe.setEntityTag("Player");
 * player.addComponent(pe);
 *
 * // Add to companion
 * PersistentEntity pe = new PersistentEntity();
 * pe.setEntityTag("Companion1");
 * companion.addComponent(pe);
 * </pre>
 */
@ComponentMeta(category = "Core")
public class PersistentEntity extends Component {

    /**
     * Identifies this entity across scenes.
     * Entities in different scenes with the same tag are considered
     * the same logical entity (e.g., "Player", "Companion1").
     */
    @Getter
    @Setter
    private String entityTag = "Player";

    /**
     * Prefab ID used to create this entity if the target scene
     * doesn't have a matching entity. Auto-set by RuntimeSceneLoader.
     */
    @Getter
    @Setter
    @HideInInspector
    private String sourcePrefabId = "";

    public PersistentEntity() {}
}
