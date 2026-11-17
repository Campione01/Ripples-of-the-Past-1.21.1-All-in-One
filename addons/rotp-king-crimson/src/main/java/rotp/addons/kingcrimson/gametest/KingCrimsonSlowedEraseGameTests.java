package rotp.addons.kingcrimson.gametest;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Owner 2026-11-05: an erase lasts in the world's time, not its user's own ("時間刪除的時間應該按世界時間算，不按玩家自時間算"), so it
 * shrinks as the world accelerates. In Made in Heaven's mode A the eraser is held at 1/m and the world runs in plain
 * ticks: 200 ticks, a listener cancelling two of every three ticks of his decoy as that mode does. In mode B he keeps
 * his time and the world runs m times faster: 200 / m ticks. Test providers stand in for Made in Heaven; his key stays
 * down the whole time.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonSlowedEraseGameTests {
    private static final String SLOW_TAG = "rotp_kingcrimson_gametest_third_time";
    private static final float RATE = 1.0F / 3.0F;
    private static final short ERASE_KEY = 11;
    private static boolean providerRegistered;
    private static float worldRate = LevelTimeRates.NO_RATE;

    private KingCrimsonSlowedEraseGameTests() {}

    // Providers cannot be unregistered: these answer only for an entity carrying the test's tag and while a test sets a rate.
    private static synchronized void registerProvider() {
        if (!providerRegistered) {
            EntityTimeRates.register(KingCrimsonAddon.id("gametest_third_time"), (entity, channel) ->
                    (channel == EntityTimeRates.Channel.BEING || channel == EntityTimeRates.Channel.STAND_ACTIONS)
                            && entity.getTags().contains(SLOW_TAG)
                            ? RATE : EntityTimeRates.NO_RATE);
            LevelTimeRates.register(KingCrimsonAddon.id("gametest_world_time"), (level, channel) ->
                    channel == LevelTimeRates.Channel.TIME ? worldRate : LevelTimeRates.NO_RATE);
            providerRegistered = true;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 320, batch = "kc_erase_world_time_held_back")
    public static void heldBackByTheAccelerationAnEraseStillLastsItsWorldTicks(GameTestHelper helper) {
        registerProvider();
        worldRate = 3.0F;
        Erase erase = new Erase(helper);
        erase.player.addTag(SLOW_TAG);
        int[] decoyTicks = {0};
        Consumer<EntityTickEvent.Pre> holdBack = event -> {
            if (event.getEntity() instanceof KingCrimsonTimeEraseDecoyEntity decoy
                    && decoy.getOwner() == erase.player && decoyTicks[0]++ % 3 != 0) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTickEvent.Pre.class, holdBack);
        erase.onCleanup(() -> NeoForge.EVENT_BUS.unregister(holdBack));
        helper.runAfterDelay(2, erase::press);
        helper.runAfterDelay(150, () -> erase.guarded(() -> erase.assertStillErasing(148)));
        helper.runAfterDelay(240, () -> erase.guarded(() -> erase.assertEndedAfter(195L, 205L,
                "A held-back eraser's erase lasts its 200 world ticks")));
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "kc_erase_world_time_accelerating")
    public static void asTheWorldRunsFourTimesFasterAnEraseLastsAQuarter(GameTestHelper helper) {
        registerProvider();
        worldRate = 4.0F;
        Erase erase = new Erase(helper);
        helper.runAfterDelay(2, erase::press);
        helper.runAfterDelay(30, () -> erase.guarded(() -> erase.assertStillErasing(28)));
        helper.runAfterDelay(80, () -> erase.guarded(() -> erase.assertEndedAfter(47L, 53L,
                "200 ticks of the world's time at 4 times its speed are 50 server ticks")));
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "kc_erase_world_time_rising")
    public static void anEraseShortensFromTheMomentTheWorldAccelerates(GameTestHelper helper) {
        registerProvider();
        worldRate = LevelTimeRates.NO_RATE;
        Erase erase = new Erase(helper);
        helper.runAfterDelay(2, erase::press);
        // Counted from the erase's own start, which can come some ticks after the press on a loaded server.
        helper.onEachTick(() -> {
            if (!erase.player.isRemoved() && erase.startedAt[0] >= 0L && erase.endedAt[0] < 0L
                    && helper.getLevel().getGameTime() - erase.startedAt[0] >= 100L) {
                worldRate = 2.0F;
            }
        });
        helper.runAfterDelay(200, () -> erase.guarded(() -> erase.assertEndedAfter(147L, 153L,
                "100 plain ticks, then the other 100 of the world's time at twice its speed, are 150 server ticks")));
    }

    private static final class Erase {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final StandPower power;
        private final StandEntity stand;
        private final long[] startedAt = {-1L};
        private final long[] endedAt = {-1L};
        // The tests' 200 ticks: a limit of 10 s at Resolve 4.
        private final int oldLimit = KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.get();
        private Runnable extraCleanup = () -> {};

        private Erase(GameTestHelper helper) {
            this.helper = helper;
            KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(10);
            player = makePlayer(helper);
            power = PowerClass.STAND.attachGet(player);
            power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
            power.skipProgression();
            helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Setup: Stand summon failed");
            stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            power.setStamina(power.getMaxStamina());
            // Embedded fixture connections have no server network loop to drive the player's full tick.
            helper.onEachTick(() -> {
                if (player.isRemoved()) {
                    return;
                }
                player.doTick();
                KingCrimsonTemporalState state = KingCrimsonAddon.temporalState(player);
                long now = helper.getLevel().getGameTime();
                if (startedAt[0] < 0L && state.isErasing(now)) {
                    startedAt[0] = state.startedAt();
                }
                else if (startedAt[0] >= 0L && endedAt[0] < 0L && state.isIdle()) {
                    endedAt[0] = now;
                }
            });
        }

        private void onCleanup(Runnable cleanup) {
            extraCleanup = cleanup;
        }

        private void press() {
            guarded(() -> {
                Ability erase = power.getAbility("kingcrimson_timeerase");
                BufferingState buffering = BufferingState.clickCanBuffer();
                FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    helper.assertTrue(erase != null && AbilityInput.withConditionCheck(erase, player),
                            "Setup: Time Erase is not usable");
                    AbilityInput.keyPress(ERASE_KEY, erase, player, input, InputMethod.HOLD, 0.0F, buffering,
                            erase.getAbilityId());
                }
                finally {
                    input.release();
                }
                helper.assertTrue(buffering.isActionSuccess && !buffering.shouldBuffer,
                        "Setup: the held Time Erase did not start");
            });
        }

        private void assertStillErasing(int ticks) {
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(startedAt[0] >= 0L && KingCrimsonAddon.temporalState(player).isErasing(now),
                    "The held erase ended within " + ticks + " world ticks: started " + startedAt[0] + ", now " + now);
            helper.assertTrue(decoys(helper, player).size() == 1,
                    "The decoy vanished " + (now - startedAt[0]) + " world ticks into its erase");
        }

        private void assertEndedAfter(long atLeast, long atMost, String rule) {
            long length = endedAt[0] - startedAt[0];
            helper.assertTrue(endedAt[0] >= 0L && length >= atLeast && length <= atMost,
                    rule + "; the held erase lasted " + (endedAt[0] < 0L ? "longer than this test" : String.valueOf(length)));
            helper.assertTrue(power.getAbilityCooldown("kingcrimson_timeerase") > 0,
                    "The erase did not end through its own end (no cooldown was set)");
            helper.assertTrue(decoys(helper, player).isEmpty(), "The decoy outlived its erase");
            cleanup();
            helper.succeed();
        }

        private void cleanup() {
            worldRate = LevelTimeRates.NO_RATE;
            KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(oldLimit);
            extraCleanup.run();
            AbilityInput.keyRelease(ERASE_KEY, player);
            player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            KingCrimsonTemporalStateService.terminate(player);
            stand.discard();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }

        private void guarded(Runnable check) {
            try {
                check.run();
            }
            catch (RuntimeException | AssertionError failure) {
                cleanup();
                throw failure;
            }
        }
    }

    private static List<KingCrimsonTimeEraseDecoyEntity> decoys(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(KingCrimsonTimeEraseDecoyEntity.class,
                player.getBoundingBox().inflate(16.0D), decoy -> !decoy.isRemoved());
    }

    private static ServerPlayer makePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "kc-slowed-erase");
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        BlockPos spawn = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(Vec3.atBottomCenterOf(spawn));
        player.setNoGravity(true);
        level.addNewPlayer(player);
        return player;
    }

    private static final class SilentListener extends ServerGamePacketListenerImpl {
        private SilentListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {}
    }
}
