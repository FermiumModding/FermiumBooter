package fermiumbooter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fermiumbooter.config.FermiumBooterConfig;
import fermiumbooter.util.CustomLogger;
import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrashReport.class)
public abstract class CrashReportAppender {
	
	@WrapOperation(
			method = "getCompleteReport",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/crash/CrashReport;getCauseStackTraceOrString()Ljava/lang/String;")
	)
	private String fermiumBooter_vanillaCrashReport_getCompleteReport(CrashReport instance, Operation<String> original) {
		String value = original.call(instance);
		if(FermiumBooterConfig.appendGeneralMixinExceptionsToCrashReports) {
			synchronized(CustomLogger.mixinErrors) {
				if(!CustomLogger.mixinErrors.isEmpty()) {
					StringBuilder builder = new StringBuilder();
					builder.append(value);
					builder.append("\n\n");
					for(String error : CustomLogger.mixinErrors) {
						builder.append(error);
					}
					value = builder.toString();
				}
			}
		}
		return value;
	}
}