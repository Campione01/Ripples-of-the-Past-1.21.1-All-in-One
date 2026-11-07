package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonZoomPunchEntity;
import rotp.core.impl.powers.pillarman.PillarmanHornEntity;
import rotp.core.impl.stands.hierophant.HGStringEntity;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A body part bound to its owner runs in its owner's time (EntityTimeRates STAND_ACTIONS, a Stand's by its user): Made
 * in Heaven's mode A left a slowed player's Zoom Punch arm flying at full speed and clearing the cooldown as it came
 * back. In mode B the server already ticks the part m times a tick (ENTITY_TICK): the owner's rate m must not count again.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OwnerTimeBodyPartGameTests {
	private static final String SLOWED = "jojo_ripples_gametest_owner_time_slowed";
	private static final String BEING_SLOWED = "jojo_ripples_gametest_owner_time_being_slowed";
	private static final String SPED_UP = "jojo_ripples_gametest_owner_time_sped_up";
	private static final String ZOOM_PUNCH = "zoom_punch";
	private static final int COOLDOWN = 14;
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private OwnerTimeBodyPartGameTests() {}

	private static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		EntityTimeRates.register(JojoMod.resLoc("gametest_owner_time"), (entity, channel) -> {
			if (channel == EntityTimeRates.Channel.STAND_ACTIONS) {
				return entity.getTags().contains(SLOWED) ? 1.0F / 3.0F
						: entity.getTags().contains(SPED_UP) ? 3.0F : EntityTimeRates.NO_RATE;
			}
			if (channel == EntityTimeRates.Channel.BEING && entity.getTags().contains(BEING_SLOWED)) {
				return 0.5F;
			}
			if (channel == EntityTimeRates.Channel.ENTITY_TICK && entity instanceof OwnerBoundProjectileEntity part) {
				LivingEntity owner = part.getOwner();
				LivingEntity user = owner instanceof StandEntity stand ? stand.getUser() : owner;
				return user != null && user.getTags().contains(SPED_UP) ? 3.0F : EntityTimeRates.NO_RATE;
			}
			return EntityTimeRates.NO_RATE;
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aZoomPunchFliesAndEndsItsCooldownInItsUsersTime(GameTestHelper helper) {
		register();
		BlockPos wall = new BlockPos(1, 2, 3);
		helper.setBlock(wall, Blocks.STONE.defaultBlockState());
		helper.setBlock(wall.above(), Blocks.STONE.defaultBlockState());

		Flight air = flyZoomPunch(helper, null, false);
		Flight slowedAir = flyZoomPunch(helper, SLOWED, false);
		Flight fastAir = flyZoomPunch(helper, SPED_UP, false);
		helper.assertTrue(near(slowedAir.distances.get(2), air.distances.get(0)),
				"three server ticks of a user at 1/3 move his arm one tick's distance: " + slowedAir.distances.get(2)
						+ " against " + air.distances.get(0));
		helper.assertTrue(near(fastAir.distances.get(0), air.distances.get(2)),
				"one server tick of a user at 3 moves his arm three ticks' distance, not nine: " + fastAir.distances.get(0)
						+ " against " + air.distances.get(2));
		helper.assertTrue(Math.abs(slowedAir.ticks - 3 * air.ticks) <= 3,
				"an arm at 1/3 lasts 3 times as long: " + slowedAir.ticks + " server ticks against " + air.ticks);
		helper.assertTrue(Math.abs(3 * fastAir.ticks - air.ticks) <= 3,
				"an arm at 3 lasts a third as long: " + fastAir.ticks + " server ticks against " + air.ticks);
		helper.assertTrue(air.cooldownAfter == COOLDOWN && slowedAir.cooldownAfter == COOLDOWN && fastAir.cooldownAfter == COOLDOWN,
				"an arm that flew its whole lifespan leaves the cooldown to run out, in its user's time too: " + air.cooldownAfter
						+ ", at 1/3 " + slowedAir.cooldownAfter + ", at 3 " + fastAir.cooldownAfter);

		Flight atWall = flyZoomPunch(helper, null, true);
		Flight slowedAtWall = flyZoomPunch(helper, SLOWED, true);
		helper.assertTrue(Math.abs(slowedAtWall.ticks - 3 * atWall.ticks) <= 3,
				"an arm back from a wall at 1/3 takes 3 times as long: " + slowedAtWall.ticks + " server ticks against " + atWall.ticks);
		helper.assertTrue(slowedAtWall.cooldowns.get(atWall.ticks - 1) == COOLDOWN,
				"at 1/3 the cooldown is still on when the unslowed arm was back: " + slowedAtWall.cooldowns.get(atWall.ticks - 1));
		helper.assertTrue(atWall.cooldownAfter == 0 && slowedAtWall.cooldownAfter == 0,
				"an arm back early ends the cooldown as it leaves the world: " + atWall.cooldownAfter + ", at 1/3 " + slowedAtWall.cooldownAfter);
		helper.succeed();
	}

	private record Flight(int ticks, int cooldownAfter, List<Double> distances, List<Integer> cooldowns) {}

	private static Flight flyZoomPunch(GameTestHelper helper, String tag, boolean atTheWall) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
		user.moveTo(at.x, at.y, at.z, 0.0F, atTheWall ? 0.0F : -90.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Zoom Punch user");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		HamonZoomPunchEntity punch = null;
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.ZOOM_PUNCH.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			Ability found = power.getAbility(ZOOM_PUNCH);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered zoom_punch");
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			helper.assertTrue(action != null, "setup: the Zoom Punch did not start");
			component.setAction(action, user, SyncType.NO_SYNC);
			user.tickCount++;
			component.tick();
			List<HamonZoomPunchEntity> punches = helper.getLevel().getEntitiesOfClass(HamonZoomPunchEntity.class,
					user.getBoundingBox().inflate(16.0D), entity -> entity.getOwner() == user);
			helper.assertTrue(punches.size() == 1 && hamon.getAbilityCooldown(ZOOM_PUNCH) == COOLDOWN,
					"setup: the Zoom Punch threw no punch or set no 14-tick cooldown: punches " + punches.size());
			punch = punches.getFirst();
			// from here: a rate on the user would hold back the action that throws the arm, which its own test does not need
			if (tag != null) {
				user.addTag(tag);
			}
			List<Double> distances = new ArrayList<>();
			List<Integer> cooldowns = new ArrayList<>();
			int ticks = 0;
			while (!punch.isRemoved() && ticks < 200) {
				helper.getLevel().tickNonPassenger(punch);
				ticks++;
				cooldowns.add(hamon.getAbilityCooldown(ZOOM_PUNCH));
				distances.add(punch.saveWithoutId(new CompoundTag()).getDouble("Distance"));
			}
			helper.assertTrue(punch.isRemoved(), "the arm of the row " + tag + " is still out after " + ticks + " server ticks");
			return new Flight(ticks, hamon.getAbilityCooldown(ZOOM_PUNCH), distances, cooldowns);
		}
		finally {
			if (punch != null && !punch.isRemoved()) {
				punch.discard();
			}
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aStandsStringAndFingerAndAPillarMansHornLastInTheirOwnersTime(GameTestHelper helper) {
		register();
		ServerLevel level = helper.getLevel();
		Zombie user = EntityType.ZOMBIE.create(level);
		helper.assertTrue(user != null, "setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.setNoAi(true);
		user.setNoGravity(true);
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, -90.0F);
		helper.assertTrue(level.addFreshEntity(user), "setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(user);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
					"setup: could not give the zombie Star Platinum and summon it");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the zombie's Stand is missing");
			stand.setYRot(0.0F);
			stand.setXRot(-90.0F);
			lastsInItsOwnersTime(helper, user, "the Pillar Man's horn", () -> {
				PillarmanHornEntity horn = new PillarmanHornEntity(user, level);
				horn.setLifeSpan(20);
				return horn;
			}, entity -> entity.setPos(user.position()));
			lastsInItsOwnersTime(helper, user, "Star Platinum's finger", () -> {
				SPStarFingerEntity finger = new SPStarFingerEntity(stand, level);
				finger.setLifeSpan(20);
				return finger;
			}, entity -> entity.setPos(stand.position()));
			lastsInItsOwnersTime(helper, user, "a Stand's string", () -> {
				HGStringEntity string = new HGStringEntity(stand, level);
				string.setStringProperties(0.0F, 0.0F, false, 16);
				return string;
			}, entity -> entity.setPos(stand.position()));
		}
		finally {
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}

	private static void lastsInItsOwnersTime(GameTestHelper helper, LivingEntity user, String what,
			Supplier<OwnerBoundProjectileEntity> make, Consumer<OwnerBoundProjectileEntity> place) {
		int control = serverTicksOut(helper, make, place, what);
		user.addTag(BEING_SLOWED);
		int beingSlowed = serverTicksOut(helper, make, place, what + " with a BEING-only slowdown");
		helper.assertTrue(Math.abs(beingSlowed - 2 * control) <= 2,
				what + " follows the mob user's BEING-only rate of 1/2: " + beingSlowed + " server ticks against " + control);
		user.addTag(SLOWED);
		int bothSlowed = serverTicksOut(helper, make, place, what + " with both slowdowns");
		user.removeTag(BEING_SLOWED);
		int slowed = serverTicksOut(helper, make, place, what + " at 1/3");
		helper.assertTrue(bothSlowed == slowed, what + " uses the slower of 1/2 and 1/3, not their product: "
				+ bothSlowed + " server ticks against " + slowed + " at 1/3 alone");
		user.removeTag(SLOWED);
		user.addTag(SPED_UP);
		int fast = serverTicksOut(helper, make, place, what + " at 3");
		user.removeTag(SPED_UP);
		helper.assertTrue(Math.abs(slowed - 3 * control) <= 3, what + " at 1/3 lasts 3 times as long: " + slowed
				+ " server ticks against " + control);
		helper.assertTrue(Math.abs(3 * fast - control) <= 3, what + " at 3 lasts a third as long: " + fast
				+ " server ticks against " + control);
	}

	private static int serverTicksOut(GameTestHelper helper, Supplier<OwnerBoundProjectileEntity> make,
			Consumer<OwnerBoundProjectileEntity> place, String what) {
		OwnerBoundProjectileEntity part = make.get();
		place.accept(part);
		helper.assertTrue(helper.getLevel().addFreshEntity(part), "setup: could not add " + what);
		try {
			int ticks = 0;
			while (!part.isRemoved() && ticks < 300) {
				helper.getLevel().tickNonPassenger(part);
				ticks++;
			}
			helper.assertTrue(part.isRemoved(), what + " is still out after " + ticks + " server ticks");
			return ticks;
		}
		finally {
			if (!part.isRemoved()) {
				part.discard();
			}
		}
	}

	private static boolean near(double actual, double expected) {
		return Math.abs(actual - expected) < 0.02D;
	}
}
