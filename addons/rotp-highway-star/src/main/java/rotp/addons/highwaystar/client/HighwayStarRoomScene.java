package rotp.addons.highwaystar.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.room.HighwayStarRoomLure;

/** A small client-only set: vanilla furniture and the viewer's server-selected lure. */
final class HighwayStarRoomScene {
    static final ResourceLocation WALL = texture("wallpaper");
    static final ResourceLocation FEET = texture("footprints");
    static final ResourceLocation FLOOR = texture("floor");
    static final ResourceLocation CEILING = texture("ceiling");
    static final ResourceLocation FOOT = HighwayStarAddon.id("textures/particle/room_footprint.png");
    private static final UUID PREVIEW_ID = UUID.fromString("ef8ff731-5507-44ac-aa26-0cd3076a1c07");

    private HighwayStarRoomScene() {}

    private static ResourceLocation texture(String name) {
        return HighwayStarAddon.id("textures/room/" + name + ".png");
    }

    @Nullable
    static Entity preview(HighwayStarRoomLure lure) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || lure.kind() != HighwayStarRoomLure.Kind.COMBAT || lure.entityType().isEmpty()) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(lure.entityType().get());
        Entity entity = type == EntityType.PLAYER
                ? new RemotePlayer(mc.level, new GameProfile(PREVIEW_ID, "Illusion")) : type.create(mc.level);
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = 180;
            living.yHeadRot = living.yHeadRotO = 180;
        }
        if (entity != null) {
            entity.setYRot(180);
            entity.yRotO = 180;
            entity.setSilent(true);
        }
        return entity;
    }

    static void furniture(PoseStack pose, MultiBufferSource buffers, HighwayStarRoomLure lure,
            @Nullable Entity preview, float partialTick) {
        block(pose, buffers, -0.65F, -0.9F, -2.2F, 1.3F, 0.12F, 0.8F);
        for (float x : new float[] {-0.58F, 0.48F}) {
            for (float z : new float[] {-2.15F, -1.55F}) {
                block(pose, buffers, x, -1.6F, z, 0.10F, 0.7F, 0.10F);
            }
        }
        item(pose, buffers, new ItemStack(Items.LANTERN), -0.48F, -0.57F, -1.85F, 0.4F);
        switch (lure.kind()) {
            case REST -> {
                item(pose, buffers, new ItemStack(Items.RED_BED), 0.14F, -0.61F, -1.8F, 0.65F);
                item(pose, buffers, new ItemStack(Items.GOLDEN_APPLE), 0.43F, -0.55F, -1.45F, 0.35F);
            }
            case FOOD -> {
                item(pose, buffers, new ItemStack(Items.COOKED_BEEF), 0.22F, -0.6F, -1.8F, 0.55F);
                item(pose, buffers, new ItemStack(Items.BREAD), 0.37F, -0.61F, -1.4F, 0.4F);
            }
            case CHEST -> {
                ItemStack chest = new ItemStack(Items.CHEST);
                chest.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                item(pose, buffers, chest, 0.15F, -0.48F, -1.7F, 0.65F);
            }
            case COMBAT -> {
                if (preview != null) {
                    pose.pushPose();
                    pose.translate(0, -1.6, -2.65);
                    float scale = Math.min(1, 1.85F / Math.max(0.1F, preview.getBbHeight()));
                    scale = Math.min(scale, 1.3F / Math.max(0.1F, preview.getBbWidth()));
                    pose.scale(scale, scale, scale);
                    renderPreview(preview, pose, buffers, partialTick);
                    pose.popPose();
                }
            }
        }
    }

    private static <T extends Entity> void renderPreview(T entity, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        EntityRenderer<? super T> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        renderer.render(entity, 180, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
    }

    private static void block(PoseStack pose, MultiBufferSource buffers, float x, float y, float z,
            float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.OAK_PLANKS.defaultBlockState(),
                pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static void item(PoseStack pose, MultiBufferSource buffers, ItemStack stack,
            float x, float y, float z, float size) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(size, size, size);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        // Chests use a block-entity renderer, which otherwise ignores the stack's foil override.
        MultiBufferSource itemBuffers = stack.is(Items.CHEST) && stack.hasFoil()
                ? type -> ItemRenderer.getFoilBufferDirect(buffers, type, false, true) : buffers;
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, itemBuffers, Minecraft.getInstance().level, 0);
        pose.popPose();
    }

    static void enclosure(PoseStack pose, MultiBufferSource buffers, float collapse) {
        quad(pose, buffers, FLOOR, -3.05F,-1.6F,-3.05F, 3.05F,-1.6F,-3.05F,
                3.05F,-1.6F,3.05F, -3.05F,-1.6F,3.05F, 4,4, -1);
        quad(pose, buffers, CEILING, -3.05F,1.5F,3.05F, 3.05F,1.5F,3.05F,
                3.05F,1.5F,-3.05F, -3.05F,1.5F,-3.05F, 4,4, -1);
        ResourceLocation wall = collapse > 0 ? FEET : WALL;
        // An opaque enclosure remains behind the separating footprint tiles.
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            quad(pose,buffers,wall,-3.05F,-1.6F,-3.05F,3.05F,-1.6F,-3.05F,
                    3.05F,1.5F,-3.05F,-3.05F,1.5F,-3.05F,6,3, collapse > 0 ? 0xFF28333B : -1);
            if (collapse > 0) {
                for (int row=0; row<3; row++) {
                    for (int col=0; col<6; col++) {
                        float delay = ((row * 7 + col * 3 + side) % 11) / 28F;
                        float drift = Math.max(0, collapse - delay);
                        float x=-2.8F+col*0.94F, y=-1.57F+row*1.02F-drift*0.45F;
                        float z=-3+drift*(0.45F+(col%3)*0.2F);
                        int alpha = (int)(255*(1-drift));
                        quad(pose,buffers,FOOT,x,y,z,x+0.72F,y,z,x+0.72F,y+0.85F,z,x,y+0.85F,z,
                                1,1,(alpha<<24)|0x9EDFF0);
                    }
                }
            }
            pose.popPose();
        }
    }

    static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
            float x1,float y1,float z1,float x2,float y2,float z2,
            float x3,float y3,float z3,float x4,float y4,float z4,float u,float v,int color) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        vertex(out,pose,x1,y1,z1,0,v,color);
        vertex(out,pose,x2,y2,z2,u,v,color);
        vertex(out,pose,x3,y3,z3,u,0,color);
        vertex(out,pose,x4,y4,z4,0,0,color);
    }

    static void doorImage(PoseStack pose, MultiBufferSource buffers, RenderType opening) {
        VertexConsumer out = buffers.getBuffer(opening);
        // Render target UVs start at the bottom, unlike the room's PNG textures.
        vertex(out, pose, -0.375F, 0.0625F, 0, 0, 0, -1);
        vertex(out, pose,  0.375F, 0.0625F, 0, 1, 0, -1);
        vertex(out, pose,  0.375F, 1.875F, 0, 1, 1, -1);
        vertex(out, pose, -0.375F, 1.875F, 0, 0, 1, -1);
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x,float y,float z,float u,float v,int color) {
        out.addVertex(pose.last(),x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
    }
}
