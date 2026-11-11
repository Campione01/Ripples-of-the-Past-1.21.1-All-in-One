package rotp.core.gametest;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.command.commands.JojoConfigCommand;
import rotp.core.config.editor.CommonConfigEditor;
import rotp.core.config.editor.ConfigEditResult;
import rotp.core.config.editor.ConfigEditResult.Status;
import rotp.core.config.editor.ConfigEditorPackets.Reply;
import rotp.core.config.editor.ConfigEntry;
import rotp.core.config.editor.StandConfigKeys;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

/** What the Stands page's game tests share: the Stands they edit, users of them, the pack's files, and putting all back. */
final class StandConfigTestKit {
	static final ResourceLocation SP = JojoMod.resLoc("star_platinum");
	static final ResourceLocation TW = JojoMod.resLoc("the_world");
	static final ResourceLocation ADDON = AddonStandStandIn.ID;

	private final GameTestHelper helper;
	private final List<ResourceLocation> edited = new ArrayList<>();
	private final List<Player> users = new ArrayList<>();
	private final List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();

	StandConfigTestKit(GameTestHelper helper, ResourceLocation... stands) {
		this.helper = helper;
		for (ResourceLocation stand : stands) {
			edited.add(stand);
			clean(stand);
		}
	}

	MinecraftServer server() {
		return helper.getLevel().getServer();
	}

	static StandType type(ResourceLocation id) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(id);
		if (type == null) {
			throw new IllegalStateException("no registered Stand " + id);
		}
		return type;
	}

	static String key(ResourceLocation stand, String field) {
		return StandConfigKeys.key(stand, field);
	}

	ConfigEditResult set(ResourceLocation stand, String field, JsonElement value) {
		ConfigEditResult result = CommonConfigEditor.set(key(stand, field), value);
		helper.assertTrue(result.status() == Status.OK, "setting " + key(stand, field) + " to " + value + " must be taken: " + result);
		return result;
	}

	static ConfigEntry entry(String key) {
		return CommonConfigEditor.entries().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
	}

	Path packRoot() {
		return JojoConfigCommand.packRoot(server());
	}

	Path file(ResourceLocation stand, String member) {
		return JojoConfigCommand.standFilePath(packRoot(), stand, member + ".json");
	}

	JsonObject read(ResourceLocation stand, String member) throws IOException {
		Path path = file(stand, member);
		return Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : null;
	}

	/** Every row of the Stand back at its default: its files leave the pack, its obtaining leaves bannedStands. */
	void clean(ResourceLocation stand) {
		ConfigEditResult reset = CommonConfigEditor.resetGroup(StandConfigKeys.group(stand));
		helper.assertTrue(reset.status() == Status.OK, "a Stand's group must reset: " + reset);
	}

	/** A survival user with this Stand, summoned when it has a body. */
	Player user(ResourceLocation stand, Vec3 at) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.moveTo(at.x, at.y, at.z, 0, 0);
		user.setNoGravity(true);
		helper.getLevel().addFreshEntity(user);
		users.add(user);
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = type(stand);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
				"the user must get " + stand);
		if (power.getPowerType().summon(user, power)) {
			StandEntity entity = power.getSummonedStandEntity();
			if (entity != null) {
				entity.summonLockTicks = 0;
				entity.gradualSummonWeaknessTicks = 0;
			}
		}
		power.setStamina(power.getMaxStamina());
		return user;
	}

	Cow cow(Vec3 at, float health) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		cow.setHealth(health);
		cow.moveTo(at.x, at.y, at.z, 0, 0);
		helper.getLevel().addFreshEntity(cow);
		spawned.add(cow);
		return cow;
	}

	Player target(Vec3 at, float health) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		player.getAbilities().invulnerable = false;
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		player.setHealth(health);
		player.moveTo(at.x, at.y, at.z, 0, 0);
		player.setNoGravity(true);
		helper.getLevel().addFreshEntity(player);
		users.add(player);
		return player;
	}

	void close() {
		for (Player user : users) {
			StandPower power = StandPower.get(user);
			if (power != null && power.hasPower() && power.isSummoned()) {
				power.getPowerType().forceUnsummon(user, power);
			}
			user.discard();
		}
		spawned.forEach(net.minecraft.world.entity.Entity::discard);
		edited.forEach(this::clean);
	}

	static ServerPlayer serverPlayer(GameTestHelper helper, String name) {
		var level = helper.getLevel();
		return new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
	}

	/** A connection that keeps the replies the server sends the player. */
	static final class Exchange {
		final List<Reply> replies = new ArrayList<>();
		final IPayloadContext context;

		Exchange(ServerPlayer player) {
			var listener = (ICommonPacketListener) Proxy.newProxyInstance(ICommonPacketListener.class.getClassLoader(),
					new Class<?>[] {ICommonPacketListener.class}, (proxy, method, args) -> switch (method.getName()) {
						case "hasChannel" -> Reply.TYPE.equals(args[0]);
						case "send" -> {
							replies.add((Reply) args[0]);
							yield null;
						}
						default -> throw new UnsupportedOperationException(method.getName());
					});
			context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
					new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> switch (method.getName()) {
						case "player" -> player;
						case "listener" -> listener;
						case "reply" -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
						default -> throw new UnsupportedOperationException(method.getName());
					});
		}
	}
}
