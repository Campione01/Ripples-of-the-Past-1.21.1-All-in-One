package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.ability.GerReturnToZeroAbility;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.state.GerHitAnswerService;
import rotp.addons.ger.state.GerLivingState;
import rotp.addons.ger.state.GerRtzRewindService;
import rotp.core.api.entity.MobAiPause;
import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;

/**
 * The owner, 2026-11-08, on the press: "歸零時玩家自身和友方生物不改變位置，
 * 生命值可以恢復到歸零前。但敵方生物或其他玩家受到的傷害及自身生命值與持續性
 * debuff不會消失". The user and his side keep their places and get their
 * health back; enemies and other players go back to their places at the mark
 * and keep what was done to them.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerRtzPressGameTests {
    private static final int MARK_CHUNK_RADIUS = 8;
    private static final float STAMINA_COST = 200.0F;

    private GerRtzPressGameTests() {}

    private static GerLivingState state(LivingEntity entity) {
        return GoldExperienceRequiemAddon.livingState(entity);
    }

    private static Husk husk(GerMeleeGameTestScene scene, Vec3 position) {
        Husk husk = EntityType.HUSK.create(scene.level);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        return scene.add(husk, position);
    }

    private static void hurt(LivingEntity entity, float amount) {
        entity.invulnerableTime = 0;
        entity.hurt(entity.damageSources().generic(), amount);
    }

    private static void poison(LivingEntity entity) {
        // not a hit the user's Requiem answers: the test puts it there itself
        GerHitAnswerService.applyingOwnEffects(() ->
                entity.addEffect(new MobEffectInstance(MobEffects.POISON, 2000, 0)));
    }

    private static void weaken(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 2000, 0));
    }

    private static boolean near(Entity entity, Vec3 place) {
        return entity.position().distanceTo(place) < 1.0E-3;
    }

    /** What the log says of a body after the press. */
    private record Body(float health, boolean poison, boolean weakness, int fire, Vec3 at) {
        Body(LivingEntity entity) {
            this(entity.getHealth(), entity.hasEffect(MobEffects.POISON), entity.hasEffect(MobEffects.WEAKNESS),
                    entity.getRemainingFireTicks(), entity.position());
        }
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_rtz_press")
    public static void thePressRestoresTheUsersSideAndLeavesHisEnemiesTheirWounds(GameTestHelper helper) {
        new Sequence(helper, "rtz/press", 250) {
            private Actor owner;
            private ObservedCow friend;
            private ObservedCow fallen;
            private Husk enemy;
            private Husk slain;
            private Client other;
            private UUID fallenId;
            private UUID slainId;
            private Vec3 friendMark;
            private Vec3 fallenMark;
            private Vec3 enemyMark;
            private Vec3 otherMark;
            private Vec3 ownerNow;
            private Vec3 friendNow;
            private float stamina;
            private float otherHealth;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        Vec3 at = scene.origin;
                        owner = scene.actor();
                        // an effect he has at the mark stays his
                        owner.user().addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 4000, 0));
                        friendMark = at.add(3, 0, 0);
                        fallenMark = at.add(3, 0, 3);
                        enemyMark = at.add(-3, 0, 0);
                        otherMark = at.add(0, 0, 3);
                        friend = scene.cow(friendMark);
                        fallen = scene.cow(fallenMark);
                        enemy = husk(scene, enemyMark);
                        slain = husk(scene, at.add(-3, 0, 3));
                        other = scene.client(otherMark);
                        fallenId = fallen.getUUID();
                        slainId = slain.getUUID();
                        stage = 1;
                    }
                    case 1 -> {
                        // past the frames a joining player has
                        if (GerMeleeGameTestScene.pastSpawnGrace(other.player())
                                && GerMeleeGameTestScene.pastSpawnGrace(owner.user())) {
                            scene.premise(GerRtzRewindService.setMark(scene.level, owner.user(), MARK_CHUNK_RADIUS)
                                            == GerRtzRewindService.Result.SUCCESS,
                                    "the mark is set");
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        // What happens after the mark. The user: three small hits, poison, a step aside.
                        for (int i = 0; i < 3; i++) {
                            hurt(owner.user(), 10);
                        }
                        poison(owner.user());
                        // a helpful effect gained since the mark stays his, and goes from his side
                        owner.user().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2000, 0));
                        friend.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2000, 0));
                        ownerNow = scene.origin.add(1, 0, 1);
                        owner.user().teleportTo(ownerNow.x, ownerNow.y, ownerNow.z);
                        // His side: a wounded, poisoned cow that walked off, and one that died.
                        hurt(friend, 50);
                        poison(friend);
                        friendNow = friendMark.add(1, 0, 1);
                        friend.teleportTo(friendNow.x, friendNow.y, friendNow.z);
                        hurt(fallen, 1000);
                        // His enemies: one wounded, weakened and moved, and one killed.
                        hurt(enemy, 5);
                        weaken(enemy);
                        enemy.teleportTo(enemyMark.x + 1, enemyMark.y, enemyMark.z + 1);
                        hurt(slain, 1000);
                        // Another player: wounded, weakened, moved, with something picked up.
                        hurt(other.player(), 5);
                        weaken(other.player());
                        other.player().teleportTo(otherMark.x + 1, otherMark.y, otherMark.z + 1);
                        other.player().getInventory().add(new ItemStack(Items.DIRT, 3));
                        scene.premise(owner.user().getHealth() == 170 && owner.user().hasEffect(MobEffects.POISON)
                                        && friend.getHealth() == 150 && !fallen.isAlive() && !slain.isAlive()
                                        && enemy.getHealth() == 15 && other.player().getHealth() == 15,
                                "everyone was hurt after the mark: user " + owner.user().getHealth() + ", friend "
                                        + friend.getHealth() + ", enemy " + enemy.getHealth() + ", player "
                                        + other.player().getHealth());
                        stage = 3;
                    }
                    case 3 -> {
                        // the dead are gone from the level by now
                        if (tick > 110 && scene.level.getEntity(fallenId) == null
                                && scene.level.getEntity(slainId) == null) {
                            // set alight in the tick of the press: his own Requiem would answer a burn before it
                            owner.user().setRemainingFireTicks(200);
                            stamina = owner.power().getStamina();
                            otherHealth = other.player().getHealth();
                            scene.premise(otherHealth < 20 && owner.user().getHealth() <= 170,
                                    "the player and the user are still wounded at the press");
                            GerRtzRewindService.Result result = GerRtzRewindService.rewind(
                                    scene.level, owner.user(), owner.stand(), owner.power(), STAMINA_COST);
                            helper.assertTrue(result == GerRtzRewindService.Result.SUCCESS,
                                    scene.id + ": Return to Zero answered " + result);
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private void verify() {
                Entity risen = scene.level.getEntity(fallenId);
                if (risen != null) {
                    scene.owned.add(risen);
                }
                scene.log("user " + new Body(owner.user()) + "; friend " + new Body(friend) + " locked="
                        + state(friend).returnToZeroDisabled() + "; fallen=" + risen + "; enemy " + new Body(enemy)
                        + " locked=" + state(enemy).returnToZeroDisabled() + "; slain="
                        + scene.level.getEntity(slainId) + "; player " + new Body(other.player()) + " locked="
                        + state(other.player()).returnToZeroDisabled() + "; stamina " + stamina + " -> "
                        + owner.power().getStamina());
                List<String> wrong = new ArrayList<>();
                if (owner.user().getHealth() != 200 || owner.user().hasEffect(MobEffects.POISON)
                        || owner.user().getRemainingFireTicks() > 0 || !near(owner.user(), ownerNow)) {
                    wrong.add("the user did not keep his place with his health back and his poison and fire gone");
                }
                if (!owner.user().hasEffect(MobEffects.MOVEMENT_SPEED) || friend.hasEffect(MobEffects.MOVEMENT_SPEED)
                        || !owner.user().hasEffect(MobEffects.NIGHT_VISION)) {
                    wrong.add("a helpful effect gained since the mark went from the user ("
                            + !owner.user().hasEffect(MobEffects.MOVEMENT_SPEED) + ") or stayed on the friendly cow ("
                            + friend.hasEffect(MobEffects.MOVEMENT_SPEED) + "), or the one the user had at the mark went ("
                            + !owner.user().hasEffect(MobEffects.NIGHT_VISION) + ")");
                }
                if (owner.power().getStamina() != stamina - STAMINA_COST) {
                    wrong.add("the press took " + (stamina - owner.power().getStamina()) + " stamina");
                }
                if (friend.getHealth() != 200 || friend.hasEffect(MobEffects.POISON) || !near(friend, friendNow)) {
                    wrong.add("the friendly cow did not keep its place, " + friendNow
                            + ", with its health back and its poison gone");
                }
                if (!(risen instanceof LivingEntity back) || !back.isAlive() || back.getHealth() != 200
                        || !near(risen, fallenMark)) {
                    wrong.add("the friendly cow that died since the mark did not come back");
                }
                if (enemy.getHealth() != 15 || !enemy.hasEffect(MobEffects.WEAKNESS) || !near(enemy, enemyMark)) {
                    wrong.add("the enemy did not go back to its place, " + enemyMark
                            + ", with its wound and its weakness");
                }
                if (scene.level.getEntity(slainId) != null) {
                    wrong.add("the enemy killed since the mark came back");
                }
                if (other.player().getHealth() != otherHealth || !other.player().hasEffect(MobEffects.WEAKNESS)
                        || !near(other.player(), otherMark)
                        || other.player().getInventory().contains(new ItemStack(Items.DIRT))) {
                    wrong.add("the other player did not go back to his place, " + otherMark + ", with his wound of "
                            + otherHealth + ", his weakness and his old inventory");
                }
                if (!state(enemy).returnToZeroDisabled() || !state(other.player()).returnToZeroDisabled()
                        || state(owner.stand()).returnToZeroDisabled()
                        || state(owner.user()).returnToZeroDisabled()) {
                    wrong.add("the lockdown does not hold the enemy and the other player, or holds the user or his"
                            + " Stand");
                }
                // The owner: "只在歸零瞬間把敵對生物ai暫停0.5s".
                if (MobAiPause.remainingTicks(enemy) != GerRtzRewindService.ENEMY_AI_PAUSE_TICKS
                        || GerRtzRewindService.ENEMY_AI_PAUSE_TICKS != 10
                        || MobAiPause.remainingTicks(friend) != 0) {
                    wrong.add("the enemy's thinking is stopped for " + MobAiPause.remainingTicks(enemy)
                            + " ticks and the friendly cow's for " + MobAiPause.remainingTicks(friend)
                            + ", not 10 and 0");
                }
                helper.assertTrue(wrong.isEmpty(), scene.id + ": " + String.join("; ", wrong));
            }
        }.start();
    }

    /** An enemy is who is after him at the press: a creature that took him as its target is one, whatever its kind. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_rtz_press_sides")
    public static void aCreatureAfterTheUserIsHisEnemyAtThePress(GameTestHelper helper) {
        new Sequence(helper, "rtz/press-sides", 150) {
            @Override
            boolean step() {
                Actor owner = scene.actor();
                Vec3 mark = scene.origin.add(3, 0, 0);
                IronGolem golem = EntityType.IRON_GOLEM.create(scene.level);
                golem.setNoAi(true);
                scene.add(golem, mark);
                scene.premise(GerRtzRewindService.setMark(scene.level, owner.user(), MARK_CHUNK_RADIUS)
                                == GerRtzRewindService.Result.SUCCESS,
                        "the mark is set");
                float full = golem.getHealth();
                hurt(golem, 30);
                golem.teleportTo(mark.x + 1, mark.y, mark.z + 1);
                golem.setTarget(owner.user());
                scene.premise(golem.getTarget() == owner.user() && golem.getHealth() == full - 30,
                        "the golem is after the user, wounded");
                helper.assertTrue(GerRtzRewindService.rewind(scene.level, owner.user(), owner.stand(), owner.power(), 0)
                                == GerRtzRewindService.Result.SUCCESS,
                        scene.id + ": Return to Zero did not run");
                helper.assertTrue(golem.getHealth() == full - 30 && near(golem, mark),
                        scene.id + ": a golem that was after the user was treated as his side: health "
                                + golem.getHealth() + " at " + golem.position());
                return true;
            }
        }.start();
    }

    /**
     * The commit is all or nothing: when a step fails after others were applied, the inverse journal puts every body
     * back as it stood, the stamina comes back, and the mark is still there and still works. The service logs the
     * failed commit as an error, which the test task lets through for the entity id below and nothing else.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_rtz_press_rollback")
    public static void aFailedCommitPutsEverythingBackAndKeepsTheMark(GameTestHelper helper) {
        new Sequence(helper, "rtz/rollback", 250) {
            // the last in the commit's order of entities: the others are applied before it fails
            private final UUID last = new UUID(-1L, -1L);
            private Actor owner;
            private ObservedCow friend;
            private Husk enemy;
            private Vec3 friendMark;
            private Vec3 enemyMark;
            private Vec3 fallenMark;
            private boolean refuseJoin;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        owner = scene.actor();
                        friendMark = scene.origin.add(3, 0, 0);
                        enemyMark = scene.origin.add(-3, 0, 0);
                        fallenMark = scene.origin.add(3, 0, 3);
                        friend = scene.cow(friendMark);
                        enemy = husk(scene, enemyMark);
                        Cow fallen = EntityType.COW.create(scene.level);
                        fallen.setUUID(last);
                        fallen.setNoAi(true);
                        scene.add(fallen, fallenMark);
                        scene.listen(EntityJoinLevelEvent.class, event -> {
                            if (refuseJoin && event.getEntity().getUUID().equals(last)) {
                                event.setCanceled(true);
                            }
                        });
                        stage = 1;
                    }
                    case 1 -> {
                        if (GerMeleeGameTestScene.pastSpawnGrace(owner.user())) {
                            scene.premise(GerRtzRewindService.setMark(scene.level, owner.user(), MARK_CHUNK_RADIUS)
                                            == GerRtzRewindService.Result.SUCCESS,
                                    "the mark is set");
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        hurt(owner.user(), 10);
                        poison(owner.user());
                        hurt(friend, 50);
                        poison(friend);
                        friend.teleportTo(friendMark.x + 1, friendMark.y, friendMark.z + 1);
                        hurt(enemy, 5);
                        weaken(enemy);
                        enemy.teleportTo(enemyMark.x + 1, enemyMark.y, enemyMark.z + 1);
                        if (scene.level.getEntity(last) instanceof LivingEntity fallen) {
                            hurt(fallen, 1000);
                        }
                        stage = 3;
                    }
                    case 3 -> {
                        if (scene.level.getEntity(last) == null) {
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private void verify() {
                Body ownerBefore = new Body(owner.user());
                Body friendBefore = new Body(friend);
                Body enemyBefore = new Body(enemy);
                float stamina = owner.power().getStamina();
                long day = scene.level.getDayTime();
                refuseJoin = true;
                GerRtzRewindService.Result failed = GerRtzRewindService.rewind(
                        scene.level, owner.user(), owner.stand(), owner.power(), STAMINA_COST);
                refuseJoin = false;
                scene.log("failed press: " + failed + "; user " + new Body(owner.user()) + " was " + ownerBefore
                        + "; friend " + new Body(friend) + " was " + friendBefore + "; enemy " + new Body(enemy)
                        + " was " + enemyBefore + "; stamina " + stamina + " -> " + owner.power().getStamina());
                List<String> wrong = new ArrayList<>();
                if (failed != GerRtzRewindService.Result.COMMIT_FAILED) {
                    wrong.add("the press whose commit was made to fail answered " + failed);
                }
                if (!new Body(owner.user()).equals(ownerBefore) || !new Body(friend).equals(friendBefore)
                        || !new Body(enemy).equals(enemyBefore) || scene.level.getEntity(last) != null
                        || scene.level.getDayTime() != day) {
                    wrong.add("a body, the dead cow or the day time is not as before the failed press");
                }
                if (owner.power().getStamina() != stamina) {
                    wrong.add("the failed press kept " + (stamina - owner.power().getStamina()) + " stamina");
                }
                if (!GerRtzRewindService.hasMark(owner.user()) || state(enemy).returnToZeroDisabled()
                        || state(friend).returnToZeroDisabled() || MobAiPause.remainingTicks(enemy) != 0) {
                    wrong.add("the failed press spent the mark, locked someone down or paused the enemy");
                }
                helper.assertTrue(wrong.isEmpty(), scene.id + ": " + String.join("; ", wrong));

                GerRtzRewindService.Result again = GerRtzRewindService.rewind(
                        scene.level, owner.user(), owner.stand(), owner.power(), STAMINA_COST);
                Entity risen = scene.level.getEntity(last);
                if (risen != null) {
                    scene.owned.add(risen);
                }
                helper.assertTrue(again == GerRtzRewindService.Result.SUCCESS && friend.getHealth() == 200
                                && near(enemy, enemyMark) && risen != null && near(risen, fallenMark),
                        scene.id + ": the same mark no longer rewinds after the failed press: " + again
                                + ", friend health " + friend.getHealth() + ", enemy at " + enemy.position()
                                + ", the dead cow " + risen);
            }
        }.start();
    }

    /**
     * The coordinator, R772.1: at the press the user keeps the helpful effects he gained since the mark, Resolve's
     * among them, loses the harmful ones he gained since, and keeps those he had at the mark, harmful or not.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_rtz_press_own_effects")
    public static void theUserKeepsWhatHelpsHimAndLosesWhatHarmedHimSinceTheMark(GameTestHelper helper) {
        new Sequence(helper, "rtz/own-effects", 150) {
            @Override
            boolean step() {
                Actor owner = scene.actor();
                ServerPlayer user = owner.user();
                user.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 4000, 0));
                GerHitAnswerService.applyingOwnEffects(() ->
                        user.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 4000, 0)));
                scene.premise(GerRtzRewindService.setMark(scene.level, user, MARK_CHUNK_RADIUS)
                                == GerRtzRewindService.Result.SUCCESS,
                        "the mark is set");
                poison(user);
                user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2000, 0));
                user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 2000, 0));
                scene.premise(user.hasEffect(MobEffects.POISON) && user.hasEffect(MobEffects.MOVEMENT_SPEED)
                                && user.hasEffect(ModStatusEffects.RESOLVE) && user.hasEffect(MobEffects.WEAKNESS),
                        "the effects are on the user before the press");
                helper.assertTrue(GerRtzRewindService.rewind(scene.level, user, owner.stand(), owner.power(), 0)
                                == GerRtzRewindService.Result.SUCCESS,
                        scene.id + ": Return to Zero did not run");
                helper.assertTrue(!user.hasEffect(MobEffects.POISON) && user.hasEffect(MobEffects.MOVEMENT_SPEED)
                                && user.hasEffect(ModStatusEffects.RESOLVE) && user.hasEffect(MobEffects.NIGHT_VISION)
                                && user.hasEffect(MobEffects.WEAKNESS),
                        scene.id + ": after the press the user has poison " + user.hasEffect(MobEffects.POISON)
                                + " (gained, harmful), speed " + user.hasEffect(MobEffects.MOVEMENT_SPEED)
                                + " and Resolve " + user.hasEffect(ModStatusEffects.RESOLVE)
                                + " (gained, helpful), night vision " + user.hasEffect(MobEffects.NIGHT_VISION)
                                + " and weakness " + user.hasEffect(MobEffects.WEAKNESS) + " (at the mark)");
                return true;
            }
        }.start();
    }

    /**
     * Core 3821a37c, the mode without cooldowns: Return to Zero counts in the fatigue and is no practical skill (the
     * owner). Its answer to a stop of time runs the same action as a press and reaches setCooldownOnUse, so it records a
     * use; a second one while the fatigue of the first runs pays the repeat fee on top of its own price.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_rtz_press_fatigue")
    public static void inTheModeWithoutCooldownsARepeatedReturnToZeroPaysTheRepeatFee(GameTestHelper helper) {
        new Sequence(helper, "rtz/fatigue", 250) {
            private Actor owner;
            private GerReturnToZeroAbility rtz;
            private int stage;

            private float press() {
                float stamina = owner.power().getStamina();
                helper.assertTrue(rtz.tryAutomaticTimeStopResponse(scene.level, owner.user(), owner.power()),
                        scene.id + ": Return to Zero did not run at stage " + stage);
                return stamina - owner.power().getStamina();
            }

            @Override
            boolean step() {
                String name = GoldExperienceRequiemAddon.GER_RETURN_TO_ZERO_NAME;
                switch (stage) {
                    case 0 -> {
                        owner = scene.actor();
                        owner.user().getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                                .noStandAbilityCooldown = true;
                        rtz = (GerReturnToZeroAbility) owner.power().getMoveset().getAbility(name);
                        scene.premise(StandAbilityFatigue.applies(owner.power()) && !rtz.isPracticalSkill(owner.power()),
                                "the user plays without cooldowns, and Return to Zero is no practical skill");
                        scene.premise(GerRtzRewindService.setMark(scene.level, owner.user(), MARK_CHUNK_RADIUS)
                                        == GerRtzRewindService.Result.SUCCESS,
                                "the first mark is set");
                        stage = 1;
                    }
                    case 1 -> {
                        float paid = press();
                        int cooldown = owner.power().getAbilityCooldownShare(GerReturnToZeroAbility.COOLDOWN_TICKS);
                        helper.assertTrue(Math.abs(paid - owner.power().getStaminaCostShare(STAMINA_COST)) < 0.01F
                                        && StandAbilityFatigue.getFatigueTicks(owner.power(), name) == cooldown
                                        && StandAbilityFatigue.getRepeats(owner.power(), name) == 0,
                                scene.id + ": the first answer cost " + paid + " and left "
                                        + StandAbilityFatigue.getFatigueTicks(owner.power(), name)
                                        + " ticks of fatigue, not " + cooldown);
                        stage = 2;
                    }
                    case 2 -> {
                        if (!GerRtzRewindService.hasActivePresentation(owner.user())) {
                            owner.power().setStamina(owner.power().getMaxStamina());
                            scene.premise(GerRtzRewindService.setMark(scene.level, owner.user(), MARK_CHUNK_RADIUS)
                                            == GerRtzRewindService.Result.SUCCESS,
                                    "the second mark is set");
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        float fatigue = StandAbilityFatigue.getFatigueTicks(owner.power(), name);
                        int cooldown = owner.power().getAbilityCooldownShare(GerReturnToZeroAbility.COOLDOWN_TICKS);
                        float fee = StandAbilityFatigue.repeatCost(fatigue, cooldown, 1, false);
                        float paid = press();
                        float expected = owner.power().getStaminaCostShare(STAMINA_COST)
                                + owner.power().getStaminaCostShare(fee);
                        scene.log("fatigue " + fatigue + ", fee " + fee + ", paid " + paid);
                        helper.assertTrue(fatigue > 0 && fee > 0 && Math.abs(paid - expected) < 0.01F
                                        && StandAbilityFatigue.getRepeats(owner.power(), name) == 1,
                                scene.id + ": the repeated answer with " + fatigue + " ticks of fatigue left cost "
                                        + paid + ", not " + expected + " (its price and the first repeat's fee " + fee
                                        + "); repeats " + StandAbilityFatigue.getRepeats(owner.power(), name));
                        return true;
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }
}
