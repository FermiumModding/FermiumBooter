package fermiumbooter.api.config;

import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

public interface IConfig {
    /**
     * Runs only once during startup
     */
    default void onCfgLoad(ModConfigEvent.Loading event, Config callingCfg){}

    /**
     * Runs twice per Cfg Reload, once before the registered setters run, and once after
     * Note that for some reason every saved cfg change produces TWO cfg reload events, both after having already written to file
     * so this is effectively ran 4 times: before, after, before, after
     */
    default void onCfgReload(ModConfigEvent.Reloading event, Config callingCfg, boolean postSet){}

    default void onSetup(FMLCommonSetupEvent event, Config callingCfg){}
    default void onLoadComplete(FMLLoadCompleteEvent event, Config callingCfg){}
}
