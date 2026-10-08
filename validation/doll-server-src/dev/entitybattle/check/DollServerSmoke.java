package dev.entitybattle.check;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import dev.entitybattle.api.*;
import dev.entitybattle.compat.DollModelIds;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated-server conversion, stamping and persistent identity; no client classes loaded. */
@EventBusSubscriber(modid="entitybattle", value=Dist.DEDICATED_SERVER)
public final class DollServerSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static boolean ready, finished;
    private static int ticks;
    @SubscribeEvent public static void started(ServerStartedEvent event){ready=true;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ready||finished||++ticks<20)return;
        finished=true;
        try{
            var server=event.getServer();var level=server.overworld();
            require(PokemonSpecies.getByIdentifier(id("entitybattle:cow")).getImplemented(),"Active cow marked implemented on dedicated server");
            require(!PokemonSpecies.getByIdentifier(id("entitybattle:twilightforest_naga")).getImplemented(),"Absent optional source stays unimplemented");
            require(!PokemonSpecies.getByIdentifier(id("entitybattle:aether_moa")).getImplemented(),"Absent Aether stays unimplemented");
            for(String source:List.of("entitybattle:cow","entitybattle:twilightforest_naga","entitybattle:aether_moa")){
                var original=PokemonSpecies.getByIdentifier(id(source));
                var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
                try{
                    original.encode(buffer);
                    var decoded=new com.cobblemon.mod.common.pokemon.Species();decoded.setResourceIdentifier(original.getResourceIdentifier());decoded.decode(buffer);
                    require(decoded.getImplemented()==original.getImplemented(),"Cobblemon native species sync retains availability "+source);
                }finally{buffer.release();}
            }
            LOG.info("SPECIES_AVAILABILITY PASS: native sources enabled, absent Twilight/Aether hidden on dedicated server, all three native species sync round trips");
            var player=FakePlayerFactory.getMinecraft(level);
            var defaultItemClass=Class.forName("com.cobblemondoll.examplemod.item.DefaultModelItem");
            var base=BuiltInRegistries.ITEM.stream().filter(defaultItemClass::isInstance).filter(item->{
                try{return !(Boolean)defaultItemClass.getMethod("isGiant").invoke(item);}
                catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
            }).findFirst().orElseThrow();
            var mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("minecraft:sheep")).create(level);
            ((net.minecraft.world.entity.animal.Sheep)mob).setAge(-24000);
            ((net.minecraft.world.entity.animal.Sheep)mob).setColor(net.minecraft.world.item.DyeColor.RED);
            var species=EntityBattleProfiles.get(mob.getType()).species();
            var pokemon=PokemonSpecies.getByIdentifier(species).create(35);
            EntityPokemonOrigin.bind(pokemon,mob);EntityPokemonOrigin.refreshAppearance(pokemon,mob);
            PlayerExtensionsKt.party(player).add(pokemon);
            player.getInventory().clearContent();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(base));
            defaultItemClass.getMethod("handleConvertRequest",net.minecraft.server.level.ServerPlayer.class,String.class,boolean.class,String.class,String.class)
                    .invoke(null,player,species.toString(),false,String.join(",",pokemon.getAspects()),pokemon.getUuid().toString());
            var doll=player.getInventory().items.stream().filter(item->BuiltInRegistries.ITEM.getKey(item.getItem()).equals(id("kaleidoscope_doll:custom_doll"))).findFirst().orElseThrow();
            var identityClass=Class.forName("com.cobblemondoll.examplemod.doll.DollIdentity");
            String modelId=(String)identityClass.getMethod("modelIdOf",ItemStack.class).invoke(null,doll);
            var identity=DollModelIds.decode(modelId);
            require(identity!=null&&identity.species().equals(species.toString()),"Namespaced converted identity");
            require(identity.aspects().equals(pokemon.getAspects()),"Exact presentation aspects preserved");
            require((Boolean)identityClass.getMethod("isPokemonDoll",ItemStack.class).invoke(null,doll),"Still recognized as Pokemon doll");
            require(pokemon.getUuid().equals(identityClass.getMethod("pokemonUuid",ItemStack.class).invoke(null,doll)),"Native UUID stamping retained");
            require(identityClass.getMethod("speciesOf",ItemStack.class).invoke(null,doll)==pokemon.getSpecies(),"Native species lookup retained");
            var restored=ItemStack.parseOptional(level.registryAccess(),(net.minecraft.nbt.CompoundTag)doll.save(level.registryAccess()).copy());
            require(modelId.equals(identityClass.getMethod("modelIdOf",ItemStack.class).invoke(null,restored)),"Persistent doll model identity");
            require(pokemon.getUuid().equals(identityClass.getMethod("pokemonUuid",ItemStack.class).invoke(null,restored)),"Persistent UUID");
            LOG.info("DOLL_SERVER PASS: real handleConvertRequest, namespaced species, exact variants, Pokemon identity, UUID stamp and item save/load on dedicated server");
        }catch(Throwable failure){LOG.error("DOLL_SERVER FAIL",failure);}
        finally{event.getServer().halt(false);}
    }
}
