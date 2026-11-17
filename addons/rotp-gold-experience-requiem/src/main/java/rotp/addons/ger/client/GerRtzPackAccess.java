package rotp.addons.ger.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.function.IntFunction;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import rotp.addons.ger.state.GerRtzPackRoute;
import rotp.core.api.client.render.ClientRenderCompatibility;

/**
 * The only file of this add-on that knows the names of Iris and Super Resolution, read at run time and all optional. The route comes from
 * what the pack declares in its id map, never from its name; a reload makes a new pack object, so it is read again when that changes.
 */
@OnlyIn(Dist.CLIENT)
final class GerRtzPackAccess {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_ENTITY_ID = 0;
    private static Object readPack;
    private static GerRtzPackRoute readRoute = GerRtzPackRoute.UNDECLARED;

    private GerRtzPackAccess() {}

    /** Only asked during Return to Zero. */
    static GerRtzPackRoute route() {
        if (!ClientRenderCompatibility.snapshot().irisShaderPackInUse()) {
            readPack = null;
            return GerRtzPackRoute.NO_SHADERS;
        }
        Declarations access = Declarations.ACCESS;
        if (access == null) return GerRtzPackRoute.UNDECLARED;
        try {
            Object pack = access.currentPack();
            if (pack != readPack) {
                readPack = pack;
                int declared = pack == null ? -1 : access.markerId(pack);
                readRoute = GerRtzPackRoute.choose(true, declared);
                LOGGER.info("Return to Zero's cosmos: the shader pack declares {}, route {}", declared, readRoute);
            }
            return readRoute;
        } catch (Throwable failure) {
            rethrowFatal(failure);
            LOGGER.warn("Return to Zero could not read the shader pack's id map; drawing the cosmos itself.", failure);
            readPack = null;
            return GerRtzPackRoute.UNDECLARED;
        }
    }

    static boolean inShadowPass() {
        return ClientRenderCompatibility.isIrisShadowPass();
    }

    /**
     * Runs {@code write} with this id captured in the vertices it writes, for a pack that declares it. Any other pack could mean anything by
     * the id, so it gets none; the id before stays for the next entity.
     */
    static <T> T withEntityId(int entityId, boolean declared, IntFunction<T> write) {
        Capture capture = Capture.ACCESS;
        if (capture == null) return write.apply(NO_ENTITY_ID);
        int previous;
        try {
            previous = (int) capture.getter.invokeExact();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return write.apply(NO_ENTITY_ID);
        }
        try {
            setEntityId(capture, declared ? entityId : NO_ENTITY_ID);
            return write.apply(declared ? entityId : NO_ENTITY_ID);
        } finally {
            setEntityId(capture, previous);
        }
    }

    /** Whether the pack can be told the void's id at all: without it a declaring pack cannot find the void. */
    static boolean canCaptureEntityId() {
        return Capture.ACCESS != null;
    }

    private static void setEntityId(Capture capture, int entityId) {
        try {
            capture.setter.invokeExact(entityId);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            throw new IllegalStateException("Could not set the entity id for Return to Zero's shell", failure);
        }
    }

    /** The part of the world's depth texture the pack has filled: an upscaler renders into a corner of it. */
    static int[] renderExtent(int width, int height) {
        Scale scale = Scale.ACCESS;
        if (scale != null) {
            try {
                if ((boolean) scale.enabled.invokeExact()) {
                    return new int[] {Math.min(width, (int) scale.renderWidth.invokeExact()), Math.min(height, (int) scale.renderHeight.invokeExact())};
                }
            } catch (Throwable failure) {
                rethrowFatal(failure);
                throw new IllegalStateException("Could not query the active render extent", failure);
            }
        }
        return new int[] {width, height};
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
    }

    private record Declarations(MethodHandle getPack, MethodHandle getIdMap, MethodHandle getEntities, Object marker) {
        private static final Declarations ACCESS = resolve();

        private static Declarations resolve() {
            try {
                ClassLoader loader = GerRtzPackAccess.class.getClassLoader();
                Class<?> iris = loader.loadClass("net.irisshaders.iris.Iris");
                Class<?> pack = loader.loadClass("net.irisshaders.iris.shaderpack.ShaderPack");
                Class<?> idMap = loader.loadClass("net.irisshaders.iris.shaderpack.IdMap");
                Class<?> name = loader.loadClass("net.irisshaders.iris.shaderpack.materialmap.NamespacedId");
                var lookup = MethodHandles.publicLookup();
                return new Declarations(
                        lookup.findStatic(iris, "getCurrentPack", MethodType.methodType(Optional.class)),
                        lookup.findVirtual(pack, "getIdMap", MethodType.methodType(idMap)).asType(MethodType.methodType(Object.class, Object.class)),
                        lookup.findVirtual(idMap, "getEntityIdMap", MethodType.methodType(Object2IntFunction.class))
                                .asType(MethodType.methodType(Object2IntFunction.class, Object.class)),
                        lookup.findConstructor(name, MethodType.methodType(void.class, String.class, String.class))
                                .invoke(GerRtzPackRoute.MARKER_NAMESPACE, GerRtzPackRoute.MARKER_PATH));
            } catch (Throwable failure) {
                rethrowFatal(failure);
                LOGGER.warn("Return to Zero cannot read shader pack id maps; every pack takes the general route.", failure);
                return null;
            }
        }

        private Object currentPack() throws Throwable {
            return ((Optional<?>) getPack.invokeExact()).orElse(null);
        }

        @SuppressWarnings("unchecked")
        private int markerId(Object pack) throws Throwable {
            Object map = (Object) getIdMap.invokeExact(pack);
            Object2IntFunction<Object> entities = (Object2IntFunction<Object>) getEntities.invokeExact(map);
            return entities.getInt(marker);
        }
    }

    private record Capture(MethodHandle getter, MethodHandle setter) {
        private static final Capture ACCESS = resolve();

        private static Capture resolve() {
            try {
                Class<?> type = GerRtzPackAccess.class.getClassLoader().loadClass("net.irisshaders.iris.uniforms.CapturedRenderingState");
                var lookup = MethodHandles.publicLookup();
                Object instance = lookup.findStaticGetter(type, "INSTANCE", type).invoke();
                return new Capture(
                        lookup.findVirtual(type, "getCurrentRenderedEntity", MethodType.methodType(int.class)).bindTo(instance),
                        lookup.findVirtual(type, "setCurrentEntity", MethodType.methodType(void.class, int.class)).bindTo(instance));
            } catch (Throwable failure) {
                rethrowFatal(failure);
                LOGGER.warn("Return to Zero cannot capture entity ids.", failure);
                return null;
            }
        }
    }

    private record Scale(MethodHandle enabled, MethodHandle renderWidth, MethodHandle renderHeight) {
        private static final Scale ACCESS = resolve();

        private static Scale resolve() {
            if (!ModList.get().isLoaded("super_resolution")) return null;
            try {
                ClassLoader loader = GerRtzPackAccess.class.getClassLoader();
                Class<?> api = loader.loadClass("io.homo.superresolution.api.SuperResolutionAPI");
                Class<?> iris = loader.loadClass("io.homo.superresolution.shadercompat.IrisShaderCompatUtils");
                var lookup = MethodHandles.publicLookup();
                return new Scale(
                        lookup.findStatic(iris, "shouldApplySuperResolutionChanges", MethodType.methodType(boolean.class)),
                        lookup.findStatic(api, "getRenderWidth", MethodType.methodType(int.class)),
                        lookup.findStatic(api, "getRenderHeight", MethodType.methodType(int.class)));
            } catch (ReflectiveOperationException | LinkageError failure) {
                LOGGER.warn("Return to Zero cannot resolve the upscaler's render extent.", failure);
                return null;
            }
        }
    }
}
