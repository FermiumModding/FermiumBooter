package fermiumbooter;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fermiumbooter.api.ConditionalMixin;
import fermiumbooter.api.MixinConfig;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main class for loading conditional mixins based on config and mod dependencies.
 */
public class ConditionalMixinLoader {
    private static final Logger LOGGER = LogManager.getLogger("FermiumBooter");
    private static final Gson GSON = new Gson();

    private final EarlyConfigReader configReader = new EarlyConfigReader();
    private final ModDependencyChecker dependencyChecker = new ModDependencyChecker();
    private final Map<String, CommentedFileConfig> loadedConfigs = new HashMap<>();

    /**
     * Scans all mods for @MixinConfig annotated classes and loads their conditional mixins.
     *
     * @return List of mixin class names to load
     */
    public List<String> loadConditionalMixins() {
        LOGGER.info("Scanning for conditional mixin configs...");

        // Find all classes with @MixinConfig annotation
        List<ModFileScanData.AnnotationData> mixinConfigs = FMLLoader.getLoadingModList()
                .getModFiles()
                .stream()
                .map(ModFileInfo::getFile)
                .map(ModFile::getScanResult)
                .flatMap(scanData -> scanData.getAnnotations().stream())
                .filter(a -> a.annotationType().equals(Type.getType(MixinConfig.class)))
                .collect(Collectors.toList());

        LOGGER.info("Found {} @MixinConfig class(es)", mixinConfigs.size());

        List<String> mixinsToLoad = new ArrayList<>();

        for (ModFileScanData.AnnotationData annotation : mixinConfigs) {
            String className = annotation.clazz().getClassName();
            LOGGER.debug("Processing @MixinConfig class: {}", className);

            try {
                // Load the config class
                Class<?> configClass = Class.forName(className);

                // Get config file name from annotation or derive from class
                String configFileName = getConfigFileName(annotation, configClass);

                // Process all fields with @ConditionalMixin
                List<String> classMixins = processConfigClass(configClass, configFileName);
                mixinsToLoad.addAll(classMixins);

            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to load @MixinConfig class: {}", className, e);
            }
        }

        LOGGER.info("Loaded {} conditional mixin(s)", mixinsToLoad.size());
        return mixinsToLoad;
    }

    /**
     * Gets the config file name from annotation or derives it from class package.
     */
    private String getConfigFileName(ModFileScanData.AnnotationData annotation, Class<?> configClass) {
        // Check if annotation has a value
        Map<String, Object> annotationData = annotation.annotationData();
        if (annotationData.containsKey("value") && !annotationData.get("value").toString().isEmpty()) {
            return annotationData.get("value").toString();
        }

        // Derive from package name (e.g., fermiumbooter.api.config.MixinToggles -> fermiumbooter)
        String packageName = configClass.getPackageName();
        String modid = packageName.split("\\.")[0];
        return modid + "-mixintoggles";
    }

    /**
     * Processes a @MixinConfig class and returns mixins to load.
     */
    private List<String> processConfigClass(Class<?> configClass, String configFileName) {
        List<String> mixinsToLoad = new ArrayList<>();
        Map<String, Object> defaultValues = new HashMap<>();
        Map<String, String> comments = new HashMap<>();

        // First pass: collect all fields, their defaults, and comments
        for (Field field : configClass.getDeclaredFields()) {
            ConditionalMixin annotation = field.getAnnotation(ConditionalMixin.class);
            if (annotation == null) continue;

            field.setAccessible(true);
            try {
                Object defaultValue = field.get(null); // Assuming static fields
                defaultValues.put(field.getName(), defaultValue);

                // Collect comment/description if present
                if (!annotation.description().isEmpty()) {
                    comments.put(field.getName(), annotation.description());
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("Failed to read default value for field: {}", field.getName(), e);
            }
        }

        // Load or create config file
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(configFileName + ".toml");
        CommentedFileConfig config = configReader.loadConfig(configPath, defaultValues, comments);
        loadedConfigs.put(configFileName, config);

        // Second pass: evaluate conditions and collect mixins
        for (Field field : configClass.getDeclaredFields()) {
            ConditionalMixin annotation = field.getAnnotation(ConditionalMixin.class);
            if (annotation == null) continue;

            String mixinJson = annotation.mixinJson();
            LOGGER.debug("Evaluating condition for mixin JSON: {}", mixinJson);

            // If config disables it, don't even check dependencies
            Object configValue = configReader.getValue(config, field.getName(), defaultValues.get(field.getName()));
            boolean configAllows = configReader.evaluateConfigCondition(
                    configValue,
                    annotation.enableWhen(),
                    annotation.disableWhen()
            );

            if (!configAllows) {
                LOGGER.debug("Config disabled mixin: {} (field {} = {})", mixinJson, field.getName(), configValue);
                continue;
            }

            // Only check mod dependencies if config allows the mixin
            if (!dependencyChecker.checkAllDependencies(annotation.requireMods())) {
                handleFailure(annotation, mixinJson, "Required mod dependency not met");
                continue;
            }

            // Load the mixin JSON and extract mixin class names
            try {
                List<String> mixinClasses = loadMixinJson(mixinJson);
                mixinsToLoad.addAll(mixinClasses);
                LOGGER.info("Enabled mixin JSON: {} ({} mixin class(es))", mixinJson, mixinClasses.size());
            } catch (Exception e) {
                handleFailure(annotation, mixinJson, "Failed to load mixin JSON: " + e.getMessage());
            }
        }

        return mixinsToLoad;
    }

    /**
     * Loads a mixin JSON file and extracts all mixin class names from it.
     */
    private List<String> loadMixinJson(String mixinJsonPath) throws Exception {
        List<String> mixinClasses = new ArrayList<>();

        // Load JSON from classpath
        InputStream stream = getClass().getClassLoader().getResourceAsStream(mixinJsonPath);
        if (stream == null) {
            throw new IllegalStateException("Mixin JSON not found: " + mixinJsonPath);
        }

        JsonObject json = GSON.fromJson(new InputStreamReader(stream), JsonObject.class);

        // Extract mixins from all arrays: "mixins", "client", "server"
        for (String key : new String[]{"mixins", "client", "server"}) {
            if (json.has(key)) {
                JsonArray arr = json.getAsJsonArray(key);
                for (JsonElement elem : arr) {
                    mixinClasses.add(elem.getAsString());
                }
            }
        }

        return mixinClasses;
    }

    /**
     * Handles failure based on the configured failure action.
     */
    private void handleFailure(ConditionalMixin annotation, String mixinJson, String reason) {
        String message = annotation.failureMessage().isEmpty() ? reason : reason + ": " + annotation.failureMessage();

        switch (annotation.onFailure()) {
            case IGNORE:
                LOGGER.debug("Disabled mixin json. {}", message);
                break;
            case WARN:
                LOGGER.warn("Disabled mixin json. {}", message);
                break;
            case ERROR:
                LOGGER.error("Mixin json conditions aren't met, crashing deliberately: {}", message);
                throw new Error(message);
        }
    }

    /**
     * Cleanup method to close all loaded configs.
     */
    public void close() {
        configReader.close();
        loadedConfigs.clear();
    }
}