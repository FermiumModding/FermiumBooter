package fermiumbooter;

import fermiumbooter.util.TransformerBlocker;

/**
 * Allows for external IClassTransformers to be blocked or modified from your IFMLLoadingPlugin class init
 * Includes blocking a defined IClassTransformer entirely from running, or blocking a defined IClassTransformer from
 * making changes to defined Classes while allowing the rest of the transformation to proceed
 * Warning: Test feature, use at own risk, will break things if not used carefully
 */
public abstract class FermiumTransformerAPI {
	
	/**
	 * Blocks a defined transformer from running entirely
	 * @param name the class name of the transformer to be blocked
	 */
	public static void blockTransformer(String name) {
		if(name != null && !name.isEmpty()) TransformerBlocker.blockTransformer(name);
	}
	
	/**
	 * Blocks a defined transformer from modifying defined classes, while allowing the rest of the transformer to run
	 * @param name the class name of the transformer to be partially blocked
	 * @param classes the class name(s) to prevent the transformer from modifying
	 */
	public static void blockTransformerFromClasses(String name, String... classes) {
		if(name != null && !name.isEmpty() && classes != null && classes.length > 0) TransformerBlocker.blockTransformerTargets(name, classes);
	}
}