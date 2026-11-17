package rotp.addons.beachboy.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entityglow.EntityGlowChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 BeachBoyRemoteSenseClient: with sound sense on, every sound within 128 blocks of the user, its view or its hook
 * shows its subtitle where it was made (5 s), and the creatures and items within 48 blocks glow (moving ones blue,
 * still ones dark blue, the user itself pink while it looks through the hook). Under remote control the world goes
 * black (dense black fog) so that only the senses show.
 */
public final class BeachBoyRemoteSenseClient {
    private static final double SENSE_RANGE = 48.0;
    private static final int MOVING_GLOW_COLOR = 6745855;
    private static final int BREATH_GLOW_COLOR = 3836074;
    private static final int USER_GLOW_COLOR = 16753883;
    private static final int SOUND_LABEL_COLOR = 16737894;
    private static final int SOUND_LABEL_LIFE = 100;
    private static final int MAX_SOUND_LABELS = 48;
    private static final List<SoundLabel> SOUND_LABELS = new ArrayList<>();
    private static boolean wasSenseActive;
    private static boolean wasRemoteVision;

    private BeachBoyRemoteSenseClient() {}

    public static boolean isRemoteVision() {
        Player player = Minecraft.getInstance().player;
        BeachBoyStandEntity stand = player != null ? BeachBoyStandUtil.getStand(player) : null;
        return stand != null && stand.isManuallyControlled();
    }

    public static boolean isSoundSenseActive() {
        Player player = Minecraft.getInstance().player;
        return player != null && BeachBoyStandUtil.isSoundSenseEnabled(StandPower.get(player));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) {
            if (!isSoundSenseActive()) {
                SOUND_LABELS.clear();
            }
            return;
        }
        if (!isSoundSenseActive()) {
            SOUND_LABELS.clear();
            if (wasSenseActive) {
                clearKineticGlow(mc);
                wasSenseActive = false;
            }
            wasRemoteVision = false;
            return;
        }
        wasSenseActive = true;
        boolean remote = isRemoteVision();
        if (wasRemoteVision && !remote) {
            EntityGlowChannel.PILLARMAN_ENHANCED_SENSES.clear(mc.player);
        }
        wasRemoteVision = remote;
        Iterator<SoundLabel> it = SOUND_LABELS.iterator();
        while (it.hasNext()) {
            SoundLabel label = it.next();
            if (++label.age >= label.lifetime) {
                it.remove();
            }
        }
        tickKineticSense(mc);
    }

    public static void onSound(SoundInstance sound) {
        if (!isSoundSenseActive() || sound == null || sound.isRelative()) {
            return;
        }
        SoundSource category = sound.getSource();
        if (category == SoundSource.MUSIC || category == SoundSource.RECORDS || category == SoundSource.MASTER
                || category == SoundSource.AMBIENT || category == SoundSource.WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player user = mc.player;
        if (user == null) {
            return;
        }
        double x = sound.getX();
        double y = sound.getY();
        double z = sound.getZ();
        if (!isWithinHearRange(user, x, y, z)) {
            return;
        }
        WeighedSoundEvents accessor = mc.getSoundManager().getSoundEvent(sound.getLocation());
        Component subtitle = accessor != null ? accessor.getSubtitle() : null;
        if (subtitle == null || subtitle.getString().isEmpty()) {
            return;
        }
        for (SoundLabel existing : SOUND_LABELS) {
            if (existing.age < 4 && existing.title.getString().equals(subtitle.getString())
                    && existing.distanceToSqr(x, y, z) < 0.36) {
                return;
            }
        }
        SOUND_LABELS.add(new SoundLabel(x, y, z, subtitle, SOUND_LABEL_LIFE));
        while (SOUND_LABELS.size() > MAX_SOUND_LABELS) {
            SOUND_LABELS.remove(0);
        }
    }

    public static void renderOverlays(PoseStack poseStack, MultiBufferSource buffer, Vec3 camPos, float partialTicks) {
        if (!isSoundSenseActive() || SOUND_LABELS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) {
            return;
        }
        Font font = mc.font;
        RenderSystem.disableDepthTest();
        for (SoundLabel label : SOUND_LABELS) {
            float life = (label.age + partialTicks) / label.lifetime;
            if (life >= 1.0F) {
                continue;
            }
            int alpha = Mth.clamp((int) ((1.0F - life) * 255.0F), 24, 255);
            int color = alpha << 24 | SOUND_LABEL_COLOR;
            poseStack.pushPose();
            poseStack.translate(label.x - camPos.x, label.y - camPos.y + 0.35, label.z - camPos.z);
            poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            // 1.16's negative x was for its camera; with this one it mirrors the text and culling drops every face.
            poseStack.scale(0.025F, -0.025F, 0.025F);
            Matrix4f pose = poseStack.last().pose();
            float width = -font.width(label.title) / 2.0F;
            int background = Mth.clamp((int) (mc.options.getBackgroundOpacity(0.25F) * alpha), 0, 255) << 24;
            font.drawInBatch(label.title, width, 0.0F, color, false, pose, buffer, Font.DisplayMode.SEE_THROUGH,
                    background, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
        RenderSystem.enableDepthTest();
    }

    private static boolean isWithinHearRange(Player user, double x, double y, double z) {
        double rangeSq = BeachBoyStandUtil.SOUND_HEAR_RANGE * BeachBoyStandUtil.SOUND_HEAR_RANGE;
        if (user.distanceToSqr(x, y, z) <= rangeSq) {
            return true;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera != null && camera != user && camera.distanceToSqr(x, y, z) <= rangeSq) {
            return true;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        return hook != null && hook.distanceToSqr(x, y, z) <= rangeSq;
    }

    private static void tickKineticSense(Minecraft mc) {
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity();
        Player user = mc.player;
        if (level == null || camera == null || user == null || level.getGameTime() % 2L != 0L) {
            return;
        }
        AABB box = user.getBoundingBox().inflate(SENSE_RANGE);
        if (camera != user) {
            box = box.minmax(camera.getBoundingBox().inflate(SENSE_RANGE));
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                target -> isSenseTarget(target, camera, user))) {
            boolean moving = isMovingOrInteracting(target);
            int color = target == user ? USER_GLOW_COLOR : moving ? MOVING_GLOW_COLOR : BREATH_GLOW_COLOR;
            int duration = target == user ? 20 : moving ? 40 : 24;
            EntityGlowChannel.PILLARMAN_ENHANCED_SENSES.apply(target, OptionalInt.of(color), duration);
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
            boolean moving = item.getDeltaMovement().lengthSqr() > 4.0E-4 || !item.onGround();
            EntityGlowChannel.PILLARMAN_ENHANCED_SENSES.apply(item,
                    OptionalInt.of(moving ? MOVING_GLOW_COLOR : BREATH_GLOW_COLOR), moving ? 40 : 24);
        }
    }

    private static void clearKineticGlow(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(mc.player);
        Entity hooked = hook != null ? hook.getHookedEntity() : null;
        AABB box = mc.player.getBoundingBox().inflate(56.0);
        Entity camera = mc.getCameraEntity();
        if (camera != null && camera != mc.player) {
            box = box.minmax(camera.getBoundingBox().inflate(56.0));
        }
        for (Entity target : mc.level.getEntitiesOfClass(Entity.class, box, Entity::isAlive)) {
            if (target != hooked) {
                EntityGlowChannel.PILLARMAN_ENHANCED_SENSES.clear(target);
            }
        }
    }

    private static boolean isSenseTarget(LivingEntity target, Entity camera, Player user) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target == user) {
            return isRemoteVision();
        }
        if (target == camera || target instanceof ArmorStand || target instanceof StandEntity) {
            return false;
        }
        BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(user);
        return hook == null || hook.getHookedEntity() != target;
    }

    private static boolean isMovingOrInteracting(LivingEntity entity) {
        Vec3 vel = entity.getDeltaMovement();
        return vel.x * vel.x + vel.z * vel.z > 0.001 || entity.swinging || vel.y > 0.1;
    }

    private static final class SoundLabel {
        final double x;
        final double y;
        final double z;
        final Component title;
        final int lifetime;
        int age;

        SoundLabel(double x, double y, double z, Component title, int lifetime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.title = title;
            this.lifetime = lifetime;
        }

        double distanceToSqr(double ox, double oy, double oz) {
            double dx = x - ox;
            double dy = y - oy;
            double dz = z - oz;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
