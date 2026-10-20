package fermiumbooter.config;

import fermiumbooter.FermiumBooter;
import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

@Config(modid = FermiumBooter.MODID)
@MixinConfig(name = FermiumBooter.MODID)
public class FermiumBooterConfig {
	
	@Config.Comment("Prevents the mixin compatibility warning text from rendering on screen" + "\n" +
			"Errors and warnings will still be printed to the log")
	@Config.Name("Suppress Mixin Compatibility Warnings Render")
	public static boolean suppressMixinCompatibilityWarningsRender = false;
	
	@Config.Comment("Disables config based mixin compatibility checks" + "\n" +
			"Warning: this may cause undefined behavior in mods, you should not enable this if not absolutely required" + "\n" +
			"Do not report issues to any mods if you have this enabled unless you want to be laughed at")
	@Config.Name("Override Mixin Config Compatibility Checks")
	@Config.RequiresMcRestart
	public static boolean overrideMixinCompatibilityChecks = false;
	
	@Config.Comment("Mixin config json files to forcibly remove from FermiumBooter enqueue")
	@Config.Name("Forced Early Mixin Config Removals")
	@Config.RequiresMcRestart
	public static String[] forcedEarlyMixinConfigRemovals = {};
	
	@Config.Comment("Appends prior mixin exceptions to crash reports to help diagnose crashes")
	@Config.Name("Append General Mixin Exceptions To Crash Reports")
	@Config.RequiresMcRestart
	@MixinConfig.MixinToggle(mixinJson = "mixins.fermiumbooter.crashreport.json", defaultValue = true)
	public static boolean appendGeneralMixinExceptionsToCrashReports = true;

	@Config.Comment({
			"Used to detect mods that don't use @Mod or mcmod.info to define their modid",
			"If the jar scanner finds the given package, it will set the respective modid as being present. ",
			"It is not possible to filter for version or modname when using this system",
			"Pattern: S:path.of.package=modid"
	})
	@Config.Name("Jar Scanner Manual Overrides")
	public static Map<String, String> jarScannerOverrides = new HashMap<String, String>(){{
		put("git.jbredwards.jsonpaintings", "jsonpaintings");
		put("net.optifine", "optifine");
		put("gg.essential", "essential");
	}};

	@Config.Comment({
			"Adds all mod jars to the classpath before coremods finish loading instead of only right before mods are constructed",
			"This allows early mixins to target classes of any mod, not just of mods containing coremods, making late loading unnecessary.",
			"Forge still discovers and loads these mods the same way as before",
			"Debug toggle. Mods will rely on this being on, only disable to check if this is responsible for classloading-related crashes you experience."
	})
	@Config.Name("Add All Mods To Classpath Early")
	@Config.RequiresMcRestart
	public static boolean addAllModsToClasspathEarly = true;

	@Config.Comment({
			"Amount of threads used to scan mod jars for mods and @MixinConfig classes",
			"0 uses the amount of CPU cores (at most 8), 1 scans single threaded"
	})
	@Config.Name("Jar Scanner Threads")
	@Config.RangeInt(min = 0, max = 64)
	@Config.RequiresMcRestart
	public static int jarScannerThreads = 0;

	@Mod.EventBusSubscriber
	private static class ConfigSyncHandler {
		@SubscribeEvent
		public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
			if(event.getModID().equals(FermiumBooter.MODID))
				ConfigManager.sync(FermiumBooter.MODID, Config.Type.INSTANCE);
		}
	}
}