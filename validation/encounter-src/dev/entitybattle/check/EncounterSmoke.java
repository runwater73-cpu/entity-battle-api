package dev.entitybattle.check;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.battle.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real incomplete / complete Boss encounters in an isolated world; excluded from release. */
@EventBusSubscriber(modid="entitybattle")
public final class EncounterSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final AABB AREA=new AABB(-16,90,-16,19,120,19);
    private static boolean ready;
    private static int ticks,stage,deadline,rewardsBefore,lootBefore,waitingSince;
    private static ServerPlayer player;
    private static PokemonBattle battle;
    private static PokemonEntity boss;
    private static com.cobblemon.mod.common.pokemon.Pokemon pokemon;
    private static List<PokemonEntity> followers;
    private static UUID sourceId;
    private static int initialHealth;
    @SubscribeEvent public static void start(ServerStartedEvent e){
        var level=e.getServer().overworld();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            level.setChunkForced(x,z,true);level.getChunk(x,z);
        }
        ready=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!ready)return;
        try{
            ticks++;
            var level=e.getServer().overworld();
            if(ticks==20){
                level.setChunkForced(0,0,true);
                for(int x=-8;x<11;x++)for(int z=-8;z<11;z++)
                    level.setBlock(new BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,AREA))entity.discard();
                player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"EncounterSmoke"));
                player.setPos(0,100,4);
                var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
                ((Map<UUID,ServerPlayer>)field.get(e.getServer().getPlayerList())).put(player.getUUID(),player);
                var party=Cobblemon.INSTANCE.getStorage().getParty(player);
                for(int i=0;i<6;i++)party.add(PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100));
                party.get(0).sendOut(level,new Vec3(0,100,4),null,s->kotlin.Unit.INSTANCE);
                Mob source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:ur_ghast")).create(level);
                source.setPos(0,100,0);
                require(EntityBattleProfiles.get(source.getType())!=null,"Boss profile loaded");
                LOG.info("BOSS_ENCOUNTER source health={} max={} alive={} removed={} difficulty={}",source.getHealth(),source.getMaxHealth(),source.isAlive(),source.isRemoved(),level.getDifficulty());
                var individual=dev.entitybattle.api.EntityPokemonData.getOrCreate(source,EntityBattleProfiles.get(source.getType()));
                LOG.info("BOSS_ENCOUNTER individual={} health={}",individual,individual==null?null:individual.getCurrentHealth());
                require(EntityNativePokemonConversion.convertPermanently(source,EntityBattleProfiles.get(source.getType())),"Permanent converted Boss");
                boss=level.getEntitiesOfClass(PokemonEntity.class,AREA,p->p.getPokemon().getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_ur_ghast"))).getFirst();
                pokemon=boss.getPokemon();initialHealth=pokemon.getCurrentHealth();rewardsBefore=rewardCount();lootBefore=lootCount();
                begin();stage=0;deadline=ticks+400;
            }
            if(battle==null)return;
            if(ticks>deadline)throw new AssertionError("Timeout stage="+stage);
            if(stage==0 && activeCount()==3){
                require(EntityBossEncounters.awaitingSettlement(pokemon),"Encounter tracked before faint");
                followers=opponents().stream().map(p->p.getEntity()).filter(p->p!=boss).toList();
                faintMain();
                stage=1;waitingSince=-1;deadline=ticks+700;
            }else if(stage==1 && pokemon.isFainted() && waitingSince<0){
                waitingSince=ticks;
            }else if(stage==1 && waitingSince>=0 && ticks-waitingSince>150){
                require(pokemon.isFainted(),"Main Boss fainted while escorts survive");
                require(!battle.getEnded(),"Partial defeat did not win battle");
                require(lootCount()==lootBefore&&rewardCount()==rewardsBefore,"No premature native loot or level-one reward");
                require(!boss.isRemoved(),"Fainted Boss retained for recovery after normal removal time");
                player.setPos(400,100,400);battle.checkFlee();
                require(battle.getEnded(),"Actual distance escape ended encounter");
                stage=2;deadline=ticks+180;
            }else if(stage==2 && ticks%10==0){
                var restored=pokemon.getEntity();
                require(restored!=null&&!restored.isRemoved(),"Boss returned after partial-defeat escape");
                require(pokemon.getCurrentHealth()==initialHealth,"Original pre-battle HP restored");
                require(followers.stream().allMatch(PokemonEntity::isRemoved),"Temporary escorts cleaned after escape");
                require(lootCount()==lootBefore&&rewardCount()==rewardsBefore,"Escape did not grant loot or reward");
                require(!EntityBossEncounters.awaitingSettlement(pokemon),"Recovery marker cleared");
                boss=restored;player.setPos(0,100,4);begin();stage=3;deadline=ticks+600;
            }else if(stage==3 && activeCount()==3){
                send("battle.sides[1].active.filter(Boolean).forEach(p=>p.faint()); battle.faintMessages(); battle.win(battle.sides[0]);");
                stage=4;
            }else if(stage==4 && battle.getEnded() && ticks%10==0){
                require(rewardCount()==rewardsBefore+1,"Full victory granted one level-one reward");
                require(lootCount()>lootBefore,"Full victory committed native loot");
                lootBefore=lootCount();
                CobblemonEvents.BATTLE_VICTORY.post(new BattleVictoryEvent(battle,battle.getWinners(),battle.getLosers(),false));
                require(lootCount()==lootBefore&&rewardCount()==rewardsBefore+1,"Repeated victory did not duplicate settlement");
                stage=5;waitingSince=ticks;deadline=ticks+200;
            }else if(stage==5 && ticks-waitingSince>80){
                require(pokemon.getEntity()==null||pokemon.getEntity().isRemoved(),"Won Boss not restored");
                require(lootCount()==lootBefore,"Later ordinary death did not duplicate source loot");
                var source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:ur_ghast")).create(level);
                source.setPos(0,100,0);source.setNoAi(true);source.setNoGravity(true);level.addFreshEntity(source);sourceId=source.getUUID();
                var party=Cobblemon.INSTANCE.getStorage().getParty(player);for(var p:party)p.setCurrentHealth(p.getMaxHealth());
                require(EntityBattleSessions.start(player,party.get(0).getUuid(),source),"Temporary native Mob encounter starts");
                battle=BattleRegistry.getBattleByParticipatingPlayer(player);
                boss=mainEntity();pokemon=boss.getPokemon();
                initialHealth=pokemon.getCurrentHealth();stage=6;deadline=ticks+400;
            }else if(stage==6 && activeCount()==3){
                faintMain();stage=7;waitingSince=-1;deadline=ticks+700;
            }else if(stage==7 && pokemon.isFainted() && waitingSince<0){
                waitingSince=ticks;
            }else if(stage==7 && waitingSince>=0 && ticks-waitingSince>150){
                require(pokemon.isFainted(),"Temporary native Boss fainted");
                battle.stop();stage=8;deadline=ticks+140;
            }else if(stage==8 && ticks%10==0){
                require(level.getEntity(sourceId) instanceof Mob mob&&mob.isAlive(),"Native source UUID restored alive after abort");
                require(lootCount()==lootBefore&&rewardCount()==rewardsBefore+1,"Temporary abort did not settle loot or reward");
                var source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id("twilightforest:ur_ghast")).create(level);
                source.setPos(5,100,0);source.setNoAi(true);source.setNoGravity(true);level.addFreshEntity(source);sourceId=source.getUUID();
                var party=Cobblemon.INSTANCE.getStorage().getParty(player);for(var p:party)p.setCurrentHealth(p.getMaxHealth());
                require(EntityBattleSessions.start(player,party.get(0).getUuid(),source),"Logout fixture native encounter starts");
                battle=BattleRegistry.getBattleByParticipatingPlayer(player);
                boss=mainEntity();pokemon=boss.getPokemon();
                stage=9;deadline=ticks+700;
            }else if(stage==9 && activeCount()==3){
                faintMain();stage=10;waitingSince=-1;
            }else if(stage==10 && pokemon.isFainted() && waitingSince<0){
                waitingSince=ticks;
            }else if(stage==10 && waitingSince>=0 && ticks-waitingSince>150){
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
                require(level.getEntity(sourceId) instanceof Mob mob&&mob.isAlive(),"Immediate logout restores native Boss alive before next tick");
                require(lootCount()==lootBefore&&rewardCount()==rewardsBefore+1,"Logout did not settle loot or reward");
                require(!EntityBossEncounters.awaitingSettlement(pokemon),"Logout cleared encounter recovery marker");
                LOG.info("BOSS_ENCOUNTER PASS: main-only KO plus actual flee, retained recovery body, full-victory once-only loot/reward, no later duplicates, native temporary rollback and immediate logout");
                ready=false;e.getServer().halt(false);
            }
        }catch(Throwable failure){LOG.error("BOSS_ENCOUNTER FAIL",failure);ready=false;if(battle!=null){battle.saveBattleLog();if(!battle.getEnded())battle.stop();}e.getServer().halt(false);}
    }
    private static void begin(){
        var party=Cobblemon.INSTANCE.getStorage().getParty(player);for(var p:party)p.setCurrentHealth(p.getMaxHealth());
        var result=BattleBuilder.INSTANCE.pve(player,boss,party.get(0).getUuid());
        require(result instanceof SuccessfulBattleStart,"Wild Boss encounter starts");battle=((SuccessfulBattleStart)result).getBattle();
    }
    private static List<com.cobblemon.mod.common.battles.pokemon.BattlePokemon> opponents(){return battle.getSide2().getActors()[0].getPokemonList();}
    private static PokemonEntity mainEntity(){return opponents().stream().filter(p->p.getOriginalPokemon().getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_ur_ghast"))).findFirst().orElseThrow().getEntity();}
    private static long activeCount(){return battle.getSide2().getActivePokemon().stream().filter(p->p.hasPokemon()).count();}
    private static void faintMain(){send("const main = battle.getAllActive().find(p=>p.baseSpecies.id==='entitybattletwilightforesturghast'); if (!main) throw new Error('Missing primary Boss'); main.faint(); battle.faintMessages();");}
    private static void send(String script){ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{">eval "+script});}
    private static int lootCount(){return player.serverLevel().getEntitiesOfClass(ItemEntity.class,AREA).stream().mapToInt(item->item.getItem().getCount()).sum();}
    private static int rewardCount(){int count=0;for(var p:Cobblemon.INSTANCE.getStorage().getParty(player))if(p.getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_ur_ghast")))count++;for(var p:Cobblemon.INSTANCE.getStorage().getPC(player))if(p.getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_ur_ghast")))count++;return count;}
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);LOG.info("BOSS_ENCOUNTER CHECK: {}",label);}
}
