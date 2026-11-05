package rotp.addons.killerqueen.btd;

/** 1.6.10 (2026-10-20) com.zeml.rotp_zkq.btd.BtdState: the life of one Bite the Dust placed in a carrier. */
public enum BtdState {
    INACTIVE,
    ACTIVE,
    TIME_LOOP,
    COUNTDOWN,
    SAVED,
    EXPIRED,
    CANCELLED;

    public boolean locksOwnerStand() {
        return this == ACTIVE || this == TIME_LOOP || this == COUNTDOWN;
    }

    public static BtdState byOrdinal(int ordinal) {
        BtdState[] values = values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : INACTIVE;
    }
}
