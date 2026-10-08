package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** Presentation cues only: native source AI never performs a second attack. */
public final class OtherBossMoveEffects {
    public static final String PHASE="entitybattle:visual_source_phase";
    private static final Set<String> SOURCES=Set.of("aether:slider","aether:valkyrie_queen","aether:sun_spirit","aether:fire_minion","minecraft:wither","minecraft:wither_skeleton","minecraft:warden","deep_aether:eots_controller");
    private OtherBossMoveEffects(){}
    private static boolean relevant(PokemonEntity e){return EntityPokemonOrigin.entityId(e.getPokemon()).map(id->SOURCES.contains(id.toString())).orElse(false);}
    public static void register(){
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event->{
            event.getBattle().getOnEndHandlers().add(battle->{
                for(var actor:battle.getActors())for(var p:actor.getPokemonList())if(p.getEntity()!=null&&relevant(p.getEntity())){
                    var e=p.getEntity();e.getPersistentData().remove(PHASE);
                    PacketDistributor.sendToPlayersTrackingEntity(e,new EntityBattleNetwork.NativePose(e.getUUID(),"",0));
                }
                return kotlin.Unit.INSTANCE;
            });
        });
    }
    public static void onAbility(PokemonEntity e,String ability){
        if(!relevant(e))return;
        int phase=switch(ability){case "entitybattlewitherarmor"->2;case "entitybattlecorecooling"->3;default->0;};
        if(phase==0)return;e.getPersistentData().putInt(PHASE,phase);
        PacketDistributor.sendToPlayersTrackingEntity(e,new EntityBattleNetwork.NativePose(e.getUUID(),"",phase));
    }
    public static void onMove(PokemonEntity e,String move){
        if(!relevant(e)||!(e.level() instanceof ServerLevel level))return;
        var particle=switch(move){
            case "fireblast","heatwave","ember","flamecharge"->ParticleTypes.FLAME;
            case "icywind"->ParticleTypes.SNOWFLAKE;
            case "hurricane","airslash","tailwind"->ParticleTypes.CLOUD;
            case "shadowball","shadowclaw","darkpulse","bite"->ParticleTypes.SOUL;
            case "sludgebomb","poisonjab"->ParticleTypes.WITCH;
            case "roost","protect","wideguard"->ParticleTypes.ENCHANT;
            case "rockslide","heavyslam","bodypress","bulldoze","sacredsword","aerialace","quickattack","hammerarm","stompingtantrum"->ParticleTypes.CRIT;
            default->null;
        };
        if(particle!=null)level.sendParticles(particle,e.getX(),e.getY()+e.getBbHeight()*.65,e.getZ(),18,.55,.65,.55,.04);
        if(move.equals("boomburst")||move.equals("throatchop")){
            var direction=e.getLookAngle();
            for(int i=1;i<=6;i++)level.sendParticles(ParticleTypes.SONIC_BOOM,e.getX()+direction.x*i,e.getY()+e.getBbHeight()*.65,e.getZ()+direction.z*i,1,0,0,0,0);
        }
    }
}
