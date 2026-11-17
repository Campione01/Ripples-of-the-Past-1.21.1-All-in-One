package rotp.addons.kingcrimson.epitaph;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseAbility;
import rotp.addons.kingcrimson.ability.KingCrimsonTimeEraseStepAbility;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;
import rotp.addons.kingcrimson.epitaph.human.PhantomAbilityAllowList;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The one gate every press of an ability passes, on the event the core posts for it (EPITAPH-ARCH, ruling R9). A phantom
 * presses what is on the table and nothing else, whoever pressed for it. A walked player presses what his walk lets
 * through: his way out. From that press his time is his own again (ruling R5).
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class EpitaphAbilityGate {
    private EpitaphAbilityGate() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAbilityKeyPress(RipplesAbilityKeyPressEvent event) {
        if (!EpitaphSessions.any) {
            return;
        }
        LivingEntity user = event.getEntity();
        // The event is posted on a client too: the predictions are the server thread and nobody else.
        if (user.level().isClientSide()) {
            return;
        }
        EpitaphSandbox.countPass();
        Ability ability = event.getAbility();
        SandboxRoom room = EpitaphSessions.roomOf(user);
        if (room != null) {
            if (!PhantomAbilityAllowList.allows(ability, StandPower.get(user))) {
                event.setCanceled(true);
            } else {
                room.notePress(user);
            }
            return;
        }
        if (user instanceof ServerPlayer player && EpitaphSessions.isWalked(player)) {
            if (ForcedWalk.refuse(ability, event.getPower(), player) != null) {
                event.setCanceled(true);
            } else if (ability instanceof KingCrimsonTimeEraseAbility || ability instanceof KingCrimsonTimeEraseStepAbility) {
                // His own prediction, or the one of another that walks him.
                EpitaphSession session = EpitaphSessions.walking(player);
                if (session != null) {
                    session.wayOutPressed(player);
                }
            }
        }
    }
}
