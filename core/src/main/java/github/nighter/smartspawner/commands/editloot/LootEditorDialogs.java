package github.nighter.smartspawner.commands.editloot;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The amount / chance / durability form behind every loot row, and all validation of it.
 *
 * <p>All Dialog API use lives in this one class, so a Paper breaking change stays confined here. The
 * fields are plain text inputs rather than sliders because an admin types exact ranges like
 * {@code 0-2}; on any bad value the same dialog is reopened with the offending field named, so nothing
 * is saved until every field parses.</p>
 *
 * <p>Two rules this file exists to enforce:</p>
 * <ul>
 *   <li>Every action goes through {@link #click}. {@code DialogAction.customClick} dereferences its
 *       options argument, so a null there throws inside Paper before the dialog is ever built.</li>
 *   <li>Callbacks arrive on a network thread. Nothing in them touches the server directly; they hop
 *       through {@link Scheduler#runEntityTask} first.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class LootEditorDialogs {

    private static final int CONTENT_WIDTH = 320;
    private static final int HALF_WIDTH = CONTENT_WIDTH / 2;
    private static final int FIELD_MAX_LENGTH = 16;
    private static final int MAX_AMOUNT = 64;
    private static final int MAX_DURABILITY = 100000;

    private final SmartSpawner plugin;
    private final LootEditorService service;
    private final LootEditorUI ui;

    public LootEditorDialogs(SmartSpawner plugin, LootEditorService service, LootEditorUI ui) {
        this.plugin = plugin;
        this.service = service;
        this.ui = ui;
    }

    // ============== Entry points ==============

    /**
     * The form for a captured item that is not in the file yet. Saving writes a new loot row; either
     * ending hands the physical item back, because only its data is kept.
     */
    public void openAddForm(Player player, LootEditorTarget target, String entryKey, ItemStack pending) {
        boolean durability = isDamageable(pending);
        show(player, () -> render(player, new Form(target, entryKey, pending, null, durability,
                "1", "100", "")));
    }

    /** The form for an existing loot row. Saving updates it in place; there is no item to hand back. */
    public void openEditForm(Player player, LootEditorTarget target, String entryKey, String lootKey) {
        LootEditorService.LootView loot = service.readLoot(target, entryKey, lootKey);
        if (loot == null) {
            show(player, () -> ui.openLootList(player, target, entryKey));
            return;
        }
        boolean durability = loot.minDurability() != null || isDamageable(loot.preview());
        String durabilityInit = loot.durabilityLabel() == null ? "" : loot.durabilityLabel();
        show(player, () -> render(player, new Form(target, entryKey, null, lootKey, durability,
                loot.amountLabel(), trim(loot.chance()), durabilityInit)));
    }

    // ============== Rendering ==============

    /** The current state of one open form, so a re-show after a validation error keeps what was typed. */
    private record Form(LootEditorTarget target, String entryKey, ItemStack pending, String lootKey,
                        boolean showDurability, String amount, String chance, String durability) {

        Form withValues(String amount, String chance, String durability) {
            return new Form(target, entryKey, pending, lootKey, showDurability, amount, chance, durability);
        }

        boolean isAdd() {
            return pending != null;
        }
    }

    private void render(Player player, Form form) {
        render(player, form, List.of());
    }

    private void render(Player player, Form form, List<Component> errors) {
        List<DialogInput> inputs = new ArrayList<>(3);
        inputs.add(DialogInput.text("amount", label("editloot.field_amount"))
                .width(CONTENT_WIDTH).maxLength(FIELD_MAX_LENGTH).initial(form.amount()).build());
        inputs.add(DialogInput.text("chance", label("editloot.field_chance"))
                .width(CONTENT_WIDTH).maxLength(FIELD_MAX_LENGTH).initial(form.chance()).build());
        if (form.showDurability()) {
            inputs.add(DialogInput.text("durability", label("editloot.field_durability"))
                    .width(CONTENT_WIDTH).maxLength(FIELD_MAX_LENGTH).initial(form.durability()).build());
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(label("editloot.dialog_body"), CONTENT_WIDTH));
        for (Component error : errors) {
            body.add(DialogBody.plainMessage(error, CONTENT_WIDTH));
        }

        ActionButton save = ActionButton.create(label("editloot.dialog_save"), null, HALF_WIDTH,
                click((view, audience) -> onSave(player, form, view)));
        ActionButton cancel = ActionButton.create(label("editloot.dialog_cancel"), null, HALF_WIDTH,
                click((view, audience) -> onCancel(player, form)));

        // Save and Cancel share the top row; Delete sits alone below, so it is only offered for an
        // existing row and is kept away from Save to avoid a misclick.
        List<ActionButton> buttons = new ArrayList<>(3);
        buttons.add(save);
        buttons.add(cancel);
        if (!form.isAdd()) {
            buttons.add(ActionButton.create(label("editloot.dialog_delete"), null, HALF_WIDTH,
                    click((view, audience) -> onDelete(player, form))));
        }

        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(label("editloot.dialog_title"))
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(buttons, null, 2))));
    }

    // ============== Save / cancel ==============

    private void onSave(Player player, Form form, DialogResponseView view) {
        String amountText = text(view, "amount");
        String chanceText = text(view, "chance");
        String durabilityText = form.showDurability() ? text(view, "durability") : "";

        List<Component> errors = new ArrayList<>();
        int[] amount = parseAmount(amountText, errors);
        Double chance = parseChance(chanceText, errors);
        int[] durability = form.showDurability() ? parseDurability(durabilityText, errors) : null;

        Form filled = form.withValues(amountText, chanceText, durabilityText);

        show(player, () -> {
            if (!errors.isEmpty()) {
                render(player, filled, errors);
                return;
            }

            Integer durabilityMin = durability == null ? null : durability[0];
            Integer durabilityMax = durability == null ? null : durability[1];

            if (form.isAdd()) {
                String label = service.addLoot(form.target(), form.entryKey(), form.pending(),
                        amount[0], amount[1], chance, durabilityMin, durabilityMax);
                returnItem(player, form.pending());
                plugin.getMessageService().sendMessage(player, "command.editloot.loot_added", Map.of("label", label));
            } else {
                if (service.readLoot(form.target(), form.entryKey(), form.lootKey()) == null) {
                    plugin.getMessageService().sendMessage(player, "command.editloot.entry_missing");
                    ui.openLootList(player, form.target(), form.entryKey());
                    return;
                }
                service.setLootAmount(form.target(), form.entryKey(), form.lootKey(), amount[0], amount[1]);
                service.setLootChance(form.target(), form.entryKey(), form.lootKey(), chance);
                service.setLootDurability(form.target(), form.entryKey(), form.lootKey(),
                        durabilityMin, durabilityMax);
                plugin.getMessageService().sendMessage(player, "command.editloot.loot_saved",
                        Map.of("label", form.lootKey()));
            }
            ui.openLootList(player, form.target(), form.entryKey());
        });
    }

    private void onCancel(Player player, Form form) {
        show(player, () -> {
            if (form.isAdd()) {
                returnItem(player, form.pending());
            }
            ui.openLootList(player, form.target(), form.entryKey());
        });
    }

    /** Removes an existing loot row from the entry, then returns to the loot list. */
    private void onDelete(Player player, Form form) {
        show(player, () -> {
            if (service.readLoot(form.target(), form.entryKey(), form.lootKey()) == null) {
                plugin.getMessageService().sendMessage(player, "command.editloot.entry_missing");
                ui.openLootList(player, form.target(), form.entryKey());
                return;
            }
            service.removeLoot(form.target(), form.entryKey(), form.lootKey());
            plugin.getMessageService().sendMessage(player, "command.editloot.loot_deleted",
                    Map.of("label", form.lootKey()));
            ui.openLootList(player, form.target(), form.entryKey());
        });
    }

    // ============== Validation ==============

    /** @return {min,max} on success; adds an error and returns null on failure. */
    private int[] parseAmount(String raw, List<Component> errors) {
        int[] range = readRange(raw);
        if (range == null || range[0] < 0 || range[1] < range[0] || range[1] > MAX_AMOUNT) {
            errors.add(label("editloot.error_amount", Map.of("max", String.valueOf(MAX_AMOUNT))));
            return null;
        }
        return range;
    }

    private Double parseChance(String raw, List<Component> errors) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (value < 0.0 || value > 100.0) {
                errors.add(label("editloot.error_chance"));
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(label("editloot.error_chance"));
            return null;
        }
    }

    /** Blank durability means "no range", which is valid and clears the key. */
    private int[] parseDurability(String raw, List<Component> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int[] range = readRange(raw);
        if (range == null || range[0] < 0 || range[1] < range[0] || range[1] > MAX_DURABILITY) {
            errors.add(label("editloot.error_durability"));
            return null;
        }
        return range;
    }

    /** Parses {@code "a"} or {@code "a-b"} into {min,max}, or null if either side is not an integer. */
    private static int[] readRange(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            int separator = value.indexOf('-', 1);
            if (separator < 0) {
                int single = Integer.parseInt(value);
                return new int[]{single, single};
            }
            return new int[]{
                    Integer.parseInt(value.substring(0, separator).trim()),
                    Integer.parseInt(value.substring(separator + 1).trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============== Helpers ==============

    private boolean isDamageable(ItemStack item) {
        return item != null && item.getType().getMaxDurability() > 0;
    }

    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static String text(DialogResponseView view, String key) {
        String value = view.getText(key);
        return value == null ? "" : value;
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Wraps a click body into a custom-click action.
     *
     * <p>{@code DialogAction.customClick} reads {@code options.lifetime()} while registering, so the
     * options argument must be a real instance. A null there fails inside Paper, not here.</p>
     */
    private static DialogAction click(BiConsumer<DialogResponseView, Audience> body) {
        return DialogAction.customClick(body::accept, ClickCallback.Options.builder().build());
    }

    /** Dialog callbacks arrive off the server thread, so anything they do is scheduled back onto it. */
    private void show(Player player, Runnable action) {
        Scheduler.runEntityTask(player, () -> {
            if (player.isOnline()) {
                action.run();
            }
        });
    }

    private Component label(String key) {
        return label(key, Map.of());
    }

    /**
     * Dialog text. It is display text, so a {@code <click>} in the language file cannot turn a dialog
     * line into a command.
     */
    private Component label(String key, Map<String, String> placeholders) {
        return plugin.getLanguageManager().commandGui().name(key + ".name", placeholders);
    }
}
