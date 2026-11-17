package rotp.addons.highwaystar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarRoomEntity;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads;
import rotp.addons.highwaystar.room.HighwayStarRoomState;

final class HighwayStarRoomClient {
    static final int PREVIEW_RANGE_SQR = 24 * 24;
    private static boolean previewRefresh;

    private HighwayStarRoomClient() {}

    static void register(IEventBus modBus) {
        modBus.addListener(HighwayStarRoomClient::particles);
        NeoForge.EVENT_BUS.addListener(HighwayStarRoomClient::tick);
        NeoForge.EVENT_BUS.addListener(HighwayStarRoomClient::render);
        NeoForge.EVENT_BUS.addListener(HighwayStarRoomClient::roomLeaves);
        NeoForge.EVENT_BUS.addListener(HighwayStarRoomClient::levelUnloads);
        NeoForge.EVENT_BUS.addListener(HighwayStarRoomClient::logout);
        HighwayStarRoomPayloads.installClientHandlers(payload -> {
            Minecraft mc=Minecraft.getInstance();
            if (mc.level == null || !mc.level.dimension().location().equals(payload.dimension())) return;
            // Payload handlers execute on MAIN. The entity owns its viewer-only preview, never a static map.
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof HighwayStarRoomEntity room && room.getUUID().equals(payload.roomId())) {
                    room.setClientLure(payload.lure());
                    break;
                }
            }
        }, payload -> {
            Minecraft mc=Minecraft.getInstance();
            if (mc.player != null && mc.level != null && mc.level.dimension().location().equals(payload.dimension())) {
                mc.player.getData(HighwayStarAddon.ROOM_STATE).startClientView(payload);
            }
        }, payload -> {
            Minecraft mc=Minecraft.getInstance();
            if (mc.player != null) mc.player.getData(HighwayStarAddon.ROOM_STATE).endClientView(payload);
        });
    }

    private static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(HighwayStarAddon.ROOM_FOOTPRINT.get(), HighwayStarRoomParticle.Provider::new);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.isPaused() && mc.player.tickCount % 4 == 0) previewRefresh = true;
        HighwayStarRoomState state=mc.player.getData(HighwayStarAddon.ROOM_STATE);
        var view=state.getClientView();
        if (view == null) return;
        if (mc.level == null || !mc.level.dimension().location().equals(view.dimension()) || !mc.player.isAlive()) {
            state.clearClientView();
        } else if (!mc.isPaused()) {
            state.tickClientView();
        }
    }

    private static void render(RenderGuiEvent.Pre event) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (previewRefresh) {
            previewRefresh = false;
            refreshPreviews(mc, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
        HighwayStarRoomState state=mc.player.getData(HighwayStarAddon.ROOM_STATE);
        var view=state.getClientView();
        if (view == null || !mc.level.dimension().location().equals(view.dimension())) return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float elapsed=state.getClientTicks()+partial;
        float collapse=Mth.clamp((elapsed-(view.durationTicks()-20))/20F,0,1);
        if (state.getClientPreview() == null) state.setClientPreview(HighwayStarRoomScene.preview(view.lure()));

        var gui=event.getGuiGraphics();
        gui.flush();
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting=RenderSystem.getVertexSorting();
        float fogStart=RenderSystem.getShaderFogStart(),fogEnd=RenderSystem.getShaderFogEnd();
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var modelView=RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        var buffers=mc.renderBuffers().bufferSource();
        try {
            // This illusion intentionally replaces the whole world view, including during the collapse.
            gui.fill(0,0,gui.guiWidth(),gui.guiHeight(),0xFF18232D);
            gui.flush();
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            float aspect=(float)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
            float fov=mc.options.fov().get().floatValue();
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(fov),aspect,0.05F,32),
                    VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            RenderSystem.setShaderFogStart(64);
            RenderSystem.setShaderFogEnd(128);
            PoseStack pose=new PoseStack();
            pose.mulPose(Axis.XP.rotationDegrees(mc.gameRenderer.getMainCamera().getXRot()));
            float entryYaw=view.facing().getOpposite().toYRot();
            pose.mulPose(Axis.YP.rotationDegrees(mc.gameRenderer.getMainCamera().getYRot()-entryYaw));
            HighwayStarRoomScene.enclosure(pose,buffers,collapse);
            HighwayStarRoomScene.furniture(pose,buffers,view.lure(),state.getClientPreview(),partial);
        } finally {
            try {
                buffers.endBatch();
            } finally {
                RenderSystem.depthMask(true);
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
                RenderSystem.setProjectionMatrix(projection,sorting);
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderFogStart(fogStart);
                RenderSystem.setShaderFogEnd(fogEnd);
                RenderSystem.depthMask(depthMask);
                RenderSystem.depthFunc(depthFunc);
                RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
                if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
                if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            }
        }
    }

    private static void refreshPreviews(Minecraft mc, float partialTick) {
        // GUI Pre runs after the world and Iris composites. The door never renders a second world pass.
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof HighwayStarRoomEntity room)) continue;
            if (room.isRemoved() || room.isConsumed() || !room.hasClientLure()
                    || room.distanceToSqr(mc.player) > PREVIEW_RANGE_SQR) {
                room.setClientPreview(null);
                continue;
            }
            if (room.getClientPreview() instanceof HighwayStarRoomPreview preview) {
                preview.refresh(room.getClientLure(), partialTick);
            } else {
                var preview = new HighwayStarRoomPreview();
                room.setClientPreview(preview);
                preview.refresh(room.getClientLure(), partialTick);
            }
        }
    }

    private static void roomLeaves(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ClientLevel && event.getEntity() instanceof HighwayStarRoomEntity room) {
            room.setClientPreview(null);
        }
    }

    private static void levelUnloads(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) closePreviews(level);
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        closePreviews(Minecraft.getInstance().level);
    }

    private static void closePreviews(ClientLevel level) {
        previewRefresh = false;
        if (level == null) return;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof HighwayStarRoomEntity room) room.setClientPreview(null);
        }
    }
}
