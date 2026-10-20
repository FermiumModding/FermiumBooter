package fermiumbooter.util;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.Repository;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Handles which mod jars are on the LaunchClassLoader before mod construction and how Forge's mod discovery treats them
 * Forge only puts coremod jars on the classpath early, every other mod jar is only added right before mods get constructed
 */
public abstract class ClasspathModHandler {

	private static List<File> modFileCandidates = null;

	/**
	 * Same candidates Forge's Loader searches for mods: mods folder, mods/version folder, --mods and extracted contained dependencies
	 * @return jar/zip files, cached until clearCaches
	 */
	public static List<File> getModFileCandidates() {
		if(modFileCandidates == null) {
			//Always feels wrong but it works
			File mcDir = new File(".");

			List<File> candidates = LibraryManager.gatherLegacyCanidates(mcDir);
			for(Artifact artifact : LibraryManager.flattenLists(mcDir)) {
				artifact = Repository.resolveAll(artifact);
				if(artifact != null && !candidates.contains(artifact.getFile())) {
					candidates.add(artifact.getFile());
				}
			}
			modFileCandidates = candidates;
		}
		return modFileCandidates;
	}

	public static void clearCaches() {
		modFileCandidates = null;
	}

	/**
	 * Adds jars to the classpath early so their classes and resources can be read before mod construction.
	 * Added jars are put on the reparseable coremod list, which makes Forge skip them in its classpath mod discovery.
	 * They are then still discovered as mods in the normal mods folder pass, keeping mod load order and behavior unchanged.
	 */
	static void addJarsToClasspath(Collection<File> jars) {
		Set<String> existingPaths = new HashSet<>();
		for(URL url : Launch.classLoader.getURLs()) {
			try {
				existingPaths.add(new File(url.toURI()).getCanonicalPath());
			}
			catch(Exception ignored) { }
		}

		List<String> ignoredMods = CoreModManager.getIgnoredMods();
		List<String> reparseableMods = CoreModManager.getReparseableCoremods();
		int added = 0;
		for(File jar : jars) {
			String name = jar.getName();
			if(!jar.isFile() || (!name.endsWith(".jar") && !name.endsWith(".zip"))) continue;
			try {
				File canonicalJar = jar.getCanonicalFile();
				//Also skips duplicates in the given jars
				if(!existingPaths.add(canonicalJar.getPath())) continue;

				Launch.classLoader.addURL(canonicalJar.toURI().toURL());
				//Ignored jars are pure coremods/tweakers that Forge never loads as mods
				if(!ignoredMods.contains(name) && !reparseableMods.contains(name)) {
					reparseableMods.add(name);
				}
				added++;
				FermiumJarScanner.LOGGER.log(Level.DEBUG, "FermiumJarScanner added {} to the classpath early.", name);
			}
			catch(IOException ex) {
				FermiumJarScanner.LOGGER.log(Level.ERROR, "FermiumJarScanner failed to add {} to the classpath.", name, ex);
			}
		}
		FermiumJarScanner.LOGGER.log(Level.INFO, "FermiumJarScanner added {} jars to the classpath early.", added);
	}

	/**
	 * Indev, Mixin puts jars using 'ForceLoadAsMod' on Forge's reparseable coremod list, which Forge's classpath mod discovery skips
	 * Mixin only exempts ForgeGradle 2 dependencies ("deobfedDeps"), so with ForgeGradle 3+ those dependencies run their coremod and mixins but never load as mods
	 * This takes such jars back off that list if they are on the launch classpath and not in the mods folder
	 * Based on PR #10 by fresh-Glitch
	 */
	public static void fixIndevForceLoadAsModDependencies() {
		if(!FMLLaunchHandler.isDeobfuscatedEnvironment()) return;

		Set<String> classpathJars = new HashSet<>();
		for(String path : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
			File file = new File(path);
			if(file.isFile()) classpathJars.add(file.getName());
		}

		Set<String> modFolderJars = new HashSet<>();
		for(File file : getModFileCandidates()) {
			modFolderJars.add(file.getName());
		}

		List<String> ignoredMods = CoreModManager.getIgnoredMods();
		Iterator<String> iterator = CoreModManager.getReparseableCoremods().iterator();
		while(iterator.hasNext()) {
			String name = iterator.next();
			if(classpathJars.contains(name) && !modFolderJars.contains(name) && !ignoredMods.contains(name)) {
				iterator.remove();
				FermiumJarScanner.LOGGER.log(Level.INFO, "FermiumJarScanner taking indev dependency {} off the reparseable coremod list so it can load as a mod.", name);
			}
		}
	}
}
