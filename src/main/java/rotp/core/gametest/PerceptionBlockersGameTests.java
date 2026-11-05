package rotp.core.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;

import rotp.core.api.perception.PerceptionBlockers;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * PerceptionBlockers is asked where the core takes an entity as a target: HitResultUtil's rays go through an entity
 * the viewer cannot perceive, the aim a client sends drops it, a Stand cannot pick it, and the time-stop blink does
 * not land next to one its user cannot perceive in the instant stop. The test's rule only blocks for a viewer and a
 * target carrying its tags, so it changes nothing for the other tests once registered.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerceptionBlockersGameTests {
	private static final String VIEWER = "jojo_ripples.gametest.perception_viewer";
	private static final String UNSEEN = "jojo_ripples.gametest.unseen";
	private static final String UNSEEN_IN_BLINK = "jojo_ripples.gametest.unseen_in_blink";
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private PerceptionBlockersGameTests() {}

	@GameTest(template = "empty")
	public static void anEntityItsViewerCannotPerceiveIsNeitherAimedAtNorStruckNorLandedBy(GameTestHelper helper) {
		if (REGISTERED.compareAndSet(false, true)) {
			PerceptionBlockers.register(JojoMod.resLoc("gametest_perception"), (viewer, target, sight) -> {
				Entity looker = viewer instanceof StandEntity stand && stand.getUser() != null ? stand.getUser() : viewer;
				return looker != null && looker.getTags().contains(VIEWER) && (target.getTags().contains(UNSEEN)
						|| sight == PerceptionBlockers.Sight.INSTANT_TIME_STOP && target.getTags().contains(UNSEEN_IN_BLINK));
			});
		}
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PerceptionViewer"));
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 1)));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		user.addTag(VIEWER);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the user");
		// close together: the neighbour cells of a one-block template are a few blocks away
		Cow unseen = cow(helper, origin.add(0, 0, 1.6), UNSEEN);
		Cow behind = cow(helper, origin.add(0, 0, 3.2), null);
		Cow unseenInBlink = cow(helper, origin.add(2.4, 0, 0), UNSEEN_IN_BLINK);
		try {
			List<Entity> cows = List.of(unseen, behind, unseenInBlink);
			Predicate<Entity> ownCows = cows::contains;
			Vec3 rayStart = origin.add(0, 0.7, 0);
			ActionTarget hit = HitResultUtil.clip(rayStart, new Vec3(0, 0, 1), 4.5, 4.5, helper.getLevel(),
					ownCows, user, 0);
			ActionTarget hitByNobody = HitResultUtil.clip(rayStart, new Vec3(0, 0, 1), 4.5, 4.5, helper.getLevel(),
					ownCows, null, 0);
			List<Entity> rayEntities = HitResultUtil.clipMultipleEntities(rayStart, new Vec3(0, 0, 1), 4.5, helper.getLevel(),
					user, ownCows, 0, 0).stream().map(ActionTarget::getEntity).toList();
			helper.assertTrue(hit.getEntity() == behind && hitByNobody.getEntity() == unseen
					&& rayEntities.contains(behind) && !rayEntities.contains(unseen),
					"The ray went to " + hit.getEntity() + " (for no viewer " + hitByNobody.getEntity() + "), its entities "
							+ rayEntities + ": it must go through the cow its viewer cannot perceive, and only for that viewer");
			ActionTarget aimedInBlinkSight = HitResultUtil.clip(rayStart, new Vec3(1, 0, 0), 4.5, 4.5, helper.getLevel(),
					ownCows, user, 0);
			helper.assertTrue(aimedInBlinkSight.getEntity() == unseenInBlink,
					"A cow hidden only in the instant stop was not aimed at: " + aimedInBlinkSight.getEntity());

			LivingComponentAction component = LivingComponentAction.getComponent(user);
			component.setUserAimFromClient(new ActionTarget(unseen));
			ActionTarget aimUnseen = component.entityAim.getTarget();
			component.setUserAimFromClient(new ActionTarget(behind));
			ActionTarget aimBehind = component.entityAim.getTarget();
			helper.assertTrue(aimUnseen.getType() == ActionTarget.TargetType.EMPTY && aimBehind.getEntity() == behind,
					"The server kept the client's aim at a cow its user cannot perceive: " + aimUnseen.getEntity()
							+ ", at another cow " + aimBehind.getEntity());

			helper.assertTrue(TimeStopBlinkAbility.landsByUnperceived(helper.getLevel(), user, unseenInBlink.position().add(0.8, 0, 0))
					&& !TimeStopBlinkAbility.landsByUnperceived(helper.getLevel(), user, origin.add(-2.5, 0, 0)),
					"The blink's landing spot next to a cow hidden in the instant stop was not refused, or one clear of every cow was");

			StandPower power = PowerClass.STAND.attachGet(user);
			StandType starPlatinum = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(starPlatinum != null && StandPowerTransitions.insert(power, new StandInstance(starPlatinum)).status()
					== StandPowerTransitions.Status.APPLIED && starPlatinum.summon(user, power)
					&& power.getSummonedStandEntity() != null, "Could not summon the user's Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(!StandEntityPunchAbility.canStandPick(stand, unseen) && StandEntityPunchAbility.canStandPick(stand, behind),
					"The Stand could pick the cow its user cannot perceive, or not the other one");
		}
		finally {
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			StandPower power = PowerClass.STAND.get(user);
			if (power != null && power.isSummoned() && power.getPowerType() instanceof StandType type) {
				type.forceUnsummon(user, power);
			}
			unseen.discard();
			behind.discard();
			unseenInBlink.discard();
			user.discard();
		}
		helper.succeed();
	}

	private static Cow cow(GameTestHelper helper, Vec3 pos, String tag) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Could not create a cow");
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		if (tag != null) {
			cow.addTag(tag);
		}
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
		return cow;
	}
}
