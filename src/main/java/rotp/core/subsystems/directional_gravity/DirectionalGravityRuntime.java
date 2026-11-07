package rotp.core.subsystems.directional_gravity;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;
import rotp.core.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class DirectionalGravityRuntime {
	private DirectionalGravityRuntime() {}

	public static LocalFrame enterLocalFrame(LivingEntity entity) {
		DirectionalGravityFrameAccess access = access(entity);
		int depth = access.jojo_ripples$localFrameDepth();
		Direction direction = depth > 0
				? access.jojo_ripples$localFrameDirection()
				: DirectionalGravityApi
						.getEffectiveDirection(entity);
		if (direction == Direction.DOWN) {
			return LocalFrame.INACTIVE;
		}
		if (depth == 0) {
			access.jojo_ripples$setLocalFrameDirection(direction);
			access.jojo_ripples$setLastLocalMovement(Vec3.ZERO);
		}
		entity.setDeltaMovement(enterFrameVelocity(
				direction, entity.getDeltaMovement(), depth));
		access.jojo_ripples$setLocalFrameDepth(depth + 1);
		return new LocalFrame(entity);
	}

	public static boolean isLocalFrame(Entity entity) {
		return entity instanceof DirectionalGravityFrameAccess access
				&& access.jojo_ripples$localFrameDepth() > 0;
	}

	public static Direction localFrameDirection(Entity entity) {
		if (!isLocalFrame(entity)) {
			return Direction.DOWN;
		}
		return access(entity).jojo_ripples$localFrameDirection();
	}

	public static Vec3 lastLocalMovement(Entity entity) {
		return isLocalFrame(entity)
				? access(entity).jojo_ripples$lastLocalMovement()
				: Vec3.ZERO;
	}

	public static void recordLocalMovement(Entity entity,
			Vec3 movement) {
		if (isLocalFrame(entity)) {
			access(entity).jojo_ripples$setLastLocalMovement(
					movement);
		}
	}

	public static void setWorldMoveAdapterActive(Entity entity,
			boolean active) {
		access(entity).jojo_ripples$setWorldMoveAdapterActive(active);
	}

	public static boolean isWorldMoveAdapterActive(Entity entity) {
		return entity instanceof DirectionalGravityFrameAccess access
				&& access.jojo_ripples$isWorldMoveAdapterActive();
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Pre event) {
		DirectionalGravityApi.reconcileEffectiveDirection(
				event.getEntity());
	}

	/** Vanilla starts a shot 0.1 under the eye of a shooter standing upright, which can be inside a block otherwise. */
	public static void startProjectileAtEye(Entity projectile, LivingEntity shooter) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection(shooter);
		if (direction != Direction.DOWN) {
			projectile.setPos(shooter.getEyePosition().subtract(
					DirectionalGravityTransforms.toWorld(direction, new Vec3(0.0, 0.1, 0.0))));
		}
	}

	/**
	 * LocalPlayer.moveTowardsClosestSpace on the body's own floor: the offset is along that floor. Vanilla takes the blocks
	 * beside an upright body, so on a wall it finds the wall at the feet and keeps pushing the body off it.
	 */
	public static void pushOutOfBlocks(Player player, Direction gravity, double localX, double localZ) {
		Vec3 point = player.position().add(
				DirectionalGravityTransforms.toWorld(gravity, new Vec3(localX, 0.0, localZ)));
		BlockPos block = BlockPos.containing(point);
		if (!suffocatesAt(player, gravity, block)) {
			return;
		}
		Direction out = null;
		double nearest = Double.MAX_VALUE;
		for (Direction side : Direction.values()) {
			if (side.getAxis() == gravity.getAxis()) {
				continue;
			}
			double inside = side.getAxis().choose(
					point.x - block.getX(), point.y - block.getY(), point.z - block.getZ());
			double toEdge = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - inside : inside;
			if (toEdge < nearest && !suffocatesAt(player, gravity, block.relative(side))) {
				nearest = toEdge;
				out = side;
			}
		}
		if (out != null) {
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(
					out.getAxis() == Direction.Axis.X ? 0.1 * out.getStepX() : velocity.x,
					out.getAxis() == Direction.Axis.Y ? 0.1 * out.getStepY() : velocity.y,
					out.getAxis() == Direction.Axis.Z ? 0.1 * out.getStepZ() : velocity.z);
		}
	}

	private static boolean suffocatesAt(Player player, Direction gravity, BlockPos block) {
		AABB body = player.getBoundingBox();
		AABB cell = new AABB(block);
		AABB probe = switch (gravity.getAxis()) {
		case X -> new AABB(body.minX, cell.minY, cell.minZ, body.maxX, cell.maxY, cell.maxZ);
		case Y -> new AABB(cell.minX, body.minY, cell.minZ, cell.maxX, body.maxY, cell.maxZ);
		case Z -> new AABB(cell.minX, cell.minY, body.minZ, cell.maxX, cell.maxY, body.maxZ);
		};
		return player.level().collidesWithSuffocatingBlock(player, probe.deflate(1.0E-7));
	}

	private static DirectionalGravityFrameAccess access(Entity entity) {
		return (DirectionalGravityFrameAccess) entity;
	}

	static Vec3 enterFrameVelocity(Direction direction,
			Vec3 velocity, int currentDepth) {
		return currentDepth == 0
				? DirectionalGravityTransforms.toLocal(
						direction, velocity)
				: velocity;
	}

	static Vec3 exitFrameVelocity(Direction direction,
			Vec3 velocity, int remainingDepth) {
		return remainingDepth == 0
				? DirectionalGravityTransforms.toWorld(
						direction, velocity)
				: velocity;
	}

	public static final class LocalFrame implements AutoCloseable {
		private static final LocalFrame INACTIVE =
				new LocalFrame(null);

		private LivingEntity entity;

		private LocalFrame(LivingEntity entity) {
			this.entity = entity;
		}

		public boolean isActive() {
			return entity != null;
		}

		@Override
		public void close() {
			LivingEntity closing = entity;
			if (closing == null) {
				return;
			}
			entity = null;
			DirectionalGravityFrameAccess access = access(closing);
			int depth = access.jojo_ripples$localFrameDepth() - 1;
			if (depth < 0) {
				throw new IllegalStateException(
						"Directional gravity frame underflow");
			}
			access.jojo_ripples$setLocalFrameDepth(depth);
			Direction direction =
					access.jojo_ripples$localFrameDirection();
			closing.setDeltaMovement(exitFrameVelocity(
					direction, closing.getDeltaMovement(), depth));
			if (depth == 0) {
				access.jojo_ripples$setLocalFrameDirection(
						Direction.DOWN);
				access.jojo_ripples$setLastLocalMovement(
						Vec3.ZERO);
			}
		}
	}
}
