package rotp.addons.highwaystar.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class HighwayStarRoomParticle extends TextureSheetParticle {
    private HighwayStarRoomParticle(ClientLevel level, double x,double y,double z,
            double dx,double dy,double dz, SpriteSet sprites) {
        super(level,x,y,z,dx,dy,dz);
        xd=dx; yd=dy; zd=dz;
        gravity=0.06F;
        lifetime=20+random.nextInt(8);
        quadSize=0.11F+random.nextFloat()*0.06F;
        setColor(0.62F,0.87F,0.95F);
        pickSprite(sprites);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public void tick() {
        super.tick();
        alpha=Math.max(0,1-(float)age/lifetime);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,
                double x,double y,double z,double dx,double dy,double dz) {
            return new HighwayStarRoomParticle(level,x,y,z,dx,dy,dz,sprites);
        }
    }
}
