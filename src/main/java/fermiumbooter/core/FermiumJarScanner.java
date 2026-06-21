package fermiumbooter.core;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fermiumbooter.api.MixinConfig;
import fermiumbooter.api.MixinToggle;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FermiumJarScanner {
    private static final Logger LOGGER = LogManager.getLogger("FermiumBooter");
    private static final Gson GSON = new Gson();

    /**
     * Scans all mods for @MixinConfig annotated classes and loads their conditional mixins.
     *
     * @return List of mixin class names to load
     */
    public static List<String> getToggleMixins() {
        LOGGER.info("Scanning for MixinConfig classes...");

        // Find all classes with @MixinConfig annotation
        List<ModFileScanData.AnnotationData> mixinConfigs = FMLLoader.getLoadingModList()
                .getModFiles()
                .stream()
                .map(ModFileInfo::getFile)
                .map(ModFile::getScanResult)
                .flatMap(scanData -> scanData.getAnnotations().stream())
                .filter(a -> a.annotationType().equals(Type.getType(MixinConfig.class)))
                .toList();

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
                List<String> mixinClasses = parseMixinToggles(configClass, configFileName);
                mixinsToLoad.addAll(mixinClasses);

            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to load @MixinConfig class: {}", className, e);
            }
        }

        EarlyConfigReader.close();

        LOGGER.info("Loaded {} mixin toggle(s)", mixinsToLoad.size());
        return mixinsToLoad;
    }

    private static String getConfigFileName(ModFileScanData.AnnotationData annotation, Class<?> configClass) {
        // Check if annotation has a value
        Map<String, Object> annotationData = annotation.annotationData();
        if (annotationData.containsKey("fileName") && !annotationData.get("fileName").toString().isEmpty())
            return annotationData.get("fileName").toString();

        // Derive from package name (e.g., fermiumbooter.api.config.MixinToggles -> fermiumbooter-mixintoggles)
        String packageName = configClass.getPackageName();
        String modid = packageName.split("\\.")[0];
        return modid + "-mixintoggles";
    }

    /**
     * Processes a @MixinConfig class and returns mixins to load.
     */
    private static List<String> parseMixinToggles(Class<?> configClass, String configFileName) {
        List<String> mixinsToLoad = new ArrayList<>();
        Map<String, Object> defaultValues = new HashMap<>();
        Map<String, String> comments = new HashMap<>();

        // First pass: collect all fields, their defaults, and comments
        for (Field field : configClass.getDeclaredFields()) {
            MixinToggle annotation = field.getAnnotation(MixinToggle.class);
            if (annotation == null) continue;

            field.setAccessible(true);
            try {
                Object defaultValue = field.get(null); // Assuming static fields
                defaultValues.put(field.getName(), defaultValue);

                // Collect comment/description if present
                if (!annotation.comment().isEmpty()) {
                    comments.put(field.getName(), annotation.comment());
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("Failed to read default value for field: {}", field.getName(), e);
            }
        }

        // Load or create config file
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(configFileName + ".toml");
        CommentedFileConfig config = EarlyConfigReader.loadConfig(configPath, defaultValues, comments);

        // Second pass: evaluate conditions and collect mixins
        for (Field field : configClass.getDeclaredFields()) {
            MixinToggle annotation = field.getAnnotation(MixinToggle.class);
            if (annotation == null) continue;

            String mixinJson = annotation.mixinJson();
            LOGGER.debug("Evaluating condition for mixin JSON: {}", mixinJson);

            // If config disables it, don't even check dependencies
            Object configValue = EarlyConfigReader.getValue(config, field.getName(), defaultValues.get(field.getName()));
            boolean configEnabled = EarlyConfigReader.evaluateConfigCondition(
                    configValue,
                    annotation.enableWhen(),
                    annotation.disableWhen()
            );

            if (!configEnabled) {
                LOGGER.debug("Config disabled mixin: {} (field {} = {})", mixinJson, field.getName(), configValue);
                continue;
            }

            // Only check mod dependencies if config allows the mixin
            if (!ModDependencyChecker.checkAllDependencies(annotation.dependencies())) {
                handleFailure(annotation, "Required mod dependency not met");
                continue;
            }

            // Load the mixin JSON and extract mixin class names
            try {
                List<String> mixinClasses = loadMixinJson(mixinJson);
                mixinsToLoad.addAll(mixinClasses);
                LOGGER.info("Enabled mixin JSON: {} ({} mixin class(es))", mixinJson, mixinClasses.size());
            } catch (Exception e) {
                handleFailure(annotation, "Failed to load mixin JSON: " + e.getMessage());
            }
        }

        return mixinsToLoad;
    }

    /**
     * Loads a mixin JSON file and extracts all mixin class names from it.
     */
    private static List<String> loadMixinJson(String mixinJsonPath) {
        List<String> mixinClasses = new ArrayList<>();

        // Load JSON from classpath
        InputStream stream = FermiumJarScanner.class.getClassLoader().getResourceAsStream(mixinJsonPath);
        if (stream == null) throw new IllegalStateException("Mixin JSON not found: " + mixinJsonPath);

        JsonObject json = GSON.fromJson(new InputStreamReader(stream), JsonObject.class);

        for (String key : new String[]{"mixins", "client", "server"}) {
            if (!json.has(key)) continue;
            if (MixinEnvironment.getCurrentEnvironment().getSide() == MixinEnvironment.Side.CLIENT && key.equals("server")) continue;
            if (MixinEnvironment.getCurrentEnvironment().getSide() == MixinEnvironment.Side.SERVER && key.equals("client")) continue;

            for (JsonElement elem : json.getAsJsonArray(key))
                mixinClasses.add(elem.getAsString());
        }

        return mixinClasses;
    }

    private static void handleFailure(MixinToggle annotation, String reason) {
        String message = annotation.failureMessage().isEmpty() ? reason : reason + ": " + annotation.failureMessage();

        switch (annotation.onFailure()) {
            case IGNORE: LOGGER.debug("Disabled mixin json. {}", message); break;
            case WARN: LOGGER.warn("Disabled mixin json. {}", message); break;
            case ERROR:
                LOGGER.error("Mixin json conditions aren't met, crashing deliberately: {}", message);
                throw new Error(message);
        }
    }
}