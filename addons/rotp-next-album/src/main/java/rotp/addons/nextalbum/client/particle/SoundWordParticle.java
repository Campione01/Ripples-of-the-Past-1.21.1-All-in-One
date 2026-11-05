package rotp.addons.nextalbum.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Next Album 0.1.13 JojoSoundParticle: a sound word that rises a little,
 * wobbles, fades out in its second half and lives 18 ticks, full bright.
 */
public final class SoundWordParticle extends TextureSheetParticle {
    private static final int LIFETIME = 18;
    private final SpriteSet sprites;
    private final float baseRoll;
    private double baseOffsetY;
    private double riseSpeed = 0.008D;

    private SoundWordParticle(
            ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        xd = 0.0D;
        yd = 0.0D;
        zd = 0.0D;
        gravity = 0.0F;
        hasPhysics = false;
        quadSize *= 1.2F + (random.nextFloat() - 0.5F) * 0.6F;
        baseRoll = (random.nextFloat() - 0.5F) * (float) (Math.PI / 2);
        roll = baseRoll;
        oRoll = baseRoll;
        lifetime = LIFETIME;
        setSpriteFromAge(sprites);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        riseSpeed *= 0.78D;
        baseOffsetY += riseSpeed;
        x += (random.nextDouble() - 0.5D) * 0.03D;
        y = yo + baseOffsetY + (random.nextDouble() - 0.5D) * 0.03D;
        z += (random.nextDouble() - 0.5D) * 0.03D;
        roll = baseRoll + (random.nextFloat() - 0.5F) * 0.08F;
        setSpriteFromAge(sprites);
        float fadeStart = lifetime * 0.5F;
        if (age > fadeStart) {
            alpha = Math.max(0.0F, 1.0F - (age - fadeStart) / (lifetime - fadeStart));
        }
    }

    // 0.1.13 drew these without the depth test (through walls); the
    // port keeps the translucent sheet, whose state the engine restores.
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new SoundWordParticle(level, x, y, z, sprites);
        }
    }
}
