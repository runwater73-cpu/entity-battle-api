package dev.entitybattle;

import dev.entitybattle.api.EntityBattleProfile;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative switch for challenging native mobs with the party key. */
public final class EntityBattleConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_R_CHALLENGE = BUILDER
            .comment("Allow pressing Cobblemon's party-send key while targeting a native mob to start a battle."
                    + " When false, native mobs can still be converted with the converter item.")
            .translation("entitybattle.configuration.enableRChallenge")
            .gameRestart()
            .define("enableRChallenge", false);

    public static final ModConfigSpec.EnumValue<EntityBattleProfile.WorldMode> BOSS_WORLD_MODE = BUILDER
            .comment("World mode for profiles marked boss. POKEMON_ENTITY converts after source eligibility;"
                    + " NATIVE_MOB keeps the source mob and allows manual converter use."
                    + " Native dialogue, story and awakening requirements always apply."
                    + " Existing world entities are not converted when this setting changes.")
            .translation("entitybattle.configuration.bossWorldMode")
            .gameRestart()
            .defineEnum("bossWorldMode", EntityBattleProfile.WorldMode.POKEMON_ENTITY);

    public static final ModConfigSpec.IntValue CONVERSION_SECONDS = BUILDER
            .comment("Seconds to hold the converter on a native mob before it becomes a Pokemon."
                    + " Set to 0 for instant conversion. The target must stay alive and within reach.")
            .translation("entitybattle.configuration.conversionSeconds")
            .defineInRange("conversionSeconds", 5, 0, 60);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EntityBattleConfig() {}
}
