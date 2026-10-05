package dev.entitybattle.item;

import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.battle.EntityNativePokemonConversion;
import dev.entitybattle.client.EntityBattleClientProfiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Manually makes an eligible native mob a permanent wild PokemonEntity. */
public final class ConversionItem extends Item {
    public ConversionItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob)) return InteractionResult.PASS;
        if (player.level().isClientSide()) {
            return EntityBattleClientProfiles.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        EntityBattleProfile profile = EntityBattleProfiles.get(mob.getType());
        if (profile == null || profile.worldMode() != EntityBattleProfile.WorldMode.NATIVE_MOB) {
            return InteractionResult.PASS;
        }
        if (!EntityNativePokemonConversion.convertPermanently(mob, profile)) return InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }
}
