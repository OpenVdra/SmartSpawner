package github.nighter.smartspawner.agentclient.mixin;

import github.nighter.smartspawner.agentclient.Pointer;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ignores the real mouse while the virtual pointer is shown, so a hand passing over the window does
 * not move the hover a capture is meant to show.
 */
@Mixin(MouseHandler.class)
abstract class MouseMoveMixin {
    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void agent$followVirtualPointer(CallbackInfo info) {
        if (Pointer.shown()) info.cancel();
    }
}
