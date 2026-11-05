package rotp.addons.madeinheaven.client;

import rotp.core.api.client.time.ClientEntityClockRates;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.state.TimeLagService;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MadeInHeavenAddon.MOD_ID, dist = Dist.CLIENT)
public final class MadeInHeavenClientMod {
    public MadeInHeavenClientMod(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class, ConfigurationScreen::new);
        // A1-CLOCK: idle animations keep the pace of the being's own time.
        ClientEntityClockRates.register(
                MadeInHeavenAddon.id("time_acceleration"),
                TimeLagService::clockRate);
    }
}
