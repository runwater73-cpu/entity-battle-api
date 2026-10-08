package dev.entitybattle.check;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import dev.entitybattle.api.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual TeamRocket fill/use/roll entry points, in an isolated dedicated server. */
@EventBusSubscriber(modid = "entitybattle")
public final class RecruitmentSmoke {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void empty(ServerPlayer player) {
        var party = PlayerExtensionsKt.party(player);
        var members = new ArrayList<Pokemon>();
        party.forEach(members::add);
        members.forEach(party::remove);
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        try {
            var level = event.getServer().overworld();
            var player = FakePlayerFactory.getMinecraft(level);
            player.getAbilities().instabuild = false;
            var item = BuiltInRegistries.ITEM.get(id("teamrocket:recruit_ball"));
            var type = Class.forName("com.jhnwudi666.teamrocket.recruit.RecruitBallItem");
            Method fill = type.getMethod("fillFromParty", ServerPlayer.class, ItemStack.class, int.class);
            Method write = type.getMethod("writeTag", ItemStack.class, String.class, String.class, int[].class, int.class);
            int[] ivs = {31, 22, 17, 30, 25, 10};
            for (var entity : List.of(EntityType.COW, EntityType.SHEEP, EntityType.SLIME, EntityType.ZOMBIE, EntityType.WARDEN)) {
                empty(player);
                var mob = entity.create(level);
                if (mob instanceof Sheep sheep) { sheep.setColor(DyeColor.RED); sheep.setAge(-24000); }
                if (mob instanceof Slime slime) slime.setSize(4, true);
                var pokemon = EntityPokemonData.getOrCreate(mob, EntityBattleProfiles.get(entity));
                pokemon.setNickname(Component.literal("招募身份验证"));
                pokemon.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(id("cobblemon:leftovers"))), false, false);
                var originalId = pokemon.getUuid();
                var appearance = EntityPokemonOrigin.appearance(pokemon);
                var moves = pokemon.getMoveSet().getMoves().stream().map(move -> move.getName()).toList();
                var ability = pokemon.getAbility().getName();
                var nature = pokemon.getNature();
                var source = EntityPokemonOrigin.entityId(pokemon);
                require(PlayerExtensionsKt.party(player).add(pokemon), "Add source to party");
                var ball = new ItemStack(item);
                require((Boolean) fill.invoke(null, player, ball, 0), "fillFromParty " + entity);
                require(PlayerExtensionsKt.party(player).get(0) == null, "Source removed exactly once");
                var tag = ball.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                require(tag.getString("species").equals(pokemon.getSpecies().getResourceIdentifier().toString()), "Namespaced ball");
                require(tag.contains("pokemon_nbt"), "Full individual saved");
                ball = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) ball.save(level.registryAccess()));
                player.setItemInHand(InteractionHand.MAIN_HAND, ball);
                item.use(level, player, InteractionHand.MAIN_HAND);
                var restored = PlayerExtensionsKt.party(player).get(0);
                require(restored != null && restored.getSpecies() == pokemon.getSpecies(), "Correct species on real use");
                require(restored.getUuid().equals(originalId), "Individual UUID retained");
                require(EntityPokemonOrigin.entityId(restored).equals(source), "Native origin retained");
                require(EntityPokemonOrigin.appearance(restored).equals(appearance), "Variant retained");
                require(restored.getMoveSet().getMoves().stream().map(move -> move.getName()).toList().equals(moves), "Moves retained");
                require(restored.getAbility().getName().equals(ability) && restored.getNature().equals(nature), "Ability and nature retained");
                require(restored.getNickname().getString().equals("招募身份验证"), "Nickname retained");
                require(BuiltInRegistries.ITEM.getKey(restored.heldItem().getItem()).equals(id("cobblemon:leftovers")), "Held item retained");
                require(ball.isEmpty(), "Ball consumed once");
            }
            for (String species : List.of("entitybattle:cow", "cow", "charizard")) {
                empty(player);
                var ball = new ItemStack(item);
                write.invoke(null, ball, "common", species, ivs, 35);
                player.setItemInHand(InteractionHand.MAIN_HAND, ball);
                item.use(level, player, InteractionHand.MAIN_HAND);
                var result = PlayerExtensionsKt.party(player).get(0);
                String wanted = species.equals("charizard") ? "cobblemon:charizard" : "entitybattle:cow";
                require(result != null && result.getSpecies().getResourceIdentifier().equals(id(wanted)), "Generated / old / official ball " + species);
                require(result.getLevel() == 35 && result.getIvs().get(com.cobblemon.mod.common.api.pokemon.stats.Stats.HP) == 31, "Machine level and IV retained");
                if (!species.equals("charizard")) require(EntityPokemonOrigin.entityId(result).orElseThrow().equals(id("minecraft:cow")), "Generated presentation");
            }
            empty(player);
            var invalid = new ItemStack(item);
            write.invoke(null, invalid, "common", "entitybattle:removed_source", ivs, 35);
            player.setItemInHand(InteractionHand.MAIN_HAND, invalid);
            item.use(level, player, InteractionHand.MAIN_HAND);
            require(PlayerExtensionsKt.party(player).get(0) == null && invalid.getCount() == 1, "Missing API species rejects without random or loss");
            var block = BuiltInRegistries.BLOCK.get(id("teamrocket:recruit_block"));
            var machineType = Class.forName("com.jhnwudi666.teamrocket.recruit.RecruitBlockEntity");
            var machine = (net.minecraft.world.level.block.entity.BlockEntity) machineType
                    .getConstructor(BlockPos.class, net.minecraft.world.level.block.state.BlockState.class)
                    .newInstance(new BlockPos(0, 64, 0), block.defaultBlockState());
            machine.setLevel(level);
            var allowed = machineType.getDeclaredMethod("allAllowedSpecies", net.minecraft.server.level.ServerLevel.class);
            allowed.setAccessible(true);
            var pool = (List<com.cobblemon.mod.common.pokemon.Species>) allowed.invoke(machine, level);
            require(pool.contains(PokemonSpecies.getByIdentifier(id("entitybattle:cow"))), "Cow eligible for native pool");
            require(!pool.contains(PokemonSpecies.getByIdentifier(id("entitybattle:minecraft_warden"))), "Wild Boss excluded from machine");
            var flags = new IdentityHashMap<com.cobblemon.mod.common.pokemon.Species, Boolean>();
            try {
                PokemonSpecies.getSpecies().forEach(species -> {
                    flags.put(species, species.getImplemented());
                    species.setImplemented(species.getResourceIdentifier().equals(id("entitybattle:cow")));
                });
                var roll = machineType.getDeclaredMethod("rollRecruit", net.minecraft.server.level.ServerLevel.class, int.class);
                roll.setAccessible(true);
                var result = roll.invoke(machine, level, 0);
                require(result != null, "Actual machine roll");
                var selected = result.getClass().getDeclaredField("species");
                selected.setAccessible(true);
                require(selected.get(result).equals("entitybattle:cow"), "Actual machine output retains namespace");
            } finally { flags.forEach((species, implemented) -> species.setImplemented(implemented)); }
            LOG.info("RECRUITMENT PASS: five native individuals fill/save/use with UUID, variant, moves, ability, nature and held item; old/generated/official balls; no random fallback; real machine pool and roll");
        } catch (Throwable failure) { LOG.error("RECRUITMENT FAIL", failure); }
        finally { empty(FakePlayerFactory.getMinecraft(event.getServer().overworld())); event.getServer().halt(false); }
    }
}
