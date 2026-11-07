package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravitySource;
import rotp.core.api.gravity.DirectionalGravityTransforms;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.directional_gravity.DirectionalGravityRuntime;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a body under a changed gravity does and what it starts, measured in the body's own frame: a mob's steps and looks,
 * its sight, a hit's push, a shot, a dropped item, the turn of the body and the walk of its legs, and where its Stand stands.
 * RotP-CMoon-gravity-1.4.3.jar handled each of these; a source that only binds gravity gets them here.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DirectionalGravityLivingGameTests {
	private static final ResourceLocation SOURCE = JojoMod.resLoc("gravity_living_test");
	private static final Direction[] TURNED = {
			Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	private DirectionalGravityLivingGameTests() {}

	@GameTest(template = "empty", batch = "gravityLiving1", timeoutTicks = 100)
	public static void aMobWalksTowardsWhatItWantsOnAnyFloor(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.plate(4);
		Cow cow = scene.cow(scene.origin);
		cow.removeFreeWill();
		scene.turn(cow, Direction.UP);
		Vec3 start = cow.position();
		Vec3 wanted = start.add(6.0, 0.0, 0.0);
		int[] ticks = {0};
		helper.onEachTick(() -> {
			cow.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, 1.0);
			if (++ticks[0] == 30) {
				double walked = cow.getX() - start.x;
				scene.close();
				helper.assertTrue(walked > 0.5, "The cow must walk towards +x on the ceiling, it went " + walked);
				helper.succeed();
			}
		});
	}

	@GameTest(template = "empty", batch = "gravityLiving2", timeoutTicks = 40)
	public static void aMobLooksAtWhatItWantsToSeeOnAnyFloor(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		List<String> wrong = new ArrayList<>();
		for (Direction direction : TURNED) {
			Cow cow = scene.cow(scene.origin);
			cow.removeFreeWill();
			scene.turn(cow, direction);
			Vec3 target = cow.getEyePosition().add(4.0, 3.0, -2.0);
			Vec3 toTarget = target.subtract(cow.getEyePosition()).normalize();

			cow.getLookControl().setLookAt(target.x, target.y, target.z, 360.0F, 360.0F);
			cow.getLookControl().tick();
			double head = DirectionalGravityTransforms.toWorld(direction,
					Vec3.directionFromRotation(cow.getXRot(), cow.yHeadRot)).dot(toTarget);
			if (head < 0.99) {
				wrong.add(direction + " look control faces " + head);
			}

			Cow other = scene.cow(target);
			other.setNoGravity(true);
			other.setNoAi(true);
			cow.lookAt(other, 360.0F, 360.0F);
			Vec3 toOther = other.getEyePosition().subtract(cow.getEyePosition()).normalize();
			double body = DirectionalGravityTransforms.toWorld(direction,
					Vec3.directionFromRotation(cow.getXRot(), cow.getYRot())).dot(toOther);
			if (body < 0.99) {
				wrong.add(direction + " lookAt faces " + body);
			}
		}
		scene.close();
		helper.assertTrue(wrong.isEmpty(), "A mob looks in its own frame: " + wrong);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "gravityLiving3", timeoutTicks = 40)
	public static void twoBodiesHangingFromACeilingSeeEachOther(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.plate(3);
		Cow first = scene.cow(scene.origin);
		Cow second = scene.cow(scene.origin.add(2.0, 0.0, 0.0));
		first.setNoAi(true);
		second.setNoAi(true);
		scene.turn(first, Direction.UP);
		scene.turn(second, Direction.UP);
		double plateBottom = scene.origin.y + 3.0;
		first.setPos(first.getX(), plateBottom, first.getZ());
		second.setPos(second.getX(), plateBottom, second.getZ());
		boolean sees = first.hasLineOfSight(second);
		double eyeY = first.getEyeY();
		double eye = first.getEyePosition().y;
		helper.assertTrue(sees, "Their eyes are under the ceiling, in the open");
		helper.assertTrue(Math.abs(eyeY - eye) < 1.0E-6, "getEyeY must be the eye's height: " + eyeY + ", the eye " + eye);

		// feet on the surface of a wall to the east: the point above them that vanilla looks from is in that wall
		BlockPos wall = BlockPos.containing(scene.origin).east(3);
		scene.block(wall);
		scene.block(wall.above());
		scene.block(wall.south());
		scene.block(wall.south().above());
		scene.block(wall.south(2));
		scene.block(wall.south(2).above());
		Cow left = scene.cow(scene.origin);
		Cow right = scene.cow(scene.origin.add(0.0, 0.0, 2.0));
		left.setNoAi(true);
		right.setNoAi(true);
		scene.turn(left, Direction.EAST);
		scene.turn(right, Direction.EAST);
		left.setPos(wall.getX(), wall.getY() + 0.9, wall.getZ() + 0.5);
		right.setPos(wall.getX(), wall.getY() + 0.9, wall.getZ() + 2.5);
		boolean seesOnWall = left.hasLineOfSight(right);
		scene.close();
		helper.assertTrue(seesOnWall, "Their eyes are off the wall, in the open");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "gravityLiving4", timeoutTicks = 40)
	public static void aHitLiftsItsTargetOffItsOwnFloor(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Cow cow = scene.cow(scene.origin);
		cow.setNoAi(true);
		scene.turn(cow, Direction.UP);
		cow.setDeltaMovement(Vec3.ZERO);
		cow.setOnGround(true);
		cow.knockback(0.4, 1.0, 0.0);
		Vec3 pushed = cow.getDeltaMovement();
		scene.close();
		// toWorld(UP) flips x and y: a push from +x sends it to -x, and the lift is -y, away from the ceiling
		helper.assertTrue(Math.abs(pushed.x + 0.4) < 1.0E-6 && Math.abs(pushed.y + 0.4) < 1.0E-6,
				"The hit must push along the ceiling and lift off it: " + pushed);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "gravityLiving5", timeoutTicks = 40)
	public static void aShotAndADroppedItemLeaveTheEyeAlongTheLook(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		List<String> wrong = new ArrayList<>();
		for (Direction direction : TURNED) {
			Player player = scene.player(scene.origin);
			scene.turn(player, direction);
			player.setXRot(10.0F);
			player.setYRot(-90.0F);
			player.setYHeadRot(-90.0F);
			Vec3 look = player.getViewVector(1.0F);
			Vec3 eye = player.getEyePosition();

			Arrow arrow = new Arrow(scene.level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
			scene.checkStart(wrong, direction + " arrow", arrow, eye, direction, 0.1);
			arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 0.0F);
			scene.checkShot(wrong, direction + " arrow", arrow, look);

			Snowball snowball = new Snowball(scene.level, player);
			scene.checkStart(wrong, direction + " snowball", snowball, eye, direction, 0.1);
			snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 0.0F);
			scene.checkShot(wrong, direction + " snowball", snowball, look);

			ItemEntity item = player.drop(new ItemStack(Items.STICK), false, true);
			scene.checkStart(wrong, direction + " dropped item", item, eye, direction, 0.3);
			if (item.getDeltaMovement().dot(look) < 0.1) {
				wrong.add(direction + " dropped item goes " + item.getDeltaMovement() + " with the look " + look);
			}
		}
		scene.close();
		helper.assertTrue(wrong.isEmpty(), "What a body starts leaves its eye along its look: " + wrong);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "gravityLiving6", timeoutTicks = 60)
	public static void theBodyTurnsAndTheLegsWalkWithTheWalkOnItsOwnFloor(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Player walker = scene.player(scene.origin);
		Player climber = scene.player(scene.origin.add(4.0, 0.0, 0.0));
		scene.turn(walker, Direction.UP);
		scene.turn(climber, Direction.NORTH);
		walker.setYRot(45.0F);
		walker.setYBodyRot(45.0F);
		int[] ticks = {0};
		helper.onEachTick(() -> {
			walker.setYRot(45.0F);
			walker.setDeltaMovement(0.14, 0.0, 0.14);
			climber.setDeltaMovement(0.0, 0.2, 0.0);
			if (++ticks[0] == 20) {
				float body = walker.yBodyRot;
				float legs = climber.walkAnimation.speed();
				scene.close();
				// on the ceiling world +x is -x: this walk is a yaw of 45, a body turning by the world's axes goes to -45
				helper.assertTrue(Math.abs(Mth.wrapDegrees(body - 45.0F)) < 5.0F,
						"The body must turn to where it walks on the ceiling, its yaw is " + body);
				helper.assertTrue(legs > 0.1F, "Up a wall is a walk for the legs, their speed is " + legs);
				helper.succeed();
			}
		});
	}

	@GameTest(template = "empty", batch = "gravityLiving7", timeoutTicks = 40)
	public static void aStandStaysBesideAUserOnACeiling(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Player user = scene.player(scene.origin);
		user.setYRot(0.0F);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
				"setup: could not give the user Star Platinum and summon it");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null, "setup: the summoned Stand is missing");
		scene.entities.add(stand);
		Vec3 upright = stand.offsetFromUser.getPosition(user).subtract(user.position());
		scene.turn(user, Direction.UP);
		Vec3 hanging = stand.offsetFromUser.getPosition(user).subtract(user.position());
		scene.close();
		Vec3 expected = DirectionalGravityTransforms.toWorld(Direction.UP, upright);
		helper.assertTrue(hanging.distanceTo(expected) < 1.0E-6,
				"The Stand's offset turns with its user's floor: " + hanging + " for " + expected);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "gravityLiving8", timeoutTicks = 60)
	public static void aPlayerStandsCrouchesAndCrawlsByTheRoomOnItsOwnFloor(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		List<String> wrong = new ArrayList<>();
		int spot = 0;
		for (Direction direction : TURNED) {
			BlockPos floor = BlockPos.containing(scene.origin).offset(spot++ * 8, 0, 0);
			scene.block(floor);
			Vec3 onFloor = scene.onFace(floor, direction);
			Vec3 up = Vec3.atLowerCornerOf(direction.getNormal()).scale(-1.0);
			Player player = scene.player(onFloor.add(up.scale(3.0)));
			scene.turn(player, direction);

			player.setPos(onFloor);
			player.setPose(Pose.SWIMMING);
			scene.checkPose(wrong, player, Pose.STANDING, direction + " left lying with room to stand");
			player.setPos(onFloor.add(up));
			scene.checkPose(wrong, player, Pose.STANDING, direction + " a block off the floor");
			player.setPos(onFloor);
			player.setShiftKeyDown(true);
			scene.checkPose(wrong, player, Pose.CROUCHING, direction + " sneaking");
			player.setShiftKeyDown(false);
			scene.checkPose(wrong, player, Pose.STANDING, direction + " done sneaking");

			BlockPos roof = floor.relative(direction.getOpposite(), 2);
			scene.block(roof);
			scene.checkPose(wrong, player, Pose.SWIMMING, direction + " with one block of room");
			scene.level.setBlockAndUpdate(roof, Blocks.AIR.defaultBlockState());
			scene.checkPose(wrong, player, Pose.STANDING, direction + " when the room comes back");
		}

		// a slab leaves 1.5 of room on a ceiling: too low to stand, enough to crouch
		BlockPos ceiling = BlockPos.containing(scene.origin).offset(0, 0, 8);
		scene.block(ceiling);
		Player low = scene.player(scene.onFace(ceiling, Direction.UP).subtract(0.0, 3.0, 0.0));
		scene.turn(low, Direction.UP);
		low.setPos(scene.onFace(ceiling, Direction.UP));
		BlockPos slab = ceiling.below(2);
		scene.level.setBlockAndUpdate(slab, Blocks.STONE_SLAB.defaultBlockState());
		scene.plates.add(slab);
		scene.checkPose(wrong, low, Pose.CROUCHING, "on a ceiling over a slab");

		scene.plate(3);
		double plateBottom = scene.origin.y + 3.0;
		Player walker = scene.player(scene.origin.subtract(0.0, 0.0, 2.0));
		scene.turn(walker, Direction.UP);
		walker.setNoGravity(false);
		walker.setPos(scene.origin.x, plateBottom, scene.origin.z - 2.0);
		walker.setYRot(90.0F);
		double startX = walker.getX();
		int[] ticks = {0};
		helper.onEachTick(() -> {
			walker.zza = 1.0F;
			if (walker.getPose() != Pose.STANDING || Math.abs(walker.getY() - plateBottom) > 1.0E-6) {
				wrong.add("a walk on the ceiling at tick " + ticks[0] + ": " + walker.getPose() + " at y " + walker.getY()
						+ " under " + plateBottom);
			}
			if (++ticks[0] == 20) {
				double walked = walker.getX() - startX;
				scene.close();
				helper.assertTrue(Math.abs(walked) > 1.0, "Premise: the walker must walk, he went " + walked);
				helper.assertTrue(wrong.isEmpty(), "A player's pose goes by the room on his own floor: " + wrong);
				helper.succeed();
			}
		});
	}

	@GameTest(template = "empty", batch = "gravityLiving9", timeoutTicks = 40)
	public static void aBodyIsNotPushedOffItsFloorButIsPushedOutOfWhatItStandsIn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		List<String> wrong = new ArrayList<>();
		int spot = 0;
		for (Direction direction : TURNED) {
			BlockPos floor = BlockPos.containing(scene.origin).offset(spot++ * 8, 0, 0);
			scene.block(floor);
			Vec3 onFloor = scene.onFace(floor, direction);
			Player player = scene.player(onFloor.subtract(Vec3.atLowerCornerOf(direction.getNormal()).scale(3.0)));
			scene.turn(player, direction);
			player.setPos(onFloor);
			player.setDeltaMovement(Vec3.ZERO);
			double side = player.getBbWidth() * 0.35;
			scene.pushFromCorners(player, direction, side);
			if (player.getDeltaMovement().lengthSqr() != 0.0) {
				wrong.add(direction + " pushed off its floor: " + player.getDeltaMovement());
			}

			// a block beside the body, into which it reaches by 0.15
			Direction beside = direction.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
			Vec3 toBlock = Vec3.atLowerCornerOf(beside.getNormal());
			scene.block(floor.relative(direction.getOpposite()).relative(beside));
			player.setPos(onFloor.add(toBlock.scale(0.35)));
			scene.pushFromCorners(player, direction, side);
			double along = player.getDeltaMovement().dot(toBlock);
			if (Math.abs(along + 0.1) > 1.0E-9) {
				wrong.add(direction + " must be pushed out along its floor, it moves " + player.getDeltaMovement());
			}
		}
		scene.close();
		helper.assertTrue(wrong.isEmpty(), "The push out of blocks works on the body's own floor: " + wrong);
		helper.succeed();
	}

	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final List<BlockPos> plates = new ArrayList<>();

		Scene(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			origin = Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO).above(24));
		}

		void plate(int height) {
			BlockPos at = BlockPos.containing(origin).above(height);
			for (int x = -8; x <= 8; x++) {
				for (int z = -4; z <= 4; z++) {
					BlockPos block = at.offset(x, 0, z);
					level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
					plates.add(block);
				}
			}
		}

		void block(BlockPos block) {
			level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
			plates.add(block);
		}

		Cow cow(Vec3 at) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "setup: no cow");
			cow.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(cow), "setup: the cow was not added");
			entities.add(cow);
			return cow;
		}

		Player player(Vec3 at) {
			Player player = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "gravity-living")) {
				@Override public boolean isSpectator() { return false; }
				@Override public boolean isCreative() { return false; }
			};
			player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
			player.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(player), "setup: the player was not added");
			entities.add(player);
			return player;
		}

		void turn(LivingEntity entity, Direction direction) {
			DirectionalGravitySource source = turned -> direction;
			DirectionalGravityApi.bind(entity, SOURCE, source);
			helper.assertTrue(DirectionalGravityApi.getEffectiveDirection(entity) == direction,
					"setup: " + direction + " was not applied to " + entity);
		}

		void checkStart(List<String> wrong, String what, Entity started, Vec3 eye, Direction direction, double under) {
			Vec3 expected = eye.subtract(DirectionalGravityTransforms.toWorld(direction, new Vec3(0.0, under, 0.0)));
			if (started.position().distanceTo(expected) > 1.0E-4) {
				wrong.add(what + " starts at " + started.position() + ", the eye's is " + expected);
			}
		}

		void checkShot(List<String> wrong, String what, Entity shot, Vec3 look) {
			double along = shot.getDeltaMovement().normalize().dot(look);
			if (along < 0.99) {
				wrong.add(what + " flies " + shot.getDeltaMovement() + " with the look " + look);
			}
		}

		/** The middle of the block's face that a body falling in this direction lands on. */
		Vec3 onFace(BlockPos block, Direction direction) {
			return Vec3.atCenterOf(block).subtract(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5));
		}

		void checkPose(List<String> wrong, Player player, Pose expected, String what) {
			player.tick();
			if (player.getPose() != expected) {
				wrong.add(what + ": " + player.getPose() + " for " + expected);
			}
		}

		void pushFromCorners(Player player, Direction direction, double side) {
			DirectionalGravityRuntime.pushOutOfBlocks(player, direction, -side, side);
			DirectionalGravityRuntime.pushOutOfBlocks(player, direction, -side, -side);
			DirectionalGravityRuntime.pushOutOfBlocks(player, direction, side, -side);
			DirectionalGravityRuntime.pushOutOfBlocks(player, direction, side, side);
		}

		void close() {
			entities.forEach(Entity::discard);
			plates.forEach(block -> level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState()));
		}
	}
}
