package rotp.core.mechanics.jojopose;

import javax.annotation.Nullable;

import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.core.JojoMod;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.entityaction.type.SpecialEntityActionType;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The official special action {@code jojo_pose}: the performer holds one pose of a pose set ({@link JojoPoseSets}) in its
 * own action slot, synced to itself and every player tracking it, until it moves, jumps, sneaks, swings its arm or starts
 * a Stand-power action of its own (official JojoPoseActionType: swinging or any movement key). Any other action may
 * replace it. As the pose starts, its voice line is said through the voice packs ({@link JojoPoseSets#voiceTrigger}).
 */
public class JojoPoseAction extends SpecialEntityActionType {
	public static final ResourceLocation ID = JojoMod.resLoc("jojo_pose");
	public static final int MAX_POSE_NAME_LENGTH = 64;

	public JojoPoseAction(ResourceLocation id) {
		super(null, id);
	}

	public static JojoPoseAction get() {
		return (JojoPoseAction) ModSpecialActions.JOJO_POSE.get();
	}

	@Override
	public EntityActionInstance createActionObj() {
		return new PosingInstance(this);
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return action instanceof PosingInstance posing ? posing.animId : null;
	}

	@Override
	@Nullable
	public ResourceLocation getEntityAnimSet(LivingEntity user) {
		PosingInstance posing = getPose(user);
		return posing != null ? posing.poseSet : null;
	}


	/** The pose the entity holds, if any. */
	@Nullable
	public static PosingInstance getPose(@Nullable LivingEntity entity) {
		EntityActionInstance action = entity != null ? LivingComponentAction.getCurEntityAction(entity) : null;
		return action instanceof PosingInstance posing && !posing.isOver() ? posing : null;
	}

	/**
	 * Whether the entity may strike a pose now: alive, awake, not a spectator, its own slot free or holding a pose, and no
	 * Stand-power action of its own running.
	 */
	public static boolean canPose(LivingEntity entity) {
		if (!entity.isAlive() || entity.isSpectator() || entity.isSleeping()) {
			return false;
		}
		EntityActionInstance own = LivingComponentAction.getCurEntityAction(entity);
		if (own != null && !own.isOver() && !(own instanceof PosingInstance)) {
			return false;
		}
		return !performsStandPowerAction(entity);
	}

	/** Starts a pose on the server. The pose's clip is client data: the server checks only the names. */
	public static boolean start(LivingEntity entity, ResourceLocation poseSet, String pose) {
		if (entity.level().isClientSide() || !JojoPoseSets.isValidPoseName(pose) || !canPose(entity)) {
			return false;
		}
		PosingInstance posing = new PosingInstance(get(), poseSet, pose);
		LivingComponentAction.getComponent(entity).setAction(posing, entity, SyncType.TRACKING_AND_SELF);
		return getPose(entity) == posing;
	}

	/** Ends the entity's pose on the server, if it holds one. */
	public static boolean stop(LivingEntity entity) {
		PosingInstance posing = getPose(entity);
		if (entity.level().isClientSide() || posing == null) {
			return false;
		}
		posing.forceStop();
		posing.syncPhaseChanges();
		return true;
	}

	/** The official end rule: an arm swing or a movement key; ours adds a Stand-power action the poser performs itself. */
	public static boolean endsPose(LivingEntity performer) {
		if (performer.swinging) {
			return true;
		}
		if (performer instanceof Player) {
			PlayerMovementInputData input = PlayerMovementInputData.get(performer);
			if (input != null && (input.jumping || input.shiftKeyDown || input.left != 0 || input.forward != 0)) {
				return true;
			}
		}
		return performsStandPowerAction(performer);
	}

	private static boolean performsStandPowerAction(LivingEntity entity) {
		EntityActionInstance standAction = LivingComponentAction.getCurEntityAction(entity, PowerClass.STAND);
		return standAction != null && !standAction.isOver() && standAction.getPerformer() == entity;
	}

	/**
	 * The voice packs' trigger of a pose, as a sound event no registry holds: packs name it, nothing plays it itself. Not
	 * cached: the names come from clients.
	 */
	public static Holder<SoundEvent> voiceTriggerHolder(ResourceLocation trigger) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(trigger));
	}


	public static class PosingInstance extends EntityActionInstance {
		public ResourceLocation poseSet;
		public String poseName;
		private ActionAnimIdentifier animId;

		protected PosingInstance(EntityActionType type) {
			super(type);
			phasesLength.put(ActionPhase.PERFORM, Float.MAX_VALUE);
		}

		public PosingInstance(EntityActionType type, ResourceLocation poseSet, String poseName) {
			this(type);
			setPose(poseSet, poseName);
			setStartingPhase();
		}

		private void setPose(ResourceLocation poseSet, String poseName) {
			this.poseSet = poseSet;
			this.poseName = poseName;
			// the pose's own name, with no variant number split off ("volume13"), and not interned: the names come from clients
			this.animId = new ActionAnimIdentifier(poseName, 0, false);
		}

		public JojoPoseSets.PoseRef pose() {
			return new JojoPoseSets.PoseRef(poseSet, poseName);
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			buf.writeResourceLocation(poseSet);
			buf.writeUtf(poseName, MAX_POSE_NAME_LENGTH);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			setPose(buf.readResourceLocation(), buf.readUtf(MAX_POSE_NAME_LENGTH));
		}

		@Override
		public void extractAnim(LivingAnimState animVariables,
				LivingEntity performer, float partialTick) {
			super.extractAnim(animVariables, performer, partialTick);
			animVariables.animSet = poseSet;
		}

		@Override
		public void actionPerformStart() {
			LivingEntity performer = getPerformer();
			if (performer != null && !level().isClientSide()) {
				JojoModUtil.sayVoiceLine(performer, null, voiceTriggerHolder(pose().voiceTrigger()), 1.0F, 1.0F, 0, false);
			}
		}

		@Override
		public void actionTick() {
			LivingEntity performer = getPerformer();
			if (performer != null && !level().isClientSide() && endsPose(performer)) {
				forceStop();
				syncPhaseChanges();
			}
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return true;
		}
	}
}
