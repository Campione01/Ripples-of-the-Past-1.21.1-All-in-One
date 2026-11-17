package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.GoldExperienceRequiemGameEvents;
import rotp.addons.ger.damage.GerDamageTypes;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Hurt;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.gametest.GerUndieGameTests.LoopWatch;
import rotp.addons.ger.state.GerLivingState;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The owner, 2026-11-08: the death loop, once a killing Finisher Punch has
 * started it, breaks every escape ("應該能破除包括不死圖騰，紅王時刪在內的所有
 * 規避手段，包括無敵或限傷等"), and a Requiem holder is never put in it
 * ("死亡循環對黃鎮持有者不生效").
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerDeathLoopEscapeGameTests {
    private static final String UNDIE = "ger_undie_punch";

    private GerDeathLoopEscapeGameTests() {}

    private static GerLivingState state(net.minecraft.world.entity.LivingEntity entity) {
        return GerUndieGameTests.state(entity);
    }

    /** A killing Finisher Punch on a cow, followed through its loop deaths. */
    private abstract static class Loop extends Sequence {
        Actor actor;
        ObservedCow cow;
        LoopWatch watch;
        private int revivals;
        private int stage;

        Loop(GameTestHelper helper, String id, int deadline) {
            super(helper, id, deadline);
        }

        /** The escape, on a cow of 10 health in front of Requiem. */
        abstract void prepare();

        abstract boolean done();

        abstract void verify();

        @Override
        boolean step() {
            switch (stage) {
                case 0 -> {
                    actor = scene.actor();
                    cow = GerUndieGameTests.markableCow(scene, scene.origin.add(0, 0, 1.5));
                    watch = new LoopWatch(cow);
                    prepare();
                    stage = 1;
                }
                case 1 -> {
                    scene.pressAction(actor, cow, UNDIE, UNDIE, InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                    stage = 2;
                }
                case 2 -> {
                    watch.observe();
                    if (cow.getHealth() > 0 && state(cow).undieDeaths() > revivals) {
                        revivals = state(cow).undieDeaths();
                        watch.bringBack();
                    }
                    if (done()) {
                        verify();
                        return true;
                    }
                }
                default -> throw new IllegalStateException();
            }
            return false;
        }

        boolean diedTwice() {
            return state(cow).undieDeaths() >= 2 && cow.getHealth() > 0;
        }
    }

    /** A totem of undying in hand is never asked, in the loop or at its real last death. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 900, batch = "ger_loop_totem")
    public static void aTotemDoesNotSaveALoopVictim(GameTestHelper helper) {
        new Loop(helper, "loop/totem", 850) {
            private final List<Boolean> deathEvents = new ArrayList<>();
            private int totemsAsked;
            private boolean regenerated;

            @Override
            void prepare() {
                cow.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                scene.listen(LivingUseTotemEvent.class, event -> {
                    if (event.getEntity() == cow) {
                        totemsAsked++;
                    }
                });
                scene.listen(LivingDeathEvent.class, event -> {
                    if (event.getEntity() == cow) {
                        deathEvents.add(event.isCanceled());
                    }
                });
            }

            @Override
            boolean done() {
                regenerated |= cow.hasEffect(MobEffects.REGENERATION);
                helper.assertTrue(totemsAsked == 0 && !regenerated,
                        scene.id + ": the totem saved the cow after " + state(cow).undieDeaths()
                                + " loop deaths");
                return deathEvents.contains(Boolean.FALSE);
            }

            @Override
            void verify() {
                scene.log("deaths=" + state(cow).undieDeaths() + " totemsAsked=" + totemsAsked + " regenerated="
                        + regenerated + " deathEvents=" + deathEvents + " health=" + cow.getHealth());
                helper.assertTrue(state(cow).undieDeaths() == GoldExperienceRequiemGameEvents.MAX_UNDIE_LOOPS,
                        scene.id + ": the loop ran " + state(cow).undieDeaths() + " times");
                helper.assertTrue(totemsAsked == 0 && !regenerated && cow.getHealth() <= 0,
                        scene.id + ": the totem was used (asked " + totemsAsked + " times, regeneration "
                                + regenerated + ") or the cow lives at " + cow.getHealth());
            }
        }.start();
    }

    /**
     * Resistance V, the invulnerable flag and the frames after a hit: the
     * punch's hit and the loop's rekill both land as damage.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_loop_invulnerable")
    public static void resistanceAndInvulnerabilityDoNotSaveALoopVictim(GameTestHelper helper) {
        new Loop(helper, "loop/invulnerable", 350) {
            @Override
            void prepare() {
                cow.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 4000, 4));
                cow.setInvulnerable(true);
            }

            @Override
            boolean done() {
                if (cow.getHealth() > 0) {
                    cow.invulnerableTime = 20;
                    cow.lastHurt = Float.MAX_VALUE;
                }
                return diedTwice();
            }

            @Override
            void verify() {
                // the punch's hit of the cow's 10 health or more, then a rekill of what the revived cow has
                scene.log("hurts=" + cow.hurts);
                helper.assertTrue(cow.hurts.size() >= 2 && cow.hurts.stream().allMatch(Hurt::result)
                                && cow.hurts.get(0).amount() >= 10,
                        scene.id + ": a hit of the loop did not land as damage on the invulnerable cow under"
                                + " Resistance V: " + cow.hurts);
            }
        }.start();
    }

    /** A damage cap from another mod's handler: the death is finished directly. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_loop_cap")
    public static void aDamageCapDoesNotSaveALoopVictim(GameTestHelper helper) {
        new Loop(helper, "loop/cap", 350) {
            private int capped;

            @Override
            void prepare() {
                scene.listen(LivingDamageEvent.Pre.class, event -> {
                    if (event.getEntity() == cow && event.getNewDamage() > 0.5F) {
                        event.setNewDamage(0.5F);
                        capped++;
                    }
                });
            }

            @Override
            boolean done() {
                return diedTwice();
            }

            @Override
            void verify() {
                scene.log("capped=" + capped + " deaths=" + state(cow).undieDeaths());
                scene.premise(capped >= 2, "the cap cut the punch and the rekill");
            }
        }.start();
    }

    /**
     * A hook that cancels every hit on the victim, as King Crimson's reflex
     * and last-chance erase cancel the hit that sets them off.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_loop_cancel")
    public static void aCancelledHitDoesNotSaveALoopVictim(GameTestHelper helper) {
        new Loop(helper, "loop/cancel", 350) {
            private int cancelled;

            @Override
            void prepare() {
                scene.listen(LivingIncomingDamageEvent.class, event -> {
                    if (event.getEntity() == cow && cow.getHealth() > 0) {
                        event.setCanceled(true);
                        cancelled++;
                    }
                });
            }

            @Override
            boolean done() {
                return diedTwice();
            }

            @Override
            void verify() {
                scene.log("cancelled=" + cancelled + " deaths=" + state(cow).undieDeaths() + " hurts=" + cow.hurts);
                scene.premise(cancelled >= 2 && cow.hurts.stream().noneMatch(Hurt::result),
                        "every hit on the cow was cancelled");
            }
        }.start();
    }

    /**
     * A killing Finisher Punch on a Requiem holder, his Stand away, kills him
     * once and for real; and a victim who gains Requiem in the loop leaves it.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_loop_holder")
    public static void aRequiemHolderIsNeverPutInTheLoop(GameTestHelper helper) {
        new Sequence(helper, "loop/holder", 350) {
            private final List<Boolean> deathEvents = new ArrayList<>();
            private Actor actor;
            private Actor holder;
            private Client gainer;
            private boolean looped;
            private int stage;

            @Override
            boolean step() {
                if (holder != null) {
                    looped |= state(holder.user()).undieActive();
                }
                switch (stage) {
                    case 0 -> {
                        boolean pvp = scene.level.getServer().isPvpAllowed();
                        scene.level.getServer().setPvpAllowed(true);
                        scene.onClose(() -> scene.level.getServer().setPvpAllowed(pvp));
                        actor = scene.actor();
                        holder = scene.standUser(scene.origin.add(0, 0, 1.5), 180,
                                GoldExperienceRequiemAddon.GER_STAND.get(), false);
                        holder.user().setNoGravity(true);
                        gainer = GerUndieGameTests.standUser(scene, scene.origin.add(3, 0, 2), false);
                        scene.listen(LivingDeathEvent.class, event -> {
                            if (event.getEntity() == holder.user()) {
                                deathEvents.add(event.isCanceled());
                            }
                        });
                        scene.premise(!GoldExperienceRequiemGameEvents.canEnterUndieLoop(holder.user())
                                        && GoldExperienceRequiemGameEvents.canEnterUndieLoop(gainer.player()),
                                "the holder is one and the other player is not");
                        stage = 1;
                    }
                    case 1 -> {
                        // past the frames a joining player has
                        if (holder.user().tickCount > 60) {
                            holder.user().setHealth(10);
                            state(gainer.player()).armUndie(actor.user().getUUID());
                            scene.premise(gainer.player().hurt(scene.level.damageSources().generic(), 1000),
                                    "the other player took a lethal hit in the loop");
                            scene.pressAction(actor, holder.user(), UNDIE, UNDIE,
                                    InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (gainer.player().getHealth() <= 0 && state(gainer.player()).inUndieDeath()) {
                            StandPower power = StandPower.get(gainer.player());
                            scene.premise(StandPowerTransitions.replace(power,
                                            power.getStandInstance().orElseThrow().getStandId(),
                                            new StandInstance(GoldExperienceRequiemAddon.GER_STAND.get())).applied(),
                                    "the dying player gained Requiem");
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (!deathEvents.isEmpty() && !state(gainer.player()).undieActive()) {
                            scene.log("deathEvents=" + deathEvents + " looped=" + looped + " gainerHealth="
                                    + gainer.player().getHealth());
                            helper.assertTrue(deathEvents.equals(List.of(Boolean.FALSE)) && !looped
                                            && holder.user().getHealth() <= 0,
                                    scene.id + ": the holder was put in the loop: death events " + deathEvents
                                            + ", looped " + looped);
                            helper.assertTrue(gainer.player().getHealth() > 0
                                            && !state(gainer.player()).inUndieDeath()
                                            && state(gainer.player()).undieDeaths() == 0,
                                    scene.id + ": the player who gained Requiem did not leave the loop alive");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** A victim who may no longer be in the loop, here by going Creative, is let out of it on his feet. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_loop_leave")
    public static void aVictimWhoGoesCreativeLeavesTheLoop(GameTestHelper helper) {
        new Sequence(helper, "loop/leave", 250) {
            private Client victim;
            private int changedAt;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        victim = scene.client(scene.origin.add(0, 0, 2));
                        stage = 1;
                    }
                    case 1 -> {
                        if (GerMeleeGameTestScene.pastSpawnGrace(victim.player())) {
                            state(victim.player()).armUndie(UUID.randomUUID());
                            scene.premise(victim.player().hurt(scene.level.damageSources().generic(), 1000),
                                    "the armed player took the lethal hit");
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (state(victim.player()).inUndieDeath()) {
                            victim.player().setGameMode(GameType.CREATIVE);
                            changedAt = tick;
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (tick - changedAt >= 2) {
                            GerLivingState state = state(victim.player());
                            scene.log("active=" + state.undieActive() + " dying=" + state.inUndieDeath() + " health="
                                    + victim.player().getHealth());
                            helper.assertTrue(!state.undieActive() && !state.inUndieDeath()
                                            && victim.player().isAlive(),
                                    scene.id + ": a victim who went Creative is still in the loop: active "
                                            + state.undieActive() + ", dying " + state.inUndieDeath() + ", health "
                                            + victim.player().getHealth());
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * A hit turned back on its attacker, as the core turns a hit on a Gold Experience lifeform: the loop's rekill is
     * nothing to its own caster, and the victim that turned the hits back is in the loop all the same.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_loop_reflect")
    public static void aRekillTurnedBackOnItsCasterIsNothing(GameTestHelper helper) {
        new Loop(helper, "loop/reflect", 350) {
            private final List<String> onCaster = new ArrayList<>();
            private float largestTurnedBack;
            private int turnedBack;

            @Override
            void prepare() {
                // with PvP off no hit by a player reaches a player, his own turned back included
                boolean pvp = scene.level.getServer().isPvpAllowed();
                scene.level.getServer().setPvpAllowed(true);
                scene.onClose(() -> scene.level.getServer().setPvpAllowed(pvp));
                // what EventHandler.redirectDamageFromGoldExperienceLifeforms does for a lifeform, for this cow
                scene.listen(LivingIncomingDamageEvent.class, event -> {
                    if (event.getEntity() == cow && event.getAmount() > 0
                            && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != cow) {
                        turnedBack++;
                        largestTurnedBack = Math.max(largestTurnedBack, event.getAmount());
                        attacker.hurt(event.getSource(), event.getAmount());
                        event.setCanceled(true);
                    }
                });
                scene.listen(LivingDamageEvent.Post.class, event -> {
                    if (event.getEntity() == actor.user() && event.getSource().is(GerDamageTypes.DEATH_LOOP)) {
                        onCaster.add(String.valueOf(event.getNewDamage()));
                    }
                });
            }

            @Override
            boolean done() {
                return diedTwice();
            }

            @Override
            void verify() {
                scene.log("turned back=" + turnedBack + " largest=" + largestTurnedBack + " loop damage on the caster="
                        + onCaster + " caster health=" + actor.user().getHealth());
                scene.premise(turnedBack >= 2, "the punch and a rekill were turned back on the caster");
                helper.assertTrue(onCaster.isEmpty() && actor.user().isAlive(),
                        scene.id + ": the loop's rekill, turned back on its caster, took " + onCaster + " from him");
                // a rekill carries what kills its victim and no more: nothing passes a hit of no end on to anyone
                helper.assertTrue(largestTurnedBack <= 1000,
                        scene.id + ": a hit of " + largestTurnedBack + " was dealt to a cow of 10 health");
            }
        }.start();
    }
}
