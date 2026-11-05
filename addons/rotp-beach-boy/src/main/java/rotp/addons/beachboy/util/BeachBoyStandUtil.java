package rotp.addons.beachboy.util;

import java.util.List;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** 1.16 BeachBoyStandUtil. */
public final class BeachBoyStandUtil {
    public static final double SOUND_HEAR_RANGE = 128.0;

    private BeachBoyStandUtil() {}

    public static boolean isBeachBoyUser(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && BeachBoySettings.isBeachBoy(StandPower.get(living));
    }

    public static boolean isBeachBoySummoned(@Nullable LivingEntity entity) {
        StandPower power = entity != null ? StandPower.get(entity) : null;
        return BeachBoySettings.isBeachBoy(power) && power.getSummonedStandEntity() instanceof BeachBoyStandEntity;
    }

    @Nullable
    public static BeachBoyStandEntity getStand(@Nullable LivingEntity user) {
        return user == null ? null : getStand(StandPower.get(user));
    }

    @Nullable
    public static BeachBoyStandEntity getStand(@Nullable StandPower power) {
        return power != null && power.getSummonedStandEntity() instanceof BeachBoyStandEntity stand ? stand : null;
    }

    public static boolean isHoldingRod(@Nullable LivingEntity entity) {
        return !getHeldRod(entity).isEmpty();
    }

    public static ItemStack getHeldRod(@Nullable LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = entity.getItemInHand(InteractionHand.MAIN_HAND);
        if (isRodItem(main)) {
            return main;
        }
        ItemStack off = entity.getItemInHand(InteractionHand.OFF_HAND);
        return isRodItem(off) ? off : ItemStack.EMPTY;
    }

    public static boolean isRodItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(BeachBoyAddon.BEACH_BOY_ROD.get());
    }

    public static void removeAllRods(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isRodItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Nullable
    public static BeachBoyHookEntity findHook(@Nullable LivingEntity owner) {
        if (owner == null || owner.level() == null) {
            return null;
        }
        AABB box = owner.getBoundingBox().inflate(264.0);
        List<BeachBoyHookEntity> hooks = owner.level().getEntitiesOfClass(BeachBoyHookEntity.class, box,
                candidate -> candidate.isAlive() && owner.equals(candidate.getOwner()));
        return hooks.isEmpty() ? null : hooks.get(0);
    }

    @Nullable
    public static BeachBoyHookEntity findHook(@Nullable StandPower power) {
        return power == null ? null : findHook(power.getUser());
    }

    public static boolean isRemoteHook(@Nullable StandPower power) {
        BeachBoyStandEntity stand = getStand(power);
        return stand != null && stand.isManuallyControlled();
    }

    public static boolean isSoundSenseEnabled(@Nullable StandPower power) {
        return BeachBoySettings.isSoundSense(power);
    }

    public static boolean isCrushDamageEnabled(@Nullable StandPower power) {
        return BeachBoySettings.isCrushDamage(power);
    }

    public static boolean isPathLineEnabled(@Nullable StandPower power) {
        return BeachBoySettings.isPathLine(power);
    }

    public static boolean isWhipSwinging(@Nullable LivingEntity user) {
        BeachBoyStandEntity stand = getStand(user);
        return stand != null && stand.getWhipTicks() > 0;
    }

    /** 1.16 power.getType().getColor(): Beach Boy's colour 0x7EB3D6. */
    public static int beachBoyColor(@Nullable StandPower power) {
        return 0x7EB3D6;
    }

    public static boolean isEmbeddedHook(LivingEntity user) {
        BeachBoyHookEntity hook = findHook(user);
        return hook != null && hook.getHookedEntity() != null;
    }

    @Nullable
    public static BeachBoyHookEntity findHookEmbeddedIn(@Nullable Entity target) {
        if (target == null || target.level() == null) {
            return null;
        }
        AABB box = target.getBoundingBox().inflate(8.0);
        List<BeachBoyHookEntity> hooks = target.level().getEntitiesOfClass(BeachBoyHookEntity.class, box,
                candidate -> candidate.isAlive() && candidate.getHookedEntity() == target);
        return hooks.isEmpty() ? null : hooks.get(0);
    }

    public static boolean isRemotePuppeted(LivingEntity target) {
        BeachBoyHookEntity hook = findHookEmbeddedIn(target);
        return hook != null && hook.isRemoteControlled();
    }

    public static void suppressPuppetAi(LivingEntity target) {
        if (target != null && isRemotePuppeted(target)) {
            target.xxa = 0.0F;
            target.yya = 0.0F;
            target.zza = 0.0F;
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
    }

    public static float getSpeedRatio(@Nullable StandPower power) {
        double speed = 14.0;
        BeachBoyStandEntity stand = getStand(power);
        if (stand != null) {
            speed = stand.getAttackSpeed();
        }
        else if (power != null && power.getPowerType() != null) {
            speed = statSpeed(power);
        }
        return (float) (speed / 14.0);
    }

    public static float getPowerRatio(@Nullable StandPower power) {
        BeachBoyStandEntity stand = getStand(power);
        double attack = stand != null ? stand.getAttackDamage() : standPowerStat(power);
        return (float) (attack / 8.0);
    }

    /** 1.16 TheGratefulDeadGrabUtil.getStandPowerStat: the Stand's power stat (base plus development). */
    public static float standPowerStat(@Nullable StandPower power) {
        if (power == null || power.getPowerType() == null) {
            return 5.0F;
        }
        return (float) power.getPowerType().getStandStats().power(power.getStatsDevelopment());
    }

    private static double statSpeed(StandPower power) {
        return power.getPowerType().getStandStats().speed(power.getStatsDevelopment());
    }

    private static float speedScale(@Nullable LivingEntity user) {
        StandPower power = user == null ? null : StandPower.get(user);
        return Mth.clamp(0.88F + 0.12F * getSpeedRatio(power), 0.88F, 1.22F);
    }

    public static float getReelStep(@Nullable LivingEntity user) {
        return 1.5F * speedScale(user);
    }

    public static double getRetractSpeed(@Nullable LivingEntity user) {
        return 1.35 * speedScale(user);
    }

    public static double getPathPullStep(@Nullable LivingEntity user) {
        return 0.55 * speedScale(user);
    }

    public static float getCastSpeedMultiplier(@Nullable LivingEntity user) {
        StandPower power = user == null ? null : StandPower.get(user);
        return Mth.clamp(0.92F + 0.08F * getSpeedRatio(power) + 0.08F * getPowerRatio(power), 0.92F, 1.18F);
    }

    public static float getRangeMultiplier(@Nullable StandPower power) {
        return Mth.clamp(0.96F + 0.08F * getPowerRatio(power) + 0.04F * getSpeedRatio(power), 0.96F, 1.12F);
    }

    public static float getCrushDamage(@Nullable LivingEntity user) {
        StandPower power = user == null ? null : StandPower.get(user);
        return 0.45F * Mth.clamp(0.75F + 0.35F * getPowerRatio(power), 0.75F, 1.35F);
    }
}
