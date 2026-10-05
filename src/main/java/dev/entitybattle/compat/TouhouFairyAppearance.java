package dev.entitybattle.compat;

import dev.entitybattle.api.EntityPokemonOrigin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;

/** Keeps fairy color and baby size without requiring the maid mod at compile time. */
public final class TouhouFairyAppearance {
    private static final ResourceLocation FAIRY = ResourceLocation.fromNamespaceAndPath(
            "touhou_little_maid", "fairy");

    private TouhouFairyAppearance() {}

    public static void register() {
        if (!ModList.get().isLoaded("touhou_little_maid")
                || !BuiltInRegistries.ENTITY_TYPE.containsKey(FAIRY)) return;
        EntityPokemonOrigin.registerAppearance(BuiltInRegistries.ENTITY_TYPE.get(FAIRY),
                new EntityPokemonOrigin.AppearanceAdapter() {
                    @Override
                    public CompoundTag save(Mob mob) {
                        CompoundTag tag = new CompoundTag();
                        tag.putInt("FairyType", ((Number) invoke(mob, "getFairyTypeOrdinal")).intValue());
                        tag.putBoolean("IsBaby", (Boolean) invoke(mob, "isBaby"));
                        return tag;
                    }

                    @Override
                    public void load(Mob mob, CompoundTag snapshot) {
                        if (snapshot.contains("FairyType", net.minecraft.nbt.Tag.TAG_INT)) {
                            int color = snapshot.getInt("FairyType");
                            if (color >= 0 && color < 18) {
                                invoke(mob, "setFairyTypeOrdinal", int.class, color);
                            }
                        }
                        if (snapshot.contains("IsBaby", net.minecraft.nbt.Tag.TAG_BYTE)) {
                            invoke(mob, "setBaby", boolean.class, snapshot.getBoolean("IsBaby"));
                        }
                    }
                });
    }

    private static Object invoke(Mob mob, String method) {
        try {
            return mob.getClass().getMethod(method).invoke(mob);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read Touhou fairy appearance", exception);
        }
    }

    private static void invoke(Mob mob, String method, Class<?> argumentType, Object value) {
        try {
            mob.getClass().getMethod(method, argumentType).invoke(mob, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot restore Touhou fairy appearance", exception);
        }
    }
}
