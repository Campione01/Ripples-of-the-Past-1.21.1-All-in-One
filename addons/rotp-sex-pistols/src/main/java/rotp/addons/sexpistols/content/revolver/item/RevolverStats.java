package rotp.addons.sexpistols.content.revolver.item;

import java.util.Locale;

import net.minecraft.world.item.ItemStack;

// Next Album 0.1.13 RevolverStats, as registered in its InitItems.
public record RevolverStats(
        String id,
        float closeDamage,
        float stableDamage,
        float piercingDamage,
        float minDistanceDamage,
        double closeRange,
        double stableRange,
        double farDropRate,
        float bulletSpeed,
        float hipInaccuracy,
        float adsInaccuracy,
        float recoilPitch,
        float recoilYaw,
        float adsRecoilPitchMul,
        float adsRecoilYawMul,
        int shotCooldownTicks,
        int reloadCooldownTicks,
        float meleeDamage,
        float adsZoom,
        float criticalBonus,
        boolean heavyFireSound,
        float swaySensitivity,
        float visualRecoilKick,
        float visualRecoilLift) {
    public static final double DEFAULT_CLOSE_RANGE = 5.0D;
    public static final double DEFAULT_STABLE_RANGE = 50.0D;
    public static final float CRITICAL_DAMAGE_MULTIPLIER = 1.5F;

    // 0.1.13 "mista_revolver": the port's rotp_sex_pistols:revolver.
    public static final RevolverStats MISTA = new RevolverStats(
            "mista",
            10.0F, 5.0F, 8.0F, 1.0F,
            5.0D, 50.0D, 0.28D,
            6.1F,
            0.78F, 0.24F,
            4.85F, 1.45F,
            0.68F, 0.48F,
            5, 8,
            1.15F,
            1.92F,
            0.2F,
            false,
            0.88F,
            0.072F, 5.6F);

    // 0.1.13 "revolver": the port's rotp_sex_pistols:heavy_revolver.
    public static final RevolverStats HEAVY = new RevolverStats(
            "heavy",
            12.0F, 6.5F, 9.0F, 2.0F,
            8.0D, 72.0D, 0.16D,
            6.8F,
            1.15F, 0.38F,
            11.8F, 4.0F,
            0.9F, 0.8F,
            14, 18,
            1.25F,
            1.7F,
            0.0F,
            true,
            1.18F,
            0.18F, 14.0F);

    public static RevolverStats of(ItemStack stack) {
        return stack != null
                        && stack.getItem() instanceof RevolverItem revolver
                ? revolver.stats()
                : MISTA;
    }

    public static RevolverStats byId(String id) {
        return HEAVY.id.equals(
                        id == null ? "" : id.toLowerCase(Locale.ROOT))
                ? HEAVY
                : MISTA;
    }

    public float pitchRecoil(boolean aiming, double multiplier) {
        return (float) (recoilPitch
                * (aiming ? adsRecoilPitchMul : 1.0F)
                * multiplier);
    }

    public float yawRecoil(
            boolean aiming, float randomValue, double multiplier) {
        return (float) ((randomValue - 0.5F)
                * recoilYaw
                * (aiming ? adsRecoilYawMul : 1.0F)
                * multiplier);
    }
}
