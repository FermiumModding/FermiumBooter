package fermiumbooter.util;

import fermiumbooter.FermiumPlugin;
import fermiumbooter.FermiumRegistryAPI;
import fermiumbooter.config.FermiumBooterConfig;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scans for modids and @MixinConfig annotated classes.
 * Scans both mcmod.info and @Mod annotations to find modids, since some mods only have one or the other.
 * Some also have neither or set it improperly, so they have manual config compatibility.
 * Also handles searching for and parsing @MixinConfig handling using ASM (to avoid classloading issues)
 */
public abstract class FermiumJarScanner {

	static final Logger LOGGER = LogManager.getLogger("FermiumJarScanner");

	static final Map<String, ModInfo> presentMods = new HashMap<>();
	@Deprecated @SuppressWarnings({"DeprecatedIsStillUsed", "MismatchedQueryAndUpdateOfCollection"})
	private static final Set<String> earlyModIDs = new HashSet<>();
	private static final Set<MixinConfigInfo> mixinToggles = new HashSet<>();
	private static int warningCount = 0;
	
	public static boolean isModPresent(String modID) {
		if(modID == null || modID.isEmpty()) return false;
		handleCaching();
		return presentMods.containsKey(modID);
	}

	public static boolean isModPresent(String modID, String modName, String versionRange) {
		return isModPresent(modID)
				&& (modName == null || isModNamed(modID, modName))
				&& (versionRange == null || isModVersionInRange(modID, versionRange));
	}

	public static boolean isModVersionInRange(String modId, String range) {
		if(modId == null || range == null) return false;
		handleCaching();
		ModInfo modInfo = presentMods.get(modId);
		if(modInfo == null || modInfo.version == null) return false;
		if(range.equals(modInfo.version.toString())) return true; // if targeting an exact version (or a very weirdly named one)
		try {
			VersionRange versionRange = VersionRange.createFromVersionSpec(range);
			return versionRange.containsVersion(modInfo.version);
		} catch (InvalidVersionSpecificationException e){
			return false;
		}
	}

	public static boolean isModNamed(String modId, String name) {
		if(modId == null || name == null) return false;
		handleCaching();
		ModInfo modInfo = presentMods.get(modId);
		if(modInfo == null || modInfo.modName == null) return false;
		return modInfo.modName.equals(name);
	}
	
	public static void handleCaching() {
		if(!presentMods.isEmpty() || !mixinToggles.isEmpty()) return;

		//Other coremods can trigger caching before our injectData, jars still on the reparseable list would be skipped as known libraries
		ClasspathModHandler.fixIndevForceLoadAsModDependencies();

		LOGGER.log(Level.INFO, "FermiumJarScanner beginning jar searching.");
		startJarSearching();
		LOGGER.log(Level.INFO, "FermiumJarScanner finished jar searching, found {} ModIDs.", presentMods.size());
		
		LOGGER.log(Level.INFO, "FermiumMixinConfig beginning MixinConfig parsing.");
		for(MixinConfigInfo mixinConfig : mixinToggles)
			applyMixinConfig(mixinConfig);

		LOGGER.log(Level.INFO, "FermiumMixinConfig finished MixinConfig parsing, parsed {} config options with {} warnings", mixinToggles.size(), warningCount);
	}

	public static int getWarningCount() {
		return warningCount;
	}
	
	public static void clearCaches() {
		ClasspathModHandler.clearCaches();
		mixinToggles.clear();
		modConfigMap.clear();
	}
	
	//Not the most efficient implementation possible, but only takes around 2-3 seconds or so total even in relatively large packs
	private static void startJarSearching() {
		long time = System.currentTimeMillis();

		//Technically shows up as modids but isn't found through normal methods, just add manually
		addMod("minecraft", "1.12.2", "Minecraft");
		addMod("mcp", "9.42", "Minecraft Coder Pack");
		addMod("FML", null, "Forge Mod Loader");
		addMod("forge", null, "Minecraft Forge");

		Map<String, String> manualOverrides = new HashMap<>();
		if(FermiumPlugin.CONFIG.hasCategory("general.jar scanner manual overrides")) {
			ConfigCategory cat = FermiumPlugin.CONFIG.getCategory("general.jar scanner manual overrides");
			if (cat != null)
				for (Map.Entry<String, Property> entry : cat.getValues().entrySet())
					manualOverrides.put(entry.getKey(), entry.getValue().getString());
		} else
			manualOverrides.putAll(FermiumBooterConfig.jarScannerOverrides); // first startup

		List<File> modFiles = ClasspathModHandler.getModFileCandidates();
		Set<File> mixinConfigJars = new HashSet<>();
		List<String> classloadEarlyClasses = new ArrayList<>();
		Map<String, List<String>> modClassesByJar = new HashMap<>();

		int threads = FermiumPlugin.CONFIG.get("general", "Jar Scanner Threads", FermiumBooterConfig.jarScannerThreads).getInt();
		if(threads <= 0) threads = Math.min(Runtime.getRuntime().availableProcessors(), 8);
		LOGGER.log(Level.INFO, "FermiumJarScanner scanning with {} thread(s).", threads);
		AsmJarScanner.scan(modFiles, manualOverrides, mixinConfigJars, classloadEarlyClasses, modClassesByJar, threads);

		//Mod Director classes+resources are gone by the time fermiumbooter is checking. This is probably not needed, but it also doesn't hurt
		try {
			if (((ArrayList<String>) Launch.blackboard.get("TweakClasses")).contains("net.jan.moddirector.launchwrapper.forge.AfterDeobfTweaker"))
				addMod("moddirector", null, null);
		} catch(Exception ignored) {}

		earlyModIDs.addAll(presentMods.keySet()); // just for mix2ferm

		//Add jars containing @MixinConfig classes (or all mod jars) to classpath
		//to allow FermiumBooter and Mixin to find the MixinConfig classes, the mixin jsons and mixin targets in other mods
		// Idea by Meldexun
		Set<File> earlyJars = new LinkedHashSet<>();
		if(FermiumPlugin.CONFIG.get("general", "Add All Mods To Classpath Early", FermiumBooterConfig.addAllModsToClasspathEarly).getBoolean())
			earlyJars.addAll(modFiles);
		earlyJars.addAll(mixinConfigJars);
		ClasspathModHandler.addJarsToClasspath(earlyJars, modClassesByJar);

		for(String className : classloadEarlyClasses)
			try {
				Class.forName(className);
			} catch (ClassNotFoundException e) {
				LOGGER.error("Could not find class {} when trying to classload early, skipping.", className, e);
			}

		long elapsed = System.currentTimeMillis() - time;
		LOGGER.log(Level.INFO, "Finished jar scanning, took {} ms", elapsed);
	}

    static void addMod(String modid, String version, String name) {
		if(modid != null && !modid.isEmpty()) {
			if(presentMods.containsKey(modid)) {
				ModInfo mcmodInfo = presentMods.get(modid);
				//if modid already found through mcmod.info, prefer @Mod values if they aren't null
				mcmodInfo.modName = name == null ? mcmodInfo.modName : name;
				if(version != null) mcmodInfo.setVersion(version);
			} else
				presentMods.put(modid, new ModInfo(modid, version, name));
		}
	}

	static void addMixinToggle(MixinConfigInfo configInfo) {
		mixinToggles.add(configInfo);
	}

	private static void applyMixinConfig(MixinConfigInfo mixinConfig) {
		boolean shouldApply = getRawBooleanConfigValue(mixinConfig.modId, mixinConfig.name, mixinConfig.defaultValue);
		if(!shouldApply) return;

		if(!skipCompatHandlingChecks()) {
			for(MixinConfigInfo.CompatInfo compatInfo : mixinConfig.compatInfos) {
				boolean hasCompatIssue = compatInfo.desired != isModPresent(compatInfo.modId); //modid is absent even though it should be present, or modid is present even though it should be absent

				if(isModPresent(compatInfo.modId)) {
					boolean targetedModFitsSpecification = true;
					if(compatInfo.modName != null && !isModNamed(compatInfo.modId, compatInfo.modName)) { //target specific mod name
						LOGGER.debug("FermiumMixinConfig config \"{}\" from {} found compat mod {} but with different mod name {} than target {}.", mixinConfig.name, mixinConfig.modId, compatInfo.modId, presentMods.get(compatInfo.modId).modName, compatInfo.modName);
						targetedModFitsSpecification = false;
					}
					if(targetedModFitsSpecification && compatInfo.modVersionRange != null && !isModVersionInRange(compatInfo.modId, compatInfo.modVersionRange)) {
						if(isValidVersionRange(compatInfo.modVersionRange)) {
							LOGGER.debug("FermiumMixinConfig config \"{}\" from {} found compat mod {} but with version {} outside target range {}.", mixinConfig.name, mixinConfig.modId, compatInfo.modId, presentMods.get(compatInfo.modId).version, compatInfo.modVersionRange);
							targetedModFitsSpecification = false;
						}
						else
							LOGGER.warn("FermiumMixinConfig config \"{}\" from {} specified invalid target version range {}, ignoring version check", mixinConfig.name, mixinConfig.modId, compatInfo.modVersionRange);
					}
					// desired and present but wrong name/version -> was no issue, now it is.
					// not desired but present but diff name/version -> was issue, now not anymore
					if(!targetedModFitsSpecification)
						hasCompatIssue = !hasCompatIssue;
				}

				if(hasCompatIssue) {
					if(compatInfo.warnIngame) warningCount++;
					if(compatInfo.disableMixin) {
						shouldApply = false;
						LOGGER.log(Level.ERROR, "FermiumMixinConfig config \"{}\" from {} disabled as incompatible {} {}: {}.", mixinConfig.name, mixinConfig.modId, (
								compatInfo.desired ? "without" : "with"), compatInfo.modId, compatInfo.reason);
					}
					else {
						LOGGER.log(Level.WARN, "FermiumMixinConfig config \"{}\" from {} may have issues {} {}: {}.", mixinConfig.name, mixinConfig.modId, (
								compatInfo.desired ? "without" : "with"), compatInfo.modId, compatInfo.reason);
					}
				}
			}
		}

		if(shouldApply) {
			LOGGER.log(Level.INFO, "FermiumMixinConfig enqueueing mixin(s) parsed from annotated config {} from {}.", mixinConfig.name, mixinConfig.modId);
			//Don't need to provide suppliers as enqueue will just be skipped in the first place instead
			if(mixinConfig.mixinJson != null && !mixinConfig.mixinJson.isEmpty())
				FermiumRegistryAPI.enqueueMixin(false, mixinConfig.mixinJson);
			if(mixinConfig.earlyJson != null && !mixinConfig.earlyJson.isEmpty())
				FermiumRegistryAPI.enqueueMixin(false, mixinConfig.earlyJson);
			if(mixinConfig.lateJson != null && !mixinConfig.lateJson.isEmpty())
				FermiumRegistryAPI.enqueueMixin(true, mixinConfig.lateJson);
		}
	}

	private static boolean isValidVersionRange(String range) {
		try {
			VersionRange.createFromVersionSpec(range);
			return true;
		} catch (InvalidVersionSpecificationException e) {
			return false;
		}
	}

	private static Boolean skipCompatHandlingChecks = null;
	
	private static boolean skipCompatHandlingChecks() {
		if(skipCompatHandlingChecks == null) {
			skipCompatHandlingChecks = FermiumPlugin.CONFIG.get("general", "Override Mixin Config Compatibility Checks", FermiumBooterConfig.overrideMixinCompatibilityChecks).getBoolean();
			if(skipCompatHandlingChecks)
				LOGGER.log(Level.WARN, "FermiumMixinConfig detected Override Mixin Config Compatibility Checks as enabled, good luck, don't report issues.");
		}
		return skipCompatHandlingChecks;
	}
	
	private static final Map<String, String> modConfigMap = new HashMap<>();
	
	//Janky but works, has worked, will continue to work (hopefully), too lazy to change
	private static boolean getRawBooleanConfigValue(String modConfigName, String configFieldName, boolean defaultValue) {
		if(!modConfigMap.containsKey(modConfigName)) {
			//Read config file
			File configFile = new File("config", modConfigName + ".cfg");
			String rawConfigString = null;
			if(configFile.exists() && configFile.isFile()) {
				try(Stream<String> stream = Files.lines(configFile.toPath())) {
					//Only collect boolean config options
					rawConfigString = stream.filter(s -> s.trim().startsWith("B:")).collect(Collectors.joining());
				}
				catch(Exception ex) {
					LOGGER.log(Level.ERROR, "FermiumMixinConfig failed to read config {}.cfg: {}.", modConfigName, ex);
					rawConfigString = null;
				}
			}
			if(rawConfigString == null) LOGGER.log(Level.WARN, "FermiumMixinConfig config {}.cfg missing or failed to read, using default values.", modConfigName);
			modConfigMap.put(modConfigName, rawConfigString);
		}
		
		String modConfigString = modConfigMap.get(modConfigName);
		//File doesn't exist or failed to read, assume all default values
		if(modConfigString == null) return defaultValue;
		if(modConfigString.contains("B:\"" + configFieldName + "\"=")) {
			return modConfigString.contains("B:\"" + configFieldName + "\"=true");
		}
		//Option likely new and not written to file yet, return default
		return defaultValue;
	}
}