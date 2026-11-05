package rotp.addons.nextalbum.client.compat;

import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.client.render.AjaStoneCurioRenderer;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

public final class AjaNecklaceCuriosClient {
    private AjaNecklaceCuriosClient() {}

    public static void register() {
        CuriosRendererRegistry.register(
                NextAlbumAddon.AJA_STONE_NECKLACE.get(),
                AjaStoneCurioRenderer::new);
    }
}
