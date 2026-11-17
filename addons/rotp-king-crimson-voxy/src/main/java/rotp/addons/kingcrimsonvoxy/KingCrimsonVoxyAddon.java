package rotp.addons.kingcrimsonvoxy;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Client only. Without Voxy, Sodium, Iris or King Crimson the mod is loaded and nothing else of it runs. */
@Mod(value = KingCrimsonVoxyAddon.MOD_ID, dist = Dist.CLIENT)
public final class KingCrimsonVoxyAddon {
    public static final String MOD_ID = "rotp_kingcrimson_voxy";

    public KingCrimsonVoxyAddon() {
        if (KcVoxyStatus.applicable()) {
            NeoForge.EVENT_BUS.register(KcVoxyClientEvents.class);
            if (KcVoxyTrace.ENABLED) {
                NeoForge.EVENT_BUS.register(KcVoxyTraceEvents.class);
            }
        }
    }
}
