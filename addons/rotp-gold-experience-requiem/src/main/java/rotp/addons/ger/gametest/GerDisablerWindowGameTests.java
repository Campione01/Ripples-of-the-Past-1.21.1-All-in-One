package rotp.addons.ger.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.ability.GerDisablerAbility;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.network.GerAnswerPayloads;
import rotp.addons.ger.state.GerDisabledAttacks;
import rotp.addons.ger.state.GerHitAnswerService;
import rotp.addons.ger.state.GerLivingState;
import rotp.addons.ger.state.GerRtzRewindService;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * The owner, 2026-11-08, on the Disabler: it never touches its user's own
 * power, its user cannot be touched for its 9 seconds, and a disabled enemy
 * creature makes no special attack.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerDisablerWindowGameTests {
    private static final int MARK_CHUNK_RADIUS = 8;

    private GerDisablerWindowGameTests() {}

    private static GerLivingState state(net.minecraft.world.entity.LivingEntity entity) {
        return GoldExperienceRequiemAddon.livingState(entity);
    }

    /** Presses the Disabler and reports once its victims are held. */
    private abstract static class Disabled extends Sequence {
        Actor actor;
        ObservedCow cow;
        private int stage;

        Disabled(GameTestHelper helper, String id, int deadline) {
            super(helper, id, deadline);
        }

        void prepare() {}

        /** @return true once every check has passed; called each tick from the one the cow is disabled in */
        abstract boolean disabled();

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = scene.cow(scene.origin.add(0, 0, 4));
                    prepare();
                    stage = 1;
                }
                case 1 -> {
                    if (!GerMeleeGameTestScene.pastSpawnGrace(actor.user())) {
                        return false;
                    }
                    scene.pressAction(actor, null, "ger_disabler", "ger_disabler",
                            InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                    AbilityInput.keyRelease(GerMeleeGameTestScene.SPECIAL, actor.user());
                    stage = 2;
                }
                case 2 -> {
                    if (state(cow).disablerActive()) {
                        stage = 3;
                        return disabled();
                    }
                }
                case 3 -> {
                    return disabled();
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }
    }

    /** Neither the Disabler nor the rewind's lockdown holds the user's own Stand. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_disabler_own_stand")
    public static void theDisablerAndTheLockdownSpareTheUsersOwnStand(GameTestHelper helper) {
        new Disabled(helper, "disabler/own-stand", 250) {
            private EntityActionInstance punch;
            private int since;

            @Override
            boolean disabled() {
                if (since++ == 0) {
                    helper.assertTrue(!state(actor.stand()).disablerActive()
                                    && !state(actor.user()).disablerActive(),
                            scene.id + ": the Disabler holds its own user or his Stand");
                    return false;
                }
                // The Stand acts inside its own Disabler: a held one has every action cut each tick.
                if (punch == null) {
                    if (actor.stand().getCurStandAction() == null) {
                        punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch", "ger_stand_heavy_punch",
                                InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                    }
                    return false;
                }
                if (punch.punchedTarget == null) {
                    helper.assertTrue(actor.stand().getCurStandAction() == punch,
                            scene.id + ": the user's own Stand was cut off inside his Disabler");
                    return false;
                }
                scene.premise(state(cow).disablerActive(), "the cow is still disabled");
                scene.premise(GerRtzRewindService.setMark(scene.level, actor.user(), MARK_CHUNK_RADIUS)
                                == GerRtzRewindService.Result.SUCCESS,
                        "the mark is set");
                scene.premise(GerRtzRewindService.rewind(scene.level, actor.user(), actor.stand(), actor.power(), 0)
                                == GerRtzRewindService.Result.SUCCESS,
                        "Return to Zero ran");
                helper.assertTrue(state(cow).returnToZeroDisabled()
                                && !state(actor.stand()).returnToZeroDisabled()
                                && !state(actor.user()).returnToZeroDisabled(),
                        scene.id + ": the rewind's lockdown holds the user's own Stand, or not the cow");
                return true;
            }
        }.start();
    }

    /** Every hit on the user is returned to zero for the 9 seconds, free, and he glows gold for them. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 500, batch = "ger_disabler_untouchable")
    public static void theDisablersUserIsUntouchableForNineSeconds(GameTestHelper helper) {
        new Disabled(helper, "disabler/untouchable", 450) {
            private Husk husk;
            private float health;
            private int since;
            private int untouchableTicks;

            @Override
            void prepare() {
                husk = scene.add(mob(EntityType.HUSK.create(scene.level)), scene.origin.add(2, 0, 2));
            }

            @Override
            boolean disabled() {
                boolean untouchable = state(actor.user()).untouchable();
                if (since++ == 0) {
                    health = actor.user().getHealth();
                    scene.premise(untouchable, "the window began with the Disabler");
                    // away, so that only the window answers: with Requiem out the paid rule would take over
                    actor.type().forceUnsummon(actor.user(), actor.power());
                }
                if (untouchable) {
                    untouchableTicks++;
                    float stamina = actor.power().getStamina();
                    actor.user().invulnerableTime = 0;
                    boolean hurt = actor.user().hurt(scene.level.damageSources().mobAttack(husk), 3);
                    actor.user().invulnerableTime = 0;
                    hurt |= actor.user().hurt(scene.level.damageSources().fall(), 60);
                    actor.user().invulnerableTime = 0;
                    actor.user().igniteForTicks(15);
                    hurt |= actor.user().hurt(scene.level.damageSources().lava(), 4);
                    boolean poisoned = actor.user().addEffect(
                            new MobEffectInstance(MobEffects.POISON, 200, 0), husk);
                    helper.assertTrue(!hurt && actor.user().getHealth() == health
                                    && !poisoned && !actor.user().hasEffect(MobEffects.POISON)
                                    && actor.user().getRemainingFireTicks() <= 0
                                    && actor.power().getStamina() >= stamina,
                            scene.id + ": in the window a hit reached the user: hurt " + hurt + ", health "
                                    + actor.user().getHealth() + " of " + health + ", poisoned " + poisoned
                                    + ", fire " + actor.user().getRemainingFireTicks() + ", stamina "
                                    + actor.power().getStamina() + " of " + stamina);
                    return false;
                }
                scene.log("untouchable for " + untouchableTicks + " ticks");
                // The Stand performs and its user ticks in one server tick, in either order: a tick of slack.
                helper.assertTrue(untouchableTicks >= GerHitAnswerService.UNTOUCHABLE_TICKS - 2
                                && untouchableTicks <= GerHitAnswerService.UNTOUCHABLE_TICKS
                                && GerHitAnswerService.UNTOUCHABLE_TICKS == GerDisablerAbility.LEGACY_DURATION_TICKS,
                        scene.id + ": the window lasted " + untouchableTicks + " ticks, not the Disabler's "
                                + GerDisablerAbility.LEGACY_DURATION_TICKS);
                actor.user().invulnerableTime = 0;
                helper.assertTrue(actor.user().hurt(scene.level.damageSources().generic(), 3)
                                && actor.user().getHealth() == health - 3,
                        scene.id + ": after the window a hit still did not land");
                List<GerAnswerPayloads.GlowPayload> glows = actor.received().stream()
                        .filter(GerAnswerPayloads.GlowPayload.class::isInstance)
                        .map(GerAnswerPayloads.GlowPayload.class::cast)
                        .filter(glow -> glow.entityId() == actor.user().getId())
                        .toList();
                boolean lightFlash = actor.received().stream()
                        .anyMatch(payload -> payload instanceof GerAnswerPayloads.FlashPayload flash
                                && flash.userId() == actor.user().getId() && !flash.full());
                helper.assertTrue(!glows.isEmpty()
                                && glows.get(0).ticks() == GerHitAnswerService.UNTOUCHABLE_TICKS
                                && glows.get(glows.size() - 1).ticks() == 0 && lightFlash,
                        scene.id + ": the clients were not told the glow from its start to its end, or no flash: "
                                + glows);
                return true;
            }
        }.start();
    }

    /** What a disabled enemy shoots, summons or blows up; its fist and its arrows stay. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_disabler_special")
    public static void aDisabledEnemyMakesNoSpecialAttack(GameTestHelper helper) {
        new Disabled(helper, "disabler/special", 250) {
            private Husk husk;
            private Blaze blaze;
            private Creeper creeper;
            private SnowGolem golem;
            private int since;

            @Override
            void prepare() {
                husk = scene.add(mob(EntityType.HUSK.create(scene.level)), scene.origin.add(2, 0, 2));
                blaze = scene.add(mob(EntityType.BLAZE.create(scene.level)), scene.origin.add(-2, 0, 2));
                creeper = scene.add(mob(EntityType.CREEPER.create(scene.level)), scene.origin.add(3, 0, -2));
                golem = scene.add(mob(EntityType.SNOW_GOLEM.create(scene.level)), scene.origin.add(-3, 0, -2));
            }

            @Override
            boolean disabled() {
                if (since++ == 0) {
                    scene.premise(GerDisabledAttacks.isDisabledEnemy(husk)
                                    && GerDisabledAttacks.isDisabledEnemy(blaze)
                                    && GerDisabledAttacks.isDisabledEnemy(creeper)
                                    && state(golem).disablerActive()
                                    && !GerDisabledAttacks.isDisabledEnemy(golem),
                            "the monsters are disabled enemies and the golem a disabled creature");
                    Vec3 aim = new Vec3(0, 0, -0.1);
                    SmallFireball fireball = new SmallFireball(scene.level, blaze, aim);
                    fireball.setPos(blaze.position());
                    Arrow arrow = new Arrow(scene.level, husk, new ItemStack(Items.ARROW), null);
                    Snowball snowball = new Snowball(scene.level, golem);
                    boolean fireballJoined = scene.level.addFreshEntity(fireball);
                    boolean arrowJoined = scene.level.addFreshEntity(arrow);
                    boolean snowballJoined = scene.level.addFreshEntity(snowball);
                    scene.owned.addAll(List.of(fireball, arrow, snowball));
                    helper.assertTrue(!fireballJoined && scene.level.getEntity(fireball.getUUID()) == null,
                            scene.id + ": a disabled blaze's fireball flew");
                    helper.assertTrue(arrowJoined && snowballJoined,
                            scene.id + ": an arrow, or what a creature that is no enemy throws, was taken out");

                    int hurts = cow.hurts.size();
                    scene.level.explode(creeper, cow.getX(), cow.getY(), cow.getZ(), 3.0F,
                            Level.ExplosionInteraction.NONE);
                    helper.assertTrue(cow.hurts.size() == hurts,
                            scene.id + ": a disabled creeper's explosion went off");
                    helper.assertTrue(!cow.hurt(scene.level.damageSources().sonicBoom(husk), 10),
                            scene.id + ": a disabled enemy's harm that is no blow of its body landed");
                    cow.invulnerableTime = 0;
                    helper.assertTrue(cow.hurt(scene.level.damageSources().mobAttack(husk), 3),
                            scene.id + ": a disabled enemy's plain melee did not land");
                    creeper.ignite();
                    return false;
                }
                // a lit creeper explodes after 30 ticks
                if (since < 45) {
                    return false;
                }
                scene.log("creeper swelling=" + creeper.getSwelling(1.0F));
                helper.assertTrue(creeper.isAlive(), scene.id + ": the disabled creeper blew up");
                return true;
            }
        }.start();
    }

    private static <T extends Mob> T mob(T mob) {
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        return mob;
    }
}
