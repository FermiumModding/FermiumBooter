package fermiumbooter.core;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import fermiumbooter.api.MixinConfig;
import fermiumbooter.api.MixinToggle;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FermiumJarScanner {

    public static List<String> getToggleMixins() {
        FermiumBooterPlugin.LOGGER.info("Scanning for MixinConfig classes...");

        // Find all classes with @MixinConfig annotation
        List<ModFileScanData.AnnotationData> mixinConfigs = FMLLoader.getLoadingModList()
                .getModFiles()
                .stream()
                .map(ModFileInfo::getFile)
                .map(ModFile::getScanResult)
                .flatMap(scanData -> scanData.getAnnotations().stream())
                .filter(a -> a.annotationType().equals(Type.getType(MixinConfig.class)))
                .toList();

        FermiumBooterPlugin.LOGGER.info("Found {} @MixinConfig class(es)", mixinConfigs.size());

        List<String> mixinsToLoad = new ArrayList<>();

        for (ModFileScanData.AnnotationData annotation : mixinConfigs) {
            String className = annotation.clazz().getClassName();
            FermiumBooterPlugin.LOGGER.debug("Processing @MixinConfig class: {}", className);

            try {
                // Load the config class
                Class<?> configClass = Class.forName(className);

                // Get config file name from annotation or derive from class
                String configFileName = getConfigFileName(annotation, configClass);

                // Process all fields with @ConditionalMixin
                List<String> mixinClasses = parseMixinToggles(configClass, configFileName);
                mixinsToLoad.addAll(mixinClasses);

            } catch (ClassNotFoundException e) {
                FermiumBooterPlugin.LOGGER.error("Failed to load @MixinConfig class: {}", className, e);
            }
        }

        EarlyConfigReader.close();

        FermiumBooterPlugin.LOGGER.info("Loaded {} mixin toggle(s)", mixinsToLoad.size());
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
                FermiumBooterPlugin.LOGGER.error("Failed to read default value for field: {}", field.getName(), e);
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
            FermiumBooterPlugin.LOGGER.debug("Evaluating condition for mixin JSON: {}", mixinJson);

            // If config disables it, don't even check dependencies
            Object configValue = EarlyConfigReader.getValue(config, field.getName(), defaultValues.get(field.getName()));
            boolean configEnabled = EarlyConfigReader.evaluateConfigCondition(
                    configValue,
                    annotation.enableWhen(),
                    annotation.disableWhen()
            );

            if (!configEnabled) {
                FermiumBooterPlugin.LOGGER.debug("Config disabled mixin: {} (field {} = {})", mixinJson, field.getName(), configValue);
                continue;
            }

            // Only check mod dependencies if config allows the mixin
            if (!ModDependencyChecker.checkAllDependencies(annotation.dependencies())) {
                handleFailure(annotation, mixinJson, "Required mod dependency not met");
                continue;
            }

            mixinsToLoad.add(mixinJson);
        }

        return mixinsToLoad;
    }

    private static void handleFailure(MixinToggle annotation, String mixinJson, String reason) {
        String message = annotation.failureMessage().isEmpty() ? reason : reason + ": " + annotation.failureMessage();

        switch (annotation.onFailure()) {
            case IGNORE: FermiumBooterPlugin.LOGGER.debug("Disabled mixin json {}. {}", mixinJson, message); break;
            case WARN: FermiumBooterPlugin.LOGGER.warn("Disabled mixin json {}. {}", mixinJson, message); break;
            case ERROR:
                FermiumBooterPlugin.LOGGER.error("Mixin json {}, conditions aren't met, crashing deliberately: {}", mixinJson, message);
                throw new Error(message);
        }
    }
}