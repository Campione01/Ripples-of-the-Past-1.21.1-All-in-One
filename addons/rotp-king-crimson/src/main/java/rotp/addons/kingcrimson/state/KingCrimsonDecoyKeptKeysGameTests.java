package rotp.addons.kingcrimson.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

/**
 * Owner's choice of 2026-11-05: Time Erase's decoy goes on with what its user's keys did at the press, on a player's
 * physics, and a killed decoy simply disappears. The user faces +X in a corridor; each test has a batch of its own.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonDecoyKeptKeysGameTests {
    private static final TicketType<BlockPos> ARENA_TICKET =
            TicketType.create("king_crimson_decoy_keys_test", BlockPos::compareTo, 400);
    // The arena chunks may still be generating when a test starts.
    private static final int ARENA_LOAD_TICKS = 140;
    private static final int CORRIDOR_LENGTH = 13;
    private static final int CORRIDOR_WIDTH = 5;
    private static final BlockPos USER_AT = new BlockPos(1, 2, 2);

    private KingCrimsonDecoyKeptKeysGameTests() {}

    private record Keys(float left, float forward, boolean jumping, boolean sneaking, boolean sprinting) {
        private static final Keys NONE = new Keys(0.0F, 0.0F, false, false, false);
        private static final Keys WALK = new Keys(0.0F, 1.0F, false, false, false);
    }

    // Speeds are read after ten ticks, when a player has reached his steady speed: a push of about 16% hardly shows
    // from rest. A player's steady speeds are 4.32 blocks a second walking, 5.61 sprinting, 1.30 sneaking.
    private static void assertSteadySpeed(GameTestHelper helper, Keys keys, double slowest, double fastest,
            Consumer<Scene> also) {
        double[] atTen = {0.0D};
        runWith(helper, keys, 10, h -> {}, player -> {}, scene -> {}, scene -> atTen[0] = scene.moved().x, 20, scene -> {
            double perSecond = scene.moved().x - atTen[0];
            helper.assertTrue(perSecond > slowest && perSecond < fastest,
                    "A player's steady speed here is between " + slowest + " and " + fastest
                            + " blocks a second; the decoy's is " + perSecond);
            also.accept(scene);
        });
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysWalk")
    public static void theDecoyWalksOnAsItsUserWasWalkingAtHisOwnSpeed(GameTestHelper helper) {
        assertSteadySpeed(helper, Keys.WALK, 4.2D, 4.45D, scene -> {
            helper.assertTrue(Math.abs(scene.decoy.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.1D) < 1.0E-6D,
                    "The decoy's speed is " + scene.decoy.getAttributeValue(Attributes.MOVEMENT_SPEED)
                            + ", not the user's 0.1: the erase's boost is his alone");
            helper.assertTrue(Math.abs(scene.moved().z) < 0.05D, "The decoy left its user's heading: " + scene.moved());
            helper.assertTrue(scene.player.position().distanceTo(scene.start) < 1.0E-6D,
                    "The fixture's user moved: " + scene.player.position());
        });
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysSprint")
    public static void theDecoySprintsOnAsItsUserWasSprinting(GameTestHelper helper) {
        assertSteadySpeed(helper, new Keys(0.0F, 1.0F, false, false, true), 5.45D, 5.75D, scene ->
                helper.assertTrue(scene.decoy.isSprinting(), "The decoy stopped sprinting"));
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysSneak")
    public static void theDecoySneaksAtASneakingSpeed(GameTestHelper helper) {
        assertSteadySpeed(helper, new Keys(0.0F, 0.3F, false, true, false), 1.2D, 1.4D, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 80, batch = "kcDecoyKeysHopping")
    public static void theDecoyHopsForwardAtAPlayersPace(GameTestHelper helper) {
        // A hop takes 12 ticks: three of them, as measured on a real player, at 3.92 blocks a second.
        double[] atTen = {0.0D};
        runWith(helper, new Keys(0.0F, 1.0F, true, false, false), 10, h -> {}, player -> {}, scene -> {},
                scene -> atTen[0] = scene.moved().x, 36, scene -> {
                    double hopped = scene.moved().x - atTen[0];
                    helper.assertTrue(hopped > 6.4D && hopped < 7.8D,
                            "A player holding forward and jump covers about 7.06 blocks in 36 ticks; the decoy covered "
                                    + hopped);
                });
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysUserInWay")
    public static void itsErasedUserDoesNotStandInTheDecoysWay(GameTestHelper helper) {
        double[] atTen = {0.0D};
        Vec3[] userAt = {Vec3.ZERO};
        runWith(helper, Keys.WALK, 10, h -> {}, player -> {},
                scene -> {
                    // Ahead on its line: the decoy walks right through him in the window measured.
                    userAt[0] = scene.player.position().add(4.0D, 0.0D, 0.0D);
                    scene.player.setPos(userAt[0]);
                },
                scene -> atTen[0] = scene.moved().x, 20, scene -> {
                    double perSecond = scene.moved().x - atTen[0];
                    helper.assertTrue(perSecond > 4.2D && perSecond < 4.45D,
                            "The decoy's speed through its user is " + perSecond + " blocks a second, not 4.32");
                    helper.assertTrue(scene.player.position().distanceTo(userAt[0]) < 1.0E-6D,
                            "The decoy shoved its erased user to " + scene.player.position());
                });
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 40, batch = "kcDecoyKeysHeldItem")
    public static void theDecoyKeepsHoldingWhatItsUserHolds(GameTestHelper helper) {
        runWith(helper, Keys.NONE, 5, h -> {},
                player -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)),
                scene -> {},
                scene -> helper.assertTrue(scene.decoy.getMainHandItem().is(Items.IRON_SWORD),
                        "The user holds an iron sword; the decoy holds " + scene.decoy.getMainHandItem()),
                1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 40, batch = "kcDecoyKeysPitch")
    public static void theDecoyLooksAsHighAsItsUserLooked(GameTestHelper helper) {
        runWith(helper, Keys.NONE, 5, h -> {}, player -> player.setXRot(30.0F), scene -> {},
                scene -> helper.assertTrue(scene.decoy.getXRot() == 30.0F,
                        "The user looks down at 30 degrees; the decoy's pitch is " + scene.decoy.getXRot()),
                1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysBack")
    public static void theDecoyBacksAndStrafesOnItsUsersHeading(GameTestHelper helper) {
        // Facing +X, his left is -Z and his back -X.
        run(helper, new Keys(1.0F, -1.0F, false, false, false), 8, scene -> helper.assertTrue(
                scene.moved().x < -0.3D && scene.moved().z < -0.3D,
                "Back and left on a +X heading is toward -X and -Z; the decoy moved " + scene.moved()));
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysIdle")
    public static void aDecoyWithoutKeysStandsStill(GameTestHelper helper) {
        run(helper, Keys.NONE, 30, scene -> helper.assertTrue(scene.moved().length() < 0.01D,
                "The decoy moved on no keys: " + scene.moved()));
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysMotion")
    public static void theDecoyCarriesOnTheMotionItsUserHad(GameTestHelper helper) {
        runWith(helper, Keys.NONE, 10, h -> {}, player -> KingCrimsonAddon.temporalState(player).notePosition(
                player.position().subtract(0.3D, 0.0D, 0.0D)), scene -> {}, scene -> {
            double slid = scene.moved().x;
            helper.assertTrue(slid > 0.2D && slid < 1.2D,
                    "A step of 0.3 slides about 0.66 blocks on stone; the decoy slid " + slid);
        }, 1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysTeleport")
    public static void aTeleportJustBeforeTheEraseIsNoMotion(GameTestHelper helper) {
        runWith(helper, Keys.NONE, 10, h -> {}, player -> KingCrimsonAddon.temporalState(player).notePosition(
                player.position().subtract(50.0D, 0.0D, 0.0D)), scene -> {},
                scene -> helper.assertTrue(scene.moved().length() < 0.01D,
                        "The decoy flew off on its user's teleport: " + scene.moved()),
                1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 100, batch = "kcDecoyKeysLedge")
    public static void aSneakingDecoyStopsAtALedge(GameTestHelper helper) {
        Consumer<GameTestHelper> ledge = h -> {
            for (int x = 4; x < CORRIDOR_LENGTH; x++) {
                for (int z = 0; z < CORRIDOR_WIDTH; z++) {
                    h.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                }
            }
        };
        runWith(helper, new Keys(0.0F, 0.3F, false, true, false), 70, ledge, player -> {}, scene -> {}, scene -> {
            helper.assertTrue(scene.decoy.isCrouching(), "The decoy is not crouching");
            helper.assertTrue(scene.moved().x > 1.0D, "The decoy did not sneak forward: " + scene.moved());
            helper.assertTrue(scene.moved().y > -1.0E-3D,
                    "A sneaking player does not walk off a ledge; the decoy dropped to " + scene.moved());
        }, 1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 100, batch = "kcDecoyKeysJump")
    public static void theDecoyKeepsJumpingWhileItsUserHeldJump(GameTestHelper helper) {
        int[] takeOffs = {0};
        boolean[] wasOnGround = {true};
        double[] peak = {0.0D};
        runWith(helper, new Keys(0.0F, 0.0F, true, false, false), 60, h -> {}, player -> {},
                scene -> scene.watch = decoy -> {
                    if (wasOnGround[0] && !decoy.onGround()) {
                        takeOffs[0]++;
                    }
                    wasOnGround[0] = decoy.onGround();
                    peak[0] = Math.max(peak[0], decoy.getY() - scene.start.y);
                },
                scene -> {
                    helper.assertTrue(takeOffs[0] >= 3,
                            "A held jump key jumps again and again; the decoy took off " + takeOffs[0] + " times");
                    helper.assertTrue(peak[0] > 1.0D, "A player's jump rises above a block; the decoy rose " + peak[0]);
                },
                1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 80, batch = "kcDecoyKeysStep")
    public static void aWalkingDecoyHopsAOneBlockStep(GameTestHelper helper) {
        runWith(helper, Keys.WALK, 40, h -> platform(h, 4, 1), player -> {}, scene -> {}, scene -> helper.assertTrue(
                scene.moved().x > 3.0D && Math.abs(scene.moved().y - 1.0D) < 0.5D,
                "The decoy did not climb the step in its way: " + scene.moved()), 1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 80, batch = "kcDecoyKeysWall")
    public static void aDecoyDoesNotHopAtAWallTooHigh(GameTestHelper helper) {
        runWith(helper, new Keys(0.0F, 1.0F, true, false, false), 40, h -> platform(h, 4, 2), player -> {}, scene -> {},
                scene -> {
                    helper.assertTrue(scene.moved().x < 2.3D,
                            "The decoy passed a wall two blocks high: " + scene.moved());
                    helper.assertTrue(scene.decoy.onGround(), "The decoy is still hopping at a wall it cannot pass");
                }, 1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 60, batch = "kcDecoyKeysRider")
    public static void aRidersKeysSteerHisMountNotTheDecoy(GameTestHelper helper) {
        runWith(helper, Keys.WALK, 30, h -> {}, player -> {
            Pig pig = EntityType.PIG.create(helper.getLevel());
            helper.assertTrue(pig != null, "Setup: no pig");
            pig.setPos(player.position());
            pig.setNoAi(true);
            helper.getLevel().addFreshEntity(pig);
            helper.assertTrue(player.startRiding(pig, true), "Setup: the user did not mount");
        }, scene -> {}, scene -> helper.assertTrue(Math.abs(scene.moved().x) < 1.0D,
                "The decoy walked off on a rider's keys: " + scene.moved()), 1, scene -> {});
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 80, batch = "kcDecoyKeysFrozen")
    public static void aDecoyWaitsWhileItsUsersEraseIsFrozen(GameTestHelper helper) {
        runWith(helper, Keys.WALK, 20, h -> {}, player -> {},
                scene -> KingCrimsonAddon.temporalState(scene.player).pauseEraseTick(),
                scene -> {
                    helper.assertTrue(scene.decoy.isOwnerErasePaused(), "Setup: the erase is not paused");
                    helper.assertTrue(Math.abs(scene.moved().x) < 0.05D,
                            "The decoy walked on while its user's erase was frozen: " + scene.moved());
                    KingCrimsonAddon.temporalState(scene.player).resumeErase();
                },
                20, scene -> helper.assertTrue(scene.moved().x > 2.0D,
                        "The decoy did not walk on when the erase went on: " + scene.moved()));
    }

    @GameTest(template = "empty", timeoutTicks = ARENA_LOAD_TICKS + 40, batch = "kcDecoyKeysKilled")
    public static void aKilledDecoySimplyDisappearsWithItsStand(GameTestHelper helper) {
        run(helper, Keys.NONE, 5, scene -> {
            StandPower decoyPower = StandPower.get(scene.decoy);
            StandEntity stand = decoyPower != null ? decoyPower.getSummonedStandEntity() : null;
            helper.assertTrue(stand != null, "Setup: the decoy has no Stand");
            helper.assertTrue(scene.decoy.hurt(helper.getLevel().damageSources().generic(), 1000.0F),
                    "Setup: the decoy took no damage");
            helper.assertTrue(scene.decoy.isRemoved(), "A killed decoy lay there dying instead of disappearing");
            helper.assertTrue(stand.isRemoved(), "A killed decoy left its Stand behind");
            helper.assertTrue(KingCrimsonTemporalStateService.isErasing(scene.player),
                    "The decoy's death ended its user's erase");
        });
    }

    private static final class Scene {
        private final ServerPlayer player;
        private final KingCrimsonTimeEraseDecoyEntity decoy;
        private final Vec3 start;
        private Consumer<KingCrimsonTimeEraseDecoyEntity> watch = decoy -> {};

        private Scene(ServerPlayer player, KingCrimsonTimeEraseDecoyEntity decoy, Vec3 start) {
            this.player = player;
            this.decoy = decoy;
            this.start = start;
        }

        private Vec3 moved() {
            return decoy.position().subtract(start);
        }
    }

    private static void run(GameTestHelper helper, Keys keys, int ticks, Consumer<Scene> check) {
        runWith(helper, keys, ticks, h -> {}, player -> {}, scene -> {}, check, 1, scene -> {});
    }

    private static void runWith(GameTestHelper helper, Keys keys, int ticks, Consumer<GameTestHelper> corridor,
            Consumer<ServerPlayer> beforeErase, Consumer<Scene> arrange, Consumer<Scene> check, int moreTicks,
            Consumer<Scene> lastCheck) {
        Arena arena = prepareArena(helper);
        corridor.accept(helper);
        Scene[] scene = {null};
        helper.onEachTick(() -> {
            if (scene[0] != null && !scene[0].decoy.isRemoved()) {
                scene[0].watch.accept(scene[0].decoy);
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> arena.assertTicking(helper))
                .thenExecute(() -> {
                    scene[0] = startErase(helper, keys, beforeErase);
                    arrange.accept(scene[0]);
                })
                .thenIdle(ticks)
                .thenExecute(() -> guarded(scene[0], arena, () -> check.accept(scene[0])))
                .thenIdle(moreTicks)
                .thenExecute(() -> guarded(scene[0], arena, () -> lastCheck.accept(scene[0])))
                .thenExecute(() -> close(scene[0], arena))
                .thenSucceed();
    }

    private static Scene startErase(GameTestHelper helper, Keys keys, Consumer<ServerPlayer> beforeErase) {
        ServerPlayer player = makePlayer(helper);
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        // Facing +X (yaw -90), with all the keys set before the press.
        player.setYRot(-90.0F);
        player.yRotO = -90.0F;
        player.setYHeadRot(-90.0F);
        player.yBodyRot = -90.0F;
        PlayerMovementInputData.get(player).setValues(
                keys.left(), keys.forward(), keys.jumping(), keys.sneaking(), keys.sprinting());
        player.setShiftKeyDown(keys.sneaking());
        player.setSprinting(keys.sprinting());
        beforeErase.accept(player);
        Vec3 start = player.position();
        helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(player),
                "Setup: the production Time Erase service refused the user");
        List<KingCrimsonTimeEraseDecoyEntity> decoys = helper.getLevel().getEntitiesOfClass(
                KingCrimsonTimeEraseDecoyEntity.class, player.getBoundingBox().inflate(8.0D));
        helper.assertTrue(decoys.size() == 1, "Setup: the Time Erase spawned " + decoys.size() + " decoys");
        return new Scene(player, decoys.get(0), start);
    }

    private static void platform(GameTestHelper helper, int fromX, int height) {
        for (int x = fromX; x < CORRIDOR_LENGTH; x++) {
            for (int y = 2; y < 2 + height; y++) {
                for (int z = 0; z < CORRIDOR_WIDTH; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static void guarded(Scene scene, Arena arena, Runnable check) {
        try {
            check.run();
        }
        catch (RuntimeException | AssertionError failure) {
            close(scene, arena);
            throw failure;
        }
    }

    private static void close(Scene scene, Arena arena) {
        scene.player.stopRiding();
        KingCrimsonTemporalStateService.terminate(scene.player);
        arena.level.removePlayerImmediately(scene.player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
    }

    private static Arena prepareArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos first = new ChunkPos(origin);
        ChunkPos last = new ChunkPos(helper.absolutePos(new BlockPos(CORRIDOR_LENGTH, 4, CORRIDOR_WIDTH)));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = first.x; x <= last.x; x++) {
            for (int z = first.z; z <= last.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                level.getChunkSource().addRegionTicket(ARENA_TICKET, chunk, 2, origin, true);
                chunks.add(chunk);
            }
        }
        for (int x = first.x - 2; x <= last.x + 2; x++) {
            for (int z = first.z - 2; z <= last.z + 2; z++) {
                level.getChunk(x, z);
            }
        }
        // The shared 1x1x1 template has a barrier shell: the corridor replaces it.
        for (int x = -1; x < CORRIDOR_LENGTH; x++) {
            for (int z = 0; z < CORRIDOR_WIDTH; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        return new Arena(level, origin, List.copyOf(chunks));
    }

    private static ServerPlayer makePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "kc-decoy-keys");
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(USER_AT)));
        player.setNoGravity(true);
        level.addNewPlayer(player);
        return player;
    }

    private record Arena(ServerLevel level, BlockPos origin, List<ChunkPos> chunks) {
        private void assertTicking(GameTestHelper helper) {
            for (ChunkPos chunk : chunks) {
                helper.assertTrue(level.isPositionEntityTicking(
                                new BlockPos(chunk.getMinBlockX(), origin.getY() + 2, chunk.getMinBlockZ())),
                        "Arena chunk " + chunk + " is not entity ticking yet");
            }
        }
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
