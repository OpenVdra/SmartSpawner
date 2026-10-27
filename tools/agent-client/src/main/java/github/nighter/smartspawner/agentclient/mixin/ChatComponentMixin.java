package github.nighter.smartspawner.agentclient.mixin;

import github.nighter.smartspawner.agentclient.Messages;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logs every chat line for {@code client.messages}. The private method all three public adds go through. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addMessage", at = @At("HEAD"))
    private void smartspawnerAgent$record(Component message, MessageSignature signature, GuiMessageSource source,
                                          GuiMessageTag tag, CallbackInfo info) {
        Messages.record("chat", message);
    }
}
