package rotp.core.impl.stands.goldexperience;

import rotp.core.core.JojoMod;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Tells a Gold Experience user's record of a lifeform where it came into a level, loaded or not. */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class GELifeformRecordEvents {

    private GELifeformRecordEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLifeformArrives(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            GECreatedLifeformEffect.noteArrival(event.getEntity());
        }
    }

    // a world closed in single player leaves its records behind; the next one indexes its own
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GECreatedLifeformEffect.forgetServerRecords();
    }
}
