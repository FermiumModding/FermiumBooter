package fermiumbooter.util;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Gross
public class ASMClassVisitor extends ClassVisitor {
	
	//Lazy hardcore types
	private final static Type modType = Type.getType("Lnet/minecraftforge/fml/common/Mod;");
	private final static Type configNameType = Type.getType("Lnet/minecraftforge/common/config/Config$Name;");
	private final static Type mixinConfigType = Type.getType("Lfermiumbooter/annotations/MixinConfig;");
	private final static Type mixinToggleType = Type.getType("Lfermiumbooter/annotations/MixinConfig$MixinToggle;");
	private final static Type compatHandlingType = Type.getType("Lfermiumbooter/annotations/MixinConfig$CompatHandling;");
	private final static Type compatHandlingContainerType = Type.getType("Lfermiumbooter/annotations/MixinConfig$CompatHandlingContainer;");
	
	//Cached visitors
	private ASMAnnotationVisitor modAnnoVisitor = null;
	private ASMAnnotationVisitor mixinConfigAnnoVisitor = null;
	private final List<ASMFieldVisitor> fieldVisitors = new ArrayList<>();
	private boolean parseFields = false;
	
	//Parsed values
	public String modid = null;
	public String mixinConfigName = null;
	public final List<ASMFieldVisitor> parsedFieldVisitors = new ArrayList<>();
	
	public ASMClassVisitor() {
		super(Opcodes.ASM5);
	}
	
	//Class annotation visitation
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		Type type = Type.getType(desc);
		//Only bother parsing relevant annotations
		if(type.equals(modType)) {
			this.modAnnoVisitor = new ASMAnnotationVisitor();
			return this.modAnnoVisitor;
		}
		else if(type.equals(mixinConfigType)) {
			this.mixinConfigAnnoVisitor = new ASMAnnotationVisitor();
			//Only bother fully parsing fields if mixinconfig exists
			this.parseFields = true;
			return this.mixinConfigAnnoVisitor;
		}
		else return null;
	}
	
	//Field visitation
	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		if(!this.parseFields) return null;
		
		Type type = Type.getType(desc);
		//Only bother parsing relevant fields
		if(type.equals(Type.BOOLEAN_TYPE)) {
			ASMFieldVisitor booleanField = new ASMFieldVisitor();
			fieldVisitors.add(booleanField);
			return booleanField;
		}
		return null;
	}
	
	//End of visitation
	@Override
	public void visitEnd() {
		//Parse and validate
		if(this.modAnnoVisitor != null) {
			this.modid = (String)modAnnoVisitor.getValues().get("modid");
		}
		
		if(this.mixinConfigAnnoVisitor != null) {
			this.mixinConfigName = (String)this.mixinConfigAnnoVisitor.getValues().get("name");
			if(this.mixinConfigName == null || this.mixinConfigName.isEmpty()) return;
			
			for(ASMFieldVisitor fieldVisitor : this.fieldVisitors) {
				if(fieldVisitor.valid) {
					this.parsedFieldVisitors.add(fieldVisitor);
				}
			}
		}
	}
	
	//Annotation visitor
	public static class ASMAnnotationVisitor extends AnnotationVisitor {
		
		private final Map<String, Object> values = new HashMap<>();
		private List<ASMAnnotationVisitor> compatHandlingVisitors = null;
		
		public ASMAnnotationVisitor() {
			super(Opcodes.ASM5);
		}
		
		public ASMAnnotationVisitor(List<ASMAnnotationVisitor> compatHandlingVisitors) {
			super(Opcodes.ASM5);
			this.compatHandlingVisitors = compatHandlingVisitors;
		}
		
		public Map<String, Object> getValues() {
			return this.values;
		}
		
		//Annotation value visitation
		@Override
		public void visit(String name, Object value) {
			this.values.put(name, value);
		}
		
		@Override
		public AnnotationVisitor visitAnnotation(String name, String desc) {
			if(this.compatHandlingVisitors != null) {
				Type type = Type.getType(desc);
				if(type.equals(compatHandlingType)) {
					ASMAnnotationVisitor nestedCompatHandling = new ASMAnnotationVisitor();
					this.compatHandlingVisitors.add(nestedCompatHandling);
					return nestedCompatHandling;
				}
			}
			return null;
		}
		
		@Override
		public AnnotationVisitor visitArray(String name) {
			if(this.compatHandlingVisitors != null) {
				return new ASMAnnotationVisitor(this.compatHandlingVisitors);
			}
			return null;
		}
	}
	
	//Field visitor
	public static class ASMFieldVisitor extends FieldVisitor {
		
		private ASMAnnotationVisitor configNameVisitor = null;
		private ASMAnnotationVisitor mixinToggleVisitor = null;
		private final List<ASMAnnotationVisitor> compatHandlingVisitors = new ArrayList<>();
		
		public String configFieldName = null;
		public String earlyMixinName = null;
		public String lateMixinName = null;
		public boolean defaultValue = false;
		public final List<CompatHandlingAnnotation> compatHandlingAnnotations = new ArrayList<>();
		public boolean valid = false;
		
		public ASMFieldVisitor() {
			super(Opcodes.ASM5);
		}
		
		//Field annotation visitation
		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			Type type = Type.getType(desc);
			//Only bother parsing relevant annotations
			if(type.equals(configNameType)) {
				this.configNameVisitor = new ASMAnnotationVisitor();
				return this.configNameVisitor;
			}
			else if(type.equals(mixinToggleType)) {
				this.mixinToggleVisitor = new ASMAnnotationVisitor();
				return this.mixinToggleVisitor;
			}
			else if(type.equals(compatHandlingType)) {
				ASMAnnotationVisitor compatHandlingVisitor = new ASMAnnotationVisitor();
				this.compatHandlingVisitors.add(compatHandlingVisitor);
				return compatHandlingVisitor;
			}
			else if(type.equals(compatHandlingContainerType)) {
				ASMAnnotationVisitor compatHandlingVisitor = new ASMAnnotationVisitor(this.compatHandlingVisitors);
				this.compatHandlingVisitors.add(compatHandlingVisitor);
				return compatHandlingVisitor;
			}
			else return null;
		}
		
		//End of visitation
		@Override
		public void visitEnd() {
			//Parse visited annotations on field and determine validity
			if(this.configNameVisitor != null && this.mixinToggleVisitor != null) {
				this.configFieldName = (String)this.configNameVisitor.getValues().get("value");
				if(this.configFieldName == null || this.configFieldName.isEmpty()) return;
				this.earlyMixinName = (String)this.mixinToggleVisitor.getValues().get("earlyMixin");
				this.lateMixinName = (String)this.mixinToggleVisitor.getValues().get("lateMixin");
				if((this.earlyMixinName == null || this.earlyMixinName.isEmpty()) && (this.lateMixinName == null || this.lateMixinName.isEmpty())) return;
				Boolean defaultVal = (Boolean)this.mixinToggleVisitor.getValues().get("defaultValue");
				if(defaultVal == null) defaultVal = false;
				this.defaultValue = (boolean)defaultVal;
				
				this.valid = true;
				
				for(ASMAnnotationVisitor compatHandlingVisitor : this.compatHandlingVisitors) {
					String modid = (String)compatHandlingVisitor.getValues().get("modid");
					if(modid == null || modid.isEmpty()) continue;
					Boolean desired = (Boolean)compatHandlingVisitor.getValues().get("desired");
					if(desired == null) desired = true;
					Boolean disableMixin = (Boolean)compatHandlingVisitor.getValues().get("disableMixin");
					if(disableMixin == null) disableMixin = true;
					String reason = (String)compatHandlingVisitor.getValues().get("reason");
					if(reason == null) reason = "";
					
					this.compatHandlingAnnotations.add(new CompatHandlingAnnotation(modid, desired, disableMixin, reason));
				}
			}
		}
	}
	
	public static class CompatHandlingAnnotation {
		
		public final String modid;
		public final boolean desired;
		public final boolean disableMixin;
		public final String reason;
		
		public CompatHandlingAnnotation(String modid, boolean desired, boolean disableMixin, String reason) {
			this.modid = modid;
			this.desired = desired;
			this.disableMixin = disableMixin;
			this.reason = reason;
		}
	}
}