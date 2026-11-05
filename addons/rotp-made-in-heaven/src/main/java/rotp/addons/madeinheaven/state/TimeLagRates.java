package rotp.addons.madeinheaven.state;

// Plain arithmetic of the acceleration, free of game classes so the
// contract can run it.
public final class TimeLagRates {
    public static final int LAST_PHASE = 30;
    public static final int LAST_LEGACY_PHASE = 25;
    public static final int MAX_MULTIPLIER = 20;
    // Phases 26-30: the other living beings come close to standing still
    // before the Universe Reset; phase 31 keeps the top value.
    private static final int[] FINAL_PHASE_MULTIPLIERS = {8, 10, 12, 16, 20};

    // Vanilla LivingEntity.travel in the air: the entity moves by its
    // velocity, then the velocity becomes (v - gravity) * 0.98.
    static final double JUMP_POWER = 0.42F;
    static final double GRAVITY = 0.08D;
    static final double AIR_DRAG = 0.98F;

    private static final double[] JUMP_FACTORS =
            new double[MAX_MULTIPLIER + 1];

    private TimeLagRates() {}

    /** How many times slower every other living being runs at this phase. */
    public static int lagMultiplier(int phase) {
        if (phase < 0) {
            return 1;
        }
        if (phase <= LAST_LEGACY_PHASE) {
            return legacyMultiplier(phase);
        }
        int index = phase - LAST_LEGACY_PHASE - 1;
        return index < FINAL_PHASE_MULTIPLIERS.length
                ? FINAL_PHASE_MULTIPLIERS[index]
                : MAX_MULTIPLIER;
    }

    // 1.16 TimeUtil.getCalculatedPhase: 0-5 -> 2, 6-10 -> 3, 11-15 -> 4,
    // 16-20 -> 5, 21-25 -> 6, 26-30 -> 7, any other phase -> 1.
    public static int legacyMultiplier(int phase) {
        if (phase < 0 || phase > LAST_PHASE) {
            return 1;
        }
        return phase <= 5 ? 2 : (phase - 1) / 5 + 2;
    }

    // Mode B, 1.16 boostOnAcceleration: the owner's sprint and swim speed
    // is multiplied by 1 + 0.4 * phase (13x at phase 30), uncapped by the
    // owner's decision of 2026-11-04.
    public static double ownerSprintFactor(int phase) {
        return 1.0D + 0.4D * Math.max(0, phase);
    }

    // 1.16 ChangeMaxUpStepPacket: 1 + getCalculatedPhase / 10 while the
    // boost runs.
    public static double ownerStepHeight(int phase) {
        return 1.0D + legacyMultiplier(phase) / 10.0D;
    }

    public static double rateFactor(int multiplier) {
        return 1.0D / Math.max(1, multiplier);
    }

    public static double gravityFactor(int multiplier) {
        double rate = rateFactor(multiplier);
        return rate * rate;
    }

    /**
     * The jump strength factor that keeps the normal jump height under
     * gravity / m^2. Plain 1 / m would only do so without air drag: the
     * per-tick drag works m times as often during the slow jump, which at
     * m = 3 already leaves the apex under one block.
     */
    public static synchronized double jumpStrengthFactor(int multiplier) {
        int m = Math.min(Math.max(1, multiplier), MAX_MULTIPLIER);
        if (m == 1) {
            return 1.0D;
        }
        if (JUMP_FACTORS[m] == 0.0D) {
            double gravity = GRAVITY * gravityFactor(m);
            double target = apex(JUMP_POWER, GRAVITY);
            double low = 0.0D;
            double high = JUMP_POWER;
            for (int step = 0; step < 60; step++) {
                double middle = (low + high) / 2.0D;
                if (apex(middle, gravity) < target) {
                    low = middle;
                }
                else {
                    high = middle;
                }
            }
            JUMP_FACTORS[m] = high / JUMP_POWER;
        }
        return JUMP_FACTORS[m];
    }

    static double apex(double jumpVelocity, double gravity) {
        double height = 0.0D;
        double velocity = jumpVelocity;
        for (int tick = 0; tick < 100_000 && velocity > 0.0D; tick++) {
            height += velocity;
            velocity = (velocity - gravity) * AIR_DRAG;
        }
        return height;
    }

    /** Ticks from the jump until the jumper is back at its start height. */
    static int airTicks(double jumpVelocity, double gravity) {
        double height = 0.0D;
        double velocity = jumpVelocity;
        int tick = 0;
        do {
            height += velocity;
            velocity = (velocity - gravity) * AIR_DRAG;
            tick++;
        }
        while (height > 0.0D && tick < 100_000);
        return tick;
    }

    public static double jumpApex(int multiplier) {
        return apex(
                JUMP_POWER * jumpStrengthFactor(multiplier),
                GRAVITY * gravityFactor(multiplier));
    }

    public static int jumpAirTicks(int multiplier) {
        return airTicks(
                JUMP_POWER * jumpStrengthFactor(multiplier),
                GRAVITY * gravityFactor(multiplier));
    }
}
