package com.pocket.rpg.pokemon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatsTest {

    @Test
    @DisplayName("total sums all six stats")
    void totalSumsAll() {
        Stats stats = new Stats(45, 49, 49, 65, 65, 45);
        assertEquals(318, stats.total());
    }

    @Test
    @DisplayName("get returns correct value for each stat type")
    void getByStatType() {
        Stats stats = new Stats(10, 20, 30, 40, 50, 60);
        assertEquals(10, stats.get(StatType.HP));
        assertEquals(20, stats.get(StatType.ATK));
        assertEquals(30, stats.get(StatType.DEF));
        assertEquals(40, stats.get(StatType.SP_ATK));
        assertEquals(50, stats.get(StatType.SP_DEF));
        assertEquals(60, stats.get(StatType.SPD));
    }

    @Test
    @DisplayName("record accessors work")
    void recordAccessors() {
        Stats stats = new Stats(1, 2, 3, 4, 5, 6);
        assertEquals(1, stats.hp());
        assertEquals(2, stats.atk());
        assertEquals(3, stats.def());
        assertEquals(4, stats.spAtk());
        assertEquals(5, stats.spDef());
        assertEquals(6, stats.spd());
    }

    @Test
    @DisplayName("all zeroes total is zero")
    void allZeroes() {
        Stats stats = new Stats(0, 0, 0, 0, 0, 0);
        assertEquals(0, stats.total());
    }
}
