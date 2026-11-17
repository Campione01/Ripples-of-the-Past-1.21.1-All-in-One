package rotp.addons.spin.command;

import java.util.Collection;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.power.SpinData;
import rotp.core.command.commands.JojoCommandsCommand;

public final class SpinCommands {
    private static final String ENERGY = "commands.rotp_spin.spinenergy";
    private static final String LESSON = "commands.rotp_spin.spinlesson";
    private static final SimpleCommandExceptionType INVALID_RATIO =
            new SimpleCommandExceptionType(Component.translatable(ENERGY + ".set.ratio.invalid"));
    private static final DynamicCommandExceptionType NO_SPIN =
            new DynamicCommandExceptionType(name -> Component.translatable(ENERGY + ".failed.single.no_spin", name));
    private static final DynamicCommandExceptionType NO_GROUP_SPIN =
            new DynamicCommandExceptionType(count -> Component.translatable(ENERGY + ".failed.multiple.no_spin", count));

    private SpinCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spinenergy").requires(source -> source.hasPermission(2))
                .then(Commands.literal("set").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                .executes(ctx -> setEnergy(ctx, false))
                                .then(Commands.literal("points").executes(ctx -> setEnergy(ctx, false)))
                                .then(Commands.literal("ratio").executes(ctx -> setEnergy(ctx, true))))))
                .then(Commands.literal("get").then(Commands.argument("target", EntityArgument.player())
                        .executes(SpinCommands::getEnergy))));
        dispatcher.register(Commands.literal("spinlesson").requires(source -> source.hasPermission(2))
                .then(Commands.literal("set").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("lesson", IntegerArgumentType.integer(1, 5))
                                .executes(SpinCommands::setLesson))))
                .then(Commands.literal("get").then(Commands.argument("target", EntityArgument.player())
                        .executes(SpinCommands::getLesson))));
        JojoCommandsCommand.addCommand("spinenergy");
        JojoCommandsCommand.addCommand("spinlesson");
    }

    private static int getEnergy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var target = EntityArgument.getPlayer(context, "target");
        var data = required(target);
        float energy = SpinData.energy(target);
        float max = data.getCommandMaxEnergy(target);
        context.getSource().sendSuccess(() -> Component.translatable(ENERGY + ".get.success", target.getDisplayName(),
                String.format(Locale.ROOT, "%.1f", energy), String.format(Locale.ROOT, "%.1f", max),
                String.format(Locale.ROOT, "%.4f", max > 0 ? energy / max : 0)), false);
        return (int) energy;
    }

    private static int setEnergy(CommandContext<CommandSourceStack> context, boolean ratio) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(context, "targets");
        float amount = FloatArgumentType.getFloat(context, "amount");
        if (ratio && amount > 1.0F) throw INVALID_RATIO.create();
        int count = 0;
        for (ServerPlayer player : targets) {
            var data = SpinData.of(player).orElse(null);
            if (data == null) continue;
            data.setCommandEnergy(player, ratio ? amount * data.getCommandMaxEnergy(player) : amount);
            count++;
        }
        requireAny(targets, count);
        final int affected = count;
        String unit = ratio ? "ratio" : "points";
        context.getSource().sendSuccess(() -> targets.size() == 1
                ? Component.translatable(ENERGY + ".set.success.single." + unit, amount, targets.iterator().next().getDisplayName())
                : Component.translatable(ENERGY + ".set.success.multiple." + unit, amount, affected), true);
        return count;
    }

    private static int getLesson(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var target = EntityArgument.getPlayer(context, "target");
        var data = required(target);
        context.getSource().sendSuccess(() -> Component.translatable(LESSON + ".get.success", target.getDisplayName(),
                data.lesson(), Component.translatable("rotp_spin.lesson." + data.lesson()), data.ballHits(),
                SpinConfig.LESSON2_BALL_HITS.get(), data.hijacks(), SpinConfig.LESSON3_HIJACKS.get(), data.goldenHits()), false);
        return data.lesson();
    }

    private static int setLesson(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var targets = EntityArgument.getPlayers(context, "targets");
        int lesson = IntegerArgumentType.getInteger(context, "lesson");
        int count = 0;
        for (ServerPlayer player : targets) {
            var data = SpinData.of(player).orElse(null);
            if (data == null) continue;
            data.setLesson(player, lesson, true);
            count++;
        }
        requireAny(targets, count);
        final int affected = count;
        context.getSource().sendSuccess(() -> targets.size() == 1
                ? Component.translatable(LESSON + ".set.success.single", targets.iterator().next().getDisplayName(), lesson)
                : Component.translatable(LESSON + ".set.success.multiple", affected, lesson), true);
        return count;
    }

    private static SpinData required(ServerPlayer player) throws CommandSyntaxException {
        return SpinData.of(player).orElseThrow(() -> NO_SPIN.create(player.getDisplayName()));
    }

    private static void requireAny(Collection<ServerPlayer> targets, int count) throws CommandSyntaxException {
        if (count > 0) return;
        if (targets.size() == 1) throw NO_SPIN.create(targets.iterator().next().getDisplayName());
        throw NO_GROUP_SPIN.create(targets.size());
    }
}
