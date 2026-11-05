package rotp.addons.killerqueen.btd;

/** 1.6.10 (2026-10-20) com.zeml.rotp_zkq.btd.BtdClientState: the local player's Bite the Dust as the server sent it. */
public final class BtdClientState {
    private static volatile BtdState state = BtdState.INACTIVE;
    private static volatile int ticks;
    private static volatile boolean owner;

    private BtdClientState() {}

    public static void set(BtdState newState, int newTicks, boolean isOwner) {
        state = newState;
        ticks = newTicks;
        owner = isOwner;
    }

    public static BtdState getState() {
        return state;
    }

    public static int getTicks() {
        return ticks;
    }

    public static boolean isOwner() {
        return owner;
    }

    public static boolean isOwnerStandLocked() {
        return owner && state.locksOwnerStand();
    }
}
