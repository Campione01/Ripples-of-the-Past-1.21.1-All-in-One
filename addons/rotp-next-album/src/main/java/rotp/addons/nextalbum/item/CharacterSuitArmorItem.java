package rotp.addons.nextalbum.item;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.client.render.CharacterSuitArmorRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Next Album 0.1.13 PesciSuitArmorItem and ProsciuttoSuitArmorItem: the
 * three-piece character suits on the general_suit models (material
 * CHARACTER_SUIT, never wearing out). A full Prosciutto set cycles its
 * look (default, no coat, stealth) by right-clicking a worn piece in the
 * inventory.
 */
public final class CharacterSuitArmorItem
        extends ArmorItem implements GeoItem {
    public enum Suit {
        PESCI(0x7EB3D6),
        PROSCIUTTO(0x694BDB);

        public final int themeColor;

        Suit(int themeColor) {
            this.themeColor = themeColor;
        }
    }

    public static final int FULL_SET = 3;
    public static final int DURABILITY_MULTIPLIER = 15;
    // 1.16 NextAlbumArmorMaterials.CHARACTER_SUIT.
    public static final Holder<ArmorMaterial> CHARACTER_SUIT =
            Holder.direct(new ArmorMaterial(
                    Map.of(
                            ArmorItem.Type.BOOTS, 2,
                            ArmorItem.Type.LEGGINGS, 5,
                            ArmorItem.Type.CHESTPLATE, 6,
                            ArmorItem.Type.HELMET, 2),
                    9,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(
                            NextAlbumIds.id("character_suit"))),
                    0.0F,
                    0.0F));
    public static final String MODE_KEY = "ProsciuttoSuitMode";
    public static final int MODE_NORMAL = 0;
    public static final int MODE_NON_OUTFIT = 1;
    public static final int MODE_STEALTH = 2;

    private final AnimatableInstanceCache cache =
            GeckoLibUtil.createInstanceCache(this);
    private final Suit suit;
    private final String modelPath;
    private final String texturePath;
    @Nullable
    private final String slimModelPath;
    @Nullable
    private final String slimTexturePath;
    private boolean renderingSlimVariant;
    private int renderingMode;

    public CharacterSuitArmorItem(
            Suit suit,
            ArmorItem.Type type,
            String modelPath,
            String texturePath,
            @Nullable String slimModelPath,
            @Nullable String slimTexturePath) {
        super(
                CHARACTER_SUIT,
                type,
                new Item.Properties()
                        .stacksTo(1)
                        .durability(type.getDurability(DURABILITY_MULTIPLIER))
                        .component(
                                DataComponents.UNBREAKABLE,
                                new Unbreakable(true)));
        this.suit = suit;
        this.modelPath = modelPath;
        this.texturePath = texturePath;
        this.slimModelPath = slimModelPath;
        this.slimTexturePath = slimTexturePath;
    }

    public Suit suit() {
        return suit;
    }

    public static boolean isSuit(ItemStack stack, Suit suit) {
        return !stack.isEmpty()
                && stack.getItem() instanceof CharacterSuitArmorItem item
                && item.suit == suit;
    }

    public static int countPieces(@Nullable LivingEntity entity, Suit suit) {
        if (entity == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : entity.getArmorSlots()) {
            if (isSuit(stack, suit)) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasFullSet(@Nullable LivingEntity entity, Suit suit) {
        return countPieces(entity, suit) >= FULL_SET;
    }

    public static int getMode(ItemStack stack) {
        return isSuit(stack, Suit.PROSCIUTTO)
                ? stack.getOrDefault(DataComponents.CUSTOM_DATA,
                                CustomData.EMPTY)
                        .getUnsafe().getInt(MODE_KEY)
                : MODE_NORMAL;
    }

    public static void setMode(ItemStack stack, int mode) {
        if (isSuit(stack, Suit.PROSCIUTTO)) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putInt(MODE_KEY, Math.floorMod(mode, 3)));
        }
    }

    /** Cycles the worn chest piece's look and gives it to every piece. */
    public static int cycleWornMode(Player player) {
        int next = (getMode(player.getItemBySlot(EquipmentSlot.CHEST)) + 1) % 3;
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            setMode(player.getItemBySlot(slot), next);
        }
        return next;
    }

    // 1.16 onArmorTick: the worn chest piece's look is copied to the
    // pants and shoes while the full set is worn.
    @Override
    public void inventoryTick(
            ItemStack stack, Level level, Entity entity,
            int slotId, boolean isSelected) {
        if (level.isClientSide()
                || suit != Suit.PROSCIUTTO
                || !(entity instanceof Player player)
                || player.getItemBySlot(EquipmentSlot.CHEST) != stack
                || !hasFullSet(player, Suit.PROSCIUTTO)) {
            return;
        }
        int chestMode = getMode(stack);
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack piece = player.getItemBySlot(slot);
            if (getMode(piece) != chestMode) {
                setMode(piece, chestMode);
            }
        }
    }

    public String modelPath() {
        return renderingSlimVariant && slimModelPath != null
                ? slimModelPath
                : modelPath;
    }

    public String texturePath() {
        String base = renderingSlimVariant && slimTexturePath != null
                ? slimTexturePath
                : texturePath;
        return renderingMode == MODE_NON_OUTFIT
                ? base.replace(".png", "_nonoutfit.png")
                : base;
    }

    public void setRenderingState(boolean slim, ItemStack stack) {
        renderingSlimVariant = slim && slimModelPath != null;
        renderingMode = getMode(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        Player viewer = net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                ? ClientViewer.player()
                : null;
        int worn = countPieces(viewer, suit);
        if (suit == Suit.PROSCIUTTO) {
            int mode = getMode(stack);
            tooltip.add(switch (mode) {
                case MODE_NON_OUTFIT -> Component.translatable(
                                "item.nextalbum.prosciutto_suit.appearance.nonoutfit")
                        .withStyle(ChatFormatting.GOLD);
                case MODE_STEALTH -> Component.translatable(
                                "item.nextalbum.prosciutto_suit.appearance.stealth")
                        .withStyle(ChatFormatting.GREEN);
                default -> Component.translatable(
                                "item.nextalbum.prosciutto_suit.appearance.normal")
                        .withStyle(ChatFormatting.DARK_PURPLE);
            });
            tooltip.add(effectLine("assassination_squad", worn));
            tooltip.add(effectLine("glorious_death", worn));
        } else {
            tooltip.add(effectLine("mammoni_resolve", worn));
            tooltip.add(effectLine("inherited_will", worn));
        }
        tooltip.add(Component.translatable(
                        stack.getDescriptionId() + ".reference_quote")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private Component effectLine(String effect, int worn) {
        Style theme = Style.EMPTY.withColor(suit.themeColor);
        return Component.translatable(
                "item.nextalbum.suit.effect.line",
                Component.translatable("item.nextalbum.suit.effect." + effect)
                        .withStyle(theme),
                Component.literal(String.valueOf(worn)).withStyle(theme),
                Component.literal(String.valueOf(FULL_SET)).withStyle(theme),
                Component.translatable(
                        "item.nextalbum.suit.effect." + effect + ".desc"));
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    private static final class ClientViewer {
        private static Player player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }

    @Override
    public void registerControllers(
            AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<CharacterSuitArmorItem> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?>
                    getGeoArmorRenderer(
                            @Nullable T livingEntity,
                            ItemStack itemStack,
                            @Nullable EquipmentSlot equipmentSlot,
                            @Nullable HumanoidModel<T> original) {
                if (renderer == null) {
                    renderer = new CharacterSuitArmorRenderer();
                }
                return renderer;
            }
        });
    }
}
