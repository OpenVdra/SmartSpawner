package github.nighter.smartspawner.agentclient;

import net.minecraft.client.Minecraft;

/**
 * What a client that is driven rather than played does with its window and the mouse pointer.
 *
 * <ul>
 *   <li><b>The pointer.</b> Minecraft takes the cursor as soon as the window is focused with a world
 *       open. A driven client never needs it, since every command goes through the game, so
 *       {@code MouseGrabMixin} refuses the grab and the desktop around the window stays usable.</li>
 *   <li><b>The window size.</b> Maximised, so a capture is big enough to crop from. Not full screen,
 *       which would take the whole display from the person working beside it.</li>
 *   <li><b>Pausing.</b> An unfocused window opens the pause menu, which would sit over every capture.</li>
 * </ul>
 */
public final class ClientWindow {
    /** Read once from a system property, because {@code MouseGrabMixin} asks before the mod initializes. */
    private static final boolean DRIVEN =
            Boolean.parseBoolean(System.getProperty("smartspawner.agent.driven", "false"))
                    || System.getProperty("smartspawner.agent.script") != null;

    private ClientWindow() { }

    public static boolean driven() {
        return DRIVEN;
    }

    /** Maximises the window and gives the pointer back, once, on the first client tick. */
    static void open(Minecraft minecraft) {
        if (!DRIVEN) return;
        minecraft.options.pauseOnLostFocus = false;
        // A fresh game directory opens the accessibility prompt first, which swallows the
        // --quickPlayMultiplayer join; it only ever needs answering once.
        if (minecraft.options.onboardAccessibility) {
            minecraft.options.onboardAccessibility = false;
            minecraft.options.save();
        }
        org.lwjgl.glfw.GLFW.glfwMaximizeWindow(minecraft.getWindow().handle());
        if (minecraft.mouseHandler.isMouseGrabbed()) minecraft.mouseHandler.releaseMouse();
    }

    /**
     * Puts what the agent is doing in the window title once a second, so a long wait does not look
     * like a hung game. The title rather than the screen, so nothing of it reaches a capture.
     */
    static void showStatus(Minecraft minecraft, String status, long tick) {
        if (!DRIVEN || status == null || tick % 20 != 0) return;
        minecraft.getWindow().setTitle("SmartSpawner agent: " + status);
    }
}
