package rotp.addons.gratefuldead.client;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Next Album 0.1.13 AgingMistParticle: a faint upright mist sheet drifting sideways. */
public final class AgingMistParticle extends TextureSheetParticle {
    private final float facingYaw;
    private final float maxAlpha;

    private AgingMistParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        facingYaw = random.nextFloat() * Mth.TWO_PI;
        float speed = 0.0035F + random.nextFloat() * 0.0055F;
        xd = Mth.cos(facingYaw) * speed;
        yd = 0.0D;
        zd = Mth.sin(facingYaw) * speed;
        gravity = 0.0F;
        hasPhysics = false;
        lifetime = 80 + random.nextInt(55);
        quadSize *= 2.1F + random.nextFloat() * 1.6F;
        maxAlpha = 0.16F + random.nextFloat() * 0.12F;
        alpha = 0.0F;
        rCol = 0.84F;
        gCol = 0.8F;
        bCol = 0.74F;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        move(xd, yd, zd);
        alpha = alphaAt(age);
    }

    private float alphaAt(int ticks) {
        float fadeIn = Math.min(18.0F, lifetime * 0.22F);
        float fadeOut = Math.min(28.0F, lifetime * 0.32F);
        if (ticks < fadeIn) {
            return maxAlpha * (ticks / fadeIn);
        }
        return ticks > lifetime - fadeOut
                ? maxAlpha * Math.max(0.0F, (lifetime - ticks) / fadeOut)
                : maxAlpha;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        alpha = alphaAt(age);
        renderRotatedQuad(buffer, camera, new Quaternionf().rotationY(-facingYaw), partialTicks);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        int packed = super.getLightColor(partialTick);
        int block = Math.max(packed & 0xFFFF, 128);
        int sky = Math.max(packed >> 16, 128);
        return block | sky << 16;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new AgingMistParticle(level, x, y, z, sprites);
        }
    }
}
