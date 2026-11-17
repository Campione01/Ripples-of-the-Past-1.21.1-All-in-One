package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonClientConfig;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterClient;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterShake;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Item 9: the camera of the erasing user alone, in its angles only; the player's own rotation and aim stay untouched. */
public final class KingCrimsonTimeEraseCameraShake {
    private static final float[] ANGLES = new float[3];

    private KingCrimsonTimeEraseCameraShake() {}

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        // Only a running shatter shakes, so an erase without the breaking of the ground never does.
        float tick = KingCrimsonShatterClient.shakeTick((float) event.getPartialTick());
        if (tick < 0.0F || !KingCrimsonClientConfig.timeEraseCameraShake()) {
            return;
        }
        float strength = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        KingCrimsonShatterShake.angles(tick, strength, ANGLES);
        if (ANGLES[0] == 0.0F && ANGLES[1] == 0.0F && ANGLES[2] == 0.0F) {
            return;
        }
        event.setPitch(event.getPitch() + ANGLES[0]);
        event.setYaw(event.getYaw() + ANGLES[1]);
        event.setRoll(event.getRoll() + ANGLES[2]);
    }
}
