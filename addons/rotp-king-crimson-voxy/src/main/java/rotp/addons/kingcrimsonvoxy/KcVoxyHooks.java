package rotp.addons.kingcrimsonvoxy;

/** What the mixins call. The classes behind it are package-private; mixin code runs from the target's own class. */
public final class KcVoxyHooks {
    private KcVoxyHooks() {}

    public static boolean live() {
        return KcVoxyFar.live();
    }

    public static void bindBuffers() {
        KcVoxyFar.bindBuffers();
    }

    public static int shellEntityId(int id) {
        return KcVoxyFar.shellEntityId(id);
    }

    public static Object compileGuarded(Object builder, java.util.Map<Object, String> sources) {
        return KcVoxyCompileGuard.compile(builder, sources);
    }

    public static void onRenderOpaque() {
        KcVoxyProbe.onRenderOpaque();
        KcVoxyTrace.voxyHead();
    }

    public static void onRenderOpaqueEnd() {
        KcVoxyTrace.voxyReturn();
    }
}
