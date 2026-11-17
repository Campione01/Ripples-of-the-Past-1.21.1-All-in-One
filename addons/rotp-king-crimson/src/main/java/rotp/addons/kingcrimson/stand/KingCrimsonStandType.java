package rotp.addons.kingcrimson.stand;

import javax.annotation.Nullable;

import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class KingCrimsonStandType extends EntityStandType {
    public KingCrimsonStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> entityType,
            ResourceLocation id) {
        super(stats, moveset, entityType, id);
    }

    // The core clears the Stand's action inside this call, with no word of why.
    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (user.level().isClientSide() || !(user instanceof Player player)) {
            super.forceUnsummon(user, standPower);
            return;
        }
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        state.setStandLeaving(true);
        try {
            super.forceUnsummon(user, standPower);
        } finally {
            state.setStandLeaving(false);
        }
    }

    /**
     * The owner's rule of 2026-11-06, "全部替身技能都不应该随替身收回而自动取消": whether an action of the Stand is being
     * cleared by a recall, which ends no lasting skill.
     */
    public static boolean isRecall(
            Player user, @Nullable EntityActionInstance newAction) {
        return KingCrimsonAddon.temporalState(user).isStandLeaving()
                || newAction != null
                        && newAction.ability
                                == ModSpecialActions.STAND_UNSUMMON.get();
    }
}
