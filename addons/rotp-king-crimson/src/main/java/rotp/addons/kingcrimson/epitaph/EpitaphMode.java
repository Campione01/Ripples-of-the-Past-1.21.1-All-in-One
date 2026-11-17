package rotp.addons.kingcrimson.epitaph;

/** How a prediction goes through time, fixed at the press. */
public enum EpitaphMode {
    /** One player on the server: the bodies live slowly during the phantom phase while the phantoms run ahead. */
    SOLO_SLOW,
    /** Several players, no player under the crosshair: nobody slows; the bodies follow from the press. */
    MULTI_BOUND,
    /** Several players, a player under the crosshair: real play for the phase, then the pull-back, then the walk. */
    MULTI_WINDOW
}
