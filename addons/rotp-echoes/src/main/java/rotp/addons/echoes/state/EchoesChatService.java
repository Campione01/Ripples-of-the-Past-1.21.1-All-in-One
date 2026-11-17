package rotp.addons.echoes.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesSounds;
import rotp.addons.echoes.init.EchoesStands;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public final class EchoesChatService {
    private static final int ACTIVE_TALK_COOLDOWN_TICKS = 1200;

    // 016 EchoAutoActionHandler.COUNTER_LINES, EchoAIHandler.ACTIVE_TALKS and SoundCooldownHelper.SHIT_PHRASES; the lines
    // are in the lang files, so each player reads his own language
    private static final String COUNTER_KEY = EchoesChatReplies.KEY_PREFIX + "counter.";
    private static final int COUNTER_LINES = 6;
    private static final String IDLE_KEY = EchoesChatReplies.KEY_PREFIX + "idle.";
    private static final int IDLE_LINES = 12;
    private static final String SKILL_KEY = EchoesChatReplies.KEY_PREFIX + "skill.";
    private static final int SKILL_LINES = 12;

    // 092 kept nicknames for the server session (survive death)
    private static final Map<UUID, String> NICKNAMES =
            new ConcurrentHashMap<>();

    private EchoesChatService() {}

    // master SoundCooldownHelper.playSkillBGM and the author's 1.21.1 FreezeSoundHelper: the skill stinger and a voice
    // from the freeze pool on every freeze, self freeze and infuse (the author took out the master's 30 seconds between
    // voices)
    public static void playSkillSound(ServerPlayer player) {
        player.serverLevel().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                EchoesSounds.SKILL_BGM.get(),
                SoundSource.PLAYERS,
                0.8F,
                1.0F);
        player.serverLevel().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                EchoesSounds.FREEZE_VOICE.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        if (player.getRandom().nextFloat() < 0.3F) {
            player.displayClientMessage(
                    randomLine(player, SKILL_KEY, SKILL_LINES)
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
    }

    // 016's lines are signed ACT3 and that addon's one Stand was ACT3; the later builds have no other speaker
    public static void tickPassive(ServerPlayer player) {
        if (player.getAbilities().instabuild
                || !isAct3User(player)) {
            return;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        long gameTime = player.level().getGameTime();
        long lastTalk = state.lastActiveTalk();
        if (lastTalk != Long.MIN_VALUE
                && gameTime - lastTalk < ACTIVE_TALK_COOLDOWN_TICKS) {
            return;
        }
        if (player.getRandom().nextFloat() < 0.001F) {
            sendEchoLine(player, randomLine(player, IDLE_KEY, IDLE_LINES), false);
            state.setLastActiveTalk(gameTime);
        }
    }

    public static void showCounterLine(ServerPlayer player) {
        if (isAct3User(player)) {
            sendEchoLine(player, randomLine(player, COUNTER_KEY, COUNTER_LINES), false);
        }
    }

    public static boolean isAct3User(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        return power != null
                && power.getPowerType() == EchoesStands.ECHOES_ACT3.get();
    }

    // 092 EchoAIHandler.handleChat: ACT3 only, 5-tick cooldown
    public static boolean handleChat(
            ServerPlayer player, String message) {
        if (message == null
                || message.isBlank()
                || !isAct3User(player)) {
            return false;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        long gameTime = player.level().getGameTime();
        long lastCommand = state.lastCommandTime();
        if (lastCommand != Long.MIN_VALUE
                && gameTime >= lastCommand
                && gameTime - lastCommand
                        < EchoesChatReplies.COOLDOWN_TICKS) {
            return false;
        }
        IntUnaryOperator random =
                bound -> player.getRandom().nextInt(bound);
        String name = NICKNAMES.getOrDefault(
                player.getUUID(), player.getName().getString());
        EchoesChatReplies.Reply reply =
                EchoesChatReplies.reply(message, name, random);
        if (reply.nickname() != null) {
            NICKNAMES.put(player.getUUID(), reply.nickname());
        }
        // 092 picked its table by the client's language; the client now translates the line itself
        MutableComponent line = reply.key() != null
                ? Component.translatable(reply.key())
                : Component.literal(reply.literal());
        if (reply.frame() != null) {
            line = Component.translatable(reply.frame(), line, reply.name());
        }
        sendEchoLine(
                player,
                line.append(EchoesChatReplies.withSuffix("", random)),
                false);
        state.setLastCommandTime(gameTime);
        return true;
    }

    // 092's gray speaker, dark gray colon and white line; the speaker is ACT3's name in the player's language
    private static void sendEchoLine(
            ServerPlayer player, MutableComponent line, boolean actionBar) {
        player.displayClientMessage(
                Component.empty()
                        .append(Component.translatable("stand.rotp_echoes.echoes_act3")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(line.withStyle(ChatFormatting.WHITE)),
                actionBar);
    }

    private static MutableComponent randomLine(
            ServerPlayer player, String key, int lines) {
        return Component.translatable(key + (1 + player.getRandom().nextInt(lines)));
    }
}
