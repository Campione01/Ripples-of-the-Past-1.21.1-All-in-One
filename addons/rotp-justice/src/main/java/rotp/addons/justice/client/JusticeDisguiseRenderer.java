package rotp.addons.justice.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import rotp.addons.justice.JusticeAddon;
import rotp.addons.justice.JusticeDisguise.Mode;

@EventBusSubscriber(modid = JusticeAddon.MOD_ID, value = Dist.CLIENT)
public final class JusticeDisguiseRenderer {
    private static final ResourceLocation VILLAGER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation ZOMBIE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");
    private static final double MODEL_VERTICAL_OFFSET = 1.5;
    private static Models models;

    private JusticeDisguiseRenderer() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        Mode mode = JusticeClientState.mode(player);
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (mode == Mode.NONE || level == null || player.level() != level) {
            return;
        }

        if (models == null || models.level != level) {
            models = Models.create(level, minecraft.getEntityModels());
        }
        PoseStack matrices = event.getPoseStack();
        matrices.pushPose();
        try {
            // Legacy DisguiseClient turns around body yaw, then flips about the mid-model Z pivot.
            matrices.translate(0.0, MODEL_VERTICAL_OFFSET, 0.0);
            matrices.mulPose(Axis.YP.rotationDegrees(180.0F - player.yBodyRot));
            matrices.translate(0.0, -1.501, 0.0);
            matrices.translate(0.0, 0.75, 0.0);
            matrices.mulPose(Axis.ZP.rotationDegrees(180.0F));
            matrices.translate(0.0, -0.75, 0.0);

            if (mode == Mode.VILLAGER) {
                draw(event, player, models.villager, models.villagerModel, VILLAGER_TEXTURE);
            }
            else {
                models.zombie.setBaby(false);
                models.zombie.setAggressive(false);
                draw(event, player, models.zombie, models.zombieModel, ZOMBIE_TEXTURE);
            }
        }
        finally {
            matrices.popPose();
        }
        event.setCanceled(true);
    }

    private static <T extends LivingEntity> void draw(RenderPlayerEvent.Pre event, Player player, T proxy,
            EntityModel<T> model, ResourceLocation texture) {
        proxy.setYRot(player.getYRot());
        proxy.yRotO = player.yRotO;
        proxy.yBodyRot = player.yBodyRot;
        proxy.yBodyRotO = player.yBodyRotO;
        proxy.yHeadRot = player.yHeadRot;
        proxy.yHeadRotO = player.yHeadRotO;
        proxy.setXRot(player.getXRot());
        proxy.xRotO = player.xRotO;
        proxy.walkDist = player.walkDist;
        proxy.walkDistO = player.walkDistO;

        float partialTick = event.getPartialTick();
        float walkDelta = player.walkDist - player.walkDistO;
        model.setupAnim(proxy, player.walkDistO + walkDelta * partialTick, Math.min(1.0F, walkDelta),
                player.tickCount + partialTick, player.getViewYRot(partialTick) - player.yBodyRot,
                player.getViewXRot(partialTick));
        model.renderToBuffer(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(texture)),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
    }

    @SubscribeEvent
    public static void unloadLevel(LevelEvent.Unload event) {
        if (models != null && event.getLevel() == models.level) {
            models = null;
        }
    }

    private record Models(ClientLevel level, Villager villager, VillagerModel<Villager> villagerModel,
            Zombie zombie, ZombieModel<Zombie> zombieModel) {
        private static Models create(ClientLevel level, EntityModelSet layers) {
            return new Models(level, new Villager(EntityType.VILLAGER, level),
                    new VillagerModel<>(layers.bakeLayer(ModelLayers.VILLAGER)), new Zombie(EntityType.ZOMBIE, level),
                    new ZombieModel<>(layers.bakeLayer(ModelLayers.ZOMBIE)));
        }
    }
}
