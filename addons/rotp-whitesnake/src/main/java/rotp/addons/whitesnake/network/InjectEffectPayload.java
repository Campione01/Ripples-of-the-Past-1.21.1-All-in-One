package rotp.addons.whitesnake.network;

import java.util.Objects;

import rotp.core.util.functions.ItemUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.buffdisc.DiscItem;
import rotp.addons.whitesnake.buffdisc.EffectRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * PATCH1 InjectEffectPacket: the effect picked on the inject screen turns one blank Buff disc into a disc of that
 * effect. PATCH1 also sent the amplifier the screen read from EffectRegistry; the server reads it there itself, and
 * it serves only the player whose Inject Effect action opened the screen.
 */
public record InjectEffectPayload(ResourceLocation effectId)
        implements CustomPacketPayload {
    public static final int MAX_ID_LENGTH = 256;
    public static final String ABILITY_NAME = "inject_effect";
    public static final Type<InjectEffectPayload> TYPE =
            new Type<>(WhitesnakeAddon.id("inject_effect"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            InjectEffectPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    InjectEffectPayload::write,
                    InjectEffectPayload::new);

    public InjectEffectPayload {
        Objects.requireNonNull(effectId);
        if (effectId.toString().length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Injected effect ID is too long");
        }
    }

    private InjectEffectPayload(RegistryFriendlyByteBuf buffer) {
        this(parse(buffer.readUtf(MAX_ID_LENGTH)));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(effectId.toString(), MAX_ID_LENGTH);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenInjectEffectPayload.TYPE,
                OpenInjectEffectPayload.STREAM_CODEC,
                OpenInjectEffectPayload::handle);
        registrar.playToServer(
                TYPE, STREAM_CODEC, InjectEffectPayload::handle);
    }

    private static void handle(
            InjectEffectPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                inject(player, payload.effectId());
            }
        });
    }

    private static void inject(
            ServerPlayer player, ResourceLocation effectId) {
        if (!WhitesnakeAddon.buffDiscState(player).consumeInjectPermit(
                        player.level().getGameTime())
                || !WhitesnakeAddon.isActiveWhitesnakeUser(
                        player, ABILITY_NAME)
                || !EffectRegistry.isEffectExists(effectId)) {
            return;
        }
        ItemStack blankDisc = findBlankDisc(player);
        if (blankDisc.isEmpty()) {
            return;
        }
        int amplifier = EffectRegistry.getDefaultAmplifier(effectId);
        ItemStack effectDisc = DiscItem.createEffect(
                effectId,
                amplifier,
                EffectRegistry.calculateDuration(effectId, amplifier));
        blankDisc.shrink(1);
        ItemUtil.giveItemTo(player, effectDisc, true);
    }

    // PATCH1 took a multi-effect disc for a blank one too (it has no EffectId); only a blank disc is taken here
    private static ItemStack findBlankDisc(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (DiscItem.isBlank(held)) {
                return held;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (DiscItem.isBlank(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ResourceLocation parse(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Invalid injected effect ID");
        }
        return id;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
