package rotp.core.mechanics;

import java.util.OptionalInt;

import javax.annotation.Nullable;

import rotp.core.network.s2c.ScreenSplashPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Who gets blood or sand in the face. A look only: it tells the client and changes nothing on the server. Blood goes
 * through {@link BleedingEffect#splashBlood}, which hits and Blood Cutter already use, sand through the block breaks of
 * Stands and of the core's other attacks, so add-on Stands need no change.
 */
public final class ScreenSplashes {
	public static final double MIN_BLOOD_REACH = 3.5;
	public static final double SAND_REACH = 4;
	public static final float SAND_SIZE = 0.7F;
	public static final float BLOOD_FULL_AT = 6;
	public static final float BLOOD_MIN_SIZE = 0.25F;
	public static final float MIN_STRENGTH = 0.08F;
	// looking within 30 degrees of it takes all of it, none past 70
	private static final double FULL_FACING_COS = 0.866;
	private static final double NO_FACING_COS = 0.342;

	private ScreenSplashes() {}

	public static void blood(ServerLevel level, Vec3 pos, double radius, float amount, @Nullable Entity source) {
		float size = Math.max(BLOOD_MIN_SIZE, Math.min(1, amount / BLOOD_FULL_AT));
		send(level, pos, Math.max(radius, MIN_BLOOD_REACH), size, source, false, 0xFFFFFF);
	}

	public static void brokenBlock(Level level, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			OptionalInt dust = sandTint(state, level, pos);
			if (dust.isPresent()) {
				send(serverLevel, Vec3.atCenterOf(pos), SAND_REACH, SAND_SIZE, null, true, dust.getAsInt());
			}
		}
	}

	/** Sand, gravel and concrete powder, in their falling dust's colour; an anvil or a dragon egg is no dust. */
	public static OptionalInt sandTint(BlockState state, BlockGetter level, BlockPos pos) {
		if (state.getBlock() instanceof FallingBlock falling && !(falling instanceof AnvilBlock || falling instanceof DragonEggBlock)) {
			return OptionalInt.of(falling.getDustColor(state, level, pos) & 0xFFFFFF);
		}
		return OptionalInt.empty();
	}

	private static void send(ServerLevel level, Vec3 pos, double reach, float size, @Nullable Entity source, boolean sand, int tint) {
		for (ServerPlayer player : level.players()) {
			if (player == source || player.isSpectator() || player.isUnderWater()) {
				continue;
			}
			Vec3 eye = player.getEyePosition();
			float strength = strength(eye, player.getLookAngle(), pos, reach, size);
			if (strength >= MIN_STRENGTH && level.clip(new ClipContext(pos, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
					.getType() == HitResult.Type.MISS) {
				PacketDistributor.sendToPlayer(player, new ScreenSplashPacket(sand, strength, tint));
			}
		}
	}

	/** How much of a splash of this size lands on a face at eye, looking along look: all of it near and head on. */
	public static float strength(Vec3 eye, Vec3 look, Vec3 splash, double reach, float size) {
		Vec3 toSplash = splash.subtract(eye);
		double distance = toSplash.length();
		if (distance >= reach) {
			return 0;
		}
		double facing = distance < 1.0E-3 ? 1 : (toSplash.scale(1 / distance).dot(look) - NO_FACING_COS) / (FULL_FACING_COS - NO_FACING_COS);
		double nearness = distance <= reach / 2 ? 1 : 1 - 0.75 * (distance - reach / 2) / (reach / 2);
		return (float) (Math.max(0, Math.min(1, facing)) * nearness * size);
	}
}
