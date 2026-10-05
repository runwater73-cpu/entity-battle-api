package dev.entitybattle.battle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.logging.LogUtils;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

/** Converts an opted-in native spawn into a single Cobblemon world entity. */
public final class EntityPokemonWorldSpawns {
    private static final Logger LOGGER = LogUtils.getLogger();

    private EntityPokemonWorldSpawns() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob instanceof PokemonEntity
                || !(event.getLevel() instanceof ServerLevel level)) return;
        EntityBattleProfile profile = EntityBattleProfiles.get(mob.getType());
        if (profile == null || profile.worldMode() != EntityBattleProfile.WorldMode.POKEMON_ENTITY) return;

        EntityNativePokemonConversion.Converted converted = EntityNativePokemonConversion.sendOut(mob, profile, false);
        if (converted == null) {
            LOGGER.error("Could not replace native entity {} with PokemonEntity", mob.getUUID());
            return;
        }
        event.setCanceled(true);
        LOGGER.debug("Converted {} ({}) into PokemonEntity {}", mob.getUUID(), profile.entity(),
                converted.entity().getUUID());
    }
}
