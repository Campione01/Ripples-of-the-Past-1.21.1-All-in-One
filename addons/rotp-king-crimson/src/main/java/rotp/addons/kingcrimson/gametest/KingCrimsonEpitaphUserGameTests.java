package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.PacketBytes;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxDriver;
import rotp.addons.kingcrimson.network.EpitaphForcedWalkPayload;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;

/**
 * Epitaph, the user (EPITAPH-ARCH 3.1 to 3.4 and section 4). His phantom fights what hunts him and gets out of the
 * way of harm; he is walked along what it does, and what was foreseen for him lands on him at its tick; an erase
 * frees him and voids his own part of the prediction, and that of nobody else.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphUserGameTests {
    private static final short STEP_KEY = 33;

    private KingCrimsonEpitaphUserGameTests() {}

    // The passive erase would answer a foreseen hit: where a test is about the hit itself, it is switched off.
    private static void withoutReflex(KingCrimsonEpitaphFixture fixture) {
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        fixture.steadyHealth();
    }

    // The press comes before a creature that was just put there has had the time to reach the user for real.
    private static final int PRESS_AT = 5;

    private static List<RecordedOp.Damage> blowsOf(EpitaphSession session, Entity striker) {
        ActorKey key = ActorKey.of(striker);
        return KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream().filter(hit -> hit.actor().equals(key)).toList();
    }

    private static List<RecordedOp.Damage> hitsOn(EpitaphSession session, Entity struck) {
        ActorKey key = ActorKey.of(struck);
        return KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream().filter(hit -> hit.victim().equals(key)).toList();
    }

    // C1
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_user_foes")
    public static void userPhantomFightsOnlyWhatHuntsTheUser(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-foes", 4, 20, 8);
        withoutReflex(fixture);
        Zombie hunter = fixture.hunter(EntityType.ZOMBIE, 9.5D, 2.5D);
        // Beside the user, nearer than what hunts him, and after nobody.
        Cow cow = fixture.mob(EntityType.COW, 3.5D, 4.5D);
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.predict(hunter, cow);
            session.foreseeAll();
            List<RecordedOp.Damage> blows = blowsOf(session, fixture.user);
            ActorKey zombie = ActorKey.of(hunter);
            ActorKey beside = ActorKey.of(cow);
            fixture.rule(blows.stream().anyMatch(hit -> hit.victim().equals(zombie)), "Setup: in " + session.record().foresight()
                    + " ticks the phantom of the user never struck the zombie that hunts him (" + blows.size() + " blows in all)");
            long onCow = blows.stream().filter(hit -> hit.victim().equals(beside)).count();
            fixture.rule(onCow == 0L, "the phantom of the user struck a cow " + onCow + " times that stood beside it and was after nobody");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The phantom of the user fights only what hunts him");
        }));
    }


    // C2
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_creeper")
    public static void userPhantomAvoidsTheSwellingCreeper(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-creeper", 4, 24, 12);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        Creeper creeper = fixture.hunter(EntityType.CREEPER, 5.5D, 2.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.Explode[] foreseen = new RecordedOp.Explode[1];
        int[] at = new int[1];
        float[] health = new float[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // It will go off whatever its target does: a blast there is no talking it out of.
            creeper.ignite();
            health[0] = user.getHealth();
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            List<RecordedOp.Explode> blasts = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Explode.class);
            helper.assertTrue(!blasts.isEmpty(), "Setup: the lit creeper did not go off in the room");
            RecordedOp.Explode blast = blasts.get(0);
            foreseen[0] = blast;
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, blast);
            EntityTrack own = session.record().track(user.getId());
            Vec3 centre = new Vec3(blast.x(), blast.y(), blast.z());
            double away = KingCrimsonEpitaphFixture.on(own, at[0]).distanceTo(centre);
            fixture.rule(away > 2.0D * blast.radius(), "at the blast of tick " + at[0] + " the phantom of the user stood " + away
                    + " blocks from it: a blast of power " + blast.radius() + " harms within " + 2.0D * blast.radius());
            long burnt = hitsOn(session, user).stream().filter(hit -> hit.source().is(DamageTypeTags.IS_EXPLOSION)).count();
            fixture.rule(burnt == 0L, "the phantom of the user was caught by the blast " + burnt + " times");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < at[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            // For real: walked along what his phantom did, he is out of the blast when it comes, and unhurt.
            Vec3 centre = new Vec3(foreseen[0].x(), foreseen[0].y(), foreseen[0].z());
            double away = user.position().distanceTo(centre);
            fixture.rule(session.passed() >= at[0] && !creeper.isAlive() && away > 2.0D * foreseen[0].radius() && user.getHealth() == health[0],
                    "when the real blast came (tick " + session.passed() + " of " + at[0] + ", the creeper " + (creeper.isAlive() ? "alive" : "gone")
                            + ") the user stood " + away + " blocks from it with " + user.getHealth() + " of " + health[0] + " health");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The phantom of the user gets out of the blast of a creeper");
        }));
    }

    // C3
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_walk")
    public static void userIsWalkedAlongHisTrack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-walk", 2, 20, 6);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 14.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        int[] checked = new int[1];
        int[] sentBefore = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            sentBefore[0] = KingCrimsonEraseFixture.listener(user).sentCount();
            running[0] = fixture.press(null);
            helper.assertTrue(EpitaphPredictions.isForcedWalking(user), "Setup: after the press the user is not walked");
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            // What he is shown at the press: his own phantom among the others, as a player, under an id of its own.
            boolean shown = false;
            for (byte[] bytes : running[0].stream().frame(0)) {
                for (var packet : PacketBytes.decode(bytes, user.registryAccess())) {
                    shown |= packet instanceof ClientboundAddEntityPacket add && add.getId() == user.getId()
                            && add.getType() == EntityType.PLAYER && !add.getUUID().equals(user.getUUID());
                }
            }
            fixture.rule(shown, "the first frame the walked user is sent does not make his own phantom, as a player with his id");
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            EntityTrack track = session.record().track(user.getId());
            if (!session.ended()) {
                double tick = session.clock().body();
                double off = user.position().distanceTo(KingCrimsonEpitaphFixture.on(track, tick));
                fixture.rule(off < 1.0E-6D, "at tick " + tick + " of his track the user stood " + off
                        + " blocks from where his phantom stood then");
                if (++checked[0] == 15) {
                    // What his client sends when he pushes a key: a step to the side.
                    Vec3 before = user.position();
                    user.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(before.x, before.y, before.z + 0.4D, true));
                    double moved = user.position().distanceTo(before);
                    fixture.rule(moved < 1.0E-9D, "a move sent by the client of the walked user moved him " + moved + " blocks");
                }
                return;
            }
            running[0] = null;
            Vec3 start = track.frame(0).position();
            Vec3 end = track.frame(track.lastTick()).position();
            fixture.rule(start.distanceTo(end) > 2.0D, "Setup: the phantom of the user, with a zombie coming at it, went only from "
                    + KingCrimsonEpitaphFixture.at(start) + " to " + KingCrimsonEpitaphFixture.at(end));
            double off = user.position().distanceTo(end);
            fixture.rule(checked[0] >= 40 && !EpitaphPredictions.isForcedWalking(user) && off < 1.0E-6D, "walked for " + checked[0]
                    + " server ticks and let go, the user " + (EpitaphPredictions.isForcedWalking(user) ? "is still walked and " : "")
                    + "stands " + off + " blocks from where his phantom ended");
            List<EpitaphForcedWalkPayload> told = KingCrimsonEraseFixture.listener(user).payloadsSince(sentBefore[0], EpitaphForcedWalkPayload.class);
            long begun = told.stream().filter(walk -> walk.kind() == EpitaphForcedWalkPayload.Kind.BEGIN).count();
            long steps = told.stream().filter(walk -> walk.kind() == EpitaphForcedWalkPayload.Kind.STEP).count();
            long ends = told.stream().filter(walk -> walk.kind() == EpitaphForcedWalkPayload.Kind.RELEASE).count();
            fixture.rule(begun == 1L && ends == 1L && steps >= checked[0] - 2L, "his client was told of the beginning of his walk " + begun
                    + " times, of its end " + ends + " times and of " + steps + " places over " + checked[0] + " ticks");
            // His own again: the same move is taken now.
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            Vec3 before = user.position();
            user.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(before.x, before.y, before.z + 0.4D, true));
            double moved = user.position().distanceTo(before);
            fixture.rule(moved > 0.3D, "let go, a move sent by his client moved the user only " + moved + " blocks");
            fixture.finish("The user is walked along his track");
        }));
    }


    // C4, and T4 of section 4: the hit comes as a real hit, with the amount it was dealt with.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_hit")
    public static void aForeseenHitLandsOnTheUserAtItsTick(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-hit", 3, 16, 6);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerLevel level = fixture.level;
        ServerPlayer user = fixture.user;
        // On hard a player takes half as much again of a blow of a creature: the blow is scaled once, by him.
        Difficulty difficulty = level.getDifficulty();
        fixture.base.onClose(() -> level.getServer().setDifficulty(difficulty, true));
        level.getServer().setDifficulty(Difficulty.HARD, true);
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 5.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.Damage[] foreseen = new RecordedOp.Damage[1];
        int[] at = new int[1];
        List<String> landed = new ArrayList<>();
        fixture.listen(LivingIncomingDamageEvent.class, event -> {
            EpitaphSession session = running[0];
            if (session != null && event.getEntity() == user) {
                landed.add("while tick " + (session.passed() + 1) + " was carried out a hit of " + event.getAmount() + " by "
                        + (event.getSource().getEntity() == zombie ? "the zombie" : String.valueOf(event.getSource().getEntity())));
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            List<RecordedOp.Damage> hits = hitsOn(session, user);
            helper.assertTrue(!hits.isEmpty(), "Setup: in " + session.record().foresight() + " ticks the zombie never hit the phantom of the user");
            foreseen[0] = hits.get(0);
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, foreseen[0]);
            // A zombie strikes once a second, and a player can be hit again half a second after a hit.
            fixture.rule(hits.size() >= 2, "in the " + (session.record().foresight() - at[0]) + " ticks after its first hit the zombie that"
                    + " stands at the phantom of the user never hit it again: the frames after a hit do not run down for it");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < at[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            RecordedOp.Damage hit = foreseen[0];
            String expected = "while tick " + at[0] + " was carried out a hit of " + hit.amount() * 1.5F + " by the zombie";
            fixture.rule(KingCrimsonGroundGameTestFixture.near(hit.amount(), 3.0F), "the blow of a zombie was recorded as " + hit.amount()
                    + ", not as the 3 it was dealt with");
            fixture.rule(landed.equals(List.of(expected)), "foreseen for tick " + at[0] + ": " + expected + "; what landed on the user: " + landed);
            fixture.rule(KingCrimsonGroundGameTestFixture.near(user.getHealth(), hit.healthAfter()), "after the hit the user has "
                    + user.getHealth() + " health, his phantom had " + hit.healthAfter());
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A foreseen hit lands on the user at its tick");
        }));
    }

    // C8
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_user_logout")
    public static void forcedUserIsFreeAfterLogout(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-logout", 2, 20, 6);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 14.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.press(null);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
        }));
        helper.runAfterDelay(PRESS_AT + 12, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(!session.ended() && EpitaphPredictions.isForcedWalking(user), "Setup: twelve ticks after the press the user is not walked");
            Vec3 stood = user.position();
            EpitaphSessions.onLogout(new PlayerEvent.PlayerLoggedOutEvent(user));
            fixture.rule(session.endedAs() == EpitaphEnd.USER_LOGGED_OUT && !EpitaphSessions.any, "the logout of the walked user ended his prediction as "
                    + session.endedAs());
            fixture.rule(!EpitaphPredictions.isForcedWalking(user) && user.position().distanceTo(stood) < 1.0E-9D,
                    "logged out in the middle of his walk, the user is still walked, or was moved " + user.position().distanceTo(stood) + " blocks");
            // What is saved of him is a player like any other: his next login finds him his own.
            var kept = user.saveWithoutId(new net.minecraft.nbt.CompoundTag())
                    .getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY).getAllKeys();
            fixture.rule(kept.stream().noneMatch(key -> key.contains("epitaph")), "what is saved of him holds something of Epitaph: " + kept);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            user.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(stood.x, stood.y, stood.z + 0.4D, true));
            fixture.rule(user.position().distanceTo(stood) > 0.3D, "after the logout a move of his client still does nothing");
            fixture.finish("A walked user is free after his logout");
        }));
    }


    // C9 (rulings after stages A and B, R1): a player foreseen to die takes his last foreseen blow for real.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_death")
    public static void aForeseenDeathOfAPlayerIsHisLastHit(GameTestHelper helper) {
        foreseenDeath(helper, "kc-ep-death", false);
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_death_forced")
    public static void withTheSettingAForeseenDeathOfAPlayerIsForced(GameTestHelper helper) {
        foreseenDeath(helper, "kc-ep-forced", true);
    }

    private static void foreseenDeath(GameTestHelper helper, String name, boolean forced) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, name, 2, 16, 6);
        fixture.budget(0);
        withoutReflex(fixture);
        boolean was = KingCrimsonConfig.EPITAPH_FORCE_PLAYER_DEATH.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_FORCE_PLAYER_DEATH.set(was));
        KingCrimsonConfig.EPITAPH_FORCE_PLAYER_DEATH.set(forced);
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 5.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        int[] at = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // Nearly dead as his future is foreseen: his copy dies of the first blow.
            user.setHealth(2.0F);
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            ActorKey key = ActorKey.of(user);
            List<RecordedOp.Death> deaths = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Death.class).stream()
                    .filter(death -> death.victim().equals(key)).toList();
            helper.assertTrue(!deaths.isEmpty(), "Setup: the phantom of a user with 2 health was not killed by the zombie");
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, deaths.get(0));
            // The Stand of a phantom that dies leaves the room with it, in the tick of its death.
            ActorKey standKey = ActorKey.of(fixture.power.getSummonedStandEntity());
            List<Integer> left = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Remove.class).stream()
                    .filter(gone -> gone.actor().equals(standKey)).map(gone -> KingCrimsonEpitaphFixture.tickOf(session, gone)).toList();
            fixture.rule(left.size() == 1 && left.get(0) == at[0], "the phantom of the user died at tick " + at[0]
                    + " and its Stand left the room at " + left);
            // What his copy did not have: he is whole when the blow comes.
            user.setHealth(user.getMaxHealth());
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            // Not forced, he is looked at three ticks past his foreseen death: what followed from it in the room has its ticks then.
            if (session == null || session.passed() < at[0] + (forced ? 0 : 3) && !session.ended()) {
                return;
            }
            running[0] = null;
            if (forced) {
                fixture.rule(user.isDeadOrDying() && session.endedAs() == EpitaphEnd.USER_DIED, "with his foreseen death forced, at tick "
                        + at[0] + " the user has " + user.getHealth() + " health and his prediction ended as " + session.endedAs());
            } else {
                fixture.rule(user.isAlive() && user.getHealth() < user.getMaxHealth(), "foreseen to die at tick " + at[0]
                        + " and whole when the blow came, the user has " + user.getHealth() + " health: the blow lands, and it alone");
                fixture.rule(!EpitaphPredictions.isForcedWalking(user) && !session.ended(), "after the tick of his foreseen death the user "
                        + (EpitaphPredictions.isForcedWalking(user) ? "is still walked" : "is let go") + " and his prediction "
                        + (session.ended() ? "ended as " + session.endedAs() : "goes on"));
                // The death did not happen, and neither does what followed from it in the room, where his Stand went with him.
                var stand = fixture.power.getSummonedStandEntity();
                fixture.rule(stand != null && !stand.isRemoved() && session.binder().of(stand) == null && session.userVoid(),
                        "three ticks after the death he lived through, the Stand of the user is " + (stand == null || stand.isRemoved()
                                ? "gone" : session.binder().of(stand) != null ? "out, on a track" : "out and his own") + " and his part is "
                                + (session.userVoid() ? "void" : "not void"));
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.finish(forced ? "With the setting a foreseen death of a player is forced" : "A foreseen death of a player is his last hit");
        }));
    }

    // C7, and T4: the reflex judges a foreseen hit as any hit, and the erase it starts voids his part at once.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_reflex")
    public static void reflexSeesTheForeseenHit(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-reflex", 4, 16, 6);
        fixture.budget(0);
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 5.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        int[] at = new int[1];
        float[] health = new float[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            List<RecordedOp.Damage> hits = hitsOn(session, user);
            helper.assertTrue(!hits.isEmpty(), "Setup: in " + session.record().foresight() + " ticks the zombie never hit the phantom of the user");
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, hits.get(0));
            fixture.power.setStamina(fixture.power.getMaxStamina());
            health[0] = user.getHealth();
            helper.assertTrue(!KingCrimsonEraseFixture.erasing(user) && EpitaphPredictions.isForcedWalking(user),
                    "Setup: the user erases already, or is not walked");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < at[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(KingCrimsonEraseFixture.erasing(user) && user.getHealth() == health[0], "the foreseen hit of tick " + at[0]
                    + " did not set off the reflex: he " + (KingCrimsonEraseFixture.erasing(user) ? "erases" : "does not erase")
                    + ", health " + health[0] + " -> " + user.getHealth());
            fixture.rule(session.userVoid() && !EpitaphPredictions.isForcedWalking(user), "in the tick the foreseen hit set off his erase the part"
                    + " of the user was not void yet: void " + session.userVoid() + ", walked " + EpitaphPredictions.isForcedWalking(user));
            fixture.rule(!session.ended(), "the erase of the user ended his prediction as " + session.endedAs());
            KingCrimsonTemporalStateService.terminate(user);
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The reflex sees the foreseen hit");
        }));
    }


    // C5
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_user_void")
    public static void eraseVoidsOnlyTheUsersPart(GameTestHelper helper) {
        voidedPart(helper, "kc-ep-void", false);
    }

    // The other answer to question 4 of the owner, behind its setting.
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_user_void_go")
    public static void withTheSettingACreatureIsLetGoAtABlowThatNoLongerLands(GameTestHelper helper) {
        voidedPart(helper, "kc-ep-void-go", true);
    }

    private static boolean onItsTrack(EpitaphSession session, Entity body) {
        EntityTrack track = session.record().track(body.getId());
        return EpitaphSessions.isOnTrack(body) && track != null
                && body.position().distanceTo(KingCrimsonEpitaphFixture.on(track, session.clock().body())) < 1.0E-6D;
    }

    private static void voidedPart(GameTestHelper helper, String name, boolean letGo) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, name, 4, 24, 10);
        fixture.budget(0);
        withoutReflex(fixture);
        boolean was = KingCrimsonConfig.EPITAPH_RELEASE_ON_VOID_BLOW.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_RELEASE_ON_VOID_BLOW.set(was));
        KingCrimsonConfig.EPITAPH_RELEASE_ON_VOID_BLOW.set(letGo);
        ServerPlayer user = fixture.user;
        // One blow of his kills the near one; the far one cannot be stopped, and reaches him.
        Zombie weak = fixture.hunter(EntityType.ZOMBIE, 8.5D, 2.5D);
        weak.setHealth(1.0F);
        Zombie strong = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 13.5D, 5.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        // The ticks of the future: his erase, the death of the weak one, the next hit on him, his next blow on the strong one.
        int[] erase = new int[1];
        int[] death = new int[1];
        int[] hit = new int[1];
        int[] blow = new int[1];
        int[] since = { -1 };
        int[] struck = new int[1];
        float[] health = new float[1];
        fixture.listen(LivingIncomingDamageEvent.class, event -> {
            // A hit of the record, as it is carried out: what a creature that was let go does by itself is another matter.
            if (since[0] >= 0 && event.getEntity() == user && rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox.replaying()) {
                struck[0]++;
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            ActorKey own = ActorKey.of(user);
            ActorKey near = ActorKey.of(weak);
            ActorKey far = ActorKey.of(strong);
            death[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Death.class).stream()
                    .filter(died -> died.victim().equals(near) && died.actor().equals(own))
                    .mapToInt(died -> KingCrimsonEpitaphFixture.tickOf(session, died)).findFirst().orElse(-1);
            erase[0] = death[0] - 4;
            helper.assertTrue(erase[0] >= 2, "Setup: the phantom of the user did not kill the zombie that one blow kills, or too soon: at tick "
                    + death[0]);
            hit[0] = hitsOn(session, user).stream().mapToInt(landed -> KingCrimsonEpitaphFixture.tickOf(session, landed))
                    .filter(tick -> tick > erase[0] + 8).findFirst().orElse(-1);
            blow[0] = blowsOf(session, user).stream().filter(landed -> landed.victim().equals(far))
                    .mapToInt(landed -> KingCrimsonEpitaphFixture.tickOf(session, landed)).filter(tick -> tick > erase[0]).findFirst().orElse(-1);
            helper.assertTrue(hit[0] > 0 && blow[0] > 0, "Setup: after tick " + erase[0] + " the zombie that cannot be stopped never hit the phantom"
                    + " of the user (tick " + hit[0] + "), or his phantom never struck it (tick " + blow[0] + ")");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (since[0] < 0 && session.passed() >= erase[0]) {
                health[0] = user.getHealth();
                Vec3 stood = user.position();
                helper.assertTrue(EpitaphPredictions.isForcedWalking(user), "Setup: at tick " + erase[0] + " the user is not walked");
                helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(user, KingCrimsonTemporalState.EraseKind.REFLEX),
                        "Setup: no erase began");
                since[0] = 0;
                fixture.rule(session.userVoid() && !EpitaphPredictions.isForcedWalking(user) && user.position().distanceTo(stood) < 1.0E-9D,
                        "as his erase began the user was not let go where he stood: void " + session.userVoid() + ", walked "
                                + EpitaphPredictions.isForcedWalking(user) + ", moved " + user.position().distanceTo(stood));
            } else if (since[0] >= 0 && ++since[0] == 6) {
                // The erase is over long before the hit that was foreseen for him.
                KingCrimsonTemporalStateService.terminate(user);
            }
            if (!session.ended()) {
                boolean weakFree = session.passed() >= death[0];
                fixture.rule(weakFree ? weak.isAlive() && !EpitaphSessions.isOnTrack(weak) : onItsTrack(session, weak), weakFree
                        ? "past tick " + death[0] + ", where his phantom would have killed it, the zombie is dead or still held"
                        : "at tick " + session.clock().body() + " the zombie his phantom would kill at " + death[0] + " is off its track");
                if (weakFree) {
                    // Let go, it would come at him by itself: that is not what is looked at here.
                    weak.setNoAi(true);
                }
                boolean strongFree = letGo && session.passed() >= blow[0];
                fixture.rule(strongFree ? strong.isAlive() && !EpitaphSessions.isOnTrack(strong) : onItsTrack(session, strong), strongFree
                        ? "with the setting, past his blow of tick " + blow[0] + " that no longer lands the zombie is still held, or dead"
                        : "at tick " + session.clock().body() + " the zombie that hunts him is off its track");
                if (!strongFree) {
                    return;
                }
                session.end(EpitaphEnd.CANCELLED);
            }
            running[0] = null;
            fixture.rule(letGo || session.endedAs() == EpitaphEnd.COMPLETE, "his prediction ended as " + session.endedAs());
            fixture.rule(letGo || session.passed() >= hit[0] && struck[0] == 0 && user.getHealth() == health[0], "a hit foreseen for tick " + hit[0]
                    + ", after his erase had begun and ended: " + struck[0] + " hits of the record landed on him, health " + health[0] + " -> "
                    + user.getHealth() + ", carried out to tick " + session.passed());
            fixture.rule(session.record().skipped() > 0, "Setup: no op of the record was left out");
            fixture.finish(letGo ? "With the setting a creature is let go at a blow that no longer lands" : "An erase voids only the part of the user");
        }));
    }


    // T8 of section 4: the step of Time Erase takes the walked user off his track for good; a setting refuses it instead.
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_user_step")
    public static void theStepFreesTheUserFromHisWalk(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-step", 4, 24, 6);
        fixture.budget(0);
        withoutReflex(fixture);
        boolean frees = KingCrimsonConfig.EPITAPH_STEP_FREES_USER.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_STEP_FREES_USER.set(frees));
        // A short step: it lands on the floor of the test.
        int reach = KingCrimsonConfig.TIME_ERASE_STEP_MAX_DISTANCE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.TIME_ERASE_STEP_MAX_DISTANCE.set(reach));
        KingCrimsonConfig.TIME_ERASE_STEP_MAX_DISTANCE.set(6);
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 20.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        Vec3[] landed = new Vec3[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.press(null);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
        }));
        helper.runAfterDelay(PRESS_AT + 7, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(!session.ended() && EpitaphPredictions.isForcedWalking(user), "Setup: the user is not walked");
            Vec3 stood = user.position();
            // Set to refuse it: the key of the step does nothing while he is walked.
            KingCrimsonConfig.EPITAPH_STEP_FREES_USER.set(false);
            boolean went = fixture.base.press(user, "kingcrimson_timeerase_step", STEP_KEY, InputMethod.CLICK, false).isActionSuccess;
            fixture.rule(!went && EpitaphPredictions.isForcedWalking(user) && user.position().distanceTo(stood) < 1.0E-9D,
                    "set not to free him, the step of the walked user went off (" + went + ") or moved him "
                            + user.position().distanceTo(stood) + " blocks");
            // As it is set at first: the step goes off, and he is his own from there.
            KingCrimsonConfig.EPITAPH_STEP_FREES_USER.set(true);
            went = fixture.base.press(user, "kingcrimson_timeerase_step", STEP_KEY, InputMethod.CLICK, false).isActionSuccess;
            landed[0] = user.position();
            fixture.rule(went && landed[0].distanceTo(stood) > 1.0D, "the step of the walked user did not go off (" + went
                    + "), or took him only " + landed[0].distanceTo(stood) + " blocks");
            fixture.rule(session.userVoid() && !EpitaphPredictions.isForcedWalking(user) && !session.ended(), "after his step the user is"
                    + " still walked, or his prediction ended as " + session.endedAs());
        }));
        helper.runAfterDelay(PRESS_AT + 12, () -> fixture.guarded(() -> {
            double back = user.position().distanceTo(landed[0]);
            fixture.rule(back < 1.0E-6D, "five ticks after his step the user is " + back + " blocks from where he landed: his track took him back");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("The step frees the user from his walk");
        }));
    }

    // 3.4: a walked user uses no ability but the erase that frees him, and picks nothing up.
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_user_hands")
    public static void aWalkedUserUsesNoAbilityAndPicksNothingUp(GameTestHelper helper) {
        // With one player online the Stand of the walked user acts a quarter as fast: the wind-up of his erase takes its time.
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-hands", 4, 24, 6);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 20.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        ItemEntity[] lying = new ItemEntity[1];
        int[] held = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            running[0] = fixture.press(null);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
        }));
        helper.runAfterDelay(PRESS_AT + 5, () -> fixture.guarded(() -> {
            helper.assertTrue(EpitaphPredictions.isForcedWalking(user), "Setup: the user is not walked");
            boolean went = fixture.base.press(user, "heavy_punch", KingCrimsonEraseFixture.OTHER_KEY, InputMethod.CLICK, false).isActionSuccess;
            fixture.rule(!went && LivingComponentAction.getCurEntityAction(power.getSummonedStandEntity()) == null,
                    "the Stand of the walked user began a heavy punch at his key");
            ItemEntity diamond = new ItemEntity(fixture.level, user.getX(), user.getY(), user.getZ(), new ItemStack(Items.DIAMOND));
            diamond.setNoPickUpDelay();
            lying[0] = fixture.thing(diamond);
        }));
        helper.runAfterDelay(PRESS_AT + 9, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            helper.assertTrue(!session.ended() && EpitaphPredictions.isForcedWalking(user) && user.distanceTo(lying[0]) < 1.5F,
                    "Setup: the user is not walked any more, or has left the diamond behind");
            fixture.rule(lying[0].isAlive() && !user.getInventory().contains(new ItemStack(Items.DIAMOND)),
                    "the walked user picked up a diamond that lay at his feet");
            // The erase is his way out: its key is not refused, and the Stand winds up.
            fixture.rule(fixture.base.holdErase(user).isActionSuccess, "the key of Time Erase was refused to the walked user");
            held[0] = 1;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (held[0] == 0 || !KingCrimsonEraseFixture.erasing(user) && ++held[0] < 120 && !session.ended()) {
                return;
            }
            held[0] = 0;
            fixture.rule(KingCrimsonEraseFixture.erasing(user) && session.userVoid() && !EpitaphPredictions.isForcedWalking(user)
                    && !session.ended(), "the key of Time Erase did not free the walked user: erasing " + KingCrimsonEraseFixture.erasing(user)
                            + ", void " + session.userVoid() + ", walked " + EpitaphPredictions.isForcedWalking(user));
            fixture.base.release(user, KingCrimsonEraseFixture.ERASE_KEY);
            KingCrimsonTemporalStateService.terminate(user);
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A walked user uses no ability but the erase and picks nothing up");
        }));
    }


    // Question 2 of the owner, the other answer, behind its setting: the user keeps control.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_free")
    public static void aUserWhoKeepsControlIsNotWalked(GameTestHelper helper) {
        // Resolve 4: a zombie made a moment ago begins its attack a second late, and its phantom with it (the warm start).
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-free", 4, 16, 6);
        fixture.budget(0);
        withoutReflex(fixture);
        fixture.userKeepsControl();
        ServerPlayer user = fixture.user;
        fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 5.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        int[] at = new int[1];
        float[] health = new float[1];
        Vec3[] stood = new Vec3[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            health[0] = user.getHealth();
            stood[0] = user.position();
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            List<RecordedOp.Damage> hits = hitsOn(session, user);
            helper.assertTrue(!hits.isEmpty(), "Setup: the zombie never hit the phantom of a user who stands still");
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, hits.get(0));
            fixture.rule(session.userVoid() && !EpitaphPredictions.isForcedWalking(user), "set to keep control, the user is walked");
            fixture.rule(blowsOf(session, user).isEmpty(), "the phantom of a user who keeps control struck " + blowsOf(session, user).size()
                    + " times: it is nobody to be led by, and has no mind");
            fixture.rule(session.room().info(ActorKey.of(user)) != null
                    && !session.room().shows(session.room().info(ActorKey.of(user)).entity),
                    "the phantom of a user who keeps control is shown to him");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < at[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(user.getHealth() == health[0] && user.position().distanceTo(stood[0]) < 1.0E-9D && session.record().skipped() > 0,
                    "past the hit foreseen for tick " + at[0] + " the user who keeps control has " + user.getHealth() + " of " + health[0]
                            + " health and stands " + user.position().distanceTo(stood[0]) + " blocks from where he stood; "
                            + session.record().skipped() + " ops were left out");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A user who keeps control is not walked");
        }));
    }

    // The ruling after stage G, by the rider rule of the owner (2026-11-11): a user who rides at the press is one with what he
    // rides. Both are foreseen, his phantom sits on the phantom of his mount, and he is walked on the mount as it is carried.
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_user_rides")
    public static void aUserWhoRidesIsForeseenAndWalkedWithHisMount(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-rides", 4, 24, 8);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        Horse mount = fixture.mob(EntityType.HORSE, 2.5D, 2.5D);
        mount.setTamed(true);
        mount.equipSaddle(new ItemStack(Items.SADDLE), null);
        mount.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2D);
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 14.5D, 2.5D));
        String title = "A user who rides is foreseen and walked with his mount";
        EpitaphSession[] running = new EpitaphSession[1];
        int[] seen = new int[1];
        helper.runAfterDelay(2, () -> fixture.guarded(
                () -> helper.assertTrue(user.startRiding(mount, true), "Setup: the user did not mount the horse")));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(user.getVehicle() == mount && mount.getControllingPassenger() == user, "Setup: at the press the user"
                    + " does not ride the horse, or it does not answer to him");
            EpitaphSession session = fixture.press(null);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            EntityTrack rider = session.record().track(user.getId());
            EntityTrack ridden = session.record().track(mount.getId());
            fixture.rule(rider != null && ridden != null && !session.userVoid(), "a user who rides at the press has "
                    + (rider != null ? "a" : "no") + " track and what he rides has " + (ridden != null ? "a" : "no") + " track; his part is "
                    + (session.userVoid() ? "void" : "not void"));
            fixture.rule(user.getVehicle() == mount && EpitaphPredictions.isForcedWalking(user) && EpitaphSessions.isOnTrack(mount),
                    "after the press the user " + (user.getVehicle() == mount ? "rides on" : "is off his mount") + " and is "
                            + (EpitaphPredictions.isForcedWalking(user) ? "walked" : "not walked") + ", and his mount is "
                            + (EpitaphSessions.isOnTrack(mount) ? "carried" : "free"));
            fixture.rule(EpitaphSessions.isOnTrack(zombie), "the zombie that hunts a user who rides is not foreseen");
            if (rider == null || ridden == null) {
                session.end(EpitaphEnd.CANCELLED);
                fixture.finish(title);
                return;
            }
            // In the room he sits on it for as long as both are there.
            int both = Math.min(15, Math.min(rider.lastTick(), ridden.lastTick()));
            double apart = 0.0D;
            for (int tick = 0; tick <= both; tick++) {
                Vec3 seat = rider.frame(tick).position().subtract(ridden.frame(tick).position());
                apart = Math.max(apart, Math.sqrt(seat.x * seat.x + seat.z * seat.z));
            }
            fixture.rule(both >= 10 && apart < 0.3D, "in the first " + both + " ticks of the future the phantom of the user was up to "
                    + apart + " blocks beside the phantom of his mount: he does not sit on it");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.ended()) {
                return;
            }
            // For real: the horse is carried along its track, and carries him.
            double tick = session.clock().body();
            double his = user.position().distanceTo(KingCrimsonEpitaphFixture.on(session.record().track(user.getId()), tick));
            double its = mount.position().distanceTo(KingCrimsonEpitaphFixture.on(session.record().track(mount.getId()), tick));
            fixture.rule(user.getVehicle() == mount && his < 0.05D && its < 1.0E-6D, "at tick " + tick + " of their tracks the user "
                    + (user.getVehicle() == mount ? "rides" : "does not ride") + " his horse, " + his + " blocks from where his"
                    + " phantom was then, and the horse stands " + its + " blocks from where its phantom was");
            if (++seen[0] == 10) {
                running[0] = null;
                session.end(EpitaphEnd.CANCELLED);
                fixture.finish(title);
            }
        }));
    }

    // A user who keeps control keeps his seat and his mount: neither is foreseen, and nothing holds the mount.
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_user_rides_free")
    public static void aUserWhoRidesAndKeepsControlIsNotForeseen(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-rides-free", 2, 16, 6);
        fixture.userKeepsControl();
        aRidingUserIsLeftOut(fixture, "A user who rides and keeps control is not foreseen");
    }

    // What he rides cannot be foreseen (the setting names its type): he is not foreseen either, as before the ruling.
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_user_rides_denied")
    public static void aUserWhoRidesWhatCannotBeForeseenIsNotForeseen(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-rides-denied", 2, 16, 6);
        String denied = KingCrimsonConfig.EPITAPH_DENIED_ENTITIES.get();
        fixture.base.onClose(() -> KingCrimsonConfig.EPITAPH_DENIED_ENTITIES.set(denied));
        KingCrimsonConfig.EPITAPH_DENIED_ENTITIES.set(denied.isBlank() ? "minecraft:pig" : denied + ",minecraft:pig");
        aRidingUserIsLeftOut(fixture, "A user who rides what cannot be foreseen is not foreseen");
    }

    private static void aRidingUserIsLeftOut(KingCrimsonEpitaphFixture fixture, String title) {
        GameTestHelper helper = fixture.helper;
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        Pig mount = fixture.mob(EntityType.PIG, 2.5D, 2.5D);
        mount.setNoAi(true);
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 9.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        float[] health = new float[1];
        helper.runAfterDelay(2, () -> fixture.guarded(
                () -> helper.assertTrue(user.startRiding(mount, true), "Setup: the user did not mount the pig")));
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(user.getVehicle() == mount, "Setup: at the press the user does not ride");
            health[0] = user.getHealth();
            EpitaphSession session = fixture.press(null);
            fixture.rule(session.room().phantom(user.getId()) == null && session.room().phantom(mount.getId()) == null,
                    "the user was given " + (session.room().phantom(user.getId()) == null ? "no" : "a") + " phantom and what he rides "
                            + (session.room().phantom(mount.getId()) == null ? "none" : "one"));
            fixture.rule(session.userVoid() && !EpitaphPredictions.isForcedWalking(user) && !EpitaphSessions.isOnTrack(mount),
                    "the user is walked, or his part is not void, or his mount is held: void " + session.userVoid() + ", walked "
                            + EpitaphPredictions.isForcedWalking(user) + ", the mount on a track " + EpitaphSessions.isOnTrack(mount));
            fixture.rule(EpitaphSessions.isOnTrack(zombie), "the zombie that hunts a user who rides is not foreseen");
            running[0] = session;
        }));
        helper.runAfterDelay(PRESS_AT + 12, () -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            fixture.rule(!session.ended() && EpitaphSessions.isOnTrack(zombie), "twelve ticks after the press his prediction "
                    + (session.ended() ? "ended as " + session.endedAs() : "goes on") + " and the zombie is "
                    + (EpitaphSessions.isOnTrack(zombie) ? "on its track" : "off its track"));
            fixture.rule(user.getVehicle() == mount && !EpitaphPredictions.isForcedWalking(user) && user.getHealth() == health[0],
                    "twelve ticks after the press the user who rode " + (user.getVehicle() == mount ? "still rides" : "is off his mount")
                            + (EpitaphPredictions.isForcedWalking(user) ? ", is walked" : "") + ", health " + health[0] + " -> " + user.getHealth());
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish(title);
        }));
    }

    // 3.1, the dodges of the mind. A mind that dodges wrongly walks the real user wrongly: each is seen in the room and for real.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_tnt")
    public static void userPhantomRunsFromPrimedTnt(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-tnt", 4, 24, 12);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.Explode[] foreseen = new RecordedOp.Explode[1];
        PrimedTnt[] lit = new PrimedTnt[1];
        int[] at = new int[1];
        float[] health = new float[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // Between him and the edge of the floor: the way out is across the floor.
            Vec3 beside = fixture.spot(0.5D, 2.0D, 2.5D);
            PrimedTnt tnt = fixture.thing(new PrimedTnt(fixture.level, beside.x, beside.y, beside.z, null));
            tnt.setFuse(60);
            tnt.setDeltaMovement(Vec3.ZERO);
            lit[0] = tnt;
            health[0] = user.getHealth();
            // Under the crosshair: the one thing a press foresees whatever it is.
            EpitaphSession session = fixture.press(tnt);
            session.foreseeAll();
            List<RecordedOp.Explode> blasts = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Explode.class);
            helper.assertTrue(!blasts.isEmpty(), "Setup: the primed TNT did not go off in the room");
            RecordedOp.Explode blast = blasts.get(0);
            foreseen[0] = blast;
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, blast);
            EntityTrack own = session.record().track(user.getId());
            Vec3 centre = new Vec3(blast.x(), blast.y(), blast.z());
            double away = KingCrimsonEpitaphFixture.on(own, at[0]).distanceTo(centre);
            fixture.rule(away > 2.0D * blast.radius(), "at the blast of tick " + at[0] + " the phantom of the user stood " + away
                    + " blocks from the TNT: a blast of power " + blast.radius() + " harms within " + 2.0D * blast.radius());
            long burnt = hitsOn(session, user).stream().filter(hit -> hit.source().is(DamageTypeTags.IS_EXPLOSION)).count();
            fixture.rule(burnt == 0L, "the phantom of the user was caught by the blast of the TNT " + burnt + " times");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < at[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            Vec3 centre = new Vec3(foreseen[0].x(), foreseen[0].y(), foreseen[0].z());
            double away = user.position().distanceTo(centre);
            fixture.rule(session.passed() >= at[0] && lit[0].isRemoved() && away > 2.0D * foreseen[0].radius() && user.getHealth() == health[0],
                    "when the real blast came (tick " + session.passed() + " of " + at[0] + ", the TNT " + (lit[0].isRemoved() ? "gone" : "still there")
                            + ") the user stood " + away + " blocks from it with " + user.getHealth() + " of " + health[0] + " health");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The phantom of the user runs out of the blast of primed TNT");
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_anvil")
    public static void userPhantomStepsFromUnderAFallingBlock(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-under", 4, 16, 8);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerLevel level = fixture.level;
        ServerPlayer user = fixture.user;
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.SetBlock[] landing = new RecordedOp.SetBlock[1];
        int[] at = new int[1];
        float[] health = new float[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // Straight above him, and it hurts what it lands on, as an anvil that fell by itself does.
            FallingBlockEntity anvil = fixture.base.track(FallingBlockEntity.fall(level, user.blockPosition().above(14),
                    Blocks.ANVIL.defaultBlockState()));
            anvil.setHurtsEntities(2.0F, 40);
            health[0] = user.getHealth();
            EpitaphSession session = fixture.press(anvil);
            session.foreseeAll();
            landing[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.SetBlock.class).stream()
                    .filter(set -> set.state().getBlock() instanceof AnvilBlock).findFirst().orElse(null);
            helper.assertTrue(landing[0] != null, "Setup: the anvil did not land in the room");
            at[0] = KingCrimsonEpitaphFixture.tickOf(session, landing[0]);
            helper.assertTrue(landing[0].pos().getX() == user.getBlockX() && landing[0].pos().getZ() == user.getBlockZ(),
                    "Setup: the anvil did not come down where the user stood, but at " + landing[0].pos());
            EntityTrack own = session.record().track(user.getId());
            double aside = beside(KingCrimsonEpitaphFixture.on(own, at[0]), landing[0].pos());
            fixture.rule(aside > 0.8D, "when the anvil landed at tick " + at[0] + " the phantom of the user stood " + aside
                    + " blocks from the middle of its column: under it");
            long struck = hitsOn(session, user).stream().filter(hit -> hit.source().is(DamageTypes.FALLING_ANVIL)).count();
            fixture.rule(struck == 0L, "the phantom of the user was struck by the anvil " + struck + " times");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < at[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            double aside = beside(user.position(), landing[0].pos());
            fixture.rule(session.passed() >= at[0] && level.getBlockState(landing[0].pos()).getBlock() instanceof AnvilBlock && aside > 0.8D
                    && user.getHealth() == health[0], "when the real anvil landed (tick " + session.passed() + " of " + at[0] + ", its place holds "
                            + level.getBlockState(landing[0].pos()) + ") the user stood " + aside + " blocks from the middle of its column with "
                            + user.getHealth() + " of " + health[0] + " health");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The phantom of the user steps from under a falling block");
        }));
    }

    // How far a place is, on the flat, from the middle of the column of a block.
    private static double beside(Vec3 place, BlockPos column) {
        double dx = place.x - (column.getX() + 0.5D);
        double dz = place.z - (column.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_shot")
    public static void userPhantomStepsOutOfTheFlightOfAShot(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-shot", 4, 24, 8);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerLevel level = fixture.level;
        ServerPlayer user = fixture.user;
        EpitaphSession[] running = new EpitaphSession[1];
        int[] at = new int[1];
        float[] health = new float[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // Lobbed at him from fourteen blocks, as a skeleton lobs its arrow from afar: it comes down on him.
            Vec3 from = fixture.spot(16.5D, 3.3D, 2.5D);
            Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            double dx = user.getX() - from.x;
            double dy = user.getY(0.3333D) - from.y;
            double dz = user.getZ() - from.z;
            arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2D, dz, 1.6F, 0.0F);
            fixture.thing(arrow);
            health[0] = user.getHealth();
            Vec3 stood = user.position();
            EpitaphSession session = fixture.press(arrow);
            session.foreseeAll();
            EntityTrack flight = session.record().track(arrow.getId());
            EntityTrack own = session.record().track(user.getId());
            helper.assertTrue(flight != null && own != null, "Setup: the arrow under the crosshair or the user is not foreseen");
            // Where he stood at the press the arrow came through: it was coming at him.
            double nearest = Double.MAX_VALUE;
            for (int tick = flight.firstTick(); tick < flight.lastTick(); tick++) {
                Vec3 a = flight.frame(tick).position();
                Vec3 b = flight.frame(tick + 1).position();
                for (int part = 0; part <= 8; part++) {
                    Vec3 on = a.lerp(b, part / 8.0D);
                    if (on.y >= stood.y - 0.3D && on.y <= stood.y + 2.1D) {
                        nearest = Math.min(nearest, Math.sqrt((on.x - stood.x) * (on.x - stood.x) + (on.z - stood.z) * (on.z - stood.z)));
                    }
                }
            }
            helper.assertTrue(nearest < 0.55D, "Setup: the arrow passed " + nearest + " blocks from where the user stood at the press: it was"
                    + " not coming at him");
            at[0] = flight.lastTick();
            long struck = hitsOn(session, user).stream().filter(hit -> hit.source().is(DamageTypeTags.IS_PROJECTILE)).count();
            fixture.rule(struck == 0L, "the phantom of the user was struck by the arrow " + struck + " times");
            double aside = Math.abs(KingCrimsonEpitaphFixture.on(own, own.lastTick()).z - stood.z);
            fixture.rule(aside > 0.5D, "the phantom of the user ended " + aside + " blocks to the side of where he stood: it did not step out");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || session.passed() < at[0] && !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(session.passed() >= at[0] && user.getHealth() == health[0], "when the real arrow had flown (tick " + session.passed()
                    + " of " + at[0] + ") the user has " + user.getHealth() + " of " + health[0] + " health");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The phantom of the user steps out of the flight of a shot");
        }));
    }

    // Ruling R5: in the slow phase the being of the walked user lives at the pace of his track, and what his own state
    // does to him his own tick does, once.
    @GameTest(template = "empty", timeoutTicks = 300, batch = "kc_epitaph_user_pace")
    public static void theWalkedUserLivesAtHisTracksPace(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-pace", 4, 16, 8);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        // Under the crosshair and after nobody: his phantom has nothing to fight, and only his poison harms him.
        Cow cow = fixture.mob(EntityType.COW, 8.5D, 2.5D);
        cow.setNoAi(true);
        EpitaphSession[] running = new EpitaphSession[1];
        int[] sentBefore = new int[1];
        int[] phase = new int[1];
        boolean[] phaseSeen = new boolean[1];
        List<Integer> harmedAt = new ArrayList<>();
        fixture.listen(LivingDamageEvent.Post.class, event -> {
            if (running[0] != null && event.getEntity() == user) {
                harmedAt.add(running[0].passed());
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // Poison of the second strength harms every twelfth tick.
            user.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
            sentBefore[0] = KingCrimsonEraseFixture.listener(user).sentCount();
            EpitaphSession session = fixture.press(cow);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            helper.assertTrue(session.mode() == EpitaphMode.SOLO_SLOW && EpitaphPredictions.isForcedWalking(user),
                    "Setup: the user is not walked, or others are online");
            phase[0] = session.clock().phase() / 4;
            long inPhase = poisonTicks(session, user, phase[0]).size();
            helper.assertTrue(inPhase >= 1L && poisonTicks(session, user, 40).size() >= 3, "Setup: the poison harmed the phantom of the user "
                    + inPhase + " times in the " + phase[0] + " ticks of the slow phase and " + poisonTicks(session, user, 40).size() + " times in 40");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            if (!phaseSeen[0] && session.passed() >= phase[0]) {
                phaseSeen[0] = true;
                List<Integer> foreseen = poisonTicks(session, user, phase[0]);
                fixture.rule(harmedAt.equals(foreseen), "in the slow phase the poison harmed the phantom of the user at the ticks " + foreseen
                        + " of his track and the user himself while his track stood at " + harmedAt);
            }
            if (session.passed() < 40 && !session.ended()) {
                return;
            }
            running[0] = null;
            List<Integer> foreseen = poisonTicks(session, user, 40);
            fixture.rule(harmedAt.equals(foreseen), "in 40 ticks the poison harmed the phantom of the user at the ticks " + foreseen
                    + " of his track and the user himself while his track stood at " + harmedAt);
            float left = hitsOn(session, user).stream().filter(hit -> KingCrimsonEpitaphFixture.tickOf(session, hit) <= 40)
                    .reduce((first, last) -> last).map(RecordedOp.Damage::healthAfter).orElse(-1.0F);
            fixture.rule(KingCrimsonGroundGameTestFixture.near(user.getHealth(), left), "after 40 ticks of his track the user has "
                    + user.getHealth() + " health, his phantom had " + left);
            // His client counted his poison down at its own pace through the slow phase: it is told once what is left.
            long told = KingCrimsonEpitaphFixture.flat(KingCrimsonEraseFixture.listener(user).sentSince(sentBefore[0])).stream()
                    .filter(packet -> packet instanceof ClientboundUpdateMobEffectPacket effect && effect.getEntityId() == user.getId()).count();
            fixture.rule(told == 1L, "since the press the user was told " + told + " times how long his poison lasts, not once as the slow phase ended");
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("The walked user lives at the pace of his track");
        }));
    }

    // The ticks of the future, up to one, at which the poison of the user harmed his phantom.
    private static List<Integer> poisonTicks(EpitaphSession session, ServerPlayer user, int upTo) {
        List<Integer> ticks = new ArrayList<>();
        for (RecordedOp.Damage hit : hitsOn(session, user)) {
            int tick = KingCrimsonEpitaphFixture.tickOf(session, hit);
            if (tick <= upTo && hit.source().is(NeoForgeMod.POISON_DAMAGE)) {
                ticks.add(tick);
            }
        }
        return ticks;
    }

    // Ruling R5: from the press of his way out the time of the walked user is his own: his erase winds up as it always does.
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_user_way_out")
    public static void theWayOutWindsUpAtFullSpeedInTheSlowPhase(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-way-out", 4, 16, 8);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerLevel level = fixture.level;
        ServerPlayer user = fixture.user;
        StandPower power = fixture.power;
        Cow cow = fixture.mob(EntityType.COW, 8.5D, 2.5D);
        cow.setNoAi(true);
        // 0: his own erase is timed; 1: it is over, the press of Epitaph comes; 2: walked, he holds the key; 3: timed again.
        int[] step = new int[1];
        long[] since = new long[1];
        long[] usual = new long[1];
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(fixture.base.holdErase(user).isActionSuccess, "Setup: the key of Time Erase was refused to a free user");
            since[0] = level.getGameTime();
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            long now = level.getGameTime();
            if (since[0] == 0L) {
                return;
            }
            if (step[0] == 0 && KingCrimsonEraseFixture.erasing(user)) {
                usual[0] = now - since[0];
                fixture.base.release(user, KingCrimsonEraseFixture.ERASE_KEY);
                KingCrimsonTemporalStateService.terminate(user);
                power.resetAbilityCooldowns();
                power.setStamina(power.getMaxStamina());
                since[0] = now;
                step[0] = 1;
            } else if (step[0] == 0 && now - since[0] > 200L) {
                helper.assertTrue(false, "Setup: a free user who held the key of Time Erase did not erase in 200 ticks");
            } else if (step[0] == 1 && now - since[0] == 10L) {
                helper.assertTrue(!KingCrimsonEraseFixture.erasing(user), "Setup: the erase that was timed is not over");
                running[0] = fixture.press(cow);
                KingCrimsonEpitaphFixture.acceptTeleport(user);
                since[0] = now;
                step[0] = 2;
            } else if (step[0] == 2 && now - since[0] >= 4L && EpitaphSessions.holdsBeing(user)) {
                // A server tick of the slow phase on which his track passed no whole tick: the tick of his being is held.
                EpitaphSession session = running[0];
                helper.assertTrue(session.mode() == EpitaphMode.SOLO_SLOW && session.clock().inPhase() && EpitaphPredictions.isForcedWalking(user),
                        "Setup: four ticks after the press the user is not walked in the slow phase");
                fixture.rule(fixture.base.holdErase(user).isActionSuccess, "the key of Time Erase was refused to the walked user");
                fixture.rule(session.ownTime(), "the way out was pressed and let through, and the time of the user is not his own");
                fixture.rule(!EpitaphSessions.holdsBeing(user), "his way out is pressed, and the tick of his being is still held to his track");
                since[0] = now;
                step[0] = 3;
            } else if (step[0] == 3 && (KingCrimsonEraseFixture.erasing(user) || now - since[0] > 4L * usual[0] + 40L)) {
                EpitaphSession session = running[0];
                long took = now - since[0];
                fixture.rule(KingCrimsonEraseFixture.erasing(user) && took <= usual[0] + 2L, "a free user erased " + usual[0]
                        + " ticks after he held the key; walked, in the slow phase, " + (KingCrimsonEraseFixture.erasing(user) ? "he erased after "
                                + took : "he had not erased after " + took) + " ticks");
                fixture.rule(session.userVoid() && !EpitaphPredictions.isForcedWalking(user), "erasing, the user is still walked");
                step[0] = 4;
                fixture.base.release(user, KingCrimsonEraseFixture.ERASE_KEY);
                KingCrimsonTemporalStateService.terminate(user);
                session.end(EpitaphEnd.CANCELLED);
                fixture.finish("The way out winds up at full speed in the slow phase");
            }
        }));
    }

    // The core ends a sprint in a stun as the tick of a player starts: for a phantom that is after its mind has steered it
    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_epitaph_user_stun")
    public static void aStunnedPhantomDoesNotSprint(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-stun", 4, 24, 12);
        fixture.budget(0);
        withoutReflex(fixture);
        ServerPlayer user = fixture.user;
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // Something to run from: the mind of his phantom wants to sprint, stunned or not.
            Vec3 beside = fixture.spot(0.5D, 2.0D, 2.5D);
            PrimedTnt tnt = fixture.thing(new PrimedTnt(fixture.level, beside.x, beside.y, beside.z, null));
            tnt.setFuse(60);
            tnt.setDeltaMovement(Vec3.ZERO);
            int[] sprinted = new int[2];
            int leaks = EpitaphSandbox.leaks();
            for (int round = 0; round < 2; round++) {
                if (round == 1) {
                    user.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200, 0));
                    helper.assertTrue(ModStatusEffects.isStunned(user) && ModStatusEffects.isStunned(fixture.power.getSummonedStandEntity()),
                            "Setup: the user and his Stand could not be stunned");
                }
                EpitaphSession session = fixture.press(tnt);
                helper.assertTrue(session.room().phantom(user.getId()) instanceof ServerPlayer, "Setup: the user has no phantom");
                ServerPlayer phantom = (ServerPlayer) session.room().phantom(user.getId());
                if (round == 1) {
                    // The core gives a Stand the stun of its user as it is summoned: the Stand of the copy is summoned so.
                    StandPower copied = StandPower.get(phantom);
                    fixture.rule(copied != null && copied.getSummonedStandEntity() != null
                            && ModStatusEffects.isStunned(copied.getSummonedStandEntity()), "the Stand of a stunned user is stunned,"
                                    + " and the Stand of his phantom is not");
                }
                for (int tick = 0; tick < 30; tick++) {
                    SandboxDriver.step(session.room());
                    sprinted[round] += phantom.isSprinting() ? 1 : 0;
                }
                session.end(EpitaphEnd.CANCELLED);
            }
            helper.assertTrue(sprinted[0] >= 5, "Setup: with primed TNT beside it the phantom of the user was sprinting at the end of"
                    + " only " + sprinted[0] + " of 30 ticks of the room");
            fixture.rule(sprinted[1] == 0, "stunned, the phantom of the user was sprinting at the end of " + sprinted[1]
                    + " of 30 ticks of the room");
            fixture.rule(EpitaphSandbox.leaks() == leaks, "making the phantoms of a stunned user and his Stand counted as reaching"
                    + " past the room: " + EpitaphSandbox.firstLeak());
            fixture.finish("A stunned phantom does not sprint");
        }));
    }
}
