package fermiumbooter.util;

import fermiumbooter.FermiumPlugin;
import fermiumbooter.FermiumRegistryAPI;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public abstract class ForcedConfigHandler {
	
	private static int removedMixinConfigCount = 0;
	
	public static void handleForcedMixinConfigs() {
		FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter beginning forced mixin config handling.");
		parseForcedMixinConfig();
		FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter finished forced mixin config handling, removed {} mixin configs.", removedMixinConfigCount);
	}
	
	private static void parseForcedMixinConfig() {
		//Read config file
		File configFile = new File("config", "fermiumbooter.cfg");
		List<String> forcedRemoves = new ArrayList<>();
		if(configFile.exists() && configFile.isFile()) {
			try(Stream<String> stream = Files.lines(configFile.toPath())) {
				//Gross but im lazy
				final boolean[] parsingRemove = {false};
				stream.forEachOrdered(s -> {
					//weee
					String st = s.trim();
					if(!st.isEmpty()) {
						if(parsingRemove[0]) {
							if(st.contains(".json")) forcedRemoves.add(st);
							else parsingRemove[0] = false;
						}
						else {
							if(st.contains("S:\"Forced Early Mixin Config Removals\"")) parsingRemove[0] = true;
						}
					}
				});
			}
			catch(Exception ex) {
				FermiumPlugin.LOGGER.log(Level.ERROR, "FermiumBooter failed to read FermiumBooter config:", ex);
			}
		}
		else {
			FermiumPlugin.LOGGER.log(Level.INFO, "FermiumBooter config missing, assuming first launch.");
		}
		
		for(String remove : forcedRemoves) {
			removedMixinConfigCount++;
			FermiumRegistryAPI.removeMixin(remove);
		}
	}
}