package rotp.addons.echoes.state;

import javax.annotation.Nullable;

/**
 * R772 F11: ACT2's onomatopoeia words (the master's onomatopoeia/Onomatopoeia and OnomatopoeiaType): what each does
 * is in EchoesOnomatopoeiaService; the colour tints the word ACT2's tail leaves on its target.
 */
public enum EchoesOnomatopoeia {
    ZIZI("zizi", Type.ILLUSION, 0xFFAA00),
    PIKA("pika", Type.ILLUSION, 0xFFEE00),
    RERE("rere", Type.ILLUSION, 0xFF4400),
    BING("bing", Type.ILLUSION, 0x88DDFF),
    HONG("hong", Type.ILLUSION, 0xFF6600),
    KACHA("kacha", Type.ILLUSION, 0xAAAAAA),
    JIAN("jian", Type.ILLUSION, 0xCC4488),
    WENG("weng", Type.ILLUSION, 0x99AAFF),

    YUNHUHU("yunhuhu", Type.MENTAL, 0xAA66FF),
    PAPA("papa", Type.MENTAL, 0x8844AA),
    HUANGHUANG("huanghuang", Type.MENTAL, 0x9955CC),
    MENGMENG("mengmeng", Type.MENTAL, 0xCC88DD),
    YANGYANG("yangyang", Type.MENTAL, 0xDDAACC),
    GOGOGO("gogogo", Type.MENTAL, 0x9933CC),

    DON("don", Type.KNOCKBACK, 0xFF3300),
    TANTAN("tantan", Type.KNOCKBACK, 0xFF6600),
    FUWA("fuwa", Type.KNOCKBACK, 0x99DDFF),

    NIANHUHU("nianhuhu", Type.CONTROL, 0x66AA66),
    HUALIU("hualiu", Type.CONTROL, 0x66DDFF),
    SHILU("shilu", Type.CONTROL, 0x4488CC),
    YINGBANGBANG("yingbangbang", Type.CONTROL, 0xAAAAAA),
    RUANMIANMIAN("ruanmianmian", Type.CONTROL, 0xFFDDFF),
    CHENDIANDIAN("chendiandian", Type.CONTROL, 0x554433),
    LENGBINGBING("lengbingbing", Type.CONTROL, 0x88CCFF),
    NUANNUAN("nuannuan", Type.CONTROL, 0xFF8844),
    SHANSHAN("shanshan", Type.CONTROL, 0xFFFF88),
    YOUHUO("youhuo", Type.CONTROL, 0xFF66CC);

    public enum Type {
        ILLUSION,
        MENTAL,
        KNOCKBACK,
        CONTROL
    }

    public static final int VARIANTS = 4;
    private static final EchoesOnomatopoeia[] VALUES = values();

    private final String id;
    private final Type type;
    private final int color;

    EchoesOnomatopoeia(String id, Type type, int color) {
        this.id = id;
        this.type = type;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public Type type() {
        return type;
    }

    public int color() {
        return color;
    }

    public String translationKey() {
        return "rotp_echoes.onomatopoeia." + id;
    }

    // master OnomatopoeiaAttachHandler.isOneTimeWord: these act once when the word lands, the rest every half second
    public boolean actsOnce() {
        return this == HONG || this == DON || this == FUWA || this == WENG || this == PIKA;
    }

    @Nullable
    public static EchoesOnomatopoeia byId(@Nullable String id) {
        for (EchoesOnomatopoeia word : VALUES) {
            if (word.id.equals(id)) {
                return word;
            }
        }
        return null;
    }
}
