package dev.entitybattle.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Exposes NeoForge's built-in editor through the mod list Config button. */
public final class EntityBattleConfigScreen {
    private EntityBattleConfigScreen() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent));
    }
}
