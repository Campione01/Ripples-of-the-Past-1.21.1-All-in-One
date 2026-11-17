package rotp.addons.yellowtemperance.ability;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceGameEvents;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceDisguiseState;
import rotp.addons.yellowtemperance.disguise.YellowTemperanceScaleTypes;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import rotp.addons.yellowtemperance.network.DisguiseSelectionPayload;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The player's list of 2026-11-05 on Yellow Temperance, against its 1.16 donor. AbstractDisguiseStandEntity kept the
 * Stand disguise (DISGUISE_ENTITY) apart from the user's own (USER_DISGUISE_ENTITY): a fake Stand beside the user,
 * who keeps his look and size, and whom mobs still see (DisguiseAwareTargeting asks isUserDisguised). Turning the
 * user himself into the Stand's look is the port's, kept as a choice because the player likes it.
 */
@GameTestHolder(YellowTemperanceAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class YellowTemperanceDisguiseGameTests {
    private static final String STAR_PLATINUM = "jojo_ripples:star_platinum";
    private static final String VILLAGER = "minecraft:villager";
    // past the controller's limit on one selection request per five ticks
    private static final int PERMIT_TICKS = 6;
    private static final double SCENE_CHUNK_MARGIN = 2.5D;

    private YellowTemperanceDisguiseGameTests() {}

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 100)
    public static void theStandDisguiseShowsAStandBesideTheUser(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.choose(DisguiseMode.STAND, DisguiseMode.STAND, STAR_PLATINUM);
            helper.assertTrue(scene.controller.getBesideStand().map(id -> id.toString().equals(STAR_PLATINUM))
                            .orElse(false),
                    "1.16's Stand disguise put the chosen Stand on the Stand entity; beside="
                            + scene.controller.getBesideStand());
            helper.assertTrue(!scene.controller.isDisguised()
                            && scene.controller.getDisguiseMode() == DisguiseMode.NONE,
                    "1.16's Stand disguise left the user as he was (isUserDisguised false), got "
                            + scene.controller.getDisguiseMode());
            helper.assertTrue(scene.heightScale() == 1.0F,
                    "1.16's Stand disguise left the user's size alone, height scale " + scene.heightScale());
            helper.assertTrue(scene.standAbility() == scene.power.getAbility(YellowTemperanceAddon.DISGUISE_STAND_DISABLE_NAME),
                    "With the fake Stand out the Stand disguise's key turns it off, got " + scene.standAbility());
            scene.pressStandDisable(helper, () -> {
                helper.assertTrue(scene.controller.getBesideStand().isEmpty(),
                        "1.16 DisguiseStandDisable took the fake Stand away; beside="
                                + scene.controller.getBesideStand());
                LogUtils.getLogger().info("R772_YT_DISGUISE_PASS beside");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 100)
    public static void theUserCanStillTakeTheStandsLookHimself(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.choose(DisguiseMode.STAND, DisguiseMode.STAND_SELF, STAR_PLATINUM);
            helper.assertTrue(scene.controller.getDisguiseMode() == DisguiseMode.STAND_SELF
                            && scene.controller.isDisguised(),
                    "The screen's second choice turns the user himself into the Stand, got "
                            + scene.controller.getDisguiseMode());
            helper.assertTrue(scene.controller.getBesideStand().isEmpty(),
                    "Turning himself into the Stand puts none beside him; beside="
                            + scene.controller.getBesideStand());
            // the core's Stands report the player's size through their entity type, so take a size that shows
            scene.controller.beginSelection(DisguiseMode.STAND);
            scene.controller.applyEntityOrStandDisguise(DisguiseMode.STAND_SELF,
                    ResourceLocation.parse(STAR_PLATINUM), EntityDimensions.scalable(0.7F, 2.1F));
            helper.assertTrue(scene.heightScale() > 1.1F,
                    "Taking the Stand's look still takes its size, height scale " + scene.heightScale());
            helper.assertTrue(scene.standAbility() == scene.power.getAbility(YellowTemperanceAddon.DISGUISE_STAND_DISABLE_NAME),
                    "With the Stand's look on the Stand disguise's key turns it off, got " + scene.standAbility());
            scene.pressStandDisable(helper, () -> {
                helper.assertTrue(!scene.controller.isDisguised() && scene.heightScale() == 1.0F,
                        "The Stand disguise's key gives the user his look and size back, mode "
                                + scene.controller.getDisguiseMode() + " height scale " + scene.heightScale());
                LogUtils.getLogger().info("R772_YT_DISGUISE_PASS self");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 120)
    public static void theStandBesideLeavesTheUsersOwnDisguiseOn(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.choose(DisguiseMode.ENTITY, DisguiseMode.ENTITY, VILLAGER);
            helper.assertTrue(scene.controller.getDisguiseMode() == DisguiseMode.ENTITY,
                    "Fixture: the entity disguise did not go on, got " + scene.controller.getDisguiseMode());
            helper.runAfterDelay(PERMIT_TICKS, () -> {
                scene.choose(DisguiseMode.STAND, DisguiseMode.STAND, STAR_PLATINUM);
                helper.assertTrue(scene.controller.getDisguiseMode() == DisguiseMode.ENTITY
                                && scene.controller.getBesideStand().isPresent(),
                        "1.16 kept both: the user's entity disguise and the fake Stand; mode "
                                + scene.controller.getDisguiseMode() + " beside "
                                + scene.controller.getBesideStand());
                scene.controller.clearStandDisguise();
                helper.assertTrue(scene.controller.getDisguiseMode() == DisguiseMode.ENTITY
                                && scene.controller.getBesideStand().isEmpty(),
                        "1.16 clearDisguisesStand left the user's entity disguise on; mode "
                                + scene.controller.getDisguiseMode() + " beside "
                                + scene.controller.getBesideStand());
                helper.runAfterDelay(PERMIT_TICKS, () -> {
                    scene.choose(DisguiseMode.STAND, DisguiseMode.STAND, STAR_PLATINUM);
                    helper.assertTrue(scene.controller.getBesideStand().isPresent(),
                            "Fixture: the fake Stand did not come back");
                    scene.power.getPowerType().forceUnsummon(scene.player, scene.power);
                    YellowTemperanceDisguiseState recalled = scene.state();
                    helper.assertTrue(recalled.besideStand().isPresent()
                                    && recalled.mode() == DisguiseMode.ENTITY,
                            "Both go on with the Stand recalled, the owner's rule of 2026-11-06; beside "
                                    + recalled.besideStand() + " mode " + recalled.mode());
                    scene.level.removePlayerImmediately(scene.player, Entity.RemovalReason.DISCARDED);
                    LogUtils.getLogger().info("R772_YT_DISGUISE_PASS both");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 40)
    public static void clearDebuffsIsTheShiftVariationOfAbsorption(GameTestHelper helper) {
        ControlSchemeTemplate template = YellowTemperanceAddon.YELLOW_TEMPERANCE.get().makeDefaultControlSchemeTemplate();
        int bare = 0;
        int underAbsorption = 0;
        for (ControlSchemeTemplate.GroupTemplate group : template.groups.values()) {
            for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
                for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
                    Map<InputMethod, String> base = slot.get(null);
                    Map<InputMethod, String> shift = slot.get(InputKey.Modifier.SHIFT);
                    if (base != null && YellowTemperanceAddon.CLEAR_NAME.equals(base.get(InputMethod.HOLD))) {
                        bare++;
                    }
                    if (base != null && shift != null
                            && YellowTemperanceAddon.ABSORPTION_NAME.equals(base.get(InputMethod.HOLD))
                            && YellowTemperanceAddon.CLEAR_NAME.equals(shift.get(InputMethod.HOLD))) {
                        underAbsorption++;
                    }
                }
            }
        }
        helper.assertTrue(bare == 0 && underAbsorption == 1,
                "1.16 clear_debuffs is shiftVariationOf(absorption), so it takes no slot of its own; own slots "
                        + bare + ", under absorption " + underAbsorption);
        LogUtils.getLogger().info("R772_YT_DISGUISE_PASS variation");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 40)
    public static void clientsAreToldOfTheErosionOnAnyEntity(GameTestHelper helper) {
        helper.assertTrue(ModStatusEffects.TRACKED_EFFECTS.contains(YellowTemperanceAddon.EROSION),
                "1.16 InitStatusEffect tracked the erosion; without it a client sees it on itself alone, so no mob "
                        + "ever shows the patches");
        LogUtils.getLogger().info("R772_YT_DISGUISE_PASS tracked");
        helper.succeed();
    }

    // The owner's rule of 2026-11-06, over 1.16's: the disguises, the doll and the Protection go on with the Stand recalled.
    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 160)
    public static void theDisguisesDollAndProtectionGoOnThroughARecall(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.choose(DisguiseMode.ENTITY, DisguiseMode.ENTITY, VILLAGER);
            helper.runAfterDelay(PERMIT_TICKS, () -> {
                scene.choose(DisguiseMode.STAND, DisguiseMode.STAND, STAR_PLATINUM);
                scene.controller.setDollEnabled(true);
                float disguisedHeight = scene.heightScale();
                helper.runAfterDelay(3, () -> {
                    helper.assertTrue(scene.player.hasEffect(YellowTemperanceAddon.PROTECTION),
                            "Fixture: a summoned Yellow Temperance gives its Protection");
                    scene.power.getPowerType().forceUnsummon(scene.player, scene.power);
                    helper.assertTrue(YellowTemperanceAddon.controller(scene.player) == null,
                            "Fixture: the recall left a controller");
                    // The effect lives three ticks at a time: only a new one proves that the Protection goes on.
                    scene.player.removeEffect(YellowTemperanceAddon.PROTECTION);
                    helper.runAfterDelay(3, () -> {
                        YellowTemperanceDisguiseState state = scene.state();
                        helper.assertTrue(state.mode() == DisguiseMode.ENTITY && state.disguiseId().isPresent(),
                                "The entity disguise ended with the recall: " + state);
                        helper.assertTrue(state.besideStand().isPresent(), "The fake Stand ended with the recall: " + state);
                        helper.assertTrue(state.doll(), "The doll ended with the recall: " + state);
                        helper.assertTrue(scene.heightScale() == disguisedHeight && disguisedHeight != 1.0F,
                                "The disguise's size ended with the recall: " + scene.heightScale());
                        helper.assertTrue(scene.player.hasEffect(YellowTemperanceAddon.PROTECTION),
                                "The Protection ended with the recall");
                        Zombie zombie = EntityType.ZOMBIE.create(scene.level);
                        zombie.setPos(scene.player.position());
                        zombie.setNoAi(true);
                        scene.level.addFreshEntity(zombie);
                        zombie.setTarget(scene.player);
                        helper.assertTrue(zombie.getTarget() == null,
                                "Mobs must go on being fooled with the Stand recalled, target " + zombie.getTarget());
                        zombie.discard();
                        helper.assertTrue(YellowTemperanceAddon.YELLOW_TEMPERANCE.get().summon(scene.player, scene.power),
                                "Fixture: the next summon failed");
                        YellowTemperanceControllerEntity next = YellowTemperanceAddon.controller(scene.player);
                        assertTicks(helper, next);
                        helper.runAfterDelay(next.summonLockTicks + 2, () -> {
                            helper.assertTrue(next.getDisguiseMode() == DisguiseMode.ENTITY
                                            && next.getBesideStand().isPresent() && next.isDollEnabled(),
                                    "The next summon takes the state over; mode " + next.getDisguiseMode()
                                            + " beside " + next.getBesideStand() + " doll " + next.isDollEnabled());
                            new Scene(scene.level, scene.player, scene.power, next).pressStandDisable(helper, () -> {
                                helper.assertTrue(next.getBesideStand().isEmpty()
                                                && next.getDisguiseMode() == DisguiseMode.ENTITY,
                                        "After the next summon the key ends the fake Stand and leaves the rest; beside "
                                                + next.getBesideStand() + " mode " + next.getDisguiseMode());
                                LogUtils.getLogger().info("R772_YT_RECALL_PASS goes_on");
                                helper.succeed();
                            });
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 200)
    public static void whatGoesOnEndsWithItsUserAndALossOfTheStand(GameTestHelper helper) {
        scene(helper, scene -> {
            Deque<Ending> endings = new ArrayDeque<>(List.of(
                    new Ending("death", () -> YellowTemperanceGameEvents.onLivingDeath(new LivingDeathEvent(
                            scene.player, scene.player.damageSources().magic()))),
                    new Ending("logout", () -> YellowTemperanceGameEvents.onPlayerLogout(
                            new PlayerEvent.PlayerLoggedOutEvent(scene.player))),
                    new Ending("a change of level", () -> YellowTemperanceGameEvents.onChangedDimension(
                            new PlayerEvent.PlayerChangedDimensionEvent(scene.player, Level.OVERWORLD, Level.NETHER))),
                    new Ending("the loss of the Stand", () -> scene.power.setStand(null))));
            nextEnding(helper, scene, endings);
        });
    }

    private record Ending(String name, Runnable run) {}

    private static void nextEnding(GameTestHelper helper, Scene scene, Deque<Ending> endings) {
        Ending ending = endings.poll();
        if (ending == null) {
            scene.level.removePlayerImmediately(scene.player, Entity.RemovalReason.DISCARDED);
            LogUtils.getLogger().info("R772_YT_RECALL_PASS ends");
            helper.succeed();
            return;
        }
        helper.assertTrue(YellowTemperanceAddon.YELLOW_TEMPERANCE.get().summon(scene.player, scene.power),
                "Fixture: the summon before " + ending.name() + " failed");
        YellowTemperanceControllerEntity controller = YellowTemperanceAddon.controller(scene.player);
        assertTicks(helper, controller);
        helper.runAfterDelay(controller.summonLockTicks + PERMIT_TICKS, () -> {
            new Scene(scene.level, scene.player, scene.power, controller)
                    .choose(DisguiseMode.ENTITY, DisguiseMode.ENTITY, VILLAGER);
            helper.assertTrue(scene.state().isDisguised(), "Fixture: no disguise before " + ending.name());
            helper.runAfterDelay(3, () -> {
                scene.power.getPowerType().forceUnsummon(scene.player, scene.power);
                helper.assertTrue(scene.state().isDisguised() && scene.state().protection(),
                        "Fixture: the state did not go on before " + ending.name() + ": " + scene.state());
                ending.run().run();
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(scene.state().isEmpty(), ending.name() + " left the state: " + scene.state());
                    helper.assertTrue(scene.heightScale() == 1.0F,
                            ending.name() + " left the disguise's size: " + scene.heightScale());
                    helper.assertTrue(!scene.player.hasEffect(YellowTemperanceAddon.PROTECTION),
                            ending.name() + " left the Protection");
                    nextEnding(helper, scene, endings);
                });
            });
        });
    }

    /**
     * R772.1 OQ-C04: the HUD lights a slot whose skill goes on with the Stand recalled (Ability#cl_runsWithStandRecalled).
     * With Yellow Temperance recalled, the entity disguise's slot and its end, and the doll's, say so; the other
     * disguises' do not, and none does once the disguise is off.
     */
    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_recall_sign",
            timeoutTicks = 80)
    public static void theSlotsOfWhatGoesOnSaySoWithTheStandRecalled(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.choose(DisguiseMode.ENTITY, DisguiseMode.ENTITY, VILLAGER);
            scene.controller.setDollEnabled(true);
            scene.power.getPowerType().forceUnsummon(scene.player, scene.power);
            try {
                helper.assertTrue(scene.state().isDisguised() && scene.state().doll(),
                        "Fixture: the disguise or the doll did not go on with the recall: " + scene.state());
                helper.assertTrue(runs(scene, YellowTemperanceAddon.DISGUISE_NAME)
                                && runs(scene, YellowTemperanceAddon.DISGUISE_DISABLE_NAME)
                                && runs(scene, YellowTemperanceAddon.TOGGLE_DOLL_NAME),
                        "The entity disguise, its end and the doll must say they go on with the Stand recalled");
                helper.assertTrue(!runs(scene, YellowTemperanceAddon.DISGUISE_PLAYER_NAME)
                                && !runs(scene, YellowTemperanceAddon.DISGUISE_PLAYER_DISABLE_NAME)
                                && !runs(scene, YellowTemperanceAddon.DISGUISE_STAND_NAME)
                                && !runs(scene, YellowTemperanceAddon.DISGUISE_STAND_DISABLE_NAME),
                        "A disguise that is not on must not say it goes on");
                YellowTemperanceDisguiseState.put(scene.player, YellowTemperanceDisguiseState.of(scene.player)
                        .withUserDisguise(DisguiseMode.NONE, Optional.empty(), Optional.empty(), "")
                        .withDoll(false));
                helper.assertTrue(!runs(scene, YellowTemperanceAddon.DISGUISE_NAME)
                                && !runs(scene, YellowTemperanceAddon.TOGGLE_DOLL_NAME),
                        "With the disguise and the doll off their slots must not say they go on");
            }
            finally {
                scene.level.removePlayerImmediately(scene.player, Entity.RemovalReason.DISCARDED);
            }
            helper.succeed();
        });
    }

    private static boolean runs(Scene scene, String name) {
        Ability ability = scene.power.getAbility(name);
        return ability != null && ability.cl_runsWithStandRecalled(scene.player);
    }

    // NeoForge sends this to a client as it starts tracking the user, a late joiner included, and nothing saves it.
    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 80)
    public static void everyClientIsToldOfTheDisguiseAndARelogForgetsIt(GameTestHelper helper) {
        scene(helper, scene -> {
            scene.choose(DisguiseMode.STAND, DisguiseMode.STAND, STAR_PLATINUM);
            scene.power.getPowerType().forceUnsummon(scene.player, scene.power);
            YellowTemperanceDisguiseState state = scene.state();
            helper.assertTrue(state.besideStand().isPresent(), "Fixture: no fake Stand after the recall");

            YellowTemperanceDisguiseState late = scene.throughTheClient(state, true, null);
            helper.assertTrue(late.besideStand().equals(state.besideStand()) && late.protection() == state.protection(),
                    "A client that starts tracking the user gets the state: " + late);
            helper.assertTrue(late.besideShownTick() < scene.player.tickCount - 1000,
                    "A client that only now sees the user sees no grow-in: shown at " + late.besideShownTick()
                            + ", now " + scene.player.tickCount);

            YellowTemperanceDisguiseState changed = scene.throughTheClient(
                    state.withBesideStand(Optional.of(ResourceLocation.parse(VILLAGER))), false, late);
            helper.assertTrue(changed.besideShownTick() == scene.player.tickCount,
                    "A client watching the change sees the new Stand grow in: shown at " + changed.besideShownTick());
            helper.assertTrue(scene.throughTheClient(state, false, late).besideShownTick() == late.besideShownTick(),
                    "An update that leaves the fake Stand alone must not grow it in again");

            CompoundTag saved = scene.player.serializeAttachments(scene.level.registryAccess());
            helper.assertTrue(saved == null || !saved.contains("rotp_yellowtemperance:disguise_state"),
                    "A relog must not bring the state back: " + saved);
            scene.level.removePlayerImmediately(scene.player, Entity.RemovalReason.DISCARDED);
            LogUtils.getLogger().info("R772_YT_RECALL_PASS synced");
            helper.succeed();
        });
    }

    // 075 YellowTemperanceEntity.addProtectionEffectBasedOnHunger: no food, no Protection. With the Stand recalled that ends it.
    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_disguise",
            timeoutTicks = 80)
    public static void theProtectionEndsWithTheFoodBarWithTheStandRecalled(GameTestHelper helper) {
        scene(helper, scene -> helper.runAfterDelay(3, () -> {
            scene.power.getPowerType().forceUnsummon(scene.player, scene.power);
            helper.assertTrue(scene.state().protection(), "Fixture: the Protection did not go on: " + scene.state());
            scene.player.getFoodData().setFoodLevel(0);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!scene.state().protection() && !scene.player.hasEffect(YellowTemperanceAddon.PROTECTION),
                        "An empty food bar must end the Protection: " + scene.state() + ", effect "
                                + scene.player.getEffect(YellowTemperanceAddon.PROTECTION));
                scene.player.getFoodData().setFoodLevel(20);
                helper.runAfterDelay(3, () -> {
                    MobEffectInstance effect = scene.player.getEffect(YellowTemperanceAddon.PROTECTION);
                    helper.assertTrue(effect == null, "Food alone must not bring it back with no Stand out: " + effect);
                    scene.level.removePlayerImmediately(scene.player, Entity.RemovalReason.DISCARDED);
                    LogUtils.getLogger().info("R772_YT_RECALL_PASS food");
                    helper.succeed();
                });
            });
        }));
    }

    /**
     * R772.1 OQ-Y02 (075 YellowTemperanceEntity, 0.7 by 2.1 at the Stand's place; 1.16 StandEntity.actuallyHurt): with
     * the fake Stand out, Yellow Temperance's body stands where the fake Stand is drawn and can be struck there, and a
     * Stand's hit on it passes to the user through the link, as 1.16's did. The entity stays at the user's feet, which
     * the ranges are measured from. With the fake Stand gone the body is back at his feet and cannot be struck.
     */
    @GameTest(template = "empty", templateNamespace = YellowTemperanceAddon.MOD_ID, batch = "yt_struck",
            timeoutTicks = 100)
    public static void theFakeStandIsABodyThatCanBeStruck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = connectedUser(helper, "YTStruck");
        StandPower power = PowerClass.STAND.attachGet(player);
        Zombie attacker = EntityType.ZOMBIE.create(level);
        Runnable leave = () -> {
            if (power.hasPower() && power.isSummoned()) {
                power.getPowerType().forceUnsummon(player, power);
            }
            if (attacker != null) {
                attacker.discard();
            }
            player.connection.disconnect(Component.literal("Yellow Temperance GameTest complete"));
        };
        stepOrLeave(leave, () -> {
            power.setStand(YellowTemperanceAddon.YELLOW_TEMPERANCE.get());
            power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(attacker != null && YellowTemperanceAddon.YELLOW_TEMPERANCE.get().summon(player, power),
                    "Fixture: Yellow Temperance's summon failed");
        });
        YellowTemperanceControllerEntity controller = YellowTemperanceAddon.controller(player);
        int settled = controller.summonLockTicks + 2;
        helper.runAfterDelay(settled, () -> stepOrLeave(leave, () -> {
            assertTicks(helper, controller);
            helper.assertTrue(!controller.isPickable()
                            && controller.getBoundingBox().getXsize() < 0.2D
                            && controller.getBoundingBox().contains(player.position().add(0.0D, 0.05D, 0.0D)),
                    "Fixture: with no fake Stand the body is the controller's speck at the user's feet: "
                            + controller.getBoundingBox());
            controller.beginSelection(DisguiseMode.STAND);
            DisguiseSelectionPayload.select(player, DisguiseMode.STAND.id(), STAR_PLATINUM);
        }));
        helper.runAfterDelay(settled + 3, () -> stepOrLeave(leave, () -> {
            Vec3 beside = YellowTemperanceControllerEntity.besidePosition(player, 1.0F);
            AABB box = controller.getBoundingBox();
            helper.assertTrue(controller.showsBesideStand() && controller.isPickable(),
                    "With the fake Stand out, 1.16's Stand body there can be struck");
            helper.assertTrue(Math.abs(box.getCenter().x - beside.x) < 1.0E-6D
                            && Math.abs(box.getCenter().z - beside.z) < 1.0E-6D
                            && Math.abs(box.minY - beside.y) < 1.0E-6D
                            && Math.abs(box.getXsize() - 0.7D) < 1.0E-6D
                            && Math.abs(box.getYsize() - 2.1D) < 1.0E-6D,
                    "The body must be 1.16's 0.7 by 2.1 where the fake Stand is drawn, at " + beside + ", got " + box);
            helper.assertTrue(controller.position().distanceTo(player.position()) < 1.0E-6D,
                    "The entity must stay at the user's feet, which its ranges are measured from: "
                            + controller.position() + " for " + player.position());
            // a pick along a line through the body from outside, as a client's crosshair makes it
            Vec3 outward = box.getCenter().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
            Vec3 from = box.getCenter().add(outward.scale(3.0D));
            EntityHitResult pick = ProjectileUtil.getEntityHitResult(level, attacker, from, box.getCenter(),
                    box.inflate(4.0D), Entity::isPickable, 0.0F);
            helper.assertTrue(pick != null && pick.getEntity() == controller,
                    "A pick through the fake Stand must find the Stand's body, found "
                            + (pick != null ? pick.getEntity() : null));

            attacker.moveTo(from.x, player.getY(), from.z);
            float health = player.getHealth();
            DamageSource standHit = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(ModDamageTypes.STAND_ATTACK), attacker);
            boolean struck = controller.hurt(standHit, 8.0F);
            helper.assertTrue(struck && player.getHealth() < health,
                    "A Stand's hit on the body must pass to the user, as 1.16's link did; struck " + struck
                            + ", health " + health + " -> " + player.getHealth());
            controller.clearStandDisguise();
        }));
        helper.runAfterDelay(settled + 6, () -> {
            stepOrLeave(leave, () -> helper.assertTrue(!controller.isPickable()
                            && controller.getBoundingBox().contains(player.position().add(0.0D, 0.05D, 0.0D)),
                    "With the fake Stand gone the body must be back at the user's feet and not be struck: "
                            + controller.getBoundingBox()));
            leave.run();
            LogUtils.getLogger().info("R772_YT_DISGUISE_PASS struck");
            helper.succeed();
        });
    }

    // a player in the server's list, who takes damage, unlike a fake player
    private static ServerPlayer connectedUser(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 feet = sceneFeet(helper);
        player.setNoGravity(true);
        player.setPos(feet.x, feet.y, feet.z);
        try {
            Field protection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            protection.setAccessible(true);
            protection.setInt(player, 0);
        }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("A new player's spawn protection is not reachable", exception);
        }
        return player;
    }

    // a failed step takes its connected player out, or he would stay a Stand user beside the next tests
    private static void stepOrLeave(Runnable leave, Runnable step) {
        try {
            step.run();
        }
        catch (RuntimeException failure) {
            leave.run();
            throw failure;
        }
    }

    private static void scene(GameTestHelper helper, Consumer<Scene> body) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "YTDisguise"));
        Vec3 feet = sceneFeet(helper);
        player.setPos(feet.x, feet.y, feet.z);
        player.setNoGravity(true);
        level.addNewPlayer(player);
        StandPower power = PowerClass.STAND.attachGet(player);
        power.setStand(YellowTemperanceAddon.YELLOW_TEMPERANCE.get());
        power.getStandInstance().orElseThrow().addPart(StandPart.ARMS);
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(YellowTemperanceAddon.YELLOW_TEMPERANCE.get().summon(player, power),
                "Fixture: Yellow Temperance's summon failed");
        YellowTemperanceControllerEntity controller = YellowTemperanceAddon.controller(player);
        helper.assertTrue(controller != null, "Fixture: the summon made no Yellow Temperance controller");
        assertTicks(helper, controller);
        helper.runAfterDelay(controller.summonLockTicks + 2, () -> body.accept(new Scene(level, player, power, controller)));
    }

    /*
     * The framework holds only the chunk of the one-block test cell at the entity-ticking level (simulation distance 0,
     * and a fake player holds no chunk), and a summon puts the Stand up to a block behind its user. From a cell at a
     * chunk border it stood in the next chunk, which never ticked it, so its actions never ran. Shared by the
     * module's other GameTests.
     */
    static Vec3 sceneFeet(GameTestHelper helper) {
        BlockPos cell = helper.absolutePos(new BlockPos(0, 4, 0));
        return new Vec3(insideCellChunk(cell.getX()), cell.getY(), insideCellChunk(cell.getZ()));
    }

    private static double insideCellChunk(int block) {
        int chunkMin = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(block));
        return Mth.clamp(block + 0.5D, chunkMin + SCENE_CHUNK_MARGIN, chunkMin + 16 - SCENE_CHUNK_MARGIN);
    }

    static void assertTicks(GameTestHelper helper, Entity entity) {
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(entity.blockPosition()),
                "Fixture: the " + (entity instanceof YellowTemperanceControllerEntity ? "Stand" : entity.getType().toShortString())
                        + " stands where no entity ticks, at " + entity.blockPosition());
    }

    private record Scene(ServerLevel level, FakePlayer player, StandPower power, YellowTemperanceControllerEntity controller) {
        void choose(DisguiseMode opened, DisguiseMode chosen, String selection) {
            controller.beginSelection(opened);
            DisguiseSelectionPayload.select(player, chosen.id(), selection);
        }

        float heightScale() {
            return YellowTemperanceScaleTypes.heightScale().getScaleData(player).getTargetScale();
        }

        YellowTemperanceDisguiseState state() {
            return YellowTemperanceDisguiseState.of(player);
        }

        YellowTemperanceDisguiseState throughTheClient(
                YellowTemperanceDisguiseState state, boolean firstSeen, YellowTemperanceDisguiseState previous) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
            try {
                YellowTemperanceDisguiseState.SYNC.write(buffer, state, firstSeen);
                return YellowTemperanceDisguiseState.SYNC.read(player, buffer, previous);
            }
            finally {
                buffer.release();
            }
        }

        Ability standAbility() {
            AvailableAbilities available = new AvailableAbilities();
            available.update(power, power.getMoveset());
            return available.getContextVariation(YellowTemperanceAddon.DISGUISE_STAND_NAME);
        }

        void pressStandDisable(GameTestHelper helper, Runnable then) {
            Ability disable = standAbility();
            BufferingState buffering = BufferingState.clickOnly();
            disable.onKeyPress(level, player, null, InputMethod.CLICK, 0.0F, buffering);
            helper.assertTrue(buffering.isActionSuccess, "Fixture: the server did not accept the press of " + disable);
            helper.runAfterDelay(5, () -> {
                try {
                    then.run();
                }
                finally {
                    if (power.hasPower() && power.isSummoned()) {
                        power.getPowerType().forceUnsummon(player, power);
                    }
                    if (!player.isRemoved()) {
                        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                    }
                }
            });
        }
    }
}
