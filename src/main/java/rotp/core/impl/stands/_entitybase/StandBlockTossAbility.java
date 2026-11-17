package rotp.core.impl.stands._entitybase;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.input.AbilityInputState;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityLingeringSoundInstance;
import rotp.core.client.ui.utils.GuiItemIcon;
import rotp.core.customobjects.entity_projectile.ThrownBlockEntity;
import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier.ActionAnimIdHandsided;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.entity_useitem.StandCallbackWhenShooting;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.util.functions.UtilFunctions;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The Stand throws a block it holds, which it took with its grab or was given (the item swap key, its inventory slots). The
 * held key of the punches: while a block is in its hands this takes the hold from the barrage (HIGH_PRIORITY), and without
 * one the ability is not there, so the barrage has the key as ever.
 */
public class StandBlockTossAbility extends StandEntityAbility {
	private static final float STAMINA_COST = 20.0F;
	private static final float THROW_VELOCITY = 1.0F;
	private static final float THROW_INACCURACY = 1.0F;

	private final ActionAnimIdHandsided clipOfTheThrowingArm = new ActionAnimIdHandsided(anim);

	public StandBlockTossAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, Toss::new);
		partsRequired(StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.BUTTON_CHARGE, 16);
		setButtonHoldPhase(ActionPhase.WINDUP);
		setDefaultPhaseLength(ActionPhase.PERFORM, 6);
		setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
	}

	/** A block item, and not one that carries a container's contents, which would be lost with it. */
	public static boolean canBeTossed(ItemStack item) {
		return item.getItem() instanceof BlockItem
				&& item.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).equals(ItemContainerContents.EMPTY);
	}

	@Nullable
	private static InteractionHand handWithABlock(LivingEntity holder) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (canBeTossed(holder.getItemInHand(hand))) {
				return hand;
			}
		}
		return null;
	}

	// a Stand that holds an entity has its hands full: its held key is the grab's barrage, or nothing while that is locked
	@Override
	public boolean isAbilityAvailable(Power<?> context) {
		if (!super.isAbilityAvailable(context)) {
			return false;
		}
		StandEntity stand = StandUtil.getSummonedStand(context);
		return stand != null && handWithABlock(stand) != null && LivingComponentGrab.getEntityGrabbedBy(stand) == null;
	}

	@Override
	protected ConditionCheck checkHeldSpecificConditions(EntityActionInstance action, Power<?> context) {
		return action instanceof Toss toss && toss.throwingHandHoldsABlock() ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
	}

	// let go during its charge it charges on and throws, but is held no more, on the user's client as on the server
	@Override
	public boolean isHeldSincePress(EntityActionInstance action) {
		return super.isHeldSincePress(action) && !(action instanceof Toss toss && toss.isLetGoInCharge());
	}

	@Override
	public AbilityInputState cl_abilityInputState(Power<?> context) {
		return takesTheHeldKey(super.cl_abilityInputState(context));
	}

	/** The flags that let it past the barrage on the held key and keep it there while the Stand holds an item. */
	public static AbilityInputState takesTheHeldKey(AbilityInputState state) {
		state.setFlag(AbilityInputState.WITH_ITEM_HELD, true);
		state.setFlag(AbilityInputState.HIGH_PRIORITY, true);
		return state;
	}

	// the HUD shows what would fly; the skills screen and a locked slot show the ability's own icon
	@Override
	public void renderAbilityIcon(Power<?> context, GuiGraphics guiGraphics, TextureAtlasSprite sprite, float x, float y, int color) {
		StandEntity stand = context != null ? StandUtil.getSummonedStand(context) : null;
		InteractionHand hand = stand != null ? handWithABlock(stand) : null;
		if (hand != null) {
			GuiItemIcon.render(guiGraphics, null, stand.getItemInHand(hand), (int) x, (int) y, color);
		}
		else {
			super.renderAbilityIcon(context, guiGraphics, sprite, x, y, color);
		}
	}

	// the clip is mirrored, so only its _left and _right keys exist
	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return action instanceof Toss toss ? clipOfTheThrowingArm.get(toss.throwingArm) : super.getEntityAnim(action);
	}

	@Override
	public float getStaminaCost(Power<?> context) {
		return STAMINA_COST;
	}

	public static class Toss extends EntityActionInstance implements SyncedDataHolderExtended {
		private static final EntityDataAccessor<Boolean> LET_GO_IN_CHARGE = SynchedEntityData.defineId(
				Toss.class, EntityDataSerializers.BOOLEAN);
		private HumanoidArm throwingArm = HumanoidArm.RIGHT;

		public Toss(EntityActionType ability) {
			super(ability);
		}

		// no performer yet (the starting phase is set before the action is): not pressed, so not let go either
		boolean isLetGoInCharge() {
			return performer != null && synchedData.get(LET_GO_IN_CHARGE);
		}

		boolean throwingHandHoldsABlock() {
			return canBeTossed(performer.getItemInHand(UtilFunctions.getHand(performer, throwingArm)));
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			InteractionHand hand = handWithABlock(performer);
			if (hand != null) {
				throwingArm = UtilFunctions.getHandSide(performer, hand);
			}
		}

		@Override
		public void toBuf(FriendlyByteBuf buf) {
			buf.writeEnum(throwingArm);
		}

		@Override
		public void fromBuf(FriendlyByteBuf buf) {
			throwingArm = buf.readEnum(HumanoidArm.class);
		}

		@Override
		public void onButtonStopHold() {
			if (getPhase() == ActionPhase.BUTTON_CHARGE) {
				phasesLength.put(ActionPhase.WINDUP, 0F);
				synchedData.set(LET_GO_IN_CHARGE, true);
				syncPhaseChanges();
			}
			else if (getPhase() == ActionPhase.WINDUP) {
				if (mayStillThrow()) {
					setPhaseStart(ActionPhase.PERFORM);
				}
				else {
					forceStop();
				}
				syncPhaseChanges();
			}
		}

		// with the key up the held recheck no longer runs, and the charge is about to end in a throw
		@Override
		protected void checkNextPhase() {
			if (isLetGoInCharge() && phase == ActionPhase.BUTTON_CHARGE && getPhaseTick() >= curPhaseLength && !mayStillThrow()) {
				forceStop();
				syncPhaseChanges();
				return;
			}
			super.checkNextPhase();
		}

		private boolean mayStillThrow() {
			return ((EntityActionAbility) ability).canFireReleasedHold(this);
		}

		@Override
		public void actionPerformStart() {
			if (performer instanceof StandEntity stand) {
				setStandOffset(0, Math.max(stand.offsetFromUser.getOffsetBeforeAction().z, 0) + 2,
						StandOffsetFromUser.Rotations.HEAD_XY, false);
				if (level().isClientSide() && ClientGlobals.canHearStand(stand)) {
					ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
							ModSoundEvents.STAND_PUNCH_HEAVY_CRY.get(), stand),
							stand.getSoundSource(), 1, 1, stand, level()));
				}
			}
			aimAs = AimingEntity.STAND;
		}

		@Override
		public void actionPerformEnd() {
			if (level() instanceof ServerLevel level && throwingHandHoldsABlock()) {
				ItemStack block = performer.getItemInHand(UtilFunctions.getHand(performer, throwingArm));
				StandCallbackWhenShooting.shootProjectileWithStandStats(ThrownBlockEntity::new, level, block, performer,
						performer instanceof StandEntity stand ? stand : null, 0, THROW_VELOCITY, THROW_INACCURACY);
				LivingEntity user = getPowerUser();
				// a Creative user keeps the block, as a Stand's other item uses leave it
				block.consume(1, user);
				StandAbilityStamina.consume(ability, user != null ? StandPower.get(user) : null, STAMINA_COST, false);
			}
			aimAs = AimingEntity.CAMERA_ENTITY;
		}

		@Override
		public boolean canBeCancelledInto(EntityActionType cancellingAbility) {
			return phase != null && phase.ordinal() < ActionPhase.PERFORM.ordinal();
		}

		@Override
		public void defineSynchedData(SynchedDataBuilder builder) {
			builder.define(LET_GO_IN_CHARGE, false);
		}

		@Override
		public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}
	}
}
