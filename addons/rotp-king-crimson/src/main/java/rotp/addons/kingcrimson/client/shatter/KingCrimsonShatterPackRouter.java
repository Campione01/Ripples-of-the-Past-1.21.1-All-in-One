package rotp.addons.kingcrimson.client.shatter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPackRoute;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlateLight;
import rotp.core.api.client.render.ClientRenderCompatibility;

/**
 * Which route the running shader pack gets, from what the pack declares in its id map and never from its name. A reload makes a new
 * pack object, so the answer is read again whenever that object changes.
 */
@OnlyIn(Dist.CLIENT)
public final class KingCrimsonShatterPackRouter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Object readPack;
    private static KingCrimsonShatterPackRoute readRoute = KingCrimsonShatterPackRoute.UNDECLARED;
    private static boolean readWide;

    private KingCrimsonShatterPackRouter() {}

    /** Only asked during an erase and the one warm-up of a King Crimson user's session. */
    public static KingCrimsonShatterPackRoute current() {
        if (!ClientRenderCompatibility.snapshot().irisShaderPackInUse()) {
            readPack = null;
            return KingCrimsonShatterPackRoute.NO_SHADERS;
        }
        Declarations access = Declarations.ACCESS;
        if (access == null) return KingCrimsonShatterPackRoute.UNDECLARED;
        try {
            Object pack = access.currentPack();
            if (pack != readPack) {
                readPack = pack;
                int declared = pack == null ? -1 : access.markerId(pack, access.marker);
                readRoute = KingCrimsonShatterPackRoute.choose(true, declared);
                readWide = readRoute == KingCrimsonShatterPackRoute.DECLARED
                        && access.markerId(pack, access.wideMarker) == KingCrimsonShatterPlateLight.WIDE_MARKER_ID;
                LOGGER.info("King Crimson erased space: the shader pack declares {}, route {}, plate levels above {}: {}",
                        declared, readRoute, KingCrimsonShatterPlateLight.LAST_LEVEL, readWide);
            }
            return readRoute;
        } catch (Throwable failure) {
            if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
            LOGGER.warn("King Crimson could not read the shader pack's id map; drawing the universe itself.", failure);
            readPack = null;
            readWide = false;
            return KingCrimsonShatterPackRoute.UNDECLARED;
        }
    }

    /** Whether the running pack reads plate levels above {@link KingCrimsonShatterPlateLight#LAST_LEVEL}: it says so in its id map. */
    public static boolean widePlateLight() {
        return current() == KingCrimsonShatterPackRoute.DECLARED && readWide;
    }

    private record Declarations(MethodHandle getPack, MethodHandle getIdMap, MethodHandle getEntities, Object marker, Object wideMarker) {
        private static final Declarations ACCESS = resolve();

        private static Declarations resolve() {
            try {
                ClassLoader loader = KingCrimsonShatterPackRouter.class.getClassLoader();
                Class<?> iris = loader.loadClass("net.irisshaders.iris.Iris");
                Class<?> pack = loader.loadClass("net.irisshaders.iris.shaderpack.ShaderPack");
                Class<?> idMap = loader.loadClass("net.irisshaders.iris.shaderpack.IdMap");
                Class<?> name = loader.loadClass("net.irisshaders.iris.shaderpack.materialmap.NamespacedId");
                var lookup = MethodHandles.publicLookup();
                MethodHandle named = lookup.findConstructor(name, MethodType.methodType(void.class, String.class, String.class));
                return new Declarations(
                        lookup.findStatic(iris, "getCurrentPack", MethodType.methodType(Optional.class)),
                        lookup.findVirtual(pack, "getIdMap", MethodType.methodType(idMap)).asType(MethodType.methodType(Object.class, Object.class)),
                        lookup.findVirtual(idMap, "getEntityIdMap", MethodType.methodType(Object2IntFunction.class))
                                .asType(MethodType.methodType(Object2IntFunction.class, Object.class)),
                        named.invoke(KingCrimsonShatterPackRoute.MARKER_NAMESPACE, KingCrimsonShatterPackRoute.MARKER_PATH),
                        named.invoke(KingCrimsonShatterPackRoute.MARKER_NAMESPACE, KingCrimsonShatterPlateLight.WIDE_MARKER_PATH));
            } catch (Throwable failure) {
                if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
                LOGGER.warn("King Crimson cannot read shader pack id maps; every pack takes the general route.", failure);
                return null;
            }
        }

        private Object currentPack() throws Throwable {
            return ((Optional<?>) getPack.invokeExact()).orElse(null);
        }

        @SuppressWarnings("unchecked")
        private int markerId(Object pack, Object name) throws Throwable {
            Object map = (Object) getIdMap.invokeExact(pack);
            Object2IntFunction<Object> entities = (Object2IntFunction<Object>) getEntities.invokeExact(map);
            return entities.getInt(name);
        }
    }
}
