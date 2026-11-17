package rotp.addons.yellowtemperance.disguise;

public enum DisguiseMode {
    NONE(0),
    ENTITY(1),
    PLAYER(2),
    /** The 1.16 Stand disguise: a fake Stand beside the user, who keeps his own look. */
    STAND(3),
    /** The user himself takes the Stand's look: the port's, kept as a choice in the Stand disguise's screen. */
    STAND_SELF(4);

    private final byte id;

    DisguiseMode(int id) {
        this.id = (byte) id;
    }

    public byte id() {
        return id;
    }

    public static DisguiseMode byId(int id) {
        for (DisguiseMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return NONE;
    }
}
