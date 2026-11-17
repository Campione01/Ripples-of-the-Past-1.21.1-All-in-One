package rotp.addons.ultimate_lifeform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.network.s2c.TrPowerDataPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.playerpower.PlayerPower;

/**
 * 1.16 Action.getCooldownTechnical, the first part of a cooldown(technical, additional), which a Stand-bearing mob
 * waited (+ 10) before its next pick. The only Ultimate Lifeform registration with a technical part is the Tentacles'
 * cooldown(80, 0) (040 UltimateLifeformActions.java:81, Action.getCooldownTechnical applied in creative too); every
 * other move is a plain cooldown(n) or none. The cooldown itself lives in the spec, not in Ability.cooldown.
 */
@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateCooldownTechnicalGameTests {
    private static final int DONOR_TENTACLES_TECHNICAL = 80;
    private static final String SLOW_COOLDOWN = "r772_ulf_cooldown_half";
    private static final String FAST_COOLDOWN = "r772_ulf_cooldown_triple";
    private static final AtomicBoolean RATES_REGISTERED = new AtomicBoolean();

    private UltimateCooldownTechnicalGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void onlyTheTentaclesReportATechnicalCooldownAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerPlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.fromString("a1c0e2f4-0b1d-4c2e-8f3a-5b6c7d8e9f21"), "UltimateCooldownTechnical"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(helper.getLevel().addFreshEntity(player), "setup: could not add the user");
        try {
            PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
            helper.assertTrue(power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()),
                    "setup: could not give the Ultimate Lifeform power");
            power.updateAvailableMoves();
            for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
                Ability ability = power.getAbility(spec.id);
                helper.assertTrue(ability != null, "setup: no ability " + spec.id);
                int expected = spec == UltimateLifeformSpec.TENTACLES ? DONOR_TENTACLES_TECHNICAL : 0;
                int reported = ability.getCooldownTechnical(power);
                if (reported != expected) {
                    wrong.add(spec.id + " reports technical " + reported + ", the donor's " + expected);
                }
            }
            UltimateLifeformSpec tentacles = UltimateLifeformSpec.TENTACLES;
            if (tentacles.cooldownTicks(false, -1) != DONOR_TENTACLES_TECHNICAL
                    || tentacles.cooldownTicks(true, -1) != DONOR_TENTACLES_TECHNICAL) {
                wrong.add("tentacles take " + tentacles.cooldownTicks(false, -1) + " ticks in survival and "
                        + tentacles.cooldownTicks(true, -1) + " in creative, the donor's cooldown(80, 0) both");
            }
        }
        finally {
            player.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 Ultimate Lifeform cooldown(technical, additional): "
                + String.join("; ", wrong));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void abilityCooldownsFollowPlayerTimeAndSyncWhileActive(GameTestHelper helper) {
        if (RATES_REGISTERED.compareAndSet(false, true)) {
            EntityTimeRates.register(UltimateLifeformAddon.id("r772_cooldown_gametest"), (entity, channel) -> {
                if (channel == EntityTimeRates.Channel.STAND_ACTIONS && entity instanceof ServerPlayer) {
                    if (entity.getTags().contains(SLOW_COOLDOWN)) {
                        return 0.5F;
                    }
                    if (entity.getTags().contains(FAST_COOLDOWN)) {
                        return 3.0F;
                    }
                }
                return EntityTimeRates.NO_RATE;
            });
        }
        List<Packet<?>> sent = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "UlfCooldownRate");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
                sent.add(packet);
            }

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
                sent.add(packet);
            }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        player.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(player);
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        try {
            helper.assertTrue(power.trySetPowerType(UltimateLifeformAddon.ULTIMATE_LIFEFORM.get()),
                    "setup: could not give the Ultimate Lifeform power");
            UltimateLifeformData data = power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
            data.setAbilityCooldown(UltimateLifeformSpec.TENTACLES.id, 7);
            data.setAbilityCooldown(UltimateLifeformSpec.SCALE_SHOT.id, 2);

            checkCooldownTick(helper, player, power, data, sent, "rate 1", 6, 1, 0);
            player.addTag(SLOW_COOLDOWN);
            checkCooldownTick(helper, player, power, data, sent, "first half-rate tick", 6, 1, 1);
            checkCooldownTick(helper, player, power, data, sent, "second half-rate tick", 5, 0, 0);
            player.removeTag(SLOW_COOLDOWN);
            checkCooldownTick(helper, player, power, data, sent, "back to rate 1 while active", 4, 0, 1);
            player.addTag(FAST_COOLDOWN);
            checkCooldownTick(helper, player, power, data, sent, "first triple-rate tick", 1, 0, 1);
            checkCooldownTick(helper, player, power, data, sent, "second triple-rate tick", 0, 0, 0);
            player.removeTag(FAST_COOLDOWN);
            checkCooldownTick(helper, player, power, data, sent, "back to rate 1 with an empty map", 0, 0, 0);
        }
        finally {
            power.setPowerType(null);
            player.discard();
            channel.finishAndReleaseAll();
        }
        helper.succeed();
    }

    private static void checkCooldownTick(GameTestHelper helper, ServerPlayer player, PlayerPower power,
            UltimateLifeformData data, List<Packet<?>> sent, String row, int tentacles, int scaleShot, int syncs) {
        sent.clear();
        int hamonTimer = data.getHamonCooldown();
        player.tickCount++;
        power.tick();
        String tentaclesId = UltimateLifeformSpec.TENTACLES.id;
        String scaleShotId = UltimateLifeformSpec.SCALE_SHOT.id;
        int actualTentacles = data.getAbilityCooldownTicks(tentaclesId);
        int actualScaleShot = data.getAbilityCooldownTicks(scaleShotId);
        helper.assertTrue(actualTentacles == tentacles && actualScaleShot == scaleShot,
                row + ": expected cooldowns " + tentacles + "/" + scaleShot + ", got "
                        + actualTentacles + "/" + actualScaleShot);
        helper.assertTrue(data.getAbilityCooldownTotalTicks(tentaclesId) == (tentacles > 0 ? 7 : 0)
                        && data.getAbilityCooldownTotalTicks(scaleShotId) == (scaleShot > 0 ? 2 : 0),
                row + ": cooldown totals should persist until expiry");
        helper.assertTrue(data.getHamonCooldown() == hamonTimer + 1, row + ": the Hamon timer must stay unscaled");
        long actualSyncs = sent.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof TrPowerDataPacket).count();
        helper.assertTrue(actualSyncs == syncs, row + ": expected " + syncs + " cooldown data syncs, got " + actualSyncs);
    }
}
