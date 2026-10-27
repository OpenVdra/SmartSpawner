package github.nighter.smartspawner.language.file;

import java.util.List;
import java.util.Map;

/**
 * The language file layout before 1.8.0 and where each of its keys went. Used by
 * {@code updates/LanguageRestructure} to move owners' values, and by the sections to answer an old key
 * an addon or fork still asks for. That second use is for one release only.
 *
 * <p>Each entry maps a key or a whole section by prefix: {@code spawner_storage_item} covers
 * {@code spawner_storage_item.lore} too. The longest matching prefix wins.</p>
 */
public final class LegacyLanguageKeys {

    /** One file of the old layout: where its keys go, and the prefix for keys the table does not list. */
    public record OldFile(String name, LanguageFileType target, String defaultPrefix, Map<String, String> moves) {}

    public static final OldFile MESSAGES = new OldFile("messages.yml", LanguageFileType.MESSAGES, "", Map.ofEntries(
            Map.entry("missing_message_key", "missing_key"),
            Map.entry("inventory_full", "general.inventory_full"),
            Map.entry("click_too_fast", "general.click_too_fast"),
            Map.entry("action_in_progress", "general.action_in_progress"),
            Map.entry("action_failed", "general.action_failed"),
            Map.entry("action_not_ready", "general.action_not_ready"),
            Map.entry("spawner_activated", "spawner.activated"),
            Map.entry("entity_changed", "spawner.entity_changed"),
            Map.entry("spawner_storage_empty", "storage.empty"),
            Map.entry("storage_in_use", "storage.in_use"),
            Map.entry("no_exp", "exp.none"),
            Map.entry("exp_collected", "exp.collected"),
            Map.entry("exp_collected_with_mending", "exp.collected_mending"),
            Map.entry("spawner_different", "stack.different_type"),
            Map.entry("spawner_stack_full", "stack.full"),
            Map.entry("spawner_stack_success", "stack.success"),
            Map.entry("break_no_permission", "break.no_permission"),
            Map.entry("break_silk_touch", "break.silk_touch_required"),
            Map.entry("break_tool_required", "break.wrong_tool"),
            Map.entry("drop_failed", "break.no_drop"),
            Map.entry("sneak_break_blocked", "break.sneak_blocked"),
            Map.entry("no_sellable_items", "sell.nothing_to_sell"),
            Map.entry("sell_success", "sell.success"),
            Map.entry("sell_and_exp_success", "sell.success_with_exp"),
            Map.entry("sell_and_exp_success_with_mending", "sell.success_with_exp_mending")));

    public static final OldFile COMMAND_MESSAGES = new OldFile("command_messages.yml", LanguageFileType.MESSAGES,
            "command.", Map.ofEntries(
            Map.entry("player_only", "command.player_only"),
            Map.entry("no_permission", "command.no_permission"),
            Map.entry("list", "command.list"),
            Map.entry("set", "command.set"),
            Map.entry("near", "command.near"),
            Map.entry("give", "command.give"),
            Map.entry("hologram", "command.hologram"),
            Map.entry("clear", "command.clear"),
            Map.entry("prices", "command.prices"),
            Map.entry("reload", "command.reload"),
            Map.entry("language", "command.language"),
            Map.entry("gui_layout", "command.gui_layout"),
            Map.entry("editloot", "command.editloot")));

    /** Also the layout of each {@code gui_layouts/<layout>/texts.yml}. */
    public static final OldFile GUI = new OldFile("gui.yml", LanguageFileType.GUI, "", Map.ofEntries(
            Map.entry("gui_title_main.single_spawner", "main.title"),
            Map.entry("gui_title_main.stacked_spawner", "main.title_stacked"),
            Map.entry("spawner_storage_item", "main.storage"),
            Map.entry("spawner_info_item", "main.info"),
            Map.entry("exp_info_item", "main.exp"),
            Map.entry("gui_title_storage", "storage.title"),
            Map.entry("gui_title_storage_single_spawner", "storage.title_single"),
            Map.entry("navigation_button_previous", "storage.previous_page"),
            Map.entry("navigation_button_next", "storage.next_page"),
            Map.entry("sell_button", "storage.sell"),
            Map.entry("sell_and_exp_button", "storage.sell_and_exp"),
            Map.entry("return_button", "storage.back"),
            Map.entry("take_all_button", "storage.take_all"),
            Map.entry("item_filter_button", "storage.filter"),
            Map.entry("sort_items_button", "storage.sort"),
            Map.entry("drop_page_button", "storage.drop_page"),
            Map.entry("collect_exp_button", "storage.collect_exp"),
            Map.entry("storage_spawner_info_button", "storage.info"),
            Map.entry("gui_title_sell_confirm", "sell_confirm.title"),
            Map.entry("button_sell_cancel", "sell_confirm.cancel"),
            Map.entry("button_sell_confirm", "sell_confirm.confirm"),
            Map.entry("button_sell_confirm_with_exp", "sell_confirm.confirm_with_exp"),
            Map.entry("button_sell_info", "sell_confirm.summary"),
            Map.entry("gui_title_filter_config", "filter.title"),
            Map.entry("filter_divider", "filter.divider"),
            Map.entry("filter_item_allowed", "filter.allowed"),
            Map.entry("filter_item_blocked", "filter.blocked")));

    public static final OldFile COMMAND_GUI = new OldFile("command_gui.yml", LanguageFileType.GUI, "command.", Map.ofEntries(
            Map.entry("general_navigation", "command.navigation"),
            Map.entry("general.selected_option", "command.list.spawners.selected_option"),
            Map.entry("general.unselected_option", "command.list.spawners.unselected_option"),
            Map.entry("gui_title_server_selection", "command.list.servers.title"),
            Map.entry("server_selection.current_server", "command.list.servers.current"),
            Map.entry("server_selection.remote_server", "command.list.servers.remote"),
            Map.entry("gui_title_world_selection", "command.list.worlds.title"),
            Map.entry("gui_title_world_selection_server", "command.list.worlds.title_server"),
            Map.entry("world_display_names", "command.list.worlds.names"),
            Map.entry("world_buttons", "command.list.worlds.buttons"),
            Map.entry("remote_world_lore", "command.list.worlds.remote_lore"),
            Map.entry("gui_title_spawner_list", "command.list.spawners.title"),
            Map.entry("gui_title_spawner_list_remote", "command.list.spawners.title_remote"),
            Map.entry("filter.button", "command.list.spawners.filter"),
            Map.entry("filter.option_name", "command.list.spawners.filter_options"),
            Map.entry("sort.button", "command.list.spawners.sort"),
            Map.entry("sort.option_name", "command.list.spawners.sort_options"),
            Map.entry("spawner_item_list", "command.list.spawners.spawner"),
            Map.entry("gui_title_spawner_management", "command.list.manage.title"),
            Map.entry("spawner_management", "command.list.manage"),
            Map.entry("gui_title_prices", "command.prices.title"),
            Map.entry("price_item", "command.prices.item"),
            Map.entry("editloot", "command.editloot")));

    public static final OldFile HOLOGRAM = new OldFile("hologram.yml", LanguageFileType.ITEMS, "hologram.",
            Map.of("hologram_text", "hologram.lines"));

    /** Old files merged into another, in the order their values are applied. */
    public static final List<OldFile> MERGED = List.of(COMMAND_MESSAGES, COMMAND_GUI, HOLOGRAM);

    /** Every old file that has a rename table, including those that kept their name. */
    public static final List<OldFile> ALL = List.of(MESSAGES, COMMAND_MESSAGES, GUI, COMMAND_GUI, HOLOGRAM);

    public static final String OLD_IRIDIUM_FILE = "iridium_skyblock.yml";

    private LegacyLanguageKeys() {}

    /** Where {@code path} from {@code file} goes, or {@code null} when the table does not list it. */
    public static String move(OldFile file, String path) {
        String best = null;
        for (String old : file.moves().keySet()) {
            if ((path.equals(old) || path.startsWith(old + ".")) && (best == null || old.length() > best.length())) {
                best = old;
            }
        }
        return best == null ? null : file.moves().get(best) + path.substring(best.length());
    }

    /** As {@link #move}, falling back to the file's default prefix for a key the table does not list. */
    public static String moveOrKeep(OldFile file, String path) {
        String moved = move(file, path);
        return moved != null ? moved : file.defaultPrefix() + path;
    }

    /** The new message key for an old one from {@code messages.yml} or {@code command_messages.yml}. */
    public static String messageKey(String oldKey) {
        String moved = move(MESSAGES, oldKey);
        return moved != null ? moved : move(COMMAND_MESSAGES, oldKey);
    }

    /** The new {@code gui.yml} key for an old one from {@code gui.yml}. */
    public static String guiKey(String oldKey) {
        return move(GUI, oldKey);
    }

    /** The new {@code gui.yml} key, without its {@code command.} prefix, for an old one from {@code command_gui.yml}. */
    public static String commandGuiKey(String oldKey) {
        String moved = move(COMMAND_GUI, oldKey);
        return moved == null ? null : moved.substring("command.".length());
    }
}
