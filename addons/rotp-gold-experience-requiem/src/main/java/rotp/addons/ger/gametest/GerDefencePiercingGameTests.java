package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.damage.GerDamageTypes;
import rotp.addons.ger.damage.GerUnavoidableDamage;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.entity.HamonMasterEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.entity.StandUserGuard;

/**
 * Requiem against the core's own defences, on real damage. The owner, 2026-11-08: "黃鎮自身其他傷害以及持有者召喚黃鎮
 * 期間的近戰傷害也無視敵方格擋或無敵幀以及無敵機制和限傷等". What lessens a hit as armour does (a Stand's resistance by
 * its durability, its user's Resolve) is passed by true damage only: the Finisher Punch's hit and the death loop's.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerDefencePiercingGameTests {
    private static final float EPS = 1.0E-3F;

    private GerDefencePiercingGameTests() {}

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < EPS;
    }

    /** A Star Platinum user who faces south, his Stand idle half a block before him: toward the scene's own actor. */
    private static final class Defender {
        final GerMeleeGameTestScene scene;
        final Actor actor;
        final Vec3 at;

        Defender(GerMeleeGameTestScene scene, Vec3 at) {
            this.scene = scene;
            this.at = at;
            actor = scene.standUser(at, 0, ModStands.STAR_PLATINUM.get(), true);
            reset();
        }

        void reset() {
            LivingComponentAction.getComponent(actor.stand()).setAction(null, actor.user(), SyncType.NO_SYNC);
            actor.power().resolveCounter.resetResolveValue(actor.power());
            actor.user().setHealth(actor.user().getMaxHealth());
            actor.user().invulnerableTime = 0;
            actor.user().lastHurt = 0;
            actor.user().setDeltaMovement(Vec3.ZERO);
            actor.user().moveTo(at.x, at.y, at.z, 0, 0);
            actor.user().setYHeadRot(0);
            actor.stand().invulnerableTime = 0;
            face();
        }

        private void face() {
            actor.stand().moveTo(at.x, at.y, at.z + 0.5, 0, 0);
            actor.stand().setYHeadRot(0);
            actor.stand().yRotO = 0;
            actor.stand().yHeadRotO = 0;
        }

        /** The Stand's guard, held as a user holds it. */
        void holdGuard() {
            Ability ability = actor.power().getAbility("guard");
            scene.premise(ability instanceof EntityActionType, "Star Platinum has its guard");
            EntityActionType guard = (EntityActionType) ability;
            EntityActionInstance action = guard.createActionObj();
            guard.initActionFromConfig(action, scene.level, actor.user(), actor.stand());
            action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
            action.phasesLength.put(ActionPhase.WINDUP, 0F);
            action.phasesLength.put(ActionPhase.PERFORM, 200F);
            action.setStartingPhase();
            LivingComponentAction.getComponent(actor.stand()).setAction(action, actor.user(), SyncType.NO_SYNC);
            actor.power().setStamina(actor.power().getMaxStamina());
            face();
            scene.premise(actor.stand().isStandBlocking() && actor.stand().isFollowingUser(),
                    "the held guard is blocking");
        }

        void fullResolve() {
            actor.power().resolveCounter.setResolveValue(actor.power(), Float.MAX_VALUE);
        }

        /** What a hit takes from the user's health, on him or passed on from his Stand's body. */
        float taken(Runnable hit) {
            float before = actor.user().getHealth();
            hit.run();
            return before - actor.user().getHealth();
        }

        /** What the Stand's resistance by its durability leaves of a hit it does not block. */
        float afterDurability(float amount) {
            return amount * (1 - StandStatFormulas.getPhysicalResistance(
                    actor.stand().getDurability(), actor.stand().getAttackDamage(), 0, amount));
        }
    }

    private static void allowPvp(GerMeleeGameTestScene scene) {
        boolean pvp = scene.level.getServer().isPvpAllowed();
        scene.level.getServer().setPvpAllowed(true);
        scene.onClose(() -> scene.level.getServer().setPvpAllowed(pvp));
    }

    /** The holder's own fist goes through a held Stand guard while his Requiem is out, and is cut by it once it is recalled. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_piercing_guard")
    public static void theHoldersFistPassesAHeldGuardOnlyWhileHisRequiemIsOut(GameTestHelper helper) {
        new Sequence(helper, "piercing/guard", 150) {
            private Actor holder;
            private Defender defender;

            @Override
            boolean step() {
                if (holder == null) {
                    allowPvp(scene);
                    holder = scene.actor();
                    defender = new Defender(scene, scene.origin.add(0, 0, -2));
                }
                if (!GerMeleeGameTestScene.pastSpawnGrace(defender.actor.user())) {
                    return false;
                }
                float durability = (float) defender.actor.stand().getDurability();
                float raw = durability / 2 + 3;
                DamageSource fist = scene.level.damageSources().playerAttack(holder.user());

                defender.reset();
                float unguarded = defender.taken(() -> defender.actor.user().hurt(fist, raw));
                defender.reset();
                defender.holdGuard();
                float out = defender.taken(() -> defender.actor.user().hurt(fist, raw));

                holder.type().forceUnsummon(holder.user(), holder.power());
                defender.reset();
                defender.holdGuard();
                float recalled = defender.taken(() -> defender.actor.user().hurt(fist, raw));

                float guarded = StandUserGuard.userHitAfterGuard(raw, durability);
                scene.log("fist of " + raw + ": unguarded=" + unguarded + ", through the guard with Requiem out=" + out
                        + ", with Requiem recalled=" + recalled + " (the guard leaves " + guarded + ")");
                scene.premise(unguarded > guarded + 1, "the guard would cut the hit by more than 1");
                helper.assertTrue(close(out, unguarded),
                        scene.id + ": through a held guard the fist of a holder whose Requiem is out took " + out
                                + ", not the unguarded hit's " + unguarded);
                helper.assertTrue(close(recalled, guarded),
                        scene.id + ": with Requiem recalled the holder's fist took " + recalled
                                + " through a held guard, not the guarded hit's " + guarded);
                return true;
            }
        }.start();
    }

    /** Inside the frames a hit leaves, the holder's fist lands again while his Requiem is out, and not once it is recalled. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_piercing_frames")
    public static void theHoldersFistPassesTheFramesAfterAHitOnlyWhileHisRequiemIsOut(GameTestHelper helper) {
        new Sequence(helper, "piercing/frames", 150) {
            @Override
            boolean step() {
                Actor holder = scene.actor();
                ObservedCow cow = scene.cow(scene.origin.add(0, 0, 2));
                DamageSource fist = scene.level.damageSources().playerAttack(holder.user());
                float health = cow.getHealth();
                cow.hurt(fist, 4);
                float first = health - cow.getHealth();
                boolean cooling = cow.invulnerableTime > 10;
                cow.hurt(fist, 4);
                float again = health - first - cow.getHealth();

                holder.type().forceUnsummon(holder.user(), holder.power());
                cow.invulnerableTime = 20;
                cow.lastHurt = 4;
                float before = cow.getHealth();
                cow.hurt(fist, 4);
                float recalled = before - cow.getHealth();
                scene.log("first=" + first + " cooling=" + cooling + " again=" + again + " recalled=" + recalled);
                scene.premise(first == 4 && cooling, "the first hit of 4 landed and left its frames");
                helper.assertTrue(again == 4,
                        scene.id + ": inside the frames the fist of a holder whose Requiem is out took " + again
                                + ", not its 4");
                helper.assertTrue(recalled == 0,
                        scene.id + ": with Requiem recalled the fist took " + recalled + " inside the frames");
                return true;
            }
        }.start();
    }

    /**
     * A Stand's resistance by its durability and its user's Resolve still lessen Requiem's ordinary punch and its
     * holder's fist; the Finisher Punch's true damage and the death loop's are whole.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_piercing_mitigation")
    public static void durabilityAndResolveLessenAnOrdinaryHitAndNotTheLoops(GameTestHelper helper) {
        new Sequence(helper, "piercing/mitigation", 150) {
            private Actor holder;
            private Defender defender;

            @Override
            boolean step() {
                if (holder == null) {
                    allowPvp(scene);
                    holder = scene.actor();
                    defender = new Defender(scene, scene.origin.add(0, 0, -2));
                }
                if (!GerMeleeGameTestScene.pastSpawnGrace(defender.actor.user())) {
                    return false;
                }
                Actor victim = defender.actor;
                // what every punch of the Stand passes to StandEntity.hurtWithStandAttack
                DamageSource punch = scene.level.damageSources().source(
                        ModDamageTypes.STAND_ATTACK, holder.stand(), holder.user());
                DamageSource finisher = GerDamageTypes.source(
                        scene.level, GerDamageTypes.REQUIEM_TRUE, holder.stand(), holder.user());
                DamageSource loop = GerDamageTypes.source(
                        scene.level, GerDamageTypes.DEATH_LOOP, holder.stand(), holder.user());
                DamageSource fist = scene.level.damageSources().playerAttack(holder.user());
                List<String> wrong = new ArrayList<>();

                // On the Stand's body: its resistance by durability.
                float body = 6;
                float resisted = defender.afterDurability(body);
                defender.reset();
                float bodyPunch = defender.taken(() -> holder.stand().hurtWithStandAttack(victim.stand(), punch, body));
                defender.reset();
                float bodyFinisher = defender.taken(() -> GerUnavoidableDamage.deal(victim.stand(), finisher, body));
                defender.reset();
                float bodyLoop = defender.taken(() -> GerUnavoidableDamage.deal(victim.stand(), loop, body));
                scene.log("on the Stand's body, of " + body + ": punch=" + bodyPunch + " (durability leaves " + resisted
                        + "), Finisher Punch=" + bodyFinisher + ", loop=" + bodyLoop);
                scene.premise(resisted < body - 1, "the Stand's durability would cut the hit by more than 1");
                // Only a Stand touches a Stand's body: the core asks no attacker rule there.
                defender.reset();
                float bodyFist = defender.taken(() -> victim.stand().hurt(fist, body));
                scene.log("the holder's fist on the Stand's body, of " + body + ": " + bodyFist);
                if (bodyFist != 0) {
                    wrong.add("the holder's fist took " + bodyFist + " through a Stand's body");
                }
                if (!close(bodyPunch, resisted)) {
                    wrong.add("an ordinary punch on the Stand's body took " + bodyPunch + ", not the " + resisted
                            + " its durability leaves");
                }
                if (!close(bodyFinisher, body) || !close(bodyLoop, body)) {
                    wrong.add("of " + body + " true damage on the Stand's body the Finisher Punch took " + bodyFinisher
                            + " and the loop " + bodyLoop);
                }

                // On the user: his Resolve.
                float hit = 4;
                defender.reset();
                defender.fullResolve();
                float userPunch = defender.taken(() -> holder.stand().hurtWithStandAttack(victim.user(), punch, hit));
                defender.reset();
                defender.fullResolve();
                float userFist = defender.taken(() -> victim.user().hurt(fist, hit));
                defender.reset();
                defender.fullResolve();
                float userFinisher = defender.taken(() -> GerUnavoidableDamage.deal(victim.user(), finisher, hit));
                defender.reset();
                defender.fullResolve();
                float userLoop = defender.taken(() -> GerUnavoidableDamage.deal(victim.user(), loop, hit));
                scene.log("on a user at full Resolve, of " + hit + ": punch=" + userPunch + ", the holder's fist="
                        + userFist + ", Finisher Punch=" + userFinisher + ", loop=" + userLoop);
                if (!(userPunch < hit - 1) || !(userFist < hit - 1) || userPunch <= 0 || userFist <= 0) {
                    wrong.add("at full Resolve an ordinary punch took " + userPunch + " and the holder's fist "
                            + userFist + " of " + hit + ": Resolve did not lessen them");
                }
                if (!close(userFinisher, hit) || !close(userLoop, hit)) {
                    wrong.add("of " + hit + " true damage at full Resolve the Finisher Punch took " + userFinisher
                            + " and the loop " + userLoop);
                }
                helper.assertTrue(wrong.isEmpty(), scene.id + ": " + String.join("; ", wrong));
                return true;
            }
        }.start();
    }

    /**
     * Vanilla's own invulnerable states, which it opens to a type in minecraft:bypasses_invulnerability and to no
     * other: a Wither in its spawn phase and a player in his first ticks after joining take Requiem's ordinary punch.
     * The holder's fist is a vanilla type, which cannot carry that tag: it stops at both.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_piercing_vanilla_states")
    public static void anOrdinaryPunchPassesVanillasInvulnerableStatesByItsTag(GameTestHelper helper) {
        new Sequence(helper, "piercing/vanilla-states", 150) {
            @Override
            boolean step() {
                allowPvp(scene);
                Actor holder = scene.actor();
                DamageSource punch = scene.level.damageSources().source(
                        ModDamageTypes.STAND_ATTACK, holder.stand(), holder.user());
                DamageSource fist = scene.level.damageSources().playerAttack(holder.user());

                WitherBoss wither = EntityType.WITHER.create(scene.level);
                wither.setNoAi(true);
                scene.add(wither, scene.origin.add(0, 0, 4));
                wither.makeInvulnerable();
                scene.premise(wither.getInvulnerableTicks() > 0, "the Wither is in its spawn phase");
                float full = wither.getHealth();
                // the core's own Stand attack type, as any other Stand deals it
                boolean others = wither.hurt(punch, 6);
                boolean fisted = wither.hurt(fist, 6);
                float untouched = wither.getHealth();
                wither.invulnerableTime = 0;
                holder.stand().hurtWithStandAttack(wither, punch, 6);
                float witherLost = untouched - wither.getHealth();

                Client joined = scene.client(scene.origin.add(3, 0, 0));
                scene.premise(!GerMeleeGameTestScene.pastSpawnGrace(joined.player()), "the player has just joined");
                float health = joined.player().getHealth();
                boolean fistedPlayer = joined.player().hurt(fist, 5);
                float afterFist = joined.player().getHealth();
                holder.stand().hurtWithStandAttack(joined.player(), punch, 5);
                float playerLost = afterFist - joined.player().getHealth();
                scene.log("Wither in its spawn phase: another Stand's punch=" + others + " the holder's fist=" + fisted
                        + " Requiem's punch of 6 took " + witherLost + "; a player who just joined: the holder's fist="
                        + fistedPlayer + " Requiem's punch of 5 took " + playerLost);
                scene.premise(!others && !fisted && untouched == full && !fistedPlayer && afterFist == health,
                        "vanilla holds both states against a type outside its tag");
                helper.assertTrue(witherLost > 1,
                        scene.id + ": Requiem's punch of 6 took " + witherLost + " from a Wither in its spawn phase");
                helper.assertTrue(close(playerLost, 5),
                        scene.id + ": Requiem's punch of 5 took " + playerLost + " from a player who just joined");
                return true;
            }
        }.start();
    }

    /**
     * Hamon Protection is a guard in the core: a Hamon Master's, which takes the whole hit, and the one a Hamon player
     * switches on. Neither holds Requiem's punch, nor its holder's fist while his Requiem is out.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_piercing_hamon")
    public static void theHamonGuardHoldsNeitherRequiemsPunchNorItsHoldersFist(GameTestHelper helper) {
        new Sequence(helper, "piercing/hamon", 150) {
            private Actor holder;
            private HamonMasterEntity master;
            private Client guarded;
            private HamonData hamon;

            @Override
            boolean step() {
                if (holder == null) {
                    allowPvp(scene);
                    holder = scene.actor();
                    master = ModEntityTypes.HAMON_MASTER.get().create(scene.level);
                    master.setNoAi(true);
                    master.setNoGravity(true);
                    scene.add(master, scene.origin.add(3, 0, 2));
                    master.addMasterHamon();
                    guarded = scene.client(scene.origin.add(-3, 0, 2));
                    PowerClass.PLAYER_POWER.attachGet(guarded.player()).setPowerType(ModPlayerPowers.HAMON.get());
                    hamon = PlayerPower.getPowerData(guarded.player(), ModPlayerPowers.HAMON).orElseThrow();
                    hamon.setBreathStability(hamon.getMaxBreathStability());
                    hamon.toggleHamonProtection();
                    scene.premise(hamon.isProtectionEnabled(), "the Hamon player's Protection is on");
                }
                if (!GerMeleeGameTestScene.pastSpawnGrace(guarded.player())) {
                    return false;
                }
                float hit = 4;
                scene.log("after the wait: Protection on=" + hamon.isProtectionEnabled() + " energy=" + hamon.getEnergy()
                        + " of " + hamon.getMaxEnergy() + " breath=" + hamon.getBreathStability());
                DamageSource punch = scene.level.damageSources().source(
                        ModDamageTypes.STAND_ATTACK, holder.stand(), holder.user());
                DamageSource fist = scene.level.damageSources().playerAttack(holder.user());
                float masterFist = onMaster(() -> master.hurt(fist, hit));
                float masterPunch = onMaster(() -> holder.stand().hurtWithStandAttack(master, punch, hit));
                float playerFist = onPlayer(() -> guarded.player().hurt(fist, hit));
                float playerPunch = onPlayer(() -> holder.stand().hurtWithStandAttack(guarded.player(), punch, hit));
                holder.type().forceUnsummon(holder.user(), holder.power());
                float masterRecalled = onMaster(() -> master.hurt(fist, hit));
                float playerRecalled = onPlayer(() -> guarded.player().hurt(fist, hit));
                scene.log("of " + hit + ", a Hamon Master took: the fist=" + masterFist + " the punch=" + masterPunch
                        + " the fist with Requiem recalled=" + masterRecalled + "; a Hamon player under Protection: the"
                        + " fist=" + playerFist + " the punch=" + playerPunch + " the fist with Requiem recalled="
                        + playerRecalled);
                scene.premise(masterRecalled == 0 && playerRecalled > 0 && playerRecalled < hit - 1,
                        "the Hamon guard holds a plain fist: all of it on a Master, part of it on a player");
                helper.assertTrue(close(masterFist, hit) && close(masterPunch, hit),
                        scene.id + ": of " + hit + " a Hamon Master took " + masterFist + " from the holder's fist and "
                                + masterPunch + " from Requiem's punch");
                helper.assertTrue(close(playerFist, hit) && close(playerPunch, hit),
                        scene.id + ": of " + hit + " a Hamon player under Protection took " + playerFist
                                + " from the holder's fist and " + playerPunch + " from Requiem's punch");
                return true;
            }

            private float onMaster(Runnable blow) {
                master.setHealth(master.getMaxHealth());
                master.invulnerableTime = 0;
                master.lastHurt = 0;
                float before = master.getHealth();
                blow.run();
                return before - master.getHealth();
            }

            private float onPlayer(Runnable blow) {
                guarded.player().setHealth(guarded.player().getMaxHealth());
                guarded.player().invulnerableTime = 0;
                guarded.player().lastHurt = 0;
                // its energy runs out and Protection goes off while the test waits for the player's first ticks
                hamon.setBreathStability(hamon.getMaxBreathStability());
                hamon.setEnergy(hamon.getMaxEnergy());
                if (!hamon.isProtectionEnabled()) {
                    hamon.toggleHamonProtection();
                }
                float before = guarded.player().getHealth();
                blow.run();
                return before - guarded.player().getHealth();
            }
        }.start();
    }
}
