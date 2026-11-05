package rotp.addons.beachboy.client;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.util.BeachBoyLine;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.16 BeachBoyClientReel and BeachBoyClientReelSound: the local rod's reel angle (28 degrees per reel step) and the
 * reel sound, looping from the Stand while the hook is retracting or the wheel reels in.
 */
public final class BeachBoyClientReel {
    public static final float ROLL_RADIANS_PER_STEP = (float) Math.toRadians(28.0);
    private static float angle;
    private static float lastLength;
    private static boolean initialized;
    private static int playingHookId = -1;
    private static int scrollReelTicks;

    private BeachBoyClientReel() {}

    public static void tick() {
        float length = currentLength();
        ensureInit(length);
        float delta = length - lastLength;
        if (Math.abs(delta) > 1.0E-4F) {
            angle -= delta / Math.max(0.25F, BeachBoyStandUtil.getReelStep(Minecraft.getInstance().player))
                    * ROLL_RADIANS_PER_STEP;
            lastLength = length;
        }
    }

    public static float angleFor(float length) {
        tick();
        return angle;
    }

    public static void spin(boolean lengthen) {
        tick();
        angle -= (lengthen ? 1.0F : -1.0F) * ROLL_RADIANS_PER_STEP;
        if (!lengthen) {
            notifyReelIn();
        }
    }

    public static void reset() {
        initialized = false;
        angle = 0.0F;
        lastLength = BeachBoyLine.DEFAULT_LENGTH;
    }

    public static float reelToRoll(float length) {
        float steps = (length - BeachBoyLine.MIN_LENGTH) / BeachBoyLine.LENGTH_STEP;
        return -steps * ROLL_RADIANS_PER_STEP;
    }

    private static void ensureInit(float length) {
        if (!initialized) {
            lastLength = length;
            angle = reelToRoll(length);
            initialized = true;
        }
    }

    private static float currentLength() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return BeachBoyLine.DEFAULT_LENGTH;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(mc.player);
        if (hook != null) {
            return hook.getVisualReelLength();
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(mc.player);
        return stand != null ? stand.getMaxLineLength() : BeachBoyLine.DEFAULT_LENGTH;
    }

    public static void notifyReelIn() {
        scrollReelTicks = 8;
    }

    public static boolean isScrollReeling() {
        return scrollReelTicks > 0;
    }

    /** Per hook tick on the client (1.16 BeachBoyClientReelSound.tick). */
    public static void tickSound(@Nullable BeachBoyHookEntity hook) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (scrollReelTicks > 0) {
            scrollReelTicks--;
        }
        if (hook == null || player == null || !ClientGlobals.canHearStands) {
            return;
        }
        if (stillReeling(hook)) {
            if (playingHookId != hook.getId()) {
                playingHookId = hook.getId();
                Entity source = sourceEntity(hook);
                mc.getSoundManager().play(new EntityStoppableSoundInstance(BeachBoyAddon.BEACH_BOY_REEL_BACK.get(),
                        source.getSoundSource(), 1.0F, 1.0F, true, source, hook.getRandom().nextLong(),
                        () -> !stillReeling(hook)));
            }
        }
        else if (playingHookId == hook.getId()) {
            playingHookId = -1;
        }
    }

    private static boolean stillReeling(BeachBoyHookEntity hook) {
        return hook != null && hook.isAlive() && (hook.isRetracting() || scrollReelTicks > 0);
    }

    private static Entity sourceEntity(BeachBoyHookEntity hook) {
        if (hook.getOwner() instanceof LivingEntity owner) {
            BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(owner);
            if (stand != null && stand.isAlive()) {
                return stand;
            }
        }
        return hook;
    }
}
