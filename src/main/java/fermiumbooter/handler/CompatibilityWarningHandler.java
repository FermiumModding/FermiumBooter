package fermiumbooter.handler;

import fermiumbooter.config.FermiumBooterConfig;
import fermiumbooter.util.CustomLogger;
import fermiumbooter.util.FermiumJarScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public abstract class CompatibilityWarningHandler {
	
	private static String warningMessage = null;
	private static int boxX, boxY, boxW, boxH;
	
	@SubscribeEvent
	public static void render(TickEvent.RenderTickEvent event) {
		if(FermiumBooterConfig.suppressMixinCompatibilityWarningsRender) return;
		if(event.phase != TickEvent.Phase.END) return;

		Minecraft minecraft = Minecraft.getMinecraft();
		if (!(minecraft.currentScreen instanceof GuiMainMenu)) return; //Only in main menu

		int warningCount = FermiumJarScanner.getWarningCount();
		if(warningCount <= 0) return;
		
		if(warningMessage == null) warningMessage = String.format("FermiumBooter found %d possible mixin compat errors, check your log or click here.", warningCount);

		FontRenderer fontRenderer = minecraft.fontRenderer;

		boxX = fontRenderer.FONT_HEIGHT;
		boxY = fontRenderer.FONT_HEIGHT;
		boxW = fontRenderer.getStringWidth(warningMessage) + 4;
		boxH = boxY + 2;

		GlStateManager.pushMatrix();
		Gui.drawRect(boxX, boxY, boxX + boxW, boxY+boxH, Integer.MIN_VALUE);
		fontRenderer.drawString(warningMessage, boxX + 2, boxY + 2, 0xFF5555);
		GlStateManager.popMatrix();
	}

	@SubscribeEvent
	public static void onClick(GuiScreenEvent.MouseInputEvent.Pre event) {
		if(FermiumBooterConfig.suppressMixinCompatibilityWarningsRender) return;

		Minecraft mc = Minecraft.getMinecraft();
		if (!(mc.currentScreen instanceof GuiMainMenu)) return;

		int warningCount = FermiumJarScanner.getWarningCount();
		if(warningCount <= 0) return;

		if (Mouse.getEventButtonState() && Mouse.getEventButton() == 0) { // left click
			int mouseX = Mouse.getEventX() * mc.currentScreen.width / mc.displayWidth;
			int mouseY = mc.currentScreen.height - Mouse.getEventY() * mc.currentScreen.height / mc.displayHeight - 1;

			if (isMouseInsideBox(mouseX, mouseY)) openLogFile();
		}
	}

	private static boolean isMouseInsideBox(int x, int y) {
		return x >= boxX && x <= boxX + boxW && y >= boxY && y <= boxY + boxH;
	}

	private static void openLogFile() {
		File file = CustomLogger.LOG_PATH.toFile();
		if (!file.exists()) return;

		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(file);
			}
		} catch (IOException e) {
			e.printStackTrace(System.out);
		}
	}
}