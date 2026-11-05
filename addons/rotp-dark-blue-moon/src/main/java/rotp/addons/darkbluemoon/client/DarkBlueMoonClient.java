package rotp.addons.darkbluemoon.client;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The Mods list's Config button opens NeoForge's screen for darkbluemoon-server.toml (owner decision 2026-10-29). */
@Mod(value = DarkBlueMoonAddon.MOD_ID, dist = Dist.CLIENT)
public final class DarkBlueMoonClient {
    public DarkBlueMoonClient(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
