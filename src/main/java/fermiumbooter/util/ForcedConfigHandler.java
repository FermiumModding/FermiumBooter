package fermiumbooter.util;

import fermiumbooter.FermiumPlugin;
import fermiumbooter.FermiumRegistryAPI;
import org.apache.logging.log4j.Level;

import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class ForcedConfigHandler {
	
	private static int removedMixinConfigCount = 0;
	
	public static void handleForcedMixinConfigs() {
		FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter beginning forced mixin config handling.");
		parseForcedMixinConfig();
		FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter finished forced mixin config handling, removed {} mixin configs.", removedMixinConfigCount);
	}
	
	private static void parseForcedMixinConfig() {
		//Read config file
		String[] forcedRemovals = FermiumPlugin.CONFIG.get("general", "Forced Early Mixin Config Removals", new String[0]).getStringList();
		if(forcedRemovals == null) {
			FermiumPlugin.LOGGER.log(Level.ERROR, "FermiumBooter failed to read FermiumBooter config");
			return;
		}

		for(String remove : Arrays.stream(forcedRemovals).filter(s -> s.endsWith(".json")).collect(Collectors.toList())) {
			removedMixinConfigCount++;
			FermiumRegistryAPI.removeMixin(remove);
		}
	}
}