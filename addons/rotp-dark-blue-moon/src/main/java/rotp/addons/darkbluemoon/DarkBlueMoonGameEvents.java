package rotp.addons.darkbluemoon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rotp.addons.darkbluemoon.entity.DarkBlueMoonEntity;
import rotp.addons.darkbluemoon.util.BarnacleBinds;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.addons.darkbluemoon.util.ScaleShotAmmo;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The 1.16 game-bus handlers: DarkBlueMoonEventHandler (water and rain boosts), ScaleShotAmmoHandler (scale regen),
 * VulnerabilityEventHandler, BarnacleSplitHandler and BarnacleTrapTickHandler.
 */
@EventBusSubscriber(modid = DarkBlueMoonAddon.MOD_ID)
public final class DarkBlueMoonGameEvents {
    private static final List<Holder<MobEffect>> WATER_VANILLA_EFFECTS = List.of(
            MobEffects.WATER_BREATHING, MobEffects.DOLPHINS_GRACE, MobEffects.NIGHT_VISION, MobEffects.DIG_SPEED);
    private static final Map<UUID, Integer> TRAP_TARGET_TIMER = new HashMap<>();

    private DarkBlueMoonGameEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        tickWaterAndRainBoosts(player);
        ScaleShotAmmo.tickRegen(player);
    }

    /*
     * 1.16 DarkBlueMoonEventHandler: a Dark Blue Moon user in water gets the water boost, Water Breathing (7440 ticks),
     * Dolphin's Grace III, Night Vision (400) and Haste; in the rain under the open sky the rain boost instead. Out of
     * both, 1.16 removed those four vanilla effects from every player on every tick, potions and beacons included; the
     * port removes only the instances this handler gave (no particles, not ambient, icon shown).
     */
    private static void tickWaterAndRainBoosts(Player player) {
        boolean hasDbm = hasDarkBlueMoon(player);
        boolean inWater = hasDbm && player.isAlive() && DbmWater.isInWater(player);
        boolean inRain = hasDbm && player.isAlive() && !inWater
                && player.level().isRaining() && player.level().canSeeSky(player.blockPosition());
        if (inWater) {
            player.addEffect(new MobEffectInstance(DarkBlueMoonAddon.DARK_BLUE_MOON_WATER_USER, 40, 0, false, false, true));
            addBoostEffect(player, MobEffects.WATER_BREATHING, 7440, 0);
            addBoostEffect(player, MobEffects.DOLPHINS_GRACE, 40, 2);
            addBoostEffect(player, MobEffects.NIGHT_VISION, 400, 0);
            addBoostEffect(player, MobEffects.DIG_SPEED, 40, 0);
            player.removeEffect(DarkBlueMoonAddon.DARK_BLUE_MOON_RAIN_USER);
        }
        else if (inRain) {
            player.addEffect(new MobEffectInstance(DarkBlueMoonAddon.DARK_BLUE_MOON_RAIN_USER, 40, 0, false, false, true));
            player.removeEffect(DarkBlueMoonAddon.DARK_BLUE_MOON_WATER_USER);
            removeOwnVanillaEffects(player);
        }
        else {
            player.removeEffect(DarkBlueMoonAddon.DARK_BLUE_MOON_WATER_USER);
            player.removeEffect(DarkBlueMoonAddon.DARK_BLUE_MOON_RAIN_USER);
            removeOwnVanillaEffects(player);
        }
    }

    // addEffect copies the new instance's flags onto the held one whatever the duration, so a potion or beacon effect
    // under the boost would look like the boost's own when the user leaves the water
    private static void addBoostEffect(Player player, Holder<MobEffect> effect, int duration, int amplifier) {
        MobEffectInstance held = player.getEffect(effect);
        if (held == null || isOwnBoostInstance(held) || held.getAmplifier() < amplifier) {
            player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, true));
        }
    }

    private static void removeOwnVanillaEffects(Player player) {
        for (Holder<MobEffect> effect : WATER_VANILLA_EFFECTS) {
            MobEffectInstance instance = player.getEffect(effect);
            if (instance != null && isOwnBoostInstance(instance)) {
                player.removeEffect(effect);
            }
        }
    }

    private static boolean isOwnBoostInstance(MobEffectInstance instance) {
        return !instance.isAmbient() && !instance.isVisible() && instance.showIcon();
    }

    // 1.16 asked the Stand power's type, whether the Stand was out or not
    private static boolean hasDarkBlueMoon(Player player) {
        StandPower power = StandPower.get(player);
        return power != null && power.hasPower() && power.getPowerType() == DarkBlueMoonAddon.DARK_BLUE_MOON.get();
    }

    /** 1.16 VulnerabilityEventHandler: no healing above half health, and none past it. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || !entity.isAlive() || entity.getEffect(DarkBlueMoonAddon.VULNERABILITY) == null) {
            return;
        }
        float half = entity.getMaxHealth() * 0.5F;
        float current = entity.getHealth();
        if (current >= half) {
            event.setCanceled(true);
        }
        else {
            float allowed = half - current;
            if (event.getAmount() > allowed) {
                event.setAmount(allowed);
            }
        }
    }

    /**
     * 1.16 BarnacleSplitHandler: splitting a barnacle with shears also gives its shell, gives the shears back worn by 2
     * (unless in Creative or they would break) and plays the shearing sound.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !event.getCrafting().is(DarkBlueMoonAddon.BARNACLE_MEAT.get())) {
            return;
        }
        Container inventory = event.getInventory();
        if (!(inventory instanceof CraftingContainer craftInv)) {
            return;
        }
        boolean hasBarnacle = false;
        boolean hasShears = false;
        for (int i = 0; i < craftInv.getContainerSize(); i++) {
            ItemStack stack = craftInv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(DarkBlueMoonAddon.BARNACLE_ITEM.get())) {
                hasBarnacle = true;
            }
            else if (isShears(stack)) {
                hasShears = true;
            }
        }
        if (!hasBarnacle || !hasShears) {
            return;
        }
        ItemStack shell = new ItemStack(DarkBlueMoonAddon.BARNACLE_SHELL.get());
        if (!player.getInventory().add(shell)) {
            player.drop(shell, false);
        }
        for (int i = 0; i < craftInv.getContainerSize(); i++) {
            ItemStack stack = craftInv.getItem(i);
            if (!stack.isEmpty() && isShears(stack) && !player.getAbilities().instabuild) {
                ItemStack returned = new ItemStack(Items.SHEARS);
                returned.setDamageValue(stack.getDamageValue() + 2);
                if (returned.getDamageValue() < returned.getMaxDamage() && !player.getInventory().add(returned)) {
                    player.drop(returned, false);
                }
            }
        }
        player.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
    }

    private static boolean isShears(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem || stack.is(Items.SHEARS);
    }

    /**
     * 1.16 BarnacleTrapTickHandler: every living entity within a block of a summoned Dark Blue Moon's trap is bound for
     * 600 ticks, at once if the trap block is a liquid, else after 10 ticks there; a bound player who has left every trap
     * for 40 ticks is freed, whatever bound it.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Player player : level.players()) {
            DarkBlueMoonEntity dbm = summonedTrapStand(player);
            if (dbm == null) {
                continue;
            }
            BlockPos trapPos = dbm.getBarnacleTrapPos();
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(trapPos).inflate(1.0),
                    LivingEntity::isAlive);
            if (targets.isEmpty()) {
                dbm.resetBarnacleTrapDelay();
            }
            else if (DbmWater.isLiquid(level.getBlockState(trapPos))) {
                applyBind(targets);
            }
            else if (dbm.incBarnacleTrapDelay() >= 10) {
                applyBind(targets);
            }
        }
        for (Player player : level.players()) {
            UUID id = player.getUUID();
            if (!player.hasEffect(DarkBlueMoonAddon.BARNACLE_BIND)) {
                TRAP_TARGET_TIMER.remove(id);
            }
            else if (isOnAnyTrap(level, player)) {
                TRAP_TARGET_TIMER.put(id, 0);
            }
            else {
                int timer = TRAP_TARGET_TIMER.getOrDefault(id, 0) + 1;
                if (timer >= 40) {
                    player.removeEffect(DarkBlueMoonAddon.BARNACLE_BIND);
                    BarnacleBinds.removeAllBarnacles(level, player);
                    TRAP_TARGET_TIMER.remove(id);
                }
                else {
                    TRAP_TARGET_TIMER.put(id, timer);
                }
            }
        }
    }

    private static DarkBlueMoonEntity summonedTrapStand(Player player) {
        DarkBlueMoonEntity dbm = DarkBlueMoonEntity.current(player);
        return dbm != null && dbm.hasBarnacleTrap() ? dbm : null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DarkBlueMoonEntity.forgetRecalled(event.getEntity());
    }

    private static boolean isOnAnyTrap(ServerLevel level, LivingEntity entity) {
        for (Player player : level.players()) {
            DarkBlueMoonEntity dbm = summonedTrapStand(player);
            if (dbm != null && new AABB(dbm.getBarnacleTrapPos()).inflate(1.0).intersects(entity.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    private static void applyBind(List<LivingEntity> targets) {
        for (LivingEntity target : targets) {
            if (!target.hasEffect(DarkBlueMoonAddon.BARNACLE_BIND)) {
                target.addEffect(new MobEffectInstance(DarkBlueMoonAddon.BARNACLE_BIND, 600, 0, false, false, false));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ScaleShotAmmo.clear();
        BarnacleBinds.clear();
        TRAP_TARGET_TIMER.clear();
    }
}
