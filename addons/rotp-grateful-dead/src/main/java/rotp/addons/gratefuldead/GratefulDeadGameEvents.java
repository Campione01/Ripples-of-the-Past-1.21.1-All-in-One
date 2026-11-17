package rotp.addons.gratefuldead;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.gratefuldead.ability.AgingInjectAbility;
import rotp.addons.gratefuldead.compat.TemperatureCompat;
import rotp.addons.gratefuldead.service.AgingService;
import rotp.addons.gratefuldead.service.GrabStrength;
import rotp.addons.gratefuldead.state.AgingRateState;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import rotp.addons.gratefuldead.service.AgingText;
import rotp.addons.gratefuldead.state.BlockAgingData;
import rotp.addons.gratefuldead.state.ItemAgingState;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandMeleeReach;
import rotp.addons.gratefuldead.entity.GratefulDeadStandEntity;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = GratefulDeadAddon.MOD_ID)
public final class GratefulDeadGameEvents {
    private static final Map<UUID, FoodSnapshot>
            AGED_FOOD_STARTS = new HashMap<>();
    private static final Set<UUID>
            ACTIVE_GRATEFUL_DEAD_OWNERS =
                    new HashSet<>();
    private static final Set<UUID> MINING_THIS_TICK =
            new HashSet<>();

    private GratefulDeadGameEvents() {}

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GrabStrength.forgetSession();
        AgingInjectAbility.forgetSession();
    }

    @SubscribeEvent
    public static void onEntityTick(
            EntityTickEvent.Post event) {
        if (!(event.getEntity().level()
                instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof GratefulDeadStandEntity stand) {
            GrabStrength.tickStand(stand);
            AgingInjectAbility.tickStand(stand);
        }
        if (event.getEntity()
                instanceof LivingEntity living) {
            if (living.hasData(
                    GratefulDeadAddon.LIVING_AGING)) {
                LivingAgingState.get(living)
                        .tickServer(level, living);
                float progress =
                        LivingAgingState.get(living)
                                .progress();
                // 0.1.13 tickAgedPlayerHunger: any aging makes hungry.
                float hunger = (float) LivingAgingState
                        .easeOutExpo(progress);
                if (hunger > 0.0F
                        && living instanceof Player player
                        && living.tickCount % 20 == 0) {
                    player.causeFoodExhaustion(
                            0.04F + hunger * 0.1F);
                    float targetMaximum =
                            12.0F - hunger * 8.0F;
                    float saturation = player.getFoodData()
                            .getSaturationLevel();
                    if (saturation > targetMaximum) {
                        float drain =
                                0.08F + hunger * 0.1F;
                        player.getFoodData().setSaturation(
                                Math.max(
                                        targetMaximum,
                                        saturation - drain));
                    }
                }
                if (progress > 0.0F
                        && living.tickCount % 20 == 0) {
                    level.sendParticles(
                            net.minecraft.core.particles
                                    .ParticleTypes.ASH,
                            living.getX(),
                            living.getY(0.5D),
                            living.getZ(),
                            Math.max(
                                    1,
                                    (int) (progress * 4.0F)),
                            living.getBbWidth() * 0.35D,
                            living.getBbHeight() * 0.35D,
                            living.getBbWidth() * 0.35D,
                            0.005D);
                }
            }
            if (living instanceof Player player) {
                trackOwnerPower(level, player);
                AgingService.tickPlayerInventory(player);
                tickAgingRate(player);
                // 0.1.11 AgingBlockServerEvents.onPlayerTick: players only.
                BlockAgingData.get(level)
                        .crumbleOnContact(level, player.getBoundingBox());
            }
        }
        else if (event.getEntity()
                instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            if (stack.has(
                    GratefulDeadAddon.ITEM_AGING)) {
                ItemStack next =
                        AgingService.tickItemState(
                                level.getServer(), stack);
                if (next.isEmpty()) {
                    item.discard();
                }
                else {
                    item.setItem(next);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event) {
        for (ServerLevel level :
                event.getServer().getAllLevels()) {
            BlockAgingData.get(level).tick(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.hasData(
                GratefulDeadAddon.LIVING_AGING)) {
            return;
        }
        float progress =
                LivingAgingState.get(target).progress();
        // 0.1.13 getDamageTakenMultiplier: from 30% up to x1.85 at full aging.
        float multiplier = (float) LivingAgingState.easeInCirc(
                LivingAgingState.rangeT(progress, 0.3F, 1.0F));
        if (multiplier <= 0.0F) {
            return;
        }
        float damageScale =
                1.0F + 0.85F * multiplier;
        if (event.getSource().is(DamageTypes.FALL)) {
            damageScale += 0.35F * multiplier;
        }
        float originalAmount = event.getAmount();
        float scaledAmount = originalAmount * damageScale;
        if (Float.isFinite(originalAmount) && Float.isInfinite(scaledAmount)) {
            scaledAmount = Math.copySign(Float.MAX_VALUE, scaledAmount);
        }
        event.setAmount(scaledAmount);
    }

    @SubscribeEvent
    public static void onTimeStopPreStart(
            TimeStopLifecycleEvent.PreStart event) {
        if (!(event.getLevel().getEntity(
                        event.getInstance().userId())
                instanceof LivingEntity user)
                || !user.hasData(
                        GratefulDeadAddon.LIVING_AGING)) {
            return;
        }
        float progress =
                LivingAgingState.get(user).progress();
        if (progress <= 0.0F
                || LivingAgingState.get(user).selfInflicted(user)) {
            return;
        }
        TimeStopState.Instance original =
                event.getInstance();
        int agedTicks =
                AgingService.agedActiveTimeStopTicks(
                        original.ticksLeft(), progress);
        if (agedTicks <= 0) {
            event.setCanceled(true);
            return;
        }
        event.setInstance(
                original.withTiming(
                        agedTicks,
                        Math.min(
                                original.totalTicks(),
                                agedTicks)));
    }

    @SubscribeEvent
    public static void onFoodStarted(
            LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        ItemAgingState state = event.getItem().get(
                GratefulDeadAddon.ITEM_AGING);
        if (state != null
                && state.progress() > 0.0F
                && event.getItem().getFoodProperties(
                                player)
                        != null) {
            AGED_FOOD_STARTS.put(
                    player.getUUID(),
                    new FoodSnapshot(
                            player.getFoodData()
                                    .getFoodLevel(),
                            player.getFoodData()
                                    .getSaturationLevel(),
                            state.progress()));
        }
        else {
            AGED_FOOD_STARTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onFoodStopped(
            LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            AGED_FOOD_STARTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onFoodFinished(
            LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ItemAgingState state =
                event.getItem().get(
                        GratefulDeadAddon.ITEM_AGING);
        FoodSnapshot snapshot =
                event.getEntity() instanceof Player player
                        ? AGED_FOOD_STARTS.remove(
                                player.getUUID())
                        : null;
        float progress = snapshot != null
                ? snapshot.progress()
                : state != null ? state.progress() : 0.0F;
        if (progress <= 0.0F
                || event.getItem().getFoodProperties(
                                event.getEntity())
                        == null) {
            return;
        }
        if (snapshot != null
                && event.getEntity()
                        instanceof Player player) {
            player.getFoodData().setFoodLevel(
                    snapshot.foodLevel());
            player.getFoodData().setSaturation(
                    snapshot.saturation());
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetHealthPacket(
                        player.getHealth(),
                        player.getFoodData().getFoodLevel(),
                        player.getFoodData().getSaturationLevel()));
            }
        }
        int duration = Math.clamp(
                20 + Math.round(80.0F * progress),
                20,
                100);
        event.getEntity().addEffect(
                new MobEffectInstance(
                        MobEffects.HUNGER,
                        duration,
                        0,
                        false,
                        true,
                        true));
        event.getEntity().addEffect(
                new MobEffectInstance(
                        MobEffects.CONFUSION,
                        duration,
                        0,
                        false,
                        true,
                        true));
    }

    @SubscribeEvent
    public static void onBlockDrops(
            BlockDropsEvent event) {
        BlockAgingData data =
                BlockAgingData.get(event.getLevel());
        BlockAgingData.Entry entry =
                data.entry(event.getPos());
        if (entry == null) {
            return;
        }
        if (event.getState().getBlock()
                        instanceof CropBlock crop
                && crop.isMaxAge(event.getState())) {
            var youngDrops = Block.getDrops(
                    crop.getStateForAge(0),
                    event.getLevel(),
                    event.getPos(),
                    event.getBlockEntity(),
                    event.getBreaker(),
                    event.getTool());
            event.getDrops().clear();
            for (ItemStack stack : youngDrops) {
                ItemEntity drop = new ItemEntity(
                        event.getLevel(),
                        event.getPos().getX() + 0.5D,
                        event.getPos().getY() + 0.5D,
                        event.getPos().getZ() + 0.5D,
                        stack);
                drop.setDefaultPickUpDelay();
                event.getDrops().add(drop);
            }
        }
        for (ItemEntity drop : event.getDrops()) {
            if (!(drop.getItem().getItem()
                            instanceof BlockItem blockItem)
                    || blockItem.getBlock()
                            != event.getState().getBlock()) {
                continue;
            }
            drop.getItem().set(
                    GratefulDeadAddon.ITEM_AGING,
                    new ItemAgingState(
                            entry.progress(),
                            java.util.Optional.of(
                                    entry.owner()),
                            entry.ownerGeneration(),
                            entry.fading()));
        }
        data.remove(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlace(
            BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel()
                instanceof ServerLevel level)
                || !(event.getEntity()
                        instanceof LivingEntity living)) {
            return;
        }
        for (net.minecraft.world.InteractionHand hand :
                net.minecraft.world.InteractionHand.values()) {
            ItemAgingState state =
                    living.getItemInHand(hand)
                            .get(
                                    GratefulDeadAddon
                                            .ITEM_AGING);
            if (state != null) {
                ItemStack stack =
                        living.getItemInHand(hand);
                if (!(stack.getItem()
                                instanceof BlockItem blockItem)
                        || blockItem.getBlock()
                                != event.getPlacedBlock()
                                        .getBlock()) {
                    continue;
                }
                if (AgingService.isSeedItem(stack)) {
                    event.setCanceled(true);
                    return;
                }
                BlockAgingData.get(level).inherit(
                        level, event.getPos(), state);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (!(event.getLevel()
                instanceof ServerLevel level)) {
            return;
        }
        BlockAgingData.Entry entry =
                BlockAgingData.get(level)
                        .entry(event.getPos());
        if (entry == null
                || entry.progress() <= 0.0F) {
            return;
        }
        event.setCanceled(true);
        event.setSuccessful(false);
    }

    @SubscribeEvent
    public static void onCropGrow(
            CropGrowEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level
                && BlockAgingData.get(level)
                                .progress(event.getPos())
                        > 0.0F) {
            event.setResult(
                    CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    @SubscribeEvent
    public static void onNameFormat(
            PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (player.hasData(
                        GratefulDeadAddon.LIVING_AGING)
                && LivingAgingState.get(player).progress()
                        >= 0.62F) {
            event.setDisplayname(
                    AgingText.obfuscatedName(
                            event.getDisplayname()));
        }
    }

    @SubscribeEvent
    public static void onServerChat(
            ServerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasData(
                        GratefulDeadAddon.LIVING_AGING)
                && LivingAgingState.get(player).progress()
                        >= 0.62F) {
            event.setMessage(
                    AgingText.obfuscatedMessage(
                            event.getRawText()));
        }
    }

    @SubscribeEvent
    public static void onChunkSent(
            ChunkWatchEvent.Sent event) {
        BlockAgingData.get(event.getLevel())
                .syncChunk(
                        event.getPlayer(),
                        event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(
            LivingDeathEvent event) {
        if (event.isCanceled()
                || event.getEntity().level().isClientSide()) {
            return;
        }
        UUID playerId =
                event.getEntity().getUUID();
        AGED_FOOD_STARTS.remove(playerId);
        if (event.getEntity().hasData(GratefulDeadAddon.LIVING_AGING)) {
            LivingAgingState.get(event.getEntity())
                    .clearImmediately(event.getEntity());
        }
        requestOwnerFade(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId =
                event.getEntity().getUUID();
        AGED_FOOD_STARTS.remove(playerId);
        MINING_THIS_TICK.remove(playerId);
        requestOwnerFade(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        AGED_FOOD_STARTS.remove(
                event.getEntity().getUUID());
    }

    private static boolean finisherSweep;

    // Next Album 0.1.13 TheGratefulDeadFinisher.punchEntity: the finisher
    // breaks a guard 60% of the time and sweeps half its damage around the
    // target.
    @SubscribeEvent
    public static void onFinisherHit(LivingDamageEvent.Post event) {
        if (finisherSweep
                || !(event.getSource().getDirectEntity()
                        instanceof GratefulDeadStandEntity stand)
                || stand.level().isClientSide()
                || !isFinisher(stand.getCurStandAction())) {
            return;
        }
        LivingEntity target = event.getEntity();
        // owner T2: past the base reach the guard break weakens with the
        // strike; the sweep follows the hit's already weakened damage
        StandEntityHeavyPunchAbility.disableShieldOnHit(target, true,
                0.6F * StandMeleeReach.strikeFactor(stand, target));
        float sweepDamage = event.getOriginalDamage() * 0.5F;
        if (sweepDamage <= 0.0F) {
            return;
        }
        LivingEntity user = stand.getUser();
        finisherSweep = true;
        try {
            for (LivingEntity other : target.level().getEntitiesOfClass(
                    LivingEntity.class,
                    target.getBoundingBox().inflate(0.5D, 0.0D, 0.5D),
                    entity -> entity != target && entity != stand
                            && entity != user && entity.isAlive()
                            && !(entity instanceof rotp.core.powersystem.standpower.entity.StandEntity other
                                    && other.getUser() == user))) {
                other.hurt(event.getSource(), sweepDamage);
            }
        } finally {
            finisherSweep = false;
        }
    }

    private static boolean isFinisher(
            rotp.core.powersystem.entityaction.EntityActionInstance action) {
        if (action == null
                || !(action.ability
                        instanceof rotp.core.powersystem.ability.Ability ability)) {
            return false;
        }
        String name = ability.getAbilityId().nameInMoveset();
        return GratefulDeadAddon.FINISHER.equals(name);
    }

    // Next Album 0.1.13 AgingRateEvents: a player's own heat on the aging rate.
    private static void tickAgingRate(Player player) {
        AgingRateState state = AgingRateState.get(player);
        if (player.isSprinting() && !player.isPassenger()) {
            state.addMoveHeat(AgingRateState.SPRINT_HEAT_PER_TICK);
        }
        if (MINING_THIS_TICK.remove(player.getUUID())) {
            state.addMoveHeat(AgingRateState.MINE_HEAT_PER_TICK);
        }
        StandPower power = StandPower.get(player);
        if (power != null && power.hasPower()) {
            state.trackStamina(power.getStamina(), power.getMaxStamina());
        }
        state.tickDecay(digestionCap());
        state.tickDownedCool(AgingRateState.isResolveDowned(player));
    }

    private static float digestionCap() {
        return TemperatureCompat.hasPreciseTemperature()
                ? AgingRateState.PRECISE_DIGESTION_CAP
                : AgingRateState.DIGESTION_CAP;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!event.getEntity().level().isClientSide()) {
            MINING_THIS_TICK.add(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player
                && !player.level().isClientSide()) {
            AgingRateState.get(player).addMoveHeat(AgingRateState.JUMP_HEAT);
        }
    }

    @SubscribeEvent
    public static void onDigest(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        FoodProperties food = event.getItem().getFoodProperties(player);
        if (food == null) {
            return;
        }
        float nutrition = food.nutrition();
        float strength = Math.clamp(
                (nutrition + food.saturation()) / 20.0F, 0.15F, 1.0F);
        if (TemperatureCompat.hasPreciseTemperature()) {
            strength *= 0.5F;
        }
        AgingRateState.get(player).addDigestion(0.45F * strength, digestionCap());
    }

    private static void requestOwnerFade(
            LivingEntity entity) {
        boolean tracked =
                ACTIVE_GRATEFUL_DEAD_OWNERS.remove(
                        entity.getUUID());
        if (entity.getServer() != null
                && (tracked
                        || AgingService.hasGratefulDead(
                                entity))) {
            AgingService.requestFade(
                    entity.getServer(),
                    entity.getUUID());
        }
    }

    private static void trackOwnerPower(
            ServerLevel level, Player player) {
        UUID playerId = player.getUUID();
        if (AgingService.hasGratefulDead(player)) {
            ACTIVE_GRATEFUL_DEAD_OWNERS.add(playerId);
        }
        else if (ACTIVE_GRATEFUL_DEAD_OWNERS.remove(
                playerId)) {
            AgingService.requestFade(
                    level.getServer(), playerId);
        }
    }

    private record FoodSnapshot(
            int foodLevel,
            float saturation,
            float progress) {}
}
