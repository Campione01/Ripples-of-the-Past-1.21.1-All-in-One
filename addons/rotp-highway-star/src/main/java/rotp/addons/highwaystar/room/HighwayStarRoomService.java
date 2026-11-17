package rotp.addons.highwaystar.room;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarRoomEntity;
import rotp.addons.highwaystar.room.HighwayStarRoomLure.Kind;
import rotp.addons.highwaystar.room.HighwayStarRoomState.RoomRef;
import rotp.addons.highwaystar.smell.HighwayStarSmellHelper;
import rotp.core.powersystem.standpower.StandPower;

public final class HighwayStarRoomService {
    public static final double PLACEMENT_RANGE = 32.0;
    public static final int COMBAT_MEMORY_TICKS = 6_000;

    private HighwayStarRoomService() {}

    public record Placement(BlockPos wallPos, Direction face) {}

    @Nullable
    public static Placement findPlacement(Player user) {
        Level level = user.level();
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getLookAngle().scale(PLACEMENT_RANGE));
        ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user);
        // Keep vanilla ray traversal, but stop at the first unavailable chunk rather than reading it.
        BlockHitResult miss = BlockHitResult.miss(end, Direction.UP, BlockPos.containing(end));
        BlockHitResult hit = BlockGetter.traverseBlocks(start, end, context, (clip, pos) -> {
            if (!level.hasChunkAt(pos)) {
                return miss;
            }
            BlockState state = level.getBlockState(pos);
            return level.clipWithInteractionOverride(start, end, pos, clip.getBlockShape(state, level, pos), state);
        }, clip -> miss);
        Direction face = hit.getDirection();
        if (hit.getType() != HitResult.Type.BLOCK || !face.getAxis().isHorizontal()) {
            return null;
        }
        BlockPos wall = hit.getBlockPos();
        BlockPos front = wall.relative(face);
        if (!level.getBlockState(wall).isFaceSturdy(level, wall, face)
                || !clear(level, front) || !clear(level, front.above())) {
            return null;
        }
        return new Placement(wall.immutable(), face);
    }

    private static boolean clear(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos) && level.getBlockState(pos).isAir();
    }

    @Nullable
    public static HighwayStarRoomEntity place(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        if (resolveOwner(level, owner.getUUID()) != owner) {
            return null;
        }
        Placement placement = findPlacement(owner);
        if (placement == null) {
            return null;
        }
        HighwayStarRoomEntity room = HighwayStarAddon.ROOM_ENTITY.get().create(level);
        if (room == null) {
            return null;
        }
        room.bind(owner.getUUID(), placement, level.getGameTime());
        if (!level.addFreshEntity(room)) {
            return null;
        }
        RoomRef old = owner.getData(HighwayStarAddon.ROOM_STATE)
                .replaceRoom(new RoomRef(room.getUUID(), level.dimension()));
        discardLoaded(owner.getServer(), owner.getUUID(), old);
        level.playSound(null, room.getX(), room.getY(), room.getZ(), HighwayStarAddon.SMELL_VOICE.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return room;
    }

    public static boolean hasHighwayStar(LivingEntity entity) {
        StandPower power = StandPower.get(entity);
        return power != null && power.hasPower() && power.getPowerType() == HighwayStarAddon.HIGHWAY_STAR.get();
    }

    @Nullable
    private static ServerPlayer resolveOwner(ServerLevel level, @Nullable UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && !owner.isRemoved() && owner.isAlive() && owner.serverLevel() == level
                && hasHighwayStar(owner) ? owner : null;
    }

    @Nullable
    public static ServerPlayer validOwner(ServerLevel level, @Nullable UUID ownerId, UUID roomId) {
        ServerPlayer owner = resolveOwner(level, ownerId);
        RoomRef ref = owner != null ? getRoomRef(owner) : null;
        return ref != null && ref.roomId().equals(roomId) && ref.dimension().equals(level.dimension()) ? owner : null;
    }

    @Nullable
    public static RoomRef getRoomRef(ServerPlayer owner) {
        return owner.getExistingData(HighwayStarAddon.ROOM_STATE).map(HighwayStarRoomState::getRoom).orElse(null);
    }

    public static void invalidate(ServerPlayer owner) {
        RoomRef old = owner.getExistingData(HighwayStarAddon.ROOM_STATE)
                .map(state -> state.replaceRoom(null)).orElse(null);
        discardLoaded(owner.getServer(), owner.getUUID(), old);
    }

    private static void discardLoaded(MinecraftServer server, UUID ownerId, @Nullable RoomRef ref) {
        if (ref == null) {
            return;
        }
        ServerLevel level = server.getLevel(ref.dimension());
        if (level != null && level.getEntity(ref.roomId()) instanceof HighwayStarRoomEntity room
                && ownerId.equals(room.getOwnerUUID())) {
            room.discard();
        }
    }

    public static void clearIfCurrent(ServerLevel level, @Nullable UUID ownerId, UUID roomId) {
        if (ownerId == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.getExistingData(HighwayStarAddon.ROOM_STATE).ifPresent(state -> {
                RoomRef ref = state.getRoom();
                if (ref != null && ref.roomId().equals(roomId) && ref.dimension().equals(level.dimension())) {
                    state.replaceRoom(null);
                }
            });
        }
    }

    public static boolean canEnter(ServerPlayer owner, LivingEntity target) {
        return target != owner && !target.isRemoved() && owner.level() == target.level()
                && HighwayStarSmellHelper.isSmellable(target)
                && (!(target instanceof OwnableEntity ownable) || !owner.getUUID().equals(ownable.getOwnerUUID()));
    }

    static void rememberCombat(ServerPlayer viewer, LivingEntity other) {
        if (viewer != other && viewer.level() == other.level() && !viewer.isSpectator()) {
            viewer.getData(HighwayStarAddon.ROOM_STATE).rememberCombat(
                    BuiltInRegistries.ENTITY_TYPE.getKey(other.getType()), viewer.level().getGameTime());
        }
    }

    public static HighwayStarRoomLure chooseLure(ServerPlayer viewer, long gameTick) {
        ResourceLocation combat = viewer.getExistingData(HighwayStarAddon.ROOM_STATE)
                .map(state -> state.recentCombatType(gameTick, COMBAT_MEMORY_TICKS)).orElse(null);
        if (combat != null) {
            return new HighwayStarRoomLure(Kind.COMBAT, Optional.of(combat));
        }
        if (viewer.getHealth() <= viewer.getMaxHealth() * 0.5F) {
            return new HighwayStarRoomLure(Kind.REST, Optional.empty());
        }
        if (viewer.getFoodData().getFoodLevel() < 20) {
            return new HighwayStarRoomLure(Kind.FOOD, Optional.empty());
        }
        return HighwayStarRoomLure.CHEST;
    }
}
