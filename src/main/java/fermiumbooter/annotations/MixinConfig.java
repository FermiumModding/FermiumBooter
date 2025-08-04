package fermiumbooter.annotations;

import java.lang.annotation.*;

/**
 * Utility annotation system for dynamically registering and enabling mixins based on Forge's @Config annotations
 * This is specifically designed for configs using the Forge @Config annotation system
 * This was primarily made for my own use and sanity but provided for others to use
 * Please report any issues to the Github
 *
 * Any classes containing @MixinToggle fields must also annotate the class with @MixinConfig, including inner classes
 *
 * configName: Config file name, this should match the name you provide to @Config, or the modid if name is not provided
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MixinConfig {
	
	String name();
	
	/**
	 * Annotates a boolean field annotated with @Config.Name that toggles if mixins should attempt to be loaded
	 * Early loading is for mixins targeting Vanilla/Core-mod classes
	 * Late loading is for mixins targeting mod classes that are loaded too late for normal mixins to target
	 * earlyMixin: Name of the mixin configuration file (Json) to be loaded early (Empty for none, must be either or both)
	 * lateMixin: Name of the mixin configuration file (Json) to be loaded late (Empty for none, must be either or both)
	 * defaultValue: If the mixin should attempt to be loaded by default (First launch)
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface MixinToggle {
		String earlyMixin() default "";
		String lateMixin() default "";
		boolean defaultValue();
	}
	
	/**
	 * Provides additional modID-based compatibility handling for fields annotated by @MixinToggle
	 * If checks fail a specific warning will be logged and a general warning will display on screen
	 * modid: ModID of the target mod
	 * desired: If the target mod is desired, true is treated as a dependency, false is treated as an incompatibility
	 * disableMixin: If the check fails, true will disable the annotated mixin(s), false will only log a warning
	 * reason: Reasoning to print to the log to give additional context to the relation if the check fails
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@Repeatable(CompatHandlingContainer.class)
	@interface CompatHandling {
		String modid();
		boolean desired();
		boolean disableMixin() default true;
		String reason() default "Undefined";
	}
	
	/**
	 * Internal use only
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface CompatHandlingContainer {
		CompatHandling[] value();
	}
}