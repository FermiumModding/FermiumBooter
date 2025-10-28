package fermiumbooter.config;

import fermiumbooter.FermiumBooter;
import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
	@MixinConfig.MixinToggle(earlyMixin = "mixins.fermiumbooter.crashreport.json", defaultValue = true)
	public static boolean appendGeneralMixinExceptionsToCrashReports = true;
	
	@Mod.EventBusSubscriber(modid = FermiumBooter.MODID)
	private static class ConfigSyncHandler {
		
		@SubscribeEvent
		public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
			if(event.getModID().equals(FermiumBooter.MODID)) {
				ConfigManager.sync(FermiumBooter.MODID, Config.Type.INSTANCE);
			}
		}
	}
}