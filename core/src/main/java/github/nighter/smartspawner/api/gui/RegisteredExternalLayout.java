package github.nighter.smartspawner.api.gui;

import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;

/**
 * Internal data holder for externally registered layouts.
 * Stores the three GUI layouts (main, storage, sell confirm) for a named layout.
 */
class RegisteredExternalLayout {

    private final GuiLayout mainGui;
    private final GuiLayout storageGui;
    private final GuiLayout sellConfirmGui;

    RegisteredExternalLayout(GuiLayout mainGui, GuiLayout storageGui, GuiLayout sellConfirmGui) {
        this.mainGui = mainGui;
        this.storageGui = storageGui;
        this.sellConfirmGui = sellConfirmGui;
    }

    GuiLayout getMainGui() {
        return mainGui;
    }

    GuiLayout getStorageGui() {
        return storageGui;
    }

    GuiLayout getSellConfirmGui() {
        return sellConfirmGui;
    }
}
