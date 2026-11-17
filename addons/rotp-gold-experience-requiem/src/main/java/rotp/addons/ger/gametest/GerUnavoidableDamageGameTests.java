package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.damage.GerDamageTypes;
import rotp.addons.ger.damage.GerUnavoidableDamage;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.api.damage.DefencePiercing;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * The owner, 2026-11-08: Requiem's damage, and its holder's melee while it is
 * summoned, cannot be avoided ("無視敵方格擋或無敵幀以及無敵機制和限傷等").
 * Vanilla's guards and what another mod's mob does in its own code; the core's
 * own guard, parry and caps follow its damage type tags.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerUnavoidableDamageGameTests {
    private static final float HEALTH = 200.0F;

    private GerUnavoidableDamageGameTests() {}

    /** A boss as another mod writes one: its own code caps every hit before the game sees it. */
    static final class CappedCow extends Cow {
        static final float CAP = 2.0F;

        CappedCow(Level level) {
            super(EntityType.COW, level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            return super.hurt(source, Math.min(amount, CAP));
        }
    }

    private static void guard(ObservedCow cow) {
        cow.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 4000, 4));
        cow.setInvulnerable(true);
        cow.invulnerableTime = 20;
        cow.lastHurt = 1000;
    }

    /** A punch through the hurt cooldown, the invulnerable flag and Resistance V. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_unavoidable_stand")
    public static void aStandHitPassesCooldownInvulnerableFlagAndResistance(GameTestHelper helper) {
        new Sequence(helper, "unavoidable/stand", 250) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance punch;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 1.5));
                        stage = 1;
                    }
                    case 1 -> {
                        guard(cow);
                        punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch", "ger_stand_heavy_punch",
                                InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                        stage = 2;
                    }
                    case 2 -> {
                        cow.invulnerableTime = 20;
                        cow.lastHurt = 1000;
                        if (punch.punchedTarget != null) {
                            float lost = HEALTH - cow.getHealth();
                            DamageSource source = cow.sources.isEmpty() ? null : cow.sources.get(0);
                            scene.log("lost=" + lost + " type="
                                    + (source != null ? source.typeHolder().getRegisteredName() : null));
                            helper.assertTrue(source != null && source.is(GerDamageTypes.REQUIEM_ATTACK)
                                            && source.is(GerDamageTypes.REQUIEM)
                                            && source.is(DamageTypeTags.BYPASSES_SHIELD)
                                            && source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                                            && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                                            && source.is(DefencePiercing.INVULNERABILITY)
                                            && source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                                            && !source.is(DamageTypeTags.BYPASSES_ARMOR)
                                            && source.getDirectEntity() == actor.stand(),
                                    scene.id + ": the heavy punch hurt as "
                                            + (source != null ? source.typeHolder().getRegisteredName() : null));
                            helper.assertTrue(Math.abs(lost - 20.0F) < GerMeleeGameTestScene.EPS,
                                    scene.id + ": the heavy punch took " + lost + " from a cow in its hurt cooldown,"
                                            + " flagged invulnerable and under Resistance V, not its 20");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** The holder's own fist, with Requiem out and with it away. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_unavoidable_melee")
    public static void theHoldersMeleePassesThemWhileRequiemIsOut(GameTestHelper helper) {
        new Sequence(helper, "unavoidable/melee", 250) {
            private Actor actor;
            private ObservedCow cow;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 1.5));
                        stage = 1;
                    }
                    case 1 -> {
                        // a fresh player's swing is weak until its attack strength has charged
                        if (tick > 30) {
                            guard(cow);
                            actor.user().attack(cow);
                            float lost = HEALTH - cow.getHealth();
                            scene.log("summoned: lost=" + lost + " hurts=" + cow.hurts);
                            // however weak the swing: without Requiem out, nothing of it passes the three guards
                            helper.assertTrue(lost > 0.0F,
                                    scene.id + ": the holder's fist took " + lost + " from a cow in its hurt"
                                            + " cooldown, flagged invulnerable and under Resistance V");
                            actor.type().forceUnsummon(actor.user(), actor.power());
                            scene.premise(GoldExperienceRequiemAddon.activeStand(actor.user()) == null,
                                    "Requiem is away");
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (tick > 60) {
                            float before = cow.getHealth();
                            guard(cow);
                            actor.user().attack(cow);
                            scene.log("away: lost=" + (before - cow.getHealth()));
                            helper.assertTrue(cow.getHealth() == before,
                                    scene.id + ": with Requiem away the fist still passed the cow's guards");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** A cap a mob's own code puts on every hit is taken off a Stand hit again. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_unavoidable_cap")
    public static void aCapInTheTargetsOwnCodeDoesNotHoldAStandHit(GameTestHelper helper) {
        new Sequence(helper, "unavoidable/cap", 250) {
            private Actor actor;
            private CappedCow cow;
            private EntityActionInstance punch;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = new CappedCow(scene.level);
                        cow.setNoAi(true);
                        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                                .setBaseValue(HEALTH);
                        cow.setHealth(HEALTH);
                        scene.add(cow, scene.origin.add(0, 0, 1.5));
                        scene.premise(cow.hurt(scene.level.damageSources().generic(), 50)
                                        && HEALTH - cow.getHealth() == CappedCow.CAP,
                                "the cow caps an ordinary hit of 50 at " + CappedCow.CAP);
                        cow.setHealth(HEALTH);
                        cow.invulnerableTime = 0;
                        stage = 1;
                    }
                    case 1 -> {
                        punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch", "ger_stand_heavy_punch",
                                InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                        stage = 2;
                    }
                    case 2 -> {
                        if (punch.punchedTarget != null) {
                            float lost = HEALTH - cow.getHealth();
                            scene.log("lost=" + lost);
                            helper.assertTrue(Math.abs(lost - 20.0F) < GerMeleeGameTestScene.EPS,
                                    scene.id + ": the capped cow lost " + lost + " to the heavy punch, not its 20");
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
     * Creative and spectator players stay out of every one of Requiem's types, though each is in vanilla's
     * bypasses_invulnerability, which lets a hit through to a creative player.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_unavoidable_creative")
    public static void creativeAndSpectatorPlayersStayOut(GameTestHelper helper) {
        new Sequence(helper, "unavoidable/creative", 250) {
            private Actor actor;
            private Client creative;
            private Client spectator;
            private Client survival;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        // with PvP off no player's hit reaches another player at all
                        boolean pvp = scene.level.getServer().isPvpAllowed();
                        scene.level.getServer().setPvpAllowed(true);
                        scene.onClose(() -> scene.level.getServer().setPvpAllowed(pvp));
                        actor = scene.actor();
                        creative = scene.client(scene.origin.add(2, 0, 2));
                        spectator = scene.client(scene.origin.add(-2, 0, 2));
                        survival = scene.client(scene.origin.add(0, 0, 3));
                        creative.player().gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                        spectator.player().gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
                        stage = 1;
                    }
                    case 1 -> {
                        if (survival.player().tickCount > 60) {
                            for (ResourceKey<DamageType> type : List.of(GerDamageTypes.REQUIEM_ATTACK,
                                    GerDamageTypes.REQUIEM_EXPLOSION, GerDamageTypes.REQUIEM_LIFE_SHOT,
                                    GerDamageTypes.REQUIEM_TRUE, GerDamageTypes.DEATH_LOOP)) {
                                DamageSource source = GerDamageTypes.source(
                                        scene.level, type, actor.stand(), actor.user());
                                helper.assertTrue(!creative.player().hurt(source, 5)
                                                && !spectator.player().hurt(source, 5)
                                                && !GerUnavoidableDamage.deal(creative.player(), source, 5)
                                                && creative.player().getHealth() == 20
                                                && spectator.player().getHealth() == 20,
                                        scene.id + ": " + type.location() + " hurt a creative or spectator player");
                                helper.assertTrue(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                                                && source.is(DefencePiercing.INVULNERABILITY),
                                        scene.id + ": " + type.location() + " does not pass invulnerable states, so"
                                                + " vanilla alone would have kept the creative player out");
                            }
                            // the same source does reach a survival player, by the same call
                            DamageSource source = GerDamageTypes.source(scene.level,
                                    GerDamageTypes.REQUIEM_ATTACK, actor.stand(), actor.user());
                            scene.premise(survival.player().hurt(source, 5)
                                            && survival.player().getHealth() == 15,
                                    "a survival player takes the same hit");
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
     * A totem of undying still saves from an ordinary hit of Requiem: the
     * owner took it away from the death loop only.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_unavoidable_totem")
    public static void aTotemStillSavesFromAnOrdinaryRequiemHit(GameTestHelper helper) {
        new Sequence(helper, "unavoidable/totem", 250) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance punch;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = GerUndieGameTests.markableCow(scene, scene.origin.add(0, 0, 1.5));
                        cow.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                        stage = 1;
                    }
                    case 1 -> {
                        punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch", "ger_stand_heavy_punch",
                                InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                        stage = 2;
                    }
                    case 2 -> {
                        if (punch.punchedTarget != null) {
                            scene.log("health=" + cow.getHealth() + " offhand=" + cow.getOffhandItem()
                                    + " regeneration=" + cow.hasEffect(MobEffects.REGENERATION));
                            // the totem leaves 1, and its Regeneration may already have given one more
                            helper.assertTrue(cow.isAlive() && cow.getHealth() >= 1.0F && cow.getHealth() <= 2.0F
                                            && cow.getOffhandItem().isEmpty()
                                            && cow.hasEffect(MobEffects.REGENERATION),
                                    scene.id + ": the totem did not save the cow from a heavy punch: health "
                                            + cow.getHealth() + ", offhand " + cow.getOffhandItem());
                            // The same for the Finisher Punch's true damage on a victim it does not put in the loop:
                            // vanilla asks no totem against a type that passes invulnerability, the module does.
                            ObservedCow other = GerUndieGameTests.markableCow(scene, scene.origin.add(2, 0, 1.5));
                            other.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                            GerUnavoidableDamage.deal(other, GerDamageTypes.source(scene.level,
                                    GerDamageTypes.REQUIEM_TRUE, actor.stand(), actor.user()), 1000);
                            scene.log("true damage: health=" + other.getHealth() + " offhand=" + other.getOffhandItem());
                            helper.assertTrue(other.isAlive() && other.getHealth() >= 1.0F && other.getHealth() <= 2.0F
                                            && other.getOffhandItem().isEmpty(),
                                    scene.id + ": the totem did not save a cow outside the loop from true damage: health "
                                            + other.getHealth() + ", offhand " + other.getOffhandItem());
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
     * Each of Requiem's damage types is in the tags that pass a shield, the frames after a hit, invulnerable states
     * and Resistance in vanilla, and a guard or parry, invulnerable states and damage caps in the core. The core's
     * mitigation is passed by the true damage alone.
     */
    @GameTest(template = "empty", batch = "ger_unavoidable_tags")
    public static void requiemsDamageTypesPassEveryDefenceByTag(GameTestHelper helper) {
        Registry<DamageType> types = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        List<TagKey<DamageType>> tags = List.of(GerDamageTypes.REQUIEM, DamageTypeTags.BYPASSES_SHIELD,
                DamageTypeTags.BYPASSES_COOLDOWN, DamageTypeTags.BYPASSES_INVULNERABILITY,
                DamageTypeTags.BYPASSES_RESISTANCE, DefencePiercing.GUARD, DefencePiercing.INVULNERABILITY,
                DefencePiercing.DAMAGE_CAPS);
        List<String> missing = new ArrayList<>();
        for (ResourceKey<DamageType> key : List.of(GerDamageTypes.REQUIEM_ATTACK, GerDamageTypes.REQUIEM_EXPLOSION,
                GerDamageTypes.REQUIEM_LIFE_SHOT, GerDamageTypes.REQUIEM_TRUE, GerDamageTypes.DEATH_LOOP)) {
            Holder<DamageType> type = types.getHolderOrThrow(key);
            for (TagKey<DamageType> tag : tags) {
                if (!type.is(tag)) {
                    missing.add(key.location() + " is not in " + tag.location());
                }
            }
            // what lessens a hit as armour does is passed by true damage only: the Finisher Punch's hit and the loop's
            boolean trueDamage = key == GerDamageTypes.REQUIEM_TRUE || key == GerDamageTypes.DEATH_LOOP;
            if (type.is(DefencePiercing.MITIGATION) != trueDamage) {
                missing.add(key.location() + (trueDamage ? " is not in " : " is in ")
                        + DefencePiercing.MITIGATION.location());
            }
        }
        helper.assertTrue(missing.isEmpty(), "unavoidable/tags: " + String.join("; ", missing));
        helper.succeed();
    }

    /**
     * The coordinator's ruling: a cap another mod puts on the last stage of damage is broken by the death loop alone
     * (GerDeathLoopEscapeGameTests.aDamageCapDoesNotSaveALoopVictim); Requiem's ordinary hit keeps it.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_unavoidable_last_cap")
    public static void anotherModsLastStageCapHoldsAnOrdinaryHit(GameTestHelper helper) {
        new Sequence(helper, "unavoidable/last-stage-cap", 250) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance punch;
            private int capped;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 1.5));
                        scene.listen(LivingDamageEvent.Pre.class, event -> {
                            if (event.getEntity() == cow && event.getNewDamage() > 0.5F) {
                                event.setNewDamage(0.5F);
                                capped++;
                            }
                        });
                        stage = 1;
                    }
                    case 1 -> {
                        punch = scene.pressAction(actor, cow, "ger_stand_heavy_punch", "ger_stand_heavy_punch",
                                InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                        stage = 2;
                    }
                    case 2 -> {
                        if (punch.punchedTarget != null) {
                            float lost = HEALTH - cow.getHealth();
                            scene.log("capped=" + capped + " lost=" + lost);
                            scene.premise(capped == 1, "the cap cut the heavy punch once");
                            helper.assertTrue(Math.abs(lost - 0.5F) < GerMeleeGameTestScene.EPS,
                                    scene.id + ": a heavy punch another mod capped at 0.5 took " + lost);
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
