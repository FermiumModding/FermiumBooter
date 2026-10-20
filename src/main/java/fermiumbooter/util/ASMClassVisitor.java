package fermiumbooter.util;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Not gross, used by AsmJarScanner
public class ASMClassVisitor extends ClassVisitor {

	// Have you seen these annotations?
	private static final String modDesc = "Lnet/minecraftforge/fml/common/Mod;";
	private static final String configNameDesc = "Lnet/minecraftforge/common/config/Config$Name;";
	private static final String mixinConfigDesc = "Lfermiumbooter/annotations/MixinConfig;";
	private static final String mixinToggleDesc = "Lfermiumbooter/annotations/MixinConfig$MixinToggle;";
	private static final String compatHandlingDesc = "Lfermiumbooter/annotations/MixinConfig$CompatHandling;";
	private static final String compatHandlingContainerDesc = "Lfermiumbooter/annotations/MixinConfig$CompatHandlingContainer;";

	//Highest ASM api the runtime supports
	private static final int ASM_API = findAsmApi();
	
	private static int findAsmApi() {
		for(String api : new String[]{"ASM9", "ASM8", "ASM7", "ASM6"}) {
			try {
				return Opcodes.class.getField(api).getInt(null);
			}
			catch(Exception ignored) { }
		}
		return Opcodes.ASM5;
	}
	
	//Cached visitors
	private ASMAnnotationVisitor modAnnoVisitor = null;
	private ASMAnnotationVisitor mixinConfigAnnoVisitor = null;
	private final List<ASMFieldVisitor> fieldVisitors = new ArrayList<>();

	//Parsed values
	public String className = null;
	public String modid = null;
	public String modName = null;
	public String modVersion = null;
	public String mixinConfigName = null;
	public boolean classloadEarly = false;
	public final List<MixinConfigInfo> mixinToggles = new ArrayList<>();

	public ASMClassVisitor() {
		super(ASM_API);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.className = name.replace('/', '.');
	}

	//Class annotation visitation
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		//Only bother parsing relevant annotations
		if(modDesc.equals(desc)) {
			this.modAnnoVisitor = new ASMAnnotationVisitor();
			return this.modAnnoVisitor;
		}
		else if(mixinConfigDesc.equals(desc)) {
			this.mixinConfigAnnoVisitor = new ASMAnnotationVisitor();
			return this.mixinConfigAnnoVisitor;
		}
		else return null;
	}

	//Field visitation
	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		//Class annotations are visited before fields, only bother parsing fields if mixinconfig exists
		if(this.mixinConfigAnnoVisitor == null) return null;

		ASMFieldVisitor fieldVisitor = new ASMFieldVisitor(name);
		this.fieldVisitors.add(fieldVisitor);
		return fieldVisitor;
	}

	//End of visitation
	@Override
	public void visitEnd() {
		if(this.modAnnoVisitor != null) {
			this.modid = this.modAnnoVisitor.getString("modid");
			this.modName = this.modAnnoVisitor.getString("name");
			this.modVersion = this.modAnnoVisitor.getString("version");
		}

		if(this.mixinConfigAnnoVisitor != null) {
			this.mixinConfigName = this.mixinConfigAnnoVisitor.getString("name");
			if(this.mixinConfigName == null) return;
			this.classloadEarly = this.mixinConfigAnnoVisitor.getBoolean("classloadEarly", false);

			for(ASMFieldVisitor fieldVisitor : this.fieldVisitors) {
				MixinConfigInfo configInfo = fieldVisitor.toMixinConfigInfo(this.mixinConfigName);
				if(configInfo != null) this.mixinToggles.add(configInfo);
			}
		}
	}

	//Annotation visitor
	public static class ASMAnnotationVisitor extends AnnotationVisitor {

		private final Map<String, Object> values = new HashMap<>();
		//Shared with array visitors so repeated @CompatHandling inside the container end up in the same list
		private final List<ASMAnnotationVisitor> compatHandlingVisitors;

		public ASMAnnotationVisitor() {
			this(new ArrayList<>());
		}

		public ASMAnnotationVisitor(List<ASMAnnotationVisitor> compatHandlingVisitors) {
			super(ASM_API);
			this.compatHandlingVisitors = compatHandlingVisitors;
		}

		public String getString(String key) {
			Object value = this.values.get(key);
			if(!(value instanceof String) || ((String)value).isEmpty()) return null;
			return (String)value;
		}

		public boolean getBoolean(String key, boolean defaultValue) {
			Object value = this.values.get(key);
			return value instanceof Boolean ? (Boolean)value : defaultValue;
		}

		//Annotation value visitation
		@Override
		public void visit(String name, Object value) {
			this.values.put(name, value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String desc) {
			if(compatHandlingDesc.equals(desc)) {
				ASMAnnotationVisitor nestedCompatHandling = new ASMAnnotationVisitor();
				this.compatHandlingVisitors.add(nestedCompatHandling);
				return nestedCompatHandling;
			}
			return null;
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			return new ASMAnnotationVisitor(this.compatHandlingVisitors);
		}
	}

	//Field visitor
	public static class ASMFieldVisitor extends FieldVisitor {

		private final String fieldName;
		private ASMAnnotationVisitor configNameVisitor = null;
		private ASMAnnotationVisitor mixinToggleVisitor = null;
		private final List<ASMAnnotationVisitor> compatHandlingVisitors = new ArrayList<>();

		public ASMFieldVisitor(String fieldName) {
			super(ASM_API);
			this.fieldName = fieldName;
		}

		//Field annotation visitation
		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			//Only bother parsing relevant annotations
			if(configNameDesc.equals(desc)) {
				this.configNameVisitor = new ASMAnnotationVisitor();
				return this.configNameVisitor;
			}
			else if(mixinToggleDesc.equals(desc)) {
				this.mixinToggleVisitor = new ASMAnnotationVisitor();
				return this.mixinToggleVisitor;
			}
			else if(compatHandlingDesc.equals(desc)) {
				ASMAnnotationVisitor compatHandlingVisitor = new ASMAnnotationVisitor();
				this.compatHandlingVisitors.add(compatHandlingVisitor);
				return compatHandlingVisitor;
			}
			else if(compatHandlingContainerDesc.equals(desc)) {
				return new ASMAnnotationVisitor(this.compatHandlingVisitors);
			}
			else return null;
		}

		public MixinConfigInfo toMixinConfigInfo(String mixinConfigName) {
			if(this.mixinToggleVisitor == null) return null;

			//use @Config.Name param for fieldName if present
			String name = this.fieldName;
			if(this.configNameVisitor != null) {
				String configName = this.configNameVisitor.getString("value");
				if(configName != null) name = configName;
			}

			MixinConfigInfo configInfo = new MixinConfigInfo(mixinConfigName, name,
					this.mixinToggleVisitor.getString("mixinJson"),
					this.mixinToggleVisitor.getString("earlyMixin"),
					this.mixinToggleVisitor.getString("lateMixin"),
					this.mixinToggleVisitor.getBoolean("defaultValue", false));

			for(ASMAnnotationVisitor compatHandlingVisitor : this.compatHandlingVisitors) {
				String modid = compatHandlingVisitor.getString("modid");
				if(modid == null) continue;
				String reason = compatHandlingVisitor.getString("reason");
				configInfo.compatInfos.add(new MixinConfigInfo.CompatInfo(
						modid,
						compatHandlingVisitor.getString("modName"),
						compatHandlingVisitor.getString("targetVersionRange"),
						compatHandlingVisitor.getBoolean("desired", true),
						compatHandlingVisitor.getBoolean("disableMixin", true),
						compatHandlingVisitor.getBoolean("warnIngame", true),
						reason == null ? "Undefined" : reason));
			}
			return configInfo;
		}
	}
}
