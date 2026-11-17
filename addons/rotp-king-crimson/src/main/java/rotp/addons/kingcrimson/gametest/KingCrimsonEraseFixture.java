package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;

/**
 * What the Time Erase tests of R772.1 share: real server players on connections that keep what was sent to them, a
 * King Crimson user at a Resolve level, his keys pressed through the production input path, a stone floor in place of
 * the shared template's shell, and the cleanup. A fixture player has no network loop, so the test ticks him.
 */
final class KingCrimsonEraseFixture {
    static final short ERASE_KEY = 21;
    static final short OTHER_KEY = 22;
    private final GameTestHelper helper;
    private final KingCrimsonChunkTickets tickets;
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Runnable> cleanups = new ArrayList<>();
    private final List<ServerPlayer> connectionTicked = new ArrayList<>();
    private boolean closed;
    private int floorLength = -1;
    private int floorWidth = -1;

    KingCrimsonEraseFixture(GameTestHelper helper) {
        this.helper = helper;
        this.tickets = new KingCrimsonChunkTickets(helper.getLevel());
        helper.onEachTick(() -> {
            if (closed) {
                return;
            }
            for (ServerPlayer player : List.copyOf(players)) {
                if (!player.isRemoved() && !player.hasDisconnected()) {
                    if (connectionTicked.contains(player)) {
                        Vec3 good = player.position();
                        player.doTick();
                        player.absMoveTo(good.x, good.y, good.z, player.getYRot(), player.getXRot());
                    } else {
                        player.doTick();
                    }
                }
            }
        });
    }

    /** A stone floor of the given size at y = 1, air above it, its chunks held ticking. */
    void floor(int length, int width) {
        hold(-1, -1, length, width);
        floorLength = length;
        floorWidth = width;
        layFloor();
    }

    /**
     * Holds every chunk of this rectangle of the test's ground at the entity-ticking level: a creature that walks out
     * of a held chunk stops ticking, wherever the test's random place put the chunk borders.
     */
    void hold(int fromX, int fromZ, int toX, int toZ) {
        for (int x = fromX; ; x = Math.min(x + 16, toX)) {
            for (int z = fromZ; ; z = Math.min(z + 16, toZ)) {
                tickets.force(helper.absoluteVec(new Vec3(x, 2.0D, z)));
                if (z >= toZ) {
                    break;
                }
            }
            if (x >= toX) {
                break;
            }
        }
    }

    // Also when the test closes: what it laid on the floor is not left under the tests placed beside it.
    private void layFloor() {
        int length = floorLength;
        int width = floorWidth;
        for (int x = -1; x <= length; x++) {
            for (int z = -1; z <= width; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    ServerPlayer player(String name, double x, double y, double z) {
        return player(name, x, y, z, false);
    }

    /**
     * From now on this player is ticked as his connection ticks a real one: where the tick left his body is not kept,
     * since it is his client that says where he is. So he stays where the server last put him.
     */
    void tickAsConnection(ServerPlayer player) {
        connectionTicked.add(player);
    }

    /**
     * A player whose packets leave through the server's own send, as a real player's do, where the other players of a
     * fixture keep them before it: what gets as far as his connection is kept.
     */
    ServerPlayer wiredPlayer(String name, double x, double y, double z) {
        return player(name, x, y, z, true);
    }

    private ServerPlayer player(String name, double x, double y, double z, boolean wired) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = wired ? new Wire() : new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new Listener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        tickets.force(at);
        player.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
        player.setYHeadRot(-90.0F);
        level.addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        // A new server player takes no damage for its first 60 ticks: the tests' players are past that.
        for (int tick = 0; tick <= 60; tick++) {
            player.tick();
        }
        players.add(player);
        return player;
    }

    /** King Crimson with every skill, at this Resolve level, summoned and ready. */
    StandPower kingCrimson(ServerPlayer player, int resolveLevel) {
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        power.setResolveLevel(resolveLevel);
        helper.assertTrue(power.getResolveLevel() == resolveLevel,
                "Setup: the Resolve level is " + power.getResolveLevel() + ", not " + resolveLevel);
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Setup: the Stand summon failed");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        entities.add(stand);
        power.setStamina(power.getMaxStamina());
        return power;
    }

    /** King Crimson as a player who earned it has it: nothing skipped, so a skill above his Resolve level is locked. */
    StandPower kingCrimsonUnskilled(ServerPlayer player, int resolveLevel) {
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.setResolveLevel(resolveLevel);
        helper.assertTrue(power.getResolveLevel() == resolveLevel && !power.wasProgressionSkipped(),
                "Setup: the Resolve level is " + power.getResolveLevel() + ", not " + resolveLevel);
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Setup: the Stand summon failed");
        entities.add(power.getSummonedStandEntity());
        power.setStamina(power.getMaxStamina());
        return power;
    }

    <T extends Entity> T track(T entity) {
        entities.add(entity);
        return entity;
    }

    void onClose(Runnable cleanup) {
        cleanups.add(cleanup);
    }

    /** The key pressed as a client presses it: the slot's ability for the variation key's state, its condition check, the press. */
    BufferingState press(ServerPlayer player, String baseAbility, short key, InputMethod method, boolean variationKey) {
        StandPower power = StandPower.get(player);
        StandManualInput.setServerVariationKey(player, variationKey);
        BufferingState state = BufferingState.clickCanBuffer();
        Ability base = power.getAbility(baseAbility);
        helper.assertTrue(base != null, "Setup: no ability " + baseAbility);
        power._curAvailableMoves.update(power, power.getMoveset());
        var container = power._curAvailableMoves.getContextVariationContainer(base);
        if (container != null && AbilityInput.withConditionCheck(container, player, method)) {
            FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AbilityInput.keyPress(key, container.ability, player, input, method, 0.0F, state, base.getAbilityId());
            }
            finally {
                input.release();
            }
        }
        if (method == InputMethod.CLICK) {
            AbilityInput.keyRelease(key, player);
        }
        return state;
    }

    BufferingState holdErase(ServerPlayer player) {
        return press(player, "kingcrimson_timeerase", ERASE_KEY, InputMethod.HOLD, false);
    }

    /** The variation key as the player's client reports it, pressed or let go between two presses of other keys. */
    void variationKey(ServerPlayer player, boolean held) {
        StandManualInput.setServerVariationKey(player, held);
    }

    void release(ServerPlayer player, short key) {
        AbilityInput.keyRelease(key, player);
    }

    static KingCrimsonTemporalState state(ServerPlayer player) {
        return KingCrimsonAddon.temporalState(player);
    }

    static boolean erasing(ServerPlayer player) {
        return KingCrimsonTemporalStateService.isErasing(player);
    }

    List<KingCrimsonTimeEraseDecoyEntity> decoys(ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(KingCrimsonTimeEraseDecoyEntity.class,
                player.getBoundingBox().inflate(48.0D), decoy -> !decoy.isRemoved() && decoy.getOwner() == player);
    }

    static Listener listener(ServerPlayer player) {
        return (Listener) player.connection;
    }

    /** Runs a check; a failure cleans the fixture up before it is thrown on. */
    void guarded(Runnable check) {
        try {
            check.run();
        }
        catch (RuntimeException | AssertionError failure) {
            close();
            throw failure;
        }
    }

    void succeed() {
        close();
        helper.succeed();
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (Runnable cleanup : cleanups) {
            try {
                cleanup.run();
            }
            catch (RuntimeException error) {
                failure = failure == null ? error : failure;
            }
        }
        for (ServerPlayer player : players) {
            try {
                StandManualInput.clear(player);
                player.stopRiding();
                player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
                KingCrimsonTemporalStateService.terminate(player);
            }
            catch (RuntimeException error) {
                failure = failure == null ? error : failure;
            }
        }
        for (Entity entity : entities) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        for (ServerPlayer player : players) {
            if (!player.isRemoved()) {
                helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            }
        }
        if (floorLength >= 0) {
            try {
                layFloor();
            }
            catch (RuntimeException error) {
                failure = failure == null ? error : failure;
            }
        }
        tickets.release();
        if (failure != null) {
            throw failure;
        }
    }

    /** The connection of a wired player: what the server's send hands it is kept, nothing is written. */
    private static final class Wire extends Connection {
        private List<Packet<?>> sink;

        private Wire() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
            if (sink != null) {
                sink.add(packet);
            }
        }
    }

    /** Keeps every packet the server sends this player. */
    static final class Listener extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> sent = new ArrayList<>();

        private final boolean wired;

        private Listener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
            wired = connection instanceof Wire;
            if (connection instanceof Wire wire) {
                wire.sink = sent;
            }
        }

        @Override
        public void send(Packet<?> packet) {
            send(packet, null);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            if (!wired) {
                sent.add(packet);
                return;
            }
            try {
                super.send(packet, listener);
            }
            catch (UnsupportedOperationException unnegotiated) {
                // A test connection has negotiated no channel: a custom payload got as far as that check.
                sent.add(packet);
            }
        }

        int sentCount() {
            return sent.size();
        }

        List<Packet<?>> sentSince(int index) {
            return List.copyOf(sent.subList(Math.min(index, sent.size()), sent.size()));
        }

        <T extends CustomPacketPayload> List<T> payloads(Class<T> type) {
            return payloadsSince(0, type);
        }

        <T extends CustomPacketPayload> List<T> payloadsSince(int index, Class<T> type) {
            List<T> found = new ArrayList<>();
            for (Packet<?> packet : sentSince(index)) {
                if (packet instanceof ClientboundCustomPayloadPacket custom && type.isInstance(custom.payload())) {
                    found.add(type.cast(custom.payload()));
                }
            }
            return found;
        }
    }
}
