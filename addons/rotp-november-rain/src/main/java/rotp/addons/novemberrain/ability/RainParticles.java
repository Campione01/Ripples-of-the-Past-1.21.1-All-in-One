package rotp.addons.novemberrain.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;

final class RainParticles {
    public static final int RING_PARTICLES_PER_TICK = 120;

    private RainParticles() {}

    /** The remake's ring of rain (HeavyRain, RainBarrier): falling from five blocks up, a splash on one drop in five. */
    static void ring(Level level, double x, double y, double z, double ringRadius) {
        for (int i = 0; i < RING_PARTICLES_PER_TICK; i++) {
            double angle = level.random.nextDouble() * 2.0D * Math.PI;
            double thickness = (level.random.nextDouble() - 0.5D) * 0.6D;
            double rx = x + Math.cos(angle) * (ringRadius + thickness);
            double rz = z + Math.sin(angle) * (ringRadius + thickness);
            level.addParticle(ParticleTypes.RAIN, rx, y + 5.0D, rz, 0.0D, -1.2D, 0.0D);
            if (level.random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.RAIN, rx, y, rz, 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
