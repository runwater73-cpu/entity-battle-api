package dev.entitybattle.check;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import dev.entitybattle.api.*;
import dev.entitybattle.battle.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real source conversion, native turn, abort and original loot container in a disposable world. */
@EventBusSubscriber(modid = "entitybattle")
public final class SunflowerBossSmoke {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final ResourceLocation SOURCE = id("kaleidoscope_twilight:umbral_sunflower");
    private static final AABB AREA = new AABB(-10, 90, -10, 10, 112, 10);
    private static ServerLevel level;
    private static ServerPlayer player;
    private static Mob source;
    private static UUID sourceId;
    private static PokemonEntity boss;
    private static PokemonBattle battle;
    private static int ticks, stage, since, deadline;
    private static boolean ready;

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        level = event.getServer().overworld();
        for (int x=-1;x<=1;x++) for (int z=-1;z<=1;z++) {
            level.setChunkForced(x,z,true); level.getChunk(x,z);
        }
        ready = true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ready) return;
        try {
            ticks++;
            if (ticks == 20) {
                for (var entity:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,AREA)) entity.discard();
                for (var pos:BlockPos.betweenClosed(-8,95,-8,8,108,8)) {
                    if(level.getBlockEntity(pos) instanceof Container container) container.clearContent();
                    level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
                }
                for (var entity:level.getEntitiesOfClass(ItemEntity.class,AREA)) entity.discard();
                for (int x=-6;x<=6;x++) for (int z=-6;z<=6;z++)
                    level.setBlock(new BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
                player = FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"SunflowerSmoke"));
                player.setPos(0,100,4);
                var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
                field.setAccessible(true);
                ((Map<UUID,ServerPlayer>)field.get(event.getServer().getPlayerList())).put(player.getUUID(),player);
                var pokemon = PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100);
                pokemon.getMoveSet().clear();
                pokemon.getMoveSet().add(com.cobblemon.mod.common.api.moves.Moves.getByName("protect").create());
                Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
                source = (Mob)BuiltInRegistries.ENTITY_TYPE.get(SOURCE).create(level);
                require(source != null,"Installed source entity exists");
                source.setNoAi(true); source.setNoGravity(true); source.setPos(0,100,0);
                require(level.addFreshEntity(source) && !source.isRemoved(),"World source remains native despite global Pokemon Boss mode");
                sourceId = source.getUUID();
                var profile = EntityBattleProfiles.get(SOURCE);
                require(profile.boss() && !profile.catchable() && profile.minLevel()==43 && profile.maxLevel()==47,"Level 45 +/- 2 uncatchable Boss profile");
                require(EntityBattleProfiles.worldMode(profile)==EntityBattleProfile.WorldMode.NATIVE_MOB,"Source manual world mode");
                var p = EntityPokemonData.getOrCreate(source,profile);
                require(p.getLevel()>=43 && p.getLevel()<=47 && p.getMoveSet().getMoves().size()==4
                        && p.getAbility().getName().equals("sharpness"),"Independent species, native ability and all four moves");
                var tag = source.saveWithoutId(new CompoundTag()); tag.putBoolean("PhaseTwo",true);
                source.readAdditionalSaveData(tag);
                var attacks=new ArrayList<net.minecraft.world.entity.Entity>();
                for(String name:List.of("ground_spike","giant_sword","thrown_sword","earthquake")) {
                    var attack=BuiltInRegistries.ENTITY_TYPE.get(id("kaleidoscope_twilight:"+name)).create(level);
                    var owner=attack.getClass().getDeclaredField("owner"); owner.setAccessible(true); owner.set(attack,source);
                    attack.setPos(2,100,2); if(attack instanceof Mob mob) mob.setNoAi(true);
                    require(level.addFreshEntity(attack) && attack.getPersistentData().getUUID(EntityBossSources.OWNER).equals(sourceId),"Actual non-projectile attack owner tagged "+name);
                    attacks.add(attack);
                }
                var unrelated=BuiltInRegistries.ENTITY_TYPE.get(id("kaleidoscope_twilight:ground_spike")).create(level);
                unrelated.getClass().getMethod("setOwner",net.minecraft.world.entity.LivingEntity.class).invoke(unrelated,player);
                unrelated.setPos(-2,100,-2); level.addFreshEntity(unrelated);
                require(EntityNativePokemonConversion.convertPermanently(source,profile),"Converter API replaces source");
                boss = level.getEntitiesOfClass(PokemonEntity.class,AREA,e->EntityPokemonOrigin.sourceUuid(e.getPokemon()).filter(sourceId::equals).isPresent()).stream().findFirst().orElseThrow();
                require(EntityBossSources.managed(boss.getPokemon()),"Real PokemonEntity retains full source lifecycle snapshot");
                require(attacks.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved) && !unrelated.isRemoved(),"Conversion removes only this Boss's native attacks");
                unrelated.discard();
                require(EntityBossSources.rollback(boss),"Converter source rollback succeeds");
                require(attacks.stream().allMatch(attack->level.getEntity(attack.getUUID())==null),"Rollback does not replay expired native attacks");
                source = (Mob)level.getEntity(sourceId);
                require(source != null && (Boolean)source.getClass().getMethod("isPhaseTwo").invoke(source),"Rollback keeps source UUID and native PhaseTwo NBT");
                challenge(); stage=0; deadline=ticks+900;
            }
            if (battle == null) return;
            if (ticks > deadline) throw new AssertionError("Timeout stage="+stage);
            if (stage==0 && battle.getTurn()>=2 && battle.getDispatches().isEmpty()) {
                require(battle.getSide2().getActors()[0].getPokemonList().size()==1,"Single Boss without generated followers");
                require(battle.getSide2().getActivePokemon().getFirst().getBattlePokemon()!=null,"Actual first singles turn completed");
                battle.stop(); stage=1; since=ticks;
            } else if (stage==1 && ticks-since>100) {
                source = (Mob)level.getEntity(sourceId);
                require(source != null && source.isAlive(),"Interrupted fight restores the same living native source");
                LOG.info("SUNFLOWER_BOSS abort chest={} rewards={} items={}",chestItems(),rewards(),level.getEntitiesOfClass(ItemEntity.class,AREA).stream().map(i->i.getItem().toString()).toList());
                require(chestItems().isEmpty() && rewards()==0 && level.getEntitiesOfClass(ItemEntity.class,AREA).isEmpty(),"Abort produces no chest, item loot or reward");
                for (var p:Cobblemon.INSTANCE.getStorage().getParty(player)) p.recall();
                challenge(); stage=2; deadline=ticks+900;
            } else if (stage==2 && battle.getTurn()>=2 && battle.getDispatches().isEmpty()) {
                ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{
                    ">eval battle.sides[1].active[0].faint(); battle.faintMessages(); battle.win(battle.sides[0]);"});
                stage=3; since=ticks;
            } else if (stage==3 && battle.getEnded() && ticks-since>160) {
                require(level.getEntity(sourceId)==null,"Original source death animation completes after victory");
                var items=chestItems();
                require(items.getOrDefault("kaleidoscope_twilight:umbral_sunflower_trophy",0)==1
                        && items.getOrDefault("kaleidoscope_twilight:hot_tear_sword",0)==1,"Native BaseTFBoss chest has original trophy and sword exactly once");
                require(items.getOrDefault("cobblemon:miracle_seed",0)==0
                        && level.getEntitiesOfClass(ItemEntity.class,AREA).stream().noneMatch(i->i.getItem().is(BuiltInRegistries.ITEM.get(id("cobblemon:miracle_seed")))),"Combat held item not dropped");
                require(rewards()==1,"Whole victory gives exactly one level-1 species reward");
                LOG.info("SUNFLOWER_BOSS PASS: optional source, level 45 +/- 2, converter, real singles rounds, abort restoration, native chest and once-only reward");
                ready=false; event.getServer().halt(false);
            }
            if (!battle.getEnded() && (stage==0 || stage==2)) {
                var actor=battle.getSide1().getActors()[0];
                if(actor.getRequest()!=null && actor.getMustChoose() && actor.getRequest().getActive()!=null && !actor.getRequest().getActive().isEmpty())
                    com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(
                        new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),new ArrayList<>(List.of(new MoveActionResponse("protect",null,null)))),event.getServer(),player);
            }
        } catch (Throwable failure) {
            LOG.error("SUNFLOWER_BOSS FAIL",failure); ready=false;
            if(battle!=null && !battle.getEnded()) battle.stop();
            event.getServer().halt(false);
        }
    }
    private static void challenge() {
        require(EntityBattleSessions.start(player,Cobblemon.INSTANCE.getStorage().getParty(player).get(0).getUuid(),source),"Native source challenge starts official wild battle");
        battle=BattleRegistry.getBattleByParticipatingPlayer(player);
        boss=battle.getSide2().getActors()[0].getPokemonList().getFirst().getEntity();
        require(BuiltInRegistries.ITEM.getKey(boss.getPokemon().heldItem().getItem()).equals(id("cobblemon:miracle_seed")),"Battle preparation equips actual Miracle Seed");
    }
    private static Map<String,Integer> chestItems() {
        var result=new HashMap<String,Integer>();
        for(var pos:BlockPos.betweenClosed(-8,95,-8,8,108,8)) if(level.getBlockEntity(pos) instanceof Container chest)
            for(int slot=0;slot<chest.getContainerSize();slot++) {
                var stack=chest.getItem(slot);
                if(!stack.isEmpty()) result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Integer::sum);
            }
        return result;
    }
    private static int rewards() {
        var profile=EntityBattleProfiles.get(SOURCE); int count=0;
        for(var p:Cobblemon.INSTANCE.getStorage().getParty(player)) if(p.getLevel()==1 && p.getSpecies().getResourceIdentifier().equals(profile.species())) count++;
        for(var p:Cobblemon.INSTANCE.getStorage().getPC(player)) if(p.getLevel()==1 && p.getSpecies().getResourceIdentifier().equals(profile.species())) count++;
        return count;
    }
    private static ResourceLocation id(String s) { return ResourceLocation.parse(s); }
    private static void require(boolean c,String label) { if(!c) throw new AssertionError(label); LOG.info("SUNFLOWER_BOSS CHECK {}",label); }
}
