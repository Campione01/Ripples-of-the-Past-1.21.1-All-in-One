package rotp.addons.highwaystar.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarRoomEntity;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads.ViewEndPayload;
import rotp.addons.highwaystar.network.HighwayStarRoomPayloads.ViewStartPayload;
import rotp.addons.highwaystar.room.HighwayStarRoomService;
import rotp.addons.highwaystar.room.HighwayStarRoomState.RoomRef;
import rotp.addons.highwaystar.smell.SmellTargets;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;

final class RoomTestScene {
    static final String ROOM = "highway_star_room";
    private static final TicketType<UUID> TICKET = TicketType.create("highwaystar_room_gametest", Comparator.<UUID>naturalOrder());
    private static final int TICKING_RADIUS = 2;
    final GameTestHelper helper;
    final ServerLevel level;
    private final List<Participant> players = new ArrayList<>();
    private final List<Entity> mobs = new ArrayList<>();
    private final List<HighwayStarRoomEntity> rooms = new ArrayList<>();
    private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
    private final UUID ticketId = UUID.randomUUID();
    private final List<ChunkPos> heldChunks = new ArrayList<>();
    private short key;
    private boolean closed;

    private RoomTestScene(GameTestHelper helper) {
        this.helper = helper;
        level = helper.getLevel();
    }

    static void run(GameTestHelper helper, Consumer<RoomTestScene> test) {
        RoomTestScene scene = new RoomTestScene(helper);
        scene.step(() -> {
            scene.holdSceneChunks();
            test.accept(scene);
        });
    }

    private void holdSceneChunks() {
        // Vanilla only holds the one-block template; the randomly placed test grid can split these actors across chunks.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        var cache = level.getChunkSource();
        for (int x = (origin.getX() - 3) >> 4; x <= (origin.getX() + 8) >> 4; x++) {
            for (int z = (origin.getZ() - 1) >> 4; z <= (origin.getZ() + 8) >> 4; z++) {
                var chunk = new ChunkPos(x, z);
                cache.addRegionTicket(TICKET, chunk, TICKING_RADIUS, ticketId);
                heldChunks.add(chunk);
            }
        }
        long start = System.nanoTime();
        while (heldChunks.stream().anyMatch(chunk -> !level.isPositionEntityTicking(chunk.getWorldPosition()))) {
            check(System.nanoTime() - start < 30_000_000_000L, "fixture: the Room scene chunks did not start ticking");
            if (!cache.pollTask()) {
                LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    void after(int delay, Runnable callback) {
        helper.runAfterDelay(delay, () -> step(callback));
    }

    private void step(Runnable callback) {
        try {
            callback.run();
        }
        catch (RuntimeException | Error failure) {
            try {
                close();
            }
            catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    void finish() {
        close();
        helper.succeed();
    }

    void reached(String name) {
        LogUtils.getLogger().info("HS_ROOM_REACHED {}", name);
    }

    void check(boolean ok, String message) {
        helper.assertTrue(ok, message);
    }

    Participant player() {
        Participant participant = new Participant(level);
        players.add(participant);
        participant.join(parked());
        check(level.getServer().getPlayerList().getPlayer(participant.player.getUUID()) == participant.player,
                "fixture: owner/viewer is not in the actual server player list");
        return participant;
    }

    ServerPlayer owner() {
        ServerPlayer owner = player().player;
        grant(owner);
        return owner;
    }

    StandPower grant(LivingEntity user) {
        StandPower power = PowerClass.STAND.attachGet(user);
        power.setStand(HighwayStarAddon.HIGHWAY_STAR.get());
        check(HighwayStarRoomService.hasHighwayStar(user), "fixture: Highway Star was not granted");
        return power;
    }

    BlockPos wall(int slot) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 6 + slot * 5, 2));
        setBlock(pos, Blocks.STONE);
        setBlock(pos.west(), Blocks.AIR);
        setBlock(pos.west().above(), Blocks.AIR);
        return pos;
    }

    void setBlock(BlockPos pos, Block block) {
        originalBlocks.putIfAbsent(pos, level.getBlockState(pos));
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    void aim(LivingEntity actor, BlockPos wall, double distance) {
        actor.moveTo(wall.getX() - distance, wall.getY() + 0.5 - actor.getEyeHeight(), wall.getZ() + 0.5,
                -90.0F, 0.0F);
    }

    boolean click(LivingEntity user, String name) {
        StandPower power = StandPower.get(user);
        Ability base = power.getAbility(name);
        check(base != null, "fixture: missing registered ability " + name);
        AvailableAbilities available = new AvailableAbilities();
        available.update(power, power.getMoveset());
        var selected = available.getContextVariationContainer(base);
        if (!AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK)) {
            return false;
        }
        AbilityInput.keyPress(++key, selected.ability, user, null, InputMethod.CLICK, 0,
                BufferingState.clickCanBuffer(), base.getAbilityId());
        return true;
    }

    @Nullable
    String shiftBinding(StandPower power) {
        var scheme = power.getPowerType().getDefaultMoveset().controlSchemes.get("hotbar");
        if (scheme == null) {
            return null;
        }
        return scheme.groups.values().stream()
                .flatMap(group -> group.hotbars.stream()).flatMap(hotbar -> hotbar.slots.stream())
                .filter(slot -> ROOM.equals(slot.getOrDefault(null, Map.of()).get(InputMethod.CLICK)))
                .map(slot -> slot.getOrDefault(InputKey.Modifier.SHIFT, Map.of()).get(InputMethod.CLICK))
                .filter(name -> name != null).findFirst().orElse(null);
    }

    @Nullable
    HighwayStarRoomEntity placedRoom(ServerPlayer owner) {
        RoomRef ref = HighwayStarRoomService.getRoomRef(owner);
        if (ref != null && level.getEntity(ref.roomId()) instanceof HighwayStarRoomEntity room) {
            if (!rooms.contains(room)) {
                rooms.add(room);
            }
            return room;
        }
        return null;
    }

    HighwayStarRoomEntity cast(ServerPlayer owner, int slot) {
        StandPower power = StandPower.get(owner);
        power.setStamina(power.getMaxStamina());
        power.setAbilityCooldown(ROOM, 0);
        aim(owner, wall(slot), 3.0);
        check(click(owner, ROOM), "fixture: valid Room click was refused");
        HighwayStarRoomEntity room = placedRoom(owner);
        check(room != null && owner.getUUID().equals(room.getOwnerUUID()), "fixture: Room not created for owner");
        return room;
    }

    Vec3 parked() {
        return Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
    }

    static Vec3 entry(HighwayStarRoomEntity room) {
        return entry(room, 0.18);
    }

    static Vec3 entry(HighwayStarRoomEntity room, double outward) {
        var face = room.getFacing();
        return room.position().add(face.getStepX() * outward, 0, face.getStepZ() * outward);
    }

    static void move(Entity entity, Vec3 pos) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
    }

    <M extends Mob> M spawn(EntityType<M> type, Vec3 pos) {
        M entity = type.create(level);
        check(entity != null, "fixture: mob could not be constructed: " + type);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        move(entity, pos);
        mobs.add(entity);
        check(level.addFreshEntity(entity), "fixture: mob could not be added: " + type);
        return entity;
    }

    List<HighwayStarSoleEntity> soles(ServerPlayer owner, HighwayStarRoomEntity at) {
        return level.getEntitiesOfClass(HighwayStarSoleEntity.class, at.getBoundingBox().inflate(12),
                sole -> owner.getUUID().equals(sole.getOwnerUUID()));
    }

    long attached(ServerPlayer owner, LivingEntity victim) {
        return victim.getPassengers().stream().filter(passenger -> passenger instanceof HighwayStarSoleEntity sole
                && owner.getUUID().equals(sole.getOwnerUUID())).count();
    }

    SmellTargets scent(ServerPlayer owner) {
        return owner.getData(HighwayStarAddon.SMELL_TARGETS);
    }

    CompoundTag save(HighwayStarRoomEntity room) {
        CompoundTag tag = new CompoundTag();
        check(room.save(tag), "fixture: registered Room did not save");
        return tag;
    }

    HighwayStarRoomEntity load(CompoundTag tag) {
        Entity entity = EntityType.create(tag, level).orElseThrow();
        check(entity instanceof HighwayStarRoomEntity, "fixture: saved Room resolved to another entity type");
        HighwayStarRoomEntity room = (HighwayStarRoomEntity) entity;
        rooms.add(room);
        check(level.addFreshEntity(room), "fixture: reloaded Room could not enter the level");
        return room;
    }

    void logout(ServerPlayer player) {
        players.stream().filter(participant -> participant.player == player).findFirst().orElseThrow().close();
    }

    private void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            for (Participant participant : players) {
                ServerPlayer owner = participant.player;
                for (HighwayStarRoomEntity room : rooms) {
                    soles(owner, room).forEach(Entity::discard);
                }
                StandPower power = StandPower.get(owner);
                if (!owner.isRemoved() && power != null && power.hasPower() && power.isSummoned()) {
                    power.getPowerType().forceUnsummon(owner, power);
                }
            }
            rooms.forEach(Entity::discard);
            mobs.forEach(Entity::discard);
            players.forEach(Participant::close);
            originalBlocks.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
        }
        finally {
            for (ChunkPos chunk : heldChunks) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKING_RADIUS, ticketId);
            }
            heldChunks.clear();
        }
    }

    static final class Participant {
        final ServerPlayer player;
        private final CommonListenerCookie cookie;
        private final Recorder connection = new Recorder();
        private final EmbeddedChannel channel = new EmbeddedChannel(connection);
        private boolean closed;

        private Participant(ServerLevel level) {
            UUID id = UUID.randomUUID();
            GameProfile profile = new GameProfile(id, "hs" + id.toString().substring(0, 8));
            cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        }

        private void join(Vec3 pos) {
            NetworkRegistry.configureMockConnection(connection);
            player.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            move(player, pos);
        }

        List<ViewStartPayload> starts(HighwayStarRoomEntity room) {
            return connection.views.stream().filter(ViewStartPayload.class::isInstance).map(ViewStartPayload.class::cast)
                    .filter(start -> room.getUUID().equals(start.roomId())).toList();
        }

        long ends(HighwayStarRoomEntity room) {
            return connection.views.stream().filter(ViewEndPayload.class::isInstance).map(ViewEndPayload.class::cast)
                    .filter(end -> room.getUUID().equals(end.roomId())
                            && room.level().dimension().location().equals(end.dimension())).count();
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                var list = player.getServer().getPlayerList();
                if (list.getPlayer(player.getUUID()) == player) {
                    list.remove(player);
                }
            }
            finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static final class Recorder extends Connection {
        private final List<CustomPacketPayload> views = new ArrayList<>();

        private Recorder() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
            record(packet);
            super.send(packet, listener, flush);
        }

        private void record(Packet<?> packet) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(this::record);
            }
            else if (packet instanceof ClientboundCustomPayloadPacket custom
                    && (custom.payload() instanceof ViewStartPayload || custom.payload() instanceof ViewEndPayload)) {
                views.add(custom.payload());
            }
        }
    }
}
