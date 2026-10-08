package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.entitybattle.api.EntityPokemonOrigin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** Local source cues supplement Cobblemon's target effects, without world damage. */
public final class TwilightBossMoveEffects {
    public static final String RAGE="entitybattle:visual_lament_rage";
    private TwilightBossMoveEffects() {}
    public static void register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
            boolean hasNative=false;
            for(var actor:event.getBattle().getActors()) for(var p:actor.getPokemonList())
                if(EntityPokemonOrigin.entityId(p.getOriginalPokemon()).filter(id->id.getNamespace().equals("twilightforest")).isPresent())hasNative=true;
            if(!hasNative)return;
            event.getBattle().getOnEndHandlers().add(ended->{
                for(var actor:ended.getActors())for(var p:actor.getPokemonList())if(p.getEntity()!=null
                        && EntityPokemonOrigin.entityId(p.getOriginalPokemon()).filter(id->id.getNamespace().equals("twilightforest")).isPresent()) {
                    p.getEntity().getPersistentData().remove(RAGE);
                    PacketDistributor.sendToPlayersTrackingEntity(p.getEntity(),new EntityBattleNetwork.NativePose(p.getEntity().getUUID(),"",0));
                }
                return kotlin.Unit.INSTANCE;
            });
        });
    }
    public static void onRage(PokemonEntity entity) {
        entity.getPersistentData().putBoolean(RAGE,true);
        PacketDistributor.sendToPlayersTrackingEntity(entity,new EntityBattleNetwork.NativePose(entity.getUUID(),"",1));
    }
    public static void onMove(PokemonEntity entity,String move) {
        var source=EntityPokemonOrigin.entityId(entity.getPokemon()).orElse(null);
        if(source==null||!source.getNamespace().equals("twilightforest")||source.getPath().equals("lich")
                ||!(entity.level() instanceof ServerLevel level))return;
        var particle=switch(move) {
            case "fireblast","heatwave","flamethrower","ember","incinerate","flameburst","willowisp"->ParticleTypes.FLAME;
            case "icebeam","blizzard","icehammer","icepunch","icywind","aurorabeam","avalanche","iceshard"->ParticleTypes.SNOWFLAKE;
            case "shadowball","shadowclaw","hex"->ParticleTypes.SOUL;
            case "hypervoice"->ParticleTypes.NOTE;
            case "auroraveil","protect"->ParticleTypes.ENCHANT;
            case "woodhammer","bugbite","lunge","rockslide","highhorsepower","stompingtantrum","stormthrow","bodyslam","ironhead","hammerarm","sacredsword"->ParticleTypes.CRIT;
            default->null;
        };
        if(particle!=null)level.sendParticles(particle,entity.getX(),entity.getY()+entity.getBbHeight()*0.65,entity.getZ(),18,0.55,0.65,0.55,0.04);
    }
}
