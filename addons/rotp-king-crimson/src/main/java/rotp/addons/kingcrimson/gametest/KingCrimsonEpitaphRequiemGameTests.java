package rotp.addons.kingcrimson.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphMode;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.PacketBytes;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.network.EpitaphNoticePayload;
import rotp.addons.kingcrimson.network.EpitaphPhantomFramePayload;
import rotp.addons.kingcrimson.network.EpitaphSessionPayload;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The side of King Crimson of what Gold Experience Requiem does to a prediction (EPITAPH-ARCH 3.7). The Requiem add-on is
 * not asked here: a rule of the test stands in for the one it will register, and the test makes the call its Return to
 * Zero will make.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphRequiemGameTests {
    private static final int PRESS_AT = 5;
    private static final short EPITAPH_KEY = 31;
    private static final String EPITAPH = "kingcrimson_epitaph";

    private KingCrimsonEpitaphRequiemGameTests() {}

    // The passive erase would answer a foreseen hit, nobody heals by himself, and players hurt players.
    private static void quiet(KingCrimsonEpitaphFixture fixture) {
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        MinecraftServer server = fixture.level.getServer();
        boolean fights = server.isPvpAllowed();
        fixture.base.onClose(() -> {
            KingCrimsonConfig.REFLEX_ERASE.set(reflex);
            server.setPvpAllowed(fights);
        });
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        server.setPvpAllowed(true);
        fixture.steadyHealth();
    }

    // The rule the Requiem add-on will register, for the length of the test: this player holds Requiem.
    private static void holdsRequiem(KingCrimsonEpitaphFixture fixture, ServerPlayer holder) {
        EpitaphPredictions.registerExemption(KingCrimsonAddon.id("requiem_of_a_test"), player -> player == holder);
        fixture.base.onClose(() -> EpitaphPredictions.removeExemption(KingCrimsonAddon.id("requiem_of_a_test")));
    }

    private static int notices(ServerPlayer to, boolean on) {
        int count = 0;
        for (EpitaphNoticePayload notice : KingCrimsonEraseFixture.listener(to).payloads(EpitaphNoticePayload.class)) {
            count += notice.on() == on ? 1 : 0;
        }
        return count;
    }

    // The ids of the phantoms the frames this player was sent make on his client.
    private static Set<Integer> made(ServerPlayer to) {
        Set<Integer> ids = new HashSet<>();
        for (EpitaphPhantomFramePayload frame : KingCrimsonEraseFixture.listener(to).payloads(EpitaphPhantomFramePayload.class)) {
            for (byte[] bytes : frame.packets()) {
                for (Packet<?> packet : PacketBytes.decode(bytes, to.registryAccess())) {
                    if (packet instanceof ClientboundAddEntityPacket add) {
                        ids.add(add.getId());
                    }
                }
            }
        }
        return ids;
    }

    // F1
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_requiem_told")
    public static void requiemPlayerIsTold(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-told", 2, 20, 12);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        // Their packets leave by the own send of the server: what gets as far as a connection is what its player is told.
        ServerPlayer holder = fixture.witness("kc-ep-told-ger", 8.5D, 2.5D);
        ServerPlayer plain = fixture.witness("kc-ep-told-him", 8.5D, 8.5D);
        ServerPlayer third = fixture.witness("kc-ep-told-third", 2.5D, 8.5D);
        fixture.base.tickAsConnection(holder);
        fixture.base.tickAsConnection(plain);
        holdsRequiem(fixture, holder);
        EpitaphSession[] running = new EpitaphSession[1];
        Vec3[] stood = new Vec3[1];
        boolean[] phaseSeen = new boolean[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            stood[0] = holder.position();
            EpitaphSession session = EpitaphSessions.predict(user, List.of(holder, plain), holder);
            helper.assertTrue(session != null && session.mode() == EpitaphMode.MULTI_BOUND, "Setup: the press at two players began no"
                    + " prediction that binds from the press");
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(plain);
            fixture.rule(EpitaphPredictions.isPredictedPlayer(holder) && !EpitaphPredictions.isForcedWalking(holder)
                    && EpitaphPredictions.isForcedWalking(plain), "at the press the player a rule exempts "
                            + (EpitaphPredictions.isPredictedPlayer(holder) ? "counts" : "does not count") + " as predicted and is "
                            + (EpitaphPredictions.isForcedWalking(holder) ? "walked" : "not walked") + "; the other player is "
                            + (EpitaphPredictions.isForcedWalking(plain) ? "walked" : "not walked"));
            fixture.rule(notices(holder, true) == 1 && notices(plain, true) + notices(third, true) + notices(user, true) == 0,
                    "the notice was sent " + notices(holder, true) + " times to the exempt player, " + notices(plain, true)
                            + " times to the other player the prediction is aimed at, " + notices(third, true) + " to a third and "
                            + notices(user, true) + " to the one who foresees");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!phaseSeen[0] && !session.clock().inPhase() && !session.ended()) {
                phaseSeen[0] = true;
                fixture.rule(!EpitaphPredictions.isPredictedPlayer(holder) && notices(holder, false) == 1, "the phantoms have been"
                        + " watched, and the exempt player " + (EpitaphPredictions.isPredictedPlayer(holder) ? "still counts" : "no"
                                + " longer counts") + " as predicted and was told " + notices(holder, false) + " times that it is over");
            }
            if (!session.ended()) {
                return;
            }
            running[0] = null;
            Set<Integer> his = made(holder);
            Set<Integer> all = made(user);
            helper.assertTrue(phaseSeen[0] && all.contains(holder.getId()) && all.contains(plain.getId()), "Setup: the end of the phantom"
                    + " phase was never seen, or the frames of the one who foresees make the phantoms " + all + ", not those of both players");
            fixture.rule(his.equals(Set.of(holder.getId())), "the frames the exempt player was sent make the phantoms " + his
                    + " on his client; his own has the number " + holder.getId());
            int others = 0;
            for (ServerPlayer other : List.of(plain, third)) {
                others += KingCrimsonEraseFixture.listener(other).payloads(EpitaphSessionPayload.class).size()
                        + KingCrimsonEraseFixture.listener(other).payloads(EpitaphPhantomFramePayload.class).size()
                        + KingCrimsonEraseFixture.listener(other).payloads(EpitaphNoticePayload.class).size();
            }
            fixture.rule(others == 0, "the other player the prediction was aimed at and a third were sent " + others + " words, frames"
                    + " or notices of it");
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && holder.position().distanceTo(stood[0]) < 1.0E-9D, "the prediction"
                    + " ended as " + session.endedAs() + ", and the exempt player was moved " + holder.position().distanceTo(stood[0])
                    + " blocks");
            fixture.finish("A player who holds Requiem is told, and nobody else");
        }));
    }

    private static int removals(ServerPlayer to) {
        int count = 0;
        for (EpitaphPhantomFramePayload frame : KingCrimsonEraseFixture.listener(to).payloads(EpitaphPhantomFramePayload.class)) {
            count += frame.tick() == EpitaphPhantomFramePayload.NOW ? 1 : 0;
        }
        return count;
    }

    // F2
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_requiem_break")
    public static void requiemBreakLeavesAHole(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-break", 4, 16, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        // Straight ahead of the user: under his crosshair.
        ServerPlayer holder = fixture.witness("kc-ep-break-ger", 7.5D, 2.5D);
        fixture.base.tickAsConnection(holder);
        holdsRequiem(fixture, holder);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] landed = new int[1];
        float[] health = new float[1];
        Vec3[] stood = new Vec3[1];
        boolean[] broken = new boolean[1];
        fixture.listen(LivingIncomingDamageEvent.class, event -> {
            // A hit of the record, as it is carried out, on him or by him.
            if (EpitaphSandbox.replaying() && (event.getEntity() == holder || event.getSource().getEntity() == holder)) {
                landed[0]++;
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            health[0] = holder.getHealth();
            stood[0] = holder.position();
            helper.assertTrue(fixture.base.press(user, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false).isActionSuccess,
                    "Setup: the press of Epitaph was refused");
            EpitaphSession session = EpitaphSessions.of(user);
            helper.assertTrue(session != null && session.exempts(holder), "Setup: no prediction began, or the player under the"
                    + " crosshair is not exempt");
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            ActorKey his = ActorKey.of(holder);
            ActorKey own = ActorKey.of(user);
            long fought = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream()
                    .filter(hit -> hit.victim().equals(own) && session.record().ownerOf(hit).equals(his)).count();
            fixture.rule(fought > 0, "the phantom of the exempt player never struck that of the one who foresees: it has no mind, and"
                    + " is no guess of what he would do");
            fixture.rule(EpitaphPredictions.isPredictedPlayer(holder) && !EpitaphPredictions.isForcedWalking(holder)
                    && EpitaphPredictions.isForcedWalking(user), "at the press the exempt player "
                            + (EpitaphPredictions.isPredictedPlayer(holder) ? "counts" : "does not count") + " as predicted and is "
                            + (EpitaphPredictions.isForcedWalking(holder) ? "walked" : "not walked") + "; the one who foresees is "
                            + (EpitaphPredictions.isForcedWalking(user) ? "walked" : "not walked"));
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!broken[0] && session.passed() >= 2 && !session.ended()) {
                broken[0] = true;
                helper.assertTrue(EpitaphPredictions.isPredictedPlayer(holder), "Setup: at tick " + session.passed() + " the exempt"
                        + " player is no longer told");
                float stamina = power.getStamina();
                int wait = power.getAbilityCooldown(EPITAPH);
                int before = removals(user);
                // The call the Return to Zero of Requiem makes when he presses it while he is predicted.
                boolean first = EpitaphPredictions.breakPrediction(holder, KingCrimsonAddon.id("return_to_zero_of_a_test"));
                boolean again = EpitaphPredictions.breakPrediction(holder, KingCrimsonAddon.id("return_to_zero_of_a_test"));
                boolean ofUser = EpitaphPredictions.breakPrediction(user, KingCrimsonAddon.id("return_to_zero_of_a_test"));
                fixture.rule(first && !again && !ofUser, "the break of the exempt player answered " + first + ", a second one " + again
                        + ", and one by the one who foresees " + ofUser);
                fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user), "the break ended the prediction of the other ("
                        + session.endedAs() + ") or let its user go");
                fixture.rule(wait > 0 && power.getStamina() == stamina && power.getAbilityCooldown(EPITAPH) == wait, "the break gave"
                        + " something back to the one who foresees: his stamina went from " + stamina + " to " + power.getStamina()
                        + " and his wait from " + wait + " to " + power.getAbilityCooldown(EPITAPH));
                fixture.rule(!EpitaphPredictions.isPredictedPlayer(holder) && notices(holder, false) == 1 && removals(user) > before,
                        "after the break the player " + (EpitaphPredictions.isPredictedPlayer(holder) ? "still counts" : "no longer"
                                + " counts") + " as predicted, was told " + notices(holder, false) + " times that it is over, and the"
                                + " one who foresees was sent " + (removals(user) - before) + " removals of phantoms");
            }
            if (!session.ended()) {
                return;
            }
            running[0] = null;
            helper.assertTrue(broken[0], "Setup: the prediction ended before it was broken");
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && landed[0] == 0 && holder.getHealth() == health[0]
                    && holder.position().distanceTo(stood[0]) < 1.0E-9D, "the prediction ended as " + session.endedAs() + "; "
                            + landed[0] + " hits of the record by the exempt player or on him were carried out, he has "
                            + holder.getHealth() + " of " + health[0] + " health and was moved "
                            + holder.position().distanceTo(stood[0]) + " blocks");
            fixture.rule(session.record().skipped() > 0, "no op of the record was left out");
            fixture.finish("The break of a player who holds Requiem leaves a hole, and the prediction goes on");
        }));
    }
}
