package rotp.addons.cmoon.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravitySource;
import rotp.core.api.gravity.DirectionalGravityTransforms;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.cmoon.CMoonAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Gravity Field of a player's C-Moon build (RotP-CMoon-gravity-1.4.3.jar, CMEntity): everything around the user
 * falls towards the surface nearest to him when it started. It belongs to the user, not to the Stand entity, so it goes
 * on through a recall; it ends by its own key, with his death, logout or change of level and when he loses the Stand.
 */
public final class GravityField implements DirectionalGravitySource {
    public static final double RADIUS = 100.0D;
    public static final int SUPPORT_RANGE = 50;
    public static final int FALL_PROTECTION_TICKS = 100;
    public static final double FALL_MULTIPLIER = 6.0D;
    // The build lifted the server's limit of 10 blocks a tick for a moving player; this stays under it.
    public static final double MAX_FALL_SPEED = 8.0D;
    // about a second: a jump or a knock is back within SUPPORT_RANGE of the floor long before
    public static final int UNSUPPORTED_TICKS_TO_SEARCH = 20;

    public static final StreamCodec<ByteBuf, GravityField> CLIENT_VIEW =
            Direction.STREAM_CODEC.map(GravityField::new, GravityField::direction);

    private static final ResourceLocation SOURCE_ID = CMoonAddon.id("gravity_field");
    // over Gravity Shift's
    private static final int PRIORITY = 200;
    // past half of a client's step of 1/4096, so that its rounding on the other two axes does not count as being stuck
    private static final double SIDE_TOLERANCE = 2.0E-4D;
    private static final double MAX_STEP_OUT = 2.0D;
    private static final Direction[] SUPPORTS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

    @Nullable
    private final LivingEntity user;
    @Nullable
    private final Level level;
    private Direction direction;
    private final Set<Entity> held = Collections.newSetFromMap(new IdentityHashMap<>());
    // Once over it stays over: a way back to this level must not bring the gravity back.
    private boolean over;
    private boolean ended;
    private int unsupportedTicks;

    private GravityField(LivingEntity user, Direction direction) {
        this.user = user;
        this.level = user.level();
        this.direction = direction;
    }

    // what a client knows of a field
    private GravityField(Direction direction) {
        this.user = null;
        this.level = null;
        this.direction = direction;
        this.over = true;
        this.ended = true;
    }

    public Direction direction() {
        return direction;
    }

    /** The user's own running field; on a client only its direction can be read. */
    @Nullable
    public static GravityField of(LivingEntity user) {
        return user.getExistingDataOrNull(CMoonAddon.USER_GRAVITY_FIELD);
    }

    public static boolean isRunning(@Nullable LivingEntity user) {
        return user != null && of(user) != null;
    }

    /** The field that holds this entity, if any. */
    @Nullable
    public static GravityField holding(Entity entity) {
        return entity.getExistingDataOrNull(CMoonAddon.HELD_BY_GRAVITY_FIELD);
    }

    /** The key of the skill: ends the user's field, or starts one where there is a surface to fall to. */
    public static void toggle(LivingEntity user) {
        GravityField running = of(user);
        if (running != null) {
            running.end();
            return;
        }
        Level level = user.level();
        Direction direction = findSupportDirection(
                user.blockPosition(), user.getUUID(), pos -> isSolid(level, pos));
        if (direction == null) {
            // the build started it, found nothing to fall to and ended it within the tick
            protectFromFall(user);
            return;
        }
        GravityField field = new GravityField(user, direction);
        user.setData(CMoonAddon.USER_GRAVITY_FIELD, field);
        field.scan();
    }

    /**
     * CMEntity.findSupportDirection: the nearest wall or ceiling in {@link #SUPPORT_RANGE} blocks. The floor counts only
     * when there is none of those, and with nothing at all there is no field. A tie goes by the user's UUID.
     */
    @Nullable
    public static Direction findSupportDirection(BlockPos base, UUID user, Predicate<BlockPos> solid) {
        List<Direction> nearest = new ArrayList<>();
        int nearestDistance = Integer.MAX_VALUE;
        for (Direction candidate : SUPPORTS) {
            int distance = distanceTo(base, candidate, solid);
            if (distance < 0 || distance > nearestDistance) {
                continue;
            }
            if (distance < nearestDistance) {
                nearest.clear();
                nearestDistance = distance;
            }
            nearest.add(candidate);
        }
        if (nearest.isEmpty()) {
            return distanceTo(base, Direction.DOWN, solid) > 0 ? Direction.DOWN : null;
        }
        return nearest.get(new Random(user.hashCode() + nearestDistance * 31).nextInt(nearest.size()));
    }

    private static int distanceTo(BlockPos base, Direction direction, Predicate<BlockPos> solid) {
        for (int distance = 1; distance <= SUPPORT_RANGE; distance++) {
            if (solid.test(base.relative(direction, distance))) {
                return distance;
            }
        }
        return -1;
    }

    private static boolean isSolid(Level level, BlockPos pos) {
        return !level.isEmptyBlock(pos);
    }

    /** From the user's own tick, on the server, whether or not his Stand is out. */
    public static void tickUser(LivingEntity user) {
        GravityField field = of(user);
        if (field != null) {
            if (field.isOver()) {
                field.end();
            }
            else {
                field.searchAgainWhenUnsupported();
                if (!field.ended) {
                    field.scan();
                }
            }
        }
    }

    /**
     * Past the edge of a wall the locked direction carries the user on for ever. After about a second in the air with
     * nothing within SUPPORT_RANGE along it, the surface is searched from where he is, as at the start, and everyone held
     * turns with him; with nothing at all the field ends, as it does at the start. A field on the floor is left alone:
     * whoever falls there falls as usual.
     */
    private void searchAgainWhenUnsupported() {
        if (direction == Direction.DOWN
                || !isFalling(user)
                || distanceTo(user.blockPosition(), direction, pos -> isSolid(level, pos)) > 0) {
            unsupportedTicks = 0;
            return;
        }
        if (++unsupportedTicks < UNSUPPORTED_TICKS_TO_SEARCH) {
            return;
        }
        unsupportedTicks = 0;
        Direction found = findSupportDirection(user.blockPosition(), user.getUUID(), pos -> isSolid(level, pos));
        if (found == null) {
            end();
        }
        else {
            turnTo(found);
        }
    }

    private static boolean isFalling(LivingEntity user) {
        return !user.onGround()
                && !user.isNoGravity()
                && !user.isPassenger()
                && !(user instanceof Player player && player.getAbilities().flying);
    }

    private void turnTo(Direction next) {
        direction = next;
        user.syncData(CMoonAddon.USER_GRAVITY_FIELD);
        List<Entity> all = new ArrayList<>(held);
        if (next == Direction.DOWN) {
            // a field that finds only the floor holds nobody, as at the start
            held.clear();
            all.forEach(this::release);
            return;
        }
        for (Entity entity : all) {
            entity.syncData(CMoonAddon.HELD_BY_GRAVITY_FIELD);
            if (entity instanceof LivingEntity living) {
                // the build reset it with every change of direction: the second of sideways fall is no height
                living.resetFallDistance();
                DirectionalGravityApi.directionChanged(living, SOURCE_ID, this);
            }
        }
    }

    /** The user left this level: logout, death's respawn, a portal, or his chunk unloaded. */
    public static void endWithUser(LivingEntity user) {
        GravityField field = of(user);
        if (field != null) {
            field.end();
        }
    }

    /**
     * Before the player list saves a leaving player: with its body still hanging from a ceiling, the position it saves
     * would wake him up inside that ceiling.
     */
    public static void letGoOfLeavingPlayer(Player player) {
        endWithUser(player);
        GravityField holder = holding(player);
        if (holder != null) {
            holder.held.remove(player);
            holder.release(player);
        }
    }

    @Override
    public Direction gravityDirection(Entity entity) {
        return !isOver() && entity.level() == level ? direction : Direction.DOWN;
    }

    private boolean isOver() {
        if (!over) {
            StandPower power = StandPower.get(user);
            over = !user.isAlive()
                    || user.isRemoved()
                    || user.level() != level
                    || power == null
                    || !power.hasPower()
                    || power.getPowerType() != CMoonAddon.C_MOON.get();
        }
        return over;
    }

    private void scan() {
        AABB area = user.getBoundingBox().inflate(RADIUS);
        for (Iterator<Entity> iterator = held.iterator(); iterator.hasNext();) {
            Entity entity = iterator.next();
            if (entity.isRemoved()
                    || entity.level() != level
                    || entity instanceof LivingEntity living && !living.isAlive()
                    || !area.intersects(entity.getBoundingBox())) {
                iterator.remove();
                release(entity);
            }
        }
        if (direction != Direction.DOWN) {
            level.getEntities((Entity) null, area, this::canHold).forEach(this::hold);
        }
    }

    // One field at a time holds an entity. Anything that does not fall by itself, a Stand's flying parts for one, is left alone.
    private boolean canHold(Entity entity) {
        if (holding(entity) != null || entity.isSpectator()) {
            return false;
        }
        return entity instanceof LivingEntity living ? living.isAlive() : entity.getGravity() != 0.0D;
    }

    private void hold(Entity entity) {
        held.add(entity);
        entity.setData(CMoonAddon.HELD_BY_GRAVITY_FIELD, this);
        if (entity instanceof LivingEntity living) {
            living.resetFallDistance();
            DirectionalGravityApi.bind(living, SOURCE_ID, PRIORITY, this);
        }
    }

    private void release(Entity entity) {
        entity.removeData(CMoonAddon.HELD_BY_GRAVITY_FIELD);
        if (entity instanceof LivingEntity living) {
            DirectionalGravityApi.unbind(living, SOURCE_ID, this);
            if (living.isAlive() && !(living instanceof StandEntity)) {
                protectFromFall(living);
            }
        }
    }

    public void end() {
        if (ended) {
            return;
        }
        over = true;
        ended = true;
        List<Entity> all = new ArrayList<>(held);
        held.clear();
        all.forEach(this::release);
        user.removeData(CMoonAddon.USER_GRAVITY_FIELD);
        if (user.isAlive()) {
            protectFromFall(user);
        }
    }

    private static void protectFromFall(LivingEntity entity) {
        entity.resetFallDistance();
        entity.addEffect(new MobEffectInstance(
                MobEffects.SLOW_FALLING, FALL_PROTECTION_TICKS, 0, false, false, true));
    }

    /**
     * Before an entity's tick, on both sides, as both move it: what is not alive has no core frame, so its gravity is
     * turned here. It is taken back from straight down, where its own tick will apply it, and given along the field.
     */
    public static void pullBeforeTick(Entity entity) {
        if (entity instanceof LivingEntity
                || entity instanceof AbstractArrow arrow && arrow.inGround) {
            return;
        }
        GravityField field = holding(entity);
        double gravity = entity.getGravity();
        if (field != null && gravity != 0.0D) {
            stepOutOfSurface(entity, field.direction);
            Vec3 along = Vec3.atLowerCornerOf(field.direction.getNormal()).scale(gravity);
            entity.setDeltaMovement(entity.getDeltaMovement().add(along.x, along.y + gravity, along.z));
        }
    }

    /** After the tick of what is not alive, on the server: a falling block lands on the field's floor. */
    public static void landAfterTick(Entity entity) {
        if (entity instanceof FallingBlockEntity falling
                && !falling.level().isClientSide()
                && falling.isAlive()) {
            GravityField field = holding(falling);
            Direction direction = field != null ? field.gravityDirection(falling) : Direction.DOWN;
            if (direction != Direction.DOWN) {
                FallingBlockLanding.landAfterTick(falling, direction);
            }
        }
    }

    /**
     * Collision does not stop a box whose leading face is already past the near face of a block, and vanilla pushes what is
     * stuck up or sideways, so from there the pull carries it on through. A client is told positions in steps of 1/4096,
     * which leaves its copy of a box 0.98 across, a primed TNT or a falling block, 0.00002 inside the surface it rests on;
     * the server's copy stays and blows up there. So anything that starts or is told to be in the way comes back out.
     */
    private static void stepOutOfSurface(Entity entity, Direction direction) {
        // A shot looks along its own flight, a cart keeps to its rail, and whatever else has no physics means to go through.
        if (entity instanceof Projectile
                || entity instanceof AbstractMinecart cart && cart.isOnRails()
                || entity.noPhysics && !(entity instanceof ItemEntity)) {
            return;
        }
        Vec3 back = Vec3.atLowerCornerOf(direction.getOpposite().getNormal());
        AABB box = entity.getBoundingBox();
        double total = 0.0D;
        double depth = depthInSolid(entity, box, direction);
        while (depth > 0.0D) {
            total += depth;
            if (total > MAX_STEP_OUT) {
                return;
            }
            box = box.move(back.scale(depth));
            depth = depthInSolid(entity, box, direction);
        }
        if (total > 0.0D) {
            entity.setPos(entity.position().add(back.scale(total)));
        }
    }

    private static double depthInSolid(Entity entity, AABB box, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        AABB probe = box.deflate(
                axis == Direction.Axis.X ? 1.0E-7D : SIDE_TOLERANCE,
                axis == Direction.Axis.Y ? 1.0E-7D : SIDE_TOLERANCE,
                axis == Direction.Axis.Z ? 1.0E-7D : SIDE_TOLERANCE);
        double depth = 0.0D;
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, probe)) {
            for (AABB solid : shape.toAabbs()) {
                if (solid.intersects(probe)) {
                    depth = Math.max(depth, direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? box.max(axis) - solid.min(axis)
                            : solid.max(axis) - box.min(axis));
                }
            }
        }
        return depth;
    }

    /**
     * After an entity's tick, where its movement is decided: the build's fall in a changed direction, six times what the
     * tick left, so that everyone is on the new floor at once. A server moves its mobs, a client its own player.
     */
    public static void fallAfterTick(LivingEntity entity) {
        GravityField field = holding(entity);
        if (field == null
                || entity.onGround()
                || entity.isNoGravity()
                || entity instanceof StandEntity
                || entity instanceof FlyingMob
                || DirectionalGravityApi.getEffectiveDirection(entity) != field.direction
                || entity instanceof Player player && player.getAbilities().flying
                || (entity.level().isClientSide()
                        ? !entity.isControlledByLocalInstance()
                        : entity instanceof ServerPlayer)) {
            return;
        }
        Vec3 local = DirectionalGravityTransforms.toLocal(field.direction, entity.getDeltaMovement());
        if (local.y < 0.0D) {
            double fall = Math.max(local.y * FALL_MULTIPLIER, -MAX_FALL_SPEED);
            entity.setDeltaMovement(DirectionalGravityTransforms.toWorld(
                    field.direction, new Vec3(local.x, fall, local.z)));
        }
    }
}
