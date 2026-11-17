package rotp.addons.beachboy.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.event.BeachBoyEvents;
import rotp.addons.beachboy.util.BeachBoySettings;
import rotp.core.powersystem.PowerClass;

/**
 * Pack log of 2026-11-06: sound sense took a seed from the world's random on a world generation thread, and C2ME threw.
 * The handler runs here on a plain thread with the server thread waiting for it, so nothing else moves the seeded
 * random. Then the server thread, not the plain one, must send what a sound sense user hears.
 */
@GameTestHolder(BeachBoyAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeachBoySoundSenseThreadGameTests {
    private static final long SEED = 0x5EEDL;
    private static final long WORKER_TIMEOUT_MILLIS = 5000L;
    private static final Holder<SoundEvent> SOUND = SoundEvents.NOTE_BLOCK_IMITATE_PIGLIN;

    private BeachBoySoundSenseThreadGameTests() {}

    @GameTest(template = "empty", templateNamespace = BeachBoyAddon.MOD_ID, timeoutTicks = 40)
    public static void soundAtPositionOffTheServerThreadLeavesTheWorldAlone(GameTestHelper helper) {
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        ServerLevel level = helper.getLevel();
        Hearer inRange = new Hearer(helper, "bb-sense-in-range", at.add(40.0D, 0.0D, 0.0D));
        Hearer beyond = new Hearer(helper, "bb-sense-beyond", at.add(200.0D, 0.0D, 0.0D));
        var event = new PlayLevelSoundEvent.AtPosition(level, at, SOUND, SoundSource.BLOCKS, 1.0F, 1.0F);
        runOffTheServerThread(helper, level, event, BeachBoyEvents::onSoundAtPosition);
        helper.assertTrue(inRange.positionSounds() == 0,
                "the sound was sent from the thread that played it, not from the server thread");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(inRange.positionSounds() == 1,
                        "sound sense did not hear a sound played off the server thread: "
                                + inRange.positionSounds()))
                .thenExecute(() -> {
                    helper.assertTrue(beyond.positionSounds() == 0,
                            "sound sense heard past its 128 blocks");
                    inRange.close();
                    beyond.close();
                })
                .thenSucceed();
    }

    /**
     * R772.1 OQ-A02 (donor PlayerListBeachBoySoundMixin.isSoundPacket: SPlaySoundEffectPacket and SPlaySoundPacket
     * only): a sound bound to an entity stays in its own range, through the level's real path; the same sound played at
     * the entity's place still reaches the sound sense user past it.
     */
    @GameTest(template = "empty", templateNamespace = BeachBoyAddon.MOD_ID, timeoutTicks = 40)
    public static void anEntitysOwnSoundStaysInItsRangeAsInTheDonor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Hearer far = new Hearer(helper, "bb-sense-entity", pig.position().add(40.0D, 0.0D, 0.0D));
        Vec3 at = pig.position();
        level.playSound(null, pig, SOUND.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.playSound(null, at.x, at.y, at.z, SOUND.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(far.entitySounds() == 0,
                            "the donor widened no sound bound to an entity, sent " + far.entitySounds());
                    // the batch's other tests play the same sound a few blocks off, inside a sense user's reach
                    helper.assertTrue(far.positionSoundsAt(at) == 1,
                            "Fixture: the sound at the entity's place did not reach the sense user: "
                                    + far.positionSoundsAt(at) + " (" + far.positionSounds() + " of the sound in all)");
                    far.close();
                })
                .thenSucceed();
    }

    // On the server thread nothing waits: the user hears it in the same tick, and the world's random still stays out.
    @GameTest(template = "empty", templateNamespace = BeachBoyAddon.MOD_ID, timeoutTicks = 20)
    public static void soundOnTheServerThreadIsHeardAtOnce(GameTestHelper helper) {
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        ServerLevel level = helper.getLevel();
        Hearer inRange = new Hearer(helper, "bb-sense-now", at.add(40.0D, 0.0D, 0.0D));
        Hearer near = new Hearer(helper, "bb-sense-near", at.add(8.0D, 0.0D, 0.0D));
        RandomSource worldRandom = level.getRandom();
        worldRandom.setSeed(SEED);
        long expected = RandomSource.create(SEED).nextLong();
        BeachBoyEvents.onSoundAtPosition(
                new PlayLevelSoundEvent.AtPosition(level, at, SOUND, SoundSource.BLOCKS, 1.0F, 1.0F));
        long next = worldRandom.nextLong();
        worldRandom.setSeed(RandomSupport.generateUniqueSeed());
        helper.assertTrue(next == expected, "the handler took a number from the world's random");
        helper.assertTrue(inRange.positionSounds() == 1,
                "sound sense did not hear the sound in the same tick: " + inRange.positionSounds());
        helper.assertTrue(near.positionSounds() == 0,
                "a user in the sound's own range was sent it twice");
        inRange.close();
        near.close();
        helper.succeed();
    }

    // The whole server path with a real block: vanilla sends the gate's sound inside its 16 blocks, this add-on past them.
    @GameTest(template = "empty", templateNamespace = BeachBoyAddon.MOD_ID, timeoutTicks = 20)
    public static void poweredFenceGateReachesASenseUserPastItsOwnRange(GameTestHelper helper) {
        BlockPos gate = new BlockPos(1, 2, 1);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(gate));
        Hearer near = new Hearer(helper, "bb-gate-near", at.add(8.0D, 0.0D, 0.0D));
        Hearer far = new Hearer(helper, "bb-gate-far", at.add(40.0D, 0.0D, 0.0D));
        Hearer senseOff = new Hearer(helper, "bb-gate-off", at.add(0.0D, 0.0D, 40.0D));
        Hearer beyond = new Hearer(helper, "bb-gate-beyond", at.add(200.0D, 0.0D, 0.0D));
        BeachBoySettings.toggle(senseOff.player, BeachBoySettings.Toggle.SOUND_SENSE);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE);
        helper.setBlock(gate.east(), Blocks.REDSTONE_BLOCK);
        helper.assertTrue(far.gateSounds() == 1,
                "a sound sense user 40 blocks away was sent the gate's sound " + far.gateSounds() + " times");
        helper.assertTrue(near.gateSounds() == 1,
                "a user inside the gate's own range was sent its sound " + near.gateSounds() + " times");
        helper.assertTrue(senseOff.gateSounds() == 0, "a user with sound sense off was sent the gate's far sound");
        helper.assertTrue(beyond.gateSounds() == 0, "sound sense heard the gate past its 128 blocks");
        near.close();
        far.close();
        senseOff.close();
        beyond.close();
        helper.succeed();
    }

    private static <E> void runOffTheServerThread(GameTestHelper helper, ServerLevel level, E event,
            Consumer<E> handler) {
        RandomSource worldRandom = level.getRandom();
        worldRandom.setSeed(SEED);
        long expected = RandomSource.create(SEED).nextLong();
        Throwable[] thrown = new Throwable[1];
        Thread worker = new Thread(() -> {
            try {
                handler.accept(event);
            }
            catch (Throwable failure) {
                thrown[0] = failure;
            }
        }, "beach-boy-sound-sense-test");
        worker.start();
        try {
            worker.join(WORKER_TIMEOUT_MILLIS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        long next = worldRandom.nextLong();
        worldRandom.setSeed(RandomSupport.generateUniqueSeed());
        helper.assertTrue(!worker.isAlive(), "the handler did not return on its own thread");
        helper.assertTrue(thrown[0] == null, "the handler threw off the server thread: " + thrown[0]);
        helper.assertTrue(next == expected,
                "the handler took a number from the world's random off the server thread");
    }

    /** A Beach Boy user with sound sense on, in the server's player list, whose outgoing sound packets a test reads. */
    private static final class Hearer {
        private final ServerPlayer player;
        private final Recorder connection = new Recorder();
        private final EmbeddedChannel channel;

        private Hearer(GameTestHelper helper, String name, Vec3 pos) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                    cookie.clientInformation());
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setPos(pos);
            PowerClass.STAND.attachGet(player).setStand(BeachBoyAddon.BEACH_BOY.get());
        }

        private long positionSounds() {
            synchronized (connection.packets) {
                return connection.packets.stream()
                        .filter(packet -> packet instanceof ClientboundSoundPacket sound
                                && sound.getSound().equals(SOUND))
                        .count();
            }
        }

        // a sound packet keeps its place as a float of eighths: far out it rounds to about half a block
        private long positionSoundsAt(Vec3 at) {
            synchronized (connection.packets) {
                return connection.packets.stream()
                        .filter(packet -> packet instanceof ClientboundSoundPacket sound
                                && sound.getSound().equals(SOUND)
                                && Math.abs(sound.getX() - at.x) < 1.0D
                                && Math.abs(sound.getY() - at.y) < 1.0D
                                && Math.abs(sound.getZ() - at.z) < 1.0D)
                        .count();
            }
        }

        private long gateSounds() {
            synchronized (connection.packets) {
                return connection.packets.stream()
                        .filter(packet -> packet instanceof ClientboundSoundPacket sound
                                && sound.getSound().value() == SoundEvents.FENCE_GATE_OPEN)
                        .count();
            }
        }

        private long entitySounds() {
            synchronized (connection.packets) {
                return connection.packets.stream()
                        .filter(packet -> packet instanceof ClientboundSoundEntityPacket sound
                                && sound.getSound().equals(SOUND))
                        .count();
            }
        }

        private void close() {
            try {
                player.connection.disconnect(Component.literal("Beach Boy sound sense test complete"));
            }
            finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static final class Recorder extends Connection {
        private final List<Packet<?>> packets = new ArrayList<>();

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
            else {
                synchronized (packets) {
                    packets.add(packet);
                }
            }
        }
    }
}
