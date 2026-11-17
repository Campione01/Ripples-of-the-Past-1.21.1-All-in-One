package rotp.addons.echoes.client;

import java.io.Reader;

import javax.annotation.Nullable;

import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.network.EchoesOnomatopoeiaPayloads;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * R772 F11: draws the word ACT2's tail left on a living entity, the red ball of the tail tip with its letter (the
 * master's OnomatopoeiaAttachedEntity and OnomatopoeiaAttachedRenderer): the author's onomatopoeia_attached model,
 * 1.0 / 0.5 / 0.3 of its size by the target's size; only Stand users see it, as in the master. R772.1 E12e: placed as the
 * master places it. Its letter stays on the target's front: the master's GeckoLib projectile renderer turned the model
 * by yaw - 90, which turns the other way round as a living target turns.
 */
public final class EchoesOnomatopoeiaClient {
    private static final ResourceLocation MODEL = RotpEchoes.id("geo/onomatopoeia_attached.geo.json");
    private static final ResourceLocation TEXTURE = RotpEchoes.id("textures/entity/onomatopoeia_attached.png");
    private static final Int2ObjectMap<EchoesOnomatopoeiaPayloads.Mark> MARKS = new Int2ObjectOpenHashMap<>();
    @Nullable private static ModelPart model;
    private static boolean modelTried;

    private EchoesOnomatopoeiaClient() {}

    public static void onMark(EchoesOnomatopoeiaPayloads.Mark mark) {
        if (mark.word().isEmpty()) {
            MARKS.remove(mark.entityId());
        }
        else {
            MARKS.put(mark.entityId(), mark);
        }
    }

    // an entity gone from this client takes its word along; the server tells again if it comes back with one
    public static void forget(int entityId) {
        MARKS.remove(entityId);
    }

    public static void clear() {
        MARKS.clear();
        model = null;
        modelTried = false;
    }

    public static void render(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (MARKS.isEmpty() || !MARKS.containsKey(entity.getId())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        StandPower viewer = mc.player != null ? StandPower.get(mc.player) : null;
        if (viewer == null || !viewer.hasPower()) {
            return;
        }
        ModelPart part = model();
        if (part == null) {
            return;
        }
        float partialTick = event.getPartialTick();
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        // master snapToTarget: 0.3 along the target's view, its height - 0.8
        Vec3 look = entity.getViewVector(partialTick);
        float size = Math.max(entity.getBbHeight(), entity.getBbWidth());
        float scale = size >= 2.5F ? 1.0F : size >= 0.8F ? 0.5F : 0.3F;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(look.x * 0.3D, entity.getBbHeight() - 0.8D, look.z * 0.3D);
        poseStack.scale(scale, scale, scale);
        // the core's bake, turned upright, is GeckoLib's model half a turn round and 1.5 blocks down, the 1.5 inside
        // the master's scale: the ball's centre is 18 model pixels above this point, not 6 below
        poseStack.translate(0.0D, 1.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        part.xRot = (float) Math.PI;
        part.render(poseStack, event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(TEXTURE)),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @Nullable
    private static ModelPart model() {
        if (!modelTried) {
            modelTried = true;
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(MODEL).orElse(null);
            if (resource != null) {
                try (Reader reader = resource.openAsReader()) {
                    model = ParseModEntityModel.parse(JsonParser.parseReader(reader), ModelFormat.GECKO).bakeRoot();
                }
                catch (Exception e) {
                    model = null;
                }
            }
        }
        return model;
    }
}
