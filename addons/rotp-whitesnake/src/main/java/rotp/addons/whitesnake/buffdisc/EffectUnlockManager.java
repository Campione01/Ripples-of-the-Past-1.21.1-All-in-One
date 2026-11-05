package rotp.addons.whitesnake.buffdisc;

import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * PATCH1 EffectUnlockManager: every effect a player receives is remembered as unlocked, on top of five base
 * effects. PATCH1 reads it nowhere (the inject screen lists every effect), so it only records.
 */
@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class EffectUnlockManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> BASE_EFFECTS = Set.of(
            "minecraft:instant_health",
            "minecraft:instant_damage",
            "minecraft:speed",
            "minecraft:strength",
            "minecraft:regeneration");

    private EffectUnlockManager() {}

    public static Set<String> getUnlockedEffects(Player player) {
        Set<String> unlocked = new LinkedHashSet<>(BASE_EFFECTS);
        if (player != null
                && player.hasData(WhitesnakeAddon.BUFF_DISC_STATE)) {
            unlocked.addAll(WhitesnakeAddon.buffDiscState(player)
                    .unlockedEffects());
        }
        return unlocked;
    }

    public static boolean isEffectUnlocked(Player player, String effectId) {
        return getUnlockedEffects(player).contains(effectId);
    }

    public static void unlockEffect(ServerPlayer player, String effectId) {
        if (player == null || isEffectUnlocked(player, effectId)) {
            return;
        }
        WhitesnakeAddon.buffDiscState(player).unlockedEffects()
                .add(effectId);
        LOGGER.info("Player {} unlocked effect: {}",
                player.getName().getString(), effectId);
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (event.getEntity() instanceof ServerPlayer player
                && instance != null) {
            instance.getEffect().unwrapKey().ifPresent(key ->
                    unlockEffect(player, key.location().toString()));
        }
    }
}
