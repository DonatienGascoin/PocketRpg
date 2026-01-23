package com.pocket.rpg.collision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CollisionCategory enum.
 */
class CollisionCategoryTest {

    @Test
    @DisplayName("All categories have display name")
    void allCategoriesHaveDisplayName() {
        for (CollisionCategory category : CollisionCategory.values()) {
            assertNotNull(category.getDisplayName());
            assertFalse(category.getDisplayName().isBlank());
        }
    }

    @Test
    @DisplayName("inOrder returns categories sorted by order")
    void inOrderReturnsSortedCategories() {
        CollisionCategory[] ordered = CollisionCategory.inOrder();

        assertEquals(5, ordered.length);

        // Verify order
        assertEquals(CollisionCategory.MOVEMENT, ordered[0]);
        assertEquals(CollisionCategory.LEDGE, ordered[1]);
        assertEquals(CollisionCategory.TERRAIN, ordered[2]);
        assertEquals(CollisionCategory.ELEVATION, ordered[3]);
        assertEquals(CollisionCategory.TRIGGER, ordered[4]);
    }

    @Test
    @DisplayName("All orders are unique")
    void allOrdersAreUnique() {
        CollisionCategory[] categories = CollisionCategory.values();
        for (int i = 0; i < categories.length; i++) {
            for (int j = i + 1; j < categories.length; j++) {
                assertNotEquals(categories[i].getOrder(), categories[j].getOrder(),
                        "Categories " + categories[i].name() + " and " + categories[j].name()
                                + " have duplicate orders");
            }
        }
    }
}
