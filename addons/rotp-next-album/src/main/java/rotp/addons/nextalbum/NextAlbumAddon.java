package rotp.addons.nextalbum;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.healing.CrazyDiamondRestoreExtension;
import rotp.core.api.healing.CrazyDiamondRestoreExtensions;
import rotp.core.api.healing.LivingRestoreContext;
import rotp.core.api.healing.RestoreAugmentation;
import rotp.core.api.power.AbilityRefusals;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility;
import rotp.core.api.stand.StandStaminaModifiers;
import rotp.core.api.item.ItemHandFreePredicates;
import rotp.core.init.ModItems;

import rotp.addons.nextalbum.compat.AjaNecklaceCurios;
import rotp.addons.nextalbum.config.NextAlbumClientConfig;
import rotp.addons.nextalbum.config.NextAlbumCommonConfig;
import rotp.addons.nextalbum.effect.CigaretteMoodEffect;
import rotp.addons.nextalbum.effect.CollapseEffect;
import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.item.LuckPluckItem;
import rotp.addons.nextalbum.item.MistaSuitArmorItem;
import rotp.addons.nextalbum.item.MistaSuitArmorMaterial;
import rotp.addons.nextalbum.item.SpiritCigaretteItem;
import rotp.addons.nextalbum.network.CigaretteMouthPayload;
import rotp.addons.nextalbum.network.NecklaceBeamUsePayload;
import rotp.addons.nextalbum.network.ProsciuttoShadowPayload;
import rotp.addons.nextalbum.network.ResolveDownedPayload;
import rotp.addons.nextalbum.state.CigaretteFocus;
import rotp.addons.nextalbum.state.ResolveDowned;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NextAlbumIds.MOD_ID)
public final class NextAlbumAddon {
    public static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(
                    Registries.MOB_EFFECT, NextAlbumIds.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(NextAlbumIds.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(
                    Registries.PARTICLE_TYPE, NextAlbumIds.MOD_ID);

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(
                    Registries.SOUND_EVENT, NextAlbumIds.MOD_ID);

    // Next Album 0.1.13 InitSounds.PROSCIUTTO_DEAD, said as its user is downed.
    public static final DeferredHolder<SoundEvent, SoundEvent> PROSCIUTTO_DEAD =
            SOUND_EVENTS.register(
                    NextAlbumIds.PROSCIUTTO_DEAD_PATH,
                    SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            STAND_RESOLVE_AURA = PARTICLE_TYPES.register(
                    NextAlbumIds.STAND_RESOLVE_AURA_PATH,
                    () -> new SimpleParticleType(false));
    // Next Album 0.1.13 onomatopoeia words for footstep sounds.
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            SOUND_GO_ORANGE = PARTICLE_TYPES.register(
                    NextAlbumIds.SOUND_GO_ORANGE_PATH,
                    () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            SOUND_DO_ORANGE = PARTICLE_TYPES.register(
                    NextAlbumIds.SOUND_DO_ORANGE_PATH,
                    () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            SOUND_BA_ORANGE = PARTICLE_TYPES.register(
                    NextAlbumIds.SOUND_BA_ORANGE_PATH,
                    () -> new SimpleParticleType(true));

    public static final DeferredHolder<MobEffect, CollapseEffect> COLLAPSE =
            MOB_EFFECTS.register(
                    NextAlbumIds.COLLAPSE_PATH,
                    () -> new CollapseEffect(
                            MobEffectCategory.HARMFUL,
                            0x6F6F7A));
    public static final DeferredHolder<MobEffect, CigaretteMoodEffect>
            FOCUS = MOB_EFFECTS.register(
                    NextAlbumIds.FOCUS_PATH,
                    () -> new CigaretteMoodEffect(
                            MobEffectCategory.BENEFICIAL,
                            0xE8C36A));
    public static final DeferredHolder<MobEffect, CigaretteMoodEffect>
            LAX = MOB_EFFECTS.register(
                    NextAlbumIds.LAX_PATH,
                    () -> new CigaretteMoodEffect(
                            MobEffectCategory.HARMFUL,
                            0x6B7A8A));
    public static final DeferredHolder<Item, LuckPluckItem> LUCK_PLUCK =
            ITEMS.register(
                    NextAlbumIds.LUCK_PLUCK_PATH,
                    () -> new LuckPluckItem(
                            new Item.Properties()
                                    .rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, MistaSuitArmorItem>
            MISTA_SUIT_CLOTH = ITEMS.register(
                    NextAlbumIds.MISTA_SUIT_CLOTH_PATH,
                    () -> new MistaSuitArmorItem(
                            ArmorItem.Type.CHESTPLATE,
                            "geo/mista_suit_cloth.geo.json",
                            "textures/models/armor/mista_suit_cloth.png",
                            "geo/mista_suit_cloth_slim.geo.json",
                            "textures/models/armor/"
                                    + "mista_suit_cloth_slim.png",
                            true,
                            true,
                            false));
    public static final DeferredHolder<Item, MistaSuitArmorItem>
            MISTA_SUIT_PANTS = ITEMS.register(
                    NextAlbumIds.MISTA_SUIT_PANTS_PATH,
                    () -> new MistaSuitArmorItem(
                            ArmorItem.Type.LEGGINGS,
                            "geo/mista_suit_pants.geo.json",
                            "textures/models/armor/mista_suit_pants.png",
                            null,
                            null,
                            false,
                            false,
                            true));
    public static final DeferredHolder<Item, MistaSuitArmorItem>
            MISTA_SUIT_BOOTS = ITEMS.register(
                    NextAlbumIds.MISTA_SUIT_BOOTS_PATH,
                    () -> new MistaSuitArmorItem(
                            ArmorItem.Type.BOOTS,
                            "geo/mista_suit_boots.geo.json",
                            "textures/models/armor/mista_suit_boots.png",
                            null,
                            null,
                            false,
                            false,
                            true));
    public static final DeferredHolder<Item, AjaStoneNecklaceItem>
            AJA_STONE_NECKLACE = ITEMS.register(
                    NextAlbumIds.AJA_STONE_NECKLACE_PATH,
                    () -> new AjaStoneNecklaceItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .durability(
                                            ArmorItem.Type.HELMET
                                                    .getDurability(5))
                                    .rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, SpiritCigaretteItem>
            SPIRIT_CIGARETTE = ITEMS.register(
                    NextAlbumIds.SPIRIT_CIGARETTE_PATH,
                    () -> new SpiritCigaretteItem(
                            new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, CharacterSuitArmorItem>
            PESCI_SUIT_CLOTH = ITEMS.register(
                    NextAlbumIds.PESCI_SUIT_CLOTH_PATH,
                    () -> new CharacterSuitArmorItem(
                            CharacterSuitArmorItem.Suit.PESCI,
                            ArmorItem.Type.CHESTPLATE,
                            "geo/general_suit_cloth.geo.json",
                            "textures/models/armor/pesci_suit_cloth.png",
                            "geo/general_suit_cloth_slim.geo.json",
                            "textures/models/armor/pesci_suit_cloth_slim.png"));
    public static final DeferredHolder<Item, CharacterSuitArmorItem>
            PESCI_SUIT_PANTS = ITEMS.register(
                    NextAlbumIds.PESCI_SUIT_PANTS_PATH,
                    () -> new CharacterSuitArmorItem(
                            CharacterSuitArmorItem.Suit.PESCI,
                            ArmorItem.Type.LEGGINGS,
                            "geo/general_suit_pants.geo.json",
                            "textures/models/armor/pesci_suit_pants.png",
                            null,
                            null));
    public static final DeferredHolder<Item, CharacterSuitArmorItem>
            PESCI_SUIT_BOOTS = ITEMS.register(
                    NextAlbumIds.PESCI_SUIT_BOOTS_PATH,
                    () -> new CharacterSuitArmorItem(
                            CharacterSuitArmorItem.Suit.PESCI,
                            ArmorItem.Type.BOOTS,
                            "geo/general_suit_boots.geo.json",
                            "textures/models/armor/pesci_suit_boots.png",
                            null,
                            null));
    public static final DeferredHolder<Item, CharacterSuitArmorItem>
            PROSCIUTTO_SUIT_CLOTH = ITEMS.register(
                    NextAlbumIds.PROSCIUTTO_SUIT_CLOTH_PATH,
                    () -> new CharacterSuitArmorItem(
                            CharacterSuitArmorItem.Suit.PROSCIUTTO,
                            ArmorItem.Type.CHESTPLATE,
                            "geo/general_suit_cloth.geo.json",
                            "textures/models/armor/prosciutto_suit_cloth.png",
                            "geo/general_suit_cloth_slim.geo.json",
                            "textures/models/armor/prosciutto_suit_cloth_slim.png"));
    public static final DeferredHolder<Item, CharacterSuitArmorItem>
            PROSCIUTTO_SUIT_PANTS = ITEMS.register(
                    NextAlbumIds.PROSCIUTTO_SUIT_PANTS_PATH,
                    () -> new CharacterSuitArmorItem(
                            CharacterSuitArmorItem.Suit.PROSCIUTTO,
                            ArmorItem.Type.LEGGINGS,
                            "geo/general_suit_pants.geo.json",
                            "textures/models/armor/prosciutto_suit_pants.png",
                            null,
                            null));
    public static final DeferredHolder<Item, CharacterSuitArmorItem>
            PROSCIUTTO_SUIT_BOOTS = ITEMS.register(
                    NextAlbumIds.PROSCIUTTO_SUIT_BOOTS_PATH,
                    () -> new CharacterSuitArmorItem(
                            CharacterSuitArmorItem.Suit.PROSCIUTTO,
                            ArmorItem.Type.BOOTS,
                            "geo/general_suit_boots.geo.json",
                            "textures/models/armor/prosciutto_suit_boots.png",
                            null,
                            null));

    public NextAlbumAddon(IEventBus modEventBus, ModContainer modContainer) {
        requireCoreApi();
        ItemHandFreePredicates.register(
                NextAlbumIds.id(NextAlbumIds.LUCK_PLUCK_PATH),
                stack -> stack.getItem() instanceof LuckPluckItem luckPluck
                        && luckPluck.openFingers());
        MOB_EFFECTS.register(modEventBus);
        ITEMS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        modContainer.registerConfig(
                ModConfig.Type.CLIENT, NextAlbumClientConfig.SPEC);
        modContainer.registerConfig(
                ModConfig.Type.COMMON, NextAlbumCommonConfig.SPEC);
        registerResolveDownedHooks();
        if (FMLEnvironment.dist.isClient()) {
            rotp.addons.nextalbum.client.NextAlbumClientSetup
                    .registerConfigScreen(modContainer);
        }
        modEventBus.addListener(NecklaceBeamUsePayload::register);
        modEventBus.addListener(CigaretteMouthPayload::register);
        modEventBus.addListener(ProsciuttoShadowPayload::register);
        modEventBus.addListener(ResolveDownedPayload::register);
        modEventBus.addListener(this::addCreativeTabContents);
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 1.16 StandPowerCigaretteStaminaMixin on getStaminaTickGain.
        if (RotpAddonApi.supportsFeature(
                RotpAddonApi.FEATURE_STAND_STAMINA_MODIFIERS_V1)) {
            event.enqueueWork(() -> StandStaminaModifiers.registerTickGain(
                    NextAlbumIds.id("cigarette_focus"),
                    power -> CigaretteFocus.staminaRegenMultiplier(
                            power.getUser())));
        }
        if (AjaNecklaceCurios.isLoaded()) {
            event.enqueueWork(() -> AjaNecklaceCurios.register(
                    AJA_STONE_NECKLACE.get()));
        }
    }

    // Next Album 0.1.13 ResolveDownedUtil's mixins as core hooks: the
    // downed user's skill lock (ActionDownedMixin) and Crazy Diamond's heal
    // of a downed body (CrazyDiamondHealDownedMixin).
    private static void registerResolveDownedHooks() {
        AbilityRefusals.register(NextAlbumIds.id("resolve_downed"),
                (ability, context, user) -> ResolveDowned.refuse(ability, user));
        CrazyDiamondRestoreExtensions.register(
                NextAlbumIds.id("resolve_downed"),
                new CrazyDiamondRestoreExtension() {
                    @Override
                    public RestoreAugmentation afterLivingRestoreAttempt(
                            LivingRestoreContext context) {
                        if (context.crazyDiamond() == null) {
                            return RestoreAugmentation.none();
                        }
                        float healed = ResolveDowned.restoreByCrazyDiamond(
                                context.target(),
                                CrazyDHealAbility.crazyDRestorationSpeed(
                                        context.crazyDiamond()));
                        return healed > 0.0F
                                ? new RestoreAugmentation(true, false, healed)
                                : RestoreAugmentation.none();
                    }
                });
    }

    private void addCreativeTabContents(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModItems.MAIN_TAB.getKey()) {
            event.accept(LUCK_PLUCK.get());
            event.accept(MISTA_SUIT_CLOTH.get());
            event.accept(MISTA_SUIT_PANTS.get());
            event.accept(MISTA_SUIT_BOOTS.get());
            event.accept(AJA_STONE_NECKLACE.get());
            event.accept(SPIRIT_CIGARETTE.get());
            event.accept(PROSCIUTTO_SUIT_CLOTH.get());
            event.accept(PROSCIUTTO_SUIT_PANTS.get());
            event.accept(PROSCIUTTO_SUIT_BOOTS.get());
            event.accept(PESCI_SUIT_CLOTH.get());
            event.accept(PESCI_SUIT_PANTS.get());
            event.accept(PESCI_SUIT_BOOTS.get());
        }
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ITEM_HAND_FREE_PREDICATES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_SERVER_HELD_INPUT_CONTROL_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ABILITY_CONDITION_REFUSALS_V1)) {
            throw new IllegalStateException(
                    "RotP:NextAlbum requires ROTP addon ABI 1 with namespaced "
                            + "ability resources, stand visual contexts, "
                            + "item hand-free predicates, server held-input "
                            + "control, and ability condition refusals.");
        }
    }
}
