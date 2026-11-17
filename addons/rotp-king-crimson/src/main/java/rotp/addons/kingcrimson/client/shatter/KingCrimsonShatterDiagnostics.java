package rotp.addons.kingcrimson.client.shatter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Deque;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import rotp.addons.kingcrimson.client.KingCrimsonTemporalClientState;

public final class KingCrimsonShatterDiagnostics {
    private static final String PREFIX = "rotp.kingcrimson.shatter.";
    public static final boolean ENABLED = Boolean.getBoolean(PREFIX + "diagnostics");
    public static final boolean NIGHT_OFF = ENABLED && Boolean.getBoolean(PREFIX + "nightOff");
    public static final boolean VELOCITY_OFF = ENABLED && Boolean.getBoolean(PREFIX + "velocityOff");
    // Paired presses in one client: each new erase has the loose blocks on, then off, then on again.
    private static final boolean LOOSE_BLOCKS_ALTERNATE = Boolean.getBoolean(PREFIX + "looseAlternate");
    private static boolean looseBlocksOff;
    // The same for the universe's fade at the return on a pack that does not declare the void.
    private static final boolean RETURN_FADE_ALTERNATE = Boolean.getBoolean(PREFIX + "returnFadeAlternate");
    private static boolean returnFadeOff;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int frames;
    private static String lastSample;
    private static boolean failed;

    private KingCrimsonShatterDiagnostics() {}

    static boolean looseBlocksOn() {
        return !looseBlocksOff;
    }

    static boolean returnFadeOn() {
        return !returnFadeOff;
    }

    /** A new erase's session: with an alternation on, its loose blocks or its return fade are the other way from the last one's. */
    static void nextErase() {
        if (LOOSE_BLOCKS_ALTERNATE) {
            looseBlocksOff = !looseBlocksOff;
            LOGGER.info("King Crimson shatter diagnostics: loose blocks {} for this erase", looseBlocksOff ? "off" : "on");
        }
        if (RETURN_FADE_ALTERNATE) {
            returnFadeOff = !returnFadeOff;
            LOGGER.info("King Crimson shatter diagnostics: return fade {} for this erase", returnFadeOff ? "off" : "on");
        }
    }

    public static void afterLevel() {
        if (!ENABLED || failed || !ModList.get().isLoaded("acceleratedrendering")) return;
        try {
            boolean erase = KingCrimsonTemporalClientState.localTimeErase();
            String sample = erase + ":" + Minecraft.getInstance().smartCull + ":"
                    + ArReadback.ENTITY.depth() + ":" + ArReadback.ITEM.depth() + ":" + ArReadback.TEXT.depth()
                    + ":" + (boolean) ArReadback.ENTITY.accelerated.invokeExact();
            if (!sample.equals(lastSample) || ++frames % 240 == 0) {
                lastSample = sample;
                LOGGER.info("King Crimson shatter AR probe [erase:smartCull:entity:item:text:accelerated]={}", sample);
            }
        } catch (Exception | LinkageError failure) {
            unavailable(failure);
        } catch (Throwable failure) {
            if (failure instanceof Error fatal) throw fatal;
            unavailable(failure);
        }
    }

    private static void unavailable(Throwable failure) {
        failed = true;
        LOGGER.warn("King Crimson shatter AR probe unavailable; stack depth unmeasured.", failure);
    }

    private record ArReadback(MethodHandle stack, MethodHandle accelerated) {
        private static final ArReadback ENTITY = find("entities.AcceleratedEntityRenderingFeature");
        private static final ArReadback ITEM = find("items.AcceleratedItemRenderingFeature");
        private static final ArReadback TEXT = find("text.AcceleratedTextRenderingFeature");

        private static ArReadback find(String name) {
            try {
                Class<?> type = KingCrimsonShatterDiagnostics.class.getClassLoader().loadClass(
                        "com.github.argon4w.acceleratedrendering.features." + name);
                var lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
                return new ArReadback(lookup.findStaticGetter(type, "PIPELINE_CONTROLLER_STACK", Deque.class),
                        lookup.findStatic(type, "shouldUseAcceleratedPipeline", MethodType.methodType(boolean.class)));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("AR probe cannot bind " + name, exception);
            }
        }

        int depth() throws Throwable {
            return ((Deque<?>) stack.invokeExact()).size();
        }
    }
}
