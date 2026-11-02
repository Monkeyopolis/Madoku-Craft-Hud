package madoku.craft.java.hud;

import madoku.craft.java.core.season.SeasonBiomeClimateAPIManager;
import madoku.craft.java.core.season.SeasonEnvironmentTransitionAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import madoku.craft.java.core.season.SeasonClientAPIManager;
import madoku.craft.java.core.ui.UiLayoutAPIManager;
import madoku.craft.java.core.ui.UiLayoutElement;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HudAPIManager {
	private static final Identifier WORLD_HUD_LAYOUT_ID = Identifier.fromNamespaceAndPath("madoku-craft", "world_hud");
	private static final int DEFAULT_WORLD_X = 4;
	private static final int DEFAULT_WORLD_Y = 4;
	private static final float WORLD_HUD_SCALE = 0.8F;
	private static final int COLOR = 0xFFFFFFFF;
	private static final WorldHudSnapshot EMPTY_SNAPSHOT = new WorldHudSnapshot(null, null, false, List.of(), 0, 0);
	private static WorldHudSnapshot worldHudSnapshot = EMPTY_SNAPSHOT;
	private static boolean initialized;
	private static final UiLayoutElement WORLD_HUD_LAYOUT = new UiLayoutElement() {
		@Override public Identifier id() { return WORLD_HUD_LAYOUT_ID; }
		@Override public int defaultX() { return DEFAULT_WORLD_X; }
		@Override public int defaultY() { return DEFAULT_WORLD_Y; }
		@Override public Bounds bounds(Minecraft client, int x, int y) { return worldHudBounds(client, x, y); }
		@Override public boolean isEnabled(Minecraft client) { return HudConfigManager.isEnabled(); }
		@Override public boolean isVisible(Minecraft client) { return !visibleWorldLines(client).isEmpty(); }
		@Override public void renderEditor(GuiGraphicsExtractor context, Minecraft client, int x, int y) {
			renderWorldHud(context, client, x, y, true);
		}
		@Override public String displayName() { return "World HUD"; }
	};

	private HudAPIManager() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		UiLayoutAPIManager.registerElement(WORLD_HUD_LAYOUT);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("madoku-craft", "madoku_hud"), HudAPIManager::renderWorldHud);
		ClientTickEvents.END_CLIENT_TICK.register(HudAPIManager::refreshWorldHudSnapshot);
	}

	public static void reset() {
		worldHudSnapshot = EMPTY_SNAPSHOT;
	}

	static void renderWorldHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (UiLayoutAPIManager.isEditMode()) return;
		UiLayoutAPIManager.Position position = UiLayoutAPIManager.position(WORLD_HUD_LAYOUT);
		renderWorldHud(context, client, position.x(), position.y(), false);
	}

	private static void renderWorldHud(GuiGraphicsExtractor context, Minecraft client, int x, int y, boolean editing) {
		if (!HudConfigManager.isEnabled() || !hasRenderableWorldHudPlayer(client, editing)) return;
		WorldHudSnapshot snapshot = getWorldHudSnapshot(client);
		for (int index = 0; index < snapshot.lines().size(); index++) {
			WorldLine line = snapshot.lines().get(index);
			drawWorldLine(context, client, x, y, line, index, snapshot.lineHeight());
		}
	}

	private static void drawWorldLine(GuiGraphicsExtractor context, Minecraft client, int originX, int originY, WorldLine line, int lineIndex, int lineHeight) {
		context.pose().pushMatrix();
		context.pose().translate(originX, originY + (lineHeight * lineIndex));
		context.pose().scale(WORLD_HUD_SCALE, WORLD_HUD_SCALE);
		context.text(client.font, line.prefix(), 0, 0, COLOR, true);
		context.text(client.font, line.value(), line.prefixWidth(), 0, line.valueColor(), true);
		context.pose().popMatrix();
	}

	private static List<WorldLine> visibleWorldLines(Minecraft client) {
		return getWorldHudSnapshot(client).lines();
	}

	private static void refreshWorldHudSnapshot(Minecraft client) {
		worldHudSnapshot = buildWorldHudSnapshot(client);
	}

	private static WorldHudSnapshot getWorldHudSnapshot(Minecraft client) {
		if (client == null) return EMPTY_SNAPSHOT;
		WorldHudSnapshot cached = worldHudSnapshot;
		boolean enabled = HudConfigManager.isEnabled();
		if (cached.level() == client.level && cached.player() == client.player && cached.enabled() == enabled) {
			return cached;
		}
		WorldHudSnapshot refreshed = buildWorldHudSnapshot(client);
		worldHudSnapshot = refreshed;
		return refreshed;
	}

	private static WorldHudSnapshot buildWorldHudSnapshot(Minecraft client) {
		if (client == null) return EMPTY_SNAPSHOT;
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		boolean enabled = HudConfigManager.isEnabled();
		if (level == null || player == null || !enabled) {
			return new WorldHudSnapshot(level, player, enabled, List.of(), 0, 0);
		}
		long day;
		int hour;
		int minute;
		if (HudPayloadManager.hasServerTime()) {
			day = HudPayloadManager.getServerDay();
			hour = HudPayloadManager.getServerHour();
			minute = HudPayloadManager.getServerMinute();
		} else {
			long dayTime = level.getOverworldClockTime();
			day = TimeAPIManager.getDay(dayTime);
			int totalMinutes = TimeAPIManager.getTotalMinutes(dayTime);
			hour = totalMinutes / 60;
			minute = totalMinutes % 60;
		}

		SeasonBiomeClimateAPIManager.Climate climate = HudPayloadManager.hasServerClimate()
			? new SeasonBiomeClimateAPIManager.Climate(
				HudPayloadManager.getServerTemperature(),
				HudPayloadManager.getServerHumidity())
			: SeasonClientAPIManager.resolveClimate(level.getBiome(player.blockPosition()).value());
		List<WorldLine> lines = new ArrayList<>(7);
		if (HudConfigManager.isEnabled("day")) lines.add(worldLine(client, "Day", Long.toString(displayDay(day)), COLOR));
		if (HudConfigManager.isEnabled("time")) {
			lines.add(worldLine(client, "Time", hour + ":" + twoDigits(minute), HudConfigManager.isColored("time") ? timeColor((hour * 60) + minute) : COLOR));
		}
		if (HudConfigManager.isEnabled("season") && HudPayloadManager.hasServerSeason()) {
			String season = getSeasonDisplayText();
			lines.add(worldLine(client, "Season", season, HudConfigManager.isColored("season")
				? seasonColor(season, HudPayloadManager.getServerSeasonDay(), HudPayloadManager.getServerSeasonLengthDays()) : COLOR));
		}
		if (HudConfigManager.isEnabled("temperature")) {
			lines.add(worldLine(client, "Temperature", formatClimate(climate.temperature()), HudConfigManager.isColored("temperature")
				? temperatureColor(climate.temperature()) : COLOR));
		}
		if (HudConfigManager.isEnabled("humidity")) {
			lines.add(worldLine(client, "Humidity", formatClimate(climate.humidity()), HudConfigManager.isColored("humidity")
				? humidityColor(climate.humidity()) : COLOR));
		}
		if (HudConfigManager.isEnabled("biome")) lines.add(worldLine(client, "Biome", getBiomeDisplayName(player, level), COLOR));
		if (HudConfigManager.isEnabled("difficulty") && HudPayloadManager.hasServerDifficulty()) {
			lines.add(worldLine(client, "Difficulty", getDifficultyDisplayText(), HudConfigManager.isColored("difficulty")
				? difficultyColor(HudPayloadManager.getServerDifficulty()) : COLOR));
		}
		List<WorldLine> immutableLines = List.copyOf(lines);
		int width = 0;
		for (WorldLine line : immutableLines) {
			width = Math.max(width, line.width());
		}
		int lineHeight = Math.round((client.font.lineHeight + 4) * WORLD_HUD_SCALE);
		return new WorldHudSnapshot(level, player, true, immutableLines, width, lineHeight);
	}

	private static UiLayoutElement.Bounds worldHudBounds(Minecraft client, int x, int y) {
		WorldHudSnapshot snapshot = getWorldHudSnapshot(client);
		if (client == null || snapshot.lines().isEmpty()) return new UiLayoutElement.Bounds(x, y, 0, 0);
		return new UiLayoutElement.Bounds(
			x,
			y,
			Math.max(1, Math.round(snapshot.width() * WORLD_HUD_SCALE)),
			Math.max(1, snapshot.lineHeight() * snapshot.lines().size())
		);
	}

	private static WorldLine worldLine(Minecraft client, String label, String value, int valueColor) {
		String prefix = label + ": ";
		return new WorldLine(prefix, value, valueColor, client.font.width(prefix), client.font.width(prefix + value));
	}

	private static boolean hasRenderableWorldHudPlayer(Minecraft client, boolean editing) {
		return client != null && client.level != null && client.player != null
			&& (editing || (client.gui != null && !client.gui.hud.isHidden()))
			&& !client.player.isSpectator();
	}

	private static String formatClimate(double value) {
		return Integer.toString((int) Math.round(value));
	}

	private static int timeColor(int totalMinutes) {
		int minutes = Math.floorMod(totalMinutes, 24 * 60);
		if (minutes < 1 * 60) return 0xFF243B80;
		if (minutes < 5 * 60) return interpolateColor(0xFF243B80, 0xFFFFF2A6, (minutes - 1 * 60) / (4.0D * 60.0D));
		if (minutes < 7 * 60) return 0xFFFFF2A6;
		if (minutes < 11 * 60) return interpolateColor(0xFFFFF2A6, 0xFFFFB347, (minutes - 7 * 60) / (4.0D * 60.0D));
		if (minutes < 13 * 60) return 0xFFFFB347;
		if (minutes < 17 * 60) return interpolateColor(0xFFFFB347, 0xFF8EC8FF, (minutes - 13 * 60) / (4.0D * 60.0D));
		if (minutes < 19 * 60) return 0xFF8EC8FF;
	if (minutes < 23 * 60) return interpolateColor(0xFF8EC8FF, 0xFF243B80, (minutes - 19 * 60) / (4.0D * 60.0D));
		return 0xFF243B80;
	}

	private static int seasonColor(String season, int seasonDay, int seasonLengthDays) {
		String current = season == null ? "" : season.toLowerCase(Locale.ROOT);
		int currentColor = seasonPaletteColor(current);
		int nextColor = seasonPaletteColor(nextSeason(current));
		if (currentColor == COLOR || nextColor == COLOR) {
			return currentColor;
		}
		return interpolateColor(
			currentColor,
			nextColor,
			SeasonEnvironmentTransitionAPIManager.resolveSeasonalTransitionProgress(seasonDay, seasonLengthDays));
	}

	private static int seasonPaletteColor(String season) {
		return switch (season) {
			case "spring" -> 0xFFFF9ECF;
			case "summer" -> 0xFFFFD34E;
			case "fall" -> 0xFFFF7043;
			case "winter" -> 0xFFB8E3FF;
			default -> COLOR;
		};
	}

	private static String nextSeason(String season) {
		return switch (season) {
			case "spring" -> "summer";
			case "summer" -> "fall";
			case "fall" -> "winter";
			case "winter" -> "spring";
			default -> "";
		};
	}

	private static int temperatureColor(double value) {
		return climateColor(value, 0xFF5AA9FF, 0xFF55C878, 0xFFFF5A5A);
	}

	private static int humidityColor(double value) {
		return climateColor(value, 0xFFFF5A5A, 0xFF55C878, 0xFF5AA9FF);
	}

	private static int climateColor(double value, int lowColor, int peakColor, int highColor) {
		double clamped = Math.max(0.0D, Math.min(100.0D, value));
		if (clamped <= 5.0D) return lowColor;
		if (clamped < 45.0D) return interpolateColor(lowColor, peakColor, (clamped - 5.0D) / 40.0D);
		if (clamped <= 55.0D) return peakColor;
		if (clamped < 95.0D) return interpolateColor(peakColor, highColor, (clamped - 55.0D) / 40.0D);
		return highColor;
	}

	private static int difficultyColor(int value) {
		if (value <= 5) return interpolateColor(0xFF55C878, 0xFFFFD34E, (value - 3.0D) / 2.0D);
		return interpolateColor(0xFFFFD34E, 0xFFFF5A5A, (value - 5.0D) / 3.0D);
	}

	private static int interpolateColor(int first, int second, double progress) {
		double clamped = Math.max(0.0D, Math.min(1.0D, progress));
		double smooth = clamped * clamped * (3.0D - (2.0D * clamped));
		int alpha = interpolateChannel((first >>> 24) & 0xFF, (second >>> 24) & 0xFF, smooth);
		int red = interpolateChannel((first >>> 16) & 0xFF, (second >>> 16) & 0xFF, smooth);
		int green = interpolateChannel((first >>> 8) & 0xFF, (second >>> 8) & 0xFF, smooth);
		int blue = interpolateChannel(first & 0xFF, second & 0xFF, smooth);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static int interpolateChannel(int first, int second, double progress) {
		return (int) Math.round(first + ((second - first) * progress));
	}

	private static String twoDigits(int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}

	private static String getBiomeDisplayName(LocalPlayer player, ClientLevel level) {
		return level.getBiome(player.blockPosition())
			.unwrapKey()
			.map(key -> biomeIdentifierToName(key.identifier()))
			.orElse("Unknown");
	}

	private static String biomeIdentifierToName(Identifier biomeIdentifier) {
		String normalized = biomeIdentifier.getPath().replace('_', ' ').replace('/', ' ');
		String[] words = normalized.split(" ");
		StringBuilder builder = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) {
				builder.append(word.substring(1));
			}
		}
		return builder.isEmpty() ? "Unknown" : builder.toString();
	}

	private static long displayDay(long rawDay) {
		return Math.max(0L, rawDay);
	}

	private static String getDifficultyDisplayText() {
		return Integer.toString(Math.max(1, HudPayloadManager.getServerDifficulty()));
	}

	private static String getSeasonDisplayText() {
		if (!HudPayloadManager.hasServerSeason()) {
			return "Unknown";
		}
		return capitalizeWord(HudPayloadManager.getServerSeason());
	}

	private static String capitalizeWord(String value) {
		if (value == null || value.isBlank()) {
			return "Unknown";
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
	}

	private record WorldLine(String prefix, String value, int valueColor, int prefixWidth, int width) { }

	private record WorldHudSnapshot(
		ClientLevel level,
		LocalPlayer player,
		boolean enabled,
		List<WorldLine> lines,
		int width,
		int lineHeight
	) { }

}
