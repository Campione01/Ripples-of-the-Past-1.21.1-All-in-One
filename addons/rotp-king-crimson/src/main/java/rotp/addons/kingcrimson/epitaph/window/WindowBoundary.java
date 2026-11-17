package rotp.addons.kingcrimson.epitaph.window;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal.Participant;
import rotp.core.api.control.PlayerOperationDecision;
import rotp.core.api.control.PlayerOperationQuery;

/**
 * The walls between the participants of a window and everybody else (EPITAPH-ARCH 3.5). They keep what a participant
 * did where it can be undone, so that holes are rare: a player outside cannot be harmed by one inside (his death could
 * not be taken back), what a participant dropped is taken by nobody else, a block he changed is broken, blown up or
 * pushed by nobody else, and he opens no container and takes no crafting result. Safety does not rest on them but on
 * the check before the pull-back.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class WindowBoundary {
    /** Only a test switches the walls off, to show what holds without them. */
    public static boolean wallsOn = true;

    private WindowBoundary() {}

    /** The answer to the core for a menu or a crafting result: denied to a participant, without a word. */
    public static PlayerOperationDecision decide(PlayerOperationQuery query) {
        if (!WindowJournal.active || !wallsOn || query.player() == null) {
            return PlayerOperationDecision.pass();
        }
        return switch (query.operation()) {
            case MENU_OPEN_STANDARD, MENU_OPEN_AS_NON_PLAYER, MENU_OPEN_EXTERNAL, CRAFT_RESULT_TAKE ->
                    WindowJournal.isParticipant(query.player()) ? PlayerOperationDecision.deny() : PlayerOperationDecision.pass();
            default -> PlayerOperationDecision.pass();
        };
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!WindowJournal.active || !wallsOn || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player struck && !WindowJournal.isParticipant(struck)
                && WindowJournal.actorOr(event.getSource().getEntity(), event.getSource().getDirectEntity()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (!WindowJournal.active || !wallsOn || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player struck && !WindowJournal.isParticipant(struck)
                && WindowJournal.actorOr(event.getEffectSource(), null) != null) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // What a participant made lies where it can be found again: only he picks it up.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!WindowJournal.active || !wallsOn || event.getPlayer().level().isClientSide()) {
            return;
        }
        Participant owner = WindowJournal.ownerOf(event.getItemEntity());
        if (owner != null && owner != WindowJournal.participantOf(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExperiencePickup(PlayerXpEvent.PickupXp event) {
        if (!WindowJournal.active || !wallsOn || event.getEntity().level().isClientSide()) {
            return;
        }
        Participant owner = WindowJournal.ownerOf(event.getOrb());
        if (owner != null && owner != WindowJournal.participantOf(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // A block a participant changed is his to the end of the window: nobody else breaks it, blows it up or pushes it.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!WindowJournal.active || !wallsOn || event.getLevel().isClientSide()) {
            return;
        }
        if (WindowJournal.changedByOthers(event.getLevel(), event.getPos(), WindowJournal.participantOf(event.getPlayer()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!WindowJournal.active || !wallsOn || event.getLevel().isClientSide()) {
            return;
        }
        Participant by = WindowJournal.actorOr(event.getExplosion().getDirectSourceEntity(),
                event.getExplosion().getIndirectSourceEntity());
        event.getAffectedBlocks().removeIf(pos -> WindowJournal.changedByOthers(event.getLevel(), pos, by));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (!WindowJournal.active || !wallsOn || event.getLevel().isClientSide() || event.getStructureHelper() == null) {
            return;
        }
        var moved = event.getStructureHelper();
        if (!moved.resolve()) {
            return;
        }
        for (BlockPos pos : moved.getToPush()) {
            if (WindowJournal.changedByOthers(event.getLevel(), pos, null)) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : moved.getToDestroy()) {
            if (WindowJournal.changedByOthers(event.getLevel(), pos, null)) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
