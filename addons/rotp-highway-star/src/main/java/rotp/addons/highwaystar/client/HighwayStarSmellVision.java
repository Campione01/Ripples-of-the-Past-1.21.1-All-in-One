package rotp.addons.highwaystar.client;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.network.HighwayStarPayloads.SmellStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** 1.16 HighwayStarSmellRenderer, HighwayStarSmellClientState and S2CHighwayStarSmellPacket.handle. */
public final class HighwayStarSmellVision {
    private static final float EXPAND_PER_TICK = 0.75F;
    private static final float RETRACT_PER_TICK = 0.75F;
    private static final float MIN_RADIUS = 20.0F;

    static boolean enabled = false;
    static boolean retracting = false;
    @Nullable static UUID casterUuid = null;
    @Nullable static UUID standUuid = null;
    static float maxRadius = 0.0F;
    static float currentRadius = 0.0F;
    static long lastUpdateGameTime = 0L;

    private HighwayStarSmellVision() {}

    public static void onSmellPayload(SmellStatePayload msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || !player.getUUID().equals(msg.caster())) {
            return;
        }
        if (!msg.enabled()) {
            enabled = false;
            retracting = true;
            lastUpdateGameTime = level.getGameTime();
        }
        else {
            enabled = true;
            retracting = false;
            casterUuid = msg.caster();
            standUuid = findStandUuid(level, msg.caster());
            maxRadius = Math.max(0.0F, msg.radius());
            lastUpdateGameTime = level.getGameTime();
        }
    }

    @Nullable
    private static UUID findStandUuid(ClientLevel level, UUID caster) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof StandEntity stand && stand.getUser() != null && caster.equals(stand.getUser().getUUID())) {
                return stand.getUUID();
            }
        }
        return null;
    }

    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!enabled && !retracting) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        updateRadius(mc.level.getGameTime());
        if (currentRadius <= 0.0F || casterUuid == null) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (casterUuid.equals(entity.getUUID())) {
            return;
        }
        if (entity instanceof StandEntity stand && stand.getUser() != null
                && casterUuid.equals(stand.getUser().getUUID())) {
            return;
        }
        LivingEntity caster = findLivingByUuid(mc.level, casterUuid);
        if (caster == null) {
            return;
        }
        LivingEntity stand = findLivingByUuid(mc.level, standUuid);
        float r = Math.max(currentRadius, MIN_RADIUS);
        boolean inRange = caster.distanceToSqr(entity) <= (double) r * r;
        if (!inRange && stand != null) {
            inRange = stand.distanceToSqr(entity) <= (double) r * r;
        }
        if (inRange) {
            event.setCanceled(true);
            renderSolid(event, entity, event.getPartialTick());
        }
    }

    private static void updateRadius(long now) {
        if (lastUpdateGameTime <= 0L) {
            lastUpdateGameTime = now;
            return;
        }
        long deltaTicks = now - lastUpdateGameTime;
        if (deltaTicks <= 0L) {
            return;
        }
        float delta = deltaTicks;
        if (enabled) {
            currentRadius = Math.min(maxRadius, currentRadius + delta * EXPAND_PER_TICK);
        }
        else if (retracting) {
            currentRadius = Math.max(0.0F, currentRadius - delta * RETRACT_PER_TICK);
            if (currentRadius <= 0.0F) {
                retracting = false;
                casterUuid = null;
                standUuid = null;
                maxRadius = 0.0F;
            }
        }
        lastUpdateGameTime = now;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void renderSolid(RenderLivingEvent.Pre<?, ?> event, LivingEntity entity, float pt) {
        LivingEntityRenderer renderer = event.getRenderer();
        EntityModel model = renderer.getModel();
        PoseStack ms = event.getPoseStack();
        float limbSwing = entity.walkAnimation.position(pt);
        float limbSwingAmount = entity.walkAnimation.speed(pt);
        float ageInTicks = entity.tickCount + pt;
        float bodyYaw = Mth.rotLerp(pt, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(pt, entity.yHeadRotO, entity.yHeadRot);
        float pitch = Mth.lerp(pt, entity.xRotO, entity.getXRot());
        ms.pushPose();
        Vec3 off = renderer.getRenderOffset(entity, pt);
        ms.translate(off.x, off.y, off.z);
        ms.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        ms.scale(-1.0F, -1.0F, 1.0F);
        ms.translate(0.0, -1.501, 0.0);
        model.young = entity.isBaby();
        model.riding = entity.isPassenger();
        model.attackTime = 0.0F;
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, headYaw - bodyYaw, pitch);
        VertexConsumer vb = event.getMultiBufferSource().getBuffer(SmellRenderType.SMELL_VISION);
        model.renderToBuffer(ms, vb, 15728880, OverlayTexture.NO_OVERLAY, 0xFF000000 | smellColor(entity));
        ms.popPose();
    }

    private static int smellColor(LivingEntity entity) {
        boolean scared = false;
        if (entity instanceof AbstractVillager || entity instanceof Animal) {
            if (!entity.level().getEntitiesOfClass(Monster.class, entity.getBoundingBox().inflate(8.0),
                    m -> m.isAlive() && m.getTarget() == entity).isEmpty()) {
                scared = true;
            }
        }
        if (entity instanceof Mob mob && mob.getTarget() == null) {
            Player nearest = entity.level().getNearestPlayer(entity, 16.0);
            if (nearest != null && nearest.isShiftKeyDown()) {
                scared = true;
            }
        }
        boolean tense = entity.getHealth() < entity.getMaxHealth() * 0.5F
                || entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || entity.isOnFire()
                || entity.isInWater();
        if (entity instanceof Player player && player.isCrouching()) {
            tense = true;
        }
        if (scared || tense) {
            return 0xFFFFFF;
        }
        boolean angry = false;
        if (entity instanceof Mob mob) {
            if (mob.getTarget() != null) {
                angry = true;
            }
            if (!angry && mob instanceof Monster) {
                LivingEntity nearby = entity.level().getNearestEntity(LivingEntity.class,
                        TargetingConditions.forCombat().range(12.0), mob,
                        mob.getX(), mob.getEyeY(), mob.getZ(), new AABB(mob.blockPosition()).inflate(12.0));
                if (nearby != null && nearby != mob && !(nearby instanceof Monster)) {
                    angry = true;
                }
            }
        }
        if (angry) {
            return 0xFF2222;
        }
        boolean weak = entity.getHealth() < entity.getMaxHealth() * 0.2F
                || entity.hasEffect(MobEffects.HUNGER)
                || entity.hasEffect(MobEffects.POISON)
                || entity.hasEffect(MobEffects.WITHER);
        if (weak) {
            return 0x777777;
        }
        if (entity instanceof Animal) {
            return 0x7CFF7C;
        }
        return entity instanceof Player ? 0x55DDFF : 0x8A7CFF;
    }

    @Nullable
    private static LivingEntity findLivingByUuid(ClientLevel level, @Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && uuid.equals(entity.getUUID())) {
                return living;
            }
        }
        return null;
    }

    private static final class SmellRenderType extends RenderType {
        private static final ResourceLocation WHITE_TEX = HighwayStarAddon.id("textures/misc/white.png");
        // 1.16 entityTranslucent(white) with no depth test, no depth write and no diffuse lighting
        static final RenderType SMELL_VISION = create("highway_star_smell", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(WHITE_TEX, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setOverlayState(OVERLAY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private SmellRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }
    }
}
