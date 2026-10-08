package dev.entitybattle.item;

import dev.entitybattle.EntityBattleConfig;
import dev.entitybattle.api.EntityBattleProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.battle.EntityNativePokemonConversion;
import dev.entitybattle.client.EntityBattleClientProfiles;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Manually makes an eligible native mob a permanent wild PokemonEntity. */
public final class ConversionItem extends Item {
    private static final int USE_DURATION = 72_000;
    private static final double MAX_DISTANCE_SQUARED = 36.0;
    private static final Map<ServerPlayer, UUID> TARGETS = new WeakHashMap<>();

    public ConversionItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob)) return InteractionResult.PASS;
        if (player.level().isClientSide()) {
            if (!EntityBattleClientProfiles.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))) {
                return InteractionResult.PASS;
            }
            player.startUsingItem(hand);
            return InteractionResult.SUCCESS;
        }
        Mob source = dev.entitybattle.api.EntityBattleSources.resolve(mob);
        if (source == null) return InteractionResult.PASS;
        EntityBattleProfile profile = EntityBattleProfiles.get(source.getType());
        if (profile == null || EntityBattleProfiles.worldMode(profile)
                != EntityBattleProfile.WorldMode.NATIVE_MOB) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        var denial = dev.entitybattle.api.EntityBattleSources.denial(source);
        if (denial != null) { player.displayClientMessage(denial, true); return InteractionResult.FAIL; }
        if (EntityBattleConfig.CONVERSION_SECONDS.get() == 0) {
            return EntityNativePokemonConversion.convertPermanently(source, profile)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        TARGETS.put(serverPlayer, mob.getUUID());
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer player)) return;
        UUID targetId = TARGETS.get(player);
        if (targetId == null) return;
        var target = player.serverLevel().getEntity(targetId);
        if (!(target instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()
                || player.distanceToSqr(mob) > MAX_DISTANCE_SQUARED) {
            TARGETS.remove(player);
            player.stopUsingItem();
            player.displayClientMessage(Component.translatable("entitybattle.conversion_cancelled"), true);
            return;
        }
        Mob source = dev.entitybattle.api.EntityBattleSources.resolve(mob);
        EntityBattleProfile profile = source == null ? null : EntityBattleProfiles.get(source.getType());
        if (profile == null || EntityBattleProfiles.worldMode(profile)
                != EntityBattleProfile.WorldMode.NATIVE_MOB) {
            TARGETS.remove(player);
            player.stopUsingItem();
            return;
        }

        int required = EntityBattleConfig.CONVERSION_SECONDS.get() * 20;
        int elapsed = USE_DURATION - remaining + 1;
        if (elapsed >= required) {
            TARGETS.remove(player);
            player.stopUsingItem();
            var denial = dev.entitybattle.api.EntityBattleSources.denial(source);
            if (denial != null) { player.displayClientMessage(denial, true); return; }
            boolean converted = EntityNativePokemonConversion.convertPermanently(source, profile);
            player.displayClientMessage(Component.translatable(converted
                    ? "entitybattle.conversion_complete" : "entitybattle.conversion_failed"), true);
        } else if (elapsed == 1 || elapsed % 5 == 0) {
            player.displayClientMessage(Component.translatable("entitybattle.conversion_progress",
                    Math.min(100, elapsed * 100 / required)), true);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player) TARGETS.remove(player);
    }
}
