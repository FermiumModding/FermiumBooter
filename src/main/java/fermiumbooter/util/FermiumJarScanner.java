package fermiumbooter.util;

import com.google.gson.*;
import fermiumbooter.FermiumRegistryAPI;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.Repository;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Janky handler for searching jar files for modids and @MixinConfig handling
 * Searches for both mcmod.info and @Mod annotations to find modids, since some mods only have one or the other
 * Some also have neither or set it improperly and give me a headache, so they have manual compatibility
 * Also handles searching for and parsing @MixinConfig handling using ASM to avoid classloading issues
 */
public abstract class FermiumJarScanner {
	
	private static final Pattern classFilePattern = Pattern.compile("[^\\s\\$]+(\\$[^\\s]+)?\\.class$");
	private static final Logger LOGGER = LogManager.getLogger("FermiumJarScanner");
	
	private static final List<ASMClassVisitor> parsedClassVisitors = new ArrayList<>();
	private static final Set<String> earlyModIDs = new HashSet<>();
	private static int mixinConfigCount = 0;
	private static int warningCount = 0;
	
	public static boolean isModPresent(String modID) {
		if(modID == null || modID.isEmpty()) return false;
		handleCaching();
		return earlyModIDs.contains(modID);
	}
	
	public static void handleCaching() {
		if(!earlyModIDs.isEmpty() || !parsedClassVisitors.isEmpty()) return;
		
		LOGGER.log(Level.INFO, "FermiumJarScanner beginning jar searching.");
		startJarSearching();
		LOGGER.log(Level.INFO, "FermiumJarScanner finished jar searching, found {} ModIDs.", earlyModIDs.size());
		
		LOGGER.log(Level.INFO, "FermiumMixinConfig beginning MixinConfig parsing.");
		for(ASMClassVisitor classVisitor : parsedClassVisitors) {
			parseMixinConfigVisitor(classVisitor);
		}
		LOGGER.log(Level.INFO, "FermiumMixinConfig finished MixinConfig parsing, parsed {} config options with {} warnings", mixinConfigCount, warningCount);
	}
	
	public static int getWarningCount() {
		return warningCount;
	}
	
	public static void clearCaches() {
		parsedClassVisitors.clear();
		modConfigMap.clear();
	}
	
	//Not the most efficient implementation possible, but only takes around 2-3 seconds or so total even in relatively large packs
	private static void startJarSearching() {
		//Always feels wrong but it works
		File mcDir = new File(".");
		
		List<Artifact> maven_canidates = LibraryManager.flattenLists(mcDir);
		List<File> file_canidates = LibraryManager.gatherLegacyCanidates(mcDir);
		for(Artifact artifact : maven_canidates) {
			artifact = Repository.resolveAll(artifact);
			if(artifact != null) {
				File target = artifact.getFile();
				if(!file_canidates.contains(target)) {
					file_canidates.add(target);
				}
			}
		}
		
		//Technically shows up as modids but isn't found through normal methods, just add manually
		earlyModIDs.addAll(Arrays.asList("minecraft", "mcp", "FML", "forge"));
		
		for(File modFile : file_canidates) {
			searchJarFile(modFile);
		}
	}
	
	private static void searchJarFile(File modFile) {
		//Search jar
		try(JarFile jar = new JarFile(modFile)) {
			//mcmod.info search
			//Some mods are dumb and set their mcmod.info modids incorrectly, need to also check annotation regardless
			ZipEntry modInfo = jar.getEntry("mcmod.info");
			if(modInfo != null) {
				try(InputStream inputStream = jar.getInputStream(modInfo)) {
					InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
					JsonElement rootElement = new JsonParser().parse(reader);
					//Manually search instead of properly parsing to class to avoid classloading issues
					searchModInfoRecursive(rootElement);
				}
				catch(Exception ignored) { }
			}
			
			//Iterate files in jar
			for(ZipEntry ze : Collections.list(jar.entries())) {
				//Skip irrelevant paths/files that waste time
				if(ze.getName().contains("__MACOSX")) continue;
				if(ze.getName().contains("module-info")) continue;
				if(ze.getName().contains("org/spongepowered")) continue;
				if(ze.getName().contains("it/unimi")) continue;
				if(ze.getName().contains("kotlin")) continue;
				
				//Manual error avoidance/compat, crucify me
				//TODO: config defined overrides?
				if(ze.getName().contains("net/jan/moddirector")) {
					earlyModIDs.add("moddirector");
					return;
				}
				if(ze.getName().contains("git/jbredwards/jsonpaintings")) {
					earlyModIDs.add("jsonpaintings");
					return;
				}
				if(ze.getName().contains("net/optifine")) {
					earlyModIDs.add("optifine");
					return;
				}
				
				//Match only class files
				if(classFilePattern.matcher(ze.getName()).matches()) {
					ASMClassVisitor visitedClass;
					try(InputStream inputStream = jar.getInputStream(ze)) {
						ClassReader reader = new ClassReader(inputStream);
						visitedClass = new ASMClassVisitor();
						reader.accept(visitedClass, 0);
					}
					catch(Exception ex) {
						LOGGER.log(Level.INFO, "FermiumJarScanner failed to search class file {} in {}, likely fine to ignore.", ze.getName(), jar.getName());
						continue;
					}
					
					//modid visitor parse
					parseModVisitor(visitedClass);
					
					//mixinconfig visitor parse has to be deferred until all modids are found
					parsedClassVisitors.add(visitedClass);
				}
			}
		}
		catch(Exception ex) {
			LOGGER.log(Level.ERROR, "FermiumJarScanner failed to search jar file {}.", modFile.getName());
		}
	}
	
	private static void searchModInfoRecursive(JsonElement element) {
		if(element instanceof JsonObject) {
			if(((JsonObject)element).has("modid")) {
				earlyModIDs.add(((JsonObject)element).get("modid").getAsString().toLowerCase());
			}
			else {
				for(Map.Entry<String, JsonElement> entry : ((JsonObject)element).entrySet()) {
					searchModInfoRecursive(entry.getValue());
				}
			}
		}
		else if(element instanceof JsonArray) {
			for(JsonElement elem : ((JsonArray)element)) {
				searchModInfoRecursive(elem);
			}
		}
	}
	
	private static void parseModVisitor(ASMClassVisitor visitedClass) {
		String modid = visitedClass.modid;
		if(modid != null && !modid.isEmpty()) {
			earlyModIDs.add(modid);
		}
	}
	
	private static void parseMixinConfigVisitor(ASMClassVisitor visitedClass) {
		String mixinConfigName = visitedClass.mixinConfigName;
		if(mixinConfigName != null && !mixinConfigName.isEmpty()) {
			for(ASMClassVisitor.ASMFieldVisitor parsedField : visitedClass.parsedFieldVisitors) {
				mixinConfigCount++;
				
				boolean shouldApply = getRawBooleanConfigValue(mixinConfigName, parsedField.configFieldName, parsedField.defaultValue);
				if(!shouldApply) continue;
				
				if(!skipCompatHandlingChecks()) {
					for(ASMClassVisitor.CompatHandlingAnnotation compatAnno : parsedField.compatHandlingAnnotations) {
						if(compatAnno.desired != isModPresent(compatAnno.modid)) {
							warningCount++;
							if(compatAnno.disableMixin) {
								shouldApply = false;
								LOGGER.log(Level.ERROR, "FermiumMixinConfig config {} from {} disabled as incompatible {} {}: {}.",
										   parsedField.configFieldName, mixinConfigName, (compatAnno.desired ? "without" : "with"), compatAnno.modid, compatAnno.reason);
							}
							else {
								LOGGER.log(Level.WARN, "FermiumMixinConfig config {} from {} may have issues {} {}: {}.",
										   parsedField.configFieldName, mixinConfigName, (compatAnno.desired ? "without" : "with"), compatAnno.modid, compatAnno.reason);
							}
						}
					}
				}
				
				if(shouldApply) {
					LOGGER.log(Level.INFO, "FermiumMixinConfig enqueueing mixin(s) parsed from annotated config {} from {}.", parsedField.configFieldName, mixinConfigName);
					//Don't need to provide suppliers as enqueue will just be skipped in the first place instead
					if(parsedField.earlyMixinName != null) FermiumRegistryAPI.enqueueMixin(false, parsedField.earlyMixinName);
					if(parsedField.lateMixinName != null) FermiumRegistryAPI.enqueueMixin(true, parsedField.lateMixinName);
				}
			}
		}
	}
	
	private static Boolean skipCompatHandlingChecks = null;
	
	private static boolean skipCompatHandlingChecks() {
		if(skipCompatHandlingChecks == null) {
			skipCompatHandlingChecks = getRawBooleanConfigValue("fermiumbooter", "Override Mixin Config Compatibility Checks", false);
			if(skipCompatHandlingChecks) {
				LOGGER.log(Level.WARN, "FermiumMixinConfig detected Override Mixin Config Compatibility Checks as enabled, good luck, don't report issues.");
			}
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