package rotp.addons.whitesnake.buffdisc;

import java.util.Objects;

public final class InjectEffectClientBridge {
    private static Runnable openHandler = () -> {};

    private InjectEffectClientBridge() {}

    public static void install(Runnable handler) {
        openHandler = Objects.requireNonNull(handler);
    }

    public static void open() {
        openHandler.run();
    }
}
