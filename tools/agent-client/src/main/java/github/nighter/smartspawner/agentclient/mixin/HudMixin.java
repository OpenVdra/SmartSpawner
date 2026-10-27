package github.nighter.smartspawner.agentclient.mixin;

import github.nighter.smartspawner.agentclient.Messages;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logs the action bar, title and subtitle for {@code client.messages}, the moment they are set. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void smartspawnerAgent$actionBar(Component message, boolean animate, CallbackInfo info) {
        Messages.record("action_bar", message);
    }

    @Inject(method = "setTitle", at = @At("HEAD"))
    private void smartspawnerAgent$title(Component title, CallbackInfo info) {
        Messages.record("title", title);
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void smartspawnerAgent$subtitle(Component subtitle, CallbackInfo info) {
        Messages.record("subtitle", subtitle);
    }
}
