package rotp.addons.sexpistols;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsIdleChoice;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;

/**
 * Next Album 0.1.13 SexPistolsEntity's idles: the idle variant rolled at the summon, and the idle_random pose after 100 to
 * 300 idle ticks, 20 ticks long, then 7 ticks of recovery (tickIdleRandomAnimation, tickRecoveryAnimation). The server
 * picks them; each member's copy built only from the entity data packets its viewer got must show the same choice.
 */
@GameTestHolder(SexPistolsIds.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SexPistolsIdleGameTests {
    private static final int LIMIT_TICKS = 40 + SexPistolsIdleChoice.IDLE_RANDOM_MIN_DELAY
            + SexPistolsIdleChoice.IDLE_RANDOM_EXTRA_DELAY + 60;

    private SexPistolsIdleGameTests() {}

    @GameTest(templateNamespace = "rotp_addon_api_smoke", template = "empty", batch = "sexPistolsIdle",
            timeoutTicks = LIMIT_TICKS + 40 + GameTestChunkTickets.WAIT_TICKS)
    public static void membersShowTheIdleTheServerPicksAndSyncs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = SexPistolsGameTests.makeCompatibleMockPlayer(helper, "sex-pistols-idle-choice");
        SexPistolsGameTests.positionPlayer(helper, owner, 1, 2, 1);
        owner.setNoGravity(true);
        SexPistolsGameTests.prepareSexPistols(helper, owner);
        SexPistolsCoordinator coordinator = SexPistolsAttachments.coordinator(owner);
        Map<Integer, Watch> watches = new HashMap<>();
        List<Packet<?>> packets = new ArrayList<>();
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            SexPistolsEntity member = coordinator.member(level, number);
            SexPistolsEntity viewerCopy = (SexPistolsEntity) member.getType().create(level);
            helper.assertTrue(viewerCopy != null, "No copy of member No." + number + " for its viewer.");
            helper.assertTrue(member.getIdleVariant() < SexPistolsIdleChoice.IDLE_VARIANTS,
                    "Member No." + number + " rolled idle variant " + member.getIdleVariant() + " at its summon.");
            // what a viewer gets on pairing (ServerEntity.sendPairingData), then the changes through a tracker of the
            // member's own: the level's tracker sends before the entities tick, so this tick's changes are still dirty
            viewerCopy.getEntityData().assignValues(member.getEntityData().getNonDefaultValues());
            ServerEntity tracker = new ServerEntity(level, member, member.getType().updateInterval(),
                    member.getType().trackDeltas(), packets::add);
            watches.put(member.getId(), new Watch(member, viewerCopy, tracker));
        }
        int[] tick = {0};
        GameTestChunkTickets.forTest(helper).cover(owner.position()).onEachTickWhenTicking(
                () -> {
                    coordinator.cleanup(level, owner);
                    owner.connection.disconnect(Component.literal("Sex Pistols idle GameTest complete"));
                },
                () -> {
                    tick[0]++;
                    readPackets(packets, watches);
                    for (Watch watch : watches.values()) {
                        watch.step(helper, tick[0]);
                        if (watch.done()) {
                            coordinator.cleanup(level, owner);
                            owner.connection.disconnect(Component.literal("Sex Pistols idle GameTest complete"));
                            helper.succeed();
                            return;
                        }
                    }
                    helper.assertTrue(tick[0] <= LIMIT_TICKS, "No member went through a whole idle_random and its "
                            + "recovery within " + LIMIT_TICKS + " ticks: " + watches.values());
                });
    }

    private static void readPackets(List<Packet<?>> packets, Map<Integer, Watch> watches) {
        for (Watch watch : watches.values()) {
            watch.tracker.sendChanges();
        }
        for (Packet<?> packet : packets) {
            if (packet instanceof ClientboundSetEntityDataPacket data && watches.containsKey(data.id())) {
                watches.get(data.id()).copy.getEntityData().assignValues(data.packedItems());
            }
        }
        packets.clear();
    }

    private static final class Watch {
        final SexPistolsEntity member;
        final SexPistolsEntity copy;
        final ServerEntity tracker;
        int lastRandom;
        int lastRecovery;
        boolean sawNotIdle;
        int idleSince = -1;
        int startedAt = -1;
        int chosenVariant = -1;
        boolean copySawTheChoice;
        boolean copySawRecovery;
        boolean recovered;

        Watch(SexPistolsEntity member, SexPistolsEntity copy, ServerEntity tracker) {
            this.member = member;
            this.copy = copy;
            this.tracker = tracker;
        }

        void step(GameTestHelper helper, int tick) {
            String who = "member No." + member.getMemberNumber() + " at tick " + tick;
            int random = member.getIdleRandomTicks();
            int recovery = member.getIdleRecoveryTicks();
            // measured only from a seen end of the summon: before the first observed tick the member may have idled
            if (!member.isInIdlePose()) {
                sawNotIdle = true;
            }
            else if (idleSince < 0 && sawNotIdle) {
                idleSince = tick;
            }
            if (random > 0 && lastRandom == 0) {
                helper.assertTrue(random == SexPistolsIdleChoice.IDLE_RANDOM_TICKS && lastRecovery == 0,
                        "1.16 started idle_random at 20 ticks, after any recovery: " + who + " random=" + random
                                + ", recovery before=" + lastRecovery);
                // the tick the summon ends already counts the delay down once, as in 1.16's tick order
                helper.assertTrue(idleSince < 0 || tick - idleSince >= SexPistolsIdleChoice.IDLE_RANDOM_MIN_DELAY - 1,
                        "1.16 waited 100 to 300 idle ticks before idle_random: " + who + " idle since " + idleSince);
                if (startedAt < 0) {
                    startedAt = tick;
                    chosenVariant = member.getIdleRandomVariant();
                    helper.assertTrue(chosenVariant < SexPistolsIdleChoice.IDLE_RANDOM_VARIANTS,
                            "idle_random variant out of 1.16's five: " + chosenVariant);
                }
            }
            else if (lastRandom > 0) {
                helper.assertTrue(random == lastRandom - 1,
                        "idle_random must count down a tick at a time: " + who + " " + lastRandom + " -> " + random);
                if (random == 0) {
                    helper.assertTrue(recovery == SexPistolsIdleChoice.IDLE_RECOVERY_TICKS,
                            "1.16 blended idle_random back over 7 recovery ticks: " + who + " recovery=" + recovery);
                }
            }
            else if (lastRecovery > 0) {
                helper.assertTrue(random == 0 && recovery == lastRecovery - 1,
                        "the recovery must count down a tick at a time: " + who + " " + lastRecovery + " -> " + recovery);
                if (recovery == 0 && startedAt >= 0) {
                    recovered = true;
                }
            }
            if (startedAt >= 0 && copy.getIdleRandomTicks() > 0) {
                helper.assertTrue(copy.getIdleRandomVariant() == chosenVariant,
                        "the viewer's copy shows idle_random variant " + copy.getIdleRandomVariant()
                                + ", the server picked " + chosenVariant + ": " + who);
                copySawTheChoice = true;
            }
            if (copySawTheChoice && copy.getIdleRecoveryTicks() > 0) {
                copySawRecovery = true;
            }
            lastRandom = random;
            lastRecovery = recovery;
        }

        boolean done() {
            return recovered && copySawTheChoice && copySawRecovery
                    && copy.getIdleRandomTicks() == 0 && copy.getIdleRecoveryTicks() == 0
                    && copy.getIdleVariant() == member.getIdleVariant();
        }

        @Override
        public String toString() {
            return "No." + member.getMemberNumber() + "{idle since " + idleSince + ", started " + startedAt
                    + ", copy saw choice " + copySawTheChoice + ", copy saw recovery " + copySawRecovery
                    + ", recovered " + recovered + ", random " + member.getIdleRandomTicks() + ", recovery "
                    + member.getIdleRecoveryTicks() + "}";
        }
    }
}
