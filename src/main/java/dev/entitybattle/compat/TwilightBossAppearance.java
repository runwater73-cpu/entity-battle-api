package dev.entitybattle.compat;

import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** A rider's weapon/number are presentation data, distinct from the full server recovery snapshot. */
public final class TwilightBossAppearance {
    private TwilightBossAppearance() {}
    public static void register() {
        register("twilightforest:knight_phantom", List.of("HandItems", "ArmorItems", "MyNumber"));
        register("twilightforest:quest_ram", List.of("ColorFlags", "Rewarded"));
    }
    private static void register(String source, List<String> keys) {
        var id = ResourceLocation.parse(source);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return;
        EntityPokemonOrigin.registerAppearance(BuiltInRegistries.ENTITY_TYPE.get(id), new EntityPokemonOrigin.AppearanceAdapter() {
            @Override public CompoundTag save(Mob mob) {
                var saved=mob.saveWithoutId(new CompoundTag()); var visual=new CompoundTag();
                for (String key:keys) if(saved.contains(key)) visual.put(key,saved.get(key).copy());
                return visual;
            }
            @Override public void load(Mob mob, CompoundTag visual) {
                var saved=mob.saveWithoutId(new CompoundTag());
                for(String key:keys) if(visual.contains(key)) saved.put(key,visual.get(key).copy());
                mob.load(saved);
            }
        });
    }
}
