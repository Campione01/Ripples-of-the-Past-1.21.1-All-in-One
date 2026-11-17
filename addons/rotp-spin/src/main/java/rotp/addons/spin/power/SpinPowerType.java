package rotp.addons.spin.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import rotp.addons.spin.config.SpinConfig;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;

public final class SpinPowerType extends PlayerPowerType<SpinData> {
    public static final int COLOR = 13934615;

    public SpinPowerType(ResourceLocation key, MovesetBuilder moveset) {
        super(key, moveset);
    }

    @Override
    public SpinData newDataInstance() {
        return new SpinData();
    }

    @Override
    public Component getName(Power<?> power) {
        return Component.translatable("non_stand.rotp_spin.spin");
    }

    @Override
    public boolean keepOnDeath(PlayerPower power) {
        return true;
    }

    @Override
    public boolean isLeapUnlocked(PlayerPower power) {
        return true;
    }

    @Override
    public float getLeapStrength(PlayerPower power) {
        float base = SpinConfig.LEAP_STRENGTH.get().floatValue();
        var speed = power.getUser().getAttribute(Attributes.MOVEMENT_SPEED);
        return speed == null ? base : SpinLeap.strength(base, speed.getValue(), speed.getBaseValue(),
                SpinConfig.LEAP_IGNORE_SLOWNESS.get());
    }

    @Override
    public int getLeapCooldownPeriod(PlayerPower power) {
        return SpinConfig.LEAP_COOLDOWN_TICKS.get();
    }

    @Override
    public float getLeapEnergyCost(PlayerPower power) {
        return SpinConfig.LEAP_ENERGY_COST.get().floatValue();
    }

    @Override
    public boolean hasLeapEnergy(PlayerPower power, float cost) {
        return SpinData.hasEnergy(power.getUser(), cost);
    }

    @Override
    public boolean consumeLeapEnergy(PlayerPower power, float cost) {
        return SpinData.consumeEnergy(power.getUser(), cost);
    }
}
