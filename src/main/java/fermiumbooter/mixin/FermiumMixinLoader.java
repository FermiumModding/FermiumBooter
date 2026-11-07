package fermiumbooter.mixin;

import fermiumbooter.FermiumPlugin;
import fermiumbooter.FermiumRegistryAPI;
import fermiumbooter.util.CustomLogger;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Loads non-vanilla and non-coremod mixins late in order to prevent ClassNotFound exceptions
 * Code based on original MIT Licensed code:
 * https://github.com/DimensionalDevelopment/JustEnoughIDs/blob/master/src/main/java/org/dimdev/jeid/mixin/init/JEIDMixinLoader.java
 */
@Mixin(Loader.class)
public abstract class FermiumMixinLoader {

    @Shadow(remap = false)
    private List<ModContainer> mods;

    @Shadow(remap = false)
    private ModClassLoader modClassLoader;

    /**
     * @reason Load all mods now and load mod support mixin configs. This can't be done later
     * since constructing mods loads classes from them.
     */
    @Inject(method = "loadMods", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/LoadController;transition(Lnet/minecraftforge/fml/common/LoaderState;Z)V", ordinal = 1), remap = false)
    private void beforeConstructingMods(List<String> nonMod, CallbackInfo ci) {
        for(ModContainer mod : mods) {
            try {
                modClassLoader.addFile(mod.getSource());
            }
            catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        //Start FermiumBooter section

        for(Map.Entry<String, List<Supplier<Boolean>>> entry : FermiumRegistryAPI.getLateMixins().entrySet()) {
            //Check for removals
            if(FermiumRegistryAPI.getRejectMixins().contains(entry.getKey())) {
                FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter received removal of \"{}\" for late mixin application, rejecting.", entry.getKey());
                continue;
            }
            //Check for enabled
            boolean enabled = false;
            for(Supplier<Boolean> supplier : entry.getValue()) {
                Boolean supplied = supplier.get();
                if(supplied == null) {
                    FermiumPlugin.LOGGER.log(Level.WARN, "FermiumBooter received null value from late application supplier for \"{}\".", entry.getKey());
                }
                else enabled |= supplied;
            }
            //Add configuration
            if(enabled) {
                CustomLogger.info(FermiumPlugin.LOGGER, "FermiumBooter adding \"{}\" for late mixin application.", entry.getKey());
                Mixins.addConfiguration(entry.getKey());
            }
        }

        //Force clear the maps
        FermiumRegistryAPI.clear();

        //End FermiumBooter section

        try {
            Class<?> proxyClass = Class.forName("org.spongepowered.asm.mixin.transformer.Proxy");
            Field transformerField = proxyClass.getDeclaredField("transformer");
            transformerField.setAccessible(true);
            Object transformer = transformerField.get(null);

            Class<?> mixinTransformerClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
            Field processorField = mixinTransformerClass.getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);

            Class<?> mixinProcessorClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinProcessor");

            Field extensionsField = mixinProcessorClass.getDeclaredField("extensions");
            extensionsField.setAccessible(true);
            Object extensions = extensionsField.get(processor);

            Method selectConfigsMethod = mixinProcessorClass.getDeclaredMethod("selectConfigs", MixinEnvironment.class);
            selectConfigsMethod.setAccessible(true);
            selectConfigsMethod.invoke(processor, MixinEnvironment.getCurrentEnvironment());

            // Mixin 0.8.4+
            try {
                Method prepareConfigs = mixinProcessorClass.getDeclaredMethod("prepareConfigs", MixinEnvironment.class, Extensions.class);
                prepareConfigs.setAccessible(true);
                prepareConfigs.invoke(processor, MixinEnvironment.getCurrentEnvironment(), extensions);
                return;
            }
            catch (NoSuchMethodException ex) {
                // no-op
            }

            // Mixin 0.8+
            try {
                Method prepareConfigs = mixinProcessorClass.getDeclaredMethod("prepareConfigs", MixinEnvironment.class);
                prepareConfigs.setAccessible(true);
                prepareConfigs.invoke(processor, MixinEnvironment.getCurrentEnvironment());
                return;
            }
            catch (NoSuchMethodException ex) {
                // no-op
            }

            throw new UnsupportedOperationException("Unsupported Mixin");
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}