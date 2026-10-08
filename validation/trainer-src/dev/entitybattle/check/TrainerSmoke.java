package dev.entitybattle.check;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import dev.entitybattle.api.EntityBattleProfiles;
import dev.entitybattle.api.EntityPokemonData;
import dev.entitybattle.compat.TwilightLordTrainer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native template placement, trainer roster, actual first turn/switch and save restoration. */
@EventBusSubscriber(modid="entitybattle")
public final class TrainerSmoke {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static ServerLevel level; private static ServerPlayer player; private static Interaction marker;
    private static NPCEntity npc; private static PokemonBattle battle;
    private static com.cobblemon.mod.common.battles.pokemon.BattlePokemon leadPokemon;
    private static boolean ready; private static int ticks,stage,deadline,initialTurn; private static UUID lead;
    private static final String[] TEAM={"naga","knight_phantom","lich","ur_ghast","hydra","snow_queen"};
    private static final String[] ITEMS={"dragon_fang","metal_coat","wise_glasses","charcoal_stick","leftovers","never_melt_ice"};
    private static AABB area;
    @SubscribeEvent public static void start(ServerStartedEvent event){
        level=event.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,id("twilightforest:twilight_forest")));
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        ready=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!ready)return;
        try{
            ticks++;
            if(ticks==20){
                area=new AABB(-10,95,-10,35,140,35);
                for(var e:level.getEntitiesOfClass(Entity.class,area))e.discard();
                var template=level.getStructureManager().getOrCreate(id("twilightforest:final_castle/temp/gazebo"));
                require(template.getSize().getX()>0,"Installed final-castle gazebo template exists");
                require(template.placeInWorld(level,new BlockPos(0,100,0),new BlockPos(0,100,0),new StructurePlaceSettings().setIgnoreEntities(false),level.random,2),"Native template placement succeeded");
                checkProgression();stage=0;deadline=ticks+200;
            }
            if(ticks>20 && marker==null){
                marker=level.getEntitiesOfClass(Interaction.class,area,e->e.getTags().contains(TwilightLordTrainer.CASTLE_MARKER)).stream().findFirst().orElse(null);
                if(marker==null){if(ticks>90)throw new AssertionError("Native template did not load marker entities");return;}
                LOG.info("TRAINER_SMOKE marker={} pos={}",marker.getTags(),marker.position());
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.setBlock(marker.blockPosition().offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
            }
            if(marker==null)return;
            if(ticks>deadline)throw new AssertionError("Trainer timeout stage="+stage);
            if(stage==0 && ticks>70){
                npc=level.getEntitiesOfClass(NPCEntity.class,area,n->n.getNpc().getId().equals(TwilightLordTrainer.CLASS)).stream().findFirst().orElse(null);
                if(npc==null){if(ticks%40==0)LOG.info("TRAINER_SMOKE waiting NPC class={} markerLoaded={} time={}",NPCClasses.getByIdentifier(TwilightLordTrainer.CLASS),level.getEntity(marker.getUUID()),level.getGameTime());return;}
                require(level.getEntitiesOfClass(NPCEntity.class,area).size()==1,"Native castle marker automatically creates one trainer");
                require(TwilightLordTrainer.ensureTrainer(marker)==npc,"Repeated marker handling does not duplicate trainer");
                checkTeam();
                var tag=npc.saveWithoutId(new net.minecraft.nbt.CompoundTag());tag.putString("id","cobblemon:npc");
                var uuid=npc.getUUID();npc.discard();
                npc=(NPCEntity)net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,level,e->e);
                require(level.addFreshEntity(npc)&&uuid.equals(npc.getUUID()),"Trainer native NBT retains UUID and six-Pokemon party");
                require(TwilightLordTrainer.ensureTrainer(marker)==npc,"Saved castle association finds restored NPC");checkTeam();
                player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"TrainerSmoke"));player.setPos(npc.position().add(0,0,3));
                var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
                ((Map<UUID,ServerPlayer>)field.get(event.getServer().getPlayerList())).put(player.getUUID(),player);
                var party=Cobblemon.INSTANCE.getStorage().getParty(player);
                for(int i=0;i<6;i++)party.add(PokemonSpecies.getByIdentifier(id("cobblemon:charizard")).create(100));
                party.get(0).getMoveSet().clear();party.get(0).getMoveSet().add(com.cobblemon.mod.common.api.moves.Moves.getByName("protect").create());
                party.get(0).getMoveSet().add(com.cobblemon.mod.common.api.moves.Moves.getByName("dragonpulse").create());
                party.get(0).setCurrentHealth(party.get(0).getMaxHealth()-1);int health=party.get(0).getCurrentHealth();
                var interaction=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(player,net.minecraft.world.InteractionHand.MAIN_HAND,marker);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(interaction);
                battle=BattleRegistry.getBattleByParticipatingPlayer(player);
                require(interaction.isCanceled() && battle!=null,"Right-click castle marker redirects to official trainer battle");
                require(party.get(0).getCurrentHealth()==health,"Challenge does not heal player's party");
                require(TwilightLordTrainer.challenge(player,npc)==null,"Duplicate concurrent challenge rejected");
                require(battle.getSide2().getActors()[0] instanceof com.cobblemon.mod.common.entity.npc.NPCBattleActor,"Official NPCBattleActor owns six-Pokemon team");
                require(battle.getSide2().getActors()[0].getPokemonList().size()==6,"Six reserve Pokemon, no horde side");
                stage=1;deadline=ticks+600;
            }
            if(battle==null)return;
            if(stage==1 && battle.getTurn()>=2 && battle.getDispatches().isEmpty()){
                require(battle.getSide2().getActivePokemon().size()==1,"Singles sends only one NPC Pokemon at a time");
                leadPokemon=battle.getSide2().getActivePokemon().getFirst().getBattlePokemon();
                lead=leadPokemon.getOriginalPokemon().getUuid();
                initialTurn=battle.getTurn();send("battle.sides[1].active[0].hp=1;");
                stage=2;deadline=ticks+500;
            }else if(stage==2 && leadPokemon.getEffectedPokemon().isFainted()
                    && battle.getSide2().getActivePokemon().stream().anyMatch(p->p.getBattlePokemon()!=null && !p.getBattlePokemon().getOriginalPokemon().getUuid().equals(lead))){
                require(leadPokemon.getFaintedAt()!=null,"Native battle copy fainted before reserve switch");
                require(battle.getSide2().getActivePokemon().size()==1,"Native AI switched to a reserve in singles");
                send("battle.sides[1].pokemon.forEach(p=>{if(p.hp)p.faint();}); battle.faintMessages(); battle.win(battle.sides[0]);");
                stage=3;deadline=ticks+500;
            }else if(stage==3 && battle.getEnded()){
                stage=4;deadline=ticks+300;initialTurn=ticks;
            }else if(stage==4 && ticks-initialTurn>110){
                require(npc.isAlive()&&!npc.isRemoved(),"Trainer remains after losing");
                require(level.getEntitiesOfClass(ItemEntity.class,area).isEmpty(),"Trainer Boss species/items do not produce wild Boss loot");
                for(var p:npc.getParty()) require(p.getEntity()==null||p.getEntity().isRemoved(),"Official NPC recall cleans sent Pokemon");
                require(TwilightLordTrainer.ensureTrainer(marker)==npc,"Trainer remains unique after full victory");
                for(var p:Cobblemon.INSTANCE.getStorage().getParty(player))p.recall();
                var replay=TwilightLordTrainer.challenge(player,npc);require(replay!=null,"Native autoHealParty permits a new challenge after loss");replay.stop();
                LOG.info("TRAINER_SMOKE PASS: actual castle marker, persistent native NPC, six level-50 Bosses/items, real singles turn/reserve switch, no wild settlement, repeat challenge");
                ready=false;event.getServer().halt(false);
            }
            if(!battle.getEnded())for(var actor:battle.getActors())if(actor.getType()==com.cobblemon.mod.common.api.battles.model.actor.ActorType.PLAYER && actor.getRequest()!=null && actor.getMustChoose()
                    && actor.getRequest().getActive()!=null && !actor.getRequest().getActive().isEmpty()){
                com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler.INSTANCE.handle(
                    new com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket(battle.getBattleId(),
                        new ArrayList<>(List.of(new com.cobblemon.mod.common.battles.MoveActionResponse(stage==2?"dragonpulse":"protect",
                            stage==2?battle.getSide2().getActivePokemon().getFirst().getPNX():null,null)))),event.getServer(),player);
            }
            if(ticks%100==0 && !battle.getEnded())for(var actor:battle.getActors())LOG.info("TRAINER_SMOKE state stage={} turn={} actor={} active={} health={} request={} choices={} queue={}",
                stage,battle.getTurn(),actor.getType(),actor.getActivePokemon().stream().map(a->a.getBattlePokemon()==null?"empty":a.getBattlePokemon().getOriginalPokemon().getSpecies().getResourceIdentifier().toString()).toList(),
                actor.getPokemonList().stream().map(p->p.getHealth()).toList(),actor.getRequest(),actor.getResponses(),battle.getDispatches().size());
        }catch(Throwable t){LOG.error("TRAINER_SMOKE FAIL",t);ready=false;event.getServer().halt(false);}
    }
    private static void checkTeam(){
        var party=npc.getParty();require(party!=null && java.util.stream.StreamSupport.stream(party.spliterator(),false).count()==6,"Native party loaded six entries");
        for(int i=0;i<6;i++){var p=party.get(i);require(p.getLevel()==50 && p.getSpecies().getResourceIdentifier().equals(id("entitybattle:twilightforest_"+TEAM[i])) && BuiltInRegistries.ITEM.getKey(p.heldItem().getItem()).equals(id("cobblemon:"+ITEMS[i])) && p.getMoveSet().getMoves().size()==4,"Team slot "+i+" species/level/item/four moves");}
        for(var p:party)require(dev.entitybattle.api.EntityPokemonOrigin.entityId(p).isPresent() && dev.entitybattle.api.EntityPokemonOrigin.sourceUuid(p).isEmpty() && !p.isWild(),"NPC visual identity without world Boss origin");
    }
    private static void checkProgression(){
        int count=0;for(var entity:EntityBattleProfiles.entityIds()) {var row=EntityBattleProfiles.get(entity);if(row.boss()){
            Mob source=(Mob)BuiltInRegistries.ENTITY_TYPE.get(row.entity()).create(level);if(source==null)continue;
            var p=EntityPokemonData.getOrCreate(source,row);require(p!=null&&p.getLevel()>=row.minLevel()&&p.getLevel()<=row.maxLevel()&&p.getMoveSet().getMoves().size()==4,"New Boss level/four moves "+row.entity());count++;
        }}require(count>=10,"Vanilla and installed Twilight Boss progression loaded");
    }
    private static void send(String script){ShowdownService.Companion.getService().send(battle.getBattleId(),new String[]{">eval "+script});}
    private static ResourceLocation id(String s){return ResourceLocation.parse(s);}
    private static void require(boolean c,String message){if(!c)throw new AssertionError(message);LOG.info("TRAINER_SMOKE CHECK {}",message);}
}
