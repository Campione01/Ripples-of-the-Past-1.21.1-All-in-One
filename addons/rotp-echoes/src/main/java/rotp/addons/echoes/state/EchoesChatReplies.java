package rotp.addons.echoes.state;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntUnaryOperator;

// 092 EchoAIHandler reply tables and pick order (no game classes); the lines live in the lang files under KEY_PREFIX
public final class EchoesChatReplies {
    public static final int COOLDOWN_TICKS = 5;
    public static final int MAX_NICKNAME_LENGTH = 20;
    public static final int RANDOM_LINES = 29;
    public static final String KEY_PREFIX = "rotp_echoes.chat.";
    // both frames take (line, name)
    public static final String FRAME_NICKNAME = KEY_PREFIX + "frame.nickname";
    public static final String FRAME_NAMED = KEY_PREFIX + "frame.named";

    private static final String[] SHIT_SUFFIXES = {" S.H.I.T", " S.H.I.T!", " S.H.I.T...", " S.H.I.T!?", " S.H.I.T!!", " S.H.I.T!!!", " S.H.I.T?!", " S.H.I.T....", " S.H.I.T??", " S.H.I.T!?!"};
    private static final String[] MILD_CURSES = {" Oh hell no.", " What the hell.", " Oh bloody hell.", " For crying out loud.", " Oh for heaven's sake.", " Good grief.", " Oh my god.", " What on earth.", " Oh darn.", " Bloody hell.", " Oh crap.", " What the heck.", " Oh jeez.", " Oh man.", " For Pete's sake.", " Oh blast.", " What in the world.", " Oh bother.", " Oh dear.", " What the devil.", " Oh for the love of god.", " Holy crap.", " What in tarnation.", " Oh fiddlesticks.", " For goodness sake."};
    private static final String[] ROASTS = {"You serious?", "That's it?", "You can do better than that.", "Git gud.", "My grandma plays better than you.", "Are you even trying?", "I can't even...", "Bro, you okay?", "You sure about that?", "I can't with you.", "Are you for real?", "You're trolling, right?", "This is painful to watch.", "I've seen better gameplay from a potato.", "You're something else.", "Stop feeding, please.", "Are you on my team or theirs?", "My blood pressure is rising.", "That's one way to do it... the wrong way.", "Is this your first time?", "Hands are optional, I guess.", "Brain lag?", "I just... never mind.", "This game is not for everyone.", "I'm embarrassed for you.", "Just afk, it's safer.", "Are you playing with your feet?", "I have no words.", "Maybe try turning it off and on again?", "Pro gamer move... not.", "What was that?", "You can't be serious.", "I need a break from this.", "Are you practicing to be this bad?", "Unbelievable.", "I'm done."};
    private static final String[] CURSE_WORDS = {"fuck", "shit", "damn", "hell", "crap", "darn", "bloody", "goddamn", "wtf", "ass", "\u64cd", "\u9760", "\u8349"};
    private static final String[] ROAST_WORDS = {"\u83dc", "\u5f31", "\u5783\u573e", "\u5e9f\u7269", "\u83dc\u9e1f", "\u5f69\u7b14", "\u840c\u65b0", "\u65b0\u624b", "\u4e0d\u4f1a\u73a9", "\u83dc\u9e21", "noob", "weak", "trash", "garbage", "useless", "\u5e9f", "\u6e23", "\u7b28", "\u8822"};
    private static final String[] GREETING_WORDS = {"\u4f60\u597d", "\u55e8", "hello", "hi", "\u563f", "\u5582", "\u5728\u5417", "\u5728\u4e0d\u5728", "hey", "yo", "\u3053\u3093\u306b\u3061\u306f", "\uc548\ub155", "\u043f\u0440\u0438\u0432\u0435\u0442", "\u6c5d\u5b89", "\u8db3\u4e0b", "\u541b\u5b89", "\u5b89\u597d"};
    // HashMap like 092, so overlapping keywords resolve the same way
    private static final Map<String, String> KEYWORD_MAP;

    private EchoesChatReplies() {}

    /**
     * The answer: the lang key of its line (the client shows it in its own language), or for 092's English-only curse
     * and roast tables the line itself; the frame (FRAME_NICKNAME, FRAME_NAMED or null) puts it together with the
     * name; nickname is non-null when the message set one.
     */
    public record Reply(String key, String literal, String frame, String name, String nickname) {
        static Reply line(String id) {
            return new Reply(KEY_PREFIX + id, null, null, null, null);
        }
    }

    // 092 handleChat order: nickname, keyword, greeting, curse, roast, random
    public static Reply reply(
            String message,
            String playerName,
            IntUnaryOperator random) {
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("叫我") || lower.startsWith("call me")) {
            String nickname = nickname(
                    trimmed, lower.startsWith("叫我") ? 2 : 8);
            if (nickname != null) {
                return new Reply(KEY_PREFIX + "okay", null, FRAME_NICKNAME, nickname, nickname);
            }
        }
        if (lower.startsWith("我叫")
                || lower.startsWith("i am")
                || lower.startsWith("im ")) {
            String nickname = nickname(
                    trimmed,
                    lower.startsWith("我叫")
                            ? 2
                            : lower.startsWith("i am") ? 5 : 3);
            if (nickname != null) {
                return new Reply(KEY_PREFIX + "got_it", null, FRAME_NICKNAME, nickname, nickname);
            }
        }
        for (Map.Entry<String, String> entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                // 092's ruins search never matched; it always said ruins_none
                return Reply.line(entry.getValue());
            }
        }
        if (containsAny(lower, GREETING_WORDS)) {
            return Reply.line("greeting");
        }
        if (containsAny(lower, CURSE_WORDS)) {
            return new Reply(null, pick(MILD_CURSES, random), null, null, null);
        }
        if (containsAny(lower, ROAST_WORDS)) {
            return new Reply(null, pick(ROASTS, random), null, null, null);
        }
        String key = KEY_PREFIX + "random" + (1 + random.applyAsInt(RANDOM_LINES));
        if (random.applyAsInt(10) < 3 && !playerName.isEmpty()) {
            return new Reply(key, null, FRAME_NAMED, playerName, null);
        }
        return new Reply(key, null, null, null, null);
    }

    // 092 sendReply: 50% S.H.I.T suffix
    public static String withSuffix(String line, IntUnaryOperator random) {
        if (random.applyAsInt(2) == 0) {
            return line + pick(SHIT_SUFFIXES, random);
        }
        return line;
    }

    public static List<String> mildCurses() {
        return List.of(MILD_CURSES);
    }

    public static List<String> roasts() {
        return List.of(ROASTS);
    }

    public static List<String> suffixes() {
        return List.of(SHIT_SUFFIXES);
    }

    private static String nickname(String trimmed, int offset) {
        // 092 threw on a bare "call me" / "i am"; treat it as no nickname
        if (trimmed.length() < offset) {
            return null;
        }
        String nickname = trimmed.substring(offset).trim();
        return nickname.isEmpty() || nickname.length() > MAX_NICKNAME_LENGTH
                ? null
                : nickname;
    }

    private static boolean containsAny(String lower, String[] words) {
        for (String word : words) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String pick(String[] choices, IntUnaryOperator random) {
        return choices[random.applyAsInt(choices.length)];
    }

    // 092 EchoAIHandler keyword table, verbatim
    static {
        KEYWORD_MAP = new HashMap<String, String>();
        KEYWORD_MAP.put("\u600e\u4e48\u73a9", "how_to_play");
        KEYWORD_MAP.put("\u600e\u4e48\u5f00\u59cb", "how_to_play");
        KEYWORD_MAP.put("\u65b0\u624b", "how_to_play");
        KEYWORD_MAP.put("\u653b\u7565", "how_to_play");
        KEYWORD_MAP.put("\u6ce2\u7eb9", "hamon");
        KEYWORD_MAP.put("\u6ce2\u7d0b", "hamon");
        KEYWORD_MAP.put("\u5438\u8840\u9b3c", "vampire");
        KEYWORD_MAP.put("\u66ff\u8eab", "stand");
        KEYWORD_MAP.put("\u56de\u97f3", "echoes");
        KEYWORD_MAP.put("\u5403\u98ef", "no_eat");
        KEYWORD_MAP.put("\u7761\u89c9", "no_sleep");
        KEYWORD_MAP.put("\u7d2f", "no_tired");
        KEYWORD_MAP.put("\u9057\u8ff9", "ruins_none");
        KEYWORD_MAP.put("how to play", "how_to_play");
        KEYWORD_MAP.put("hamon", "hamon");
        KEYWORD_MAP.put("vampire", "vampire");
        KEYWORD_MAP.put("stand", "stand");
        KEYWORD_MAP.put("echoes", "echoes");
        KEYWORD_MAP.put("eat", "no_eat");
        KEYWORD_MAP.put("sleep", "no_sleep");
        KEYWORD_MAP.put("tired", "no_tired");
        KEYWORD_MAP.put("ruin", "ruins_none");
        KEYWORD_MAP.put("\u904a\u3073\u65b9", "how_to_play");
        KEYWORD_MAP.put("\u6ce2\u7d0b", "hamon");
        KEYWORD_MAP.put("\u5438\u8840\u9b3c", "vampire");
        KEYWORD_MAP.put("\u30b9\u30bf\u30f3\u30c9", "stand");
        KEYWORD_MAP.put("\u30a8\u30b3\u30fc\u30ba", "echoes");
        KEYWORD_MAP.put("\u98df\u3079\u308b", "no_eat");
        KEYWORD_MAP.put("\u7720\u308b", "no_sleep");
        KEYWORD_MAP.put("\u75b2\u308c\u305f", "no_tired");
        KEYWORD_MAP.put("\u907a\u8de1", "ruins_none");
        KEYWORD_MAP.put("\uc5b4\ub5bb\uac8c \ud574", "how_to_play");
        KEYWORD_MAP.put("\ud30c\ub3d9", "hamon");
        KEYWORD_MAP.put("\ubc40\ud30c\uc774\uc5b4", "vampire");
        KEYWORD_MAP.put("\uc2a4\ud0e0\ub4dc", "stand");
        KEYWORD_MAP.put("\uc5d0\ucf54\uc988", "echoes");
        KEYWORD_MAP.put("\uba39", "no_eat");
        KEYWORD_MAP.put("\uc790", "no_sleep");
        KEYWORD_MAP.put("\ud53c\uace4", "no_tired");
        KEYWORD_MAP.put("\uc720\uc801", "ruins_none");
    }
}
