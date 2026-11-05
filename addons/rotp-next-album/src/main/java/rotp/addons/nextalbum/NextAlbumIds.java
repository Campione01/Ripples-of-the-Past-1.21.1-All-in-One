package rotp.addons.nextalbum;

import net.minecraft.resources.ResourceLocation;

public final class NextAlbumIds {
    public static final String MOD_ID = "rotp_nextalbum";

    public static final String COLLAPSE_PATH = "collapse";
    public static final String LUCK_PLUCK_PATH = "luck_pluck";
    public static final String MISTA_SUIT_CLOTH_PATH =
            "mista_suit_cloth";
    public static final String MISTA_SUIT_PANTS_PATH =
            "mista_suit_pants";
    public static final String MISTA_SUIT_BOOTS_PATH =
            "mista_suit_boots";
    public static final String AJA_STONE_NECKLACE_PATH =
            "aja_stone_necklace";
    public static final String PLANT_AGING_DUST_PATH = "plant_aging_dust";
    public static final String SPIRIT_CIGARETTE_PATH = "spirit_cigarette";
    public static final String FOCUS_PATH = "focus";
    public static final String LAX_PATH = "lax";
    public static final String STAND_RESOLVE_AURA_PATH = "stand_resolve_aura";
    public static final String SOUND_GO_ORANGE_PATH = "sound_go_orange";
    public static final String SOUND_DO_ORANGE_PATH = "sound_do_orange";
    public static final String SOUND_BA_ORANGE_PATH = "sound_ba_orange";
    public static final String PESCI_SUIT_CLOTH_PATH = "pesci_suit_cloth";
    public static final String PESCI_SUIT_PANTS_PATH = "pesci_suit_pants";
    public static final String PESCI_SUIT_BOOTS_PATH = "pesci_suit_boots";
    public static final String PROSCIUTTO_SUIT_CLOTH_PATH =
            "prosciutto_suit_cloth";
    public static final String PROSCIUTTO_SUIT_PANTS_PATH =
            "prosciutto_suit_pants";
    public static final String PROSCIUTTO_SUIT_BOOTS_PATH =
            "prosciutto_suit_boots";
    public static final String PROSCIUTTO_DEAD_PATH = "prosciutto_dead";

    private NextAlbumIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
