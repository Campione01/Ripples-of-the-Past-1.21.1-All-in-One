package rotp.addons.kingcrimsonvoxy;

import java.util.ArrayList;
import java.util.List;

/** Whether the far effect may run: all-or-nothing, and once something does not fit it stays off for the whole session. */
public final class KcVoxyStatus {
    private static boolean applicable;
    private static final List<String> FAILURES = new ArrayList<>();

    private KcVoxyStatus() {}

    /** Voxy, Sodium, Iris and King Crimson are all installed. Without them the add-on says nothing and does nothing. */
    static synchronized void setApplicable() {
        applicable = true;
    }

    public static synchronized boolean applicable() {
        return applicable;
    }

    public static synchronized void fail(String what) {
        if (!FAILURES.contains(what)) {
            FAILURES.add(what);
            org.slf4j.LoggerFactory.getLogger("rotp_kingcrimson_voxy")
                    .error("King Crimson Voxy far terrain stays off: {}", what);
        }
    }

    public static synchronized boolean failed() {
        return !FAILURES.isEmpty();
    }

    public static synchronized String reason() {
        return String.join("; ", FAILURES);
    }

    /** One short line for the chat; the log has them all. */
    public static synchronized String firstReason() {
        return FAILURES.isEmpty() ? "" : FAILURES.get(0);
    }

    public static synchronized boolean usable() {
        return applicable && FAILURES.isEmpty();
    }
}
