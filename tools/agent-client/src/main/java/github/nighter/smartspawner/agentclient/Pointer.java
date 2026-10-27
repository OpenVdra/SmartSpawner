package github.nighter.smartspawner.agentclient;

import github.nighter.smartspawner.agentclient.mixin.MouseHandlerAccessor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * A virtual mouse pointer: a position in GUI pixels the game is told the mouse is at, so a screen
 * draws its hover states and tooltips under it exactly as under a real mouse. The framebuffer never
 * holds a cursor, so a capture shows the hover and not the pointer.
 *
 * <p>While it is shown the real mouse is ignored ({@code MouseMoveMixin}), and the position is written
 * again every tick, because opening a screen may centre the game's idea of the mouse.
 */
public final class Pointer {
    private static boolean shown;
    private static double x;
    private static double y;

    private Pointer() { }

    public static boolean shown() {
        return shown;
    }

    static double x() {
        return x;
    }

    static double y() {
        return y;
    }

    static void place(Minecraft minecraft, double guiX, double guiY) {
        shown = true;
        x = guiX;
        y = guiY;
        apply(minecraft);
    }

    static void hide() {
        shown = false;
    }

    static void tick(Minecraft minecraft) {
        if (shown) apply(minecraft);
    }

    /** The mouse handler's fields are in window pixels, which are not GUI pixels. */
    private static void apply(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
        mouse.agent$setXpos(x * window.getScreenWidth() / window.getGuiScaledWidth());
        mouse.agent$setYpos(y * window.getScreenHeight() / window.getGuiScaledHeight());
    }
}
