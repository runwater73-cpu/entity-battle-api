package dev.entitybattle.check;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.google.gson.Gson;
import dev.entitybattle.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual native item interaction, pending confirmation and timed world evolution. */
@EventBusSubscriber(modid="entitybattle")
public final class EvolutionSmoke {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private record Route(String source, String target, int level, String sourceSpecies, String targetSpecies) {}
    private static ServerLevel level;
    private static ServerPlayer player;
    private static Pokemon timed;
    private static PokemonEntity world;
    private static int ticks;
    private static int startedAt = -1;
    private static final List<PokemonEntity> sent = new ArrayList<>();
    private static boolean failed;
    private static ResourceLocation id(String key) { return ResourceLocation.parse(key); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void empty() {
        var party=PlayerExtensionsKt.party(player);
        var list=new ArrayList<Pokemon>(); party.forEach(list::add);
        list.forEach(p -> { p.recall(); party.remove(p); });
        sent.forEach(PokemonEntity::discard); sent.clear();
    }
    private static Evolution route(Pokemon pokemon, String target) {
        return java.util.stream.StreamSupport.stream(pokemon.getEvolutions().spliterator(),false).filter(e -> target.equals(e.getResult().getSpecies())).findFirst().orElseThrow();
    }
    private static Map<Object,Integer> stats(com.cobblemon.mod.common.pokemon.PokemonStats stats) {
        var values=new HashMap<Object,Integer>(); stats.forEach(e->values.put(e.getKey(),e.getValue())); return values;
    }
    private static Pokemon source(Route r) {
        empty();
        var type=BuiltInRegistries.ENTITY_TYPE.get(id(r.source));
        require(type.create(level) instanceof Mob, "Source exists "+r.source);
        var mob=(Mob)type.create(level);
        var pokemon=EntityPokemonData.getOrCreate(mob,EntityBattleProfiles.get(type));
        pokemon.setLevel(r.level);
        pokemon.setNickname(Component.literal("转化测试"));
        pokemon.swapHeldItem(new ItemStack(Items.DIAMOND),false,false);
        require(PlayerExtensionsKt.party(player).add(pokemon), "Party accepts source");
        return pokemon;
    }
    private static PokemonEntity send(Pokemon pokemon) {
        var entity=pokemon.sendOut(level,new Vec3(0,100,0),null,e->kotlin.Unit.INSTANCE);
        require(entity!=null,"Native sendOut");
        entity.setNoGravity(true); entity.setNoAi(true);
        sent.add(entity);
        return entity;
    }
    private static void interact(PokemonEntity entity, ItemStack item) {
        player.setItemInHand(InteractionHand.MAIN_HAND,item);
        entity.mobInteract(player,InteractionHand.MAIN_HAND);
    }
    private static void result(Pokemon pokemon, Route r, UUID uuid, Object ivs, Object evs, Object nature, List<String> moves) {
        require(pokemon.getSpecies().getResourceIdentifier().equals(id(r.targetSpecies)),"Target species "+r.target);
        require(pokemon.getPreEvolution()!=null&&pokemon.getPreEvolution().getSpecies().getResourceIdentifier().equals(id(r.sourceSpecies)),"Native pre-evolution graph "+r.target);
        require(EntityPokemonOrigin.entityId(pokemon).orElseThrow().equals(id(r.target)),"Target presentation "+r.target);
        require(EntityPokemonOrigin.sourceUuid(pokemon).isEmpty()&&EntityPokemonOrigin.appearance(pokemon).isEmpty(),"Old source identity cleared");
        var decoded=EntityPokemonPresentation.decode(pokemon.getAspects());
        require(decoded!=null&&decoded.source().equals(id(r.target)),"Native lightweight GUI aspects updated");
        require(pokemon.getUuid().equals(uuid)&&pokemon.getLevel()==r.level,"UUID and level retained");
        require(stats(pokemon.getIvs()).equals(ivs)&&stats(pokemon.getEvs()).equals(evs)&&pokemon.getNature().equals(nature),"IV EV nature retained");
        require(pokemon.getNickname().getString().equals("转化测试")&&pokemon.heldItem().is(Items.DIAMOND),"Nickname and held gear retained");
        require(pokemon.getMoveSet().getMoves().stream().map(m->m.getName()).toList().containsAll(moves),"Old moves retained");
        require(pokemon.getForm().getAbilities().getMapping().values().stream().flatMap(Collection::stream).anyMatch(a->a.getTemplate().getName().equals(pokemon.getAbility().getName())),"Target ability valid");
        require(!pokemon.getPersistentData().contains("entitybattle:boss_source")&&!pokemon.getPersistentData().contains("entitybattle:source_helpers"),"No inherited boss lifecycle");
        var restored=Pokemon.Companion.loadFromNBT(level.registryAccess(),pokemon.saveToNBT(level.registryAccess(),new CompoundTag()));
        require(restored.getSpecies()==pokemon.getSpecies()&&EntityPokemonOrigin.entityId(restored).equals(EntityPokemonOrigin.entityId(pokemon)),"Native NBT roundtrip");
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        try {
            level=event.getServer().overworld(); player=FakePlayerFactory.getMinecraft(level);
            level.setChunkForced(0,0,true);
            com.cobblemon.mod.common.api.events.CobblemonEvents.EVOLUTION_DISPLAY.subscribe(preview -> {
                var source=EntityBattleProfiles.visualSpeciesMap().get(preview.getDisplay().getSpecies().getResourceIdentifier());
                if (source!=null) {
                    var visual=EntityPokemonPresentation.decode(preview.getDisplay().getAspects());
                    require(visual!=null&&source.equals(visual.source()),"Actual native pending evolution display uses target model");
                }
            });
            player.getAbilities().instabuild=false;
            // The native owner lookup uses PlayerList, rather than the party's UUID alone.
            var players=event.getServer().getPlayerList();
            var index=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); index.setAccessible(true);
            ((Map<UUID,ServerPlayer>)index.get(players)).put(player.getUUID(),player);
            require(players.getPlayer(player.getUUID())==player,"Native owner lookup resolves fixture player");
            var routes=new Gson().fromJson(Files.readString(Path.of("../../validation/creature-acquisition.json")),Route[].class);
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
                    ItemStack.EMPTY,new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY,
                    new ItemStack(Items.REDSTONE),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.REDSTONE),
                    ItemStack.EMPTY,new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY));
            var recipe=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level).orElseThrow();
            require(recipe.id().equals(id("entitybattle:converter"))&&recipe.value().assemble(input,level.registryAccess()).is(BuiltInRegistries.ITEM.get(id("entitybattle:converter"))),"Actual survival converter recipe");
            boolean powderPresent=BuiltInRegistries.ITEM.containsKey(id("twilightforest:transformation_powder"));
            if (!powderPresent) {
                var r=Arrays.stream(routes).filter(v->v.source.equals("minecraft:zombie")).findFirst().orElseThrow();
                var pokemon=source(r); var entity=send(pokemon);
                var wrong=new ItemStack(Items.DIRT,2); interact(entity,wrong);
                require(wrong.getCount()==2&&pokemon.getEvolutionProxy().server().isEmpty(),"Absent powder tag cannot match arbitrary items");
                require(!route(pokemon,r.targetSpecies).test(pokemon),"Absent target cannot pass evolution test");
                var other=Arrays.stream(routes).filter(v->v.source.equals("minecraft:horse")).findFirst().orElseThrow();
                var horse=source(other); var horseEntity=send(horse); interact(horseEntity,wrong);
                require(wrong.getCount()==2&&horse.getEvolutionProxy().server().isEmpty(),"Available vanilla target still cannot match empty tag");
                LOG.info("EVOLUTION_ABSENT PASS: empty optional powder tag; unavailable target; vanilla target; no arbitrary item use");
                empty(); event.getServer().halt(false); return;
            }
            for (var r:routes) {
                var pokemon=source(r); var entity=send(pokemon);
                require(!r.source.equals("minecraft:zombie")||!pokemon.isUncatchable(),"Zombie capture route open");
                var evolution=route(pokemon,r.targetSpecies);
                require(evolution.getOptional()&&!evolution.getConsumeHeldItem(),"Native deferred evolution preserves gear");
                var powder=new ItemStack(BuiltInRegistries.ITEM.get(id("twilightforest:transformation_powder")),2);
                pokemon.setLevel(r.level-1); interact(entity,powder);
                require(powder.getCount()==2&&pokemon.getEvolutionProxy().server().isEmpty(),"Below-level rejected "+r.source);
                pokemon.setLevel(r.level);
                var wrong=new ItemStack(Items.DIRT,2); interact(entity,wrong);
                require(wrong.getCount()==2&&pokemon.getEvolutionProxy().server().isEmpty(),"Wrong item rejected");
                var target=PokemonSpecies.getByIdentifier(id(r.targetSpecies));
                require(target!=null&&target.getImplemented(),"Target installed "+r.target);
                target.setImplemented(false);
                try { interact(entity,powder); require(powder.getCount()==2&&pokemon.getEvolutionProxy().server().isEmpty(),"Unavailable target rejects before consumption"); }
                finally { target.setImplemented(true); }
                var uuid=pokemon.getUuid(); var ivs=stats(pokemon.getIvs()); var evs=stats(pokemon.getEvs()); var nature=pokemon.getNature();
                var moves=pokemon.getMoveSet().getMoves().stream().map(m->m.getName()).toList();
                interact(entity,powder);
                require(powder.getCount()==1&&pokemon.getEvolutionProxy().server().contains(evolution),"Real item use consumes once and queues "+r.source);
                interact(entity,powder); require(powder.getCount()==1,"Duplicate pending evolution does not consume again");
                target.setImplemented(false);
                try { pokemon.getEvolutionProxy().server().start(evolution); require(pokemon.getSpecies().getResourceIdentifier().equals(id(r.sourceSpecies)),"Accept gate protects removed target"); }
                finally { target.setImplemented(true); }
                pokemon.recall();
                pokemon.getPersistentData().put("entitybattle:boss_source",new CompoundTag());
                pokemon.getEvolutionProxy().server().start(evolution);
                result(pokemon,r,uuid,ivs,evs,nature,moves);
                LOG.info("EVOLUTION CHECK {} -> {}",r.source,r.target);
            }
            empty();
            var official=PokemonProperties.Companion.parse("pikachu level=30", " ", "=").create();
            PlayerExtensionsKt.party(player).add(official);
            var officialEntity=send(official);
            var stone=new ItemStack(BuiltInRegistries.ITEM.get(id("cobblemon:thunder_stone")));
            interact(officialEntity,stone);
            require(stone.isEmpty()&&!official.getEvolutionProxy().server().isEmpty(),"Official evolution item unaffected");
            var evolution=official.getEvolutionProxy().server().iterator().next(); official.recall(); official.getEvolutionProxy().server().start(evolution);
            require(official.getSpecies().getResourceIdentifier().equals(id("cobblemon:raichu")),"Official evolution result unaffected");
            var r=Arrays.stream(routes).filter(v->v.source.equals("minecraft:zombie")).findFirst().orElseThrow();
            timed=source(r); world=send(timed);
        } catch(Throwable failure) { failed=true; LOG.error("EVOLUTION FAIL",failure); event.getServer().halt(false); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (failed||timed==null) return;
        ++ticks;
        try {
            if (startedAt < 0) {
                if (timed.getEntity()!=world) {
                    require(ticks<200,"Forced fixture chunk must become accessible"); return;
                }
                var powder=new ItemStack(BuiltInRegistries.ITEM.get(id("twilightforest:transformation_powder")));
                interact(world,powder); timed.getEvolutionProxy().server().start(route(timed,"entitybattle:twilightforest_lich_minion"));
                require(powder.isEmpty()&&world.getEntityData().get(PokemonEntity.getEVOLUTION_STARTED()),"Timed native world evolution started");
                startedAt=ticks; return;
            }
            if (ticks-startedAt<255) return;
            require(timed.getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_lich_minion")),"Timed world species changed");
            require(EntityPokemonOrigin.entityId(timed).orElseThrow().equals(id("twilightforest:lich_minion")),"Timed world origin updated");
            require(timed.getAbility().getName().equals("entitybattlesoulcovenant"),"Minion receives native target ability");
            require(timed.getEntity()==world&&!world.getEntityData().get(PokemonEntity.getEVOLUTION_STARTED()),"World entity retained and animation finished");
            LOG.info("EVOLUTION PASS: 16 native item routes, levels, optional targets, one powder, gear, IV EV nature UUID moves, NBT, official control, timed world model identity and minion ability");
        } catch(Throwable failure) { LOG.error("EVOLUTION FAIL",failure); }
        empty(); event.getServer().halt(false);
    }
}
