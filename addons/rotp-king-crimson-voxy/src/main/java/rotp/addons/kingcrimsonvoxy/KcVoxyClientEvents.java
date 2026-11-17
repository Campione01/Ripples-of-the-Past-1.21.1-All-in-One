package rotp.addons.kingcrimsonvoxy;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class KcVoxyClientEvents {
    /** The /rotpkcvoxy commands: debug, probe and trace runs only. */
    static final boolean COMMANDS = KcVoxyProbe.ENABLED || KcVoxyTrace.ENABLED || Boolean.getBoolean("rotp.kcvoxy.debug");
    private static boolean announced;
    private static boolean told;

    private KcVoxyClientEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFrameStart(RenderFrameEvent.Pre event) {
        KcVoxyFar.onFrameStart();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            KcVoxyFar.onAfterSky(event);
        } else if (KcVoxyProbe.ENABLED && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            KcVoxyProbe.afterEntities(event.getCamera());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (told || minecraft.player == null) {
            return;
        }
        if (!announced) {
            announced = true;
            if (!KcVoxyShaderPatch.hasParser()) {
                KcVoxyFar.bindShaderParser();
            }
            KcVoxyShaderPatch.verify();
            if (!KcVoxyStatus.failed()) {
                LogUtils.getLogger().info("King Crimson Voxy far terrain: every anchor fits, the effect may run.");
            }
        }
        // Voxy compiles its programs as it starts in the world, so a shader that fails to compile is known a moment after the join.
        if (KcVoxyStatus.failed()) {
            told = true;
            minecraft.gui.getChat().addMessage(Component.translatable("chat.rotp_kingcrimson_voxy.mismatch", KcVoxyStatus.firstReason())
                    .withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        announced = false;
        told = false;
        KcVoxyFar.onLogout();
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (!COMMANDS) {
            return;
        }
        event.getDispatcher().register(Commands.literal("rotpkcvoxy")
                .then(Commands.literal("stage").then(Commands.argument("stage", IntegerArgumentType.integer(0, 3)).executes(context -> {
                    KcVoxyFar.stage = IntegerArgumentType.getInteger(context, "stage");
                    LogUtils.getLogger().info("KcVoxy: {}", KcVoxyFar.describe());
                    return 1;
                })))
                .then(Commands.literal("look").then(Commands.argument("parts", IntegerArgumentType.integer(0, KcVoxyShaderPatch.LOOK_ALL)).executes(context -> {
                    KcVoxyFar.look = IntegerArgumentType.getInteger(context, "parts");
                    LogUtils.getLogger().info("KcVoxy: look {}", KcVoxyFar.look);
                    return 1;
                })))
                .then(Commands.literal("tail").then(Commands.argument("share", FloatArgumentType.floatArg(0.0F, 0.6F)).executes(context -> {
                    KcVoxyTail.share = FloatArgumentType.getFloat(context, "share");
                    LogUtils.getLogger().info("KcVoxy: far tail {}", KcVoxyTail.share);
                    return 1;
                })))
                .then(Commands.literal("pieceboost").then(Commands.argument("blocks", FloatArgumentType.floatArg(0.0F, 200.0F)).executes(context -> {
                    KcVoxyFar.pieceBoost = FloatArgumentType.getFloat(context, "blocks");
                    LogUtils.getLogger().info("KcVoxy: piece boost {}", KcVoxyFar.pieceBoost);
                    return 1;
                })))
                .then(Commands.literal("splitlevel").then(Commands.argument("level", IntegerArgumentType.integer(0, 15)).executes(context -> {
                    KcVoxyFar.splitLevel = IntegerArgumentType.getInteger(context, "level");
                    LogUtils.getLogger().info("KcVoxy: split level {}", KcVoxyFar.splitLevel);
                    return 1;
                })))
                .then(Commands.literal("tracenormal").then(Commands.argument("frames", IntegerArgumentType.integer(1, 2400)).executes(context -> {
                    KcVoxyTrace.normalFrames = IntegerArgumentType.getInteger(context, "frames");
                    LogUtils.getLogger().info("KcVoxy: tracing {} ordinary frames", KcVoxyTrace.normalFrames);
                    return 1;
                })))
                .then(Commands.literal("margin").then(Commands.argument("degrees", FloatArgumentType.floatArg(0.0F, 60.0F)).executes(context -> {
                    KcVoxyFar.marginDegrees = FloatArgumentType.getFloat(context, "degrees");
                    LogUtils.getLogger().info("KcVoxy: margin {}", KcVoxyFar.marginDegrees);
                    return 1;
                })))
                .then(Commands.literal("freeze").then(Commands.argument("tick", FloatArgumentType.floatArg(-1.0F)).executes(context -> {
                    KcVoxyFar.frozenTick = FloatArgumentType.getFloat(context, "tick");
                    LogUtils.getLogger().info("KcVoxy: {}", KcVoxyFar.describe());
                    return 1;
                })))
                .then(Commands.literal("foveffect").then(Commands.argument("scale", FloatArgumentType.floatArg(0.0F, 1.0F)).executes(context -> {
                    Minecraft.getInstance().options.fovEffectScale().set((double) FloatArgumentType.getFloat(context, "scale"));
                    return 1;
                })))
                .then(Commands.literal("status").executes(context -> {
                    LogUtils.getLogger().info("KcVoxy: {}", KcVoxyFar.describe());
                    return 1;
                })));
    }
}
