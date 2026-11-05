package rotp.addons.rhcp.client;

import java.util.Map;
import java.util.WeakHashMap;

import rotp.core.client.entityanim.barrage.BarrageSwings;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandGlowLayer;
import rotp.core.client.standskin.StandSkin;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.util.objects_java.LazyNullable;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.client.entityrender.stand.StandEntityRenderState;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

public final class RhcpRenderer extends StandEntityRenderer<
        RhcpStandEntity, RhcpRenderState, RhcpModel> {
    private static final ResourceLocation POWERED =
            RhcpAddon.id("textures/entity/stand/rhcp.png");
    private static final ResourceLocation UNPOWERED =
            RhcpAddon.id("textures/entity/stand/rhcp_off.png");
    private final Map<RhcpStandEntity, Float> idleLoopStarts = new WeakHashMap<>();
    // 1.16 LegacyStandAnimator SUMMON_ANIMATION_LENGTH and RHCPModel's three summon poses.
    private static final float SUMMON_POSE_TICKS = 20.0F;
    private static final int SUMMON_POSE_COUNT = 3;

    public RhcpRenderer(EntityRendererProvider.Context context) {
        super(context);
        missingSkinModel = LazyNullable.of(() ->
                new RhcpModel(RhcpModel.createBodyLayer().bakeRoot()));
        // The generic skin glow is always powered; RHCP owns the power-state gate.
        layers.removeIf(layer -> layer instanceof StandGlowLayer<?, ?, ?>);
        addLayer(new RhcpGlowLayer(this));
    }

    @Override
    public RhcpRenderState createRenderState() {
        return new RhcpRenderState();
    }

    @Override
    public RhcpModel createStandModel(LayerDefinition definition) {
        return new RhcpModel(RhcpModel.createBodyLayer().bakeRoot());
    }

    @Override
    public void extractRenderState(
            RhcpStandEntity entity,
            RhcpRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        if (entity.getUser() != null) {
            var state = RhcpAddon.state(entity.getUser());
            renderState.potency = state.potency();
            renderState.inConductor = state.inConductor();
        }
        else {
            renderState.potency = 0;
            renderState.inConductor = false;
        }
        renderState.mainArmLeft = entity.getMainArm() == HumanoidArm.LEFT;
        extractRhcpAction(entity, renderState, partialTick);
        extractRhcpIdle(entity, renderState);
        // RhcpModel plays the donor's own 1.16 poses for every RHCP move. The core's generic
        // humanoid clips were made for a head that turns with the torso; RHCP's head does not.
        if (renderState.actionName.startsWith("rhcp_")) {
            renderState.action.pose = null;
        }
        renderState.summonPose = renderState.actionName.isEmpty()
                && !entity.isArmsOnlyMode()
                && renderState.ageInTicks <= SUMMON_POSE_TICKS
                ? Math.floorMod(entity.getSummonPoseRandomByte(), SUMMON_POSE_COUNT) : -1;
        dropLeftoverBarrageSwings(renderState);
    }

    // The core ages barrage afterimages only on frames the Stand plays a clip on, and RHCP's
    // idle has none, so the swings left when a barrage ends would stay on the model for good.
    private static void dropLeftoverBarrageSwings(RhcpRenderState renderState) {
        BarrageSwings swings = renderState.action.barrageSwings;
        if (swings != null && swings.hasSmthToRender()
                && !"rhcp_barrage".equals(renderState.actionName)) {
            swings.barrageSwings.clear();
        }
    }

    private void extractRhcpIdle(RhcpStandEntity entity, RhcpRenderState state) {
        state.floating = (!entity.isManuallyControlled() && entity.followingUserIsEnabled())
                || entity.isNoGravity();
        var motion = entity.getDeltaMovement();
        // Either axis counts as walking. 1.16 asked for both, which left the legs
        // idle on a walk along one axis. Remote manual movement syncs position.
        if (entity.isManuallyControlled() && !entity.isControlledByLocalInstance()) {
            double deltaX = entity.getX() - entity.xo;
            double deltaZ = entity.getZ() - entity.zo;
            state.moving = (deltaX != 0.0D || deltaZ != 0.0D)
                    && state.walkAnimationSpeed > 0.0F;
        } else {
            state.moving = motion.x != 0.0D || motion.z != 0.0D;
        }
        // Manual control clears the user's sneak; read the manual Shift.
        state.userCrouching = entity.isUserCrouching();
        state.walkSpeedFactor = 1.0F;
        if (entity.getFallFlyingTicks() > 4) {
            float factor = (float) motion.lengthSqr() / 0.2F;
            state.walkSpeedFactor = Math.max(1.0F, factor * factor * factor);
        }
        // 1.16 StandEntityModel.onPose restarted the idle loop only under a pose of its own.
        if (RhcpModel.hasDonorPose(state.actionName)
                || state.actionName.isEmpty() && state.ageInTicks <= SUMMON_POSE_TICKS) {
            idleLoopStarts.put(entity, state.ageInTicks);
        }
        state.idleLoopTick = state.ageInTicks - idleLoopStarts.getOrDefault(entity, 0.0F);
    }

    private static void extractRhcpAction(
            RhcpStandEntity entity,
            RhcpRenderState renderState,
            float partialTick) {
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(entity);
        if (action == null
                || !(action.ability instanceof Ability ability)
                || action.getPhase() == null) {
            renderState.actionName = "";
            renderState.actionPhase = null;
            renderState.actionPhaseRatio = 0.0F;
            renderState.actionPhaseTick = 0.0F;
            renderState.mirrorAction = false;
            renderState.attackAlternate = false;
            renderState.comboAlternate = false;
            return;
        }

        String actionName = ability.getAbilityId().nameInMoveset();
        float phaseLength = action.getAnimPhaseLength();
        float phaseTick = action.getAnimPhaseTick(partialTick);
        renderState.actionName = actionName;
        renderState.actionPhase = action.getPhase();
        renderState.actionPhaseTick = Math.max(0.0F, phaseTick);
        renderState.actionPhaseRatio = phaseLength > 0.0F
                ? Mth.clamp(phaseTick / phaseLength, 0.0F, 1.0F)
                : 1.0F;
        // 1.16 ModelPoseSided took the punching arm's side, the main hand's side being the main arm.
        renderState.mirrorAction =
                (entity.getCurrentPunchingHand() == InteractionHand.OFF_HAND)
                        != renderState.mainArmLeft;
        renderState.attackAlternate = entity.isAttackAlternate();
        renderState.comboAlternate =
                "rhcp_combo_punch".equals(actionName)
                        && renderState.potency >= 4;
    }

    @Override
    public ResourceLocation getTextureLocation(
            RhcpRenderState renderState) {
        ResourceLocation base = renderState.potency > 0
                ? POWERED : UNPOWERED;
        StandSkin skin = renderState.skin;
        return skin != null ? skin.getTexture(base, base) : base;
    }
}
