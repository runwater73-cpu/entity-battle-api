package dev.entitybattle.check;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Load the actual GUI classes to validate required client injection targets; never shipped. */
@EventBusSubscriber(modid = "entitybattle", value = Dist.CLIENT)
public final class GuiSmoke {
    private static boolean finished;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (finished || !(client.screen instanceof TitleScreen)) return;
        finished = true;
        try {
            for (String type : new String[]{"api.gui.GuiUtilsKt", "client.gui.PokemonGuiUtilsKt",
                    "client.gui.interact.partyselect.PartySlotButton",
                    "client.gui.pasture.PasturePokemonScrollList$PastureSlot",
                    "client.gui.trade.PartySlot", "client.gui.trade.ModelWidget",
                    "client.gui.summary.widgets.ModelWidget", "client.gui.pc.StorageSlot",
                    "client.gui.battle.BattleOverlay", "client.gui.PartyOverlay"}) {
                Class.forName("com.cobblemon.mod.common." + type);
            }
            checkBattleMessages(client);
            com.mojang.logging.LogUtils.getLogger().info("GUI_SMOKE PASS: both shared render mixins and medicine/pasture/trade/storage/summary/HUD classes loaded");
        } catch (Throwable failure) {
            com.mojang.logging.LogUtils.getLogger().error("GUI_SMOKE FAIL", failure);
        }
        client.stop();
    }

    private static void checkBattleMessages(Minecraft client) throws Exception {
        Class.forName("com.cobblemon.mod.common.api.battles.model.PokemonBattle");
        net.minecraft.locale.Language.inject(net.minecraft.client.resources.language.ClientLanguage.loadFrom(
                client.getResourceManager(), java.util.List.of("en_us", "zh_cn"), false));
        var expected = java.util.Map.of("shedskin", "蜕皮", "intimidate", "威吓", "multiscale", "多重鳞片",
                "levitate", "飘浮", "snowwarning", "降雪", "entitybattletwilightdominion", "暮光支配",
                "entitybattlesoulcovenant", "魂契分担", "entitybattlelamentrage", "哀鸣暴怒");
        for (var entry : expected.entrySet()) {
            String id = entry.getKey();
            com.cobblemon.mod.common.api.abilities.Abilities.register(
                    new com.cobblemon.mod.common.api.abilities.AbilityTemplate(id,
                            new com.cobblemon.mod.common.api.abilities.AbilityTemplate().getBuilder(),
                            "cobblemon.ability." + id, "cobblemon.ability." + id + ".desc"));
            var input = net.minecraft.network.chat.Component.translatable("cobblemon.battle.ability.generic",
                    net.minecraft.network.chat.Component.literal("测试Boss"), id).withStyle(net.minecraft.ChatFormatting.YELLOW);
            var result = dev.entitybattle.battle.EntityBattleMessages.translateEffects(input);
            require(result.getString().contains(entry.getValue()), "Chinese ability announcement: " + id);
            require(result.getStyle().equals(input.getStyle()), "Announcement style retained");
            require(!net.minecraft.network.chat.Component.translatable("cobblemon.ability." + id + ".desc")
                    .getString().startsWith("cobblemon."), "Ability description present: " + id);
        }
        var names = java.util.Map.<String, net.minecraft.network.chat.Component>of(
                "entitybattle:TwilightforestKnightPhantom", net.minecraft.network.chat.Component.literal("幻影骑士"),
                "Charizard", net.minecraft.network.chat.Component.translatable("cobblemon.species.charizard.name"));
        String raw = "|-message|entitybattle:TwilightforestKnightPhantom（幻影骑士奖杯）反弹了 30 点伤害！";
        var notice = dev.entitybattle.battle.EntityBattleMessages.translateNotice(raw, names);
        require(notice.getString().equals("幻影骑士（幻影骑士奖杯）反弹了 30 点伤害！"), "Exact trophy notice");
        var notice2 = dev.entitybattle.battle.EntityBattleMessages.translateNotice(
                "|-message|entitybattle:TwilightforestKnightPhantom令 Charizard 速度下降！", names);
        require(notice2.getString().equals("幻影骑士令 喷火龙 速度下降！"), "Both participant names translated");
        require(dev.entitybattle.battle.EntityBattleMessages.translateNotice("|-unknown|debug", names)
                .getString().equals("|-unknown|debug"), "Unrelated unknown instruction retained");
        require(net.minecraft.network.chat.Component.translatable("cobblemon.battle.damage.knightphantomtrophy",
                net.minecraft.network.chat.Component.literal("喷火龙")).getString()
                .equals("喷火龙受到幻影骑士奖杯的反弹伤害！"), "Reflected damage key");
        for (String trophy : new String[]{"Naga Trophy", "Twilight Lich Trophy", "Minoshroom Trophy",
                "Hydra Trophy", "Knight Phantom Trophy", "Ur-Ghast Trophy", "Alpha Yeti Trophy",
                "Snow Queen Trophy", "Questing Ram Trophy"}) {
            var message = net.minecraft.network.chat.Component.translatable("cobblemon.battle.heal.item",
                    net.minecraft.network.chat.Component.literal("测试Boss"), trophy);
            var translated = dev.entitybattle.battle.EntityBattleMessages.translateEffects(message).getString();
            require(!translated.contains(trophy) && !translated.contains("block.twilightforest."), "Chinese held item: " + trophy);
        }
        com.mojang.logging.LogUtils.getLogger().info("BATTLE_MESSAGES PASS: eight boss/escort ability names and descriptions, nine trophies, raw notices, both participant names and reflected damage");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
