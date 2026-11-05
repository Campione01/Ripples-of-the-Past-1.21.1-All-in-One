package rotp.addons.beachboy.util;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.core.util.functions.JojoModUtil;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** 1.16 BeachBoySoundUtil: Pesci's voice lines. */
public final class BeachBoySoundUtil {
    private BeachBoySoundUtil() {}

    public static void sayGetMob(@Nullable Entity owner) {
        sayVoice(owner, BeachBoyAddon.PESCI_GET_MOB);
    }

    public static void sayReelIn(@Nullable Entity owner) {
        sayVoice(owner, BeachBoyAddon.PESCI_REEL_IN);
    }

    public static void sayLostTarget(@Nullable Entity owner) {
        sayVoice(owner, BeachBoyAddon.PESCI_LOST_TARGET);
    }

    public static void sayKillYou(@Nullable Entity owner) {
        sayVoice(owner, BeachBoyAddon.PESCI_KILL_YOU);
    }

    public static void sayBrother(@Nullable Entity owner) {
        sayVoice(owner, BeachBoyAddon.PESCI_BROTHER);
    }

    public static void onHookedLiving(@Nullable BeachBoyHookEntity hook, @Nullable Entity target) {
        if (hook != null && !hook.level().isClientSide() && target instanceof LivingEntity && target.isAlive()) {
            sayGetMob(hook.getOwner());
        }
    }

    public static void onStartRetract(@Nullable BeachBoyHookEntity hook) {
        if (hook != null && !hook.level().isClientSide() && !hook.wasReelVoicePlayed()
                && hook.getHookedEntity() instanceof LivingEntity hooked && hooked.isAlive()) {
            hook.markReelVoicePlayed();
            sayReelIn(hook.getOwner());
        }
    }

    public static void onLostEmbeddedTarget(@Nullable BeachBoyHookEntity hook, @Nullable Entity previous) {
        if (hook != null && !hook.level().isClientSide() && previous instanceof LivingEntity && previous.isAlive()) {
            sayLostTarget(hook.getOwner());
        }
    }

    private static void sayVoice(@Nullable Entity owner, Holder<SoundEvent> sound) {
        if (owner instanceof LivingEntity living) {
            JojoModUtil.sayVoiceLine(living, sound);
        }
    }
}
