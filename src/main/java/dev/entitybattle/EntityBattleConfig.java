package dev.entitybattle;

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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private EntityBattleConfig() {}
}
