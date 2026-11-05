package rotp.addons.nextalbum.client.particle;

import java.util.Random;

import org.jetbrains.annotations.Nullable;

import rotp.core.impl.powers.hamon.client.particle.custom.FirstPersonHamonAura.HamonAuraPseudoParticle;
import rotp.core.impl.powers.hamon.client.particle.custom.HamonAuraParticleRenderType;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Next Album 0.1.13 StandResolveAuraParticle: a Hamon-aura-like flame in
 * the Stand's colour that follows its entity, rises slowly, fades in and
 * out (alpha 0.05 to 0.35) and lives 25 to 34 ticks.
 */
public final class ResolveAuraParticle extends TextureSheetParticle {
    private static final Random RANDOM = new Random();
    @Nullable
    private static SpriteSet savedSprites;
    private final SpriteSet sprites;
    private final int startingSpriteRandom;
    @Nullable
    private final LivingEntity followEntity;
    private Vec3 prevEntityPos;
    private final float entityScale;

    private ResolveAuraParticle(
            ClientLevel level, @Nullable LivingEntity entity,
            double x, double y, double z, int colour, SpriteSet sprites,
            float entityScale) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.followEntity = entity;
        this.prevEntityPos = entity != null ? entity.position() : new Vec3(x, y, z);
        float[] rgb = normalised(colour);
        rCol = rgb[0];
        gCol = rgb[1];
        bCol = rgb[2];
        this.entityScale = entityScale;
        xd *= 0.1D * entityScale;
        yd *= 0.1D * entityScale;
        zd *= 0.1D * entityScale;
        quadSize *= 0.75F * (1.2F + 0.6F * RANDOM.nextFloat());
        lifetime = 25 + RANDOM.nextInt(10);
        startingSpriteRandom = RANDOM.nextInt(lifetime);
        alpha = 0.25F;
        hasPhysics = false;
        setSprite(sprites.get((age + startingSpriteRandom) % lifetime, lifetime));
    }

    // Each channel over the brightest one, so dark Stand colours still glow.
    static float[] normalised(int colour) {
        float r = (colour >> 16 & 0xFF) / 255.0F;
        float g = (colour >> 8 & 0xFF) / 255.0F;
        float b = (colour & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        return max > 0.0F
                ? new float[] {r / max, g / max, b / max}
                : new float[] {1.0F, 1.0F, 1.0F};
    }

    @Nullable
    public static SpriteSet sprites() {
        return savedSprites;
    }

    public static ResolveAuraParticle create(
            ClientLevel level, LivingEntity entity,
            double x, double y, double z, int colour, SpriteSet sprites) {
        return new ResolveAuraParticle(level, entity, x, y, z, colour, sprites, 1.0F);
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
        setSprite(sprites.get((age + startingSpriteRandom) % lifetime, lifetime));
        yd += 0.004D * entityScale;
        move(xd, yd, zd);
        if (y == yo) {
            xd *= 1.1D;
            zd *= 1.1D;
        }
        xd *= 0.96D;
        yd *= 0.96D;
        zd *= 0.96D;
        if (onGround) {
            xd *= 0.7D;
            zd *= 0.7D;
        }
        if (followEntity != null && followEntity.isAlive()) {
            Vec3 current = followEntity.position();
            Vec3 offset = current.subtract(prevEntityPos);
            move(offset.x, offset.y, offset.z);
            prevEntityPos = current;
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        return quadSize * Mth.clamp((age + partialTick) / lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (followEntity != null
                && minecraft.getCameraEntity() == followEntity
                && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        float ageF = (age + partialTick) / lifetime;
        float wave = ageF <= 0.5F ? ageF * 2.0F : (1.0F - ageF) * 2.0F;
        alpha = 0.05F + wave * 0.3F;
        super.render(buffer, camera, partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return HamonAuraParticleRenderType.HAMON_AURA;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static final class Provider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            savedSprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type, ClientLevel level,
                double x, double y, double z,
                double vx, double vy, double vz) {
            int colour = (int) (Mth.clamp(vx, 0.0D, 1.0D) * 255.0D) << 16
                    | (int) (Mth.clamp(vy, 0.0D, 1.0D) * 255.0D) << 8
                    | (int) (Mth.clamp(vz, 0.0D, 1.0D) * 255.0D);
            return new ResolveAuraParticle(level, null, x, y, z, colour, sprites, 1.0F);
        }
    }

    /** 0.1.13 ResolveAuraPseudoParticle: the aura on the first-person arms. */
    public static final class FirstPerson extends HamonAuraPseudoParticle {
        public FirstPerson(
                double x, double y, double z, SpriteSet sprites,
                HumanoidArm arm, int colour) {
            super(x, y, z, sprites, arm);
            float[] rgb = normalised(colour);
            rCol = rgb[0];
            gCol = rgb[1];
            bCol = rgb[2];
        }
    }
}
