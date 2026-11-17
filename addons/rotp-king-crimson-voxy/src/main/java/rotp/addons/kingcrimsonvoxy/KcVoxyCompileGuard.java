package rotp.addons.kingcrimsonvoxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.mojang.logging.LogUtils;

/**
 * A Voxy update can keep every anchor and still change what the patched text relies on. A program that then fails to compile
 * or link is built again from Voxy's own text and the effect turns off for the session, as with an anchor that no longer fits.
 * If Voxy's own text fails as well the failure is not ours, and Voxy gets it back unchanged. A shader pack's far look is joined
 * first (KcVoxyShaderPatch.join); if that does not compile, the patch runs without it.
 */
final class KcVoxyCompileGuard {
    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);
    /** Debug runs only (-Drotp.kcvoxy.dump=<folder>): every patched program's text as it goes to the driver. */
    private static final String DUMP = System.getProperty("rotp.kcvoxy.dump");
    private static int dumped;
    private static volatile Method compile;

    private KcVoxyCompileGuard() {}

    /** Null when the call should run as it is; otherwise the program it compiled. */
    static Object compile(Object builder, Map<Object, String> sources) {
        if (INSIDE.get() || !KcVoxyShaderPatch.patchedAny()) {
            return null;
        }
        Map<Object, String> restored = new HashMap<>();
        Map<Object, String> unjoined = new HashMap<>();
        Map<Object, String> joined = new HashMap<>();
        for (Map.Entry<Object, String> entry : sources.entrySet()) {
            String original = KcVoxyShaderPatch.unpatch(entry.getValue());
            if (original != null) {
                restored.put(entry.getKey(), original);
                unjoined.put(entry.getKey(), entry.getValue());
                String look = KcVoxyShaderPatch.join(entry.getValue());
                if (look != null) {
                    joined.put(entry.getKey(), look);
                }
            }
        }
        if (restored.isEmpty()) {
            return null;
        }
        Method method = compile;
        if (method == null) {
            try {
                method = builder.getClass().getMethod("compile");
            } catch (ReflectiveOperationException | LinkageError failure) {
                KcVoxyShaderPatch.reject("Voxy's shader builder cannot be guarded: " + failure);
                return null;
            }
            compile = method;
        }
        INSIDE.set(true);
        try {
            if (!joined.isEmpty()) {
                sources.putAll(joined);
                if (DUMP != null) {
                    dump(sources);
                }
                try {
                    Object compiled = invoke(method, builder);
                    LogUtils.getLogger().info("King Crimson Voxy far terrain: the shader pack's far look is joined");
                    return compiled;
                } catch (RuntimeException failure) {
                    sources.putAll(unjoined);
                    LogUtils.getLogger().warn("King Crimson Voxy far terrain: the shader pack's far look does not compile with the patch "
                            + "and stays off ({})", failure.getMessage());
                }
            }
            try {
                return invoke(method, builder);
            } catch (RuntimeException failure) {
                Map<Object, String> patched = new HashMap<>(unjoined);
                sources.putAll(restored);
                Object compiled;
                try {
                    compiled = invoke(method, builder);
                } catch (RuntimeException second) {
                    sources.putAll(patched);
                    throw failure;
                }
                KcVoxyShaderPatch.reject("a patched Voxy shader does not compile (" + failure.getMessage() + ")");
                return compiled;
            }
        } finally {
            INSIDE.set(false);
        }
    }

    private static void dump(Map<Object, String> sources) {
        try {
            java.nio.file.Path folder = java.nio.file.Path.of(DUMP);
            java.nio.file.Files.createDirectories(folder);
            int program = ++dumped;
            for (Map.Entry<Object, String> entry : sources.entrySet()) {
                java.nio.file.Files.writeString(folder.resolve(String.format("%03d-%s.glsl", program, entry.getKey())), entry.getValue());
            }
        } catch (java.io.IOException | RuntimeException failure) {
            LogUtils.getLogger().info("King Crimson Voxy far terrain: dump failed: {}", failure.toString());
        }
    }

    private static Object invoke(Method method, Object builder) {
        try {
            return method.invoke(builder);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            if (wrapped.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(wrapped.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
