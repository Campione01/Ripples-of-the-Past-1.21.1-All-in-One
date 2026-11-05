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
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * KC-MIH (RS-T3 T3-13, MIH-FRAME.md mode A): an eraser whose own time runs at 1/m erases for m times as many world
 * ticks, and his decoy, which the same slow-down holds back (Made in Heaven cancels its skipped ticks), lasts as long.
 * Here a test provider holds a tagged player at a third of his time and a listener cancels two of every three ticks
 * of his decoy, as Made in Heaven's mode A does; the player's key stays down the whole time.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonSlowedEraseGameTests {
    private static final String SLOW_TAG = "rotp_kingcrimson_gametest_third_time";
    private static final float RATE = 1.0F / 3.0F;
    private static final short ERASE_KEY = 11;
    private static boolean providerRegistered;

    private KingCrimsonSlowedEraseGameTests() {}

    // Providers cannot be unregistered: this one answers only for an entity carrying the test's tag.
    private static synchronized void registerProvider() {
        if (!providerRegistered) {
            EntityTimeRates.register(KingCrimsonAddon.id("gametest_third_time"), (entity, channel) ->
                    (channel == EntityTimeRates.Channel.BEING || channel == EntityTimeRates.Channel.STAND_ACTIONS)
                            && entity.getTags().contains(SLOW_TAG)
                            ? RATE : EntityTimeRates.NO_RATE);
            providerRegistered = true;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 760, batch = "kc_erase_own_time")
    public static void anEraseHeldAtAThirdOfItsOwnTimeLastsThreeTimesLongerAndKeepsItsDecoy(GameTestHelper helper) {
        registerProvider();
        ServerPlayer player = makePlayer(helper);
        player.addTag(SLOW_TAG);
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        helper.assertTrue(KingCrimsonAddon.KING_CRIMSON.get().summon(player, power), "Setup: Stand summon failed");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        power.setStamina(power.getMaxStamina());

        int[] decoyTicks = {0};
        Consumer<EntityTickEvent.Pre> holdBack = event -> {
            if (event.getEntity() instanceof KingCrimsonTimeEraseDecoyEntity decoy
                    && decoy.getOwner() == player && decoyTicks[0]++ % 3 != 0) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTickEvent.Pre.class, holdBack);
        long[] startedAt = {-1L};
        long[] endedAt = {-1L};
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(holdBack);
            AbilityInput.keyRelease(ERASE_KEY, player);
            player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
            KingCrimsonTemporalStateService.terminate(player);
            stand.discard();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        };
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

        helper.runAfterDelay(2, () -> guarded(cleanup, () -> {
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
        }));
        helper.runAfterDelay(300, () -> guarded(cleanup, () -> assertStillErasing(helper, player, startedAt[0], 298)));
        helper.runAfterDelay(560, () -> guarded(cleanup, () -> assertStillErasing(helper, player, startedAt[0], 558)));
        helper.runAfterDelay(700, () -> guarded(cleanup, () -> {
            long length = endedAt[0] - startedAt[0];
            helper.assertTrue(endedAt[0] >= 0L && length >= 585L && length <= 615L,
                    "200 of the eraser's own ticks at a third of his time are about 600 world ticks; the held erase"
                            + " lasted " + (endedAt[0] < 0L ? "beyond 700" : String.valueOf(length)));
            helper.assertTrue(power.getAbilityCooldown("kingcrimson_timeerase") > 0,
                    "The erase did not end through its own end (no cooldown was set)");
            helper.assertTrue(decoys(helper, player).isEmpty(), "The decoy outlived its erase");
            cleanup.run();
            helper.succeed();
        }));
    }

    private static void assertStillErasing(GameTestHelper helper, ServerPlayer player, long startedAt, int ticks) {
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(startedAt >= 0L && KingCrimsonAddon.temporalState(player).isErasing(now),
                "The held erase at a third of its time ended within " + ticks + " world ticks: started "
                        + startedAt + ", now " + now);
        helper.assertTrue(decoys(helper, player).size() == 1,
                "The decoy, held back with its eraser, vanished " + (now - startedAt)
                        + " world ticks into his slowed erase");
    }

    private static List<KingCrimsonTimeEraseDecoyEntity> decoys(GameTestHelper helper, ServerPlayer player) {
        return helper.getLevel().getEntitiesOfClass(KingCrimsonTimeEraseDecoyEntity.class,
                player.getBoundingBox().inflate(16.0D), decoy -> !decoy.isRemoved());
    }

    private static void guarded(Runnable cleanup, Runnable check) {
        try {
            check.run();
        }
        catch (RuntimeException | AssertionError failure) {
            cleanup.run();
            throw failure;
        }
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
