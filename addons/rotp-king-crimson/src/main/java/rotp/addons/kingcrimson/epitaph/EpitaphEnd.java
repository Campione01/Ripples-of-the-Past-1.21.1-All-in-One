package rotp.addons.kingcrimson.epitaph;

/** Why a prediction is over. Whatever the reason, the same end runs and leaves nothing held. */
public enum EpitaphEnd {
    /** Every track was walked to its end. */
    COMPLETE,
    USER_DIED,
    USER_LOGGED_OUT,
    USER_LEFT_LEVEL,
    /** The user holds King Crimson no more. A recall is no loss: the Stand put away changes nothing. */
    STAND_LOST,
    SERVER_STOPPING,
    /** The prediction itself failed; the bodies go on as they are. */
    FAULT,
    /** Called off from outside: a command, a test. */
    CANCELLED
}
