package rotp.addons.kingcrimson.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphMode;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomActions;
import rotp.addons.kingcrimson.network.EpitaphForcedWalkPayload;
import rotp.addons.kingcrimson.network.EpitaphPhantomFramePayload;
import rotp.addons.kingcrimson.network.EpitaphSessionPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseShiftService;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

/**
 * Epitaph aimed at a player, with the pull-back switched off (EPITAPH-ARCH 3.4, 3.5, 5.1 and the rulings after stage D): he
 * is walked from the press as the one who foresees is, his phantom fights back with the same mind, his Stand goes with
 * him, and he is told nothing of it but that his body walks.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphEnemyGameTests {
    private static final int PRESS_AT = 5;
    private static final short EPITAPH_KEY = 31;
    private static final short STAND_KEY = 33;
    private static final short SECOND_KEY = 34;
    private static final short STEP_KEY = 35;
    private static final String EPITAPH = "kingcrimson_epitaph";
    private static final String BARRAGE = "barrage";
    private static final int HOLD_WAIT = 150;

    private KingCrimsonEpitaphEnemyGameTests() {}

    // The passive erase would answer a foreseen hit, and a walked player heals at a pace of his own: neither is the matter here.
    private static void quiet(KingCrimsonEpitaphFixture fixture) {
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        fixture.steadyHealth();
    }

    // A second player, ticked as a connection ticks one: he stays where the server last put him.
    private static ServerPlayer enemy(KingCrimsonEpitaphFixture fixture, String name, double x, double z, boolean wired) {
        ServerPlayer enemy = wired ? fixture.witness(name, x, z) : fixture.other(name, x, z);
        fixture.base.tickAsConnection(enemy);
        return enemy;
    }

    // Players hurt players in this test, whatever the server of the tests is set to.
    private static void playersFight(KingCrimsonEpitaphFixture fixture) {
        MinecraftServer server = fixture.level.getServer();
        boolean allowed = server.isPvpAllowed();
        fixture.base.onClose(() -> server.setPvpAllowed(allowed));
        server.setPvpAllowed(true);
    }

    // A player is given The World and has it out.
    private static StandEntity theWorld(KingCrimsonEpitaphFixture fixture, LivingEntity bearer) {
        StandPower power = PowerClass.STAND.attachGet(bearer);
        StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
        fixture.helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                == StandPowerTransitions.Status.APPLIED, "Setup: The World could not be given to a player");
        power.skipProgression();
        fixture.helper.assertTrue(type.summon(bearer, power) && power.getSummonedStandEntity() != null,
                "Setup: the player could not summon his Stand");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        power.setStamina(power.getMaxStamina());
        return fixture.base.track(stand);
    }

    private static String abilityOf(EntityActionInstance action) {
        return action == null ? "none" : action.ability instanceof Ability ability ? ability.name() : String.valueOf(action.ability);
    }

    private static List<RecordedOp.Damage> blows(EpitaphSession session, ServerPlayer striker, ServerPlayer struck) {
        ActorKey by = ActorKey.of(striker);
        ActorKey on = ActorKey.of(struck);
        return KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream()
                .filter(hit -> hit.victim().equals(on) && by.equals(ActorKey.of(hit.source().getEntity()))).toList();
    }

    // What the action of a slot went through in the room: each phase with the tick it began at.
    private static String phasesOf(EntityTrack track) {
        if (track == null) {
            return "no track";
        }
        StringBuilder told = new StringBuilder();
        ActionPhase last = null;
        for (int tick = 0; tick <= track.lastTick(); tick++) {
            EntityTrack.ActionMark mark = track.mark(tick, LivingComponentAction.MAIN_SLOT);
            ActionPhase phase = mark != null ? mark.phase() : null;
            if (phase != last) {
                told.append(told.length() > 0 ? ", " : "").append(phase == null ? "nothing" : phase.toString()).append(" from ").append(tick);
                last = phase;
            }
        }
        return told.length() > 0 ? told.toString() : "no action";
    }

    // How often this player was told that the mark of Epitaph on that one was set or taken away.
    private static int faceTold(ServerPlayer to, ServerPlayer about) {
        int told = 0;
        for (SyncAttachmentsPayload sync : KingCrimsonEraseFixture.listener(to).payloads(SyncAttachmentsPayload.class)) {
            if (sync.target() instanceof SyncAttachmentsPayload.EntityTarget entity && entity.entity() == about.getId()
                    && sync.types().contains(KingCrimsonAddon.EPITAPH_FORESEEING.get())) {
                told++;
            }
        }
        return told;
    }

    // E12
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_walked")
    public static void enemyIsWalkedAndThenFree(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-foe", 2, 20, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = enemy(fixture, "kc-ep-foe-him", 10.5D, 2.5D, false);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] checked = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            helper.assertTrue(session.mode() == EpitaphMode.MULTI_BOUND && session.record().track(enemy.getId()) != null,
                    "Setup: with two players online the prediction is " + session.mode() + ", or the player under the crosshair has no track");
            fixture.rule(EpitaphPredictions.isForcedWalking(enemy) && EpitaphPredictions.isPredictedPlayer(enemy)
                    && !EpitaphPredictions.isPredictedPlayer(user), "from the press the player under the crosshair is "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "not walked") + " and "
                            + (EpitaphPredictions.isPredictedPlayer(enemy) ? "predicted" : "not predicted")
                            + "; the one who foresees counts as predicted by another: " + EpitaphPredictions.isPredictedPlayer(user));
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            EntityTrack track = session.record().track(enemy.getId());
            if (!session.ended()) {
                double tick = session.clock().body();
                double off = enemy.position().distanceTo(KingCrimsonEpitaphFixture.on(track, tick));
                fixture.rule(off < 1.0E-6D, "at tick " + tick + " of his track the player under the crosshair stood " + off
                        + " blocks from where his phantom stood then");
                fixture.rule(EpitaphPredictions.isPredictedPlayer(enemy), "at tick " + tick + " of his track he no longer counts as predicted");
                if (++checked[0] == 8) {
                    // What his client sends when he pushes a key: a step to the side.
                    Vec3 before = enemy.position();
                    enemy.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(before.x, before.y, before.z + 0.4D, true));
                    double moved = enemy.position().distanceTo(before);
                    fixture.rule(moved < 1.0E-9D, "a move sent by the client of the walked player moved him " + moved + " blocks");
                }
                return;
            }
            running[0] = null;
            Vec3 start = track.frame(0).position();
            Vec3 end = track.frame(track.lastTick()).position();
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && start.distanceTo(end) > 2.0D, "Setup: the prediction ended as "
                    + session.endedAs() + ", and the phantom of the player, with that of the one who foresees to fight, went from "
                    + KingCrimsonEpitaphFixture.at(start) + " to " + KingCrimsonEpitaphFixture.at(end));
            double off = enemy.position().distanceTo(end);
            boolean walked = EpitaphPredictions.isForcedWalking(enemy);
            boolean predicted = EpitaphPredictions.isPredictedPlayer(enemy);
            fixture.rule(checked[0] >= session.clock().foresight() - 2 && !walked && !predicted && off < 1.0E-6D, "walked for " + checked[0]
                    + " server ticks of a foresight of " + session.clock().foresight() + ", the player " + (walked ? "is still walked" : "is let go")
                    + (predicted ? ", still counts as predicted" : "") + " and stands " + off + " blocks from where his phantom ended");
            List<EpitaphForcedWalkPayload> told = KingCrimsonEraseFixture.listener(enemy).payloads(EpitaphForcedWalkPayload.class);
            long begun = told.stream().filter(walk -> walk.kind() == EpitaphForcedWalkPayload.Kind.BEGIN).count();
            long ends = told.stream().filter(walk -> walk.kind() == EpitaphForcedWalkPayload.Kind.RELEASE).count();
            fixture.rule(begun == 1L && ends == 1L, "his client was told of the beginning of his walk " + begun + " times and of its end "
                    + ends + " times");
            // His own again: the same move is taken now.
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            Vec3 before = enemy.position();
            enemy.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(before.x, before.y, before.z + 0.4D, true));
            double moved = enemy.position().distanceTo(before);
            fixture.rule(moved > 0.3D, "let go, a move sent by his client moved the player only " + moved + " blocks");
            fixture.finish("A player under the crosshair is walked along his track, and then free");
        }));
    }

    // E11
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_no_sign")
    public static void enemyGetsNoSign(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-sign", 2, 20, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        // His packets leave by the own send of the server: what gets as far as his connection is what he is told.
        ServerPlayer enemy = enemy(fixture, "kc-ep-sign-him", 8.5D, 2.5D, true);
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEpitaphFixture.tracks(enemy, user), "Setup: the player under the crosshair does not track the"
                    + " one who foresees");
            EpitaphSession session = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            helper.assertTrue(session.aimsAt(enemy) && EpitaphPredictions.isForcedWalking(enemy), "Setup: the player under the"
                    + " crosshair is not foreseen, or not walked");
            // What the one who foresees is sent: the test would see the same on the connection of the other.
            helper.assertTrue(!KingCrimsonEraseFixture.listener(user).payloads(EpitaphSessionPayload.class).isEmpty()
                    && !KingCrimsonEraseFixture.listener(user).payloads(EpitaphPhantomFramePayload.class).isEmpty(),
                    "Setup: the one who foresees was sent no word of his prediction, or no frame of its phantoms");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!session.ended()) {
                // T6: he is a predicted player from the press to his release.
                fixture.rule(EpitaphPredictions.isPredictedPlayer(enemy), "at tick " + session.passed() + " of the prediction the player it"
                        + " is aimed at does not count as predicted");
                return;
            }
            running[0] = null;
            int words = KingCrimsonEraseFixture.listener(enemy).payloads(EpitaphSessionPayload.class).size();
            int frames = KingCrimsonEraseFixture.listener(enemy).payloads(EpitaphPhantomFramePayload.class).size();
            int marks = faceTold(enemy, user);
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE, "Setup: the prediction ended as " + session.endedAs());
            fixture.rule(words == 0 && frames == 0 && marks == 0, "over the whole prediction the player it was aimed at was sent " + words
                    + " words of it, " + frames + " frames of its phantoms, and " + marks + " times the mark of the one who foresees");
            fixture.rule(!EpitaphPredictions.isPredictedPlayer(enemy), "let go at the end of his track, he still counts as predicted");
            fixture.finish("A player a prediction is aimed at gets no sign of it");
        }));
    }

    // 3.1: the phantom of a player the prediction is aimed at has the same mind, turned against the one who foresees
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_fights")
    public static void aPlayerUnderTheCrosshairFightsBack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-duel", 3, 16, 8);
        fixture.budget(0);
        quiet(fixture);
        playersFight(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = enemy(fixture, "kc-ep-duel-him", 6.5D, 2.5D, false);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] at = new int[2];
        float[] health = new float[2];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            session.foreseeAll();
            List<RecordedOp.Damage> onUser = blows(session, enemy, user);
            List<RecordedOp.Damage> onEnemy = blows(session, user, enemy);
            helper.assertTrue(!onUser.isEmpty(), "The phantom of the player under the crosshair never struck the phantom of the one who"
                    + " foresees: it has no mind turned against him");
            helper.assertTrue(!onEnemy.isEmpty(), "The phantom of the one who foresees never struck the phantom of the player he aimed at");
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, onUser.get(0));
            at[1] = KingCrimsonEpitaphFixture.tickOf(session, onEnemy.get(0));
            health[0] = user.getHealth();
            health[1] = enemy.getHealth();
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < Math.max(at[0], at[1]) && !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(user.getHealth() < health[0] && user.getLastHurtByMob() == enemy, "past tick " + at[0] + ", where the phantom of the"
                    + " player struck that of the one who foresees, the one who foresees has " + user.getHealth() + " of " + health[0]
                    + " health and was last hurt by " + user.getLastHurtByMob());
            fixture.rule(enemy.getHealth() < health[1] && enemy.getLastHurtByMob() == user, "past tick " + at[1] + ", where the phantom of"
                    + " the one who foresees struck that of the player, the player has " + enemy.getHealth() + " of " + health[1]
                    + " health and was last hurt by " + enemy.getLastHurtByMob());
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A player under the crosshair fights back, in the room and then for real");
        }));
    }

    // Ruling R12, points 3 and 4: the Stand of a walked player is taken over like any other, and the hold is that of its copy
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_stand")
    public static void theStandOfAWalkedPlayerGoesOnWithWhatItDid(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-foe-stand", 4, 20, 8);
        fixture.phantomsUse(BARRAGE);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        // Far from the one who foresees: his Stand strikes at the air, before the press and after it.
        ServerPlayer enemy = enemy(fixture, "kc-ep-foe-stand-him", 12.5D, 2.5D, false);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        EntityActionInstance[] own = new EntityActionInstance[1];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> {
            out[0] = theWorld(fixture, enemy);
            BufferingState pressed = fixture.base.press(enemy, BARRAGE, STAND_KEY, InputMethod.HOLD, false);
            helper.assertTrue(pressed.isActionSuccess, "Setup: the player could not press the barrage of his Stand");
        }));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EntityActionInstance action = LivingComponentAction.getComponent(out[0]).getAction();
            var keys = enemy.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys;
            helper.assertTrue(action != null && !action.isOver() && BARRAGE.equals(abilityOf(action))
                    && keys.values().stream().anyMatch(entry -> entry.action == action), "Setup: three ticks after his key went down the"
                            + " Stand of the player performs " + abilityOf(action) + ", or no key of his holds it");
            own[0] = action;
            ActionPhase phase = action.getPhase();
            EpitaphSession session = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            EntityActionInstance taken = session.room().phantom(out[0].getId()) instanceof LivingEntity copy
                    ? LivingComponentAction.getCurEntityAction(copy) : null;
            fixture.rule(session.binder().of(out[0]) != null, "the Stand of the walked player is on no track: it is not held to the record"
                    + " with him");
            fixture.rule(taken != null && taken != action && !taken.isOver() && BARRAGE.equals(abilityOf(taken)) && taken.getPhase() == phase,
                    "at the press the Stand of the player was at " + phase + " of its barrage, and its copy in the room performs "
                            + abilityOf(taken) + (taken == null ? "" : " at " + taken.getPhase()));
            fixture.rule(keys.values().stream().noneMatch(entry -> entry.action == action), "a key of the walked player still holds the"
                    + " barrage that the copy of his Stand took over: the hold is that of the copy");
            // 3.4: his keys do nothing while he is walked.
            boolean went = fixture.base.press(enemy, "punch", SECOND_KEY, InputMethod.CLICK, false).isActionSuccess;
            fixture.rule(!went && LivingComponentAction.getComponent(out[0]).getAction() == action, "the Stand of the walked player began a"
                    + " punch at his key");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < 3 && !session.ended()) {
                return;
            }
            running[0] = null;
            int passed = session.passed();
            helper.assertTrue(!session.ended(), "Setup: the prediction ended as " + session.endedAs() + " at tick " + passed);
            EntityTrack track = session.record().track(out[0].getId());
            EntityTrack.ActionMark mark = track != null ? track.mark(passed, LivingComponentAction.MAIN_SLOT) : null;
            EntityActionInstance now = LivingComponentAction.getComponent(out[0]).getAction();
            fixture.rule(now == own[0] && mark != null && now.getPhase() == mark.phase() && (int) now.getPhaseTick() == mark.phaseTick(),
                    "past tick " + passed + " the record has the Stand of the walked player at " + (mark == null ? "nothing" : mark.phase()
                            + " " + mark.phaseTick()) + ", and the real Stand performs " + abilityOf(now) + (now == null ? "" : " at "
                                    + now.getPhase() + " " + now.getPhaseTick()) + (now == own[0] ? "" : ", not the action it was in the middle of"));
            if (track != null) {
                // The hold is that of the mind of his phantom from the press: let go as it lets go a barrage of its own.
                session.foreseeAll();
                int held = 1;
                while (held <= track.lastTick() && track.mark(held, LivingComponentAction.MAIN_SLOT) != null
                        && track.mark(held, LivingComponentAction.MAIN_SLOT).phase() == ActionPhase.PERFORM) {
                    held++;
                }
                fixture.rule(held >= 18 && held <= 26, "the copy of the Stand of the walked player went on with the barrage it took over"
                        + " for " + held + " ticks from the press: the mind of his phantom lets go a barrage it holds after 20");
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The Stand of a walked player goes on with what it did, held to the record");
        }));
    }

    // Ruling R12, points 4 and 6: a user who is not walked ends nothing at the press, and his Stand is taken over by no copy
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_enemy_keeps")
    public static void theStandOfAUserWhoKeepsControlGoesOnAndIsNotTakenOver(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-keeps", 4, 16, 8);
        // The room waits: what the copy of his Stand took over is looked at before the room has lived a tick.
        fixture.budget(0);
        fixture.userKeepsControl();
        fixture.phantomsUseStands();
        ServerPlayer user = fixture.user;
        fixture.hunter(EntityType.ZOMBIE, 10.5D, 2.5D);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            fixture.base.press(user, "heavy_punch", KingCrimsonEraseFixture.OTHER_KEY, InputMethod.CLICK, false);
            EntityActionInstance blow = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(blow != null && !blow.isOver(), "Setup: the Stand began no heavy punch");
            BufferingState pressed = fixture.base.press(user, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false);
            EpitaphSession session = EpitaphSessions.of(user);
            helper.assertTrue(pressed.isActionSuccess && session != null && session.userVoid(), "Setup: the press began no prediction, or"
                    + " the user who keeps control is walked");
            EntityActionInstance now = LivingComponentAction.getCurEntityAction(stand);
            fixture.rule(now == blow && !now.isOver(), "the press ended the blow of the Stand of a user who keeps control: nothing of his"
                    + " is walked, and nothing of his ends");
            LivingEntity copy = session.room().phantom(stand.getId()) instanceof LivingEntity living ? living : null;
            helper.assertTrue(copy != null, "Setup: the Stand of the user has no copy in the room");
            EntityActionInstance taken = LivingComponentAction.getCurEntityAction(copy);
            fixture.rule(taken == null, "the copy of the Stand of a user who keeps control took over its " + abilityOf(taken)
                    + ": what is void does nothing in the room");
            fixture.rule(session.binder().of(stand) == null, "the Stand of a user who keeps control is on a track");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The Stand of a user who keeps control goes on with its blow, and no copy takes it over");
        }));
    }

    // 5.1 step 3: what a press costs by what is under the crosshair
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_enemy_cost")
    public static void aPlayerUnderTheCrosshairCostsMore(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-dear", 4, 16, 8);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        // Straight ahead of the user: under his crosshair.
        ServerPlayer enemy = enemy(fixture, "kc-ep-dear-him", 7.5D, 2.5D, false);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            float dear = KingCrimsonConfig.epitaphPlayerStamina();
            float plain = KingCrimsonConfig.epitaphStamina();
            int wait = KingCrimsonConfig.epitaphPlayerCooldownTicks();
            helper.assertTrue(dear > plain + 50.0F && power.getMaxStamina() > dear && wait > KingCrimsonConfig.epitaphCooldownTicks() + 50,
                    "Setup: a press at a player costs " + dear + " and one at a creature " + plain + ", of " + power.getMaxStamina());
            // A bar that pays for a press at a creature and not for this one.
            float had = plain + 20.0F;
            power.setStamina(had);
            // Ruling 25: what the key shows on the HUD is what a press would charge now.
            Ability key = power.getAbility(EPITAPH);
            float shown = key.getStaminaCost(power);
            boolean lit = key.checkConditions(power).isPositive();
            fixture.rule(Math.abs(shown - dear) < 0.01F && !lit, "with a player under the crosshair and " + had + " stamina the key"
                    + " shows a cost of " + shown + " and is " + (lit ? "lit" : "greyed out") + ": a press there costs " + dear);
            float pitch = user.getXRot();
            user.setXRot(-90.0F);
            shown = key.getStaminaCost(power);
            lit = key.checkConditions(power).isPositive();
            fixture.rule(Math.abs(shown - plain) < 0.01F && lit, "looking up at nothing with " + had + " stamina the key shows a cost of "
                    + shown + " and is " + (lit ? "lit" : "greyed out") + ": a press there costs " + plain);
            user.setXRot(pitch);
            boolean went = fixture.base.press(user, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false).isActionSuccess;
            EpitaphSession begun = EpitaphSessions.of(user);
            fixture.rule(!went && begun == null && power.getStamina() == had && power.getAbilityCooldown(EPITAPH) == 0, "with " + had
                    + " stamina and a player under the crosshair the press " + (went ? "went through" : "was refused") + ", a prediction "
                    + (begun != null ? "began" : "did not begin") + ", and the user has " + power.getStamina() + " stamina and waits "
                    + power.getAbilityCooldown(EPITAPH) + " ticks");
            if (begun != null) {
                begun.end(EpitaphEnd.CANCELLED);
            }
            power.restoreAbilityCooldown(EPITAPH, 0);
            power.setStamina(power.getMaxStamina());
            float before = power.getStamina();
            went = fixture.base.press(user, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false).isActionSuccess;
            EpitaphSession session = EpitaphSessions.of(user);
            helper.assertTrue(went && session != null && session.aimsAt(enemy), "Setup: with a full bar the press began no prediction of the"
                    + " player under the crosshair");
            float took = before - power.getStamina();
            int waits = power.getAbilityCooldown(EPITAPH);
            fixture.rule(Math.abs(took - dear) < 0.01F, "a press with a player under the crosshair took " + took + " stamina, not " + dear
                    + " as it is set");
            fixture.rule(waits > wait - 3 && waits <= wait, "after a press with a player under the crosshair the user waits " + waits
                    + " ticks for the next, not " + wait + " as it is set");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A press of Epitaph at a player costs what is set for it");
        }));
    }

    // Ruling R10, point 2: a wait that rises later than its press, as that of a hold does when it is let go
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_enemy_waits")
    public static void whatTheHoldOfHisPhantomSetAWalkedPlayerWaitsFor(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-late", 4, 16, 8);
        fixture.phantomsUse(BARRAGE);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        // No blow of the Stand of the other hurts him: the hold is the matter here, not what it strikes.
        user.setInvulnerable(true);
        ServerPlayer enemy = enemy(fixture, "kc-ep-late-him", 5.5D, 2.5D, false);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        RecordedOp.Cooldown[] set = new RecordedOp.Cooldown[1];
        int[] at = {-1};
        boolean[] seen = new boolean[1];
        List<List<PhantomActions.Wait>> ends = new java.util.ArrayList<>();
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> out[0] = theWorld(fixture, enemy)));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(StandPower.get(enemy).getAbilityCooldown(BARRAGE) == 0, "Setup: the player waits for his barrage before the press");
            EpitaphSession session = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            ActorKey him = ActorKey.of(enemy);
            ActorKey stand = ActorKey.of(out[0]);
            // The barrage of The World has no wait of its own, and the abilities of a power are its own objects: that of the
            // phantom is given one before the room has lived a tick. The core sets it when the hold ends; the body has none.
            Ability barrage = StandPower.get((LivingEntity) session.room().info(him).entity).getAbility(BARRAGE);
            helper.assertTrue(barrage != null && barrage != StandPower.get(enemy).getAbility(BARRAGE) && session.room().tick() == 0,
                    "Setup: the phantom of the player has no barrage of its own, or the room has lived " + session.room().tick() + " ticks");
            barrage.cooldown(HOLD_WAIT, 0);
            session.foreseeAll();
            // The barrage of his Stand in the room: when it began, and when the wait for the next one rose.
            int began = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class).stream()
                    .filter(op -> op.actor().equals(stand) && op.state().hasAction())
                    .mapToInt(op -> KingCrimsonEpitaphFixture.tickOf(session, op)).findFirst().orElse(-1);
            List<RecordedOp.Cooldown> waits = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Cooldown.class);
            set[0] = waits.stream().filter(op -> op.actor().equals(him) && op.ability().equals(BARRAGE)).findFirst().orElse(null);
            helper.assertTrue(began > 0 && set[0] != null && set[0].ticks() > 20, "The Stand of the phantom of the player began a barrage"
                    + " at tick " + began + " of the room (" + phasesOf(session.record().track(out[0].getId())) + "), the power of his"
                    + " phantom ended waiting for " + PhantomActions.cooldowns((LivingEntity) session.room().info(him).entity)
                    + ", and the record has these waits: " + waits);
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, set[0]);
            fixture.rule(at[0] > began, "the wait for the barrage is an op of tick " + at[0] + ", and the barrage began at tick " + began
                    + ": the core sets it when the hold ends");
            ends.add(PhantomActions.cooldowns((LivingEntity) session.room().info(him).entity));
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            int waits = StandPower.get(enemy).getAbilityCooldown(BARRAGE);
            if (!session.ended()) {
                int passed = session.passed();
                if (passed < at[0]) {
                    fixture.rule(waits == 0, "at tick " + passed + " the walked player waits " + waits + " ticks for a barrage whose wait"
                            + " rose in the room only at tick " + at[0]);
                } else if (passed == at[0] && !seen[0]) {
                    seen[0] = true;
                    fixture.rule(waits > set[0].ticks() - 3 && waits <= set[0].ticks(), "past tick " + at[0] + ", where the end of the hold of"
                            + " his phantom set a wait of " + set[0].ticks() + " ticks, the walked player waits " + waits + " ticks for his barrage");
                }
                return;
            }
            running[0] = null;
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && seen[0], "Setup: the prediction ended as " + session.endedAs()
                    + (seen[0] ? "" : " before tick " + at[0] + " was looked at"));
            // At the end of his walk he waits for each ability what the power of his phantom ended waiting.
            List<PhantomActions.Wait> has = PhantomActions.cooldowns(enemy);
            fixture.rule(!ends.get(0).isEmpty(), "Setup: the power of the phantom of the player ended waiting for nothing");
            for (PhantomActions.Wait wait : ends.get(0)) {
                int own = has.stream().filter(mine -> mine.ability().equals(wait.ability())).mapToInt(PhantomActions.Wait::ticks).findFirst()
                        .orElse(0);
                fixture.rule(Math.abs(own - wait.ticks()) <= 3, "at the end of his walk the player waits " + own + " ticks for his "
                        + wait.ability() + ", and the power of his phantom ended waiting " + wait.ticks());
            }
            fixture.finish("What the hold of his phantom set, a walked player waits for");
        }));
    }

    // 3.6: a walked player who logs out or dies goes free where he is, and the prediction goes on without him
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_gone")
    public static void aWalkedPlayerWhoIsGoneGoesFree(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-gone", 3, 20, 12);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer leaver = enemy(fixture, "kc-ep-gone-out", 9.5D, 2.5D, false);
        ServerPlayer dier = enemy(fixture, "kc-ep-gone-dead", 9.5D, 9.5D, false);
        EpitaphSession[] running = new EpitaphSession[1];
        boolean[] looked = new boolean[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.predict(leaver, dier);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(leaver);
            KingCrimsonEpitaphFixture.acceptTeleport(dier);
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(!session.ended() && EpitaphPredictions.isForcedWalking(leaver) && EpitaphPredictions.isForcedWalking(dier)
                    && EpitaphPredictions.isForcedWalking(user), "Setup: eight ticks after the press the three players are not all walked");
            Vec3 stood = leaver.position();
            EpitaphSessions.onLogout(new PlayerEvent.PlayerLoggedOutEvent(leaver));
            fixture.rule(!EpitaphPredictions.isForcedWalking(leaver) && !EpitaphPredictions.isPredictedPlayer(leaver)
                    && leaver.position().distanceTo(stood) < 1.0E-9D, "logged out in the middle of his walk, the player is "
                            + (EpitaphPredictions.isForcedWalking(leaver) ? "still walked" : "let go") + ", "
                            + (EpitaphPredictions.isPredictedPlayer(leaver) ? "still" : "no longer") + " predicted, and was moved "
                            + leaver.position().distanceTo(stood) + " blocks");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(dier) && EpitaphPredictions.isForcedWalking(user),
                    "the logout of one walked player ended the prediction (" + session.endedAs() + ") or let the others go");
            // What is saved of him is a player like any other.
            var kept = leaver.saveWithoutId(new CompoundTag()).getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).getAllKeys();
            fixture.rule(kept.stream().noneMatch(key -> key.contains("epitaph")), "what is saved of him holds something of Epitaph: " + kept);
            // A death of his own, not a foreseen one.
            dier.hurt(dier.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(dier.isDeadOrDying(), "Setup: the second player did not die");
            fixture.rule(!EpitaphPredictions.isForcedWalking(dier) && !EpitaphPredictions.isPredictedPlayer(dier), "dead in the middle of"
                    + " his walk, the player is " + (EpitaphPredictions.isForcedWalking(dier) ? "still walked" : "let go") + " and "
                    + (EpitaphPredictions.isPredictedPlayer(dier) ? "still" : "no longer") + " predicted");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user), "the death of a walked player ended the prediction ("
                    + session.endedAs() + ") or let its user go");
            looked[0] = true;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || !looked[0] || !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && session.record().failed() == 0, "without the two players the"
                    + " prediction ended as " + session.endedAs() + " with " + session.record().failed() + " ops that failed");
            fixture.finish("A walked player who logs out or dies goes free, and the prediction goes on");
        }));
    }

    // Ruling 23, kept by the owner: the passive erase of a walked player who holds King Crimson voids his part
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_erase")
    public static void thePassiveEraseOfAWalkedPlayerVoidsHisPart(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-foe-kc", 4, 20, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = enemy(fixture, "kc-ep-foe-kc-him", 10.5D, 2.5D, false);
        fixture.base.kingCrimson(enemy, 4);
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            helper.assertTrue(EpitaphPredictions.isForcedWalking(enemy), "Setup: the player under the crosshair is not walked");
        }));
        helper.runAfterDelay(PRESS_AT + 8, () -> fixture.guarded(() -> {
            helper.assertTrue(!running[0].ended() && EpitaphPredictions.isForcedWalking(enemy) && !KingCrimsonEraseFixture.erasing(enemy),
                    "Setup: eight ticks after the press the player is not walked any more, or erases time");
            // As his passive would: an erase with no key.
            helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(enemy, KingCrimsonTemporalState.EraseKind.REFLEX),
                    "Setup: an erase of the walked player could not begin");
        }));
        helper.runAfterDelay(PRESS_AT + 10, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            fixture.rule(KingCrimsonEraseFixture.erasing(enemy) && !EpitaphPredictions.isForcedWalking(enemy)
                    && !EpitaphPredictions.isPredictedPlayer(enemy), "two ticks after an erase of his own began, the walked player is "
                            + (KingCrimsonEraseFixture.erasing(enemy) ? "erasing" : "not erasing") + ", "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "still walked" : "let go") + " and "
                            + (EpitaphPredictions.isPredictedPlayer(enemy) ? "still" : "no longer") + " predicted");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user) && !session.userVoid(), "the erase of the player it"
                    + " was aimed at ended the prediction (" + session.endedAs() + ") or voided the part of the one who foresees");
            KingCrimsonTemporalStateService.terminate(enemy);
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The passive erase of a walked player voids his part");
        }));
    }

    // The owner, 2026-11-11: the key of Time Erase works for a walked player who holds King Crimson, and frees him as it
    // frees the one who foresees; every other key of his stays refused
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_erase_key")
    public static void theKeyOfTimeEraseFreesAWalkedPlayerWhoHoldsKingCrimson(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-foe-key", 4, 20, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = enemy(fixture, "kc-ep-foe-key-him", 10.5D, 2.5D, false);
        StandPower his = fixture.base.kingCrimson(enemy, 4);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] held = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
            helper.assertTrue(EpitaphPredictions.isForcedWalking(enemy), "Setup: the player under the crosshair is not walked");
        }));
        helper.runAfterDelay(PRESS_AT + 4, () -> fixture.guarded(() -> {
            StandEntity stand = his.getSummonedStandEntity();
            boolean punched = fixture.base.press(enemy, "punch", SECOND_KEY, InputMethod.CLICK, false).isActionSuccess;
            boolean foresaw = fixture.base.press(enemy, EPITAPH, EPITAPH_KEY, InputMethod.CLICK, false).isActionSuccess;
            Vec3 stood = enemy.position();
            boolean stepped = fixture.base.press(enemy, "kingcrimson_timeerase_step", STEP_KEY, InputMethod.CLICK, false).isActionSuccess;
            fixture.rule(!punched && !foresaw && !stepped && EpitaphSessions.of(enemy) == null && EpitaphPredictions.isForcedWalking(enemy)
                    && enemy.position().distanceTo(stood) < 1.0E-9D, "of a walked player who holds King Crimson the key of the punch was "
                            + (punched ? "taken" : "refused") + ", the key of Epitaph " + (foresaw ? "taken" : "refused")
                            + " and the step through erased time " + (stepped ? "taken" : "refused") + "; he is "
                            + (EpitaphPredictions.isForcedWalking(enemy) ? "walked" : "let go"));
            boolean went = fixture.base.holdErase(enemy).isActionSuccess;
            EntityActionInstance winding = LivingComponentAction.getCurEntityAction(stand);
            fixture.rule(went && winding != null && !winding.isOver() && running[0].binder().of(stand) == null, "the key of Time Erase"
                    + " of a walked player the prediction is aimed at was " + (went ? "taken" : "refused") + "; his Stand performs "
                    + abilityOf(winding) + " and is " + (running[0].binder().of(stand) == null ? "his own again" : "still on its track"));
            held[0] = 1;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (held[0] == 0) {
                return;
            }
            if (held[0] > 0) {
                // The tick after his erase began is looked at: his part is void from the next tick of the prediction.
                if (KingCrimsonEraseFixture.erasing(enemy) || ++held[0] >= 120 || session.ended()) {
                    held[0] = -held[0];
                }
                return;
            }
            int waited = -held[0];
            held[0] = 0;
            boolean erasing = KingCrimsonEraseFixture.erasing(enemy);
            boolean walked = EpitaphPredictions.isForcedWalking(enemy);
            boolean predicted = EpitaphPredictions.isPredictedPlayer(enemy);
            fixture.rule(erasing && !walked && !predicted, "with his key of Time Erase held for " + waited + " ticks, the walked player"
                    + " is " + (erasing ? "erasing" : "not erasing") + ", " + (walked ? "still walked" : "let go") + " and "
                    + (predicted ? "still" : "no longer") + " predicted");
            fixture.rule(!session.ended() && EpitaphPredictions.isForcedWalking(user) && !session.userVoid(), "the erase of the player it"
                    + " was aimed at ended the prediction (" + session.endedAs() + ") or voided the part of the one who foresees");
            fixture.base.release(enemy, KingCrimsonEraseFixture.ERASE_KEY);
            KingCrimsonTemporalStateService.terminate(enemy);
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The key of Time Erase frees a walked player who holds King Crimson");
        }));
    }

    // Section 4, T6: a player a prediction holds is not shifted in the eyes of one who erases time, and not moved at its end
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_enemy_shift")
    public static void aPredictedPlayerIsNotShiftedByAnErase(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-shift", 4, 24, 8);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        ServerPlayer enemy = enemy(fixture, "kc-ep-shift-him", 14.5D, 2.5D, false);
        EpitaphSession[] running = new EpitaphSession[1];
        Vec3[] from = new Vec3[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.press(enemy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            KingCrimsonEpitaphFixture.acceptTeleport(enemy);
        }));
        helper.runAfterDelay(PRESS_AT + 3, () -> fixture.guarded(() -> {
            // The one who foresees erases time: his own part is void, and the other walks on.
            helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(user), "Setup: the erase of the one who foresees could not begin");
            from[0] = enemy.position();
        }));
        helper.runAfterDelay(PRESS_AT + 23, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(KingCrimsonEraseFixture.erasing(user) && !session.ended() && EpitaphPredictions.isForcedWalking(enemy),
                    "Setup: twenty ticks into the erase the one who foresees does not erase, or the other is not walked");
            double went = enemy.position().distanceTo(from[0]);
            helper.assertTrue(went > 1.5D, "Setup: in twenty ticks of the erase the walked player went only " + went + " blocks");
            double shifted = KingCrimsonTimeEraseShiftService.imageOf(user, enemy).distanceTo(enemy.position());
            fixture.rule(shifted < 1.0E-6D, "a walked player who went " + went + " blocks during the erase of the one who foresees is seen "
                    + shifted + " blocks from where he is");
            Vec3 before = enemy.position();
            KingCrimsonTemporalStateService.terminate(user);
            double moved = enemy.position().distanceTo(before);
            fixture.rule(moved < 1.0E-6D, "the end of the erase moved a walked player " + moved + " blocks");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A player a prediction holds is not shifted by an erase");
        }));
    }
}
