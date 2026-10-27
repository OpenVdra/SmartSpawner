package github.nighter.smartspawner.agentclient.mixin;

import github.nighter.smartspawner.agentclient.ClientWindow;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the mouse pointer alone on a driven client, so the desktop around the window stays usable.
 * See {@link ClientWindow}.
 */
@Mixin(MouseHandler.class)
abstract class MouseGrabMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void agent$keepPointerFree(CallbackInfo info) {
        if (ClientWindow.driven()) info.cancel();
    }
}
