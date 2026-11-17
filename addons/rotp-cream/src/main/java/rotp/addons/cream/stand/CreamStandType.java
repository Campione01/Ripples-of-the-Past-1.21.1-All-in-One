package rotp.addons.cream.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.cream.entity.CreamEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class CreamStandType extends EntityStandType {
    public CreamStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> entityType,
            ResourceLocation id) {
        super(stats, moveset, entityType, id);
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (!user.level().isClientSide()
                && standPower.getSummonedStandEntity()
                        instanceof CreamEntity cream) {
            cream.markLeaving();
        }
        super.forceUnsummon(user, standPower);
    }

    // Stamina pays for the void and ends it: a Stand away would otherwise regain 3 a tick, past what a Resolve 4 void costs.
    @Override
    protected float getBaseStaminaRegen(StandPower standPower) {
        return CreamEntity.isInVoidState(standPower.getUser())
                ? 0.0F
                : super.getBaseStaminaRegen(standPower);
    }
}
