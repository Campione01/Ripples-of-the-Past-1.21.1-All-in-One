package rotp.addons.nutkingcall;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.nutkingcall.block.NutKingCallNailBlock;
import rotp.addons.nutkingcall.entity.NutKingCallAttachedNailEntity;
import rotp.addons.nutkingcall.network.NailStatePayload;
import rotp.addons.nutkingcall.state.NailState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NutKingCallAddon.MOD_ID)
public final class NutKingCallEvents {
    /** 1.16 NutKingCallPinEvents: a pin lasts 600 ticks, and while its owner stays alive within 10 blocks. */
    public static final int PIN_DURATION_TICKS = 600;
    public static final double UNPIN_DISTANCE_SQ = 100.0D;

    private NutKingCallEvents() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(NutKingCallAddon.NUT_KING_CALL_ENTITY.get(), StandEntity.createAttributes().build());
    }

    // 1.16 LivingUpdateEvent, which ran before the entity's own tick
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) {
            return;
        }
        NailState state = NailState.getExisting(entity);
        if (state == null || !state.isPinned()) {
            return;
        }
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        entity.hurtMarked = true;
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 100, false, false, false));
        boolean timeout = entity.tickCount - state.getPinStartTick() > PIN_DURATION_TICKS;
        boolean farAway = false;
        UUID ownerUUID = state.getPinOwnerUUID();
        if (ownerUUID != null) {
            LivingEntity owner = findEntityByUUID(entity.level(), ownerUUID);
            if (owner == null || !owner.isAlive() || owner.distanceToSqr(entity) > UNPIN_DISTANCE_SQ) {
                farAway = true;
            }
        }
        if (timeout || farAway) {
            NutKingCallAttachedNailEntity.unpin(entity.level(), entity, true);
        }
    }

    @Nullable
    private static LivingEntity findEntityByUUID(Level level, UUID uuid) {
        if (level instanceof ServerLevel serverLevel && serverLevel.getEntity(uuid) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    // 1.16: a player cannot break the block a pinning nail holds
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        Level level = player.level();
        BlockPos pos = event.getPos();
        for (NutKingCallAttachedNailEntity nail : level.getEntitiesOfClass(
                NutKingCallAttachedNailEntity.class, new AABB(pos).inflate(1.0D))) {
            if (nail.isPinned() && pos.equals(nail.getPinnedBlockPos())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // 1.16 NailClimbEvents: climbing a nail with a nut on it
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        NailState state = NailState.getExisting(player);
        if (state == null || !state.isWallClimbing()) {
            return;
        }
        if (!NutKingCallNailBlock.isClimbable(player.level(), state.getClimbStartPos())) {
            state.setWallClimbing(false);
            return;
        }
        player.fallDistance = 0.0F;
        if (player.isShiftKeyDown()) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0D, -0.1D, 0.0D));
        }
        else if (player.zza > 0.0F) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0D, 0.1D, 0.0D));
        }
        else if (player.zza < 0.0F) {
            player.setDeltaMovement(player.getDeltaMovement().add(0.0D, -0.1D, 0.0D));
        }
        else {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(0.0D, motion.y * 0.5D, 0.0D);
        }
        player.hurtMarked = true;
    }

    // 1.16 NailCapabilitySync: a player that starts tracking an entity gets its nail state
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity target
                && event.getEntity() instanceof ServerPlayer player
                && !(player instanceof FakePlayer)) {
            NailState state = NailState.getExisting(target);
            if (state != null) {
                PacketDistributor.sendToPlayer(player, NailStatePayload.of(state));
            }
        }
    }

    // a player's own client had no other way to learn a pin it kept over a relog
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            NailState state = NailState.getExisting(player);
            if (state != null) {
                state.setEntity(player);
                PacketDistributor.sendToPlayer(player, NailStatePayload.of(state));
            }
        }
    }
}
