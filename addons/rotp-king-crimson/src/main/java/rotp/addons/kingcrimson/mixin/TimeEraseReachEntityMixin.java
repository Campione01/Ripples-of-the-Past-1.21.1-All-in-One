package rotp.addons.kingcrimson.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;
import rotp.addons.kingcrimson.state.KingCrimsonEraseReach;

/**
 * Owner 2026-11-07: the world does not hold a Time Erase's user back, and toward every other entity he is as a spectator
 * ("單論與其他實體間邏輯和旁觀者模式無異"), without the Spectator game mode. Every hook asks KingCrimsonEraseReach, which
 * answers from one static flag while no erase runs.
 */
@Mixin(Entity.class)
public abstract class TimeEraseReachEntityMixin {
    // Cobweb, sweet berries, powder snow.
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$notStuck(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    // Soul sand and honey.
    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$fullSpeed(CallbackInfoReturnable<Float> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getBlockJumpFactor", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$fullJump(CallbackInfoReturnable<Float> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(1.0F);
        }
    }

    // Pressure plates and tripwires.
    @Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noBlockTriggers(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    // Sculk and the warden.
    @Inject(method = "dampensVibrations", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noVibrations(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noFire(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    // Explosions and wind bursts: neither hurt nor thrown.
    @Inject(method = "ignoreExplosion", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noExplosion(Explosion explosion, CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noPiston(CallbackInfoReturnable<PushReaction> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(PushReaction.IGNORE);
        }
    }

    // Boats, shulkers and the like are not solid for him, nor he for anything.
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noSolidEntities(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this) || KingCrimsonEraseReach.isOutOfReach(other)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$notAttackable(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$projectilesPass(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    // Water and lava are not there for his movement: no drag, no burning, no splash to give him away.
    @Inject(method = "isInWater", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$notInWater(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isInLava", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$notInLava(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noSplash(CallbackInfo ci) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    // Seen on every client that knows of his erase: block dust under an unseen sprinter would give him away.
    @Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$noSprintDust(CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonEraseReach.isOutOfReach((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    // What the erase of a creature keeps in the world is on the server alone: no client is told of it.
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$onNoClient(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        if (KingCrimsonCreatureErase.isPart((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    // Blocks that answer to an entity inside them (cactus, fire, bubbles, honey, plates, dripleaf); a portal still takes him.
    @WrapWithCondition(method = "checkInsideBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;entityInside(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V"))
    private boolean kingCrimson$blocksDoNotFeelHim(BlockState state, Level level, BlockPos pos, Entity entity) {
        return state.getBlock() instanceof Portal || !KingCrimsonEraseReach.isOutOfReach(entity);
    }

    // Magma, slime, redstone ore, sculk.
    @WrapWithCondition(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;stepOn(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/entity/Entity;)V"))
    private boolean kingCrimson$noStepOn(Block block, Level level, BlockPos pos, BlockState state, Entity entity) {
        return !KingCrimsonEraseReach.isOutOfReach(entity);
    }

    @WrapWithCondition(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn("
                    + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private boolean kingCrimson$noBounce(Block block, BlockGetter level, Entity entity) {
        return !KingCrimsonEraseReach.isOutOfReach(entity);
    }

    // Farmland, turtle eggs, dripstone.
    @WrapWithCondition(method = "checkFallDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;fallOn(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/entity/Entity;F)V"))
    private boolean kingCrimson$noFallOn(Block block, Level level, BlockState state, BlockPos pos, Entity entity,
            float fallDistance) {
        return !KingCrimsonEraseReach.isOutOfReach(entity);
    }
}
