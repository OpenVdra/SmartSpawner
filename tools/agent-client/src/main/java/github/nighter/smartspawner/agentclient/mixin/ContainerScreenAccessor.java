package github.nighter.smartspawner.agentclient.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Where a container screen drew its panel, so a slot's own position (relative to that panel) can be
 * turned into a point on the screen for the pointer to hover.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int agent$leftPos();

    @Accessor("topPos")
    int agent$topPos();
}
