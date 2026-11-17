package rotp.addons.kingcrimsonvoxy;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Registered on trace runs only (-Drotp.kcvoxy.trace=true): the end of each frame, for the trace's last mark. */
final class KcVoxyTraceEvents {
    private KcVoxyTraceEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFrameEnd(RenderFrameEvent.Post event) {
        KcVoxyTrace.framePost();
    }
}
