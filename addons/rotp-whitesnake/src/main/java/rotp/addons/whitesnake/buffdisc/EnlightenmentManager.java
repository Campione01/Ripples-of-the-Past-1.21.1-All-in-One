package rotp.addons.whitesnake.buffdisc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * PATCH1 EnlightenmentManager: a player who logs in, respawns or changes dimension with the enlightenment effect
 * loses it and gains an enlightenment level (three at most), each level widening the effect batches. PATCH1 reads
 * the batches nowhere, and neither the 1.16 core nor this core registers that effect, so the manager stays dormant
 * unless an add-on supplies it.
 */
@EventBusSubscriber(modid = WhitesnakeAddon.MOD_ID)
public final class EnlightenmentManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    // PATCH1 asked for jojo:enlightenment; jojo_ripples is the core namespace here
    private static final ResourceLocation ENLIGHTENMENT_EFFECT =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "enlightenment");
    private static final int MAX_COUNT = 3;
    private static final Set<String> BASE_EFFECTS = Set.of(
            "minecraft:instant_health",
            "minecraft:poison",
            "rotp_whitesnake:explosion");
    private static final List<String> BATCH_1 = List.of(
            "minecraft:speed",
            "minecraft:strength",
            "minecraft:regeneration",
            "minecraft:resistance",
            "minecraft:fire_resistance",
            "minecraft:water_breathing",
            "minecraft:night_vision",
            "minecraft:jump_boost",
            "minecraft:haste",
            "minecraft:absorption");
    private static final List<String> BATCH_2 = List.of(
            "minecraft:invisibility",
            "minecraft:health_boost",
            "minecraft:saturation",
            "minecraft:luck",
            "minecraft:slow_falling",
            "minecraft:conduit_power",
            "minecraft:dolphins_grace",
            "minecraft:hero_of_the_village",
            "minecraft:glowing",
            "minecraft:levitation",
            "minecraft:slowness",
            "minecraft:weakness",
            "minecraft:wither");

    private EnlightenmentManager() {}

    public static int getEnlightenmentCount(Player player) {
        return player.hasData(WhitesnakeAddon.BUFF_DISC_STATE)
                ? WhitesnakeAddon.buffDiscState(player)
                        .enlightenmentCount()
                : 0;
    }

    public static Set<String> getUnlockedEffects(Player player) {
        Set<String> unlocked = new LinkedHashSet<>(BASE_EFFECTS);
        int count = getEnlightenmentCount(player);
        if (count >= 1) {
            unlocked.addAll(BATCH_1);
        }
        if (count >= 2) {
            unlocked.addAll(BATCH_2);
        }
        if (count >= 3) {
            EffectRegistry.getAllEffects().forEach(id ->
                    unlocked.add(id.toString()));
        }
        return unlocked;
    }

    public static boolean isEffectUnlocked(Player player, String effectId) {
        return getUnlockedEffects(player).contains(effectId);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            checkAndApplyEnlightenment(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            checkAndApplyEnlightenment(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            checkAndApplyEnlightenment(player);
        }
    }

    private static void checkAndApplyEnlightenment(ServerPlayer player) {
        if (getEnlightenmentCount(player) >= MAX_COUNT) {
            return;
        }
        BuiltInRegistries.MOB_EFFECT.getHolder(ENLIGHTENMENT_EFFECT)
                .filter(player::hasEffect)
                .ifPresent(effect -> {
                    int newCount = getEnlightenmentCount(player) + 1;
                    WhitesnakeAddon.buffDiscState(player)
                            .setEnlightenmentCount(newCount);
                    player.removeEffect(effect);
                    LOGGER.info(
                            "Player {} reached enlightenment level {}",
                            player.getName().getString(), newCount);
                });
    }
}
