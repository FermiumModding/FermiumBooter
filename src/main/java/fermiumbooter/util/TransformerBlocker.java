package fermiumbooter.util;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

public abstract class TransformerBlocker {
	
	private static final Logger LOGGER = LogManager.getLogger("FermiumTransformerBlocker");
	
	private static FilteredList transformers = null;
	private static boolean attempted = false;
	private static final Set<String> blockedTransformers = new HashSet<>();
	private static final Map<String, Set<String>> blockedTransformerClasses = new HashMap<>();
	
	public static void blockTransformer(String name) {
		if(transformers == null) replaceField();
		if(transformers == null) {
			LOGGER.log(Level.WARN, "Skipping blocking {} as transformer blocking has not been setup", name);
			return;
		}
		LOGGER.log(Level.INFO, "Adding {} to blocked transformers", name);
		blockedTransformers.add(name);
		transformers.refilter();
	}
	
	public static void blockTransformerTargets(String name, String... classes) {
		if(transformers == null) replaceField();
		if(transformers == null) {
			LOGGER.log(Level.WARN, "Skipping blocking {} as transformer blocking has not been setup", name);
			return;
		}
		LOGGER.log(Level.INFO, "Adding {} for classes {} to blocked transformer targets", name, classes);
		Set<String> existing = blockedTransformerClasses.get(name);
		if(existing == null) existing = new HashSet<>();
		existing.addAll(Arrays.asList(classes));
		blockedTransformerClasses.put(name, existing);
		transformers.refilter();
	}
	
	private static void replaceField() {
		if(attempted) return;
		attempted = true;
		LaunchClassLoader loader = Launch.classLoader;
		try {
			Field field = LaunchClassLoader.class.getDeclaredField("transformers");
			field.setAccessible(true);
			List<IClassTransformer> original = (List<IClassTransformer>)field.get(loader);
			transformers = new FilteredList(original);
			field.set(loader, transformers);
		}
		catch(Exception ex) {
			LOGGER.log(Level.WARN, "Failed to setup transformer blocking");
		}
	}
	
	private static IClassTransformer passesFilter(IClassTransformer transformer) {
		if(transformer == null) return null;
		String name = transformer.getClass().getName();
		if(!(transformer instanceof FilteredTransformerWrapper)) {
			if(blockedTransformers.contains(name)) {
				LOGGER.log(Level.INFO, "Blocking transformer {}", name);
				return null;
			}
			else if(blockedTransformerClasses.containsKey(name)) {
				LOGGER.log(Level.INFO, "Blocking transformer {} from classes {}", name, blockedTransformerClasses.get(name));
				return new FilteredTransformerWrapper(transformer);
			}
		}
		return transformer;
	}
	
	private static class FilteredList extends ArrayList<IClassTransformer> {
		
		public FilteredList(Collection<? extends IClassTransformer> original) {
			super(original);
		}
		
		@Override
		public boolean add(IClassTransformer transformer) {
			IClassTransformer filteredTrans = passesFilter(transformer);
			if(filteredTrans != null) return super.add(filteredTrans);
			return false;
		}
		
		@Override
		public void add(int index, IClassTransformer transformer) {
			IClassTransformer filteredTrans = passesFilter(transformer);
			if(filteredTrans != null) super.add(index, filteredTrans);
		}
		
		@Override
		public boolean addAll(Collection<? extends IClassTransformer> collection) {
			boolean added = false;
			for(IClassTransformer transformer : collection) added |= add(transformer);
			return added;
		}
		
		public void refilter() {
			ListIterator<IClassTransformer> iterator = listIterator();
			while(iterator.hasNext()) {
				IClassTransformer filteredTrans = passesFilter(iterator.next());
				if(filteredTrans == null) iterator.remove();
				else iterator.set(filteredTrans);
			}
		}
	}
	
	private static class FilteredTransformerWrapper implements IClassTransformer {
		
		private final IClassTransformer original;
		private final String originalName;
		
		private FilteredTransformerWrapper(IClassTransformer original) {
			this.original = original;
			this.originalName = original.getClass().getName();
		}
		
		@Override
		public byte[] transform(String name, String transformedName, byte[] basicClass) {
			Set<String> classes = blockedTransformerClasses.get(originalName);
			if(classes != null && classes.contains(transformedName)) {
				LOGGER.log(Level.INFO, "Blocking tranformer {} from class {}", originalName, transformedName);
				return basicClass;
			}
			return original.transform(name, transformedName, basicClass);
		}
	}
}
