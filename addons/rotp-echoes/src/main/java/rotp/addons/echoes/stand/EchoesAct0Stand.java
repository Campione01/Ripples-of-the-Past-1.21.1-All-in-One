package rotp.addons.echoes.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;

import rotp.addons.echoes.init.EchoesEntities;
import rotp.addons.echoes.init.EchoesSounds;
import net.minecraft.resources.ResourceLocation;

/**
 * Echoes ACT0, the egg (R772 F11, the author's latest 1.16.5 build RotP-EchoesACT-master and his 1.21.1
 * echoes_act_0): the form the arrow gives. It has no stats and no moves; EchoesAct0Awakening hatches it into ACT1.
 */
public final class EchoesAct0Stand {
    private EchoesAct0Stand() {}

    public static EntityStandType create(ResourceLocation id) {
        MovesetBuilder moveset = new MovesetBuilder()
                .makeControlScheme("default")
                .finalizeControlScheme();

        return new EntityStandType(
                new StandStats.Builder()
                        .power(0.0)
                        .speed(0.0)
                        .range(0.0, 0.0)
                        .durability(0.0)
                        .precision(0.0)
                        .randomWeight(2.0D)
                        .build(),
                moveset,
                EchoesEntities.ACT0.get(),
                id)
                // the egg is never steered; AUTOMATIC is the core's policy for a Stand without manual control
                .standControlType(StandControlType.AUTOMATIC)
                .manualControl(false)
                .distanceStrengthDecay(false)
                .standDimensions(0.65F, 1.95F)
                .summonSound(() -> EchoesSounds.ECHOES_SUMMON_BGM_COMMON)
                .unsummonSound(() -> EchoesSounds.ECHOES_RECALL_BGM);
    }
}
