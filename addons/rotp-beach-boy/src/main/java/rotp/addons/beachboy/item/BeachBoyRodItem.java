package rotp.addons.beachboy.item;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * 1.16 BeachBoyRodItem: Beach Boy's rod, given and taken by the Stand. Held right click charges a cast (full at 1 s,
 * a bow's charge curve) and casts the hook on release; with the hook out it reels it in. Its melee hit is the Stand's
 * power (at least 4, an axe's speed) and may make the target bleed. It takes the fishing rod's enchantments.
 */
public class BeachBoyRodItem extends Item {
    private static final double DEFAULT_DAMAGE = 7.0;
    private static final double ATTACK_SPEED = -3.0;

    public BeachBoyRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        Double scaled = stack.get(BeachBoyAddon.ROD_DAMAGE.get());
        double damage = scaled != null ? scaled - 1.0 : DEFAULT_DAMAGE;
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** 1.16 syncStandScaledDamage: the held rod hits as hard as the lash. */
    public static void syncStandScaledDamage(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        StandPower power = StandPower.get(player);
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(power);
        double damage = stand != null ? Math.max(4.0, stand.getAttackDamage())
                : Math.max(4.0, BeachBoyStandUtil.standPowerStat(power));
        Double current = stack.get(BeachBoyAddon.ROD_DAMAGE.get());
        if (current == null || Double.compare(current, damage) != 0) {
            stack.set(BeachBoyAddon.ROD_DAMAGE.get(), damage);
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        BeachBoyCombatUtil.tryApplyStrikeEffects(attacker, target);
        return true;
    }

    public static boolean isCast(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && Boolean.TRUE.equals(stack.get(BeachBoyAddon.ROD_CAST.get()));
    }

    public static void setCast(@Nullable ItemStack stack, boolean cast) {
        if (stack != null && !stack.isEmpty()) {
            stack.set(BeachBoyAddon.ROD_CAST.get(), cast);
        }
    }

    public static float getReel(@Nullable ItemStack stack) {
        Float reel = stack != null && !stack.isEmpty() ? stack.get(BeachBoyAddon.ROD_REEL.get()) : null;
        return reel != null ? reel : 16.0F;
    }

    public static void setReel(@Nullable ItemStack stack, float length) {
        if (stack != null && !stack.isEmpty()) {
            stack.set(BeachBoyAddon.ROD_REEL.get(), length);
        }
    }

    public static float getChargeStrength(int usedTicks) {
        float f = usedTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Mth.clamp(f, 0.0F, 1.0F);
    }

    // 1.16 StandSummonUtil.ensureSummoned
    private static void ensureSummoned(Player player, @Nullable StandPower power) {
        if (power != null && power.hasPower() && !power.isSummoned() && !player.level().isClientSide()
                && power.getPowerType() instanceof EntityStandType standType) {
            standType.summon(player, power, stand -> {}, true);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!BeachBoyStandUtil.isBeachBoyUser(player)) {
            return InteractionResultHolder.fail(stack);
        }
        ensureSummoned(player, StandPower.get(player));
        if (!level.isClientSide() && !BeachBoyStandUtil.isBeachBoySummoned(player)) {
            return InteractionResultHolder.fail(stack);
        }
        BeachBoyHookEntity existing = BeachBoyStandUtil.findHook(player);
        if (existing != null) {
            if (!level.isClientSide()) {
                existing.reelIn();
            }
            setCast(stack, false);
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ensureSummoned(player, StandPower.get(player));
        if (!BeachBoyStandUtil.isBeachBoySummoned(player) || BeachBoyStandUtil.findHook(player) != null) {
            return;
        }
        int used = getUseDuration(stack, entity) - timeLeft;
        float charge = getChargeStrength(used);
        setCast(stack, true);
        float volume = 0.5F + charge * 0.25F;
        float pitch = 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F) + charge * 0.15F;
        if (!level.isClientSide()) {
            level.addFreshEntity(new BeachBoyHookEntity(level, player, charge));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.NEUTRAL, volume, pitch);
        }
        else {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.NEUTRAL, volume, pitch, false);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        // The client already emptied the slot when it sent the drop; mark it stale so the next sync puts the rod back.
        if (player instanceof ServerPlayer serverPlayer) {
            Inventory inventory = serverPlayer.getInventory();
            serverPlayer.containerMenu.findSlot(inventory, inventory.selected)
                    .ifPresent(slot -> serverPlayer.containerMenu.setRemoteSlot(slot, ItemStack.EMPTY));
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem();
    }
}
