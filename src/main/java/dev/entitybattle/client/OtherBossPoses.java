package dev.entitybattle.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;

/** Native public visual state and animation clocks, without ticking a source entity. */
final class OtherBossPoses {
    private static final Map<Class<?>,Map<String,Optional<Method>>> METHODS=new HashMap<>();
    private OtherBossPoses(){}
    static void clear(){METHODS.clear();}
    static void apply(PokemonEntity pokemon,Mob visual,String move,int phase,long until){
        String source=BuiltInRegistries.ENTITY_TYPE.getKey(visual.getType()).toString();
        if(source.equals("kaleidoscope_twilight:umbral_sunflower")){
            int remaining=move.isEmpty()?0:(int)Math.clamp(until-pokemon.level().getGameTime(),0,30);
            var data=visual.getEntityData();
            // Rescale each native animation to the shared 30-tick move cue.
            data.set(dev.entitybattle.client.mixin.SunflowerAnimationAccess.entitybattle$swordTime(),move.equals("leafblade")?remaining*100/30:0);
            data.set(dev.entitybattle.client.mixin.SunflowerAnimationAccess.entitybattle$swordAuraTime(),move.equals("sacredsword")?remaining*62/30:0);
            data.set(dev.entitybattle.client.mixin.SunflowerAnimationAccess.entitybattle$spikeTime(),move.equals("bulldoze")?remaining*70/30:0);
            data.set(dev.entitybattle.client.mixin.SunflowerAnimationAccess.entitybattle$shieldTime(),move.equals("protect")?remaining*35/30:0);
        }
        if(source.equals("aether:slider"))flag(visual,"setAwake",true);
        if(source.equals("aether:sun_spirit"))flag(visual,"setFrozen",phase==3);
        if(visual instanceof WitherBoss wither){
            wither.setInvulnerableTicks(0);
            wither.setHealth(phase==2?wither.getMaxHealth()*.49F:wither.getMaxHealth());
        }
        if(visual instanceof Warden warden){
            int start=(int)(visual.tickCount-(pokemon.level().getGameTime()-until+30));
            boolean sound=move.equals("boomburst")||move.equals("throatchop");
            if(sound){if(!warden.sonicBoomAnimationState.isStarted())warden.sonicBoomAnimationState.start(start);}
            else warden.sonicBoomAnimationState.stop();
            boolean punch=move.equals("hammerarm")||move.equals("stompingtantrum");
            if(punch){if(!warden.attackAnimationState.isStarted())warden.attackAnimationState.start(start);}
            else warden.attackAnimationState.stop();
        }
    }
    static Vec3 offset(String source,String move,float progress){
        if(source.equals("aether:slider")&&Set.of("heavyslam","bodypress","bulldoze").contains(move))return new Vec3(0,move.equals("heavyslam")?.35*progress:0,.7*progress);
        if(source.equals("aether:valkyrie_queen")&&Set.of("sacredsword","aerialace").contains(move))return new Vec3(0,.25*progress,.6*progress);
        return Vec3.ZERO;
    }
    private static void flag(Mob visual,String name,boolean value){
        var method=METHODS.computeIfAbsent(visual.getClass(),key->new HashMap<>()).computeIfAbsent(name,key->{
            try{return Optional.of(visual.getClass().getMethod(key,boolean.class));}catch(NoSuchMethodException ignored){return Optional.empty();}
        });
        method.ifPresent(m->{try{m.invoke(visual,value);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Native visual flag "+name,failure);}});
    }
}
