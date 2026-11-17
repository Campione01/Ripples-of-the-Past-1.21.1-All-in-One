package rotp.addons.nextalbum;

import rotp.addons.nextalbum.network.ProsciuttoShadowPayload;
import rotp.addons.nextalbum.state.PesciInheritedWill;
import rotp.addons.nextalbum.state.PesciMammoni;
import rotp.addons.nextalbum.state.ProsciuttoShadow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Next Album 0.1.13 PesciSuitEvents and ProsciuttoSuitEvents. */
@EventBusSubscriber(modid = NextAlbumIds.MOD_ID)
public final class CharacterSuitGameEvents {
    private CharacterSuitGameEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PesciMammoni.tick(player);
            PesciInheritedWill.tick(player);
            ProsciuttoShadow.tick(player);
        }
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (ProsciuttoShadow.isShadow(event.getEntity())) {
            event.setDisplayname(Component.translatable(
                    "nextalbum.prosciutto_suit.unknown"));
        }
    }

    @SubscribeEvent
    public static void onTabListNameFormat(
            PlayerEvent.TabListNameFormat event) {
        if (ProsciuttoShadow.isShadow(event.getEntity())) {
            event.setDisplayName(event.getEntity().getName());
        }
    }

    // A shadow's footsteps are silent, its other player sounds at 30%.
    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        if (event.getEntity() instanceof Player player) {
            muffle(event, player);
        }
    }

    // A player's own sounds come on the server thread; the entity lists are
    // not safe from any other, and a sound played from one has no one to muffle.
    @SubscribeEvent
    public static void onSoundAtPosition(
            PlayLevelSoundEvent.AtPosition event) {
        if (event.getSource() != SoundSource.PLAYERS
                || !(event.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) {
            return;
        }
        Vec3 position = event.getPosition();
        for (Player player : level.getEntitiesOfClass(
                Player.class, new AABB(position, position).inflate(0.01D))) {
            if (muffle(event, player)) {
                return;
            }
        }
    }

    private static boolean muffle(PlayLevelSoundEvent event, Player player) {
        if (event.getSound() == null
                || event.getSource() != SoundSource.PLAYERS
                || !ProsciuttoShadow.isShadow(player)) {
            return false;
        }
        String name = event.getSound().value().getLocation().getPath();
        if (name.contains("step") || name.contains("walk")
                || name.contains("footstep")) {
            event.setCanceled(true);
        } else {
            event.setNewVolume(event.getNewVolume() * 0.3F);
        }
        return true;
    }

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (ProsciuttoShadow.isShadow(event.getEntity())) {
            event.modifyVisibility(ProsciuttoShadow.SHADOW_VISIBILITY);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob
                && ProsciuttoShadow.shouldLoseTarget(
                        mob, event.getNewAboutToBeSetTarget())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        ProsciuttoShadow.markCombat(event.getEntity());
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!ProsciuttoShadow.isCombatDamage(event.getSource())) {
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            ProsciuttoShadow.markCombat(victim);
        }
        ProsciuttoShadow.markCombat(ProsciuttoShadow.playerFrom(
                event.getSource().getDirectEntity()));
        ProsciuttoShadow.markCombat(ProsciuttoShadow.playerFrom(
                event.getSource().getEntity()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProsciuttoShadowPayload.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProsciuttoShadowPayload.sync(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target
                && event.getEntity() instanceof ServerPlayer viewer) {
            ProsciuttoShadowPayload.sendTo(viewer, target);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            var from = event.getOriginal().getPersistentData();
            var to = event.getEntity().getPersistentData();
            to.putBoolean(ProsciuttoShadow.SHADOW_KEY,
                    from.getBoolean(ProsciuttoShadow.SHADOW_KEY));
            to.putLong(ProsciuttoShadow.LAST_COMBAT_KEY,
                    from.getLong(ProsciuttoShadow.LAST_COMBAT_KEY));
        }
    }
}
