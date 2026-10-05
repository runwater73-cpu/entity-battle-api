package dev.entitybattle.client;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders registered native mobs in Cobblemon portrait slots. */
public final class EntityBattlePortraits {
    private record Tile(GuiGraphics graphics, ActiveClientBattlePokemon active) {}

    private static final ThreadLocal<Tile> CURRENT_TILE = new ThreadLocal<>();
    private static final ThreadLocal<Pokemon> CURRENT_PARTY_POKEMON = new ThreadLocal<>();
    private static final ThreadLocal<GuiGraphics> CURRENT_PARTY_GRAPHICS = new ThreadLocal<>();
    private static final ThreadLocal<GuiGraphics> CURRENT_PC_GRAPHICS = new ThreadLocal<>();

    private EntityBattlePortraits() {}

    public static void beginTile(GuiGraphics graphics, ActiveClientBattlePokemon active) {
        CURRENT_TILE.set(new Tile(graphics, active));
    }

    public static void endTile() {
        CURRENT_TILE.remove();
    }

    public static void beginParty(GuiGraphics graphics) {
        CURRENT_PARTY_GRAPHICS.set(graphics);
    }

    public static void beginPartyPokemon(Pokemon pokemon) {
        CURRENT_PARTY_POKEMON.set(pokemon);
    }

    public static void endParty() {
        CURRENT_PARTY_POKEMON.remove();
        CURRENT_PARTY_GRAPHICS.remove();
    }

    public static void beginPcSlot(GuiGraphics graphics) {
        CURRENT_PC_GRAPHICS.set(graphics);
    }

    public static void endPcSlot() {
        CURRENT_PC_GRAPHICS.remove();
    }

    public static boolean drawPcSlot(GuiGraphics graphics, Pokemon pokemon) {
        Mob model = EntityPokemonNativeVisuals.modelFor(pokemon);
        if (model == null) return false;
        float size = Math.max(model.getBbHeight(), model.getBbWidth());
        float modelScale = Math.min(8F, 8F / Math.max(0.5F, size));
        InventoryScreen.renderEntityInInventory(graphics, 0F, 8F, modelScale,
                new Vector3f(0F, model.getBbHeight() / 2F, 0F),
                new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI),
                null, model);
        return true;
    }

    public static boolean drawPcSlot(Pokemon pokemon) {
        GuiGraphics graphics = CURRENT_PC_GRAPHICS.get();
        return graphics != null && drawPcSlot(graphics, pokemon);
    }

    public static boolean drawPcPreview(GuiGraphics graphics, Pokemon pokemon, ModelWidget widget) {
        Mob model = EntityPokemonNativeVisuals.modelFor(pokemon);
        if (model == null) return false;
        float size = Math.max(model.getBbHeight(), model.getBbWidth());
        float modelScale = Math.min(42F, 44F / Math.max(0.5F, size));
        graphics.enableScissor(widget.getX(), widget.getY(),
                widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight());
        try {
            InventoryScreen.renderEntityInInventory(graphics,
                    widget.getX() + widget.getWidth() / 2F,
                    widget.getY() + widget.getHeight() - 7F, modelScale,
                    new Vector3f(0F, model.getBbHeight() / 2F, 0F),
                    new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI),
                    null, model);
        } finally {
            graphics.disableScissor();
        }
        return true;
    }

    public static void draw(ResourceLocation species, PoseStack pose, float x, float y,
                            boolean flipped, PosableState state, float scale, float offsetX,
                            float offsetY, float rotationX, float rotationY, float rotationZ,
                            boolean shiny, float red, float green, float blue, float alpha,
                            int mask, Object marker) {
        if (drawNative()) return;
        if (drawNativeParty()) return;
        GuiUtilsKt.drawPosablePortrait(species, pose,
                (mask & 4) != 0 ? 13F : x, (mask & 8) != 0 ? 1F : y,
                (mask & 16) != 0 ? false : flipped, state, scale,
                (mask & 128) != 0 ? 0F : offsetX,
                (mask & 256) != 0 ? 0F : offsetY,
                (mask & 512) != 0 ? 0F : rotationX,
                (mask & 1024) != 0 ? 0F : rotationY,
                (mask & 2048) != 0 ? 0F : rotationZ,
                (mask & 4096) != 0 || shiny,
                (mask & 8192) != 0 ? 1F : red,
                (mask & 16384) != 0 ? 1F : green,
                (mask & 32768) != 0 ? 1F : blue,
                (mask & 65536) != 0 ? 1F : alpha);
    }

    private static boolean drawNativeParty() {
        Pokemon pokemon = CURRENT_PARTY_POKEMON.get();
        GuiGraphics graphics = CURRENT_PARTY_GRAPHICS.get();
        if (pokemon == null || graphics == null) return false;
        Mob model = EntityPokemonNativeVisuals.modelFor(pokemon);
        if (model == null) return false;
        float modelScale = Math.min(16F, 18F / Math.max(model.getBbHeight(), model.getBbWidth()));
        InventoryScreen.renderEntityInInventory(graphics, 0F, 18F, modelScale,
                new Vector3f(0F, model.getBbHeight() / 2F, 0F),
                new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI),
                null, model);
        return true;
    }

    private static boolean drawNative() {
        Tile tile = CURRENT_TILE.get();
        Minecraft client = Minecraft.getInstance();
        if (tile == null || client.level == null || tile.active().getBattlePokemon() == null) return false;

        UUID pokemonId = tile.active().getBattlePokemon().getUuid();
        Mob model = null;
        for (Entity candidate : client.level.entitiesForRendering()) {
            if (candidate instanceof PokemonEntity pokemon
                    && pokemon.getPokemon().getUuid().equals(pokemonId)) {
                model = EntityPokemonNativeVisuals.modelFor(pokemon);
                break;
            }
        }
        if (model == null) return false;

        // The parent pose is already at the portrait center and inside its scissor.
        float modelScale = Math.min(18F, 24F / Math.max(0.5F, model.getBbHeight()));
        InventoryScreen.renderEntityInInventory(tile.graphics(), 0F, 19F,
                modelScale, new Vector3f(0F, model.getBbHeight() / 2F, 0F),
                new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI),
                null, model);
        return true;
    }
}
