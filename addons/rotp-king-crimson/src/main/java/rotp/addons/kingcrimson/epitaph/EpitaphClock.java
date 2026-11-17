package rotp.addons.kingcrimson.epitaph;

import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;

/**
 * The clocks of one prediction, in ticks of the foreseen future: how far the user is being shown, and where the
 * bodies are. Both start at the press and never pass the foresight; the room is kept ahead of both. During the phantom
 * phase the shown tick runs through the whole foresight; the bodies live a quarter of a tick per server tick when the
 * user is alone on the server, and a whole one when he is not.
 */
public final class EpitaphClock {
    private final EpitaphMode mode;
    private final int foresight;
    private final int phase;
    private int elapsed;
    private double shown;
    private double body;
    /** In a window the bodies are free and wait at the press: their clock runs from the pull-back. */
    private boolean bodiesWait;

    public EpitaphClock(EpitaphMode mode, int foresightTicks, int phaseTicks) {
        this.mode = mode;
        this.foresight = foresightTicks;
        this.phase = Math.max(1, phaseTicks);
    }

    /** One server tick of the prediction. */
    public void advance() {
        boolean inPhase = elapsed < phase;
        elapsed++;
        shown = Math.min(foresight, shown + (double) foresight / phase);
        if (!bodiesWait) {
            body = Math.min(foresight, body + (mode == EpitaphMode.SOLO_SLOW && inPhase
                    ? KingCrimsonEpitaphForesightSpec.SOLO_BODY_RATE : 1.0D));
        }
    }

    /** A window opens: the bodies stay at the press until they are pulled back to it. */
    public void holdBodies() {
        bodiesWait = true;
    }

    public void releaseBodies() {
        bodiesWait = false;
    }

    /** The phantoms are never shown further than the room has lived. */
    public void holdShownTo(int simulated) {
        shown = Math.min(shown, simulated);
    }

    public int foresight() {
        return foresight;
    }

    public int phase() {
        return phase;
    }

    /** Server ticks since the press. */
    public int elapsed() {
        return elapsed;
    }

    public boolean inPhase() {
        return elapsed < phase;
    }

    /** The tick of the future the user is being shown. */
    public double shown() {
        return shown;
    }

    /** The tick of the future the bodies are at. */
    public double body() {
        return body;
    }

    /** How fast the bodies live now, in ticks of the future per server tick. */
    public double bodyRate() {
        return bodiesWait ? 0.0D : mode == EpitaphMode.SOLO_SLOW && inPhase() ? KingCrimsonEpitaphForesightSpec.SOLO_BODY_RATE : 1.0D;
    }
}
