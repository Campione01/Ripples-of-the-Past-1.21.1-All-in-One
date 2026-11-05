package rotp.addons.madeinheaven;

import rotp.addons.madeinheaven.state.TimeAccelerationMode;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MadeInHeavenServerConfig {
    public static final TimeAccelerationMode DEFAULT_MODE =
            TimeAccelerationMode.WORLD_ACCELERATES;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<TimeAccelerationMode>
            ACCELERATION_MODE;

    static {
        ModConfigSpec.Builder builder =
                new ModConfigSpec.Builder();
        builder.translation(
                        "rotp_mih.config.server.timeAcceleration")
                .comment("Made in Heaven's time acceleration.")
                .push("timeAcceleration");
        ACCELERATION_MODE = builder
                .translation(
                        "rotp_mih.config.server.accelerationMode")
                .comment(
                        "WORLD_ACCELERATES: living beings keep their own "
                                + "time; non-living things, random "
                                + "ticks, fluids, block entities and "
                                + "the weather run m times faster; the "
                                + "owner's attacks, mining, item use, "
                                + "Stand actions and cooldowns run m "
                                + "times faster.",
                        "OTHERS_FALL_BEHIND: the owner and non-living "
                                + "things keep normal speed; every other "
                                + "living being runs m times slower.",
                        "An acceleration keeps the mode it started "
                                + "with.")
                .defineEnum("accelerationMode", DEFAULT_MODE);
        builder.pop();
        SPEC = builder.build();
    }

    private MadeInHeavenServerConfig() {}

    public static TimeAccelerationMode accelerationMode() {
        return SPEC.isLoaded() ? ACCELERATION_MODE.get() : DEFAULT_MODE;
    }
}
