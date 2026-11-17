package rotp.addons.neo_stand.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.neo_stand.NeoStandAddon;
import rotp.addons.neo_stand.ability.MovementHistory;
import rotp.addons.neo_stand.ability.TimeStopTeleportationAbility;
import rotp.core.api.simulation.EntitySimulations;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * A copy of King Crimson's Epitaph prediction carries the int id of its body, so a transport stop started for a copy
 * would stop time in the real level under the real user's id. The core says which entities are copies
 * (EntitySimulations); the transport must leave at once when its user or its performer is one.
 */
@GameTestHolder(NeoStandSimulationGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NeoStandSimulationGameTests {
    public static final String NAMESPACE = NeoStandAddon.MOD_ID;

    private NeoStandSimulationGameTests() {}

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 40, batch = "neoStandTransportCopy")
    public static void transportStopLeavesASimulatedUserOrStandAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(base);
        Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5D, base.getY() + 5.0D, chunk.getMiddleBlockZ() + 0.5D);
        ResourceLocation rule = ResourceLocation.fromNamespaceAndPath("rotp_neojojostand", "test_transport_copy");
        List<TimeStopLifecycleEvent.PreStart> preStarts = new ArrayList<>();
        Consumer<TimeStopLifecycleEvent.PreStart> onPreStart = event -> preStarts.add(event);
        List<Runnable> cleanups = new ArrayList<>();
        try {
            Player user = new Player(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "neo-transport")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            user.setNoGravity(true);
            user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(user), "Setup: the user was not added");
            cleanups.add(user::discard);
            StandPower power = PowerClass.STAND.attachGet(user);
            power.setStand(ModStands.THE_WORLD.get());
            helper.assertTrue(ModStands.THE_WORLD.get().summon(user, power), "Setup: could not summon The World");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null && stand.getUser() == user, "Setup: no summoned Stand");
            cleanups.add(() -> {
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                if (power.isSummoned()) {
                    ModStands.THE_WORLD.get().forceUnsummon(user, power);
                }
                if (!stand.isRemoved()) {
                    stand.discard();
                }
            });
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setNoGravity(true);
            stand.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
            power.setStamina(power.getMaxStamina());

            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "Setup: no cow");
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
            cow.setHealth(200.0F);
            Vec3 spot = origin.add(0.0D, 0.0D, 4.0D);
            // the transport needs a past position of its target
            for (double back = 12.0D; back >= 0.0D; back -= 4.0D) {
                cow.setPos(spot.x, spot.y, spot.z + back);
                MovementHistory.record(cow);
            }
            cow.moveTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(cow), "Setup: the cow was not added");
            cleanups.add(() -> {
                MovementHistory.clear(cow.getUUID());
                cow.discard();
            });
            user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            user.setYHeadRot(user.getYRot());

            Ability ability = power.getAbility(NeoStandAddon.TIME_STOP_TELEPORTATION);
            helper.assertTrue(ability instanceof TimeStopTeleportationAbility
                            && ability instanceof EntityActionType,
                    "Setup: The World has no transport ability: " + ability);
            NeoForge.EVENT_BUS.addListener(
                    EventPriority.LOWEST, false, TimeStopLifecycleEvent.PreStart.class, onPreStart);
            cleanups.add(() -> NeoForge.EVENT_BUS.unregister(onPreStart));
            TimeStopState state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
            float stamina = power.getStamina();
            Vec3 userAt = user.position();

            // control: with no rule the same path starts the stop, effects, invulnerability and its pre-start event
            EntityActionInstance control = press(level, (EntityActionType) ability, user, stand);
            control.actionPerformStart();
            helper.assertTrue(state.getInstance(user.getId()).isPresent() && preStarts.size() == 1
                            && TimeStopTeleportationAbility.protects(user)
                            && user.hasEffect(ModStatusEffects.TIME_STOP) && user.isInvulnerable(),
                    "Control: a user who is no copy did not start the transport stop: instance "
                            + state.getInstance(user.getId()).isPresent() + ", pre-starts " + preStarts.size());
            LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
            state.getInstance(user.getId()).ifPresent(instance -> state.removeInstance(instance.id()));
            user.removeAllEffects();
            stand.removeAllEffects();
            user.setInvulnerable(false);
            stand.setInvulnerable(false);
            user.moveTo(userAt.x, userAt.y, userAt.z);
            power.setStamina(stamina);
            preStarts.clear();
            helper.assertTrue(state.getInstance(user.getId()).isEmpty()
                            && !TimeStopTeleportationAbility.protects(user),
                    "Setup: the control left its stop behind");

            // the user a copy, then the performing Stand a copy
            List<Predicate<Entity>> copies = List.of(entity -> entity == user, entity -> entity == stand);
            List<String> names = List.of("user", "performer");
            for (int round = 0; round < copies.size(); round++) {
                String who = names.get(round);
                EntitySimulations.register(rule, copies.get(round));
                try {
                    EntityActionInstance action = press(level, (EntityActionType) ability, user, stand);
                    ActionPhase phase = action.getPhase();
                    action.actionPerformStart();
                    helper.assertTrue(state.getInstance(user.getId()).isEmpty() && preStarts.isEmpty(),
                            "A simulated " + who + " got a transport stop: instance "
                                    + state.getInstance(user.getId()).isPresent() + ", pre-starts "
                                    + preStarts.size());
                    helper.assertTrue(!TimeStopTeleportationAbility.protects(user)
                                    && !user.hasEffect(ModStatusEffects.TIME_STOP)
                                    && !stand.hasEffect(ModStatusEffects.TIME_STOP)
                                    && !user.isInvulnerable() && !stand.isInvulnerable(),
                            "A simulated " + who + " got time stop effects or invulnerability");
                    helper.assertTrue(user.position().equals(userAt) && power.getStamina() == stamina,
                            "A simulated " + who + " moved or paid stamina");
                    // startRecovery would send a phase packet by the copy's id
                    helper.assertTrue(action.getPhase() == phase,
                            "A simulated " + who + " changed the action's phase: " + action.getPhase());
                }
                finally {
                    EntitySimulations.unregister(rule);
                    LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                }
            }
        }
        finally {
            EntitySimulations.unregister(rule);
            for (int i = cleanups.size() - 1; i >= 0; i--) {
                cleanups.get(i).run();
            }
        }
        helper.succeed();
    }

    private static EntityActionInstance press(ServerLevel level, EntityActionType type, Player user, StandEntity stand) {
        EntityActionInstance action = type.initActionOnAbilityUse(level, user, stand, null);
        LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
        return action;
    }
}
