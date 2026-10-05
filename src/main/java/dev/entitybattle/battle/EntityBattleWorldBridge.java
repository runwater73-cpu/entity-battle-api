package dev.entitybattle.battle;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.world.entity.Mob;

/** Converts health between native Mob and Cobblemon Pokemon scales. */
public final class EntityBattleWorldBridge {
    private EntityBattleWorldBridge() {}

    public static void applyPokemonHealthToMob(Mob mob, Pokemon pokemon) {
        if (pokemon.getCurrentHealth() <= 0) {
            mob.setHealth(1F);
            return;
        }
        float ratio = pokemon.getCurrentHealth() / (float) Math.max(1, pokemon.getMaxHealth());
        mob.setHealth(Math.max(1F, mob.getMaxHealth() * ratio));
    }

    /** A native living mob cannot have zero health without entering vanilla death processing. */
    public static void copyMobHealthToPokemon(Mob mob, Pokemon pokemon) {
        int health = Math.round(pokemon.getMaxHealth() * mob.getHealth()
                / Math.max(1F, mob.getMaxHealth()));
        pokemon.setCurrentHealth(Math.max(1, health));
    }
}
