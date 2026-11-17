package rotp.addons.spin.breaker;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.power.BallBreakerAura;
import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandEntityRenderer;

public final class BallBreakerClient {
    private static final DustParticleOptions AURA_DUST = new DustParticleOptions(new Vector3f(1, 0.9F, 0.5F), 0.6F);
    private static final DustParticleOptions ARC_DUST = new DustParticleOptions(new Vector3f(1, 1, 0.85F), 0.4F);

    private BallBreakerClient() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BallBreakerAddon.ENTITY.get(), StandEntityRenderer::new);
    }

    static void tickAura(BallBreakerEntity stand) {
        if (!SpinConfig.BALL_BREAKER_AURA_ENABLED.get() || stand.underInvisibilityEffect() || !ClientGlobals.canSeeStands) {
            return;
        }
        float alpha = stand.clientStuff.getAlpha(stand, 1);
        int sparks = BallBreakerAura.sparkCount(SpinConfig.BALL_BREAKER_AURA_SPARKS.get(), alpha);
        double radius = SpinConfig.BALL_BREAKER_AURA_RADIUS.get();
        double height = BallBreakerAura.verticalRadius(stand.getBbHeight(), radius);
        RandomSource random = stand.getRandom();
        for (int i = 0; i < sparks; i++) {
            double[] point = shellPoint(random, radius, height);
            particle(stand, AURA_DUST, point[0], point[1], point[2]);
        }
        if (alpha > 0 && random.nextDouble() < SpinConfig.BALL_BREAKER_AURA_ARC_CHANCE.get()) {
            int segments = SpinConfig.BALL_BREAKER_AURA_ARC_SEGMENTS.get();
            double[] from = shellPoint(random, radius, height);
            double[] to = shellPoint(random, radius, height);
            double[] jitter = new double[(segments - 1) * 3];
            for (int i = 0; i < jitter.length; i++) {
                jitter[i] = random.nextDouble();
            }
            double[][] points = BallBreakerAura.arc(from, to, segments,
                    SpinConfig.BALL_BREAKER_AURA_ARC_JITTER.get(), jitter);
            for (int i = 1; i < points.length; i++) {
                double[] a = points[i - 1];
                double dx = points[i][0] - a[0];
                double dy = points[i][1] - a[1];
                double dz = points[i][2] - a[2];
                int specks = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.08));
                for (int s = 0; s < specks; s++) {
                    double t = (double) s / specks;
                    particle(stand, ARC_DUST, a[0] + dx * t, a[1] + dy * t, a[2] + dz * t);
                }
            }
        }
    }

    private static double[] shellPoint(RandomSource random, double radius, double height) {
        return BallBreakerAura.shellPoint(random.nextDouble(), random.nextDouble(), radius, height);
    }

    private static void particle(BallBreakerEntity stand, DustParticleOptions dust, double x, double y, double z) {
        stand.level().addParticle(dust, stand.getX() + x, stand.getY(0.5) + y, stand.getZ() + z, 0, 0, 0);
    }
}
