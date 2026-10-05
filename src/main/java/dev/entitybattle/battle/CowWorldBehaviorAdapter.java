package dev.entitybattle.battle;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.api.EntityBattleProfile;
import java.util.EnumSet;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;

/** Installs vanilla cow Goal components on a real PokemonEntity. */
public final class CowWorldBehaviorAdapter implements EntityPokemonWorldBehaviorAdapter {
    @Override
    public void install(PokemonEntity pokemon, EntityBattleProfile profile) {
        pokemon.goalSelector.addGoal(0, activeOnly(pokemon, new FloatGoal(pokemon)));
        pokemon.goalSelector.addGoal(1, activeOnly(pokemon, new PanicGoal(pokemon, 2.0D)));
        pokemon.goalSelector.addGoal(3, activeOnly(pokemon,
                new TemptGoal(pokemon, 1.25D, stack -> stack.is(ItemTags.COW_FOOD), false)));
        pokemon.goalSelector.addGoal(5, activeOnly(pokemon,
                new WaterAvoidingRandomStrollGoal(pokemon, 1.0D)));
        pokemon.goalSelector.addGoal(6, activeOnly(pokemon,
                new LookAtPlayerGoal(pokemon, Player.class, 6.0F)));
        pokemon.goalSelector.addGoal(7, activeOnly(pokemon,
                new RandomLookAroundGoal(pokemon)));
    }

    private static Goal activeOnly(PokemonEntity pokemon, Goal nativeGoal) {
        return new Goal() {
            {
                setFlags(EnumSet.copyOf(nativeGoal.getFlags()));
            }

            private boolean active() {
                return pokemon.isAlive() && pokemon.getPokemon().isWild()
                        && pokemon.getOwnerUUID() == null && pokemon.getBattleId() == null
                        && !pokemon.isBusy();
            }

            @Override public boolean canUse() {
                return active() && nativeGoal.canUse();
            }

            @Override public boolean canContinueToUse() {
                return active() && nativeGoal.canContinueToUse();
            }

            @Override public boolean isInterruptable() {
                return nativeGoal.isInterruptable();
            }

            @Override public boolean requiresUpdateEveryTick() {
                return nativeGoal.requiresUpdateEveryTick();
            }

            @Override public void start() {
                nativeGoal.start();
            }

            @Override public void stop() {
                nativeGoal.stop();
                if (!active() && nativeGoal.getFlags().contains(Goal.Flag.MOVE)) {
                    pokemon.getNavigation().stop();
                }
            }

            @Override public void tick() {
                nativeGoal.tick();
            }
        };
    }
}
