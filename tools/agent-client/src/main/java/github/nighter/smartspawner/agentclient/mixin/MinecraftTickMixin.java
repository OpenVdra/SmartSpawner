package github.nighter.smartspawner.agentclient.mixin;

import github.nighter.smartspawner.agentclient.AgentClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client tick the queue is polled on. A mixin rather than Fabric API's tick event, so the mod
 * needs nothing on the classpath but the loader: it runs beside a vanilla server, not a modded one.
 */
@Mixin(Minecraft.class)
abstract class MinecraftTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void agent$tick(CallbackInfo info) {
        AgentClient.tick((Minecraft) (Object) this);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void agent$stopping(CallbackInfo info) {
        AgentClient.flush();
    }
}
