package dev.entitybattle.item;

import dev.entitybattle.EntityBattleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class EntityBattleItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, EntityBattleMod.ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EntityBattleMod.ID);

    public static final Supplier<Item> CONVERTER = ITEMS.register("converter",
            () -> new ConversionItem(new Item.Properties().stacksTo(64)));
    public static final Supplier<CreativeModeTab> TAB = TABS.register("entitybattle", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.entitybattle"))
                    .icon(() -> CONVERTER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(CONVERTER.get()))
                    .build());

    private EntityBattleItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
