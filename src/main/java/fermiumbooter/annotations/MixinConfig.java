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
 * name: Config file name, this should match the name you provide to @Config, or the modid if name is not provided
 * classloadEarly: If set to true, will load this class after scanning it, running its static{} block. Use this for more complex enqueues if you don't want to use a coremod
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MixinConfig {
	
	String name();

	boolean classloadEarly() default false;
	
	/**
	 * Annotates a boolean field annotated with @Config.Name that toggles if mixins should attempt to be loaded
	 * mixinJson: Name of the mixin json to be loaded. Can target any class, vanilla or modded.
	 * earlyMixin: Deprecated since 1.6.0, now an alias for mixinJson
	 * lateMixin: Deprecated since 1.6.0, mixin json to be loaded late, so right before mods are constructed
	 * defaultValue: If the mixin should attempt to be loaded by default (First launch)
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@interface MixinToggle {
		String mixinJson() default "";
		@Deprecated String earlyMixin() default "";
		@Deprecated String lateMixin() default "";
		boolean defaultValue();
	}
	
	/**
	 * Provides additional modID-based compatibility handling for fields annotated by @MixinToggle
	 * If checks fail a specific warning will be logged and a general warning will display on screen
	 * modid: ModID of the target mod
	 * modName: Additionally check for specific name of the target mod, to distinguish between forks
	 * targetVersionRange: Additionally check for version of target mod to be in the given range, using the same system as @Mod(dependencies=...)
	 * desired: Whether the target mod is desired - true is treated as a dependency, false is treated as an incompatibility
	 * disableMixin: If the check fails, true will disable the annotated mixin(s), false will only log a warning
	 * warnIngame: If the check fails, true will render a warning ingame, false will only log a warning
	 * reason: Reasoning to print to the log to give additional context to the relation if the check fails
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	@Repeatable(CompatHandlingContainer.class)
	@interface CompatHandling {
		String modid();
		String modName() default "";
		String targetVersionRange() default "";
		boolean desired();
		boolean disableMixin() default true;
		boolean warnIngame() default true;
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