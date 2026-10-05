package dev.entitybattle.battle;

import dev.entitybattle.api.EntityBattleProfile;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

/** A small adapter for source-mob behavior that can run on a real PokemonEntity. */
public interface EntityPokemonWorldBehaviorAdapter {
    void install(PokemonEntity pokemon, EntityBattleProfile profile);
}
