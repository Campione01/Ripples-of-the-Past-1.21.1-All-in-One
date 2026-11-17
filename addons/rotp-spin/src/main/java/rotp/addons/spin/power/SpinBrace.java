package rotp.addons.spin.power;

public final class SpinBrace {
    public static final float MAX_REDUCTION = 0.95F;

    private SpinBrace() {
    }

    public static boolean isBraced(int heldTicks, int windupTicks) {
        return heldTicks >= windupTicks;
    }

    public static boolean isBraceable(boolean projectile, boolean explosion, boolean melee, boolean bypassArmor, boolean fire, boolean magic) {
        return !bypassArmor && !fire && !magic ? projectile || explosion || melee : false;
    }

    public static float absorbed(float amount, float reduction, float energy, float energyPerDamage) {
        if (!(amount <= 0.0F) && !(reduction <= 0.0F)) {
            float wanted = amount * Math.min(reduction, 0.95F);
            return energyPerDamage <= 0.0F ? wanted : Math.max(0.0F, Math.min(wanted, energy / energyPerDamage));
        } else {
            return 0.0F;
        }
    }

    public static float energyCost(float absorbed, float energyPerDamage, float energy) {
        return !(absorbed <= 0.0F) && !(energyPerDamage <= 0.0F) ? Math.max(0.0F, Math.min(absorbed * energyPerDamage, energy)) : 0.0F;
    }
}
