package madoku.craft.java.hud;

/** Public client contract for HUD synchronization state. */
public final class HudClientAPIManager {
	private HudClientAPIManager() { }

	public static void reset() { HudPayloadManager.reset(); }
	public static void setServerTime(long day, int hour, int minute) { HudPayloadManager.setServerTime(day, hour, minute); }
	public static void setServerDifficulty(int difficulty) { HudPayloadManager.setServerDifficulty(difficulty); }
	public static void setServerSeason(String season) { HudPayloadManager.setServerSeason(season); }
	public static void setServerSeasonProgress(int seasonDay, int seasonLengthDays) { HudPayloadManager.setServerSeasonProgress(seasonDay, seasonLengthDays); }
	public static void setServerClimate(double temperature, double humidity) { HudPayloadManager.setServerClimate(temperature, humidity); }
	public static void setServerHunger(int current, int max) { HudPayloadManager.setServerHunger(current, max); }
}
