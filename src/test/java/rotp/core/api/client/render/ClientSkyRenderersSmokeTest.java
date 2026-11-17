package rotp.core.api.client.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.resources.ResourceLocation;

public final class ClientSkyRenderersSmokeTest {
	private ClientSkyRenderersSmokeTest() {}

	public static void run() {
		ClientSkyRenderers.resetForTests();
		// Named so that owner order, which decides this tie, is the order they are asked in.
		ResourceLocation failed = id("a_failed");
		ResourceLocation inactive = id("b_inactive");
		ResourceLocation active = id("c_active");
		ResourceLocation ignored = id("d_ignored");
		AtomicInteger ignoredCalls = new AtomicInteger();
		AtomicReference<ClientSkyRenderContext> rendered =
				new AtomicReference<>();
		ClientSkyRenderContext context = new ClientSkyRenderContext(
				null, null, null, 0.5F, null, true, () -> {});

		ClientSkyRenderers.register(failed, query -> {
			throw new IllegalStateException("expected provider failure");
		});
		ClientSkyRenderers.register(inactive, query -> null);
		ClientSkyRenderers.register(active, query ->
				new ClientSkyRenderer() {
					@Override
					public void render(ClientSkyRenderContext renderContext) {
						rendered.set(renderContext);
					}

					@Override
					public boolean suppressVanillaClouds() {
						return true;
					}
				});
		ClientSkyRenderers.register(ignored, query -> {
			ignoredCalls.incrementAndGet();
			return renderContext -> {};
		});

		check(ClientSkyRenderers.renderSky(context),
				"successful sky replacement did not claim the render");
		check(rendered.get() == context,
				"sky renderer did not receive the render context");
		check(ClientSkyRenderers.suppressesClouds(null),
				"active sky renderer did not suppress vanilla clouds");
		check(ignoredCalls.get() == 0,
				"provider after the first active renderer was queried");
		check(ClientSkyRenderers.registeredOwners().equals(
				List.of(failed, inactive, active, ignored)),
				"sky renderer registration order changed");
		expectIllegalState(() -> ClientSkyRenderers.register(
				active, query -> null));

		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(id("render_failure"), query ->
				renderContext -> {
					throw new IllegalStateException(
							"expected render failure");
				});
		check(!ClientSkyRenderers.renderSky(context),
				"failed sky replacement suppressed the vanilla sky");

		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(id("cloud_failure"), query ->
				new ClientSkyRenderer() {
					@Override
					public void render(ClientSkyRenderContext renderContext) {}

					@Override
					public boolean suppressVanillaClouds() {
						throw new IllegalStateException(
								"expected cloud failure");
					}
				});
		check(!ClientSkyRenderers.suppressesClouds(null),
				"failed cloud selection did not fail open");

		ClientSkyRenderers.resetForTests();
		check(!ClientSkyRenderers.renderSky(context),
				"empty sky registry suppressed the vanilla sky");
		check(!ClientSkyRenderers.suppressesClouds(null),
				"empty sky registry suppressed vanilla clouds");
		verifyPriorityOrder();
		verifyMixinContract();
	}

	private static void verifyPriorityOrder() {
		ResourceLocation low = id("a_low");
		ResourceLocation high = id("z_high");
		ClientSkyRenderContext context = new ClientSkyRenderContext(
				null, null, null, 0.5F, null, true, () -> {});
		AtomicReference<ResourceLocation> drawn = new AtomicReference<>();

		// Whichever mod loads first: the old register stays at the default.
		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(low, query -> drawing(low, drawn, false));
		ClientSkyRenderers.register(high, 10, query -> drawing(high, drawn, true));
		expectWinner(context, drawn, high, true,
				"a higher priority registered last did not win");
		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(high, 10, query -> drawing(high, drawn, true));
		ClientSkyRenderers.register(low, query -> drawing(low, drawn, false));
		expectWinner(context, drawn, high, true,
				"a higher priority registered first did not win");
		check(ClientSkyRenderers.resolutionOrder().equals(List.of(high, low)),
				"resolution order is not by descending priority");
		check(ClientSkyRenderers.registeredOwners().equals(List.of(high, low)),
				"registered owners stopped reporting registration order");

		// The old register is the default exactly: a tie with an explicit 0, and above -1.
		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(id("b_old"), query -> drawing(id("b_old"), drawn, false));
		ClientSkyRenderers.register(id("a_explicit"), ClientSkyRenderers.DEFAULT_PRIORITY,
				query -> drawing(id("a_explicit"), drawn, false));
		ClientSkyRenderers.register(id("0_below"), -1, query -> drawing(id("0_below"), drawn, false));
		check(ClientSkyRenderers.DEFAULT_PRIORITY == 0, "the default priority moved");
		check(ClientSkyRenderers.resolutionOrder().equals(
				List.of(id("a_explicit"), id("b_old"), id("0_below"))),
				"the old register is not the default priority");

		// A tie goes by owner, as in DirectionalGravityApi, not by who came first.
		ClientSkyRenderers.resetForTests();
		ResourceLocation first = id("tie_b");
		ResourceLocation second = id("tie_a");
		ClientSkyRenderers.register(first, 5, query -> drawing(first, drawn, false));
		ClientSkyRenderers.register(second, 5, query -> drawing(second, drawn, false));
		expectWinner(context, drawn, second, false,
				"a tie was not decided by owner id");

		// An inactive or failing leader hands over; a later provider is not asked once one answers.
		ClientSkyRenderers.resetForTests();
		AtomicInteger belowWinner = new AtomicInteger();
		ResourceLocation winner = id("winner");
		ClientSkyRenderers.register(id("top_inactive"), 30, query -> null);
		ClientSkyRenderers.register(id("next_failing"), 20, query -> {
			throw new IllegalStateException("expected provider failure");
		});
		ClientSkyRenderers.register(winner, 10, query -> drawing(winner, drawn, false));
		ClientSkyRenderers.register(id("below"), 0, query -> {
			belowWinner.incrementAndGet();
			return drawing(id("below"), drawn, true);
		});
		expectWinner(context, drawn, winner, false,
				"an inactive or failing leader did not hand over by priority");
		check(belowWinner.get() == 0, "a provider below the winner was asked");

		// The owner's rule, with the ids in play: the erase's background is the highest there is, but for
		// Return to Zero's cosmos, which is the one exception. Whichever registers first.
		ResourceLocation erase = ResourceLocation.fromNamespaceAndPath(
				"rotp_kingcrimson", "time_erase_sky");
		ResourceLocation cosmic = ResourceLocation.fromNamespaceAndPath(
				"geraddon", "rtz_cosmic_sky");
		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(cosmic, ClientSkyRenderers.HIGHEST_PRIORITY,
				query -> drawing(cosmic, drawn, true));
		ClientSkyRenderers.register(erase, ClientSkyRenderers.HIGHEST_PRIORITY - 1,
				query -> drawing(erase, drawn, true));
		expectWinner(context, drawn, cosmic, true,
				"Return to Zero's cosmos did not win over the erase");
		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(erase, ClientSkyRenderers.HIGHEST_PRIORITY - 1,
				query -> drawing(erase, drawn, true));
		ClientSkyRenderers.register(cosmic, ClientSkyRenderers.HIGHEST_PRIORITY,
				query -> drawing(cosmic, drawn, true));
		expectWinner(context, drawn, cosmic, true,
				"the order of registration decided between the erase and the cosmos");
		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(cosmic, ClientSkyRenderers.HIGHEST_PRIORITY,
				query -> null);
		ClientSkyRenderers.register(erase, ClientSkyRenderers.HIGHEST_PRIORITY - 1,
				query -> drawing(erase, drawn, true));
		ClientSkyRenderers.register(id("anything_else"), Integer.MAX_VALUE - 2,
				query -> drawing(id("anything_else"), drawn, false));
		expectWinner(context, drawn, erase, true,
				"with no cosmos the erase did not outrank every other sky");
		check(ClientSkyRenderers.HIGHEST_PRIORITY == Integer.MAX_VALUE,
				"something can outrank the highest priority");

		// A second registration of an owner is refused at any priority, and changes nothing.
		ClientSkyRenderers.resetForTests();
		ClientSkyRenderers.register(low, 1, query -> drawing(low, drawn, false));
		expectIllegalState(() -> ClientSkyRenderers.register(
				low, 99, query -> drawing(low, drawn, true)));
		expectWinner(context, drawn, low, false,
				"a refused registration replaced the provider");
		ClientSkyRenderers.resetForTests();
	}

	private static ClientSkyRenderer drawing(
			ResourceLocation owner,
			AtomicReference<ResourceLocation> drawn,
			boolean suppressClouds) {
		return new ClientSkyRenderer() {
			@Override
			public void render(ClientSkyRenderContext renderContext) {
				drawn.set(owner);
			}

			@Override
			public boolean suppressVanillaClouds() {
				return suppressClouds;
			}
		};
	}

	private static void expectWinner(
			ClientSkyRenderContext context,
			AtomicReference<ResourceLocation> drawn,
			ResourceLocation winner,
			boolean suppressesClouds,
			String message) {
		drawn.set(null);
		check(ClientSkyRenderers.renderSky(context) && winner.equals(drawn.get()),
				message + ": drew " + drawn.get());
		check(ClientSkyRenderers.suppressesClouds(null) == suppressesClouds,
				message + ": cloud suppression did not follow the winner");
	}

	private static void verifyMixinContract() {
		String source = source(
				"src/main/java/rotp/core/mixin/"
						+ "client/render/"
						+ "LevelRendererSkyPresentationMixin.java");
		check(source.contains("@Shadow private ClientLevel level;"),
				"sky hooks must use LevelRenderer's current level");
		check(source.contains("ClientSkyRenderers.renderSky("),
				"sky replacement hook is missing");
		check(source.contains("ClientSkyRenderers.suppressesClouds("),
				"cloud suppression hook is missing");
		check(source.contains("cancellable = true"),
				"sky replacement hooks must be cancellable");
		check(source.contains("ClientSkyPresentationProviders.timeOfDay("),
				"existing sky time-of-day wrapper was removed");
		for (String hook : List.of(
				"jojo_ripples$renderCustomSky",
				"jojo_ripples$suppressVanillaClouds")) {
			String annotation = annotationOf(source, hook);
			check(annotation.contains("at = @At(\"HEAD\")"),
					hook + " must stay at HEAD, before DimensionSpecialEffects");
			check(annotation.contains("cancellable = true"),
					hook + " must stay cancellable");
			check(annotation.contains("order = -1"),
					hook + " must apply before every other HEAD callback");
		}
		check(source.contains("@Mixin(LevelRenderer.class)"),
				"a raised mixin priority applies later and so runs after other HEAD cancels");
	}

	private static String annotationOf(String source, String handler) {
		int end = source.indexOf(handler);
		check(end >= 0, "hook is missing: " + handler);
		int start = source.lastIndexOf("@Inject(", end);
		check(start >= 0, "hook has no injection: " + handler);
		return source.substring(start, end);
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException exception) {
			throw new AssertionError("Could not read " + path, exception);
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
		}
		catch (IllegalStateException expected) {
			return;
		}
		throw new AssertionError(
				"duplicate sky renderer provider was accepted");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
