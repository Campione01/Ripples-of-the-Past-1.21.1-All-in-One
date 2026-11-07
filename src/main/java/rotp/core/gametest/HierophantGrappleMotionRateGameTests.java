package rotp.core.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HGGrappleEntity;
import rotp.core.impl.stands.hierophant.HierophantGrappleAbility;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HierophantGrappleMotionRateGameTests {
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();
	private static final String MOTION_SLOWED = "jojo_ripples.gametest.hg_pull_motion";
	private static final String BEING_SLOWED = "jojo_ripples.gametest.hg_pull_being";
	private static final String MOTION_FAST = "jojo_ripples.gametest.hg_pull_fast";

	private HierophantGrappleMotionRateGameTests() {}

	@GameTest(template = "empty", batch = "hierophant_grapple_motion_rate")
	public static void blockGrappleUsesTheMotionRateOfTheBodyItPulls(GameTestHelper helper) {
		register();
		try (Scene scene = new Scene(helper)) {
			HGGrappleEntity grapple = scene.start("grapple");
			helper.assertTrue(scene.stand.isFollowingUser(), "The block grapple must pull the following Stand's user");
			BlockPos anchor = BlockPos.containing(scene.stand.position().add(5.0, 0.0, 0.0));
			BlockState original = scene.level.getBlockState(anchor);
			Pig mount = scene.spawnPig(scene.user.position().add(0.0, 0.0, 2.0));
			try {
				scene.level.setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());
				grapple.attachToBlockPos(anchor);
				Vec3 direction = Vec3.atCenterOf(anchor).subtract(scene.stand.position()).normalize();
				assertBlockPull(scene, grapple, scene.user, direction.scale(2.0), "normal user");
				scene.user.addTag(MOTION_FAST);
				assertBlockPull(scene, grapple, scene.user, direction.scale(2.0), "faster user");
				scene.user.removeTag(MOTION_FAST);
				scene.user.addTag(MOTION_SLOWED);
				assertBlockPull(scene, grapple, scene.user, direction, "slowed user");

				helper.assertTrue(scene.user.startRiding(mount, true) && scene.user.getRootVehicle() == mount,
						"Setup: the mount must be the moved body");
				mount.addTag(BEING_SLOWED);
				assertBlockPull(scene, grapple, mount, direction.scale(2.0), "BEING-only mount of a slowed user");
				mount.addTag(MOTION_SLOWED);
				assertBlockPull(scene, grapple, mount, direction, "MOTION-slowed mount");
			}
			finally {
				scene.user.stopRiding();
				mount.discard();
				scene.level.setBlockAndUpdate(anchor, original);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "hierophant_grapple_motion_rate")
	public static void attachedGrappleUsesBeingRateOnlyWhenItMovesTheTargetDirectly(GameTestHelper helper) {
		register();
		try (Scene scene = new Scene(helper)) {
			HGGrappleEntity grapple = scene.start("grapple_entity");
			scene.user.addTag(MOTION_SLOWED);
			scene.stand.addTag(MOTION_SLOWED);
			Vec3 start = scene.stand.position().add(6.0, 0.0, 0.0);
			Pig target = scene.spawnPig(start);
			try {
				grapple.attachToEntity(target);
				assertTargetPull(scene, grapple, target, start, 1.0, 0.0, "normal target of a slowed puller");
				target.addTag(BEING_SLOWED);
				assertTargetPull(scene, grapple, target, start, 1.0, 0.0, "BEING-only velocity");
				target.addTag(MOTION_SLOWED);
				assertTargetPull(scene, grapple, target, start, 0.5, 0.0, "MOTION and BEING velocity");
				target.addEffect(new MobEffectInstance(ModStatusEffects.IMMOBILIZE, 100));
				helper.assertTrue(target.hasEffect(ModStatusEffects.IMMOBILIZE), "Setup: target must be immobilized");
				assertTargetPull(scene, grapple, target, start, 0.0, 0.25, "MOTION and BEING direct move");
				target.removeTag(MOTION_SLOWED);
				assertTargetPull(scene, grapple, target, start, 0.0, 0.25, "BEING-only direct move");
				target.removeTag(BEING_SLOWED);
				target.addTag(MOTION_SLOWED);
				assertTargetPull(scene, grapple, target, start, 0.0, 0.5, "MOTION-only direct move");
				target.removeTag(MOTION_SLOWED);
				assertTargetPull(scene, grapple, target, start, 0.0, 1.0, "normal direct move");
			}
			finally {
				target.discard();
			}
		}
		helper.succeed();
	}

	private static void assertBlockPull(Scene scene, HGGrappleEntity grapple, Entity moved, Vec3 expected, String what) {
		moved.setDeltaMovement(Vec3.ZERO);
		scene.level.tickNonPassenger(grapple);
		scene.helper.assertTrue(grapple.isAlive() && moved.getDeltaMovement().distanceTo(expected) < 1.0E-6,
				"Block grapple on " + what + ": expected velocity " + expected + ", got " + moved.getDeltaMovement());
	}

	private static void assertTargetPull(Scene scene, HGGrappleEntity grapple, Pig target, Vec3 start,
			double speed, double displacement, String what) {
		target.setPos(start.x, start.y, start.z);
		target.setDeltaMovement(Vec3.ZERO);
		scene.level.tickNonPassenger(grapple);
		Vec3 velocity = target.getDeltaMovement();
		Vec3 moved = target.position().subtract(start);
		scene.helper.assertTrue(grapple.isAlive() && grapple.getEntityAttachedTo() == target
						&& velocity.distanceTo(new Vec3(-speed, 0.0, 0.0)) < 1.0E-6
						&& moved.distanceTo(new Vec3(-displacement, 0.0, 0.0)) < 1.0E-6,
				"Attached grapple on " + what + ": expected velocity/displacement " + speed + "/" + displacement
						+ " toward Stand, got " + velocity + "/" + moved);
	}

	private static void register() {
		if (REGISTERED.compareAndSet(false, true)) {
			EntityTimeRates.register(JojoMod.resLoc("gametest_hg_pull"), (entity, channel) -> switch (channel) {
				case MOTION -> entity.getTags().contains(MOTION_SLOWED) ? 0.5F
						: entity.getTags().contains(MOTION_FAST) ? 2.0F : EntityTimeRates.NO_RATE;
				case BEING -> entity.getTags().contains(BEING_SLOWED) ? 0.25F : EntityTimeRates.NO_RATE;
				default -> EntityTimeRates.NO_RATE;
			});
		}
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final Player user;
		private StandType type;
		private StandPower power;
		private StandEntity stand;
		private List<HGGrappleEntity> projectiles = List.of();

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		}

		private HGGrappleEntity start(String name) {
			Vec3 start = helper.absoluteVec(new Vec3(1.5, 8.0, 1.5));
			user.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the grapple user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("hierophant_green"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
							== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
					"Could not summon Hierophant Green");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing Hierophant Green or its user");
			stand.moveTo(start.x + 1.0, start.y, start.z, 0.0F, 0.0F);
			var ability = power.getAbility(name);
			helper.assertTrue(ability instanceof HierophantGrappleAbility, "Missing registered " + name);
			EntityActionInstance action = ((HierophantGrappleAbility) ability).initActionOnAbilityUse(level, user, stand, null);
			stand.getStandActionComponent().setAction(action, user, SyncType.NO_SYNC);
			action.setPhaseStart(ActionPhase.PERFORM);
			helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"Setup: expected the matching held grapple action");
			action.actionPerformStart();
			projectiles = level.getEntitiesOfClass(HGGrappleEntity.class, stand.getBoundingBox().inflate(8.0),
					grapple -> grapple.getOwner() == stand);
			helper.assertTrue(projectiles.size() == 1 && projectiles.getFirst().isBindEntities() == name.equals("grapple_entity"),
					"Expected one matching grapple from its registered action: " + projectiles.size());
			return projectiles.getFirst();
		}

		private Pig spawnPig(Vec3 at) {
			Pig pig = EntityType.PIG.create(level);
			helper.assertTrue(pig != null, "Could not create grapple test pig");
			pig.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(pig), "Could not add grapple test pig");
			return pig;
		}

		@Override
		public void close() {
			projectiles.forEach(HGGrappleEntity::discard);
			if (stand != null) {
				stand.getStandActionComponent().setAction(null, SyncType.NO_SYNC);
			}
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
