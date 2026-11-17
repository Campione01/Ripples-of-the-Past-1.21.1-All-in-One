package rotp.addons.justice.client;

import java.util.OptionalInt;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import rotp.addons.justice.JusticeAddon;
import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.powersystem.ability.AbilityId;

@EventBusSubscriber(modid = JusticeAddon.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JusticeSelectionPolicy {
    private static final OptionalInt GREEN = OptionalInt.of(0xFF00FF00);

    private JusticeSelectionPolicy() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(JusticeAddon.id("selection"), query -> {
            AbilityId id = query.ability().getAbilityId();
            boolean green = query.power() != null && JusticeAddon.STAND.getId().equals(id.powerTypeId())
                    && JusticeClientState.greenSelection(id.nameInMoveset(), query.power().getUser());
            return green ? GREEN : OptionalInt.empty();
        });
    }
}
