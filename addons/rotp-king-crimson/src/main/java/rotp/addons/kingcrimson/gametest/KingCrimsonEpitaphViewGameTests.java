package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.mojang.datafixers.util.Pair;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphMode;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.PacketBytes;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.addons.kingcrimson.network.EpitaphPhantomFramePayload;
import rotp.addons.kingcrimson.network.EpitaphSessionPayload;
import rotp.core.network.s2c.StandSkinSoundPacket;

/**
 * Epitaph, what the one who foresees is shown (EPITAPH-ARCH 2.2): the phantoms reach him as frames of the packets a
 * client would get of them, him alone, ahead of the bodies, each packet as it was when it was made. What his client
 * does with them no server test can see.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphViewGameTests {
    private KingCrimsonEpitaphViewGameTests() {}

    private static KingCrimsonEraseFixture.Listener listener(ServerPlayer player) {
        return KingCrimsonEraseFixture.listener(player);
    }

    private static List<Packet<?>> packetsOf(ServerLevel level, List<byte[]> frame) {
        List<Packet<?>> packets = new ArrayList<>();
        for (byte[] bytes : frame) {
            packets.addAll(PacketBytes.decode(bytes, level.registryAccess()));
        }
        return packets;
    }

    // B1
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_view_user_only")
    public static void phantomStreamReachesOnlyTheUser(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-seer", 4, 24, 10);
        ServerPlayer user = fixture.user;
        // He tracks the user: what is sent to everybody who tracks the user reaches him.
        ServerPlayer beside = fixture.witness("kc-ep-beside", 0.5D, 6.5D);
        // Both further from the zombie than the user is: a zombie goes after the nearest player.
        ServerPlayer far = fixture.other("kc-ep-far", 22.5D, 8.5D);
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 8.5D, 2.5D);
        EpitaphSession[] session = new EpitaphSession[1];
        int[] from = new int[3];
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEpitaphFixture.tracks(beside, user), "Setup: the player beside the user does not track him");
            from[0] = listener(user).sentCount();
            from[1] = listener(beside).sentCount();
            from[2] = listener(far).sentCount();
            session[0] = fixture.press(null);
            helper.assertTrue(session[0].record().track(zombie.getId()) != null, "Setup: the zombie that hunts the user is not foreseen");
        }));
        helper.runAfterDelay(75, () -> fixture.guarded(() -> {
            EpitaphSession run = session[0];
            List<EpitaphSessionPayload> told = listener(user).payloadsSince(from[0], EpitaphSessionPayload.class);
            List<EpitaphPhantomFramePayload> frames = listener(user).payloadsSince(from[0], EpitaphPhantomFramePayload.class);
            fixture.rule(!told.isEmpty() && told.get(0).kind() == EpitaphSessionPayload.Kind.BEGIN && told.get(0).session() == run.id()
                    && told.get(0).mode() == run.mode() && told.get(0).foresightTicks() == run.clock().foresight()
                    && told.get(0).phaseTicks() == run.clock().phase()
                    && IntStream.of(told.get(0).bodies()).anyMatch(id -> id == zombie.getId()),
                    "the user was not told first that his prediction began, with its mode, its reach, its phantom phase and the "
                            + "zombie that is held to its track: " + (told.isEmpty() ? "nothing" : told.get(0).kind() + " "
                            + told.get(0).mode() + " " + told.get(0).foresightTicks() + " " + told.get(0).phaseTicks()));
            int packets = 0;
            for (EpitaphPhantomFramePayload frame : frames) {
                packets += frame.packets().size();
            }
            // The bodies have lived 30 ticks, and no frame waits for them.
            fixture.rule(frames.size() >= 31 && packets > 0, "30 ticks after the press the user was sent " + frames.size()
                    + " frames holding " + packets + " packets");
            ServerPlayer[] others = { beside, far };
            for (int i = 0; i < others.length; i++) {
                int ofFrames = listener(others[i]).payloadsSince(from[i + 1], EpitaphPhantomFramePayload.class).size();
                int ofSession = listener(others[i]).payloadsSince(from[i + 1], EpitaphSessionPayload.class).size();
                fixture.rule(ofFrames == 0 && ofSession == 0, others[i].getScoreboardName() + " was sent " + ofFrames
                        + " frames and " + ofSession + " messages of a prediction that is not his");
            }
            run.end(EpitaphEnd.CANCELLED);
            List<EpitaphSessionPayload> all = listener(user).payloadsSince(from[0], EpitaphSessionPayload.class);
            fixture.rule(!all.isEmpty() && all.get(all.size() - 1).kind() == EpitaphSessionPayload.Kind.END
                    && all.get(all.size() - 1).session() == run.id(), "the user was not told that his prediction ended");
            fixture.finish("The phantoms are shown to the user alone");
        }));
    }

    // B2
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_view_first_frame")
    public static void firstFrameSpawnsWithTheBodysId(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-first", 4, 24, 10);
        ServerPlayer user = fixture.user;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        helper.runAfterDelay(10, () -> fixture.guarded(() -> {
            int from = listener(user).sentCount();
            EpitaphSession session = fixture.press(null);
            List<EpitaphPhantomFramePayload> frames = listener(user).payloadsSince(from, EpitaphPhantomFramePayload.class);
            helper.assertTrue(!frames.isEmpty() && frames.get(0).tick() == 0 && frames.get(0).session() == session.id(),
                    "Setup: the press sent the user no frame of the press itself");
            ClientboundAddEntityPacket made = null;
            for (Packet<?> packet : packetsOf(fixture.level, frames.get(0).packets())) {
                if (packet instanceof ClientboundAddEntityPacket add && add.getId() == zombie.getId()) {
                    made = add;
                }
            }
            // By key: the room may have lived to its end within the press, and then holds no phantom that has not ended.
            SandboxRoom.Phantom copy = session.room().info(ActorKey.of(zombie));
            Entity phantom = copy != null ? copy.entity : null;
            fixture.rule(made != null && phantom != null, "the first frame holds no packet that makes an entity with the id of the "
                    + "zombie, " + zombie.getId());
            if (made != null && phantom != null) {
                Vec3 at = new Vec3(made.getX(), made.getY(), made.getZ());
                fixture.rule(made.getType() == EntityType.ZOMBIE && at.distanceTo(zombie.position()) < 0.01D,
                        "the first frame makes a " + made.getType().toShortString() + " " + at.distanceTo(zombie.position())
                                + " blocks from where the zombie stands");
                fixture.rule(!made.getUUID().equals(zombie.getUUID()),
                        "the first frame makes the phantom of the zombie with the UUID of the zombie itself");
                fixture.rule(made.getUUID().equals(phantom.getUUID()), "the UUID in the first frame is not the one the phantom has");
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The first frame of a prediction");
        }));
    }

    // What the core sends a player who begins to track an entity goes straight to him: of a phantom it belongs in the frame.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_view_pairing")
    public static void whatIsSentToTheUserWhileAPhantomIsPairedComesInItsFrame(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-pairing", 4, 24, 10);
        // His packets leave by the one method of the server every packet to a client passes.
        ServerPlayer seer = fixture.base.wiredPlayer("kc-ep-wired", 4.5D, 2.0D, 6.5D);
        Zombie zombie = fixture.mob(EntityType.ZOMBIE, 12.5D, 2.5D);
        helper.runAfterDelay(10, () -> fixture.guarded(() -> {
            EpitaphSession session = EpitaphSessions.predict(seer, List.of(zombie));
            helper.assertTrue(session != null, "Setup: no prediction began");
            int from = listener(seer).sentCount();
            Vec3 sign = new Vec3(0.25D, 0.5D, -0.25D);
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            EpitaphSandbox.setViewer(seer);
            try {
                seer.connection.send(new ClientboundSetEntityMotionPacket(zombie.getId(), sign));
            }
            finally {
                EpitaphSandbox.exit();
            }
            int straight = listener(seer).sentSince(from).size();
            int inFrames = 0;
            for (int tick = 0; tick <= session.record().foresight(); tick++) {
                for (Packet<?> packet : packetsOf(fixture.level, session.stream().frame(tick))) {
                    if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.getId() == zombie.getId()
                            && Math.abs(motion.getXa() - sign.x) < 0.001D && Math.abs(motion.getYa() - sign.y) < 0.001D
                            && Math.abs(motion.getZa() - sign.z) < 0.001D) {
                        inFrames++;
                    }
                }
            }
            fixture.rule(straight == 0, "a packet of a phantom that room code sent the user reached his connection as itself");
            fixture.rule(inFrames == 1, "a packet of a phantom that room code sent the user is in his frames " + inFrames + " times");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("What is sent to the user while a phantom is paired");
        }));
    }

    // B3
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_view_ahead")
    public static void framesRunAheadOfTheBodies(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-ahead", 4, 24, 10);
        ServerPlayer user = fixture.user;
        fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        EpitaphSession[] session = new EpitaphSession[1];
        int[] from = new int[1];
        int[] ahead = { 0 };
        int[] pastTheRoom = { 0 };
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            // The room is kept one tick ahead of the bodies and no further: what the user is shown cannot pass it.
            fixture.budget(0);
            from[0] = listener(user).sentCount();
            session[0] = fixture.press(null);
            helper.assertTrue(session[0].mode() == EpitaphMode.SOLO_SLOW, "Setup: the prediction runs as " + session[0].mode());
        }));
        helper.onEachTick(() -> {
            EpitaphSession run = session[0];
            if (run == null || run.ended()) {
                return;
            }
            int sent = run.stream().sentThrough();
            ahead[0] = Math.max(ahead[0], sent - run.passed());
            if (sent > run.room().tick()) {
                pastTheRoom[0]++;
            }
        });
        helper.runAfterDelay(17, () -> fixture.guarded(() -> {
            EpitaphSession run = session[0];
            fixture.rule(run.room().tick() <= run.passed() + 1, "Setup: with no time to run ahead the room has lived "
                    + run.room().tick() + " ticks while the bodies have lived " + run.passed());
            fixture.rule(ahead[0] >= 1, "in 12 ticks the user was never sent a tick of the future the bodies had not reached yet");
            // From here the room has lived all of it, and the phantoms run through it at their own pace.
            run.foreseeAll();
        }));
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            EpitaphSession run = session[0];
            List<Integer> ticks = new ArrayList<>();
            for (EpitaphPhantomFramePayload frame : listener(user).payloadsSince(from[0], EpitaphPhantomFramePayload.class)) {
                if (frame.session() == run.id() && frame.tick() != EpitaphPhantomFramePayload.NOW) {
                    ticks.add(frame.tick());
                }
            }
            boolean inOrder = ticks.size() == run.stream().sentThrough() + 1;
            for (int i = 0; i < ticks.size(); i++) {
                inOrder &= ticks.get(i) == i;
            }
            fixture.rule(inOrder, "the user was not sent every tick of the future once and in order: " + ticks);
            // 40 ticks of the phantom phase: the bodies live a quarter of a tick each, the phantoms five thirds.
            fixture.rule(run.passed() == 10 && run.stream().sentThrough() >= run.passed() + 20, "40 ticks after the press the bodies "
                    + "are at tick " + run.passed() + " and the user has been sent the future up to tick " + run.stream().sentThrough());
            fixture.rule(pastTheRoom[0] == 0, "the user was sent ticks the room had not lived yet, " + pastTheRoom[0] + " times");
            run.end(EpitaphEnd.CANCELLED);
            fixture.finish("The frames run ahead of the bodies");
        }));
    }

    // B4
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_view_snapshot")
    public static void phantomPacketsAreSnapshotsOfTheirTick(GameTestHelper helper) {
        // Resolve 0: ten ticks of future, shown within ten ticks.
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-snapshot", 0, 24, 10);
        ServerPlayer user = fixture.user;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        int[] from = new int[1];
        helper.runAfterDelay(10, () -> fixture.guarded(() -> {
            from[0] = listener(user).sentCount();
            EpitaphSession session = fixture.press(null);
            // A packet that is written from a live object: the stack it carries is read when the packet is written.
            ItemStack live = new ItemStack(Items.DIAMOND, 3);
            session.room().show(new ClientboundSetEquipmentPacket(zombie.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, live))));
            live.setCount(7);
        }));
        helper.runAfterDelay(25, () -> fixture.guarded(() -> {
            int seen = -1;
            for (EpitaphPhantomFramePayload frame : listener(user).payloadsSince(from[0], EpitaphPhantomFramePayload.class)) {
                for (Packet<?> packet : packetsOf(fixture.level, frame.packets())) {
                    if (packet instanceof ClientboundSetEquipmentPacket worn && worn.getEntity() == zombie.getId()) {
                        for (Pair<EquipmentSlot, ItemStack> slot : worn.getSlots()) {
                            if (slot.getSecond().is(Items.DIAMOND)) {
                                seen = slot.getSecond().getCount();
                            }
                        }
                    }
                }
            }
            fixture.rule(seen == 3, seen < 0 ? "a packet made in the room never reached the user in a frame"
                    : "a packet made around a stack of 3 that grew to 7 afterwards reached the user as a stack of " + seen);
            fixture.finish("The packets of a frame are as they were when they were made");
        }));
    }

    // B5
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_view_sounds")
    public static void noPhantomSoundByDefault(GameTestHelper helper) {
        // The packets of this user leave by the own send of the server: what a room holds back does not reach him.
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-silent", 4, 24, 10, true);
        ServerPlayer user = fixture.user;
        ServerLevel level = fixture.level;
        boolean setting = KingCrimsonConfig.EPITAPH_PHANTOM_SOUNDS.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_PHANTOM_SOUNDS.set(setting));
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D);
        helper.runAfterDelay(45, () -> fixture.guarded(() -> {
            fixture.rule(!setting, "the sounds of the phantoms are switched on by default");
            for (boolean heard : new boolean[] { false, true }) {
                KingCrimsonConfig.EPITAPH_PHANTOM_SOUNDS.set(heard);
                int from = listener(user).sentCount();
                EpitaphSession session = fixture.predict(zombie);
                session.foreseeAll();
                // Whatever the zombie did in its future: room code makes one sound for certain.
                EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
                try {
                    level.playSound(null, zombie.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0F, 1.0F);
                    // And the sound of a blow of a Stand, as the core sends it: straight to a player in earshot, by his connection.
                    user.connection.send(new ClientboundCustomPayloadPacket(StandSkinSoundPacket.play(zombie.position(),
                            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ANVIL_PLACE), fixture.power, SoundSource.PLAYERS,
                            1.0F, 1.0F)));
                }
                finally {
                    EpitaphSandbox.exit();
                }
                int made = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Sound.class).size();
                int inFrames = 0;
                int blows = 0;
                for (int tick = 0; tick <= session.record().foresight(); tick++) {
                    for (Packet<?> packet : packetsOf(level, session.stream().frame(tick))) {
                        if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof StandSkinSoundPacket) {
                            blows++;
                        }
                        if (packet instanceof ClientboundSoundPacket || packet instanceof ClientboundSoundEntityPacket) {
                            inFrames++;
                        }
                    }
                }
                List<EpitaphSessionPayload> told = listener(user).payloadsSince(from, EpitaphSessionPayload.class);
                fixture.rule(made > 0, "Setup: no sound was made in the room");
                fixture.rule(!told.isEmpty() && told.get(0).sounds() == heard, "with the sounds of the phantoms "
                        + (heard ? "on" : "off") + " the client of the user is told the opposite");
                fixture.rule(heard ? inFrames > 0 : inFrames == 0, "with the sounds of the phantoms " + (heard ? "on" : "off")
                        + " the frames hold " + inFrames + " sound packets of the " + made + " sounds the room made");
                // Ruling R14: the sound of a blow is in the frame of its tick where the phantoms are heard, and never sent straight.
                int straight = listener(user).payloadsSince(from, StandSkinSoundPacket.class).size();
                fixture.rule(blows == (heard ? 1 : 0) && straight == 0, "with the sounds of the phantoms " + (heard ? "on" : "off")
                        + " the frames hold the sound of a blow of a Stand " + blows + " times, and it was sent straight to the user "
                        + straight + " times");
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.finish("The sounds of the phantoms");
        }));
    }

    // How often this player was told, since this many packets, that the mark of Epitaph on that one was set or taken away.
    private static int faceTold(ServerPlayer to, int since, ServerPlayer about) {
        int told = 0;
        for (SyncAttachmentsPayload sync : KingCrimsonEraseFixture.listener(to).payloadsSince(since, SyncAttachmentsPayload.class)) {
            if (sync.target() instanceof SyncAttachmentsPayload.EntityTarget entity && entity.entity() == about.getId()
                    && sync.types().contains(KingCrimsonAddon.EPITAPH_FORESEEING.get())) {
                told++;
            }
        }
        return told;
    }

    // R3 (rulings after stages A and B): others see the face of Epitaph on the one who foresees; one he aims at does not.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_view_face")
    public static void theFaceReachesTrackersButNoTarget(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-face", 2, 16, 10);
        ServerPlayer user = fixture.user;
        ServerPlayer beside = fixture.witness("kc-ep-face-by", 0.5D, 6.5D);
        // Straight ahead of the user: under his crosshair.
        ServerPlayer aimedAt = fixture.witness("kc-ep-face-aim", 6.5D, 2.5D);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEpitaphFixture.tracks(beside, user) && KingCrimsonEpitaphFixture.tracks(aimedAt, user),
                    "Setup: the two other players do not both track the user");
            int toBeside = KingCrimsonEraseFixture.listener(beside).sentCount();
            int toAimedAt = KingCrimsonEraseFixture.listener(aimedAt).sentCount();
            EpitaphSession session = fixture.press(aimedAt);
            helper.assertTrue(session.record().track(aimedAt.getId()) != null && session.aimsAt(aimedAt) && !session.aimsAt(beside),
                    "Setup: the player under the crosshair is not foreseen, or the one who stands by is");
            fixture.rule(user.hasData(KingCrimsonAddon.EPITAPH_FORESEEING), "while his prediction runs the user is not marked as foreseeing");
            fixture.rule(faceTold(beside, toBeside, user) == 1, "a player who sees the user was told " + faceTold(beside, toBeside, user)
                    + " times that he foresees, not once");
            fixture.rule(faceTold(aimedAt, toAimedAt, user) == 0, "the player the prediction is aimed at was told that the user foresees");
            var saved = user.saveWithoutId(new net.minecraft.nbt.CompoundTag())
                    .getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY).getAllKeys();
            fixture.rule(saved.stream().noneMatch(key -> key.contains("epitaph_foreseeing")), "the mark is saved with the user: " + saved);
            toBeside = KingCrimsonEraseFixture.listener(beside).sentCount();
            toAimedAt = KingCrimsonEraseFixture.listener(aimedAt).sentCount();
            session.end(EpitaphEnd.CANCELLED);
            fixture.rule(!user.hasData(KingCrimsonAddon.EPITAPH_FORESEEING), "his prediction over, the user is still marked as foreseeing");
            fixture.rule(faceTold(beside, toBeside, user) == 1 && faceTold(aimedAt, toAimedAt, user) == 0, "at the end of the prediction the"
                    + " player who stands by was told " + faceTold(beside, toBeside, user) + " times that the mark is gone and the one aimed at "
                    + faceTold(aimedAt, toAimedAt, user) + " times: once, and never");
            fixture.finish("The face of Epitaph is seen by those who see its user, but not by one he aims at");
        }));
    }

    // Ruling R7: in a frame what makes an entity comes before everything else.
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_frame_order")
    public static void inAFrameWhatMakesAnEntityComesFirst(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-order", 2, 16, 8);
        fixture.budget(0);
        fixture.userKeepsControl();
        ServerPlayer user = fixture.user;
        Zombie zombie = fixture.hunter(EntityType.ZOMBIE, 9.5D, 2.5D);
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.predict(zombie);
            int tick = session.stream().sentThrough() + 3;
            Entity made = EntityType.ARMOR_STAND.create(fixture.level);
            helper.assertTrue(made != null, "Setup: no armour stand");
            // As a room makes them: news of a phantom the client has, then a new entity, then news of that one.
            session.stream().show(tick, zombie.getId(), new ClientboundEntityEventPacket(zombie, (byte) 4));
            session.stream().show(tick, made.getId(), new ClientboundAddEntityPacket(made, 0, made.blockPosition()));
            session.stream().show(tick, made.getId(), new ClientboundEntityEventPacket(made, (byte) 32));
            List<String> order = new ArrayList<>();
            for (byte[] bytes : session.stream().frame(tick)) {
                for (Packet<?> packet : PacketBytes.decode(bytes, user.registryAccess())) {
                    if (packet instanceof ClientboundAddEntityPacket add && add.getId() == made.getId()) {
                        order.add("the new entity");
                    } else if (packet instanceof ClientboundEntityEventPacket event) {
                        order.add(event.getEventId() == 4 ? "news of the zombie" : "news of the new entity");
                    }
                }
            }
            fixture.rule(order.equals(List.of("the new entity", "news of the zombie", "news of the new entity")),
                    "made as news of the zombie, the new entity, news of the new entity, the frame holds them as " + order);
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("In a frame what makes an entity comes first");
        }));
    }
}
