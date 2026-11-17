package rotp.addons.kingcrimson.client;

import rotp.core.init.ModStatusEffects;

import rotp.addons.kingcrimson.state.KingCrimsonEraseWitnesses;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * A player whom an erase does not fully take (Gold Experience Requiem's holder, by the core's witness rules) sees its
 * user through it, drawn as he is at his real place (owner 2026-11-07). The server hides an eraser from every client
 * alike; the witness's own client shows him again. It costs one static read while no erase runs.
 */
final class KingCrimsonEraseWitnessView {
    private KingCrimsonEraseWitnessView() {}

    private static boolean seesThroughErase(Player viewer, LivingEntity entity) {
        return entity instanceof Player eraser
                && eraser != viewer
                && KingCrimsonTemporalStateService.isErasing(eraser)
                && KingCrimsonEraseWitnesses.seesEraser(viewer, eraser);
    }

    /** Every client tick: the invisible flag the server gave an eraser is taken off him on a witness's client. */
    static void tick() {
        if (!KingCrimsonTemporalState.anyErase()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        for (Player player : minecraft.level.players()) {
            // Set again each tick: an update of his shared flags from the server brings the flag back.
            if (player.isInvisible() && seesThroughErase(minecraft.player, player)) {
                player.setInvisible(false);
            }
        }
    }

    /** The core draws no fully invisible entity (ClientTickHandler.onLivingRender); a witness is shown the eraser. */
    static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled()
                && KingCrimsonTemporalState.anyErase()
                && Minecraft.getInstance().player != null
                && event.getEntity().hasEffect(ModStatusEffects.FULL_INVISIBILITY)
                && seesThroughErase(Minecraft.getInstance().player, event.getEntity())) {
            event.setCanceled(false);
        }
    }
}
