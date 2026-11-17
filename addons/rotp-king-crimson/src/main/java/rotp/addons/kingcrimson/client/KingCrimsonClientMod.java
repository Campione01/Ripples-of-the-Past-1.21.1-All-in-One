package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonClientConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = KingCrimsonAddon.MOD_ID, dist = Dist.CLIENT)
public final class KingCrimsonClientMod {
    public KingCrimsonClientMod(ModContainer modContainer) {
        modContainer.registerConfig(
                ModConfig.Type.CLIENT,
                KingCrimsonClientConfig.SPEC,
                KingCrimsonClientConfig.FILE_NAME);
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
