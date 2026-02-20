package com.pocket.rpg.pokemon;

/**
 * Experience growth curves that determine total exp needed to reach each level.
 * Formulas match the Gen III simplified curves.
 */
public enum GrowthRate {
    FAST {
        @Override
        public int expForLevel(int level) {
            if (level <= 1) return 0;
            return 4 * level * level * level / 5;
        }
    },
    MEDIUM_FAST {
        @Override
        public int expForLevel(int level) {
            if (level <= 1) return 0;
            return level * level * level;
        }
    },
    MEDIUM_SLOW {
        @Override
        public int expForLevel(int level) {
            if (level <= 1) return 0;
            int n = level;
            return 6 * n * n * n / 5 - 15 * n * n + 100 * n - 140;
        }
    },
    SLOW {
        @Override
        public int expForLevel(int level) {
            if (level <= 1) return 0;
            return 5 * level * level * level / 4;
        }
    };

    /**
     * Returns the total experience required to reach the given level.
     *
     * @param level the target level (1-100)
     * @return total exp needed (0 for level 1)
     */
    public abstract int expForLevel(int level);
}
