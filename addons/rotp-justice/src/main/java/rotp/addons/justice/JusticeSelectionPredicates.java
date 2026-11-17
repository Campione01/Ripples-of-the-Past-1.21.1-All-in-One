package rotp.addons.justice;

import rotp.addons.justice.JusticeAddon.Move;

/** The donor's greenSelection of its four moves, as a function of the caster's synced Fog and guise. */
public final class JusticeSelectionPredicates {
    private JusticeSelectionPredicates() {}

    public static boolean donorGreenSelection(String move, boolean fog, JusticeDisguise.Mode mode) {
        if (!fog) {
            return false;
        }
        if (Move.DISGUISE.id.equals(move)) {
            return mode != JusticeDisguise.Mode.NONE;
        }
        return Move.FOG.id.equals(move) || Move.PUPPET.id.equals(move) || Move.ILLUSION.id.equals(move);
    }
}
