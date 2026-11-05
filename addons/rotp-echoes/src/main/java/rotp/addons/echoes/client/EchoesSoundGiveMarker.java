package rotp.addons.echoes.client;

import java.util.List;

import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.state.EchoesSoundService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** R772 F11: the master's SoundGiveMarkerRenderer: the sound-give icon over each entity the user gave his sound to. */
public final class EchoesSoundGiveMarker extends MarkerRenderer {
    private static final double RANGE = 100.0D;

    public EchoesSoundGiveMarker(Minecraft mc) {
        super(RotpEchoes.id("textures/ability/sound_give.png"), mc);
    }

    @Override
    protected boolean shouldRender() {
        return EchoesSoundClient.hasGiveTargets() && mc.player != null && mc.level != null;
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(RANGE),
                entity -> EchoesSoundClient.gaveTo(entity.getUUID()))) {
            if (entity instanceof LivingEntity living && !EchoesSoundService.isImmune(living)) {
                list.add(new MarkerInstance(entity.getPosition(partialTick).add(0.0D, entity.getBbHeight() * 1.1D, 0.0D),
                        true));
            }
        }
    }
}
