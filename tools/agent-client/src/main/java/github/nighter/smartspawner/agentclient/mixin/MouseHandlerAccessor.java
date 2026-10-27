package github.nighter.smartspawner.agentclient.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes where the game believes the mouse is, for the virtual pointer: screens read their hover
 * position out of these two fields. They are in window pixels. See
 * {@link github.nighter.smartspawner.agentclient.Pointer}.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void agent$setXpos(double x);

    @Accessor("ypos")
    void agent$setYpos(double y);
}
