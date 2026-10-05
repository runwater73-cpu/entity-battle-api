package dev.entitybattle.api;

import net.minecraft.resources.ResourceLocation;

/** One entity type bound to a Cobblemon Species; Species data remains Cobblemon-owned. */
public record EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                                  int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                                  WorldMode worldMode,
                                  boolean nativeDrops, boolean nativeExperience,
                                  WorldBehavior worldBehavior) {
    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat) {
        this(entity, species, minLevel, maxLevel, defeat, false, WorldMode.NATIVE_MOB,
                false, false, WorldBehavior.PASSIVE);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable) {
        this(entity, species, minLevel, maxLevel, defeat, catchable, WorldMode.NATIVE_MOB,
                false, false, WorldBehavior.PASSIVE);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode) {
        this(entity, species, minLevel, maxLevel, defeat, catchable,
                worldMode, false, false, WorldBehavior.PASSIVE);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode,
                               boolean nativeDrops, boolean nativeExperience) {
        this(entity, species, minLevel, maxLevel, defeat, catchable,
                worldMode, nativeDrops, nativeExperience, WorldBehavior.PASSIVE);
    }

    public EntityBattleProfile {
        if (entity == null || species == null || defeat == null || worldMode == null
                || worldBehavior == null) {
            throw new IllegalArgumentException("Entity, species, defeat mode, world mode and behavior are required");
        }
        if (minLevel < 1 || maxLevel > 100 || minLevel > maxLevel) {
            throw new IllegalArgumentException("Level range must be inside 1..100");
        }
    }

    public enum DefeatMode {
        VANILLA_DEATH,
        KNOCKOUT
    }

    public enum WorldMode {
        NATIVE_MOB,
        POKEMON_ENTITY
    }

    public enum WorldBehavior {
        PASSIVE,
        HOSTILE
    }

}
