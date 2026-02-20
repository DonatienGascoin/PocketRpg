package com.pocket.rpg.pokemon;

/**
 * Six-stat block used for base stats and individual values (IVs).
 */
public record Stats(int hp, int atk, int def, int spAtk, int spDef, int spd) {

    public int total() {
        return hp + atk + def + spAtk + spDef + spd;
    }

    public int get(StatType type) {
        return switch (type) {
            case HP -> hp;
            case ATK -> atk;
            case DEF -> def;
            case SP_ATK -> spAtk;
            case SP_DEF -> spDef;
            case SPD -> spd;
        };
    }
}
