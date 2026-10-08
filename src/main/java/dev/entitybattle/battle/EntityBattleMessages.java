package dev.entitybattle.battle;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Localizes effect names and displays textual Showdown notices without their protocol envelope. */
public final class EntityBattleMessages {
    private EntityBattleMessages() {}
    private static final Map<String, String> TROPHIES = Map.of(
            "nagatrophy", "naga", "twilightlichtrophy", "lich", "minoshroomtrophy", "minoshroom",
            "hydratrophy", "hydra", "knightphantomtrophy", "knight_phantom", "urghasttrophy", "ur_ghast",
            "alphayetitrophy", "alpha_yeti", "snowqueentrophy", "snow_queen",
            "questingramtrophy", "quest_ram");

    public static Component translate(PokemonBattle battle, Component message) {
        if (message.getContents() instanceof PlainTextContents text && noticeBody(text.text()) != null) {
            Map<String, Component> names = new HashMap<>();
            for (var actor : battle.getActors()) for (var member : actor.getPokemonList()) {
                var pokemon = member.getEffectedPokemon();
                var species = pokemon.getSpecies();
                String name = species.getName();
                String namespace = species.getResourceIdentifier().getNamespace();
                names.put(namespace.equals("cobblemon") ? name : namespace + ":" + name,
                        pokemon.getDisplayName(false));
                names.put(pokemon.showdownId(), pokemon.getDisplayName(false));
            }
            return translateNotice(text.text(), names);
        }
        return translateEffects(message);
    }

    public static Component translateEffects(Component message) {
        if (!(message.getContents() instanceof TranslatableContents text)
                || !text.getKey().startsWith("cobblemon.battle.")) return message;
        Object[] args = text.getArgs().clone();
        boolean changed = false;
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof String name)) continue;
            String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (text.getKey().startsWith("cobblemon.battle.ability.") && Abilities.get(id) != null) {
                args[i] = Component.translatable("cobblemon.ability." + id);
                changed = true;
            } else if (TROPHIES.containsKey(id)) {
                args[i] = Component.translatable("block.twilightforest." + TROPHIES.get(id) + "_trophy");
                changed = true;
            }
        }
        if (!changed) return message;
        var translated = Component.translatableWithFallback(text.getKey(), text.getFallback(), args)
                .setStyle(message.getStyle());
        message.getSiblings().forEach(translated::append);
        return translated;
    }

    /** Names remain components so dedicated servers do not resolve the client's language. */
    public static Component translateNotice(String raw, Map<String, Component> names) {
        String body = noticeBody(raw);
        if (body == null) return Component.literal(raw);
        var result = Component.empty().withStyle(ChatFormatting.YELLOW);
        var aliases = names.keySet().stream().filter(name -> !name.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).toList();
        if (aliases.isEmpty()) return result.append(body);
        var matcher = Pattern.compile("(?<![A-Za-z0-9_:])(?:" + String.join("|", aliases)
                + ")(?![A-Za-z0-9_])").matcher(body);
        int offset = 0;
        while (matcher.find()) {
            result.append(body.substring(offset, matcher.start())).append(names.get(matcher.group()).copy());
            offset = matcher.end();
        }
        return result.append(body.substring(offset));
    }

    private static String noticeBody(String raw) {
        if (raw.startsWith("|-message|")) return raw.substring(10);
        if (raw.startsWith("-message|")) return raw.substring(9);
        return null;
    }
}
