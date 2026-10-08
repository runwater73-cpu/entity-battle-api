package dev.entitybattle.battle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.api.EntityPokemonOrigin;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Small source-side cues while Cobblemon handles move damage and target effects. */
public final class LichMoveEffects {
    private static final ResourceLocation LICH = ResourceLocation.fromNamespaceAndPath("twilightforest", "lich");

    private LichMoveEffects() {}

    public static void onMove(PokemonEntity entity, String moveId) {
        if (!(entity.level() instanceof ServerLevel level)
                || !LICH.equals(EntityPokemonOrigin.entityId(entity.getPokemon()).orElse(null))) return;
        ParticleOptions particle = switch (moveId) {
            case "protect" -> ParticleTypes.ENCHANT;
            case "supersonic" -> ParticleTypes.NOTE;
            case "shadowball" -> ParticleTypes.SOUL;
            case "flameburst" -> ParticleTypes.FLAME;
            default -> null;
        };
        if (particle == null) return;
        level.sendParticles(particle, entity.getX(), entity.getY() + entity.getBbHeight() * 0.65,
                entity.getZ(), 18, 0.55, 0.65, 0.55, 0.04);
    }
}
