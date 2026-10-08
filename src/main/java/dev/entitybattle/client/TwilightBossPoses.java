package dev.entitybattle.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;

/** Sets visual flags only on render-only mobs; never ticks native AI or spawns attacks. */
public final class TwilightBossPoses {
    private record Pose(String move, long until, int phase) {}
    private static final Map<UUID,Pose> POSES=new HashMap<>();
    private static final Map<Class<?>,Map<String,Optional<Method>>> METHODS=new HashMap<>();
    private TwilightBossPoses() {}
    public static void receive(UUID uuid,String move,int phase) {
        var level=Minecraft.getInstance().level;if(level==null)return;
        var previous=POSES.get(uuid);
        POSES.put(uuid,new Pose(move,level.getGameTime()+30,phase<0&&previous!=null?previous.phase:Math.max(0,phase)));
    }
    public static void clear(){POSES.clear();METHODS.clear();OtherBossPoses.clear();}
    public static void remove(UUID uuid){POSES.remove(uuid);}
    /** Native knights hide their unarmoured body outside charging; portraits need a visible pose. */
    public static void preparePortrait(Mob visual) {
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(visual.getType());
        if(id.getNamespace().equals("twilightforest")&&id.getPath().equals("knight_phantom"))
            flag(visual,"setChargingAtPlayer",true);
    }
    static String moveFor(UUID uuid) {
        var pose=POSES.get(uuid); var level=Minecraft.getInstance().level;
        return pose!=null && level!=null && level.getGameTime()<pose.until ? pose.move : "";
    }
    public static net.minecraft.world.phys.Vec3 offset(PokemonEntity pokemon, float partialTick) {
        var pose=POSES.get(pokemon.getUUID());
        var source=dev.entitybattle.api.EntityPokemonOrigin.entityId(pokemon.getPokemon()).orElse(null);
        if(pose==null || source==null) return net.minecraft.world.phys.Vec3.ZERO;
        float elapsed=(float)(pokemon.level().getGameTime()-pose.until+30)+partialTick;
        if(elapsed<0 || elapsed>=30)return net.minecraft.world.phys.Vec3.ZERO;
        float progress=(float)Math.sin(Math.PI*elapsed/30);
        if(!source.getNamespace().equals("twilightforest"))return OtherBossPoses.offset(source.toString(),pose.move,progress)
                .yRot((float)Math.toRadians(-pokemon.getYRot()));
        return switch(source.getPath()) {
            case "naga" -> Set.of("bodyslam","crunch","breakingswipe").contains(pose.move)
                    ? new net.minecraft.world.phys.Vec3(0,0,1.2*progress).yRot((float)Math.toRadians(-pokemon.getYRot())) : net.minecraft.world.phys.Vec3.ZERO;
            case "alpha_yeti" -> Set.of("icehammer","stompingtantrum","stormthrow").contains(pose.move)
                    ? new net.minecraft.world.phys.Vec3(0,0.35*progress,0) : net.minecraft.world.phys.Vec3.ZERO;
            default -> net.minecraft.world.phys.Vec3.ZERO;
        };
    }
    public static void apply(PokemonEntity entity,Mob visual) {
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(visual.getType());
        var pose=POSES.get(entity.getUUID());
        String move=pose!=null&&entity.level().getGameTime()<pose.until?pose.move:"";
        if(!id.getNamespace().equals("twilightforest")){
            OtherBossPoses.apply(entity,visual,move,pose==null?0:pose.phase,pose==null?0:pose.until);return;
        }
        switch(id.getPath()) {
            case "naga" -> flag(visual,"setCharging",Set.of("bodyslam","crunch","breakingswipe").contains(move));
            case "minoshroom" -> {
                flag(visual,"setCharging",!move.isEmpty());
                flag(visual,"setGroundAttackCharge",move.equals("rockslide"));
            }
            case "alpha_yeti" -> flag(visual,"setRampaging",Set.of("icehammer","stompingtantrum","stormthrow").contains(move));
            case "ur_ghast" -> {
                flag(visual,"setCharging",Set.of("fireblast","heatwave","shadowball").contains(move));
                flag(visual,"setInTantrum",pose!=null&&pose.phase==1||move.equals("hypervoice"));
            }
            case "knight_phantom" -> flag(visual,"setChargingAtPlayer",!move.isEmpty()&&!move.equals("protect"));
            case "snow_queen" -> {
                boolean beam=Set.of("icebeam","blizzard").contains(move);
                phase(visual,beam?"BEAM":"SUMMON"); flag(visual,"setBreathing",beam);
            }
            default -> {}
        }
    }
    private static Optional<Method> method(Class<?> type,String name) {
        return METHODS.computeIfAbsent(type,k->new HashMap<>()).computeIfAbsent(name,key->{
            for(Class<?> current=type;current!=null;current=current.getSuperclass())
                for(Method m:current.getDeclaredMethods()) if(m.getName().equals(key)&&m.getParameterCount()==1) {
                    m.setAccessible(true);return Optional.of(m);
                }
            return Optional.empty();
        });
    }
    private static void flag(Mob mob,String name,boolean value) {
        method(mob.getClass(),name).ifPresent(m->{try{m.invoke(mob,value);}catch(ReflectiveOperationException ignored){}});
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void phase(Mob mob,String name) {
        method(mob.getClass(),"setCurrentPhase").ifPresent(m->{
            try {m.invoke(mob,Enum.valueOf((Class)m.getParameterTypes()[0],name));}
            catch(ReflectiveOperationException|IllegalArgumentException ignored){}
        });
    }
}
