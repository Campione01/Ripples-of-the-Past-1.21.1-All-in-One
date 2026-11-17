package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
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
import rotp.addons.kingcrimson.epitaph.human.PhantomAbilityAllowList;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxDriver;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.gametest.GameTestChunkSending;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.s2c.TrBarrageHitSoundPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.ActionReplayState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * Epitaph, Stands and powers (EPITAPH-ARCH 2.4 and ruling R9). A phantom uses its Stand in the room, and presses there
 * only what the table of the module lets it press; the Stand of a creature is foreseen with it; what a Stand did in the
 * room its body is seen to do, at the tick it was foreseen, while every blow of it lands once, as an op of the record.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphStandGameTests {
    // The press comes before a creature that was just put there has had the time to reach the user for real.
    private static final int PRESS_AT = 5;
    /** What a phantom may press, written out here: the test does not read the table it is about. */
    private static final Set<String> ALLOWED = Set.of("punch", "barrage", "heavy_punch", "guard", "kingcrimson_chop",
            "kingcrimson_impale");
    private static final String TIME_ERASE = "kingcrimson_timeerase";
    private static final String CHOP = "kingcrimson_chop";

    private KingCrimsonEpitaphStandGameTests() {}

    // The passive erase would answer a foreseen hit, and a walked player heals at a pace of his own: neither is the matter here.
    private static void quiet(KingCrimsonEpitaphFixture fixture) {
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        fixture.steadyHealth();
    }

    // A zombie that stands and does nothing, in reach of the Stand of the user, with health to spare.
    private static Zombie dummy(KingCrimsonEpitaphFixture fixture) {
        Zombie dummy = fixture.sturdy(fixture.mob(EntityType.ZOMBIE, 4.5D, 2.5D));
        dummy.setNoAi(true);
        return dummy;
    }

    private static boolean isOf(DamageSource source, ActorKey stand) {
        return ActorKey.of(source.getDirectEntity()).equals(stand) || ActorKey.of(source.getEntity()).equals(stand);
    }

    private static List<RecordedOp.Damage> blowsOf(EpitaphSession session, ActorKey stand, Entity struck) {
        ActorKey victim = ActorKey.of(struck);
        return KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream()
                .filter(hit -> hit.victim().equals(victim) && isOf(hit.source(), stand)).toList();
    }

    // The ability of an action as the record keeps it.
    private static String abilityOf(ServerLevel level, ActionReplayState state) {
        if (!state.hasAction()) {
            return "none";
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(state.action()));
        try {
            return abilityOf(EntityActionInstance.decode(level, buffer));
        }
        finally {
            buffer.release();
        }
    }

    private static String abilityOf(EntityActionInstance action) {
        return action == null ? "none" : action.ability instanceof Ability ability ? ability.name() : String.valueOf(action.ability);
    }

    // D1
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_stand_summon")
    public static void phantomSummonsKingCrimsonAndStrikes(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-summon", 4, 16, 8);
        fixture.phantomsUseStands();
        quiet(fixture);
        ServerPlayer user = fixture.user;
        Zombie foe = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 5.5D, 2.5D));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // His Stand is not out at the press: the one his phantom has out, it summoned.
            KingCrimsonAddon.KING_CRIMSON.get().forceUnsummon(user, fixture.power);
            helper.assertTrue(!fixture.power.isSummoned(), "Setup: the Stand of the user is still out");
            user.getRandom().setSeed(77_201L);
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            ActorKey own = ActorKey.of(user);
            RecordedOp.StandSummon summon = KingCrimsonEpitaphFixture.ops(session, RecordedOp.StandSummon.class).stream()
                    .filter(op -> op.user().equals(own)).findFirst().orElse(null);
            fixture.rule(summon != null, "the phantom of the user, hunted by a zombie three blocks away, summoned no Stand in "
                    + session.record().foresight() + " ticks");
            if (summon != null) {
                Entity phantom = session.room().info(summon.stand()) != null ? session.room().info(summon.stand()).entity : null;
                fixture.rule(phantom instanceof StandEntity stand && stand.getUser() == session.room().info(own).entity,
                        "what was summoned in the room is not a Stand of the phantom of the user: " + phantom);
                int blows = blowsOf(session, summon.stand(), foe).size();
                fixture.rule(blows > 0, "no blow of the Stand of the phantom on the zombie that hunts the user is in the record ("
                        + KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).size() + " hits of any kind, "
                        + KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class).size() + " changes of an action)");
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The phantom of the user summons King Crimson and strikes with it");
        }));
    }

    // D2, and the gate of ruling R9
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_forbidden")
    public static void aPhantomNeverPressesAForbiddenAbility(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-forbid", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 4.5D, 2.5D));
        Set<String> pressed = new TreeSet<>();
        int[] presses = new int[1];
        // After the gate, and only what it let through.
        fixture.listen(RipplesAbilityKeyPressEvent.class, event -> {
            if (EpitaphSessions.isPhantom(event.getEntity())) {
                // A later blow of a combo goes by the name of its first with a number behind it.
                pressed.add(event.getAbility().name().replaceAll("[0-9]+$", ""));
                presses[0]++;
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(fixture.power.updateAvailableMoves().inMovesetAndCanBeUsed.containsKey(TIME_ERASE),
                    "Setup: Time Erase is not ready for the user, so his phantom could not press it either");
            for (int seed = 0; seed < 10; seed++) {
                user.getRandom().setSeed(77_300L + seed);
                EpitaphSession session = fixture.press(null);
                if (seed == 0) {
                    // A mind that is not of this module presses for the phantom: the gate is asked all the same.
                    LivingEntity phantom = (LivingEntity) session.room().phantom(user.getId());
                    StandPower power = StandPower.get(phantom);
                    Ability erase = power != null ? power.getAbility(TIME_ERASE) : null;
                    helper.assertTrue(erase != null, "Setup: the phantom of the user has no Time Erase to press");
                    EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
                    try {
                        EpitaphSandbox.setActor(phantom);
                        AbilityInput.keyPressMob(erase, phantom, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.CLICK);
                    }
                    finally {
                        EpitaphSandbox.exit();
                    }
                }
                session.foreseeAll();
                for (SandboxRoom.Phantom phantom : session.room().all()) {
                    fixture.rule(!(phantom.entity instanceof Player player) || !KingCrimsonTemporalStateService.isErasing(player),
                            "a phantom is erasing time (seed " + seed + ")");
                }
                fixture.rule(!KingCrimsonTemporalStateService.isErasing(user), "the user is erasing time (seed " + seed + ")");
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.rule(presses[0] >= 10, "Setup: in ten predictions the phantoms pressed only " + presses[0] + " abilities");
            Set<String> forbidden = new TreeSet<>(pressed);
            forbidden.removeAll(ALLOWED);
            fixture.rule(forbidden.isEmpty(), "a phantom pressed what is not on the table: " + forbidden + " (pressed in all: " + pressed + ")");
            fixture.finish("A phantom never presses a forbidden ability");
        }));
    }

    // A creature is given The World and has it out, as a creature that bears a Stand has.
    private static StandEntity standOut(KingCrimsonEpitaphFixture fixture, Mob bearer) {
        StandPower power = PowerClass.STAND.attachGet(bearer);
        StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
        fixture.helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                == StandPowerTransitions.Status.APPLIED, "Setup: The World could not be given to a creature");
        power.skipProgression();
        fixture.helper.assertTrue(type.summon(bearer, power) && power.getSummonedStandEntity() != null,
                "Setup: the creature could not summon its Stand");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        return fixture.base.track(stand);
    }

    // D3
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_bearer")
    public static void standUserMobIsForeseenWithItsStand(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-bearer", 4, 20, 8);
        fixture.budget(0);
        quiet(fixture);
        Zombie bearer = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 11.5D, 2.5D));
        bearer.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        StandEntity[] out = new StandEntity[1];
        EpitaphSession[] running = new EpitaphSession[1];
        Vec3[] stood = new Vec3[1];
        double[] worst = new double[1];
        int[] looked = new int[1];
        int[] free = new int[1];
        LivingEntity[] twin = new LivingEntity[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = standOut(fixture, bearer);
            out[0] = stand;
            stood[0] = stand.position();
            // What a mod keeps on a creature is its own: a mark from before the press, and one put on it during its walk.
            bearer.getPersistentData().putInt("kc_epitaph_before", 1);
            EpitaphSession session = fixture.predict(bearer);
            running[0] = session;
            KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
            Entity phantom = session.room().phantom(stand.getId());
            fixture.rule(phantom instanceof StandEntity && phantom != stand,
                    "the Stand of the zombie is not in the room under the id of the real one: there is " + phantom);
            fixture.rule(phantom instanceof StandEntity copy && copy.getUser() == session.room().phantom(bearer.getId()),
                    "the Stand in the room is not that of the phantom of the zombie");
            fixture.rule(session.binder().of(stand) != null && session.record().track(stand.getId()) != null,
                    "the real Stand of the zombie is on no track");
            // The power of its phantom waits and has spent, as if it had fought: at the end that is the own of the creature.
            twin[0] = (LivingEntity) session.room().phantom(bearer.getId());
            StandPower copied = twin[0] != null ? StandPower.get(twin[0]) : null;
            helper.assertTrue(copied != null && copied.hasPower(), "Setup: the phantom of the zombie has no Stand power");
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            try {
                copied.restoreAbilityCooldown("punch", 600, 600);
                copied.setStamina(0.0F);
            }
            finally {
                EpitaphSandbox.exit();
            }
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            EntityTrack track = session.record().track(out[0].getId());
            if (!session.ended()) {
                if (track != null && session.binder().of(out[0]) != null && session.clock().body() > 0.0D) {
                    looked[0]++;
                    if (looked[0] == 10) {
                        bearer.getPersistentData().putInt("kc_epitaph_walk", 2);
                    }
                    worst[0] = Math.max(worst[0],
                            out[0].position().distanceTo(KingCrimsonEpitaphFixture.on(track, session.clock().body())));
                }
                return;
            }
            // Let go, it lives a few ticks of its own before it is looked at.
            if (++free[0] < 5) {
                return;
            }
            running[0] = null;
            StandPower power = StandPower.get(bearer);
            fixture.rule(bearer.isAlive() && power != null && power.getSummonedStandEntity() == out[0] && out[0].isAlive()
                    && !out[0].isRemoved(), "let go, the zombie " + (bearer.isAlive() ? "lives" : "is dead") + ", its power knows the Stand "
                            + (power != null ? power.getSummonedStandEntity() : null) + " and the Stand that stood by it "
                            + (out[0].isRemoved() ? "is gone" : "is there"));
            StandPower left = StandPower.get(twin[0]);
            int waits = power != null ? power.getAbilityCooldown("punch") : -1;
            fixture.rule(left != null && waits > 0 && Math.abs(waits - left.getAbilityCooldown("punch")) <= 8, "let go, the power of the"
                    + " zombie waits " + waits + " ticks for its punch, and that of its phantom ended waiting "
                    + (left != null ? left.getAbilityCooldown("punch") : -1));
            fixture.rule(left != null && power != null && Math.abs(power.getStamina() - left.getStamina()) <= left.getMaxStamina() * 0.05F,
                    "let go, the zombie has " + (power != null ? power.getStamina() : -1.0F) + " stamina, and its phantom ended with "
                            + (left != null ? left.getStamina() : -1.0F));
            fixture.rule(bearer.getPersistentData().getInt("kc_epitaph_before") == 1 && bearer.getPersistentData().getInt("kc_epitaph_walk") == 2,
                    "let go, the zombie has lost what was written into its persistent data: " + bearer.getPersistentData());
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE, "Setup: the prediction ended as " + session.endedAs());
            fixture.rule(looked[0] >= 50, "Setup: the real Stand was on a track for only " + looked[0] + " server ticks");
            fixture.rule(worst[0] < 0.05D, "the real Stand was up to " + worst[0] + " blocks from where its track had it");
            double went = track != null ? track.frame(track.lastTick()).position().distanceTo(stood[0]) : 0.0D;
            fixture.rule(went > 2.0D, "Setup: the Stand in the room did not go with its user: its track ends " + went
                    + " blocks from where the real Stand stood at the press");
            fixture.finish("The Stand of a creature is foreseen with it, and the real Stand follows its track");
        }));
    }

    // D4
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_stand_mirror")
    public static void actionsAreMirroredOntoTheBody(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-mirror", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        // With the health of any zombie: the Stand kills it, and then performs nothing more.
        Zombie dummy = fixture.mob(EntityType.ZOMBIE, 4.5D, 2.5D);
        dummy.setNoAi(true);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        long[] before = new long[1];
        int[] begins = {-1};
        int[] clears = {-1};
        String[] recorded = new String[1];
        boolean[] seen = new boolean[2];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            out[0] = stand;
            user.getRandom().setSeed(77_400L);
            EpitaphSession session = fixture.press(dummy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            before[0] = LivingComponentAction.getComponent(stand).actionGeneration();
            ActorKey key = ActorKey.of(stand);
            EntityTrack track = session.record().track(stand.getId());
            helper.assertTrue(track != null && session.binder().of(stand) != null, "Setup: the Stand of the user is on no track");
            List<RecordedOp.Action> changes = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class);
            for (RecordedOp.Action op : changes) {
                if (op.actor().equals(key) && op.state().hasAction()) {
                    begins[0] = KingCrimsonEpitaphFixture.tickOf(session, op);
                    recorded[0] = abilityOf(fixture.level, op.state());
                    break;
                }
            }
            helper.assertTrue(begins[0] > 0, "Setup: the Stand of the phantom of the user began no action in the room ("
                    + changes.size() + " changes of an action in the record)");
            for (int tick = begins[0] + 1; tick <= track.lastTick() && clears[0] < 0; tick++) {
                if (track.mark(tick, LivingComponentAction.MAIN_SLOT) == null) {
                    clears[0] = tick;
                }
            }
            helper.assertTrue(clears[0] > begins[0], "Setup: the first action of the Stand in the room, " + recorded[0]
                    + " from tick " + begins[0] + ", was followed by others without a tick between them to the end of the foresight: "
                    + changes.stream().filter(op -> op.actor().equals(key)).map(op -> KingCrimsonEpitaphFixture.tickOf(session, op)
                            + ":" + abilityOf(fixture.level, op.state())).toList());
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            LivingComponentAction slot = LivingComponentAction.getComponent(out[0]);
            EntityActionInstance action = slot.getAction();
            int passed = session.passed();
            if (passed == begins[0] && !seen[0]) {
                seen[0] = true;
                fixture.rule(slot.actionGeneration() != before[0], "past tick " + begins[0]
                        + ", where the Stand in the room began " + recorded[0] + ", the action of the real Stand has not changed");
                fixture.rule(action != null && !action.isOver() && recorded[0].equals(abilityOf(action)), "past tick " + begins[0]
                        + " the real Stand performs " + abilityOf(action) + ", and the Stand in the room began " + recorded[0]);
            }
            if (passed == clears[0] && !seen[1]) {
                seen[1] = true;
                fixture.rule(action == null || action.isOver(), "past tick " + clears[0]
                        + ", where the Stand in the room performs nothing any more, the real Stand still performs " + abilityOf(action));
            }
            if (session.ended() || seen[1]) {
                running[0] = null;
                fixture.rule(seen[0] && seen[1], "Setup: the prediction ended as " + session.endedAs() + " at tick " + passed
                        + " before the ticks " + begins[0] + " and " + clears[0] + " were looked at");
                if (!session.ended()) {
                    session.end(EpitaphEnd.CANCELLED);
                }
                fixture.finish("What a Stand did in the room its body is seen to do, from the tick it began to the tick it ended");
            }
        }));
    }

    // D5
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_stand_muted")
    public static void mutedActionHurtsNobody(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-muted", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        Zombie bystander = dummy(fixture);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] hits = new int[1];
        float[] health = new float[1];
        fixture.listen(LivingIncomingDamageEvent.class, event -> {
            if (running[0] != null && event.getEntity() == bystander) {
                hits[0]++;
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            user.getRandom().setSeed(77_500L);
            EpitaphSession session = fixture.press(bystander);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            // In the room nothing can hurt it: so no blow on it is in the record, and whatever hurts the real one, which
            // stands in reach of all the Stand does, is not of the record.
            Entity phantom = session.room().phantom(bystander.getId());
            helper.assertTrue(phantom instanceof LivingEntity && session.room().tick() == 0,
                    "Setup: the room had lived " + session.room().tick() + " ticks before the test reached it");
            phantom.setInvulnerable(true);
            session.foreseeAll();
            ActorKey key = ActorKey.of(stand);
            long acted = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class).stream()
                    .filter(op -> op.actor().equals(key) && op.state().hasAction()).count();
            helper.assertTrue(acted > 0L, "Setup: the Stand of the phantom of the user began no action in the room");
            ActorKey victim = ActorKey.of(bystander);
            long foreseen = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream()
                    .filter(hit -> hit.victim().equals(victim)).count();
            helper.assertTrue(foreseen == 0L, "Setup: " + foreseen + " hits on the phantom that nothing can hurt are in the record");
            health[0] = bystander.getHealth();
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE, "Setup: the prediction ended as " + session.endedAs());
            fixture.rule(hits[0] == 0 && bystander.getHealth() == health[0], "the zombie in reach of the Stand, with no blow on it in"
                    + " the record, was hit " + hits[0] + " times for real and has " + bystander.getHealth() + " of " + health[0]
                    + " health");
            fixture.finish("An action that is ticked muted on its body hurts nobody");
        }));
    }

    // D6
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_stand_once")
    public static void standStrikeLandsOnceAtItsTick(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-once", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        Zombie struck = dummy(fixture);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        List<Integer> foreseen = new ArrayList<>();
        // The tick of the future at which each blow of the real Stand came to the real zombie.
        List<Integer> landed = new ArrayList<>();
        fixture.listen(LivingIncomingDamageEvent.class, event -> {
            EpitaphSession session = running[0];
            if (session != null && event.getEntity() == struck && isOf(event.getSource(), ActorKey.of(out[0]))) {
                landed.add(session.passed() + 1);
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            out[0] = stand;
            user.getRandom().setSeed(77_600L);
            EpitaphSession session = fixture.press(struck);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            for (RecordedOp.Damage hit : blowsOf(session, ActorKey.of(stand), struck)) {
                foreseen.add(KingCrimsonEpitaphFixture.tickOf(session, hit));
            }
            helper.assertTrue(foreseen.size() >= 2, "Setup: only " + foreseen.size() + " blows of the Stand on the zombie are in the"
                    + " record (" + KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).size() + " hits of any kind)");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE, "Setup: the prediction ended as " + session.endedAs());
            fixture.rule(landed.equals(foreseen), "the real Stand struck the real zombie at the ticks " + landed
                    + " of the future, and the record has the blows of the Stand at " + foreseen);
            fixture.finish("A blow of a Stand lands once, at its tick");
        }));
    }

    // The summon carried out (EPITAPH-ARCH 2.4, point 1)
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_summoned")
    public static void theRealStandIsSummonedWhenItsPhantomWas(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-summoned", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 8.5D, 2.5D)).getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.StandSummon[] foreseen = new RecordedOp.StandSummon[1];
        int[] at = {-1};
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            KingCrimsonAddon.KING_CRIMSON.get().forceUnsummon(user, fixture.power);
            helper.assertTrue(!fixture.power.isSummoned(), "Setup: the Stand of the user is still out");
            EpitaphSession session = fixture.press(null);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            ActorKey own = ActorKey.of(user);
            foreseen[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.StandSummon.class).stream()
                    .filter(op -> op.user().equals(own)).findFirst().orElse(null);
            helper.assertTrue(foreseen[0] != null, "Setup: the phantom of the user summoned no Stand in the room");
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, foreseen[0]);
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            StandEntity stand = fixture.power.getSummonedStandEntity();
            if (session.passed() < at[0] && !session.ended()) {
                fixture.rule(stand == null, "at tick " + session.passed() + " the user has a Stand out, and his phantom summoned its own"
                        + " only at tick " + at[0]);
                return;
            }
            running[0] = null;
            int id = foreseen[0].stand().id();
            EntityTrack track = session.record().track(id);
            fixture.rule(stand != null && stand.isAddedToLevel() && stand.getId() == id, "past tick " + at[0] + ", where his phantom"
                    + " summoned its Stand, the Stand of the user is " + (stand == null ? "not out" : "out under the id " + stand.getId()
                            + " while the Stand of his phantom has " + id));
            fixture.rule(stand != null && track != null && session.binder().of(stand) != null
                    && stand.position().distanceTo(KingCrimsonEpitaphFixture.on(track, session.clock().body())) < 0.05D,
                    "the Stand the user was given is not on the track of the Stand of his phantom");
            if (!session.ended()) {
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.finish("The real Stand is summoned when its phantom was, under its id and on its track");
        }));
    }

    // An action of the record does not outlive the track of its body
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_let_go")
    public static void anActionOfTheRecordEndsWhenItsBodyIsLetGo(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-letgo", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        Zombie dummy = dummy(fixture);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        int[] begins = {-1};
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            out[0] = stand;
            user.getRandom().setSeed(77_900L);
            EpitaphSession session = fixture.press(dummy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            ActorKey key = ActorKey.of(stand);
            for (RecordedOp.Action op : KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class)) {
                if (begins[0] < 0 && op.actor().equals(key) && op.state().hasAction()) {
                    begins[0] = KingCrimsonEpitaphFixture.tickOf(session, op);
                }
            }
            helper.assertTrue(begins[0] > 0, "Setup: the Stand of the phantom of the user began no action in the room");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() <= begins[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            LivingComponentAction slot = LivingComponentAction.getComponent(out[0]);
            helper.assertTrue(!session.ended() && slot.getAction() != null && !slot.getAction().isOver(), "Setup: past tick "
                    + session.passed() + " the real Stand performs nothing (the prediction ended as " + session.endedAs() + ")");
            String performed = abilityOf(slot.getAction());
            // He logs out, the server stops, his Stand is lost: whatever ends the prediction lets every body go.
            session.end(EpitaphEnd.CANCELLED);
            fixture.rule(slot.getAction() == null || slot.getAction().isOver(), "the prediction is over and the real Stand, let go,"
                    + " still performs the " + performed + " the record had put on it");
            fixture.finish("An action of the record ends when its body is let go");
        }));
    }

    // The hold of a key as the record has it (EPITAPH-ARCH 2.4, point 4)
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_stand_hold")
    public static void aHeldActionGoesThroughThePhasesOfTheRecord(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-hold", 4, 16, 8);
        // A barrage lasts while its key is held: on the body no key is held, and the record alone says when it ends.
        fixture.phantomsUse("barrage");
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        Zombie dummy = dummy(fixture);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        int[] begins = {-1};
        int[] clears = {-1};
        int[] looked = {-1, 0};
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            out[0] = stand;
            user.getRandom().setSeed(77_800L);
            // At his Resolve the barrage of King Crimson is for a man near his end.
            user.setHealth(2.0F);
            EpitaphSession session = fixture.press(dummy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            ActorKey key = ActorKey.of(stand);
            EntityTrack track = session.record().track(stand.getId());
            helper.assertTrue(track != null && session.binder().of(stand) != null, "Setup: the Stand of the user is on no track");
            for (RecordedOp.Action op : KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class)) {
                if (begins[0] < 0 && op.actor().equals(key) && op.state().hasAction()) {
                    begins[0] = KingCrimsonEpitaphFixture.tickOf(session, op);
                    helper.assertTrue("barrage".equals(abilityOf(fixture.level, op.state())), "Setup: the Stand in the room began "
                            + abilityOf(fixture.level, op.state()) + ", not a barrage");
                }
            }
            helper.assertTrue(begins[0] > 0, "Setup: the Stand of the phantom of the user began no barrage in the room");
            Set<ActionPhase> phases = new TreeSet<>();
            for (int tick = begins[0]; tick <= track.lastTick() && clears[0] < 0; tick++) {
                EntityTrack.ActionMark mark = track.mark(tick, LivingComponentAction.MAIN_SLOT);
                if (mark == null) {
                    clears[0] = tick;
                } else {
                    phases.add(mark.phase());
                }
            }
            helper.assertTrue(clears[0] > begins[0] && phases.contains(ActionPhase.PERFORM) && phases.contains(ActionPhase.RECOVERY),
                    "Setup: the barrage in the room, from tick " + begins[0] + " to " + clears[0] + ", went through " + phases
                            + ": it was not let go into its recovery before the foresight ended");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            int passed = session.passed();
            if (passed >= begins[0] && passed <= clears[0] && passed != looked[0]) {
                looked[0] = passed;
                looked[1]++;
                EntityTrack.ActionMark mark = session.record().track(out[0].getId()).mark(passed, LivingComponentAction.MAIN_SLOT);
                EntityActionInstance action = LivingComponentAction.getComponent(out[0]).getAction();
                boolean acts = action != null && !action.isOver();
                fixture.rule((mark != null) == acts && (mark == null || action.getPhase() == mark.phase()
                        && (int) action.getPhaseTick() == mark.phaseTick()), "past tick " + passed + " the record has the Stand at "
                        + (mark == null ? "nothing" : mark.phase() + " " + mark.phaseTick()) + " and the real Stand is at "
                        + (acts ? action.getPhase() + " " + action.getPhaseTick() : "nothing"));
            }
            if (passed >= clears[0] || session.ended()) {
                running[0] = null;
                fixture.rule(looked[1] == clears[0] - begins[0] + 1, "Setup: of the ticks " + begins[0] + " to " + clears[0] + " only "
                        + looked[1] + " were looked at (the prediction ended as " + session.endedAs() + ")");
                if (!session.ended()) {
                    session.end(EpitaphEnd.CANCELLED);
                }
                fixture.finish("A held action goes through the phases the record has for it, and ends when it does");
            }
        }));
    }

    // The hits of a barrage the core told this player of with their sound: it tells of a hit that did not land without one.
    private static int barrageHits(ServerPlayer player, int since) {
        int count = 0;
        for (TrBarrageHitSoundPacket told : KingCrimsonEraseFixture.listener(player).payloadsSince(since, TrBarrageHitSoundPacket.class)) {
            if (told.hit()) {
                count++;
            }
        }
        return count;
    }

    // What the core sends of an action that ticks muted goes out, and in the slow phase the action keeps the pace of the record
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_heard")
    public static void theBlowsOfABarrageAreHeardAsTheyLand(GameTestHelper helper) {
        // The packets of this user leave by the own send of the server: what a room or a muted tick holds back does not reach him.
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-heard", 4, 16, 8, true);
        fixture.phantomsUse("barrage");
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        // He tracks what is around him, his own Stand too: what the core tells the trackers of the Stand, it tells him.
        GameTestChunkSending.markSentAround(user);
        fixture.level.getChunkSource().move(user);
        Zombie struck = dummy(fixture);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] sent = new int[1];
        int[] due = {-1};
        int[] slow = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            StandEntity stand = fixture.power.getSummonedStandEntity();
            helper.assertTrue(KingCrimsonEpitaphFixture.tracks(user, stand), "Setup: the user does not track his own Stand");
            int before = KingCrimsonEraseFixture.listener(user).sentCount();
            // At his Resolve the barrage of King Crimson is for a man near his end.
            user.setHealth(2.0F);
            EpitaphSession session = fixture.press(struck);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            helper.assertTrue(session.mode() == EpitaphMode.SOLO_SLOW && session.room().tick() == 0, "Setup: the prediction is "
                    + session.mode() + " and its room had lived " + session.room().tick() + " ticks before the test reached it");
            // His phantom begins its barrage at once and holds the key, so that its blows fall in the slow phase.
            LivingEntity phantom = (LivingEntity) session.room().phantom(user.getId());
            StandPower power = StandPower.get(phantom);
            StandEntity copy = power != null ? power.getSummonedStandEntity() : null;
            helper.assertTrue(copy != null, "Setup: the phantom of the user has no Stand out");
            ActionTarget aim = new ActionTarget(session.room().phantom(struck.getId()));
            LivingComponentAction.getComponent(phantom).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(copy).entityAim.setTarget(aim.copy());
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            try {
                EpitaphSandbox.setActor(phantom);
                AbilityInput.keyPressMob(power.getAbility("barrage"), phantom, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
            }
            finally {
                EpitaphSandbox.exit();
            }
            session.foreseeAll();
            slow[0] = session.clock().phase() / 4;
            due[0] = (int) blowsOf(session, ActorKey.of(stand), struck).stream()
                    .filter(hit -> KingCrimsonEpitaphFixture.tickOf(session, hit) < slow[0]).count();
            helper.assertTrue(due[0] >= 3, "Setup: only " + due[0] + " blows of the barrage of the Stand fall in the slow phase, the first "
                    + slow[0] + " ticks of the future (" + blowsOf(session, ActorKey.of(stand), struck).size() + " in all)");
            int out = barrageHits(user, before);
            fixture.rule(out == 0, "the room let the sound of " + out + " blows of the phantom Stand out to the real user");
            fixture.rule(session.room().counts().dropped > 0, "what the core sent straight to a real player from the room was held"
                    + " back but is not counted");
            sent[0] = KingCrimsonEraseFixture.listener(user).sentCount();
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < slow[0] - 1 && !session.ended()) {
                return;
            }
            running[0] = null;
            int heard = barrageHits(user, sent[0]);
            fixture.rule(heard == due[0], "in the slow phase " + due[0] + " blows of the barrage of the Stand landed on the real zombie,"
                    + " and the user was sent the sound of " + heard + " (the prediction " + (session.ended() ? "ended as "
                            + session.endedAs() : "is at tick " + session.passed()) + ")");
            if (!session.ended()) {
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.finish("The blows of a barrage are heard as they land");
        }));
    }

    // What a press costs (EPITAPH-ARCH 2.4, point 5)
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_stand_cost")
    public static void whatHisPhantomPressedHeWaitsFor(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-cost", 4, 16, 8);
        // The chop sets its cooldown as its key is pressed: the press is not made again on the body.
        fixture.phantomsUse(CHOP);
        fixture.budget(0);
        quiet(fixture);
        ServerPlayer user = fixture.user;
        Zombie dummy = dummy(fixture);
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.Cooldown[] set = new RecordedOp.Cooldown[1];
        int[] at = {-1};
        float[] left = new float[1];
        boolean[] seen = new boolean[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            user.getRandom().setSeed(77_950L);
            helper.assertTrue(fixture.power.getAbilityCooldown(CHOP) == 0, "Setup: the user waits for his chop before the press");
            // A bar that is not full: in the slow phase it fills at the pace of his track, as that of his phantom did (R10).
            fixture.power.setStamina(fixture.power.getMaxStamina() * 0.5F);
            EpitaphSession session = fixture.press(dummy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            ActorKey own = ActorKey.of(user);
            List<RecordedOp.Cooldown> waits = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Cooldown.class);
            set[0] = waits.stream().filter(op -> op.actor().equals(own) && op.ability().equals(CHOP)).findFirst().orElse(null);
            helper.assertTrue(set[0] != null && set[0].ticks() > 20, "Setup: no cooldown of a chop of the phantom of the user is in"
                    + " the record: " + waits);
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, set[0]);
            StandPower twin = StandPower.get((LivingEntity) session.room().info(own).entity);
            helper.assertTrue(twin != null, "Setup: the phantom of the user has no Stand power");
            left[0] = twin.getStamina();
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            int waits = fixture.power.getAbilityCooldown(CHOP);
            if (!session.ended()) {
                int passed = session.passed();
                if (passed < at[0]) {
                    fixture.rule(waits == 0, "at tick " + passed + " the user waits " + waits + " ticks for a chop his phantom pressed"
                            + " only at tick " + at[0]);
                } else if (passed == at[0] && !seen[0]) {
                    seen[0] = true;
                    fixture.rule(waits > set[0].ticks() - 3 && waits <= set[0].ticks(), "past tick " + at[0] + ", where the chop of his"
                            + " phantom set a cooldown of " + set[0].ticks() + " ticks, the user waits " + waits + " ticks for his chop");
                }
                return;
            }
            running[0] = null;
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && seen[0], "Setup: the prediction ended as " + session.endedAs()
                    + (seen[0] ? "" : " before tick " + at[0] + " was looked at"));
            // His power paid for what the action did as it ticked on his Stand, and filled up at the pace of his track.
            float has = fixture.power.getStamina();
            fixture.rule(Math.abs(has - left[0]) <= fixture.power.getMaxStamina() * 0.03F, "at the end of his walk the user has " + has
                    + " stamina of " + fixture.power.getMaxStamina() + ", and his phantom ended with " + left[0]);
            fixture.finish("What his phantom pressed costs the user what it cost his phantom");
        }));
    }

    // What a body is in the middle of at the press (the core hands an action in progress over)
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_taken_over")
    public static void anActionInProgressAtThePressGoesOn(GameTestHelper helper) {
        inProgressAtThePress(helper, "kc-ep-taken", true);
    }

    // What is not on the table goes on in no room, pressed there or in progress at the press: an action may reach a whole level
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_not_taken")
    public static void anActionThatIsNotOnTheTableIsNotTakenOver(GameTestHelper helper) {
        inProgressAtThePress(helper, "kc-ep-not-taken", false);
    }

    private static void inProgressAtThePress(GameTestHelper helper, String name, boolean onTable) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, name, 4, 20, 8);
        // The barrage of The World is on the table of the module; the table of a test is empty until it says otherwise.
        if (onTable) {
            fixture.phantomsUseStands();
        }
        fixture.budget(0);
        quiet(fixture);
        // It hunts the user from afar, its Stand striking at the air: its mind runs and has somebody to fight, so its copy
        // holds the barrage it takes over as it holds one of its own.
        Zombie bearer = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 11.5D, 4.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        EntityActionInstance[] own = new EntityActionInstance[1];
        int[] stood = new int[1];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> {
            out[0] = standOut(fixture, bearer);
            Ability barrage = StandPower.get(bearer).getAbility("barrage");
            helper.assertTrue(barrage != null, "Setup: The World has no barrage");
            HeldInputEntry held = AbilityInput.keyPressMob(barrage, bearer, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
            helper.assertTrue(held != null, "Setup: the zombie could not press the barrage of its Stand");
            short key = held.keyId;
            fixture.base.onClose(() -> AbilityInput.keyRelease(key, bearer));
        }));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EntityActionInstance action = LivingComponentAction.getComponent(out[0]).getAction();
            helper.assertTrue(action != null && !action.isOver(), "Setup: three ticks after its key went down the Stand of the zombie"
                    + " performs nothing");
            own[0] = action;
            ActionPhase phase = action.getPhase();
            stood[0] = (int) action.getPhaseTick();
            EpitaphSession session = fixture.predict(bearer);
            KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
            EntityActionInstance taken = session.room().phantom(out[0].getId()) instanceof LivingEntity copy
                    ? LivingComponentAction.getCurEntityAction(copy) : null;
            String found = "at the press the real Stand was at " + phase + " " + stood[0] + " of its " + abilityOf(action)
                    + ", and its copy in the room is at " + (taken == null ? "nothing" : taken.getPhase() + " "
                            + (int) taken.getPhaseTick() + " of its " + abilityOf(taken));
            if (onTable) {
                fixture.rule(taken != null && taken != action && !taken.isOver() && abilityOf(taken).equals(abilityOf(action))
                        && taken.getPhase() == phase && (int) taken.getPhaseTick() == stood[0], found);
            } else {
                fixture.rule(taken == null, found + ": the barrage is not on the table of this test");
            }
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < 3 && !session.ended()) {
                return;
            }
            running[0] = null;
            int passed = session.passed();
            EntityTrack track = session.record().track(out[0].getId());
            EntityTrack.ActionMark mark = track != null ? track.mark(passed, LivingComponentAction.MAIN_SLOT) : null;
            helper.assertTrue(!session.ended(), "Setup: the prediction ended as " + session.endedAs() + " at tick " + passed);
            EntityActionInstance now = LivingComponentAction.getComponent(out[0]).getAction();
            String real = now == null ? "nothing" : now.getPhase() + " " + now.getPhaseTick();
            fixture.rule(now == own[0], "at tick " + passed + " the real Stand no longer has the action it was in the middle of at the"
                    + " press: it performs " + abilityOf(now));
            if (onTable) {
                fixture.rule(mark != null, "at tick " + passed + " the record has the Stand of the zombie at nothing: its copy did not go"
                        + " on with the action");
                fixture.rule(mark == null || now != null && now.getPhase() == mark.phase() && (int) now.getPhaseTick() == mark.phaseTick(),
                        "past tick " + passed + " the record has the Stand at " + (mark == null ? "nothing" : mark.phase() + " "
                                + mark.phaseTick()) + " and the real Stand is at " + real);
            } else {
                // Left as it was, as before the core could hand an action over: it waits, and goes on when its body is let go.
                fixture.rule(mark == null && now != null && (int) now.getPhaseTick() == stood[0], "at tick " + passed + " the record"
                        + " has the Stand at " + (mark == null ? "nothing" : mark.phase() + " " + mark.phaseTick())
                        + " and the real Stand, at tick " + stood[0] + " of its action at the press, is at " + real);
            }
            ActorKey key = ActorKey.of(out[0]);
            long changes = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class).stream()
                    .filter(op -> op.actor().equals(key) && KingCrimsonEpitaphFixture.tickOf(session, op) <= passed).count();
            fixture.rule(changes == 0L, "the record tells of " + changes + " changes of the action of the Stand in its first " + passed
                    + " ticks");
            if (onTable && track != null) {
                // The hold that was taken over is the hold of the copy: let go as it lets go a barrage of its own, a second
                // after the press (EPITAPH-ARCH, ruling R12 point 3).
                session.foreseeAll();
                int held = 1;
                while (held <= track.lastTick() && track.mark(held, LivingComponentAction.MAIN_SLOT) != null
                        && track.mark(held, LivingComponentAction.MAIN_SLOT).phase() == ActionPhase.PERFORM) {
                    held++;
                }
                fixture.rule(held >= 18 && held <= 26, "the copy of the Stand went on with the barrage it took over for " + held
                        + " ticks from the press: its mind lets go a barrage it holds after 20");
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish(onTable ? "An action in progress at the press goes on, in the room and on the body"
                    : "An action that is not on the table is not taken over");
        }));
    }

    // The audit of ruling R9, as A15 was for creatures: what the table lets a phantom press stays in the room, for every Stand
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_stand_audit")
    public static void whatTheTableAllowsStaysInTheRoomForEveryStand(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-audit", 4, 16, 10);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        ServerLevel level = fixture.level;
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            KingCrimsonEpitaphAudit.Report report = KingCrimsonEpitaphAudit.run(fixture.user, fixture.spot(8.5D, 2.0D, 4.5D),
                    fixture.spot(10.5D, 2.0D, 4.5D), fixture.absolute(-1, 0, -1), fixture.absolute(16, 8, 10));
            for (String broken : report.broken()) {
                fixture.rule(false, broken);
            }
            List<String> audited = report.audited();
            fixture.rule(audited.size() >= 2 && report.presses() >= audited.size() * 2, "Setup: " + audited.size() + " Stand types were"
                    + " audited with " + report.presses() + " presses in all; not audited: " + report.notAudited());
            fixture.rule(!EpitaphSessions.any, "a prediction is left running");
            fixture.finish("What the table allows stays in the room, for every Stand the server has (" + audited.size() + " audited)");
        }));
    }

    // Ruling R13, point 5: a pair of Stand type and ability on the list of exclusions is neither taken over nor pressed
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_stand_excluded")
    public static void anExcludedPairIsNeitherTakenOverNorPressed(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-excluded", 4, 20, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        List<String> none = PhantomAbilityAllowList.replaceExclusions(List.of("jojo_ripples:the_world/barrage"));
        fixture.base.onClose(() -> PhantomAbilityAllowList.replaceExclusions(none));
        Zombie bearer = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 11.5D, 4.5D));
        StandEntity[] out = new StandEntity[1];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> {
            out[0] = standOut(fixture, bearer);
            Ability barrage = StandPower.get(bearer).getAbility("barrage");
            helper.assertTrue(barrage != null, "Setup: The World has no barrage");
            HeldInputEntry held = AbilityInput.keyPressMob(barrage, bearer, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
            helper.assertTrue(held != null, "Setup: the zombie could not press the barrage of its Stand");
            short key = held.keyId;
            fixture.base.onClose(() -> AbilityInput.keyRelease(key, bearer));
        }));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EntityActionInstance action = LivingComponentAction.getComponent(out[0]).getAction();
            helper.assertTrue(action != null && !action.isOver(), "Setup: three ticks after its key went down the Stand of the zombie"
                    + " performs nothing");
            StandPower power = StandPower.get(bearer);
            Ability barrage = power.getAbility("barrage");
            Ability punch = power.getAbility("punch");
            Ability ofAnother = fixture.power.getAbility("barrage");
            fixture.rule(!PhantomAbilityAllowList.allows(barrage, power) && PhantomAbilityAllowList.excludes(barrage, power)
                    && PhantomAbilityAllowList.allows(punch, power) && PhantomAbilityAllowList.allows(ofAnother, fixture.power),
                    "with the barrage of The World excluded: that barrage is "
                            + (PhantomAbilityAllowList.allows(barrage, power) ? "allowed" : "refused") + ", the punch of The World "
                            + (PhantomAbilityAllowList.allows(punch, power) ? "allowed" : "refused") + ", the barrage of King Crimson "
                            + (PhantomAbilityAllowList.allows(ofAnother, fixture.power) ? "allowed" : "refused"));
            EpitaphSession session = fixture.predict(bearer);
            KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
            LivingEntity copy = session.room().phantom(bearer.getId()) instanceof LivingEntity living ? living : null;
            StandPower copied = copy != null ? StandPower.get(copy) : null;
            helper.assertTrue(copied != null && copied.getSummonedStandEntity() != null, "Setup: the copy of the zombie has no Stand"
                    + " out in the room");
            EntityActionInstance taken = LivingComponentAction.getCurEntityAction(copied.getSummonedStandEntity());
            fixture.rule(taken == null, "the copy of the Stand took over its " + abilityOf(taken) + ", which is excluded for The World");
            HeldInputEntry again;
            HeldInputEntry other;
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            try {
                EpitaphSandbox.setActor(copy);
                again = AbilityInput.keyPressMob(copied.getAbility("barrage"), copy, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER),
                        InputMethod.HOLD);
                other = AbilityInput.keyPressMob(copied.getAbility("punch"), copy, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER),
                        InputMethod.HOLD);
            }
            finally {
                EpitaphSandbox.exit();
            }
            fixture.rule(again == null && other != null, "in the room the copy " + (again != null ? "pressed" : "was refused")
                    + " the excluded barrage and " + (other != null ? "pressed" : "was refused") + " the punch");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("An excluded pair of Stand and ability is neither taken over nor pressed");
        }));
    }

    // What the muted ticks booked on the power of a creature is not taken a second time (EPITAPH-ARCH, ruling R10)
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_stand_booked")
    public static void aCreatureWhoseStandBarragedHasTheStaminaOfItsPhantom(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-booked", 4, 20, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        // It stands away from the user and its Stand strikes at the air.
        Zombie bearer = fixture.sturdy(fixture.mob(EntityType.ZOMBIE, 11.5D, 4.5D));
        bearer.setNoAi(true);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        LivingEntity[] twin = new LivingEntity[1];
        int[] free = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            out[0] = standOut(fixture, bearer);
            StandPower power = StandPower.get(bearer);
            power.setStamina(power.getMaxStamina());
            EpitaphSession session = fixture.predict(bearer);
            KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
            twin[0] = session.room().phantom(bearer.getId()) instanceof LivingEntity copy ? copy : null;
            StandPower copied = twin[0] != null ? StandPower.get(twin[0]) : null;
            helper.assertTrue(copied != null && copied.getAbility("barrage") != null && session.room().tick() == 0,
                    "Setup: the copy of the zombie has no barrage to press, or the room has lived already");
            // Its copy holds a barrage from the press on: every tick of it costs, in the room and, muted, on the body, where
            // the cost of a tick is booked for the next tick of the power, and the power of a held creature does not tick.
            HeldInputEntry held;
            EpitaphSandbox.enter(session.room(), EpitaphSandbox.Mode.SEALED);
            try {
                EpitaphSandbox.setActor(twin[0]);
                held = AbilityInput.keyPressMob(copied.getAbility("barrage"), twin[0], new FriendlyByteBuf(Unpooled.EMPTY_BUFFER),
                        InputMethod.HOLD);
            }
            finally {
                EpitaphSandbox.exit();
            }
            helper.assertTrue(held != null, "Setup: the copy of the zombie could not press the barrage of its Stand");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            // Let go, it lives a few ticks of its own before it is looked at: the first of them takes what is booked.
            if (session == null || !session.ended() || ++free[0] < 5) {
                return;
            }
            running[0] = null;
            EntityTrack track = session.record().track(out[0].getId());
            int barraged = 0;
            for (int tick = 1; track != null && tick <= track.lastTick(); tick++) {
                EntityTrack.ActionMark mark = track.mark(tick, LivingComponentAction.MAIN_SLOT);
                barraged += mark != null && mark.phase() == ActionPhase.PERFORM ? 1 : 0;
            }
            helper.assertTrue(session.endedAs() == EpitaphEnd.COMPLETE && barraged >= 20, "Setup: the prediction ended as "
                    + session.endedAs() + " and the Stand in the room barraged for " + barraged + " ticks");
            StandPower power = StandPower.get(bearer);
            StandPower left = StandPower.get(twin[0]);
            fixture.rule(power != null && left != null && Math.abs(power.getStamina() - left.getStamina()) <= 25.0F, "five ticks after it"
                    + " was let go the zombie has " + (power != null ? power.getStamina() : -1.0F) + " stamina, and its phantom, whose"
                    + " Stand barraged for " + barraged + " ticks, ended with " + (left != null ? left.getStamina() : -1.0F));
            fixture.finish("A creature whose Stand barraged has the stamina of its phantom");
        }));
    }

    // A hold that was taken over is the hold of the copy (EPITAPH-ARCH, ruling R12 point 3)
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_stand_hold_taken")
    public static void aHoldThatWasTakenOverIsLetGoByItsCopy(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-hold-taken", 4, 20, 12);
        fixture.phantomsUseStands();
        fixture.budget(0);
        quiet(fixture);
        // Two creatures whose Stands guard at the press with nobody to guard against, and a guard has no end of its own: a cow,
        // whose mind runs and is after nobody, and a zombie whose mind does not run.
        Cow minded = fixture.mob(EntityType.COW, 11.5D, 2.5D);
        Zombie mindless = fixture.sturdy(fixture.mob(EntityType.ZOMBIE, 11.5D, 9.5D));
        mindless.setNoAi(true);
        Mob[] bearers = {minded, mindless};
        StandEntity[] out = new StandEntity[2];
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT - 3, () -> fixture.guarded(() -> {
            for (int i = 0; i < 2; i++) {
                out[i] = standOut(fixture, bearers[i]);
                Mob bearer = bearers[i];
                Ability guard = StandPower.get(bearer).getAbility("guard");
                HeldInputEntry held = guard != null
                        ? AbilityInput.keyPressMob(guard, bearer, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD) : null;
                helper.assertTrue(held != null, "Setup: a " + bearer.getType().toShortString() + " could not press the guard of its Stand");
                short key = held.keyId;
                fixture.base.onClose(() -> AbilityInput.keyRelease(key, bearer));
            }
        }));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            for (int i = 0; i < 2; i++) {
                EntityActionInstance action = LivingComponentAction.getComponent(out[i]).getAction();
                helper.assertTrue(action != null && !action.isOver() && "guard".equals(abilityOf(action)), "Setup: at the press the Stand"
                        + " of the " + bearers[i].getType().toShortString() + " performs " + abilityOf(action) + ", not its guard");
            }
            EpitaphSession session = fixture.predict(minded, mindless);
            KingCrimsonEpitaphFixture.acceptTeleport(fixture.user);
            for (int i = 0; i < 2; i++) {
                EntityTrack track = session.record().track(out[i].getId());
                helper.assertTrue(track != null && track.mark(0, LivingComponentAction.MAIN_SLOT) != null, "Setup: the copy of the"
                        + " Stand of the " + bearers[i].getType().toShortString() + " did not take its guard over");
            }
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < 3 && !session.ended()) {
                return;
            }
            running[0] = null;
            helper.assertTrue(!session.ended(), "Setup: the prediction ended as " + session.endedAs());
            for (int i = 0; i < 2; i++) {
                String whose = "the Stand of the " + bearers[i].getType().toShortString() + (i == 0 ? ", whose mind runs" : ", whose mind does not run");
                EntityTrack.ActionMark mark = session.record().track(out[i].getId()).mark(session.passed(), LivingComponentAction.MAIN_SLOT);
                fixture.rule(mark == null, "at tick " + session.passed() + " the copy of " + whose + " still guards ("
                        + (mark == null ? "" : mark.phase() + " " + mark.phaseTick()) + "): nobody let the hold go that it took over");
                EntityActionInstance now = LivingComponentAction.getComponent(out[i]).getAction();
                fixture.rule(now == null || now.isOver(), "past tick " + session.passed() + " the real " + whose.substring(4)
                        + " still performs its " + abilityOf(now));
            }
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A hold that was taken over is let go by its copy, and its body with it");
        }));
    }
}
