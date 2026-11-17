package rotp.addons.kingcrimson;

public final class KingCrimsonPhase3Spec {
    // 1.16 InitStands staminaCost(175). 1.16 never charged it (its hold-only Epitaph never ran StandAction.onPerform);
    // the owner's Epitaph of R772.1 does, at the press (EPITAPH-ARCH 5.1). Also the skill's unlock exp, which the other
    // King Crimson skills take from their stamina cost too.
    public static final float EPITAPH_ACTIVATION_STAMINA = 175.0F;
    public static final int EPITAPH_COOLDOWN_TICKS = 35;

    public static final String EPITAPH_POST_CHAIN_PATH =
            "shaders/post/epitaph.json";

    private KingCrimsonPhase3Spec() {}
}
