package fermiumbooter.api.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This class is an api to make 1.20 Forge configs feel like 1.12.2 @Config annotated configs
 * As Forge upgraded its configuration system capabilities, this now too has increased capabilities
 * - you can set transformation rules for how the read primitive data is going to be represented for the game
 * - there might be more that i'm not seeing yet TODO
 *
 * You need to modEventBus.register(Config.class) during mod '<init>' or setup
 * This ensures that all your ingame changes to configs are automatically synced to file
 */
public abstract class Config implements IConfig {

    private static final List<Config> modCfgs = new ArrayList<>();

    public final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private final Map<String, ForgeConfigSpec.ConfigValue<?>> CFG_FIELDS = new HashMap<>();
    private final Map<String, Runnable> CFG_SETTERS = new HashMap<>();
    private final List<IConfig> CATEGORIES = new ArrayList<>(); //These are only the wired categories, for internal use

    public Config(){
        modCfgs.add(this);
    }

    private ForgeConfigSpec SPEC;
    public ForgeConfigSpec getSpec(){
        if(SPEC == null) SPEC = BUILDER.build();
        return SPEC;
    }

    protected  <T> void registerCfg(Supplier<ForgeConfigSpec.ConfigValue<T>> builder, Consumer<T> setter) {
        ForgeConfigSpec.ConfigValue<T> internalConfigField = builder.get(); //creates the config entry
        String fullPath = String.join(".", internalConfigField.getPath());

        CFG_SETTERS.put(fullPath, () -> setter.accept(internalConfigField.get())); //will set the chosen variable on every cfg change
        CFG_FIELDS.put(fullPath, internalConfigField);
    }

    public void registerCategory(String categoryName, String[] comment, List<Runnable> cfgRegistrations, List<Runnable> subCategoryRegistrations) {
        BUILDER.comment(comment).push(categoryName);
        for (Runnable cfgRegistration : cfgRegistrations)
            cfgRegistration.run();
        //we do these separately as they are put into the config separately anyway, categories below fields
        for (Runnable subCatRegistration : subCategoryRegistrations)
            subCatRegistration.run();
        BUILDER.pop();
    }

    public void registerCategory(IConfig cfgObj, String categoryName, String[] comment, List<Runnable> cfgRegistrations, List<Runnable> subCategoryRegistrations) {
        CATEGORIES.add(cfgObj);
        registerCategory(categoryName, comment, cfgRegistrations, subCategoryRegistrations);
    }

    /**
     * This allows to set a config value from the code side. It will update the code side of the config, the file, and potentially ingame cfg guis
     */
    @SuppressWarnings("unchecked")
    public <T> void setValue(String key, T newVal){
        if (!CFG_FIELDS.containsKey(key)) throw new RuntimeException("Config doesn't contain config key: " + key);
        ForgeConfigSpec.ConfigValue<?> field = CFG_FIELDS.get(key);
        ((ForgeConfigSpec.ConfigValue<T>) field).set(newVal); //this sets it for ingame guis and for the file. Unsafe, this will crash if the type isn't correct
        CFG_SETTERS.get(key).run(); // usually just doing cfg.testCfg = newVal but using the registered setter
    }

    @SubscribeEvent
    public static void configLoad(ModConfigEvent.Loading event) {
        modCfgs.forEach(cfg -> cfg.onCfgLoad(event, null));
    }

    @SubscribeEvent
    public static void configReload(ModConfigEvent.Reloading event) {
        modCfgs.forEach(cfg -> {
            cfg.onCfgReload(event, null, false);
            cfg.CFG_SETTERS.values().forEach(Runnable::run);
            cfg.onCfgReload(event, null, true);
        });
    }

    @SubscribeEvent
    public static void setup(final FMLCommonSetupEvent event) {
        modCfgs.forEach(cfg -> cfg.onSetup(event, null));
    }

    @SubscribeEvent
    public static void loadComplete(final FMLLoadCompleteEvent event) {
        modCfgs.forEach(cfg -> cfg.onLoadComplete(event, null));
    }

    // ------ Wiring for IConfig ------

    @Override
    public void onCfgLoad(ModConfigEvent.Loading event, Config callingCfg){
        CATEGORIES.forEach(cat -> cat.onCfgLoad(event, callingCfg != null ? callingCfg : this));
    }
    @Override
    public void onCfgReload(ModConfigEvent.Reloading event, Config callingCfg, boolean postSet){
        CATEGORIES.forEach(cat -> cat.onCfgReload(event, callingCfg != null ? callingCfg : this, postSet));
    }
    @Override
    public void onSetup(FMLCommonSetupEvent event, Config callingCfg){
        CATEGORIES.forEach(cat -> cat.onSetup(event, callingCfg != null ? callingCfg : this));
    }
    @Override
    public void onLoadComplete(FMLLoadCompleteEvent event, Config callingCfg){
        CATEGORIES.forEach(cat -> cat.onLoadComplete(event, callingCfg != null ? callingCfg : this));
    }
}