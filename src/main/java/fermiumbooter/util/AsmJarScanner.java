package fermiumbooter.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.apache.logging.log4j.Level;
import org.objectweb.asm.ClassReader;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Jar scanner, reading the annotations of every class file in every mod jar with ASM, one jar per thread
 * Scans the mods folder candidates and the non-library classpath entries,
 * which covers launcher-injected mods as well as project output directories and gradle dependencies indev
 */
public abstract class AsmJarScanner {

	private static final Pattern classFilePattern = Pattern.compile("[^\\s\\$]+(\\$[^\\s]+)?\\.class$");

	//Skip irrelevant paths/files that waste time
	private static final String[] skippedPaths = {
			"__MACOSX",
			"module-info",
			"org/spongepowered/",
			"net/minecraftforge/",
			"net/minecraft/",
			"com/google/common/",
			"com/mojang/",
			"org/objectweb/asm/",
			"org/apache/",
			"io/github/classgraph/",
			"nonapi/io/github/classgraph/",
			"com/llamalad7/",
			"it/unimi",
			"kotlin",
			"scala/"
	};

	//Copied from Forge's ModClassLoader.STANDARD_LIBRARIES and ModClassLoader.isDefaultLibrary, used to skip classpath libraries
	private static final List<String> standardLibraries = Arrays.asList("jinput.jar", "lwjgl.jar", "lwjgl_util.jar", "rt.jar");
	private static final String[] defaultLibraryPrefixes = {
			"launchwrapper-", "asm-all-", "akka-actor_2.11-", "config-", "scala-", "jopt-simple-", "lzma-", "realms-",
			"httpclient-", "httpcore-", "vecmath-", "trove4j-", "icu4j-core-mojang-", "codecjorbis-", "codecwav-",
			"libraryjavawound-", "librarylwjglopenal-", "soundsystem-", "netty-all-", "guava-", "commons-lang3-",
			"commons-compress-", "commons-logging-", "commons-io-", "commons-codec-", "jinput-", "jutils-", "gson-",
			"authlib-", "log4j-api-", "log4j-core-", "lwjgl-", "lwjgl_util-", "twitch-", "jline-", "jna-", "platform-",
			"oshi-core-", "netty-", "libraryjavasound-", "fastutil-"
	};
	//Libraries on the classpath not covered by Forge's list (bundled by FermiumBooter, ForgeGradle or vanilla)
	private static final String[] additionalLibraryPrefixes = {
			"mixin-", "mixinextras-", "mixinsquared-", "classgraph-", "asm-debug-all-",
			"forge-1.12.2-", "client-extra", "mcp_stable-", "mcp_snapshot-", "legacydev-",
			"maven-artifact-", "plexus-utils-", "jsr305-", "patchy-", "text2speech-", "java-objc-bridge-"
	};

	/**
	 * Fills mod infos and mixin toggles of FermiumJarScanner, collects jars containing @MixinConfig classes, classes to classload early
	 * and the @Mod class names of each jar (keyed by canonical path)
	 */
	static void scan(List<File> modFiles, Map<String, String> manualOverrides, Set<File> mixinConfigJars, List<String> classloadEarlyClasses, Map<String, List<String>> modClassesByJar, int threads) {
		List<File> fileCandidates = new ArrayList<>(modFiles);

		//Some mods aren't in the mods folder but on the classpath: launcher-injected ones (e.g. OptiFine installed as library, jar mods)
		//and indev the project output directories and gradle dependencies
		fileCandidates.addAll(gatherClasspathCandidates());

		//Same file can show up multiple times, e.g. coremods in both mods folder and classpath
		Map<String, File> uniqueCandidates = new LinkedHashMap<>();
		for(File candidate : fileCandidates) {
			uniqueCandidates.putIfAbsent(canonicalPath(candidate), candidate);
		}

		//package -> modid, converted to entry paths
		Map<String, String> overridePaths = new HashMap<>();
		for(Map.Entry<String, String> entry : manualOverrides.entrySet())
			overridePaths.put(entry.getKey().replace('.', '/') + "/", entry.getValue());

		List<JarScanResult> results = searchModFiles(new ArrayList<>(uniqueCandidates.values()), overridePaths, threads);

		//Merged on this thread in candidate order, so the outcome doesn't depend on thread timing
		for(JarScanResult result : results) {
            if(result.modInfo != null) {
				try {
					//Manually search instead of properly parsing to class to avoid classloading issues
					searchModInfoRecursive(new JsonParser().parse(result.modInfo));
				}
				catch(Exception ignored) { }
			}
			for(String modid : result.overrideModIds)
				FermiumJarScanner.addMod(modid, null, null);
			for(MixinConfigInfo configInfo : result.mixinToggles)
				FermiumJarScanner.addMixinToggle(configInfo);
			classloadEarlyClasses.addAll(result.classloadEarlyClasses);
			//will be added to classpath, directories only exist in dev where they already are on the classpath
			if(result.hasMixinConfig && result.modFile.isFile()) mixinConfigJars.add(result.modFile);
		}

		//@Mod parsing has to be deferred until all mcmod.info files are read, so @Mod values are preferred
		for(JarScanResult result : results) {
			if(result.modClasses.isEmpty()) continue;
			List<String> classNames = new ArrayList<>();
			for(ASMClassVisitor modClass : result.modClasses) {
				FermiumJarScanner.addMod(modClass.modid, modClass.modVersion, modClass.modName);
				classNames.add(modClass.className);
			}
			modClassesByJar.put(canonicalPath(result.modFile), classNames);
		}
	}

	static String canonicalPath(File file) {
		try {
			return file.getCanonicalPath();
		}
		catch(IOException ex) {
			return file.getAbsolutePath();
		}
	}

	//Mirrors what Forge's ModDiscoverer.findClasspathMods considers mod candidates
	private static List<File> gatherClasspathCandidates() {
		List<File> candidates = new ArrayList<>();
		String javaHome = System.getProperty("java.home");

		for(File file : getClassLoaderSources()) {
			if(!file.exists()) continue;
			if(javaHome != null && file.getAbsolutePath().startsWith(javaHome)) continue;

			if(file.isFile()) {
				String name = file.getName();
				if(!name.endsWith(".jar") && !name.endsWith(".zip")) continue;
				if(isKnownLibrary(name)) continue;
			}

			FermiumJarScanner.LOGGER.log(Level.DEBUG, "FermiumJarScanner found classpath candidate {}.", file.getAbsolutePath());
			candidates.add(file);
		}
		return candidates;
	}

	private static List<JarScanResult> searchModFiles(List<File> modFiles, Map<String, String> overridePaths, int threads) {
		List<JarScanResult> results = new ArrayList<>();
		if(threads <= 1 || modFiles.size() <= 1) {
			for(File modFile : modFiles)
				results.add(searchModFile(modFile, overridePaths));
			return results;
		}

		AtomicInteger threadCount = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> {
			Thread thread = new Thread(runnable, "FermiumJarScanner-" + threadCount.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
		try {
			List<Future<JarScanResult>> futures = new ArrayList<>();
			for(File modFile : modFiles)
				futures.add(executor.submit(() -> searchModFile(modFile, overridePaths)));
			for(int i = 0; i < futures.size(); i++) {
				try {
					results.add(futures.get(i).get());
				}
				catch(InterruptedException | ExecutionException ex) {
					FermiumJarScanner.LOGGER.log(Level.ERROR, "FermiumJarScanner failed to search jar file {}.", modFiles.get(i).getName(), ex);
				}
			}
		}
		finally {
			executor.shutdownNow();
		}
		return results;
	}

	private static List<File> getClassLoaderSources() {
		List<File> sources = new ArrayList<>();
		for(URL url : Launch.classLoader.getSources()) {
			try {
				if("file".equals(url.toURI().getScheme())) sources.add(new File(url.toURI()));
			}
			catch(Exception ignored) { }
		}
		return sources;
	}

	private static boolean isKnownLibrary(String fileName) {
		if(standardLibraries.contains(fileName)) return true;
		if(CoreModManager.getIgnoredMods().contains(fileName)) return true;
		if(CoreModManager.getReparseableCoremods().contains(fileName)) return true;
		for(String prefix : defaultLibraryPrefixes) {
			if(fileName.startsWith(prefix)) return true;
		}
		for(String prefix : additionalLibraryPrefixes) {
			if(fileName.startsWith(prefix)) return true;
		}
		return false;
	}

	private static void searchModInfoRecursive(JsonElement element) {
		if(element instanceof JsonObject) {
			if(((JsonObject)element).has("modid")) {
				JsonObject obj = (JsonObject)element;
				String modid = obj.get("modid").getAsString().toLowerCase();
				String version = obj.has("version") ? obj.get("version").getAsString() : null;
				String name = obj.has("name") ? obj.get("name").getAsString() : null;
				FermiumJarScanner.presentMods.put(modid, new ModInfo(modid, version, name));
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

	/**
	 * Runs on scanner threads, so only collects into the returned result
	 */
	private static JarScanResult searchModFile(File modFile, Map<String, String> overridePaths) {
		JarScanResult result = new JarScanResult(modFile);
		try(EntrySource source = modFile.isDirectory() ? new DirectorySource(modFile.toPath()) : new JarSource(new JarFile(modFile))) {
			//mcmod.info search, parsed when merging
			//Some mods are dumb and set their mcmod.info modids incorrectly, need to also check annotation regardless
			try(InputStream inputStream = source.open("mcmod.info")) {
				if(inputStream != null) result.modInfo = readString(inputStream);
			}
			catch(Exception ignored) { }

			//The vanilla jar on the classpath has thousands of obfuscated classes outside the skipped packages, launchers name it differently
			if(source.contains("net/minecraft/client/main/Main.class") || source.contains("net/minecraft/server/MinecraftServer.class")) return result;

			//Iterate files in jar/directory
			entryLoop:
			for(String entryName : source.entryNames()) {
				for(String skipped : skippedPaths) {
					if(entryName.contains(skipped)) continue entryLoop;
				}

				//Manual compat for mods without @Mod or mcmod.info, crucify me
				for(Map.Entry<String, String> override : overridePaths.entrySet()) {
					if(entryName.startsWith(override.getKey())) result.overrideModIds.add(override.getValue());
				}

				//Match only class files
				if(!classFilePattern.matcher(entryName).matches()) continue;

				ASMClassVisitor visitedClass;
				try(InputStream inputStream = source.open(entryName)) {
					ClassReader reader = new ClassReader(inputStream);
					visitedClass = new ASMClassVisitor();
					//Only annotations are needed
					reader.accept(visitedClass, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				}
				catch(Exception ex) {
					FermiumJarScanner.LOGGER.log(Level.INFO, "FermiumJarScanner failed to search class file {} in {}, likely fine to ignore.", entryName, modFile.getName());
					continue;
				}

				if(visitedClass.modid != null) result.modClasses.add(visitedClass);

				if(visitedClass.mixinConfigName != null) {
					result.hasMixinConfig = true;
					result.mixinToggles.addAll(visitedClass.mixinToggles);
					if(visitedClass.classloadEarly) result.classloadEarlyClasses.add(visitedClass.className);
				}
			}
		}
		catch(Exception ex) {
			FermiumJarScanner.LOGGER.log(Level.ERROR, "FermiumJarScanner failed to search jar file {}.", modFile.getName());
		}
		return result;
	}

	private static String readString(InputStream inputStream) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while((read = inputStream.read(buffer)) != -1)
			bytes.write(buffer, 0, read);
		return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
	}

	private static class JarScanResult {

		private final File modFile;
		private String modInfo = null;
		private final Set<String> overrideModIds = new LinkedHashSet<>();
		private final List<ASMClassVisitor> modClasses = new ArrayList<>();
		private final List<MixinConfigInfo> mixinToggles = new ArrayList<>();
		private final List<String> classloadEarlyClasses = new ArrayList<>();
		private boolean hasMixinConfig = false;

		private JarScanResult(File modFile) {
			this.modFile = modFile;
		}
	}

	/**
	 * Common view on jar files and (indev) class directories, entry names always use '/' as separator
	 */
	private interface EntrySource extends Closeable {
		List<String> entryNames() throws IOException;
		
		boolean contains(String entryName);

		/**
		 * @return null if the entry doesn't exist
		 */
		InputStream open(String entryName) throws IOException;
	}

	private static class JarSource implements EntrySource {

		private final JarFile jar;

		private JarSource(JarFile jar) {
			this.jar = jar;
		}

		@Override
		public List<String> entryNames() {
			return Collections.list(this.jar.entries()).stream().map(ZipEntry::getName).collect(Collectors.toList());
		}

		@Override
		public boolean contains(String entryName) {
			return this.jar.getEntry(entryName) != null;
		}
		
		@Override
		public InputStream open(String entryName) throws IOException {
			ZipEntry entry = this.jar.getEntry(entryName);
			return entry == null ? null : this.jar.getInputStream(entry);
		}

		@Override
		public void close() throws IOException {
			this.jar.close();
		}
	}

	private static class DirectorySource implements EntrySource {

		private final Path root;

		private DirectorySource(Path root) {
			this.root = root;
		}

		@Override
		public List<String> entryNames() throws IOException {
			try(Stream<Path> paths = Files.walk(this.root)) {
				return paths.filter(Files::isRegularFile)
						.map(path -> this.root.relativize(path).toString().replace(File.separatorChar, '/'))
						.collect(Collectors.toList());
			}
		}

		@Override
		public boolean contains(String entryName) {
			return Files.isRegularFile(this.root.resolve(entryName));
		}
		
		@Override
		public InputStream open(String entryName) throws IOException {
			Path path = this.root.resolve(entryName);
			return Files.isRegularFile(path) ? Files.newInputStream(path) : null;
		}

		@Override
		public void close() { }
	}
}
