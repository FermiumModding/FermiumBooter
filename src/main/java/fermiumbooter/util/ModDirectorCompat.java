package fermiumbooter.util;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.launcher.FMLTweaker;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod Director downloads its mods in its tweaker, which LaunchWrapper only calls after Forge's coremod discovery
 * Our jar scanning usually happens during coremod discovery (whenever a coremod first asks for a modid), so on first startup the downloaded mods would be missing
 * If we're scanning during coremod discovery, run the Mod Director tweaker ourselves before that, so its mods are already in the mods folder
 */
public abstract class ModDirectorCompat {

	private static final String MOD_DIRECTOR_TWEAKER = "net.jan.moddirector.launchwrapper.ModDirectorTweaker";

	/**
	 * Only does something while FMLTweaker is being called, Forge's coremod discovery runs in there
	 * Later on LaunchWrapper is either iterating TweakClasses (changing it would throw a CME) or Mod Director is already bootstrapped, which can only happen once
	 */
	@SuppressWarnings("unchecked")
	public static void runModDirectorEarly() {
		List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
		List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
		if(tweaks == null || tweakClasses == null) return;
		if(tweaks.stream().noneMatch(t -> t instanceof FMLTweaker)) return;
		int index = tweakClasses.indexOf(MOD_DIRECTOR_TWEAKER);
		if(index < 0) return;

		FermiumJarScanner.LOGGER.log(Level.INFO, "Running Mod Director early so jar scanning can find the mods it installs.");
		try {
			//Same as LaunchWrapper does when loading a tweaker
			Launch.classLoader.addClassLoaderExclusion(MOD_DIRECTOR_TWEAKER.substring(0, MOD_DIRECTOR_TWEAKER.lastIndexOf('.')));
			ITweaker tweaker = (ITweaker) Class.forName(MOD_DIRECTOR_TWEAKER, true, Launch.classLoader).newInstance();
			//Constructing it bootstraps Mod Director, LaunchWrapper must not construct it again
			tweakClasses.remove(index);
			//Mod Director only passes these on to tweakers it injects, its own launch arguments are empty
			tweaker.acceptOptions(new ArrayList<>(), Launch.minecraftHome, Launch.assetsDir, null);
			tweaker.injectIntoClassLoader(Launch.classLoader);
		}
		catch(ReflectiveOperationException | LinkageError ex) {
			//If construction failed it's still on the list and runs late like before
			FermiumJarScanner.LOGGER.log(Level.ERROR, "Failed to run Mod Director early, mods it installs will be missing from jar scanning on first startup.", ex);
		}
	}
}
