package rotp.core.customobjects.entity_projectile;

import rotp.core.init.ModEntityTypes;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.JojoModUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * A whole block thrown by a Stand (StandBlockTossAbility): it flies and hurts as a block shard does, and the server shows
 * its end to every client, whatever that client's own copy of the flight met.
 */
public class ThrownBlockEntity extends BlockShardEntity {
	private static final int SHATTER_PARTICLES = 32;

	public ThrownBlockEntity(ServerLevel level, LivingEntity thrower, ItemStack thrown) {
		super(ModEntityTypes.THROWN_BLOCK.get(), thrower, level, thrown.getItem() instanceof BlockItem block
				? block.getBlock().defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
	}

	public ThrownBlockEntity(EntityType<? extends ThrownBlockEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void breakProjectile(TargetType targetType, HitResult hitTarget) {
		if (level() instanceof ServerLevel level && !isRemoved()) {
			BlockState block = getBlock();
			SoundType sounds = block.getSoundType(level, blockPosition(), this);
			level.playSound(null, getX(), getY(), getZ(), sounds.getBreakSound(), SoundSource.BLOCKS,
					(sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), getX(), getY() + getBbHeight() / 2.0F, getZ(),
					SHATTER_PARTICLES, 0.25D, 0.25D, 0.25D, 0.15D);
			discard();
		}
	}

	// the base asks the griefing rule only of an owner, and a Stand is not saved: a block reloaded in flight has none
	@Override
	protected boolean canBreakBlock(BlockPos blockPos, BlockState blockState) {
		return JojoModUtil.breakingBlocksEnabled(level()) && super.canBreakBlock(blockPos, blockState);
	}
}
