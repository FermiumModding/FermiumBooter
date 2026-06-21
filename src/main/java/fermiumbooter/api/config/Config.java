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
    public final Map<String, ForgeConfigSpec.ConfigValue<?>> CFG_FIELDS = new HashMap<>(); //These are only needed if you also want to WRITE into the config file dynamically
    private final List<Runnable> CFG_SETTERS = new ArrayList<>();
    public final List<IConfig> CATEGORIES = new ArrayList<>();

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
        CFG_SETTERS.add(() -> setter.accept(internalConfigField.get())); //will set the chosen variable on every cfg change

        String fullPath = String.join(".", internalConfigField.getPath());
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

    @SubscribeEvent
    public static void configLoad(ModConfigEvent.Loading event) {
        modCfgs.forEach(cfg -> cfg.onCfgLoad(event));
    }

    @SubscribeEvent
    public static void configReload(ModConfigEvent.Reloading event) {
        modCfgs.forEach(cfg -> {
            cfg.onCfgReload(event, false);
            cfg.CFG_SETTERS.forEach(Runnable::run);
            cfg.onCfgReload(event, true);
        });
    }

    @SubscribeEvent
    public static void setup(final FMLCommonSetupEvent event) {
        modCfgs.forEach(cfg -> cfg.onSetup(event));
    }

    @SubscribeEvent
    public static void loadComplete(final FMLLoadCompleteEvent event) {
        modCfgs.forEach(cfg -> cfg.onLoadComplete(event));
    }

    // ------ Wiring for IConfig ------

    @Override
    public void onCfgLoad(ModConfigEvent.Loading event){
        CATEGORIES.forEach(cat -> cat.onCfgLoad(event));
    }
    @Override
    public void onCfgReload(ModConfigEvent.Reloading event, boolean postSet){
        CATEGORIES.forEach(cat -> cat.onCfgReload(event, postSet));
    }
    @Override
    public void onSetup(FMLCommonSetupEvent event){
        CATEGORIES.forEach(cat -> cat.onSetup(event));
    }
    @Override
    public void onLoadComplete(FMLLoadCompleteEvent event){
        CATEGORIES.forEach(cat -> cat.onLoadComplete(event));
    }
}