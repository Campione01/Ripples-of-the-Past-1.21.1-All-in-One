package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonDriftBodyEntity;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;
import rotp.addons.kingcrimson.state.KingCrimsonEraseReach;
import rotp.addons.kingcrimson.state.KingCrimsonEraseStamina;
import rotp.addons.kingcrimson.state.KingCrimsonReflexErase;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.api.entity.MobAiPause;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.gametest.GameTestChunkSending;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.gametest.GameTestTimeStops;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * The Time Erase of a creature that carries King Crimson, by the owner's rules of 2026-11-11: the creature stays in the
 * world on its own AI; a fake body that no client is told of hunts each player where the erase sees him, with one
 * player online too; the blow of the fake body ends the erase, three seconds inside at the earliest, and the creature
 * is put there and strikes; it is not dazed by its own erase; a hit that would kill it in its erase is escaped by its
 * passive erase; neither begins in stopped time or against a player another add-on names; cost and cooldown are a
 * player's. The creatures here press their key as the creature add-on presses one.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonCreatureEraseGameTests {
    private static final String ERASE = "kingcrimson_timeerase";
    // The owner: "非玩家生物發動時刪最少在裏面等三秒才能結束". And the fuse of a creeper.
    private static final int THREE_SECONDS = 60;
    private static final int FUSE = 30;

    private KingCrimsonCreatureEraseGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_cost")
    public static void aCreatureThatCarriesKingCrimsonErasesTimeForAPlayersCostAndCooldown(GameTestHelper helper) {
        Scene scene = new Scene(helper, 24, 12, "The cost and the cooldown of a creature's erase");
        Zombie creature = scene.bearer(EntityType.ZOMBIE, 6.5D, 3.5D, false);
        Zombie twin = scene.bearer(EntityType.ZOMBIE, 6.5D, 8.5D, false);
        Zombie plain = scene.creature(EntityType.ZOMBIE, 12.5D, 3.5D);
        plain.setNoAi(true);
        ServerPlayer player = scene.player("kc-ce-cost-him", 18.5D, 6.5D);
        StandPower his = scene.fixture.kingCrimson(player, 4);
        StandPower power = StandPower.get(creature);
        StandPower twins = StandPower.get(twin);
        Ability key = power.getAbility(ERASE);
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(power.getStamina() == power.getMaxStamina() && twins.getStamina() == twins.getMaxStamina()
                    && !power.isAbilityOnCooldown(ERASE), "Setup: a bar is not full, or the key waits");
            scene.rule(KingCrimsonCreatureErase.bears(creature) && !KingCrimsonCreatureErase.bears(plain)
                            && !KingCrimsonCreatureErase.bears(player) && !KingCrimsonCreatureErase.bears(his.getSummonedStandEntity()),
                    "a creature with King Crimson " + (KingCrimsonCreatureErase.bears(creature) ? "has" : "has not")
                            + " the key, a creature with no Stand " + (KingCrimsonCreatureErase.bears(plain) ? "has" : "has not"));
            // What the creature add-on draws its picks from.
            scene.rule(power.updateAvailableMoves().inMovesetAndCanBeUsed.containsKey(ERASE),
                    "the erase is not among the moves a creature with King Crimson can use");
            // What its bar is set against: one that paid the start as a player pays it, and erases nothing.
            StandAbilityStamina.consume((Ability) null, twins, KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA, false);
            scene.rule(scene.press(creature), "the key of a creature that carries King Crimson is refused");
            helper.assertTrue(scene.fixture.holdErase(player).isActionSuccess, "Setup: the erase of the player did not start");
        }));
        // The bars of the creature and of its twin, and how long the creature had erased by then.
        float[] bar = new float[2];
        int[] inside = new int[1];
        helper.runAfterDelay(14, () -> scene.fixture.guarded(() -> {
            boolean erases = KingCrimsonCreatureErase.isErasing(creature);
            scene.rule(erases, "nine ticks after its key was pressed the creature does not erase");
            bar[0] = power.getStamina();
            bar[1] = twins.getStamina();
            inside[0] = KingCrimsonCreatureErase.ticksInside(creature);
            float upkeep = power.getStaminaCostShare(
                    StandAbilityStamina.effectiveCost(power, KingCrimsonConfig.timeEraseStaminaPerTick()));
            float start = power.getStaminaCostShare(
                    StandAbilityStamina.effectiveCost(power, KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA));
            // The upkeep of a tick is taken in the next tick of the Stand; the tick it began in may have filled the bar once.
            float beyond = power.getMaxStamina() - bar[0] - start - (inside[0] - 1) * upkeep;
            scene.rule(!erases || beyond < 0.01F && beyond > -twins.getStaminaTickGain() - 0.01F, inside[0] + " ticks inside,"
                    + " the bar of the creature is " + (power.getMaxStamina() - bar[0]) + " below full: not the start of a"
                    + " player (" + start + ") and " + (inside[0] - 1) + " times the " + upkeep + " a tick that he pays");
        }));
        helper.runAfterDelay(30, () -> scene.fixture.guarded(() -> {
            boolean erases = KingCrimsonCreatureErase.isErasing(creature);
            scene.rule(erases, "twenty-five ticks after its key was pressed the creature does not erase");
            helper.assertTrue(KingCrimsonEraseFixture.erasing(player), "Setup: the player does not erase");
            if (erases) {
                int ticks = KingCrimsonCreatureErase.ticksInside(creature) - inside[0];
                float upkeep = power.getStaminaCostShare(
                        StandAbilityStamina.effectiveCost(power, KingCrimsonConfig.timeEraseStaminaPerTick()));
                float fell = bar[0] - power.getStamina();
                float regained = twins.getStamina() - bar[1];
                helper.assertTrue(regained > 5.0F, "Setup: in " + ticks + " ticks a creature that does not erase regained "
                        + regained + " of its bar");
                // As the core's time stop: its user regenerates nothing during it.
                scene.rule(ticks == 16 && Math.abs(fell - ticks * upkeep) < 0.01F, "in " + ticks + " ticks of its erase the bar"
                        + " of the creature fell " + fell + ": not " + ticks + " times the " + upkeep + " a tick that a player"
                        + " pays, with nothing regained (one that does not erase regained " + regained + ")");
            }
            KingCrimsonTemporalStateService.terminate(player);
            KingCrimsonCreatureErase.end(creature);
            int waits = power.getAbilityCooldown(ERASE);
            int hisWait = his.getAbilityCooldown(ERASE);
            scene.rule(waits > 0 && waits == hisWait, "after its erase the key of the creature waits " + waits
                    + " ticks, and the key of a player whose erase ended in the same tick " + hisWait);
            scene.rule(!key.checkConditions(power).isPositive() && !KingCrimsonCreatureErase.canBegin(creature)
                            && !KingCrimsonCreatureErase.begin(creature),
                    "while its key waits a creature can begin an erase again");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_bodies")
    public static void theCreatureStaysInTheWorldAndAFakeBodyOnNoClientHuntsThePlayerWhereTheEraseSeesHim(GameTestHelper helper) {
        Scene scene = new Scene(helper, 40, 20, "The real body and the fake body of a creature's erase");
        // A whole block for each block: the image stands clear of the player.
        KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(1.0D);
        ServerPlayer player = scene.player("kc-ce-bodies-him", 24.5D, 14.5D);
        Zombie creature = scene.sturdy(scene.bearer(EntityType.ZOMBIE, 8.5D, 10.5D, true));
        Zombie other = scene.creature(EntityType.ZOMBIE, 4.5D, 3.5D);
        other.setNoAi(true);
        Pig pig = scene.creature(EntityType.PIG, 4.5D, 16.5D);
        pig.setNoAi(true);
        creature.setTarget(player);
        Vec3 home = creature.position();
        int[] sent = new int[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(EpitaphSessions.isAlone(helper.getLevel().getServer()), "Setup: more than one player is online");
            GameTestChunkSending.assertTracks(helper, player, creature, "the creature");
            sent[0] = KingCrimsonEraseFixture.listener(player).sentCount();
            helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused");
        }));
        scene.walk(player, 10, 20, 0.0D, -0.2D);
        helper.runAfterDelay(36, () -> scene.fixture.guarded(() -> {
            Mob fake = KingCrimsonCreatureErase.fakeBodyOf(creature);
            KingCrimsonDriftBodyEntity body = KingCrimsonCreatureErase.driftBodyOf(creature, player);
            helper.assertTrue(KingCrimsonCreatureErase.isErasing(creature) && fake != null,
                    "Setup: thirty ticks after its key was pressed the creature does not erase");
            // C2: the creature itself.
            float health = creature.getHealth();
            boolean hit = creature.hurt(creature.damageSources().playerAttack(player), 4.0F);
            scene.rule(creature.isAlive() && !creature.isInvisible() && !KingCrimsonEraseReach.isOutOfReach(creature)
                            && GameTestChunkSending.tracks(player, creature) && hit && creature.getHealth() < health,
                    "in its erase the creature itself is " + (KingCrimsonEraseReach.isOutOfReach(creature) ? "out of reach" : "in reach")
                            + ", " + (GameTestChunkSending.tracks(player, creature) ? "seen" : "not seen") + " by the player, and his"
                            + " hit took " + (health - creature.getHealth()) + " of its health");
            double went = creature.position().distanceTo(home);
            scene.rule(creature.getTarget() == player && went > 1.0D
                            && creature.distanceToSqr(player) < home.distanceToSqr(player.position()),
                    "in its erase the creature went " + went + " blocks on its own AI, after "
                            + (creature.getTarget() == player ? "the player" : "somebody else or nobody"));
            // C3: its fake body.
            scene.rule(fake.getType() == creature.getType() && fake != creature && fake.isAlive()
                            && helper.getLevel().getEntity(fake.getId()) == fake
                            && (StandPower.get(fake) == null || !StandPower.get(fake).hasPower()),
                    "the fake body is no plain creature of the same kind in the level");
            float fakeHealth = fake.getHealth();
            boolean fakeHit = fake.hurt(fake.damageSources().playerAttack(player), 4.0F);
            scene.rule(KingCrimsonEraseReach.isOutOfReach(fake) && !fakeHit && fake.getHealth() == fakeHealth,
                    "the fake body is " + (KingCrimsonEraseReach.isOutOfReach(fake) ? "out of reach" : "in reach")
                            + ", and a hit of the player took " + (fakeHealth - fake.getHealth()) + " of its health");
            scene.rule(!fake.broadcastToPlayer(player) && !GameTestChunkSending.tracks(player, fake),
                    "the client of the player is told of the fake body");
            // H3: what it hunts.
            Vec3 image = KingCrimsonCreatureErase.imageOf(creature, player);
            Vec3 ahead = image.subtract(player.position());
            scene.rule(KingCrimsonEraseShiftGameTests.near(ahead, new Vec3(0.0D, 0.0D, -4.0D)), "with one player online the"
                    + " erase of a creature sees a player who walked 4 blocks north " + ahead + " from where he is, not 4 north");
            scene.rule(body != null && body.isAlive() && KingCrimsonEraseShiftGameTests.near(body.position(), image)
                            && !GameTestChunkSending.tracks(player, body) && KingCrimsonEraseReach.isOutOfReach(body),
                    "the body kept for the player is " + (body == null ? "missing"
                            : body.position().subtract(image) + " from his image, or within reach, or on his client"));
            scene.rule(body != null && fake.getTarget() == body, "the fake body hunts "
                    + (fake.getTarget() == null ? "nobody" : fake.getTarget().getType().toShortString()) + ", not the body on the image");
            if (body != null) {
                double its = fake.distanceTo(body);
                double real = creature.distanceTo(body);
                scene.rule(its < real - 2.0D, "the fake body is " + its + " blocks from the image of the player and the"
                        + " creature itself " + real + ": it does not run at the image (it went "
                        + fake.position().distanceTo(home) + " blocks in " + fake.tickCount + " ticks)");
                // In an erase its user moves at twice his pace: a zombie walks 0.114 blocks a tick, its fake body 0.228.
                double gone = fake.position().distanceTo(home);
                scene.rule(gone > 0.16D * fake.tickCount, "in " + fake.tickCount + " ticks the fake body of a zombie went "
                        + gone + " blocks: not at twice the pace of a zombie");
                // What its own hunt finds is turned on that body; nothing else is its prey, and that body is nobody else's.
                fake.setTarget(player);
                boolean turned = fake.getTarget() == body;
                fake.setTarget(pig);
                boolean onlyPlayers = fake.getTarget() == body;
                other.setTarget(body);
                scene.rule(turned && onlyPlayers && other.getTarget() == null, "a fake body that turns on the player hunts "
                        + (turned ? "his body on the image" : "him") + ", it " + (onlyPlayers ? "cannot" : "can")
                        + " turn on a pig, and another creature " + (other.getTarget() == null ? "cannot" : "can")
                        + " hunt the body on the image");
            }
            List<String> told = KingCrimsonEraseShiftGameTests.oursSince(player, sent[0]);
            scene.rule(told.isEmpty(), "before the erase of the creature ended the player was sent: " + told);
            // H2, H3: the end is the end of any erase.
            KingCrimsonCreatureErase.end(creature);
            scene.rule(KingCrimsonEraseShiftGameTests.near(player.position(), image), "at the end the player stands "
                    + player.position().subtract(image) + " from the image the erase last saw of him");
            scene.rule(KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) == 1, "the player heard the end of the erase "
                    + KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) + " times");
            scene.rule(fake.isRemoved() && (body == null || body.isRemoved()) && !KingCrimsonEraseReach.isOutOfReach(fake),
                    "after the erase its fake body or the body on the image is still in the world");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_creature_erase_strike")
    public static void theBlowOfTheFakeBodyEndsTheEraseThreeSecondsInAtTheEarliestAndTheCreatureIsPutThereAndStrikes(
            GameTestHelper helper) {
        Scene scene = new Scene(helper, 30, 14, "The strike of a creature's erase");
        KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(1.0D);
        // Its longest erase is six seconds here: one that its blow does not end is over at 120 ticks.
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(6);
        ServerPlayer player = scene.player("kc-ce-strike-him", 13.5D, 9.5D);
        // No blow moves him: where he stands after it is where the end of the erase put him.
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        Zombie creature = scene.bearer(EntityType.ZOMBIE, 8.5D, 6.5D, false);
        Pig pig = scene.creature(EntityType.PIG, 22.5D, 3.5D);
        pig.setNoAi(true);
        creature.setTarget(player);
        Vec3 home = creature.position();
        StandPower power = StandPower.get(creature);
        int[] sent = new int[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            sent[0] = KingCrimsonEraseFixture.listener(player).sentCount();
            helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused");
        }));
        scene.walk(player, 9, 10, 0.0D, -0.2D);
        Watch watch = new Watch();
        helper.onEachTick(() -> scene.fixture.guarded(() -> {
            if (watch.done) {
                return;
            }
            if (KingCrimsonCreatureErase.isErasing(creature)) {
                watch.inside = KingCrimsonCreatureErase.ticksInside(creature);
                watch.fake = KingCrimsonCreatureErase.fakeBodyOf(creature);
                watch.image = KingCrimsonCreatureErase.imageOf(creature, player);
                KingCrimsonDriftBodyEntity body = KingCrimsonCreatureErase.driftBodyOf(creature, player);
                scene.rule(KingCrimsonEraseShiftGameTests.near(creature.position(), home) && player.getHealth() >= 20.0F,
                        watch.inside + " ticks inside its erase the creature is " + creature.position().subtract(home)
                                + " from its place, or the player was hurt (health " + player.getHealth() + ")");
                // How a fake body finds its way is the matter of the test before this one (a zombie at twice its pace
                // sometimes circles what it hunts for seconds). From here on it stands beside the image and thinks no
                // more, and its blows are landed for it: one before the third second, one after.
                if (watch.inside == 30) {
                    watch.fake.getNavigation().stop();
                    watch.fake.setNoAi(true);
                    watch.fake.moveTo(watch.image.x - 1.0D, watch.image.y, watch.image.z, -90.0F, 0.0F);
                }
                if (body != null && (watch.inside == 40 || watch.inside == 62)) {
                    watch.fake.doHurtTarget(body);
                }
                if (watch.inside == 50) {
                    helper.assertTrue(body != null && watch.fake.getTarget() == body && watch.fake.distanceTo(body) < 2.0D,
                            "Setup: fifty ticks inside, the fake body has not come up to the image of the player: it hunts "
                                    + watch.fake.getTarget() + ", " + (body == null ? "no body stands for him"
                                            : "stands " + watch.fake.distanceTo(body) + " blocks from the body on his image")
                                    + ", " + watch.fake.position().subtract(home) + " from where it began, on the ground "
                                    + watch.fake.onGround() + ", its way "
                                    + (watch.fake.getNavigation().isDone() ? "done" : "under way"));
                }
                return;
            }
            if (watch.fake == null) {
                return;
            }
            watch.done = true;
            int ended = watch.inside + 1;
            // C1.
            scene.rule(ended >= THREE_SECONDS && ended <= THREE_SECONDS + 5,
                    "with a blow of its fake body 40 ticks inside and the next 62 ticks inside, the erase ended " + ended
                            + " ticks inside: not with the first blow after three seconds");
            // C4.
            scene.rule(creature.position().distanceTo(watch.fake.position()) < 1.0E-3D && creature.position().distanceTo(home) > 2.0D,
                    "at the blow the creature stands " + creature.position().distanceTo(watch.fake.position())
                            + " blocks from where its fake body stood, " + creature.position().distanceTo(home) + " from its own place");
            scene.rule(putThere(player, sent[0], creature) == 1, "a client that watches was told "
                    + putThere(player, sent[0], creature) + " times to put the creature where its fake body stood in one step:"
                    + " its model would slide there");
            scene.rule(KingCrimsonEraseShiftGameTests.near(player.position(), watch.image), "at the blow the player stands "
                    + player.position().subtract(watch.image) + " from the image the erase last saw of him");
            scene.rule(player.getLastHurtByMob() == creature && player.getHealth() < 20.0F,
                    "as its erase ended the creature " + (player.getLastHurtByMob() == creature ? "struck" : "did not strike")
                            + " the player (health " + player.getHealth() + "): it stands " + creature.distanceTo(player)
                            + " blocks from him, " + (creature.isWithinMeleeAttackRange(player) ? "within" : "out of")
                            + " its reach, and he was last hurt by " + player.getLastHurtByMob());
            // H4, and what every erase does at its end.
            helper.assertTrue(MobAiPause.remainingTicks(pig) > 0, "Setup: the end of the erase did not daze a pig near by");
            scene.rule(MobAiPause.remainingTicks(creature) == 0, "the creature that erased is dazed for "
                    + MobAiPause.remainingTicks(creature) + " ticks by the end of its own erase");
            scene.rule(power.getAbilityCooldown(ERASE) > 0 && KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) == 1
                            && watch.fake.isRemoved(),
                    "after the blow the key waits " + power.getAbilityCooldown(ERASE) + " ticks, the player heard the end "
                            + KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) + " times, and the fake body is "
                            + (watch.fake.isRemoved() ? "gone" : "still there"));
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_longest")
    public static void withNoBlowTheEraseOfACreatureEndsAtItsLongestAndItStays(GameTestHelper helper) {
        Scene scene = new Scene(helper, 20, 12, "A creature's erase with no blow");
        // Seven seconds: the wait that goes by its 140 ticks is neither the shortest one (120) nor the fixed one (200).
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(7);
        ServerPlayer player = scene.player("kc-ce-longest-him", 14.5D, 6.5D);
        // A pig goes at nobody: neither does its fake body.
        Pig creature = scene.bearer(EntityType.PIG, 6.5D, 6.5D, false);
        Vec3 home = creature.position();
        StandPower power = StandPower.get(creature);
        int[] sent = new int[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            sent[0] = KingCrimsonEraseFixture.listener(player).sentCount();
            helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused");
        }));
        Watch watch = new Watch();
        helper.onEachTick(() -> scene.fixture.guarded(() -> {
            if (watch.done) {
                return;
            }
            if (KingCrimsonCreatureErase.isErasing(creature)) {
                watch.inside = KingCrimsonCreatureErase.ticksInside(creature);
                watch.fake = KingCrimsonCreatureErase.fakeBodyOf(creature);
                if (watch.inside >= 170) {
                    // The test ends here and not by its timeout, which would leave its players in the level.
                    watch.done = true;
                    scene.rule(false, "at Resolve level 4 and a longest erase of 7 seconds, an erase with no blow still"
                            + " runs " + watch.inside + " ticks inside");
                    scene.finish();
                }
                return;
            }
            if (watch.fake == null) {
                return;
            }
            watch.done = true;
            scene.rule(watch.inside + 1 == 140, "at Resolve level 4 and a longest erase of 7 seconds, an erase with no blow"
                    + " ended " + (watch.inside + 1) + " ticks inside");
            scene.rule(KingCrimsonEraseShiftGameTests.near(creature.position(), home)
                            && KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) == 1 && watch.fake.isRemoved(),
                    "after an erase with no blow the creature is " + creature.position().subtract(home) + " from its place,"
                            + " and the player heard the end "
                            + KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) + " times");
            // Owner 2026-11-11 and 12: the cooldown goes by the ticks the erase ran, as a time stop's: its 140 ticks. The
            // default is asked by itself: a run folder's settings file keeps whatever value it has.
            scene.rule(power.getAbilityCooldown(ERASE) == power.getAbilityCooldownShare(140)
                            && KingCrimsonConfig.TIME_ERASE_DYNAMIC_COOLDOWN_MULTIPLIER.getDefault() == 1.0D,
                    "after an erase of 140 ticks the key of the creature waits " + power.getAbilityCooldown(ERASE)
                            + " ticks, not as many (" + power.getAbilityCooldownShare(140) + "); the default of"
                            + " timeEraseDynamicCooldownMultiplier is "
                            + KingCrimsonConfig.TIME_ERASE_DYNAMIC_COOLDOWN_MULTIPLIER.getDefault() + ", not 1.0");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_escape")
    public static void aHitThatWouldKillACreatureInItsEraseIsEscapedByItsPassiveErase(GameTestHelper helper) {
        Scene scene = new Scene(helper, 44, 12, "The passive erase of a creature");
        ServerPlayer player = scene.player("kc-ce-escape-him", 6.5D, 9.5D);
        Zombie creature = scene.bearer(EntityType.ZOMBIE, 10.5D, 5.5D, false);
        Zombie twin = scene.bearer(EntityType.ZOMBIE, 10.5D, 1.5D, false);
        StandPower power = StandPower.get(creature);
        Vec3 home = creature.position();
        int[] sent = new int[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            sent[0] = KingCrimsonEraseFixture.listener(player).sentCount();
            helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused");
        }));
        // He walks on through the tick of the hit: his last move is a tenth of a block north.
        scene.walk(player, 10, 30, 0.0D, -0.1D);
        helper.runAfterDelay(30, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonCreatureErase.isErasing(creature), "Setup: the creature does not erase");
            // No reflex and no erase of the nearly dead for a creature: a hit it survives is a hit.
            float health = creature.getHealth();
            creature.hurt(creature.damageSources().playerAttack(player), 6.0F);
            scene.rule(creature.getHealth() < health && KingCrimsonCreatureErase.isErasing(creature)
                            && KingCrimsonEraseShiftGameTests.near(creature.position(), home)
                            && !power.isAbilityOnCooldown(KingCrimsonReflexErase.REFLEX)
                            && !power.isAbilityOnCooldown(KingCrimsonReflexErase.LAST_CHANCE),
                    "a hit of 6 on a creature of 20 health in its erase took " + (health - creature.getHealth()) + ", and it "
                            + (KingCrimsonCreatureErase.isErasing(creature) ? "erases on" : "left its erase") + " "
                            + creature.position().subtract(home) + " from its place");
        }));
        helper.runAfterDelay(34, () -> scene.fixture.guarded(() -> {
            if (!KingCrimsonCreatureErase.isErasing(creature) || !creature.isAlive()) {
                // What the hit it survived did to it is told above.
                scene.rule(false, "four ticks after a hit it survived the creature erases no more");
                scene.finish();
                return;
            }
            float health = creature.getHealth();
            Vec3 image = KingCrimsonCreatureErase.imageOf(creature, player);
            Vec3 from = player.position();
            int ticks = KingCrimsonConfig.lastChanceEraseTicks();
            double reach = KingCrimsonCreatureErase.escapeReach(creature, ticks);
            Vec3 away = new Vec3(home.x - from.x, 0.0D, home.z - from.z).normalize().scale(reach);
            creature.hurt(creature.damageSources().playerAttack(player), 1000.0F);
            scene.rule(creature.isAlive() && creature.getHealth() == health, "a hit that would kill the creature in its erase"
                    + " left it " + (creature.isAlive() ? "alive" : "dead") + " with " + creature.getHealth() + " of " + health + " health");
            scene.rule(!KingCrimsonCreatureErase.isErasing(creature) && power.getAbilityCooldown(ERASE) > 0
                            && KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) == 1,
                    "after the hit the creature " + (KingCrimsonCreatureErase.isErasing(creature) ? "erases on" : "erases no more")
                            + ", its key waits " + power.getAbilityCooldown(ERASE) + " ticks and the player heard the end "
                            + KingCrimsonEraseShiftGameTests.endSounds(player, sent[0]) + " times");
            // 2.1585 x 0.23 x 0.23 a tick, twice in an erase, for 80 ticks: 18.27 blocks.
            scene.rule(reach > 18.0D && reach < 18.6D && creature.position().distanceTo(home.add(away)) < 0.05D,
                    "the creature is " + creature.position().subtract(home) + " from its place: not the " + reach
                            + " blocks it could have walked in " + ticks + " ticks of an erase, away from the hit (" + away + ")");
            scene.rule(putThere(player, sent[0], creature) == 1, "a client that watches was told "
                    + putThere(player, sent[0], creature) + " times to put the creature where it escaped to in one step: its"
                    + " model would slide there");
            Vec3 forced = image.add(0.0D, 0.0D, -0.1D * KingCrimsonConfig.timeEraseShiftPerMove() * ticks);
            scene.rule(KingCrimsonEraseShiftGameTests.near(player.position(), forced), "the player, who walked a tenth of a"
                    + " block north a tick, stands " + player.position().subtract(image) + " from the image the erase last saw"
                    + " of him: not " + forced.subtract(image) + ", " + ticks + " ticks further on his way");
            int waits = power.getAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE);
            scene.rule(waits > 0 && waits == power.getAbilityCooldownShare(KingCrimsonConfig.lastChanceEraseCooldownTicks()),
                    "after its passive erase the creature's last chance waits " + waits + " ticks, not a player's "
                            + power.getAbilityCooldownShare(KingCrimsonConfig.lastChanceEraseCooldownTicks()));
            // Only in its erase: one that does not erase dies, and so does this one now.
            Vec3 twinAt = twin.position();
            twin.hurt(twin.damageSources().playerAttack(player), 1000.0F);
            scene.rule(twin.isDeadOrDying() && KingCrimsonEraseShiftGameTests.near(twin.position(), twinAt),
                    "a creature with King Crimson that does not erase " + (twin.isDeadOrDying() ? "died" : "survived")
                            + " a hit that would kill it, " + twin.position().subtract(twinAt) + " from its place");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_answer")
    public static void againstAPlayerAnotherAddOnNamesTheEraseOfACreatureIsReturnedToZero(GameTestHelper helper) {
        Scene scene = new Scene(helper, 30, 16, "A creature's erase returned to zero");
        ServerPlayer holder = scene.player("kc-ce-answer-holder", 22.5D, 3.5D);
        ServerPlayer player = scene.player("kc-ce-answer-him", 22.5D, 12.5D);
        Zombie first = scene.bearer(EntityType.ZOMBIE, 6.5D, 3.5D, false);
        Zombie second = scene.bearer(EntityType.ZOMBIE, 6.5D, 8.5D, false);
        Zombie third = scene.bearer(EntityType.ZOMBIE, 6.5D, 12.5D, false);
        first.setTarget(holder);
        second.setTarget(player);
        third.setTarget(player);
        // The stand-in of Gold Experience Requiem's rule: this one player is a holder.
        List<String> shown = new ArrayList<>();
        scene.answer(new KingCrimsonCreatureErase.Answer() {
            @Override
            public boolean answers(ServerPlayer asked, Mob creature) {
                return asked == holder;
            }

            @Override
            public void show(ServerPlayer asked, Mob creature) {
                shown.add(asked.getGameProfile().getName() + " on " + (creature == first ? "the first" : creature == second
                        ? "the second" : "another"));
            }
        });
        // The core's own question, which an answering ability cancels: here for the third creature.
        List<TimeEraseStartEvent> asked = new ArrayList<>();
        scene.listen(TimeEraseStartEvent.class, event -> {
            asked.add(event);
            if (event.getEraser() == third) {
                event.setCanceled(true);
            }
        });
        StandPower power = StandPower.get(first);
        int[] sent = new int[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            sent[0] = KingCrimsonEraseFixture.listener(holder).sentCount();
            helper.assertTrue(power.getStamina() == power.getMaxStamina(), "Setup: the bar of the first creature is not full");
            scene.press(first);
            scene.press(second);
            scene.press(third);
        }));
        helper.runAfterDelay(15, () -> scene.fixture.guarded(() -> {
            float paid = power.getMaxStamina() - power.getStamina();
            scene.rule(!KingCrimsonCreatureErase.isErasing(first) && KingCrimsonCreatureErase.fakeBodyOf(first) == null,
                    "a creature that fights a player the rule names erases all the same");
            scene.rule(shown.equals(List.of("kc-ce-answer-holder on the first")), "the rule that answered was told to show: " + shown);
            // Two erases were returned to zero within his hearing: the first creature's, and the third's below.
            scene.rule(paid > 100.0F && power.getAbilityCooldown(ERASE) > 0
                            && KingCrimsonEraseShiftGameTests.endSounds(holder, sent[0]) == 2,
                    "an erase returned to zero cost the creature " + paid + " of its bar, its key waits "
                            + power.getAbilityCooldown(ERASE) + " ticks and the holder heard an erase end "
                            + KingCrimsonEraseShiftGameTests.endSounds(holder, sent[0]) + " times, not twice");
            scene.rule(KingCrimsonCreatureErase.isErasing(second), "a creature that fights a player the rule does not name"
                    + " does not erase");
            boolean question = false;
            for (TimeEraseStartEvent event : asked) {
                question |= event.getEraser() == second && event.getKind() == TimeEraseStartEvent.Kind.ERASE
                        && event.getDurationTicks() == KingCrimsonConfig.timeEraseMaxTicks(4);
            }
            scene.rule(question, "the core's question of a starting erase was not asked for a creature (" + asked.size() + " asked)");
            scene.rule(!KingCrimsonCreatureErase.isErasing(third) && StandPower.get(third).getAbilityCooldown(ERASE) > 0,
                    "an erase of a creature that the core's question refused " + (KingCrimsonCreatureErase.isErasing(third)
                            ? "runs" : "does not run") + ", and its key waits " + StandPower.get(third).getAbilityCooldown(ERASE) + " ticks");
            // The passive erase against the one who deals the hit.
            Vec3 at = second.position();
            second.hurt(second.damageSources().playerAttack(holder), 1000.0F);
            scene.rule(second.isDeadOrDying() && KingCrimsonEraseShiftGameTests.near(second.position(), at),
                    "a hit of a player the rule names that would kill a creature in its erase left it "
                            + (second.isDeadOrDying() ? "dead" : "alive") + " " + second.position().subtract(at) + " from its place");
            scene.rule(shown.size() == 2 && shown.get(1).equals("kc-ce-answer-holder on the second")
                            && StandPower.get(second).isAbilityOnCooldown(KingCrimsonReflexErase.LAST_CHANCE),
                    "the rule that answered the passive erase was told to show: " + shown + ", and the last chance of the"
                            + " creature " + (StandPower.get(second).isAbilityOnCooldown(KingCrimsonReflexErase.LAST_CHANCE)
                                    ? "is spent" : "is not spent"));
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_stopped")
    public static void inStoppedTimeACreatureBeginsNeitherAnEraseNorItsPassiveErase(GameTestHelper helper) {
        Scene scene = new Scene(helper, 30, 12, "A creature's erase and stopped time");
        ServerPlayer player = scene.player("kc-ce-stopped-him", 20.5D, 6.5D);
        Zombie early = scene.bearer(EntityType.ZOMBIE, 8.5D, 3.5D, false);
        Zombie late = scene.bearer(EntityType.ZOMBIE, 8.5D, 9.5D, false);
        StandPower earlyPower = StandPower.get(early);
        StandPower latePower = StandPower.get(late);
        Vec3 home = early.position();
        GameTestTimeStops.Held[] stop = new GameTestTimeStops.Held[1];
        int[] inside = new int[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() ->
                helper.assertTrue(scene.press(early), "Setup: the key of the first creature is refused")));
        helper.runAfterDelay(12, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonCreatureErase.isErasing(early), "Setup: the first creature does not erase");
            stop[0] = GameTestTimeStops.hold(helper, new ChunkPos(early.blockPosition()), 3, 1200);
            scene.fixture.onClose(stop[0]::release);
        }));
        helper.runAfterDelay(14, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(TimeStopState.shouldFreezeOnServer(early) && TimeStopState.shouldFreezeOnServer(late),
                    "Setup: the stopped time does not hold the creatures");
            inside[0] = KingCrimsonCreatureErase.ticksInside(early);
        }));
        helper.runAfterDelay(24, () -> scene.fixture.guarded(() -> {
            // As for a player frozen in someone's time stop: his erase waits.
            scene.rule(KingCrimsonCreatureErase.isErasing(early) && KingCrimsonCreatureErase.ticksInside(early) == inside[0],
                    "in ten ticks of stopped time the erase of a frozen creature went from " + inside[0] + " ticks inside to "
                            + KingCrimsonCreatureErase.ticksInside(early));
            // C7, the key.
            float bar = latePower.getStamina();
            boolean pressed = scene.press(late);
            boolean began = KingCrimsonCreatureErase.begin(late);
            scene.rule(!pressed && !began && !KingCrimsonCreatureErase.canBegin(late) && !KingCrimsonCreatureErase.isErasing(late)
                            && latePower.getStamina() == bar && !latePower.isAbilityOnCooldown(ERASE),
                    "in stopped time the key of a creature " + (pressed ? "is taken" : "is refused") + ", an erase "
                            + (began ? "begins" : "does not begin") + ", and it cost " + (bar - latePower.getStamina()));
            // C7, the passive.
            early.hurt(early.damageSources().playerAttack(player), 1000.0F);
            scene.rule(!earlyPower.isAbilityOnCooldown(KingCrimsonReflexErase.LAST_CHANCE)
                            && KingCrimsonEraseShiftGameTests.near(early.position(), home),
                    "in stopped time a hit that would kill a creature in its erase " + (earlyPower.isAbilityOnCooldown(
                            KingCrimsonReflexErase.LAST_CHANCE) ? "spent" : "did not spend") + " its last chance, and it is "
                            + early.position().subtract(home) + " from its place");
            stop[0].release();
        }));
        helper.runAfterDelay(30, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(!TimeStopState.shouldFreezeOnServer(late), "Setup: time did not go on");
            scene.rule(scene.press(late), "after time went on the key of the creature is refused");
        }));
        helper.runAfterDelay(38, () -> scene.fixture.guarded(() -> {
            scene.rule(KingCrimsonCreatureErase.isErasing(late), "after time went on the creature does not erase");
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_creature_erase_shot")
    public static void whatAFakeBodyShootsNeverFliesAndTheCreatureShootsFromWhereItsFakeBodyStood(GameTestHelper helper) {
        Scene scene = new Scene(helper, 40, 30, "The strike of a creature that shoots");
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(8);
        ServerPlayer player = scene.player("kc-ce-shot-him", 24.5D, 15.5D);
        Skeleton creature = scene.bearer(EntityType.SKELETON, 12.5D, 15.5D, false);
        creature.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        creature.setTarget(player);
        // A skeleton steps about as it shoots: wherever that takes its fake body, an arrow near the floor is counted.
        AABB arena = new AABB(helper.absoluteVec(new Vec3(-1.0D, 0.0D, -1.0D)), helper.absoluteVec(new Vec3(40.0D, 8.0D, 30.0D)))
                .inflate(24.0D);
        Watch watch = new Watch();
        // Every shot its fake body tries, whatever becomes of it.
        int[] tried = new int[2];
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, EntityJoinLevelEvent.class, scene.kept(event -> {
            if (event.getEntity() instanceof AbstractArrow arrow && watch.fake != null && arrow.getOwner() == watch.fake) {
                tried[0]++;
            }
        }));
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() ->
                helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused")));
        helper.onEachTick(() -> scene.fixture.guarded(() -> {
            if (watch.done) {
                return;
            }
            if (KingCrimsonCreatureErase.isErasing(creature)) {
                watch.inside = KingCrimsonCreatureErase.ticksInside(creature);
                watch.fake = KingCrimsonCreatureErase.fakeBodyOf(creature);
                int flying = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, arena,
                        arrow -> arrow.getOwner() == watch.fake || arrow.getOwner() == creature).size();
                scene.rule(flying == 0, watch.inside + " ticks inside the erase " + flying + " arrows are in the world");
                if (watch.inside == 55) {
                    tried[1] = tried[0];
                    helper.assertTrue(tried[0] >= 1 && watch.fake.getMainHandItem().is(Items.BOW),
                            "Setup: in fifty-five ticks the fake body of a skeleton with a bow has not shot");
                }
                return;
            }
            if (watch.fake == null) {
                return;
            }
            watch.done = true;
            int ended = watch.inside + 1;
            scene.rule(ended >= THREE_SECONDS && ended <= THREE_SECONDS + 50
                            && tried[0] == tried[1] + 1,
                    "the fake body shot " + tried[1] + " times in the first three seconds, and the erase ended " + ended
                            + " ticks inside after " + (tried[0] - tried[1]) + " more: not with its first shot after them");
            scene.rule(creature.position().distanceTo(watch.fake.position()) < 1.0E-3D, "at the shot the creature stands "
                    + creature.position().distanceTo(watch.fake.position()) + " blocks from where its fake body stood");
            List<AbstractArrow> shot = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, arena,
                    arrow -> arrow.getOwner() == watch.fake || arrow.getOwner() == creature);
            scene.rule(shot.size() == 1 && shot.get(0).getOwner() == creature, "as its erase ended " + shot.size()
                    + " arrows fly, " + (shot.size() == 1 && shot.get(0).getOwner() == creature ? "the creature's" : "not one of the creature"));
            scene.finish();
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_creature_erase_fuse")
    public static void theFakeBodyOfACreeperNeverGoesOffAndItsFullFuseIsItsBlow(GameTestHelper helper) {
        Scene scene = new Scene(helper, 24, 12, "The strike of a creeper's erase");
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(8);
        ServerPlayer player = scene.player("kc-ce-fuse-him", 12.5D, 6.5D);
        Creeper creature = scene.bearer(EntityType.CREEPER, 8.5D, 6.5D, false);
        creature.setTarget(player);
        Vec3 home = creature.position();
        Watch watch = new Watch();
        int[] lit = {-1};
        int[] sent = new int[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            sent[0] = KingCrimsonEraseFixture.listener(player).sentCount();
            helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused");
        }));
        helper.onEachTick(() -> scene.fixture.guarded(() -> {
            if (watch.done) {
                return;
            }
            // An explosion that harms nothing is still heard, seen and felt: this one never is.
            long blasts = KingCrimsonEpitaphFixture.flat(KingCrimsonEraseFixture.listener(player).sentSince(sent[0])).stream()
                    .filter(packet -> packet instanceof ClientboundExplodePacket).count();
            if (KingCrimsonCreatureErase.isErasing(creature)) {
                watch.inside = KingCrimsonCreatureErase.ticksInside(creature);
                watch.fake = KingCrimsonCreatureErase.fakeBodyOf(creature);
                if (lit[0] < 0 && ((Creeper) watch.fake).getSwellDir() > 0) {
                    lit[0] = watch.inside;
                }
                scene.rule(!watch.fake.isRemoved() && blasts == 0 && player.getHealth() >= 20.0F
                                && KingCrimsonEraseShiftGameTests.near(creature.position(), home),
                        watch.inside + " ticks inside the erase the fake body of a creeper is "
                                + (watch.fake.isRemoved() ? "gone" : "there") + ", the player was sent " + blasts
                                + " explosions and has " + player.getHealth() + " health, and the creature is "
                                + creature.position().subtract(home) + " from its place");
                return;
            }
            if (watch.fake == null) {
                return;
            }
            watch.done = true;
            helper.assertTrue(lit[0] > 0 && lit[0] + FUSE < THREE_SECONDS,
                    "Setup: the fake body of a creeper did not want its fuse early enough (from tick " + lit[0] + ")");
            // Its fuse was full before the three seconds were over: the erase ends as they are.
            scene.rule(watch.inside + 1 == THREE_SECONDS, "the fake body of a creeper wanted its"
                    + " fuse from tick " + lit[0] + ", and the erase ended " + (watch.inside + 1) + " ticks inside: not as the"
                    + " three seconds were over");
            scene.rule(creature.position().distanceTo(watch.fake.position()) < 1.0E-3D && creature.position().distanceTo(home) > 0.5D
                            && creature.isAlive() && blasts == 0 && player.getHealth() >= 20.0F,
                    "after the erase the creeper stands " + creature.position().distanceTo(watch.fake.position())
                            + " blocks from where its fake body stood, " + creature.position().distanceTo(home)
                            + " from its own place; the player was sent " + blasts + " explosions");
            scene.rule(creature.isIgnited(), "after the swap the creeper itself is not lit");
            scene.finish();
        }));
    }

    // What a creature of another mod that goes off with its blow would do: an explosion of its own, and it is gone.
    @GameTest(template = "empty", timeoutTicks = 260, batch = "kc_creature_erase_went_off")
    public static void aFakeBodyThatGoesOffWithItsBlowHarmsNothingAndTheCreatureIsPutWhereItStood(GameTestHelper helper) {
        Scene scene = new Scene(helper, 28, 12, "The strike of a creature whose blow is its end");
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(8);
        ServerPlayer player = scene.player("kc-ce-off-him", 20.5D, 6.5D);
        Zombie creature = scene.bearer(EntityType.ZOMBIE, 8.5D, 6.5D, false);
        creature.setTarget(player);
        Vec3 home = creature.position();
        Watch watch = new Watch();
        Vec3[] wentOff = new Vec3[1];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() ->
                helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused")));
        helper.onEachTick(() -> scene.fixture.guarded(() -> {
            if (watch.done) {
                return;
            }
            if (KingCrimsonCreatureErase.isErasing(creature)) {
                watch.inside = KingCrimsonCreatureErase.ticksInside(creature);
                watch.fake = KingCrimsonCreatureErase.fakeBodyOf(creature);
                if (watch.inside == 20) {
                    wentOff[0] = watch.fake.position();
                    helper.assertTrue(wentOff[0].distanceTo(home) > 1.5D && wentOff[0].distanceTo(home) < 5.0D,
                            "Setup: twenty ticks inside, the fake body is " + wentOff[0].distanceTo(home) + " blocks from the creature");
                    helper.getLevel().explode(watch.fake, wentOff[0].x, wentOff[0].y, wentOff[0].z, 3.0F, Level.ExplosionInteraction.MOB);
                    watch.fake.discard();
                }
                BlockPos under = new BlockPos(10, 1, 6);
                scene.rule(creature.getHealth() >= creature.getMaxHealth() && creature.getDeltaMovement().lengthSqr() < 1.0E-8D
                                && helper.getBlockState(under).is(Blocks.STONE)
                                && KingCrimsonEraseShiftGameTests.near(creature.position(), home),
                        watch.inside + " ticks inside the erase the creature has " + creature.getHealth() + " health, is thrown "
                                + creature.getDeltaMovement() + " and stands " + creature.position().subtract(home)
                                + " from its place; the floor by it is " + helper.getBlockState(under).getBlock());
                return;
            }
            if (watch.fake == null) {
                return;
            }
            watch.done = true;
            helper.assertTrue(wentOff[0] != null, "Setup: the erase ended before its fake body went off");
            // Its blow came too early to end the erase, and it is gone: the blow is taken as made when the three seconds are over.
            scene.rule(watch.inside + 1 == THREE_SECONDS, "a fake body went off 20 ticks"
                    + " inside, and the erase ended " + (watch.inside + 1) + " ticks inside: not as the three seconds were over");
            scene.rule(creature.position().distanceTo(wentOff[0]) < 1.0E-3D && creature.isAlive(),
                    "after the erase the creature stands " + creature.position().distanceTo(wentOff[0])
                            + " blocks from where its fake body went off, " + creature.position().distanceTo(home)
                            + " from its own place");
            scene.finish();
        }));
    }

    // C10, as the owner put it: "時刪的耐力消耗與返還機制在兩種模式下都參照時間暫停做提前結束的能量返還獎勵".
    @GameTest(template = "empty", timeoutTicks = 240, batch = "kc_erase_refund")
    public static void anEraseThatEndsEarlyGivesBackTheUnusedShareOfItsStartAsATimeStopDoes(GameTestHelper helper) {
        Scene scene = new Scene(helper, 30, 18, "The refund of an erase that ends early");
        // The longest erase is five seconds here: 100 ticks at Resolve level 4.
        KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(5);
        ServerPlayer plain = scene.player("kc-refund-plain", 4.5D, 3.5D);
        ServerPlayer free = scene.player("kc-refund-free", 4.5D, 8.5D);
        ServerPlayer thrown = scene.player("kc-refund-thrown", 4.5D, 13.5D);
        ServerPlayer whole = scene.player("kc-refund-whole", 14.5D, 3.5D);
        Zombie creature = scene.bearer(EntityType.ZOMBIE, 22.5D, 8.5D, false);
        Zombie outcast = scene.bearer(EntityType.ZOMBIE, 22.5D, 14.5D, false);
        StandPower outcastPower = StandPower.get(outcast);
        StandPower plainPower = scene.fixture.kingCrimson(plain, 4);
        StandPower freePower = scene.fixture.kingCrimson(free, 4);
        StandPower thrownPower = scene.fixture.kingCrimson(thrown, 4);
        StandPower wholePower = scene.fixture.kingCrimson(whole, 4);
        StandPower power = StandPower.get(creature);
        // The mode with no cooldowns is a player's own setting.
        free.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = true;
        // A player and a creature are thrown out of their erases twenty ticks in, as Gold Experience Requiem throws an
        // eraser out: the core's question of a running erase is asked for both.
        scene.listen(TimeEraseRunningEvent.class, event -> {
            if ((event.getEraser() == thrown || event.getEraser() == outcast) && event.getTicksElapsed() >= 20) {
                event.answer(KingCrimsonAddon.id("game_test_answer"), null);
            }
        });
        float start = plainPower.getStaminaCostShare(
                StandAbilityStamina.effectiveCost(plainPower, KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA));
        // Two whose erase runs with their Stand doing nothing, so that their bars would fill: one in each mode.
        ServerPlayer idle = scene.player("kc-refund-idle", 14.5D, 8.5D);
        ServerPlayer freeIdle = scene.player("kc-refund-free-idle", 14.5D, 13.5D);
        StandPower idlePower = scene.fixture.kingCrimson(idle, 4);
        StandPower freeIdlePower = scene.fixture.kingCrimson(freeIdle, 4);
        freeIdle.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = true;
        int[] shortWait = new int[1];
        float[] idleFell = new float[3];
        float[] freeFell = new float[2];
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            for (ServerPlayer player : List.of(idle, freeIdle)) {
                StandPower.get(player).setStamina(StandPower.get(player).getMaxStamina() - 400.0F);
                helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(player), "Setup: an erase did not begin");
            }
            for (ServerPlayer player : List.of(plain, free, thrown, whole)) {
                helper.assertTrue(scene.fixture.holdErase(player).isActionSuccess, "Setup: an erase of a player did not start");
            }
            helper.assertTrue(scene.press(creature) && scene.press(outcast), "Setup: the key of a creature is refused");
        }));
        helper.runAfterDelay(10, () -> {
            idleFell[0] = idlePower.getStamina();
            freeFell[0] = freeIdlePower.getStamina();
            // Owner 2026-11-11: no flat multiplier in the mode with no cooldowns; its repeats are the core's to price.
            float took = plainPower.getMaxStamina() - plainPower.getStamina();
            float tookFree = freePower.getMaxStamina() - freePower.getStamina();
            // The start costs the same in both modes. What differs is the fifth of his regeneration that the core leaves
            // a player in the mode with no cooldowns while his Stand acts: the erase takes nothing more of it.
            float upkeep = plainPower.getStaminaCostShare(
                    StandAbilityStamina.effectiveCost(plainPower, KingCrimsonConfig.timeEraseStaminaPerTick()));
            float ticks = (took - start) / upkeep;
            float kept = freePower.getStaminaTickGain();
            scene.rule(took > 200.0F && ticks > 0.5F && kept > 0.0F && Math.abs(took - tookFree - ticks * kept) < 0.01F,
                    "the start and the first " + ticks + " ticks of an erase took " + took + " of the bar with cooldowns and "
                            + tookFree + " in the mode with none, where the core leaves " + kept + " a tick of his"
                            + " regeneration to a player whose Stand acts: the start costs the same, and nothing more is taken");
        });
        helper.runAfterDelay(26, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(idle) && KingCrimsonEraseFixture.erasing(freeIdle)
                    && idlePower.getSummonedStandEntity() != null && idlePower.getSummonedStandEntity().getCurStandAction() == null,
                    "Setup: an erase with an idle Stand is over, or the Stand does something");
            idleFell[0] -= idlePower.getStamina();
            idleFell[1] = idlePower.getStaminaCostShare(
                    StandAbilityStamina.effectiveCost(idlePower, KingCrimsonConfig.timeEraseStaminaPerTick()));
            idleFell[2] = idlePower.getStaminaTickGain();
            freeFell[0] -= freeIdlePower.getStamina();
            freeFell[1] = freeIdlePower.getStaminaTickGain();
            KingCrimsonTemporalStateService.terminate(idle);
            KingCrimsonTemporalStateService.terminate(freeIdle);
        }));
        helper.runAfterDelay(30, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(plain) && KingCrimsonEraseFixture.erasing(free)
                    && KingCrimsonCreatureErase.isErasing(creature), "Setup: an erase is over before its key was let go");
            long now = helper.getLevel().getGameTime();
            // The mode with cooldowns.
            int elapsed = KingCrimsonEraseFixture.state(plain).elapsedWorldTicks(now);
            float before = plainPower.getStamina();
            scene.fixture.release(plain, KingCrimsonEraseFixture.ERASE_KEY);
            float back = plainPower.getStamina() - before;
            shortWait[0] = plainPower.getAbilityCooldown(ERASE);
            float due = KingCrimsonEraseStamina.unusedStart(start, 100, elapsed);
            // 250 x 99/100 x (100 - 25)/100 = 185.6 for an erase let go after 25 of its 100 ticks.
            scene.rule(elapsed >= 20 && elapsed <= 30 && Math.abs(due - start * 0.99F * (100 - elapsed) / 100.0F) < 0.01F
                            && Math.abs(back - due) < 0.01F,
                    "with cooldowns, an erase let go " + elapsed + " ticks into its 100 gave back " + back + " of its start of "
                            + start + ": not (start - a hundredth) x " + (100 - elapsed) + "/100 = "
                            + start * 0.99F * (100 - elapsed) / 100.0F);
            // The mode without: the same share.
            elapsed = KingCrimsonEraseFixture.state(free).elapsedWorldTicks(now);
            before = freePower.getStamina();
            scene.fixture.release(free, KingCrimsonEraseFixture.ERASE_KEY);
            back = freePower.getStamina() - before;
            helper.assertTrue(freePower.getAbilityCooldown(ERASE) == 0 && plainPower.getAbilityCooldown(ERASE) > 0,
                    "Setup: the player in the mode with no cooldowns waits " + freePower.getAbilityCooldown(ERASE)
                            + " ticks, the other " + plainPower.getAbilityCooldown(ERASE));
            scene.rule(Math.abs(back - KingCrimsonEraseStamina.unusedStart(start, 100, elapsed)) < 0.01F,
                    "with no cooldowns, an erase let go " + elapsed + " ticks into its 100 gave back " + back
                            + ": not the " + KingCrimsonEraseStamina.unusedStart(start, 100, elapsed) + " it gives back with cooldowns");
            // A creature's erase.
            elapsed = KingCrimsonCreatureErase.ticksInside(creature);
            before = power.getStamina();
            KingCrimsonCreatureErase.end(creature);
            back = power.getStamina() - before;
            scene.rule(elapsed >= 20 && Math.abs(back - KingCrimsonEraseStamina.unusedStart(start, 100, elapsed)) < 0.01F,
                    "the erase of a creature that ended " + elapsed + " ticks into its 100 gave back " + back + ": not the "
                            + KingCrimsonEraseStamina.unusedStart(start, 100, elapsed) + " that a player's gives back");
        }));
        // Those that are not let go: what the bar of each gains in the tick his erase ends.
        float[] last = new float[3];
        boolean[] seen = new boolean[4];
        int[] inside = {-1, -1};
        helper.onEachTick(() -> scene.fixture.guarded(() -> {
            if (inside[1] < 0 && KingCrimsonCreatureErase.isErasing(outcast)) {
                last[2] = outcastPower.getStamina();
                inside[0] = KingCrimsonCreatureErase.ticksInside(outcast);
            } else if (inside[1] < 0 && inside[0] >= 0) {
                inside[1] = inside[0] + 1;
                float gained = outcastPower.getStamina() - last[2];
                scene.rule(inside[1] >= 20 && inside[1] <= 22 && gained < 5.0F, "a creature that the core's question of a"
                        + " running erase throws out twenty ticks in erased for " + inside[1] + " ticks and got " + gained
                        + " of its start back");
            }
            if (!seen[0] && KingCrimsonEraseFixture.erasing(thrown)) {
                last[0] = thrownPower.getStamina();
                seen[2] = true;
            } else if (!seen[0] && seen[2]) {
                seen[0] = true;
                float gained = thrownPower.getStamina() - last[0];
                scene.rule(gained < 5.0F, "one who was thrown out of his erase got " + gained + " of its start back");
            }
            if (!seen[1] && KingCrimsonEraseFixture.erasing(whole)) {
                last[1] = wholePower.getStamina();
                seen[3] = true;
            } else if (!seen[1] && seen[3]) {
                seen[1] = true;
                float gained = wholePower.getStamina() - last[1];
                scene.rule(gained < 5.0F, "an erase that ran its whole length gave back " + gained + " of its start");
                // Owner 2026-11-11 and 12: the cooldown goes by the ticks the erase ran, at least 120: its 100 and one of 24
                // both wait the
                // shortest 120.
                scene.rule(wholePower.getAbilityCooldown(ERASE) == wholePower.getAbilityCooldownShare(120)
                                && shortWait[0] == plainPower.getAbilityCooldownShare(120),
                        "after an erase of 100 ticks a player waits " + wholePower.getAbilityCooldown(ERASE) + " ticks (the"
                                + " shortest is " + wholePower.getAbilityCooldownShare(120) + "), and after one of 24 ticks "
                                + shortWait[0] + " (the shortest is " + plainPower.getAbilityCooldownShare(120) + ")");
                scene.rule(Math.abs(idleFell[0] - 16 * idleFell[1]) < 0.01F && idleFell[2] > 1.0F, "in 16 ticks of an erase the bar of a player"
                        + " whose Stand does nothing fell " + idleFell[0] + ": not 16 times the " + idleFell[1] + " a tick of"
                        + " its upkeep, with nothing regained (his bar gains " + idleFell[2] + " a tick)");
                // The core's share of it (StandAbilityFatigue.SPECIAL_STATE_REGEN_FACTOR) is what he keeps there.
                scene.rule(Math.abs(freeFell[0] - 16 * (idleFell[1] - 0.2F * freeFell[1])) < 0.01F && freeFell[1] > 1.0F,
                        "in the mode with no cooldowns, in 16 ticks of an erase the bar of a player whose Stand does nothing"
                                + " fell " + freeFell[0] + ": not 16 times the " + idleFell[1] + " a tick of its upkeep less"
                                + " the fifth he keeps of his regeneration of " + freeFell[1] + " a tick");
                scene.rule(seen[0] && inside[1] >= 0, "the erase of " + (seen[0] ? "" : "the player ")
                        + (inside[1] >= 0 ? "" : "the creature ") + "that the core's question throws out ran its whole length");
                scene.finish();
            }
        }));
    }

    // C9, as the owner put it: "如果是技能無cd僅消耗耐力模式，對生物替身使者不生效，它們還是只能用cd模式".
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_mode")
    public static void inTheModeWithNoCooldownsACreatureStillWaitsForItsCooldown(GameTestHelper helper) {
        Scene scene = new Scene(helper, 24, 12, "A creature's erase in the mode with no cooldowns");
        ServerPlayer player = scene.player("kc-ce-mode-him", 16.5D, 6.5D);
        StandPower his = scene.fixture.kingCrimson(player, 4);
        player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = true;
        Zombie creature = scene.bearer(EntityType.ZOMBIE, 6.5D, 6.5D, false);
        creature.setTarget(player);
        StandPower power = StandPower.get(creature);
        Ability key = power.getAbility(ERASE);
        helper.runAfterDelay(5, () -> scene.fixture.guarded(() -> {
            // What the core is shown of the erase: its normal cooldown, a creature's as a player's who is not in the mode.
            scene.rule(key.getCooldown(power, -1) == KingCrimsonConfig.timeEraseCooldownTicks() && key.getCooldown(his, -1) == 0,
                    "the erase tells the core a cooldown of " + key.getCooldown(power, -1) + " ticks for a creature (the setting"
                            + " is " + KingCrimsonConfig.timeEraseCooldownTicks() + ") and " + key.getCooldown(his, -1)
                            + " for a player in the mode with no cooldowns");
            helper.assertTrue(scene.fixture.holdErase(player).isActionSuccess, "Setup: the erase of the player did not start");
            helper.assertTrue(scene.press(creature), "Setup: the key of the creature is refused");
        }));
        helper.runAfterDelay(20, () -> scene.fixture.guarded(() -> {
            helper.assertTrue(KingCrimsonEraseFixture.erasing(player) && KingCrimsonCreatureErase.isErasing(creature),
                    "Setup: an erase is over before its end was asked");
            scene.fixture.release(player, KingCrimsonEraseFixture.ERASE_KEY);
            KingCrimsonCreatureErase.end(creature);
            helper.assertTrue(his.getAbilityCooldown(ERASE) == 0,
                    "Setup: the player in the mode with no cooldowns waits " + his.getAbilityCooldown(ERASE) + " ticks");
            int waits = power.getAbilityCooldown(ERASE);
            // The cooldown of the mode with cooldowns: as many ticks as the erase ran, at least 120.
            scene.rule(waits == power.getAbilityCooldownShare(120),
                    "beside a player in the mode with no cooldowns, the key of a creature waits " + waits + " ticks after its"
                            + " erase of fifteen ticks: not the " + power.getAbilityCooldownShare(120)
                            + " of the mode with cooldowns");
            scene.rule(!key.checkConditions(power).isPositive() && !KingCrimsonCreatureErase.canBegin(creature)
                            && !KingCrimsonCreatureErase.begin(creature) && !KingCrimsonCreatureErase.isErasing(creature),
                    "while its key waits a creature can begin an erase beside a player in the mode with no cooldowns");
            scene.finish();
        }));
    }

    // The meeting with Epitaph: what a prediction holds on its track is the prediction's.
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_creature_erase_track")
    public static void aCreatureAPredictionTakesOnItsTrackErasesNoMoreAndBeginsNoErase(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ce-track", 4, 28, 12);
        fixture.budget(0);
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 14.5D, 2.5D));
        StandPower power = kingCrimson(helper, fixture.base, zombie);
        fixture.base.onClose(() -> KingCrimsonCreatureErase.end(zombie));
        EpitaphSession[] running = new EpitaphSession[1];
        Mob[] fake = new Mob[1];
        HeldInputEntry[] key = new HeldInputEntry[1];
        helper.runAfterDelay(5, () -> fixture.guarded(() -> {
            key[0] = press(zombie);
            helper.assertTrue(key[0] != null, "Setup: the key of the creature is refused");
        }));
        helper.runAfterDelay(9, () -> AbilityInput.keyRelease(key[0].keyId, zombie));
        helper.runAfterDelay(12, () -> fixture.guarded(() -> {
            fake[0] = KingCrimsonCreatureErase.fakeBodyOf(zombie);
            helper.assertTrue(KingCrimsonCreatureErase.isErasing(zombie) && fake[0] != null, "Setup: the creature does not erase");
            running[0] = fixture.press(null);
            helper.assertTrue(EpitaphSessions.isOnTrack(zombie), "Setup: the zombie that hunts the user is on no track");
            fixture.rule(!EpitaphSessions.isOnTrack(fake[0]), "the fake body of a creature's erase is taken on a track");
        }));
        helper.runAfterDelay(16, () -> fixture.guarded(() -> {
            helper.assertTrue(!running[0].ended() && EpitaphSessions.isOnTrack(zombie), "Setup: the prediction let the zombie go");
            fixture.rule(!KingCrimsonCreatureErase.isErasing(zombie) && fake[0].isRemoved(),
                    "a creature that a prediction took on its track " + (KingCrimsonCreatureErase.isErasing(zombie)
                            ? "erases on" : "erases no more") + ", its fake body " + (fake[0].isRemoved() ? "gone" : "still there"));
            power.setAbilityCooldown(ERASE, 0, 0);
            power.setStamina(power.getMaxStamina());
            fixture.rule(!KingCrimsonCreatureErase.canBegin(zombie) && !KingCrimsonCreatureErase.begin(zombie),
                    "a creature on the track of a prediction can begin an erase");
            running[0].end(EpitaphEnd.CANCELLED);
            fixture.finish("A creature's erase and a prediction");
        }));
    }

    /** How often a client that watches was told to put the creature where it stands now, in one step. */
    private static long putThere(ServerPlayer watcher, int since, Mob creature) {
        return KingCrimsonEraseFixture.listener(watcher).payloadsSince(since, TrDirectEntityPosPacket.class).stream()
                .filter(packet -> packet.entityId() == creature.getId()
                        && packet.pos().distanceTo(creature.position()) < 1.0E-3D)
                .count();
    }

    /** King Crimson for a creature, as the creature add-on gives a Stand: every skill, summoned, a full bar. */
    static StandPower kingCrimson(GameTestHelper helper, KingCrimsonEraseFixture fixture, Mob creature) {
        StandPower power = PowerClass.STAND.attachGet(creature);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        power.setResolveLevel(4);
        helper.assertTrue(power.getResolveLevel() == 4 && KingCrimsonAddon.KING_CRIMSON.get().summon(creature, power),
                "Setup: the creature did not get King Crimson at Resolve level 4, or its Stand did not come out");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        fixture.track(stand);
        power.setStamina(power.getMaxStamina());
        return power;
    }

    /** The key as the creature add-on presses one: the condition, then the press of a held key. Null when refused. */
    static HeldInputEntry press(Mob creature) {
        StandPower power = StandPower.get(creature);
        Ability key = power.getAbility(ERASE);
        if (key == null || !key.checkConditions(power).isPositive()) {
            return null;
        }
        return AbilityInput.keyPressMob(key, creature, new FriendlyByteBuf(Unpooled.EMPTY_BUFFER), InputMethod.HOLD);
    }

    private static final class Watch {
        private Mob fake;
        private Vec3 image;
        private int inside;
        private boolean done;
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final KingCrimsonEraseFixture fixture;
        private final ServerLevel level;
        private final String title;
        private final List<String> broken = new ArrayList<>();
        // A rule broken in many ticks is told once: with the numbers of its first tick.
        private final List<String> told = new ArrayList<>();
        private final List<Mob> bearers = new ArrayList<>();
        // The keys the creatures hold, let go when their action is over, as the creature add-on lets go.
        private final List<Object[]> held = new ArrayList<>();

        private Scene(GameTestHelper helper, int length, int width, String title) {
            this.helper = helper;
            this.title = title;
            level = helper.getLevel();
            fixture = new KingCrimsonEraseFixture(helper);
            fixture.floor(length, width);
            double perMove = KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.get();
            int longest = KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.get();
            boolean passive = KingCrimsonConfig.REFLEX_ERASE.get();
            // The passive erase is a setting: it is on here, whatever another test left.
            KingCrimsonConfig.REFLEX_ERASE.set(true);
            AABB arena = new AABB(helper.absoluteVec(new Vec3(-1.0D, 0.0D, -1.0D)),
                    helper.absoluteVec(new Vec3(length, 8.0D, width))).inflate(12.0D);
            fixture.onClose(() -> {
                KingCrimsonConfig.TIME_ERASE_SHIFT_PER_MOVE.set(perMove);
                KingCrimsonConfig.TIME_ERASE_MAX_SECONDS.set(longest);
                KingCrimsonConfig.REFLEX_ERASE.set(passive);
                for (Mob bearer : bearers) {
                    KingCrimsonCreatureErase.end(bearer);
                }
                for (Entity loose : level.getEntities((Entity) null, arena,
                        entity -> entity instanceof Projectile || entity instanceof ItemEntity)) {
                    loose.discard();
                }
            });
            helper.onEachTick(() -> held.removeIf(entry -> {
                HeldInputEntry key = (HeldInputEntry) entry[1];
                if (key.action instanceof EntityActionInstance action && !action.isOver()) {
                    return false;
                }
                AbilityInput.keyRelease(key.keyId, (Mob) entry[0]);
                return true;
            }));
        }

        /** A player whose client is told of what is added around him. */
        private ServerPlayer player(String name, double x, double z) {
            ServerPlayer player = fixture.player(name, x, 2.0D, z);
            player.setOnGround(true);
            GameTestChunkSending.markSentAround(player);
            level.getChunkSource().move(player);
            return player;
        }

        /** A creature on the floor, kept from burning in the test's daylight. */
        private <T extends Mob> T creature(EntityType<T> type, double x, double z) {
            T mob = type.create(level);
            helper.assertTrue(mob != null, "Setup: no " + type);
            Vec3 at = helper.absoluteVec(new Vec3(x, 2.0D, z));
            mob.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
            mob.setPersistenceRequired();
            if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
            helper.assertTrue(level.addFreshEntity(mob), "Setup: the " + type + " was not added");
            return fixture.track(mob);
        }

        /** A creature that carries King Crimson; one that does not think stands where it is put. */
        private <T extends Mob> T bearer(EntityType<T> type, double x, double z, boolean thinks) {
            T mob = creature(type, x, z);
            mob.setNoAi(!thinks);
            kingCrimson(helper, fixture, mob);
            bearers.add(mob);
            return mob;
        }

        private <T extends Mob> T sturdy(T mob) {
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0D);
            mob.setHealth(200.0F);
            return mob;
        }

        private boolean press(Mob creature) {
            HeldInputEntry key = KingCrimsonCreatureEraseGameTests.press(creature);
            if (key != null) {
                held.add(new Object[] {creature, key});
            }
            return key != null;
        }

        private void walk(Entity who, int fromTick, int ticks, double dx, double dz) {
            for (int tick = 0; tick < ticks; tick++) {
                helper.runAfterDelay(fromTick + tick, () -> {
                    if (!who.isRemoved()) {
                        who.setPos(who.getX() + dx, who.getY(), who.getZ() + dz);
                    }
                });
            }
        }

        private void answer(KingCrimsonCreatureErase.Answer answer) {
            KingCrimsonCreatureErase.registerAnswer(KingCrimsonAddon.id("game_test_holder"), answer);
            fixture.onClose(() -> KingCrimsonCreatureErase.removeAnswer(KingCrimsonAddon.id("game_test_holder")));
        }

        private <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, listener);
            fixture.onClose(() -> NeoForge.EVENT_BUS.unregister(listener));
        }

        /** A listener the test registers itself, taken off the bus when the test closes. */
        private <T extends Event> Consumer<T> kept(Consumer<T> listener) {
            fixture.onClose(() -> NeoForge.EVENT_BUS.unregister(listener));
            return listener;
        }

        private void rule(boolean holds, String broke) {
            String kind = broke.replaceAll("-?[0-9][0-9.E-]*", "#");
            if (!holds && !told.contains(kind)) {
                told.add(kind);
                broken.add(broke);
            }
        }

        private void finish() {
            // The report of a failed test is kept in a book: a long list of broken rules is cut.
            String rules = String.join("; ", broken.subList(0, Math.min(5, broken.size())));
            fixture.guarded(() -> helper.assertTrue(broken.isEmpty(),
                    title + ": " + (rules.length() > 900 ? rules.substring(0, 900) + "..." : rules)));
            fixture.succeed();
        }
    }
}
