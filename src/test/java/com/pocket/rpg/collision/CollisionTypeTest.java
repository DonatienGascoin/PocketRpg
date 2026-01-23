package com.pocket.rpg.collision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CollisionType enum enhancements.
 */
class CollisionTypeTest {

    @Test
    @DisplayName("All collision types should have a category")
    void allTypesHaveCategory() {
        for (CollisionType type : CollisionType.values()) {
            assertNotNull(type.getCategory(),
                    "Type " + type.name() + " should have a category");
        }
    }

    @Test
    @DisplayName("All collision types should have a description")
    void allTypesHaveDescription() {
        for (CollisionType type : CollisionType.values()) {
            assertNotNull(type.getDescription(),
                    "Type " + type.name() + " should have a description");
            assertFalse(type.getDescription().isBlank(),
                    "Type " + type.name() + " description should not be blank");
        }
    }

    @Test
    @DisplayName("getByCategory returns correct types")
    void getByCategoryReturnsCorrectTypes() {
        // Movement category
        List<CollisionType> movement = CollisionType.getByCategory(CollisionCategory.MOVEMENT);
        assertTrue(movement.contains(CollisionType.NONE));
        assertTrue(movement.contains(CollisionType.SOLID));
        assertEquals(2, movement.size());

        // Ledge category (4 regular + 4 elevated)
        List<CollisionType> ledges = CollisionType.getByCategory(CollisionCategory.LEDGE);
        assertEquals(8, ledges.size());
        assertTrue(ledges.contains(CollisionType.LEDGE_DOWN));
        assertTrue(ledges.contains(CollisionType.LEDGE_UP));
        assertTrue(ledges.contains(CollisionType.LEDGE_LEFT));
        assertTrue(ledges.contains(CollisionType.LEDGE_RIGHT));
        assertTrue(ledges.contains(CollisionType.LEDGE_DOWN_ELEV));
        assertTrue(ledges.contains(CollisionType.LEDGE_UP_ELEV));
        assertTrue(ledges.contains(CollisionType.LEDGE_LEFT_ELEV));
        assertTrue(ledges.contains(CollisionType.LEDGE_RIGHT_ELEV));

        // Terrain category
        List<CollisionType> terrain = CollisionType.getByCategory(CollisionCategory.TERRAIN);
        assertEquals(4, terrain.size());

        // Elevation category - now just STAIRS (bidirectional)
        List<CollisionType> elevation = CollisionType.getByCategory(CollisionCategory.ELEVATION);
        assertEquals(1, elevation.size());
        assertTrue(elevation.contains(CollisionType.STAIRS));

        // Trigger category
        List<CollisionType> triggers = CollisionType.getByCategory(CollisionCategory.TRIGGER);
        assertEquals(3, triggers.size());
        assertTrue(triggers.contains(CollisionType.WARP));
        assertTrue(triggers.contains(CollisionType.DOOR));
        assertTrue(triggers.contains(CollisionType.SPAWN_POINT));
    }

    @Test
    @DisplayName("requiresMetadata returns true for trigger types")
    void requiresMetadataForTriggers() {
        assertTrue(CollisionType.WARP.requiresMetadata());
        assertTrue(CollisionType.DOOR.requiresMetadata());
        assertTrue(CollisionType.SPAWN_POINT.requiresMetadata());
        assertTrue(CollisionType.STAIRS.requiresMetadata());

        assertFalse(CollisionType.NONE.requiresMetadata());
        assertFalse(CollisionType.SOLID.requiresMetadata());
        assertFalse(CollisionType.WATER.requiresMetadata());
    }

    @Test
    @DisplayName("isTrigger returns true for trigger and elevation types")
    void isTriggerForTriggerAndElevationTypes() {
        assertTrue(CollisionType.WARP.isTrigger());
        assertTrue(CollisionType.DOOR.isTrigger());
        assertTrue(CollisionType.SPAWN_POINT.isTrigger());
        assertTrue(CollisionType.STAIRS.isTrigger());

        assertFalse(CollisionType.NONE.isTrigger());
        assertFalse(CollisionType.SOLID.isTrigger());
        assertFalse(CollisionType.WATER.isTrigger());
        assertFalse(CollisionType.LEDGE_DOWN.isTrigger());
    }

    @Test
    @DisplayName("hasIcon returns true for types with icons")
    void hasIconForTypesWithIcons() {
        assertTrue(CollisionType.SOLID.hasIcon());
        assertTrue(CollisionType.WARP.hasIcon());
        assertTrue(CollisionType.DOOR.hasIcon());
        assertTrue(CollisionType.SPAWN_POINT.hasIcon());
        assertTrue(CollisionType.STAIRS.hasIcon());
        assertTrue(CollisionType.WATER.hasIcon());

        assertFalse(CollisionType.NONE.hasIcon());
    }

    @Test
    @DisplayName("fromId returns correct type")
    void fromIdReturnsCorrectType() {
        assertEquals(CollisionType.NONE, CollisionType.fromId(0));
        assertEquals(CollisionType.SOLID, CollisionType.fromId(1));
        assertEquals(CollisionType.WARP, CollisionType.fromId(10));
        assertEquals(CollisionType.STAIRS, CollisionType.fromId(13));

        // Unknown ID should return NONE
        assertEquals(CollisionType.NONE, CollisionType.fromId(999));
    }

    @Test
    @DisplayName("All type IDs are unique")
    void allTypeIdsAreUnique() {
        CollisionType[] types = CollisionType.values();
        for (int i = 0; i < types.length; i++) {
            for (int j = i + 1; j < types.length; j++) {
                assertNotEquals(types[i].getId(), types[j].getId(),
                        "Types " + types[i].name() + " and " + types[j].name() + " have duplicate IDs");
            }
        }
    }
}
