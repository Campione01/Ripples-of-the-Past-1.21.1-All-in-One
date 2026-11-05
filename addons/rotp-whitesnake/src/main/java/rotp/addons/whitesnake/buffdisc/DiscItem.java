package rotp.addons.whitesnake.buffdisc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import rotp.core.util.functions.ItemUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * PATCH1 DiscItem, the Buff disc: blank, one effect, or several fused on an anvil. Using it applies its effects to
 * the user; thrown by Whitesnake it applies them to the entity hit.
 */
public final class DiscItem extends Item {
    // the blank_disc recipe makes four; 1.21 refuses a recipe result above the item's stack size
    public static final int MAX_STACK_SIZE = 4;
    public static final int MULTI_AMPLIFIER = 2;
    public static final int MULTI_DURATION = 100;
    public static final int FUSED_SINGLE_DURATION = 100;
    public static final int EXPLOSION_DELAY_TICKS = 40;
    private static final int SHOWN_EFFECTS = 3;

    public DiscItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createBlank() {
        return new ItemStack(WhitesnakeAddon.DISC_ITEM.get());
    }

    public static ItemStack createEffect(
            Holder<MobEffect> effect, int amplifier, int duration) {
        return effect.unwrapKey()
                .map(key -> createEffect(
                        key.location(), amplifier, duration))
                .orElseGet(DiscItem::createBlank);
    }

    public static ItemStack createEffect(
            ResourceLocation effectId, int amplifier, int duration) {
        ItemStack stack = createBlank();
        stack.set(
                WhitesnakeAddon.DISC_EFFECT.get(),
                DiscEffectData.single(effectId, amplifier, duration));
        return stack;
    }

    public static ItemStack createMultiDisc(
            List<ResourceLocation> effectIds) {
        ItemStack stack = createBlank();
        stack.set(
                WhitesnakeAddon.DISC_EFFECT.get(),
                DiscEffectData.multi(effectIds));
        return stack;
    }

    public static Optional<DiscEffectData> getData(ItemStack stack) {
        if (stack == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof DiscItem)) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                stack.get(WhitesnakeAddon.DISC_EFFECT.get()));
    }

    public static boolean isDisc(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof DiscItem;
    }

    public static boolean hasEffect(ItemStack stack) {
        return getData(stack).filter(DiscEffectData::hasEffect)
                .isPresent();
    }

    public static boolean isMultiDisc(ItemStack stack) {
        return getData(stack).filter(DiscEffectData::isMulti)
                .isPresent();
    }

    public static boolean isBlank(ItemStack stack) {
        return isDisc(stack) && !hasEffect(stack) && !isMultiDisc(stack);
    }

    public static List<ResourceLocation> getMultiDiscEffects(
            ItemStack stack) {
        return getData(stack).filter(DiscEffectData::isMulti)
                .map(DiscEffectData::effects)
                .orElse(List.of());
    }

    public static boolean canFuse(ItemStack disc1, ItemStack disc2) {
        return isDisc(disc1) && isDisc(disc2)
                && (hasEffect(disc1) || isMultiDisc(disc1))
                && (hasEffect(disc2) || isMultiDisc(disc2));
    }

    public static int effectCount(ItemStack stack) {
        if (isMultiDisc(stack)) {
            return getMultiDiscEffects(stack).size();
        }
        return hasEffect(stack) ? 1 : 0;
    }

    // PATCH1 fuseDiscs: the distinct effects of both discs; one left becomes a plain disc at level 1 for 5 s
    public static ItemStack fuseDiscs(ItemStack disc1, ItemStack disc2) {
        List<ResourceLocation> unique = new ArrayList<>();
        for (ItemStack disc : List.of(disc1, disc2)) {
            for (ResourceLocation id : effectsOf(disc)) {
                if (!unique.contains(id)) {
                    unique.add(id);
                }
            }
        }
        if (unique.isEmpty()) {
            return createBlank();
        }
        return unique.size() <= 1
                ? createEffect(unique.get(0), 0, FUSED_SINGLE_DURATION)
                : createMultiDisc(unique);
    }

    private static List<ResourceLocation> effectsOf(ItemStack disc) {
        Optional<DiscEffectData> data = getData(disc);
        if (data.isEmpty()) {
            return List.of();
        }
        if (data.get().isMulti()) {
            return data.get().effects();
        }
        return data.get().effect().map(List::of).orElse(List.of());
    }

    public static void applyEffectTo(
            LivingEntity target, ItemStack stack) {
        if (target.level().isClientSide()) {
            return;
        }
        Optional<DiscEffectData> data = getData(stack);
        if (data.isEmpty()) {
            return;
        }
        if (data.get().isMulti()) {
            for (ResourceLocation effectId : data.get().effects()) {
                applySingleEffect(
                        target, effectId, MULTI_AMPLIFIER, MULTI_DURATION);
            }
            return;
        }
        DiscEffectData single = data.get().isRandom()
                ? rollRandomEffect(target)
                : data.get();
        single.effect().ifPresent(effectId -> applySingleEffect(
                target, effectId, single.amplifier(), single.duration()));
    }

    private static void applySingleEffect(
            LivingEntity target,
            ResourceLocation effectId,
            int amplifier,
            int duration) {
        if (DiscEffectData.INSTANT_KILL.equals(effectId)) {
            DamageSource source = target.damageSources().fellOutOfWorld();
            target.hurt(source, Float.MAX_VALUE);
            if (target.isAlive()) {
                // PATCH1 forced the kill when the damage did not take
                target.setHealth(0.0F);
                target.die(source);
            }
            target.deathTime = 0;
        }
        else if (DiscEffectData.EXPLOSION.equals(effectId)) {
            WhitesnakeAddon.buffDiscState(target).scheduleExplosion(
                    EXPLOSION_DELAY_TICKS, 2.0F + amplifier);
        }
        else {
            effectHolder(effectId).ifPresent(effect -> target.addEffect(
                    new MobEffectInstance(
                            effect, duration, amplifier,
                            false, true, true)));
        }
    }

    // PATCH1 onLivingUpdate: the delayed explosion hurts the disc's target and pushes everything near it away
    public static void tickPendingExplosion(LivingEntity entity) {
        if (entity.level().isClientSide()
                || !entity.hasData(WhitesnakeAddon.BUFF_DISC_STATE)) {
            return;
        }
        BuffDiscState state = WhitesnakeAddon.buffDiscState(entity);
        if (!state.hasPendingExplosion()) {
            return;
        }
        float power = state.tickExplosion();
        if (power < 0.0F) {
            return;
        }
        DamageSource source = entity.damageSources().generic();
        entity.hurt(source, power * 3.0F);
        entity.setDeltaMovement(new Vec3(
                (entity.getRandom().nextDouble() - 0.5D) * power * 0.4D,
                power * 0.25D + 0.15D,
                (entity.getRandom().nextDouble() - 0.5D) * power * 0.4D));
        entity.hurtMarked = true;
        for (LivingEntity nearest : entity.level().getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(power))) {
            if (nearest == entity) {
                continue;
            }
            double distance = nearest.distanceTo(entity);
            if (distance >= power) {
                continue;
            }
            float falloff = (float) (1.0D - distance / power);
            nearest.hurt(source, power * 2.0F * falloff);
            Vec3 push = nearest.position()
                    .subtract(entity.position())
                    .normalize()
                    .scale(power * 0.25D * falloff);
            nearest.setDeltaMovement(push.x, power * 0.2D, push.z);
            nearest.hurtMarked = true;
        }
    }

    public static Optional<Holder.Reference<MobEffect>> effectHolder(
            ResourceLocation effectId) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(effectId);
    }

    // PATCH1 RecipeEventHandler: a disc crafted with a potion takes one random effect at the default level
    private static DiscEffectData rollRandomEffect(LivingEntity holder) {
        List<ResourceLocation> effects = EffectRegistry.getAllEffects();
        ResourceLocation effectId = effects.get(
                holder.getRandom().nextInt(effects.size()));
        int amplifier = EffectRegistry.getDefaultAmplifier(effectId);
        return DiscEffectData.single(
                effectId,
                amplifier,
                EffectRegistry.calculateDuration(effectId, amplifier));
    }

    @Override
    public void inventoryTick(
            ItemStack stack, Level level, Entity entity,
            int slotId, boolean isSelected) {
        if (level.isClientSide()
                || !(entity instanceof Player player)
                || getData(stack).filter(DiscEffectData::isRandom)
                        .isEmpty()) {
            return;
        }
        // PATCH1 rolled every crafted disc on its own (it stacked to 1); unrolled discs merge here, so one is rolled
        // and the rest go back unrolled, after the roll so that they cannot merge into this slot again
        ItemStack unrolled = stack.getCount() > 1
                ? stack.split(stack.getCount() - 1)
                : ItemStack.EMPTY;
        DiscEffectData rolled = rollRandomEffect(player);
        stack.set(WhitesnakeAddon.DISC_EFFECT.get(), rolled);
        ItemUtil.giveItemTo(player, unrolled, true);
        rolled.effect().ifPresent(effectId ->
                player.displayClientMessage(Component.translatable(
                                "message.rotp_whitesnake.random_disc",
                                effectDisplayName(effectId)),
                        true));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasEffect(stack) && !isMultiDisc(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            applyEffectTo(player, stack.copy());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(
                stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        Optional<DiscEffectData> data = getData(stack);
        if (data.isEmpty()
                || !data.get().hasEffect() && !data.get().isMulti()) {
            tooltip.add(Component.translatable(
                            "tooltip.rotp_whitesnake.disc.blank")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (data.get().isMulti()) {
            List<ResourceLocation> effects = data.get().effects();
            tooltip.add(Component.translatable(
                            "tooltip.rotp_whitesnake.disc.effect_count",
                            effects.size())
                    .withStyle(ChatFormatting.GRAY));
            int shown = Screen.hasShiftDown()
                    ? effects.size()
                    : Math.min(SHOWN_EFFECTS, effects.size());
            for (int i = 0; i < shown; i++) {
                tooltip.add(Component.literal("  - ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(effectDisplayName(effects.get(i))
                                .copy()
                                .withStyle(ChatFormatting.WHITE)));
            }
            if (shown < effects.size()) {
                tooltip.add(Component.translatable(
                                "tooltip.rotp_whitesnake.disc.more",
                                effects.size() - shown)
                        .withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable(
                                "tooltip.rotp_whitesnake.disc.shift_hint")
                        .withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        ResourceLocation effectId = data.get().effect().orElseThrow();
        if (DiscEffectData.RANDOM.equals(effectId)) {
            tooltip.add(Component.translatable(
                            "tooltip.rotp_whitesnake.disc.random")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (DiscEffectData.INSTANT_KILL.equals(effectId)) {
            tooltip.add(Component.translatable(
                            "tooltip.rotp_whitesnake.disc.instant_kill")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }
        if (DiscEffectData.EXPLOSION.equals(effectId)) {
            tooltip.add(Component.translatable(
                            "effect.rotp_whitesnake.explosion")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(
                            "tooltip.rotp_whitesnake.disc.explosion_delay")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        tooltip.add(effectDisplayName(effectId).copy()
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(
                        "tooltip.rotp_whitesnake.disc.level",
                        data.get().amplifier() + 1)
                .withStyle(ChatFormatting.GRAY));
        int duration = data.get().duration();
        tooltip.add(Component.translatable(
                        "tooltip.rotp_whitesnake.disc.duration",
                        duration < 0 ? "\u221e" : duration / 20 + "s")
                .withStyle(ChatFormatting.GRAY));
    }

    public static Component effectDisplayName(ResourceLocation effectId) {
        Optional<Holder.Reference<MobEffect>> effect =
                effectHolder(effectId);
        if (effect.isPresent()) {
            return Component.translatable(
                    effect.get().value().getDescriptionId());
        }
        if (DiscEffectData.EXPLOSION.equals(effectId)) {
            return Component.translatable(
                    "effect.rotp_whitesnake.explosion");
        }
        if (DiscEffectData.INSTANT_KILL.equals(effectId)) {
            return Component.translatable(
                    "effect.rotp_whitesnake.instant_kill");
        }
        return Component.literal(effectId.getPath());
    }
}
