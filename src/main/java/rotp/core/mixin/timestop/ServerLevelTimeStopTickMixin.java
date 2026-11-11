package rotp.core.mixin.timestop;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.entityattachment.DataEventListeners;
import rotp.core.subsystems.time.LevelTickRateState;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;

@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeStopTickMixin {
    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelFluidTickInTimeStop(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && state.isTimeStopped(new ChunkPos(pos))) {
            ServerLevel level = (ServerLevel) (Object) this;
            state.noteFrozenTickDue(pos, fluid, true);
            ((LevelAccessor) level).scheduleTick(pos, fluid, fluid.getTickDelay(level));
            ci.cancel();
        }
    }

    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelBlockTickInTimeStop(BlockPos pos, Block block, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && state.isTimeStopped(new ChunkPos(pos)) && !(block instanceof CommandBlock)) {
            ServerLevel level = (ServerLevel) (Object) this;
            state.noteFrozenTickDue(pos, block, false);
            ((LevelAccessor) level).scheduleTick(pos, block, 1);
            ci.cancel();
        }
    }

    // Random ticks, with the snow, ice and lightning of the same call; the owner took this, 1.16 is unconfirmed here.
    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelChunkTickInTimeStop(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && !state.getInstances().isEmpty() && state.isTimeStopped(chunk.getPos())) {
            ci.cancel();
        }
    }

    // Vanilla's path for a chunk that is not ticking: the event stays queued, in order, and runs once time resumes.
    @WrapOperation(method = "runBlockEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean jojo_ripples$holdBlockEventInTimeStop(ServerLevel level, BlockPos pos, Operation<Boolean> original) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && !state.getInstances().isEmpty() && state.isTimeStopped(new ChunkPos(pos))) {
            return false;
        }
        return original.call(level, pos);
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelEntityTickInTimeStop(Entity entity, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && !(entity instanceof ItemEntity) && state.interruptTickEarly(entity)) {
            jojo_ripples$tickFrozenEntityData(entity);
            ci.cancel();
            return;
        }
        // after stopped time, which a non-living entity's own time rate never overrides
        if (!LevelTickRateState.beginEntityTick((ServerLevel) (Object) this, entity)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void jojo_ripples$cancelPassengerTickInTimeStop(Entity ridingEntity, Entity passengerEntity, CallbackInfo ci) {
        TimeStopState state = jojo_ripples$getTimeStopState();
        if (state != null && state.interruptTickEarly(passengerEntity)) {
            jojo_ripples$tickFrozenEntityData(passengerEntity);
            ci.cancel();
        }
    }

    private void jojo_ripples$tickFrozenEntityData(Entity entity) {
        var key = ModDataAttachmentTypes.DATA_EVENT_HELPER.get();
        if (entity.hasData(key)) {
            DataEventListeners data = entity.getData(key);
            data.onTick();
        }
    }

    private TimeStopState jojo_ripples$getTimeStopState() {
        ServerLevel level = (ServerLevel) (Object) this;
        var attachmentType = ModDataAttachmentTypes.TIME_STOP.get();
        return level.hasData(attachmentType) ? level.getData(attachmentType) : null;
    }
}
