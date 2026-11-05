package rotp.addons.rhcp.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import com.google.gson.JsonElement;

import java.util.function.Consumer;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpConfig;
import rotp.addons.rhcp.RhcpEvents;
import rotp.addons.rhcp.RhcpService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class RhcpStandType extends EntityStandType {
    public RhcpStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        enforceControlPolicy();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        enforceControlPolicy();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        enforceControlPolicy();
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower power) {
        if (user instanceof ServerPlayer player) {
            RhcpService.cleanupTransient(player);
        }
        super.forceUnsummon(user, power);
    }

    // Beta2 RHCPEntity.onAddedToWorld: without SaveRHCPData every summon is a
    // fresh RHCP at 0 energy and potency; with it the user's values carry over.
    @Override
    public boolean summon(LivingEntity user, StandPower standPower,
            Consumer<StandEntity> beforeTheSummon, boolean addToWorld) {
        if (!user.level().isClientSide()
                && standPower.canUsePower()
                && standPower.getSummonedStandEntity() == null
                && !RhcpConfig.saveRhcpData(false)
                && RhcpAddon.state(user).setEnergy(0.0F)
                && user instanceof ServerPlayer player) {
            RhcpService.sync(player);
        }
        return super.summon(user, standPower, beforeTheSummon, addToWorld);
    }

    // 1.16 kept energy and potency on the Stand entity, so removing or
    // replacing the Stand started the next RHCP at 0/0. With SaveRHCPData
    // an unsummon keeps them.
    @Override
    public StandTypePersistentData newDataInstance() {
        return new RhcpStandData(this);
    }

    private static final class RhcpStandData
            extends StandTypePersistentData {
        private RhcpStandData(RhcpStandType powerType) {
            super(powerType);
        }

        @Override
        public void onPowerCleared(
                Power<?> userPower, PowerType newType) {
            super.onPowerCleared(userPower, newType);
            RhcpEvents.onStandCleared(userPower.getUser());
        }
    }

    private void enforceControlPolicy() {
        standControlType(StandControlType.LONG_DISTANCE_OPERATION);
        manualControl(true);
        distanceStrengthDecay(false);
    }
}

