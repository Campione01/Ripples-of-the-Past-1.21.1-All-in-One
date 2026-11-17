package rotp.addons.sexpistols.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.client.standskin.StandSkin;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;

import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsIdleChoice;
import net.minecraft.util.Mth;

/**
 * The idle a member shows, after the core has posed its plain idle: Next Album 0.1.13 SexPistolsModel.idleName (idle2 on
 * an odd idle variant) and its IDLE_RANDOM_POSE (JsonActionAnimation "idle_random", blended in over 3 ticks) with the
 * recovery after it (applyRecovery: the clip's last frame blended out over 8 ticks).
 */
final class SexPistolsIdlePose {
    private static final String IDLE2 = "idle2";
    private static final String IDLE_RANDOM = "idle_random";
    private static final String SUMMON = "summon";

    private SexPistolsIdlePose() {}

    static void apply(SexPistolsEntity entity, SexPistolsRenderState renderState, float partialTick) {
        StandSkin skin = renderState.skin;
        if (skin == null || renderState.action.pose == null
                || renderState.action.animId != null && !renderState.action.animId.isIdle()
                || coreSummonPlays(entity, skin, partialTick)) {
            return;
        }
        AnimMolangVariables molang = AnimMolangVariables.extract(entity, partialTick);
        AnimFramePose idle = renderState.action.pose;
        boolean changed = false;
        RotpAnimDefinition idle2 = entity.getIdleVariant() % SexPistolsIdleChoice.IDLE_VARIANTS == 1
                ? clip(skin, IDLE2) : null;
        if (idle2 != null) {
            float idleTicks = entity.tickCount - entity.nonIdlePoseTimeStamp + partialTick;
            idle = idle2.calcAnimPose(molang, null, idle2.getAnimTime(idleTicks), 1.0F).deepCopy();
            changed = true;
        }
        RotpAnimDefinition random = clip(skin, randomName(entity.getIdleRandomVariant()));
        int randomTicks = entity.getIdleRandomTicks();
        int recoveryTicks = entity.getIdleRecoveryTicks();
        if (random != null && (randomTicks > 0 || recoveryTicks > 0)) {
            float seconds;
            float blend;
            if (randomTicks > 0) {
                float elapsed = Mth.clamp(SexPistolsIdleChoice.IDLE_RANDOM_TICKS - randomTicks + partialTick,
                        0.0F, SexPistolsIdleChoice.IDLE_RANDOM_TICKS);
                seconds = Mth.clamp(elapsed / SexPistolsIdleChoice.IDLE_RANDOM_TICKS, 0.0F, random.lengthInSeconds);
                blend = smooth(elapsed / 3.0F);
            }
            else {
                seconds = random.lengthInSeconds;
                blend = smooth((recoveryTicks + 1.0F - partialTick) / (SexPistolsIdleChoice.IDLE_RECOVERY_TICKS + 1));
            }
            AnimFramePose target = random.calcAnimPose(molang, null, seconds, 1.0F).deepCopy();
            idle = lerp(idle, target, blend);
            changed = true;
        }
        if (changed) {
            idle.blendWeight = renderState.action.pose.blendWeight;
            renderState.action.pose = idle;
        }
    }

    // donor animationNames sorts by the first number in the name, which idle_random lacks and counts as 1
    static String randomName(int variant) {
        int index = Math.floorMod(variant, SexPistolsIdleChoice.IDLE_RANDOM_VARIANTS);
        return index == 0 ? IDLE_RANDOM : IDLE_RANDOM + (index + 1);
    }

    // Next Album SexPistolsModel.applyRecovery for a kick (recovery type 2): after the kick, its last frame blends out over
    // 8 ticks (SexPistolsEntity.getRecoveryAnimationBlend), so the member does not snap back to its idle
    private static final int KICK_RECOVERY_TICKS = 8;
    private static final Map<SexPistolsEntity, int[]> LAST_KICK = new WeakHashMap<>();

    static void kickSeen(SexPistolsEntity entity, int variant) {
        LAST_KICK.put(entity, new int[] {entity.tickCount, variant});
    }

    static void blendKickOut(SexPistolsEntity entity, SexPistolsRenderState renderState, float partialTick) {
        int[] kick = LAST_KICK.get(entity);
        if (kick == null || renderState.skin == null || renderState.action.pose == null) {
            return;
        }
        float since = entity.tickCount + partialTick - (kick[0] + 1);
        if (since >= KICK_RECOVERY_TICKS || since < 0) {
            if (since >= KICK_RECOVERY_TICKS) {
                LAST_KICK.remove(entity);
            }
            return;
        }
        RotpAnimDefinition clip = clip(renderState.skin, "kick" + kick[1]);
        if (clip == null) {
            return;
        }
        AnimFramePose last = clip.calcAnimPose(AnimMolangVariables.extract(entity, partialTick), null,
                SexPistolsEntity.KICK_DURATION_TICKS / 20.0F, 1.0F).deepCopy();
        AnimFramePose blended = lerp(renderState.action.pose, last, smooth((KICK_RECOVERY_TICKS - since) / KICK_RECOVERY_TICKS));
        blended.blendWeight = renderState.action.pose.blendWeight;
        renderState.action.pose = blended;
    }

    // as PreFrameEntityAnimCalc: the core's summon clip plays from the Stand's first tick to its end
    private static boolean coreSummonPlays(SexPistolsEntity entity, StandSkin skin, float partialTick) {
        RotpAnimDefinition summon = skin.getStandAnimation(
                anims -> anims.getSummonAnim(SUMMON, entity.getSummonPoseRandomByte()));
        return summon != null && entity.tickCount + partialTick <= summon.lengthInSeconds * 20.0F;
    }

    @Nullable
    private static RotpAnimDefinition clip(StandSkin skin, String name) {
        ActionAnimIdentifier id = ActionAnimIdentifier.getOrCreate(name, false);
        return skin.getStandAnimation(anims -> anims.getNamedAnim(id));
    }

    private static float smooth(float raw) {
        float t = Mth.clamp(raw, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static AnimFramePose lerp(AnimFramePose from, AnimFramePose to, float amount) {
        AnimFramePose out = new AnimFramePose();
        Set<String> bones = new HashSet<>(from.pose.keySet());
        bones.addAll(to.pose.keySet());
        ModelPartFrame rest = new ModelPartFrame();
        for (String bone : bones) {
            ModelPartFrame a = from.pose.getOrDefault(bone, rest);
            ModelPartFrame b = to.pose.getOrDefault(bone, rest);
            ModelPartFrame mixed = out.getForModelPart(bone);
            lerp(a.rotationOffset, b.rotationOffset, amount, mixed.rotationOffset);
            lerp(a.positionOffset, b.positionOffset, amount, mixed.positionOffset);
            lerp(a.scaleOffset, b.scaleOffset, amount, mixed.scaleOffset);
        }
        return out;
    }

    private static void lerp(Vector3f a, Vector3f b, float amount, Vector3f dest) {
        dest.set(Mth.lerp(amount, a.x, b.x), Mth.lerp(amount, a.y, b.y), Mth.lerp(amount, a.z, b.z));
    }
}
