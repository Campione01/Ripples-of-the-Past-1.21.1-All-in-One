package rotp.addons.kingcrimson.mixin.epitaph;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxExplosion;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;

/**
 * The barrier on the level itself, for the room of an Epitaph prediction: block writes become overlay entries and
 * recorded ops, block reads answer from the overlay first, block entities are not there, time is the room's and an
 * explosion is the room's own. Every hook returns at once while no prediction code runs.
 */
@Mixin(value = Level.class, priority = 900)
public abstract class LevelBarrierMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().setBlock(pos, state, flags));
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomDestroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().destroyBlock(pos, dropBlock));
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomSetBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code set a block entity");
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomRemoveBlockEntity(BlockPos pos, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code removed a block entity");
            ci.cancel();
        } else if (EpitaphSandbox.mirroring()) {
            ci.cancel();
        }
    }

    @Inject(method = "blockEntityChanged", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBlockEntityChanged(BlockPos pos, CallbackInfo ci) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            EpitaphSandbox.leak("room code changed a block entity");
            ci.cancel();
        }
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            BlockState changed = EpitaphSandbox.room().overlay().get(pos);
            if (changed != null) {
                cir.setReturnValue(changed);
            }
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            BlockState changed = EpitaphSandbox.room().overlay().get(pos);
            if (changed != null) {
                cir.setReturnValue(changed.getFluidState());
            }
        }
    }

    @Inject(method = "getChunkForCollisions", at = @At("RETURN"), cancellable = true)
    private void kingCrimson$roomCollisionChunk(int chunkX, int chunkZ, CallbackInfoReturnable<BlockGetter> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed() && cir.getReturnValue() != null && !EpitaphSandbox.room().overlay().isEmpty()) {
            cir.setReturnValue(EpitaphSandbox.room().overlay().over(cir.getReturnValue()));
        }
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomGameTime(CallbackInfoReturnable<Long> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().gameTime());
        }
    }

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomDayTime(CallbackInfoReturnable<Long> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(EpitaphSandbox.room().dayTime());
        }
    }

    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;"
            + "Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Z"
            + "Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;"
            + "Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;", at = @At("HEAD"), cancellable = true)
    private void kingCrimson$roomExplode(@Nullable Entity source, @Nullable DamageSource damageSource,
            @Nullable ExplosionDamageCalculator damageCalculator, double x, double y, double z, float radius, boolean fire,
            Level.ExplosionInteraction explosionInteraction, boolean spawnParticles, ParticleOptions smallExplosionParticles,
            ParticleOptions largeExplosionParticles, Holder<SoundEvent> explosionSound, CallbackInfoReturnable<Explosion> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed()) {
            cir.setReturnValue(SandboxExplosion.explode(EpitaphSandbox.room(), (Level) (Object) this, source, damageSource,
                    damageCalculator, x, y, z, radius, fire, explosionInteraction, smallExplosionParticles,
                    largeExplosionParticles, explosionSound));
        } else if (EpitaphSandbox.mirroring()) {
            cir.setReturnValue(SandboxExplosion.dud((Level) (Object) this, source, damageSource, damageCalculator, x, y, z,
                    radius, fire, smallExplosionParticles, largeExplosionParticles, explosionSound));
        }
    }

    // epitaphVerifyLookups: whatever a box query hands room code is checked to be of the room.
    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;"
            + "Ljava/util/function/Predicate;)Ljava/util/List;", at = @At("RETURN"))
    private void kingCrimson$roomVerifyEntities(@Nullable Entity entity, AABB boundingBox, Predicate<? super Entity> predicate,
            CallbackInfoReturnable<List<Entity>> cir) {
        if (EpitaphSandbox.depth == 0) {
            return;
        }
        if (EpitaphSandbox.sealed() && KingCrimsonConfig.epitaphVerifyLookups()) {
            SandboxRoom room = EpitaphSandbox.room();
            cir.getReturnValue().removeIf(found -> {
                if (room.owns(found)) {
                    return false;
                }
                EpitaphSandbox.leak("a lookup handed room code an entity that is not of the room");
                return true;
            });
        }
    }
}
