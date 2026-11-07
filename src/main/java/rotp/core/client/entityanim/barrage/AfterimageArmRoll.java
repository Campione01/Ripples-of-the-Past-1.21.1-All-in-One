package rotp.core.client.entityanim.barrage;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * The roll an afterimage arm gets from its random spread offset. 1.16 applied it to Stand arms only
 * (StandTwoHandedBarrageAnimation.animateSwing); a player's afterimage arm went through
 * ArmsBarrageAnimation.animateSwing, which drops the offset.
 * No client classes here, so server gametests can run it.
 */
public final class AfterimageArmRoll {
	// the owner saw these two barrages on video and accepted them, roll and all; he asked for the Ultimate Lifeform's look on the rest
	private static final Set<ResourceLocation> KEEP_ROLL = Set.of(
			ResourceLocation.fromNamespaceAndPath("rotp_cream", "cream"),
			ResourceLocation.fromNamespaceAndPath("highwaystar", "highway_star"));

	private AfterimageArmRoll() {}

	/** Radians to add to the arm's zRot. */
	public static float of(boolean noRoll, float swingAmount, float rollOffset) {
		return noRoll ? 0.0F : swingAmount * rollOffset;
	}

	/** Whether this Stand's afterimage arms keep 1.16's roll. */
	public static boolean keptBy(@Nullable ResourceLocation standType) {
		return standType != null && KEEP_ROLL.contains(standType);
	}
}
