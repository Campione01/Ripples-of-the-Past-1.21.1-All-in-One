package rotp.addons.spin.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.item.SteelBallItem;
import rotp.addons.spin.item.WreckingBallItem;

final class SpinWearableRenderer {
    private static final ResourceLocation CLOAK_TEXTURE = SpinAddon.id("textures/entity/gyros_cloak.png");
    private static final ResourceLocation HOLSTER_TEXTURE = SpinAddon.id("textures/entity/gyros_holster.png");
    private static final float DEGREES = Mth.DEG_TO_RAD;
    private static final Map<LivingEntity, CloakState> CLOAK_STATE = new WeakHashMap<>();
    private static ModelPart cloak;
    private static ModelPart holster;

    private SpinWearableRenderer() {}

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new CloakLayer(renderer));
        }
    }

    private static final class CloakLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private CloakLayer(PlayerRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (!player.isInvisible() && GyrosCloakItem.isWornOnChest(player)) {
                renderCloak(pose, buffers, light, player, getParentModel(), partialTick, age);
            }
        }
    }

    private static ModelPart bakeCloak() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition shoulders = mesh.getRoot().addOrReplaceChild("shoulders", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -0.5F, -2.5F, 9, 3, 5, new CubeDeformation(0.3F)), PartPose.ZERO);
        addCloakPanel(shoulders, 0, -3.5F, 0, 3, 11, false);
        addCloakPanel(shoulders, 1, 0.0F, 8, 4, 13, false);
        addCloakPanel(shoulders, 2, 3.5F, 0, 3, 11, true);
        return LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("shoulders");
    }

    private static void addCloakPanel(PartDefinition shoulders, int index, float x, int u, int width, int height, boolean mirror) {
        shoulders.addOrReplaceChild("panel" + index, CubeListBuilder.create().texOffs(u, 8).mirror(mirror)
                .addBox(-width / 2.0F, 0, 0, width, height, 1), PartPose.offset(x, 1.5F, 2.4F));
    }

    static void renderCloak(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity wearer,
            HumanoidModel<?> model, float partialTick, float age) {
        if (cloak == null) cloak = bakeCloak();
        cloak.copyFrom(model.body);

        double lagX = wearer.xo - wearer.getX();
        double lagY = wearer.yo - wearer.getY();
        double lagZ = wearer.zo - wearer.getZ();
        float bob = 0.0F;
        if (wearer instanceof Player player) {
            lagX = Mth.lerp(partialTick, player.xCloakO, player.xCloak) - Mth.lerp(partialTick, wearer.xo, wearer.getX());
            lagY = Mth.lerp(partialTick, player.yCloakO, player.yCloak) - Mth.lerp(partialTick, wearer.yo, wearer.getY());
            lagZ = Mth.lerp(partialTick, player.zCloakO, player.zCloak) - Mth.lerp(partialTick, wearer.zo, wearer.getZ());
            bob = Mth.lerp(partialTick, player.oBob, player.bob);
        }
        float yaw = Mth.lerp(partialTick, wearer.yBodyRotO, wearer.yBodyRot) * DEGREES;
        double sin = Mth.sin(yaw);
        double cos = -Mth.cos(yaw);
        float walk = Mth.lerp(partialTick, wearer.walkDistO, wearer.walkDist);
        float rise = Mth.clamp((float) lagY * 10.0F, -6.0F, 32.0F) + Mth.sin(walk * 6.0F) * 32.0F * bob;
        float back = Mth.clamp((float) (lagX * sin + lagZ * cos) * 100.0F, 0.0F, 150.0F);
        float drag = Mth.clamp(6.0F + back / 2.0F + rise, 0.0F, 80.0F);
        float lean = Mth.clamp((float) (lagX * cos - lagZ * sin) * 100.0F, -20.0F, 20.0F) / 2.0F;
        CloakState state = CLOAK_STATE.computeIfAbsent(wearer, ignored -> new CloakState(age));
        float delta = age - state.age;
        float step = delta >= 0.0F && delta <= 20.0F ? delta * 0.2F : 0.0F;
        state.open = Mth.clamp(state.open + (isSailing(wearer) ? step : -step), 0.0F, 1.0F);
        state.age = age;
        for (int i = 0; i < 3; i++) {
            ModelPart panel = cloak.getChild("panel" + i);
            float base = Mth.lerp(state.open, drag, Math.max(drag, 72.0F));
            float amplitude = Mth.lerp(state.open, 2.5F + 6.0F * drag / 80.0F, 8.0F);
            float speed = Mth.lerp(state.open, 0.15F, 0.7F);
            panel.xRot = Mth.clamp(base + amplitude * Mth.sin(age * speed + i * 2.1F), 0.0F, 95.0F) * DEGREES;
            panel.zRot = ((1 - i) * 24.0F * state.open + lean) * DEGREES;
        }
        cloak.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CLOAK_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
    }

    private static boolean isSailing(LivingEntity wearer) {
        return wearer == Minecraft.getInstance().player ? SpinEquipmentClient.isSailing()
                : !wearer.onGround() && !wearer.isInWater() && wearer.getY() - wearer.yo < -0.04
                        && (wearer.getMainHandItem().getItem() instanceof SteelBallItem
                                || wearer.getOffhandItem().getItem() instanceof SteelBallItem);
    }

    private static ModelPart bakeHolster() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, 10, -2, 8, 2, 4, new CubeDeformation(0.6F))
                .texOffs(48, 8).addBox(-1.618F, 10, -2.85F, 3.236F, 2, 0.5F), PartPose.ZERO);
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 8)
                .addBox(2.5F, 12, -4.5F, 3, 3, 3)
                .texOffs(24, 8).addBox(3.5F, 11, -2.8F, 1, 2, 1)
                .texOffs(12, 8).addBox(-5.5F, 12, -4.5F, 3, 3, 3)
                .texOffs(28, 8).addBox(-4.5F, 11, -2.8F, 1, 2, 1), PartPose.ZERO);
        addBall(hips, "right_steel", 32, 8, 3.0F);
        addBall(hips, "left_steel", 40, 8, -5.0F);
        addBall(hips, "right_wrecking", 32, 16, 3.0F);
        addBall(hips, "left_wrecking", 40, 16, -5.0F);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    private static void addBall(PartDefinition hips, String key, int u, int v, float x) {
        hips.addOrReplaceChild(key, CubeListBuilder.create().texOffs(u, v).addBox(x, 11, -4.8F, 2, 2, 2), PartPose.ZERO);
    }

    static void renderHolster(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> model, ItemStack stack) {
        if (holster == null) holster = bakeHolster();
        ModelPart belt = holster.getChild("belt");
        belt.copyFrom(model.body);
        ModelPart hips = holster.getChild("hips");
        hips.setPos(0.0F, (model.leftLeg.y + model.rightLeg.y) / 2.0F - 12.0F,
                (model.leftLeg.z + model.rightLeg.z) / 2.0F - 0.1F);
        hips.yRot = model.body.yRot;
        List<ItemStack> balls = GyrosHolsterItem.contents(stack);
        setBall(hips, "right", balls.isEmpty() ? ItemStack.EMPTY : balls.get(0));
        setBall(hips, "left", balls.size() < 2 ? ItemStack.EMPTY : balls.get(1));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(HOLSTER_TEXTURE));
        belt.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
        hips.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
    }

    private static void setBall(ModelPart hips, String side, ItemStack ball) {
        boolean wrecking = ball.getItem() instanceof WreckingBallItem;
        hips.getChild(side + "_steel").visible = !ball.isEmpty() && !wrecking;
        hips.getChild(side + "_wrecking").visible = wrecking;
    }

    private static final class CloakState {
        private float open;
        private float age;

        private CloakState(float age) {
            this.age = age;
        }
    }
}
