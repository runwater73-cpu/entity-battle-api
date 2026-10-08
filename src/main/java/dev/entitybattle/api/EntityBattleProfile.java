package dev.entitybattle.api;

import net.minecraft.resources.ResourceLocation;

/** One entity type bound to a Cobblemon Species; Species data remains Cobblemon-owned. */
public record EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                                  int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                                  WorldMode worldMode,
                                  boolean nativeDrops, boolean nativeExperience,
                                  WorldBehavior worldBehavior,
                                  boolean boss,
                                  ResourceLocation heldItem,
                                  BossBattle bossBattle,
                                  java.util.List<String> moves) {
    /** Preserves the existing Java API; omitted moves use Cobblemon's level learnset. */
    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode, boolean nativeDrops, boolean nativeExperience,
                               WorldBehavior worldBehavior, boolean boss, ResourceLocation heldItem,
                               BossBattle bossBattle) {
        this(entity, species, minLevel, maxLevel, defeat, catchable, worldMode, nativeDrops,
                nativeExperience, worldBehavior, boss, heldItem, bossBattle, java.util.List.of());
    }
    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat) {
        this(entity, species, minLevel, maxLevel, defeat, false, WorldMode.NATIVE_MOB,
                false, false, WorldBehavior.PASSIVE, false, null, null);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable) {
        this(entity, species, minLevel, maxLevel, defeat, catchable, WorldMode.NATIVE_MOB,
                false, false, WorldBehavior.PASSIVE, false, null, null);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode) {
        this(entity, species, minLevel, maxLevel, defeat, catchable,
                worldMode, false, false, WorldBehavior.PASSIVE, false, null, null);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode,
                               boolean nativeDrops, boolean nativeExperience) {
        this(entity, species, minLevel, maxLevel, defeat, catchable,
                worldMode, nativeDrops, nativeExperience, WorldBehavior.PASSIVE, false, null, null);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode, boolean nativeDrops, boolean nativeExperience,
                               WorldBehavior worldBehavior) {
        this(entity, species, minLevel, maxLevel, defeat, catchable,
                worldMode, nativeDrops, nativeExperience, worldBehavior, false, null, null);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode, boolean nativeDrops, boolean nativeExperience,
                               WorldBehavior worldBehavior, boolean boss) {
        this(entity, species, minLevel, maxLevel, defeat, catchable,
                worldMode, nativeDrops, nativeExperience, worldBehavior, boss, null, null);
    }

    public EntityBattleProfile(ResourceLocation entity, ResourceLocation species,
                               int minLevel, int maxLevel, DefeatMode defeat, boolean catchable,
                               WorldMode worldMode, boolean nativeDrops, boolean nativeExperience,
                               WorldBehavior worldBehavior, boolean boss, BossBattle bossBattle) {
        this(entity, species, minLevel, maxLevel, defeat, catchable,
                worldMode, nativeDrops, nativeExperience, worldBehavior, boss, null, bossBattle);
    }

    public EntityBattleProfile {
        moves = validatedMoves(moves);
        if (entity == null || species == null || defeat == null || worldMode == null
                || worldBehavior == null) {
            throw new IllegalArgumentException("Entity, species, defeat mode, world mode and behavior are required");
        }
        if (minLevel < 1 || maxLevel > 100 || minLevel > maxLevel) {
            throw new IllegalArgumentException("Level range must be inside 1..100");
        }
        if (boss && catchable) {
            throw new IllegalArgumentException("Boss profiles must be uncatchable");
        }
        if (bossBattle != null && !boss) {
            throw new IllegalArgumentException("Only boss profiles can define a boss battle");
        }
        if (bossBattle != null && bossBattle.mode() == RosterMode.EXISTING_SQUAD
                && (!bossBattle.minionEntity().equals(entity) || bossBattle.levelOffset() != 0)) {
            throw new IllegalArgumentException("Original same-species squads require their source type and levelOffset 0");
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

    public enum RosterMode { HORDE, EXISTING_SQUAD }

    private static java.util.List<String> validatedMoves(java.util.List<String> moves) {
        moves = java.util.List.copyOf(moves);
        if (moves.size() > 4 || new java.util.HashSet<>(moves).size() != moves.size()
                || moves.stream().anyMatch(move -> !move.matches("[a-z0-9]+"))) {
            throw new IllegalArgumentException("Moves require at most four unique Showdown move IDs");
        }
        return moves;
    }

    public record BossBattle(ResourceLocation minionEntity, int minionCount, int levelOffset,
                             ResourceLocation minionHeldItem, RosterMode mode, java.util.List<String> minionMoves) {
        public BossBattle(ResourceLocation minionEntity, int minionCount, int levelOffset,
                          ResourceLocation minionHeldItem, RosterMode mode) {
            this(minionEntity, minionCount, levelOffset, minionHeldItem, mode, java.util.List.of());
        }
        public BossBattle(ResourceLocation minionEntity, int minionCount, int levelOffset) {
            this(minionEntity, minionCount, levelOffset, null, RosterMode.HORDE);
        }
        public BossBattle {
            minionMoves = validatedMoves(minionMoves);
            if (minionEntity == null || mode == null || minionCount < 1 || minionCount > 5
                    || levelOffset < -99 || levelOffset > 99) {
                throw new IllegalArgumentException("Invalid boss horde settings");
            }
        }
    }

}
