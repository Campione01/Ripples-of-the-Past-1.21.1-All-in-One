package rotp.addons.kingcrimson.client.epitaph;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.network.EpitaphShortcutPayload;

/**
 * The crimson flash of a short cut in time: a burst of red where the body stood and where it stands now. How it looks
 * is to be settled by looking (the filmed pass).
 */
@OnlyIn(Dist.CLIENT)
public final class EpitaphShortcutFlash {
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.86F, 0.08F, 0.24F), 1.4F);
    private static final int MOTES = 18;

    private EpitaphShortcutFlash() {}

    /** From the network handler, on the client thread. */
    public static void at(EpitaphShortcutPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity body = level.getEntity(payload.entity());
        float width = body != null ? body.getBbWidth() : 0.6F;
        float height = body != null ? body.getBbHeight() : 1.8F;
        burst(level, payload.x(), payload.y(), payload.z(), width, height);
        if (body != null) {
            burst(level, body.getX(), body.getY(), body.getZ(), width, height);
        }
    }

    private static void burst(ClientLevel level, double x, double y, double z, float width, float height) {
        RandomSource random = level.random;
        for (int mote = 0; mote < MOTES; mote++) {
            level.addParticle(CRIMSON, x + (random.nextDouble() - 0.5D) * width, y + random.nextDouble() * height,
                    z + (random.nextDouble() - 0.5D) * width, 0.0D, 0.02D, 0.0D);
        }
    }
}
