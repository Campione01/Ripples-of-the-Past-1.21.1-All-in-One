package rotp.addons.spin.client.render;

import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.entity.SteelBallEntity;
import rotp.addons.spin.entity.WreckingBall;

public final class SteelBallRenderer extends EntityRenderer<SteelBallEntity> {
    private static final float RADIUS = 0.19F;
    private static final SpinSphere MESH = SpinSphere.build(RADIUS, 24, 16);
    private static final SpinSphere SATELLITE_MESH = SpinSphere.build(0.055F, 12, 8);
    private static final SpinSphere FLIGHT_MESH = SpinSphere.build(0.12F, 16, 12);
    private static final List<Vec3> SOCKETS = WreckingBall.socketDirections(14);
    private static final ResourceLocation STEEL = SpinAddon.id("textures/entity/steel_ball_wrapped.png");
    private ResourceLocation wreckingTexture, satelliteTexture, socketTexture;

    public SteelBallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SteelBallEntity ball, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        try {
            var speed = ball.getDeltaMovement();
            var axis = new Vector3f((float) -speed.z, 0.0F, (float) speed.x);
            if (axis.lengthSquared() < 1.0E-8F) axis.set(1.0F, 0.0F, 0.0F);
            else axis.normalize();
            poses.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians((ball.tickCount + partialTick) * 40.0F), axis));
            boolean satellite = ball.isSatellite();
            draw(poses, buffers, getTextureLocation(ball), satellite ? FLIGHT_MESH : MESH, satellite ? 15728880 : light);
            if (ball.isWrecking() && !satellite) {
                boolean empty = ball.areSatellitesReleased();
                ResourceLocation texture = empty ? socket() : satellite();
                float depth = RADIUS * (empty ? 0.96F : 0.98F);
                for (Vec3 direction : SOCKETS) {
                    poses.pushPose();
                    try {
                        poses.translate(direction.x * depth, direction.y * depth, direction.z * depth);
                        draw(poses, buffers, texture, SATELLITE_MESH, light);
                    } finally {
                        poses.popPose();
                    }
                }
            }
        } finally {
            poses.popPose();
        }
        super.render(ball, yaw, partialTick, poses, buffers, light);
    }

    private static void draw(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture, SpinSphere mesh, int light) {
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        var pose = poses.last();
        for (int index : mesh.indices) {
            int pos = index * 3, uv = index * 2;
            out.addVertex(pose, mesh.positions[pos], mesh.positions[pos + 1], mesh.positions[pos + 2])
                    .setColor(255, 255, 255, 255).setUv(mesh.uvs[uv], mesh.uvs[uv + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose, mesh.normals[pos], mesh.normals[pos + 1], mesh.normals[pos + 2]);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(SteelBallEntity ball) {
        if (ball.isSatellite()) return satellite();
        if (!ball.isWrecking()) return STEEL;
        if (wreckingTexture == null) wreckingTexture = register("wrecking_ball_sphere", SpinSphereTexture.wreckingBall());
        return wreckingTexture;
    }

    private ResourceLocation satellite() {
        if (satelliteTexture == null) satelliteTexture = register("wrecking_satellite_sphere", SpinSphereTexture.satelliteBall());
        return satelliteTexture;
    }

    private ResourceLocation socket() {
        if (socketTexture == null) socketTexture = register("wrecking_socket_sphere", SpinSphereTexture.emptySocket());
        return socketTexture;
    }

    private static ResourceLocation register(String name, NativeImage image) {
        var key = SpinAddon.id("dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(key, new DynamicTexture(image));
        return key;
    }
}
