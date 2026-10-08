package dev.entitybattle.compat;

import dev.entitybattle.api.EntityPokemonOrigin;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

/** Vanilla presentation fields only; never restores AI, health, owners or inventory loot. */
public final class VanillaMobAppearance {
    private static final List<String> KEYS = List.of("Age", "IsBaby", "Variant", "variant",
            "Color", "Sheared", "Type", "CatType", "CollarColor", "Saddle", "ChestedHorse",
            "ArmorItems", "HandItems", "body_armor_item", "ArmorItem", "DecorItem",
            "Size", "VillagerData", "Pumpkin", "PuffState", "MainHand", "HasLeftHorn", "HasRightHorn");

    private VanillaMobAppearance() {}

    public static void register() {
        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(entry -> {
            if (!"minecraft".equals(entry.getKey().location().getNamespace())) return;
            EntityPokemonOrigin.registerAppearance(entry.getValue(), new EntityPokemonOrigin.AppearanceAdapter() {
                @Override
                public CompoundTag save(Mob mob) {
                    CompoundTag saved = mob.saveWithoutId(new CompoundTag());
                    CompoundTag appearance = new CompoundTag();
                    for (String key : KEYS) {
                        if (saved.contains(key)) appearance.put(key, saved.get(key).copy());
                    }
                    return appearance;
                }

                @Override
                public void load(Mob mob, CompoundTag appearance) {
                    CompoundTag saved = mob.saveWithoutId(new CompoundTag());
                    for (String key : KEYS) {
                        if (appearance.contains(key)) saved.put(key, appearance.get(key).copy());
                    }
                    mob.load(saved);
                }
            });
        });
    }
}
