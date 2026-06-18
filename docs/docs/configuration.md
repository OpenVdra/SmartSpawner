# Main Configuration

The `config.yml` file is located in `plugins/SmartSpawner/`. It controls language, spawner behavior, economy, visual effects, logging, database storage, and performance.

Click any option or category to view additional information.

## Time Format

SmartSpawner accepts short, human-readable duration values everywhere a time is expected:

| Format | Meaning |
|--------|---------|
| `20s` | 20 seconds |
| `5m` | 5 minutes |
| `1h` | 1 hour |
| `1d` | 1 day |
| `1d_2h_30m_15s` | 1 day, 2 hours, 30 minutes, 15 seconds |

Supported units: `s` `m` `h` `d` `w` `mo` `y`

---

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="language" value="en_US" type="string">

Language folder to load from <code>plugins/SmartSpawner/language/</code>.<br><br>

<strong>Built-in languages:</strong>

| Locale | Language |
|--------|----------|
| <code>en_US</code> | English |
| <code>en_US_DonutSMP</code> | English – DonutSMP layout |
| <code>en_US_DonutSMP_v2</code> | English – DonutSMP v2 layout |
| <code>tr_TR</code> | Turkish |
| <code>vi_VN</code> | Vietnamese |

To add a custom language, create a new folder in <code>language/</code>, copy the files from <code>en_US/</code> as a template, and translate them.

</ConfigProperty>

<ConfigProperty name="gui_layout" value="default" type="string">
GUI layout folder to load from <code>plugins/SmartSpawner/gui_layouts/</code>. Built-in options: <code>default</code>, <code>DonutSMP</code>, <code>DonutSMP_v2</code>.
</ConfigProperty>

<ConfigProperty name="debug" value="false" type="boolean">
Enables extra console output for troubleshooting. Keep this <code>false</code> in production.
</ConfigProperty>

<ConfigGroup name="spawner_properties">
<template #info>
Controls the default behavior of all Smart Spawners. These values apply to every spawner unless overridden by per-entity settings.
</template>

<ConfigGroup name="default">

<ConfigProperty name="min_mobs" value="1" type="number">
Minimum number of virtual mobs per generation cycle. The actual number is a random value between <code>min_mobs</code> and <code>max_mobs</code>.
</ConfigProperty>

<ConfigProperty name="max_mobs" value="4" type="number">
Maximum number of virtual mobs per generation cycle.
</ConfigProperty>

<ConfigProperty name="range" value="16" type="number">
Player activation distance in blocks. The spawner only generates drops when a player is within this range.
</ConfigProperty>

<ConfigProperty name="delay" value="25s" type="string">
Time between generation cycles. Accepts the time format described above.
</ConfigProperty>

<ConfigProperty name="max_storage_pages" value="1" type="number">
Internal storage size. Each page provides 45 item slots. Increase this for larger farms.
</ConfigProperty>

<ConfigProperty name="max_stored_exp" value="1000" type="number">
Maximum XP the spawner can store before it stops generating more.
</ConfigProperty>

<ConfigProperty name="max_stack_size" value="10000" type="number">
Maximum number of spawners that can be stacked into one block.
</ConfigProperty>

<ConfigProperty name="allow_exp_mending" value="true" type="boolean">
When <code>true</code>, stored XP can repair items with the Mending enchantment when players claim XP from the spawner.
</ConfigProperty>

<ConfigProperty name="protect_from_explosions" value="true" type="boolean">
Prevents Smart Spawner blocks from being destroyed by explosions.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="spawner_break">
<template #info>
Controls how players break and collect Smart Spawners from the world.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Master switch for breaking and collecting Smart Spawners. If <code>false</code>, spawners cannot be broken at all.
</ConfigProperty>

<ConfigProperty name="direct_to_inventory" value="false" type="boolean">
If <code>true</code>, collected spawners go directly into the player's inventory instead of dropping on the ground.
</ConfigProperty>

<ConfigProperty name="required_tools" :value="['IRON_PICKAXE', 'GOLDEN_PICKAXE', 'DIAMOND_PICKAXE', 'NETHERITE_PICKAXE']" type="list">
Tools that are allowed to break Smart Spawners. Only tools in this list can collect spawners.
</ConfigProperty>

<ConfigProperty name="durability_loss" value="1" type="number">
Durability points removed from the tool when a spawner is broken.
</ConfigProperty>

<ConfigProperty name="sneak_break" value="true" type="boolean">
When <code>true</code>, sneaking while breaking a stacked spawner removes up to 64 spawners at once. When <code>false</code>, sneaking has no special effect.<br><br>
⚠️ If a spawner entity has <code>drop_chance</code> configured in <code>spawners_settings.yml</code>, sneak breaking is blocked for that spawner (one at a time only), unless the player has <code>smartspawner.break.bypassdropchance</code>.
</ConfigProperty>

<ConfigProperty name="sell_and_xp_break" value="true" type="boolean">
When a Smart Spawner is fully removed, automatically sells stored items and claims remaining XP. Requires a sell integration and the <code>smartspawner.sellall</code> permission.
</ConfigProperty>

<ConfigGroup name="silk_touch">

<ConfigProperty name="required" value="true" type="boolean">
Whether the Silk Touch enchantment is required to collect the spawner item when breaking.
</ConfigProperty>

<ConfigProperty name="level" value="1" type="number">
Minimum Silk Touch level required.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="natural_spawner">
<template #info>
Settings for naturally generated vanilla spawners found in dungeons, mineshafts, and other structures.
</template>

<ConfigProperty name="breakable" value="false" type="boolean">
Allows naturally generated vanilla spawners to be broken and collected.
</ConfigProperty>

<ConfigProperty name="convert_to_smart_spawner" value="false" type="boolean">
If <code>true</code>, breaking a natural spawner converts it into a Smart Spawner. If <code>false</code>, it drops a vanilla spawner item.
</ConfigProperty>

<ConfigProperty name="spawn_mobs" value="true" type="boolean">
Allows natural spawners to spawn mobs normally.
</ConfigProperty>

<ConfigProperty name="protect_from_explosions" value="false" type="boolean">
Protects natural spawner blocks from explosions.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="sell_integration">
<template #info>
Configures the economy and shop integration used by the spawner storage sell button.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Enables selling items from spawner storage. Set to <code>false</code> to disable all sell functionality.
</ConfigProperty>

<ConfigProperty name="currency" value="VAULT" type="string">
Economy backend. Supported values: <code>VAULT</code>, <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="excellenteconomy_currency" value="coins" type="string">
ExcellentEconomy currency name. Only used when <code>currency</code> is <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="price_source_mode" value="SHOP_PRIORITY" type="string">
Determines where sell prices come from.<br><br>

| Mode | Behavior |
|------|----------|
| <code>SHOP_ONLY</code> | Uses only shop plugin prices. Custom prices ignored. |
| <code>SHOP_PRIORITY</code> | Shop prices first, then custom prices as fallback. Recommended. |
| <code>CUSTOM_ONLY</code> | Uses only prices from <code>item_prices.yml</code>. |
| <code>CUSTOM_PRIORITY</code> | Custom prices first, then shop prices as fallback. |

</ConfigProperty>

<ConfigGroup name="shop_integration">

<ConfigProperty name="enabled" value="true" type="boolean">
Enables shop plugin price lookup.
</ConfigProperty>

<ConfigProperty name="preferred_plugin" value="auto" type="string">
Specify a shop plugin to use: <code>auto</code>, <code>EconomyShopGUI</code>, <code>EconomyShopGUI-Premium</code>, <code>ShopGUIPlus</code>, or <code>zShop</code>.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="custom_prices">

<ConfigProperty name="enabled" value="true" type="boolean">
Enables custom prices from <code>item_prices.yml</code>.
</ConfigProperty>

<ConfigProperty name="default_price" value="1.0" type="number">
Fallback price for items without an explicit custom price. Set to <code>0.0</code> to prevent selling unconfigured items.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="hopper">
<template #info>
Controls automatic item transfer from spawner storage through hoppers placed below the spawner.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Enables hopper item collection from spawner storage.
</ConfigProperty>

<ConfigProperty name="check_delay" value="3s" type="string">
Time between hopper transfer checks.
</ConfigProperty>

<ConfigProperty name="stack_per_transfer" value="5" type="number">
Number of item stacks transferred per cycle (up to 5).
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="bedrock_support">

<ConfigProperty name="enable_formui" value="true" type="boolean">
Shows mobile-friendly form menus to Bedrock players (via Floodgate/Geyser) instead of chest GUIs.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="hologram">
<template #info>
Controls floating text displays above spawner blocks.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Shows floating text above spawners indicating their type and stack size.
</ConfigProperty>

<ConfigProperty name="offset_x" value="0.5" type="number">Horizontal offset from the spawner block center.</ConfigProperty>
<ConfigProperty name="offset_y" value="1.6" type="number">Vertical offset above the spawner block.</ConfigProperty>
<ConfigProperty name="offset_z" value="0.5" type="number">Depth offset from the spawner block center.</ConfigProperty>

<ConfigProperty name="alignment" value="CENTER" type="string">
Text alignment: <code>CENTER</code>, <code>LEFT</code>, or <code>RIGHT</code>.
</ConfigProperty>

<ConfigProperty name="shadowed_text" value="true" type="boolean">
Adds a drop shadow to hologram text.
</ConfigProperty>

<ConfigProperty name="see_through" value="false" type="boolean">
Makes holograms visible through blocks.
</ConfigProperty>

<ConfigProperty name="transparent_background" value="false" type="boolean">
Removes the hologram background panel.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="particle">
<template #info>
Optional particle effects for spawner events.
</template>

<ConfigProperty name="spawner_stack" value="true" type="boolean">Shows particles when spawners are stacked.</ConfigProperty>
<ConfigProperty name="spawner_activate" value="true" type="boolean">Shows particles when a spawner generates drops.</ConfigProperty>
<ConfigProperty name="spawner_generate_loot" value="true" type="boolean">Shows particles when loot is added to storage.</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="logging">
<template #info>
Logs spawner actions to rotating log files for auditing and debugging.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">Enables file logging for spawner actions.</ConfigProperty>
<ConfigProperty name="json_format" value="false" type="boolean">If <code>true</code>, logs are written as JSON. Otherwise human-readable text.</ConfigProperty>
<ConfigProperty name="console_output" value="false" type="boolean">Also print log entries to the server console.</ConfigProperty>
<ConfigProperty name="max_log_files" value="10" type="number">Number of rotated log files to keep.</ConfigProperty>
<ConfigProperty name="max_log_size_mb" value="10" type="number">Maximum size of each log file before rotation.</ConfigProperty>
<ConfigProperty name="log_all_events" value="false" type="boolean">If <code>true</code>, logs every supported event and ignores the <code>logged_events</code> list.</ConfigProperty>

<ConfigProperty name="logged_events" :value="['SPAWNER_PLACE', 'SPAWNER_BREAK', 'SPAWNER_STACK_HAND', 'SPAWNER_SELL_ALL', 'COMMAND_EXECUTE_PLAYER']" type="list">
Events to log when <code>log_all_events</code> is <code>false</code>. See the table below for all available events.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Configures where spawner data is stored.
</template>

<ConfigProperty name="mode" value="YAML" type="string">
Storage backend. Supported values: <code>YAML</code>, <code>SQLITE</code>, <code>MYSQL</code>.
</ConfigProperty>

<ConfigProperty name="server_name" value="server1" type="string">
Unique server name used for cross-server MySQL setups.
</ConfigProperty>

<ConfigProperty name="sync_across_servers" value="false" type="boolean">
Shows a server selection page in <code>/ss list</code> to view spawners from all servers sharing a MySQL database. Only available in <code>MYSQL</code> mode.
</ConfigProperty>

<ConfigProperty name="migrate_from_local" value="true" type="boolean">
Automatically migrates local data on startup when switching database modes. Migrated files are renamed with a <code>.migrated</code> suffix.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="performance">
<template #info>
Controls how SmartSpawner calculates drops for very large stacked spawners.
</template>

<ConfigProperty name="approximate_loot" value="true" type="boolean">
When <code>true</code>, SmartSpawner uses a fast average-based calculation for extremely large batches instead of rolling each mob individually. Recommended for most servers. Produces realistic totals with minimal CPU usage.
</ConfigProperty>

<ConfigProperty name="approximation_threshold" value="1000" type="number">
Controls when approximation starts (when <code>approximate_loot</code> is <code>true</code>). Lower values use the fast path sooner; higher values stay on exact rolling longer.

| Value | Behavior |
|-------|----------|
| 10–100 | Very aggressive, best for enormous stacks |
| 100–1000 | Balanced performance and accuracy |
| 1000–10000 | Conservative, closer to per-mob exact rolling |
</ConfigProperty>

</ConfigGroup>

</div>

## Available Log Events

| Event | Description |
|-------|-------------|
| `SPAWNER_PLACE` | Spawner placed by a player |
| `SPAWNER_BREAK` | Spawner broken by a player |
| `SPAWNER_EXPLODE` | Spawner destroyed by an explosion |
| `SPAWNER_STACK_HAND` | Spawner stacked by hand |
| `SPAWNER_STACK_GUI` | Spawner stacked via GUI |
| `SPAWNER_DESTACK_GUI` | Spawner destacked via GUI |
| `SPAWNER_GUI_OPEN` | Main spawner GUI opened |
| `SPAWNER_STORAGE_OPEN` | Storage GUI opened |
| `SPAWNER_STACKER_OPEN` | Stacker GUI opened |
| `SPAWNER_EXP_CLAIM` | XP claimed from spawner |
| `SPAWNER_SELL_ALL` | Items sold from storage |
| `SPAWNER_ITEM_TAKE_ALL` | All items taken from storage |
| `SPAWNER_ITEM_DROP` | Item dropped with the drop key |
| `SPAWNER_ITEMS_SORT` | Items sorted in storage |
| `SPAWNER_ITEM_FILTER` | Item filter toggled |
| `SPAWNER_DROP_PAGE_ITEMS` | All items on current page dropped |
| `SPAWNER_EGG_CHANGE` | Entity type changed with spawn egg |
| `COMMAND_EXECUTE_PLAYER` | Command run by a player |
| `COMMAND_EXECUTE_CONSOLE` | Command run by console |
| `COMMAND_EXECUTE_RCON` | Command run via RCON |
