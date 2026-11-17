package rotp.addons.ger;

import java.util.EnumSet;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.time.event.BitesTheDustDetonateEvent;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.init.ModItems;
import rotp.core.init.ModSpecialActions;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import rotp.addons.ger.ability.GerReturnToZeroAbility;
import rotp.addons.ger.client.GerDisablerClientState;
import rotp.addons.ger.damage.GerDamageTypes;
import rotp.addons.ger.damage.GerUnavoidableDamage;
import rotp.addons.ger.entity.GerFieldEntity;
import rotp.addons.ger.network.GerDisablerPayloads;
import rotp.addons.ger.network.GerUndiePayloads;
import rotp.addons.ger.state.GerAttackOrigins;
import rotp.addons.ger.state.GerCoreSeams;
import rotp.addons.ger.state.GerDisabledAttacks;
import rotp.addons.ger.state.GerHitAnswerService;
import rotp.addons.ger.state.GerLivingState;
import rotp.addons.ger.state.GerPlayerState;
import rotp.addons.ger.state.GerRtzRewindService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = GoldExperienceRequiemAddon.MOD_ID)
public final class GoldExperienceRequiemGameEvents {
    public static final int EVOLUTION_TICKS = 300;
    /** 1.16 {@code GoldExperienceBeetleArrowLayer}: pose 1 until tick 20, pose 2 from tick 22. */
    public static final int ARROW_POSE_START_TICK = 20;
    public static final int ARROW_POSE_END_TICK = 22;
    public static final int EVOLUTION_VFX_DELAY_TICKS = 22;
    public static final int EVOLUTION_VFX_DURATION_TICKS = 278;
    public static final int EVOLUTION_RESOLVE_TICKS = 2900;
    public static final int MAX_UNDIE_LOOPS = 10;
    public static final int REKILL_DELAY_TICKS = 24;
    public static final int UNDIE_TELEPORT_RADIUS = 256;
    /** 1.16 UndieManager pulled the body back once its deathTime reached 15. */
    public static final int UNDIE_PULL_DEATH_TIME = 15;
    /** 1.16 DisablerMixin's message; neither the donor nor the 1.16 core translated it. */
    public static final String ON_DISABLE_MESSAGE = "on_disable";

    private GoldExperienceRequiemGameEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem()
                        != ModItems.STAND_ARROW_BEETLE.get()) {
            return;
        }
        Player player = event.getEntity();
        StandPower power = StandPower.get(player);
        if (power == null
                || !power.hasPower()
                || power.getPowerType()
                        != ModStands.GOLD_EXPERIENCE.get()) {
            return;
        }

        GerPlayerState state =
                GoldExperienceRequiemAddon.playerState(player);
        boolean ready = power.getResolveLevel()
                        >= power.getMaxResolveLevel()
                && !state.isEvolving();
        event.setCanceled(true);
        event.setCancellationResult(
                ready ? InteractionResult.SUCCESS
                        : InteractionResult.FAIL);
        if (player.level().isClientSide()) {
            return;
        }
        if (!ready || !(player instanceof ServerPlayer serverPlayer)) {
            player.displayClientMessage(
                    Component.translatable(
                            state.isEvolving()
                                    ? "geraddon.evolution.in_progress"
                                    : "geraddon.evolution.max_resolve"),
                    true);
            return;
        }

        event.getItemStack().shrink(1);
        if (!power.isSummoned()) {
            ModStands.GOLD_EXPERIENCE.get()
                    .summon(player, power);
        }
        state.beginEvolution(EVOLUTION_TICKS);
        syncState(serverPlayer);
        player.addEffect(new MobEffectInstance(
                MobEffects.LEVITATION,
                EVOLUTION_TICKS,
                0,
                false,
                false,
                true));
        play(serverPlayer,
                GoldExperienceRequiemAddon
                        .GER_EVOLUTION_VOICELINE1.get());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        tickEvolution(player);
        tickFlight(player);
    }

    private static void tickEvolution(ServerPlayer player) {
        GerPlayerState state =
                GoldExperienceRequiemAddon.playerState(player);
        if (!state.isEvolving()) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (!player.isAlive()
                || power == null
                || power.getPowerType()
                        != ModStands.GOLD_EXPERIENCE.get()) {
            cancelEvolution(player, state);
            return;
        }

        state.advanceEvolution();
        if (state.evolutionElapsed() >= ARROW_POSE_START_TICK
                && state.evolutionElapsed() <= ARROW_POSE_END_TICK) {
            // the beetle-arrow layer eases between its two poses over these ticks
            syncState(player);
        }
        switch (state.evolutionElapsed()) {
            case 20 -> play(
                    player,
                    GoldExperienceRequiemAddon
                            .GER_EVOLUTION_INSERT.get());
            case EVOLUTION_VFX_DELAY_TICKS ->
                    player.serverLevel().addFreshEntity(
                            new GerFieldEntity(
                                    player,
                                    GerFieldEntity.EVOLUTION,
                                    2.5F,
                                    EVOLUTION_VFX_DURATION_TICKS));
            case 46 -> play(
                    player,
                    GoldExperienceRequiemAddon
                            .GER_EVOLUTION_VOICELINE2.get());
            case 280 -> play(
                    player,
                    GoldExperienceRequiemAddon
                            .GER_EVOLUTION_END.get());
            default -> {
            }
        }
        if (state.evolutionRemaining() > 0) {
            return;
        }

        StandInstance current =
                power.getStandInstance().orElse(null);
        if (current == null
                || current.getStandType()
                        != ModStands.GOLD_EXPERIENCE.get()) {
            cancelEvolution(player, state);
            return;
        }
        StandInstance replacement = new StandInstance(
                GoldExperienceRequiemAddon.GER_STAND.get());
        EnumSet<StandPart> parts = current.getAllParts();
        for (StandPart part : StandPart.values()) {
            if (!parts.contains(part)) {
                replacement.removePart(part);
            }
        }
        boolean wasSummoned = power.isSummoned();
        StandPowerTransitions.Result result =
                StandPowerTransitions.replace(
                        power,
                        current.getStandId(),
                        replacement);
        finishMovementEffects(player);
        state.clearEvolution();
        syncState(player);
        if (!result.applied()) {
            player.displayClientMessage(
                    Component.translatable(
                            "geraddon.evolution.failed"),
                    true);
            return;
        }
        if (wasSummoned) {
            GoldExperienceRequiemAddon.GER_STAND.get()
                    .summon(player, power);
        }
        player.hurt(
                player.damageSources().playerAttack(player),
                1.0F);
        player.addEffect(new MobEffectInstance(
                ModStatusEffects.RESOLVE,
                EVOLUTION_RESOLVE_TICKS,
                5,
                false,
                false,
                true));
        player.displayClientMessage(
                Component.translatable(
                        "geraddon.evolution.complete"),
                true);
    }

    private static void cancelEvolution(
            ServerPlayer player, GerPlayerState state) {
        finishMovementEffects(player);
        state.clearEvolution();
        syncState(player);
    }

    private static void syncState(ServerPlayer player) {
        player.syncData(GoldExperienceRequiemAddon.PLAYER_STATE.get());
    }

    private static void finishMovementEffects(
            ServerPlayer player) {
        player.removeEffect(MobEffects.LEVITATION);
        player.addEffect(new MobEffectInstance(
                MobEffects.SLOW_FALLING,
                100,
                0,
                false,
                false,
                true));
    }

    private static void tickFlight(ServerPlayer player) {
        GerPlayerState state =
                GoldExperienceRequiemAddon.playerState(player);
        boolean shouldFly =
                GoldExperienceRequiemAddon.isGerUser(player)
                        && player.hasEffect(ModStatusEffects.RESOLVE);
        if (shouldFly) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                state.setFlightGranted(true);
                player.onUpdateAbilities();
            }
            return;
        }
        if (!state.flightGranted()) {
            return;
        }
        state.setFlightGranted(false);
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(living);
        GerHitAnswerService.tick(living, state);
        tickLockdowns(living, state);
        if (living instanceof ServerPlayer player
                && state.markActionsDisabledSent(
                        state.actionsDisabled(),
                        state.disablerActive())) {
            PacketDistributor.sendToPlayer(player,
                    new GerDisablerPayloads.StatePayload(
                            player.getId(),
                            state.actionsDisabled(),
                            state.disablerActive()));
        }
    }

    private static void tickLockdowns(
            LivingEntity living, GerLivingState state) {
        if (state.returnToZeroDisabled()) {
            state.enforceReturnToZeroDisable(living);
            interruptAction(living);
            StandPower power = StandPower.get(living);
            if (power != null
                    && power.getSummonedStandEntity()
                            instanceof StandEntity stand) {
                interruptAction(stand);
            }
            state.tickReturnToZeroDisable();
            return;
        }
        if (state.disablerActive()) {
            // The lockdown's own effects are no hit another Requiem answers.
            GerHitAnswerService.applyingOwnEffects(() -> {
                living.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        4,
                        4,
                        false,
                        false,
                        false));
                living.addEffect(new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        4,
                        4,
                        false,
                        false,
                        false));
            });
            GerDisabledAttacks.tick(living);
            Vec3 motion = living.getDeltaMovement();
            if (motion.y > 0.0D) {
                living.setDeltaMovement(
                        motion.x, 0.0D, motion.z);
                living.hasImpulse = true;
            }
            interruptAction(living);
            StandPower power = StandPower.get(living);
            if (power != null
                    && power.getSummonedStandEntity()
                            instanceof StandEntity stand) {
                interruptAction(stand);
            }
            state.tickDisabler();
        }
        if (state.undieActive()
                && state.tickRekillDelay()
                && living.isAlive()) {
            rekill(living, state);
        }
    }

    /**
     * The owner, 2026-11-08: nothing saves a loop victim. The loop kills
     * with its own damage type, which passes invulnerability, armour, effects
     * and enchantments, and where a hook still keeps the hit from its victim
     * the death is finished directly.
     */
    private static void rekill(
            LivingEntity living, GerLivingState state) {
        DamageSource source = deathLoopSource(living, state);
        living.invulnerableTime = 0;
        // What kills as true damage and no more: whatever turns a hit back or passes it on gets no hit without end.
        living.hurt(source, Math.max(
                living.getHealth() + living.getAbsorptionAmount(), 1.0F));
        if (!living.isAlive()
                || state.inUndieDeath()
                || !state.undieActive()) {
            return;
        }
        if (state.undieDeaths() < MAX_UNDIE_LOOPS) {
            holdUndieDeath(living, state);
        }
        else {
            living.setHealth(0.0F);
            living.die(source);
        }
    }

    private static DamageSource deathLoopSource(
            LivingEntity living, GerLivingState state) {
        LivingEntity caster = state.undieCaster() != null
                        && living.level() instanceof ServerLevel level
                        && level.getEntity(state.undieCaster())
                                instanceof LivingEntity found
                ? found
                : null;
        Entity stand = caster != null
                ? GoldExperienceRequiemAddon.activeStand(caster)
                : null;
        return GerDamageTypes.source(
                living.level(),
                GerDamageTypes.DEATH_LOOP,
                stand != null ? stand : caster,
                caster);
    }

    /**
     * Creative and spectator players stay out, and a Requiem holder, with his
     * Stand out or not, is never put in the loop (owner: "死亡循環對黃鎮持有者不生效").
     */
    public static boolean canEnterUndieLoop(LivingEntity victim) {
        return !GerUnavoidableDamage.staysOut(victim)
                && !GoldExperienceRequiemAddon.isGerUser(victim);
    }

    /** The Finisher Punch that is about to kill starts the loop with its own hit. */
    public static void armUndieLoop(
            LivingEntity victim, LivingEntity caster) {
        GoldExperienceRequiemAddon.livingState(victim)
                .armUndie(caster.getUUID());
    }

    /**
     * After the killing Finisher Punch: a death something kept from the
     * victim is finished directly, and a punch another rule made non-lethal
     * leaves no mark waiting.
     */
    public static void settleUndieKill(
            LivingEntity victim, boolean nonLethal) {
        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(victim);
        if (!state.undieActive()
                || state.inUndieDeath()
                || !victim.isAlive()) {
            return;
        }
        if (!nonLethal) {
            holdUndieDeath(victim, state);
        }
        else if (state.undieDeaths() == 0) {
            state.clearUndie();
        }
    }

    // Held just above 0 health until the victim's next tick starts the visible dying.
    private static void holdUndieDeath(
            LivingEntity living, GerLivingState state) {
        if (!state.inUndieDeath()) {
            state.acceptUndieDeath();
        }
        living.setHealth(Float.MIN_VALUE);
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(living);
        if (state.undieActive() && !canEnterUndieLoop(living)) {
            // a victim who gained Requiem, or went Creative, leaves the loop
            leaveUndieLoop(living, state);
            return;
        }
        if (state.undiePhase() != GerLivingState.UndiePhase.PENDING
                && state.inUndieDeath()
                && !living.isDeadOrDying()) {
            // brought back by something else (a respawn, a command)
            endUndieDeath(living, state);
            return;
        }
        switch (state.undiePhase()) {
            case PENDING -> startUndieDying(living, state);
            case DYING -> {
                if (living.deathTime >= UNDIE_PULL_DEATH_TIME) {
                    state.startUndiePull();
                    sendUndiePayload(living,
                            new GerUndiePayloads.StatePayload(
                                    living.getId(),
                                    GerUndiePayloads.PULLING));
                    pullBack(living, state);
                }
            }
            case PULLING -> pullBack(living, state);
            default -> {
                return;
            }
        }
        if (state.inUndieDeath()) {
            keepFromActing(living, state);
        }
    }

    /**
     * An Undie loop death is not a death: the lethal hit never reaches
     * die(), so it drops nothing and runs no death effect, and a totem of
     * undying in hand is never asked. The victim is held just above 0 health
     * until its next tick starts the visible dying.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide() || !living.isDeadOrDying()) {
            return;
        }
        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(living);
        if (!state.undieActive()
                || state.undieDeaths() >= MAX_UNDIE_LOOPS) {
            return;
        }
        holdUndieDeath(living, state);
    }

    private static void startUndieDying(
            LivingEntity living, GerLivingState state) {
        state.startUndieDying();
        // Closed while still alive, so the menu hands its items back
        // instead of throwing them out.
        closeOpenContainer(living);
        // Sent before the 0 health, so the victim's own client holds back
        // its death screen.
        sendUndiePayload(living,
                new GerUndiePayloads.StatePayload(
                        living.getId(), GerUndiePayloads.DYING));
        living.setHealth(0.0F);
        living.deathTime = 0;
        if (!(living instanceof Player)) {
            living.setPose(Pose.DYING);
        }
        living.level().broadcastEntityEvent(living, EntityEvent.DEATH);
    }

    private static void pullBack(
            LivingEntity living, GerLivingState state) {
        living.deathTime = Math.max(living.deathTime - 2, 0);
        if (living.deathTime <= 0) {
            reviveFromUndieDeath(living, state);
        }
    }

    /** 1.16 UndieManager.reviveEntity and finishServerRevive. */
    private static void reviveFromUndieDeath(
            LivingEntity living, GerLivingState state) {
        standUpFromUndieDeath(living);
        teleportWithinLoadedArea(living);
        endUndieDeath(living, state);
    }

    private static void standUpFromUndieDeath(LivingEntity living) {
        // Still dead here, so the tracker resets as on a real death.
        living.getCombatTracker().recheckStatus();
        living.setHealth(Math.min(living.getMaxHealth(), 1.0F));
        living.deathTime = 0;
        living.hurtTime = 0;
        living.hurtDuration = 0;
        living.swingTime = 0;
        living.attackAnim = 0.0F;
        living.invulnerableTime = 0;
        living.fallDistance = 0.0F;
        living.setPose(Pose.STANDING);
        living.clearFire();
    }

    private static void leaveUndieLoop(
            LivingEntity living, GerLivingState state) {
        if (state.inUndieDeath()) {
            standUpFromUndieDeath(living);
            sendUndiePayload(living,
                    new GerUndiePayloads.RevivePayload(
                            living.getId(), living.getHealth()));
        }
        state.clearUndie();
    }

    private static void endUndieDeath(
            LivingEntity living, GerLivingState state) {
        int nextDeath = state.undieDeaths() + 1;
        state.recordUndieRevival(
                nextDeath < MAX_UNDIE_LOOPS
                        ? REKILL_DELAY_TICKS
                        : 1);
        sendUndiePayload(living,
                new GerUndiePayloads.RevivePayload(
                        living.getId(), living.getHealth()));
    }

    private static void keepFromActing(
            LivingEntity living, GerLivingState state) {
        if (living.isUsingItem()) {
            living.stopUsingItem();
        }
        closeOpenContainer(living);
        interruptAction(living);
        StandPower power = StandPower.get(living);
        if (power != null && power.hasPower()) {
            keepStandAsItWas(living, power, state);
        }
        if (power != null
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand) {
            interruptAction(stand);
        }
    }

    private static void closeOpenContainer(LivingEntity living) {
        if (living instanceof ServerPlayer player
                && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    /**
     * The core takes a summon or unsummon request without asking whether the
     * user is alive (1.16 ClToggleStandSummonPacket did), so the window puts
     * the Stand back the way it was.
     */
    private static void keepStandAsItWas(
            LivingEntity living,
            StandPower power,
            GerLivingState state) {
        StandType type = power.getPowerType();
        boolean wasSummoned =
                state.undieStandSummoned(power.isSummoned());
        int armsShown = state.undieStandArms(
                standArms(power.getSummonedStandEntity()));
        if (power.getSummonedStandEntity() instanceof StandEntity stand
                && stand.getCurStandAction() != null
                && stand.getCurStandAction().ability
                        == ModSpecialActions.STAND_UNSUMMON.get()) {
            stand.stopRetraction();
        }
        if (power.isSummoned() && !wasSummoned) {
            type.forceUnsummon(living, power);
        }
        else if (!power.isSummoned() && wasSummoned) {
            type.summon(living, power);
        }
        // The summon key turns an arms-only Stand into a full summon
        // without changing isSummoned.
        if (armsShown >= 0
                && power.getSummonedStandEntity() instanceof StandEntity stand
                && !stand.isArmsOnlyMode()) {
            stand.setArmsOnlyMode(
                    (armsShown & 1) != 0, (armsShown & 2) != 0);
        }
    }

    private static int standArms(@Nullable StandEntity stand) {
        if (stand == null || !stand.isArmsOnlyMode()) {
            return -1;
        }
        return (stand.showArm(InteractionHand.MAIN_HAND) ? 1 : 0)
                | (stand.showArm(InteractionHand.OFF_HAND) ? 2 : 0);
    }

    private static void sendUndiePayload(
            LivingEntity living, CustomPacketPayload payload) {
        if (living.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static boolean inUndieDeath(Entity entity) {
        return entity instanceof LivingEntity living
                && !living.level().isClientSide()
                && GoldExperienceRequiemAddon.livingState(living)
                        .inUndieDeath();
    }

    private static void cancelIfInUndieDeath(
            Entity entity, ICancellableEvent event) {
        if (inUndieDeath(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseItemWhileUndying(
            PlayerInteractEvent.RightClickItem event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlockWhileUndying(
            PlayerInteractEvent.RightClickBlock event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHitBlockWhileUndying(
            PlayerInteractEvent.LeftClickBlock event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractWhileUndying(
            PlayerInteractEvent.EntityInteract event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractAtWhileUndying(
            PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStartUsingWhileUndying(
            LivingEntityUseItemEvent.Start event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSwapHandsWhileUndying(
            LivingSwapItemsEvent.Hands event) {
        cancelIfInUndieDeath(event.getEntity(), event);
    }

    /**
     * 1.16 DisablerMixin and DisablerNonStandMixin: at the head of every
     * StandAction's and NonStandAction's checkConditions, on both sides, a
     * disabled user's action was refused with on_disable, so its HUD greyed
     * the slot, its press was refused and a held action ended on its next
     * tick. The core asks this refusal in those checks (AbilityRefusals),
     * never for the manual-control key, which 1.16 sent as no action.
     * Owner Q9: another's Return to Zero lockdown lets its holder's own Return
     * to Zero through; the Disabler, which is no time state, does not.
     */
    @Nullable
    public static ConditionCheck refuseWhileDisabled(
            Ability ability, Power<?> context, LivingEntity user) {
        if (!actionsDisabled(user)
                || ability instanceof GerReturnToZeroAbility
                        && !disablerHolds(user)) {
            return null;
        }
        return ConditionCheck.createNegative(ON_DISABLE_MESSAGE);
    }

    public static boolean actionsDisabled(LivingEntity user) {
        // The server tells a disabled player's own client of itself only
        // (1.16 SyncDisablerStateS2CPacket), so no other copy on a client is.
        if (user instanceof Player player && player.isLocalPlayer()) {
            return GerDisablerClientState.isDisabled(user.getId());
        }
        return !user.level().isClientSide()
                && user.hasData(GoldExperienceRequiemAddon.LIVING_STATE)
                && GoldExperienceRequiemAddon.livingState(user)
                        .actionsDisabled();
    }

    private static boolean disablerHolds(LivingEntity user) {
        if (user instanceof Player player && player.isLocalPlayer()) {
            return GerDisablerClientState.isHeldByDisabler(user.getId());
        }
        return !user.level().isClientSide()
                && user.hasData(GoldExperienceRequiemAddon.LIVING_STATE)
                && GoldExperienceRequiemAddon.livingState(user)
                        .disablerActive();
    }

    /** Every ability a dying victim presses, Stand or not, is refused. */
    @SubscribeEvent(
            priority = EventPriority.LOWEST,
            receiveCanceled = true)
    public static void onAbilityKeyPressWhileUndying(
            RipplesAbilityKeyPressEvent event) {
        if (inUndieDeath(event.getEntity())) {
            event.setCanceled(true);
            event.newHeldInput = null;
        }
    }

    /**
     * A dying victim's toss goes back into its inventory. The toss is not
     * cancelled, because a cancelled toss deletes the item; the removed
     * entity is simply never added to the level.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTossWhileUndying(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (!inUndieDeath(player)) {
            return;
        }
        ItemEntity item = event.getEntity();
        ItemStack stack = item.getItem().copy();
        Inventory inventory = player.getInventory();
        if (inventory.getSelected().isEmpty()) {
            inventory.setItem(inventory.selected, stack.copyAndClear());
        }
        else {
            inventory.add(stack);
        }
        if (stack.isEmpty()) {
            item.discard();
        }
        else {
            item.setItem(stack);
        }
    }

    // 1.16 refused every Stand action of a disabled user, the held ones on
    // their next tick (DisablerMixin); the core's held recheck asks the
    // refusal, and this ends what it does not reach (an action no key holds,
    // one already performing). The user's own Stand actions, the time stop
    // among them, run in its Stand-power slot, apart from its own slot.
    public static void interruptAction(LivingEntity entity) {
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(entity);
        if (action != null) {
            action.startRecovery();
        }
        EntityActionInstance standPowerAction =
                LivingComponentAction.getCurEntityAction(
                        entity, PowerClass.STAND);
        if (standPowerAction != null && standPowerAction != action) {
            standPowerAction.startRecovery();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide()) {
            return;
        }
        if (living instanceof Player player) {
            GoldExperienceRequiemAddon.playerState(player)
                    .clearEvolution();
        }

        GerLivingState state =
                GoldExperienceRequiemAddon.livingState(living);
        if (!state.undieActive()) {
            return;
        }
        if (state.undieDeaths() >= MAX_UNDIE_LOOPS) {
            state.markFinalDeath();
            return;
        }

        // Only a death that skipped the damage hook (a direct die() call)
        // gets here; it is still a loop death and stays cancelled.
        event.setCanceled(true);
        if (!state.inUndieDeath()) {
            state.acceptUndieDeath();
        }
        if (state.undiePhase() == GerLivingState.UndiePhase.PENDING) {
            startUndieDying(living, state);
        }
    }

    private static void teleportWithinLoadedArea(
            LivingEntity living) {
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = living.getBlockX()
                    + living.getRandom().nextInt(
                            UNDIE_TELEPORT_RADIUS * 2 + 1)
                    - UNDIE_TELEPORT_RADIUS;
            int z = living.getBlockZ()
                    + living.getRandom().nextInt(
                            UNDIE_TELEPORT_RADIUS * 2 + 1)
                    - UNDIE_TELEPORT_RADIUS;
            BlockPos probe = new BlockPos(
                    x, living.getBlockY(), z);
            if (!level.hasChunkAt(probe)) {
                continue;
            }
            int y = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x,
                    z);
            living.teleportTo(
                    x + 0.5D, y, z + 0.5D);
            return;
        }
    }

    // A real death only: a loop death is cancelled before it gets here.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeathInMarkedArea(
            LivingDeathEvent event) {
        GerRtzRewindService.recordDeath(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        GoldExperienceRequiemAddon
                .playerState(event.getEntity())
                .copyFrom(GoldExperienceRequiemAddon.playerState(
                        event.getOriginal()));
        GoldExperienceRequiemAddon
                .livingState(event.getEntity())
                .copyFrom(GoldExperienceRequiemAddon.livingState(
                        event.getOriginal()));
    }

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event) {
        GerRtzRewindService.tick(event.getServer());
        GerAttackOrigins.tick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTimeStopAdded(
            TimeStopLifecycleEvent.Added event) {
        GerRtzRewindService.timeStopAdded(
                event.getLevel(), event.getInstance());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTimeStopRemoved(
            TimeStopLifecycleEvent.Removed event) {
        GerRtzRewindService.timeStopRemoved(
                event.getLevel(), event.getInstance());
    }

    // Requiem acts on its own will (owner Q3, Q4): no mark, no key.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTimeEraseStart(TimeEraseStartEvent event) {
        if (GerRtzRewindService.summonedRequiemWithin(
                event.getLevel(),
                event.getEraser(),
                event.getPosition(),
                GerCoreSeams.eraseAnswerChunks(event))) {
            event.setCanceled(true);
        }
    }

    // The owner, 2026-11-08: an eraser who comes near a summoned Requiem in a running erase is thrown out of it.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTimeEraseRunning(TimeEraseRunningEvent event) {
        LivingEntity holder = GerRtzRewindService.throwsEraserOut(
                event.getLevel(), event.getEraser(), event.getPosition());
        if (holder != null) {
            event.answer(
                    GoldExperienceRequiemAddon.id("return_to_zero"), holder);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBitesTheDustDetonate(
            BitesTheDustDetonateEvent event) {
        if (GerRtzRewindService.returnsDetonationToZero(
                event.getTarget())) {
            GerCoreSeams.returnDetonationToZero(event);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event) {
        GerRtzRewindService.clear(event.getServer());
        GerAttackOrigins.clear();
    }

    // Again after the levels are saved and the players logged out, which can still write; also on the crash path.
    @SubscribeEvent
    public static void onServerStopped(
            ServerStoppedEvent event) {
        GerRtzRewindService.clear(event.getServer());
        GerAttackOrigins.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            // Saved inside the window (an autosave before a crash), the
            // player would get a real death screen whose respawn loses the
            // inventory the loop death never dropped.
            GerLivingState state =
                    GoldExperienceRequiemAddon.livingState(player);
            if (state.inUndieDeath()) {
                reviveFromUndieDeath(player, state);
            }
            GerRtzRewindService.syncPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            // Saved dead, the player would rejoin to a death screen and
            // respawn without the inventory a loop death never dropped.
            GerLivingState state =
                    GoldExperienceRequiemAddon.livingState(player);
            if (state.inUndieDeath()) {
                reviveFromUndieDeath(player, state);
            }
            GerRtzRewindService.playerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            GerRtzRewindService.syncPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            GerRtzRewindService.syncPlayer(player);
            // The client's new player keeps the old id and what it was told
            // of it; 1.16 ended the lockdown here and told the client so.
            GerLivingState state =
                    GoldExperienceRequiemAddon.livingState(player);
            state.markActionsDisabledSent(
                    state.actionsDisabled(), state.disablerActive());
            PacketDistributor.sendToPlayer(player,
                    new GerDisablerPayloads.StatePayload(
                            player.getId(),
                            state.actionsDisabled(),
                            state.disablerActive()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakBlock(
            BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GerRtzRewindService.recordBlockInteraction(
                    level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaceBlock(
            BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snapshot :
                    multi.getReplacedBlockSnapshots()) {
                GerRtzRewindService.recordBlockSnapshot(snapshot);
            }
        }
        else {
            GerRtzRewindService.recordBlockSnapshot(
                    event.getBlockSnapshot());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFluidPlaceBlock(
            BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GerRtzRewindService.recordBlockInteraction(
                    level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onToolModifyBlock(
            BlockEvent.BlockToolModificationEvent event) {
        if (!event.isSimulated()
                && event.getLevel()
                        instanceof ServerLevel level) {
            GerRtzRewindService.recordBlockInteraction(
                    level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFarmlandTrample(
            BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GerRtzRewindService.recordBlockInteraction(
                    level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosionDetonate(
            ExplosionEvent.Detonate event) {
        if (!(event.getLevel()
                instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos position :
                event.getAffectedBlocks()) {
            GerRtzRewindService.recordBlockInteraction(
                    level, position);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPistonPre(
            PistonEvent.Pre event) {
        if (!(event.getLevel()
                instanceof ServerLevel level)) {
            return;
        }
        GerRtzRewindService.recordBlockInteraction(
                level, event.getPos());
        GerRtzRewindService.recordBlockInteraction(
                level, event.getFaceOffsetPos());
        var resolver = event.getStructureHelper();
        if (resolver != null && resolver.resolve()) {
            for (BlockPos position : resolver.getToPush()) {
                GerRtzRewindService.recordBlockInteraction(
                        level, position);
            }
            for (BlockPos position : resolver.getToDestroy()) {
                GerRtzRewindService.recordBlockInteraction(
                        level, position);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCropGrow(
            CropGrowEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GerRtzRewindService.recordBlockInteraction(
                    level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNeighborNotify(
            BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel()
                instanceof ServerLevel level)) {
            return;
        }
        for (var direction : event.getNotifiedSides()) {
            GerRtzRewindService.recordBlockInteraction(
                    level,
                    event.getPos().relative(direction));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlockForRtz(
            PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GerRtzRewindService.recordBlockInteraction(
                    level, event.getPos());
        }
    }

    private static void play(
            ServerPlayer player, SoundEvent sound) {
        player.serverLevel().playSound(
                null,
                player.blockPosition(),
                sound,
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }
}
