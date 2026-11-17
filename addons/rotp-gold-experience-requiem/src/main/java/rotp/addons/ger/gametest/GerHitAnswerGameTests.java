package rotp.addons.ger.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.network.GerAnswerPayloads;
import rotp.addons.ger.state.GerHitAnswerService;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * The owner, 2026-11-08, on Return to Zero's automatic answer: while Requiem
 * is summoned a hit that would leave a lasting harmful effect or take more
 * than 7% of the user's maximum health is returned to zero, the attacker's
 * action with it, for Stand stamina ("只擋會對本體造成持續負面buff或者傷害大於7%
 * 最大生命值的攻擊"). The scene's user has 200 health: the line is at 14.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerHitAnswerGameTests {
    private GerHitAnswerGameTests() {}

    private static void normalDifficulty(GerMeleeGameTestScene scene) {
        Difficulty before = scene.level.getDifficulty();
        scene.level.getServer().setDifficulty(Difficulty.NORMAL, true);
        scene.onClose(() -> scene.level.getServer().setDifficulty(before, true));
    }

    private static Husk husk(GerMeleeGameTestScene scene, Vec3 position) {
        Husk husk = EntityType.HUSK.create(scene.level);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        return scene.add(husk, position);
    }

    /** One hit on the user with his hurt cooldown out of the way. */
    private static boolean hit(Actor actor, DamageSource source, float amount) {
        actor.user().invulnerableTime = 0;
        return actor.user().hurt(source, amount);
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_answer_heavy")
    public static void aHeavyHitIsReturnedToZeroForStaminaAndASmallOneLands(GameTestHelper helper) {
        new Sequence(helper, "answer/heavy", 250) {
            private Actor actor;
            private Husk husk;

            @Override
            boolean step() {
                if (actor == null) {
                    normalDifficulty(scene);
                    actor = scene.actor();
                    husk = husk(scene, scene.origin.add(0, 0, 3));
                }
                if (!GerMeleeGameTestScene.pastSpawnGrace(actor.user())) {
                    return false;
                }
                DamageSource blow = scene.level.damageSources().mobAttack(husk);
                float health = actor.user().getHealth();
                scene.premise(health == 200 && GerHitAnswerService.HEAVY_HIT_SHARE * 200 == 14.0F,
                        "200 health, the line at 14");

                helper.assertTrue(hit(actor, blow, 14) && actor.user().getHealth() == health - 14,
                        scene.id + ": a hit of exactly 7% did not land");
                health -= 14;

                float stamina = actor.power().getStamina();
                Vec3 motion = actor.user().getDeltaMovement();
                boolean hurt = hit(actor, blow, 20);
                scene.log("heavy: hurt=" + hurt + " health=" + actor.user().getHealth() + " stamina "
                        + stamina + " -> " + actor.power().getStamina());
                helper.assertTrue(!hurt && actor.user().getHealth() == health
                                && actor.user().getDeltaMovement().equals(motion),
                        scene.id + ": a hit of 20 reached a user at " + actor.user().getHealth() + " of " + health);
                helper.assertTrue(actor.power().getStamina() == stamina - 20 * GerHitAnswerService.STAMINA_PER_DAMAGE,
                        scene.id + ": stopping 20 damage took " + (stamina - actor.power().getStamina())
                                + " stamina, not 200");
                helper.assertTrue(actor.received().stream().anyMatch(payload ->
                                payload instanceof GerAnswerPayloads.FlashPayload flash && flash.full()
                                        && flash.userId() == actor.user().getId()
                                        && flash.attackerId() == husk.getId()),
                        scene.id + ": the answer was not shown on the user and his attacker");

                // What his armour leaves of a hit is what counts: 20 through 20 armour and 8 toughness is 8.
                actor.user().getAttribute(Attributes.ARMOR).setBaseValue(20);
                actor.user().getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
                float armoured = GerHitAnswerService.damageTaken(actor.user(), blow, 20);
                scene.premise(Math.abs(armoured - 8.0F) < 1.0E-3F, "armour leaves 8 of 20, got " + armoured);
                helper.assertTrue(hit(actor, blow, 20) && Math.abs(actor.user().getHealth() - (health - 8)) < 1.0E-3F,
                        scene.id + ": a hit his armour cuts to 8 did not land");
                health = actor.user().getHealth();
                actor.user().getAttribute(Attributes.ARMOR).setBaseValue(0);
                actor.user().getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);

                // With too little stamina the hit lands, and the bar is not emptied for nothing. Another attacker:
                // what one cause brings in one tick is one answer, already paid above.
                actor.power().setStamina(150);
                DamageSource second = scene.level.damageSources().mobAttack(
                        husk(scene, scene.origin.add(0, 0, -3)));
                helper.assertTrue(hit(actor, second, 20) && actor.user().getHealth() == health - 20
                                && actor.power().getStamina() == 150,
                        scene.id + ": with 150 stamina a hit that costs 200 did not land, or the bar was drained to "
                                + actor.power().getStamina());
                health -= 20;

                // With Requiem away nothing is answered.
                actor.power().setStamina(actor.power().getMaxStamina());
                actor.type().forceUnsummon(actor.user(), actor.power());
                helper.assertTrue(hit(actor, blow, 20) && actor.user().getHealth() == health - 20,
                        scene.id + ": a hit was returned to zero with Requiem away");
                return true;
            }
        }.start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_answer_effect")
    public static void aLastingHarmfulEffectAndFireAreReturnedToZero(GameTestHelper helper) {
        new Sequence(helper, "answer/effect", 250) {
            private Actor actor;
            private Husk husk;
            private float health;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        normalDifficulty(scene);
                        actor = scene.actor();
                        husk = husk(scene, scene.origin.add(0, 0, 3));
                        health = actor.user().getHealth();
                        float stamina = actor.power().getStamina();
                        boolean poisoned = actor.user().addEffect(
                                new MobEffectInstance(MobEffects.POISON, 200, 0), husk);
                        helper.assertTrue(!poisoned && !actor.user().hasEffect(MobEffects.POISON)
                                        && actor.power().getStamina() == stamina - GerHitAnswerService.STAMINA_PER_EFFECT,
                                scene.id + ": the husk's poison took hold, or its answer cost "
                                        + (stamina - actor.power().getStamina()));
                        // the same harm again at once is the same answer
                        actor.user().addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0), husk);
                        helper.assertTrue(!actor.user().hasEffect(MobEffects.POISON)
                                        && actor.power().getStamina() == stamina - GerHitAnswerService.STAMINA_PER_EFFECT,
                                scene.id + ": the same poison a moment later was paid for again or took hold");
                        helper.assertTrue(actor.user().addEffect(
                                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0), husk)
                                        && actor.user().addEffect(
                                                new MobEffectInstance(MobEffects.WITHER, 200, 0), actor.user()),
                                scene.id + ": a helpful effect, or one the user gave himself, was refused");
                        actor.user().removeEffect(MobEffects.WITHER);
                        stage = 1;
                    }
                    case 1 -> {
                        // by then the fire he had before the hit is known: none
                        if (!GerMeleeGameTestScene.pastSpawnGrace(actor.user())) {
                            return false;
                        }
                        float stamina = actor.power().getStamina();
                        actor.user().igniteForTicks(8);
                        boolean hurt = hit(actor, scene.level.damageSources().inFire(), 1);
                        scene.log("fire: hurt=" + hurt + " ticks=" + actor.user().getRemainingFireTicks()
                                + " stamina " + stamina + " -> " + actor.power().getStamina());
                        helper.assertTrue(!hurt && actor.user().getRemainingFireTicks() <= 0
                                        && actor.user().getHealth() == health
                                        && actor.power().getStamina() == stamina - GerHitAnswerService.STAMINA_PER_EFFECT,
                                scene.id + ": fire took hold of the user: " + actor.user().getRemainingFireTicks()
                                        + " ticks, health " + actor.user().getHealth());
                        // a hot floor burns without setting alight: a small hit, which lands
                        helper.assertTrue(hit(actor, scene.level.damageSources().hotFloor(), 1)
                                        && actor.user().getHealth() == health - 1,
                                scene.id + ": a small burn that sets nothing alight was answered");
                        return true;
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** A shot: the arrow is gone and the one who shot it stands again where he shot from. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_answer_attacker")
    public static void theAttackerIsPutBackWhereItsAttackBegan(GameTestHelper helper) {
        new Sequence(helper, "answer/attacker", 250) {
            private Actor actor;
            private Husk husk;
            private Vec3 from;
            private Vec3 to;
            private int movedAt;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        normalDifficulty(scene);
                        actor = scene.actor();
                        from = scene.origin.add(3, 0, 3);
                        to = scene.origin.add(0, 0, 2);
                        husk = husk(scene, from);
                        // a creature that takes him as its target is followed from then on
                        husk.setTarget(actor.user());
                        stage = 1;
                    }
                    case 1 -> {
                        if (GerMeleeGameTestScene.pastSpawnGrace(actor.user())) {
                            husk.teleportTo(to.x, to.y, to.z);
                            movedAt = tick;
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (tick - movedAt == 3) {
                            // drawing a bow: what the attacker is doing is cut with its attack
                            husk.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
                            husk.startUsingItem(InteractionHand.MAIN_HAND);
                            scene.premise(husk.isUsingItem(), "the husk draws a bow");
                            Arrow arrow = new Arrow(scene.level, husk, new ItemStack(Items.ARROW), null);
                            arrow.setPos(actor.user().position().add(0, 1, 1));
                            scene.premise(scene.level.addFreshEntity(arrow), "the arrow flies");
                            scene.owned.add(arrow);
                            // shot five ticks ago, two before the husk moved
                            arrow.tickCount = 5;
                            float health = actor.user().getHealth();
                            boolean hurt = hit(actor, scene.level.damageSources().arrow(arrow, husk), 20);
                            scene.log("hurt=" + hurt + " arrowRemoved=" + arrow.isRemoved() + " husk at "
                                    + husk.position() + " from " + from);
                            helper.assertTrue(!hurt && actor.user().getHealth() == health,
                                    scene.id + ": the arrow's 20 damage landed");
                            helper.assertTrue(arrow.isRemoved(), scene.id + ": the arrow is still there");
                            helper.assertTrue(!husk.isUsingItem(), scene.id + ": the husk still draws its bow");
                            helper.assertTrue(husk.position().distanceTo(from) < 1.0E-3,
                                    scene.id + ": the husk stands at " + husk.position() + ", not where it shot from, "
                                            + from);
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** Requiem against Requiem: the struck one answers, the striker's punch is cut, and nothing answers the answer. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_answer_requiems")
    public static void twoRequiemsDoNotAnswerEachOtherInALoop(GameTestHelper helper) {
        new Sequence(helper, "answer/requiems", 250) {
            private Actor striker;
            private Actor struck;
            private EntityActionInstance punch;
            private float strikerStamina;
            private float struckStamina;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        boolean pvp = scene.level.getServer().isPvpAllowed();
                        scene.level.getServer().setPvpAllowed(true);
                        scene.onClose(() -> scene.level.getServer().setPvpAllowed(pvp));
                        striker = scene.actor();
                        struck = scene.actor(scene.origin.add(0, 0, 2), 180, null);
                        stage = 1;
                    }
                    case 1 -> {
                        if (struck.user().tickCount > 60) {
                            punch = scene.pressAction(striker, struck.user(), "ger_stand_heavy_punch",
                                    "ger_stand_heavy_punch", InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                            strikerStamina = striker.power().getStamina();
                            struckStamina = struck.power().getStamina();
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (punch.punchedTarget != null || striker.stand().getCurStandAction() != punch) {
                            scene.log("struck health=" + struck.user().getHealth() + " stamina " + struckStamina
                                    + " -> " + struck.power().getStamina() + "; striker health="
                                    + striker.user().getHealth() + " stamina " + strikerStamina + " -> "
                                    + striker.power().getStamina() + " phase=" + punch.getPhase());
                            helper.assertTrue(struck.user().getHealth() == 200
                                            && struck.power().getStamina() < struckStamina - 150,
                                    scene.id + ": the struck Requiem did not return the punch to zero for stamina");
                            helper.assertTrue(striker.user().getHealth() == 200
                                            && striker.power().getStamina() > strikerStamina - 150,
                                    scene.id + ": the striker was hurt or paid for an answer of his own");
                            helper.assertTrue(punch.getPhase() == ActionPhase.RECOVERY
                                            || striker.stand().getCurStandAction() != punch,
                                    scene.id + ": the striker's punch runs on in " + punch.getPhase());
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }
}
