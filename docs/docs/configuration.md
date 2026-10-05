# Main Configuration

The `config.yml` file is located in `plugins/SmartSpawner/`. It controls language, spawner behavior, visual effects, database storage, and performance.

Two areas have their own files. Selling and item prices are in `sell_integration.yml`, and action logging is in `activity_log.yml`.

Most settings apply on `/ss reload`. The ones marked RESTART are only read when the server starts.

Mob and Item Spawner drop tables can also be edited in game with `/ss editloot <name>`. See
[Commands](/docs/commands#ss-editloot) for how it works.

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

<ConfigGroup name="spawner_properties">
<template #info>
These settings apply to every Smart Spawner on the server. They control how often spawners generate, how far they reach, how much they store, and how they stack and place. Every spawner shares these values; there is no per-mob override here. Per-mob values such as XP and drop tables are set in <code>spawner_mobs.yml</code> instead. See [Mob Spawners](/docs/spawner-mobs).
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

<ConfigProperty name="sneak_stack" value="true" type="boolean">
When <code>true</code>, sneaking while right-clicking a spawner adds every held spawner to the stack at once. When <code>false</code>, each click adds one.
</ConfigProperty>

<ConfigProperty name="sneak_place" value="true" type="boolean">
When <code>true</code>, sneaking while placing a spawner places the whole held stack as one spawner. When <code>false</code>, each placement adds one.
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
::: warning Drop chance and sneak breaking
If a mob type has `drop_chance` configured in `spawner_mobs.yml`, sneak breaking is blocked for that spawner (one at a time only), unless the player has `smartspawner.break.bypassdropchance`.
:::
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

<ConfigProperty name="drop_chance" value="(commented out)" type="string">
Optional chance, as a percentage from <code>0.0</code> to <code>100.0</code>, that breaking a natural spawner actually drops the spawner item. Use the <code>default</code> key to set the chance for every mob type at once, then add specific mob types (for example <code>ZOMBIE: 75.0</code>) to override it for just that type. If this section is left commented out, or a mob type isn't listed and no <code>default</code> is set, the drop chance is 100.0. This also applies to spawners with no mob type assigned yet (uses the <code>default</code> chance).

```yaml
drop_chance:
  default: 80.0   # applies to every spawner type
  ZOMBIE: 75.0    # override for a specific type
  SKELETON: 50.0
  BLAZE: 25.0
```
</ConfigProperty>

<ConfigProperty name="spawn_mobs" value="true" type="boolean">
Allows natural spawners to spawn mobs normally.
</ConfigProperty>

<ConfigProperty name="protect_from_explosions" value="false" type="boolean">
Protects natural spawner blocks from explosions.
</ConfigProperty>

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

<ConfigGroup name="database">
<template #info>
Configures where spawner data is stored. See <a href="/docs/database-support">Database Support</a> for a full walkthrough.

::: warning RESTART
Every setting in this section except <code>autosave-interval</code> is only read when the server starts. <code>/ss reload</code> does not apply them.
:::
</template>

<ConfigProperty name="type" value="SQLITE" type="string">
Storage backend. Supported values: <code>SQLITE</code>, <code>MYSQL</code>. A config still set to <code>YAML</code> switches to <code>SQLITE</code> on the next start and imports the old file once.
</ConfigProperty>

<ConfigProperty name="table-prefix" value="sspawner_" type="string">
Prefix for the two tables this plugin creates, <code>sspawner_data</code> and <code>sspawner_schema_meta</code>. Only letters, digits and underscore are kept, anything else is removed. Change it when another plugin already uses those names in the same database, or to keep two SmartSpawner installs apart in one MySQL database.

Existing tables are renamed automatically when this value changes.
</ConfigProperty>

<ConfigProperty name="autosave-interval" value="3m" type="string">
How often unsaved spawner changes are written to the database. Accepts the time format described above, with a minimum of <code>30s</code>. Spawner data is also saved when the world saves and when the server stops, so this is a safety net rather than the only save.

Raise it on a busy server to cut disk writes. Lower it to shorten how much recent activity a server crash could lose. This is the only setting in this section that <code>/ss reload</code> applies.
</ConfigProperty>

<ConfigProperty name="sqlite-file" value="spawners.db" type="string">
Database file name, stored in <code>plugins/SmartSpawner/</code>. Used in <code>SQLITE</code> mode only.
</ConfigProperty>

<ConfigProperty name="host" value="localhost" type="string">
Database server address. Used in <code>MYSQL</code> mode only, like the five options below it.
</ConfigProperty>

<ConfigProperty name="port" value="3306" type="number">
Database server port.
</ConfigProperty>

<ConfigProperty name="database" value="smartspawner" type="string">
Name of the MySQL or MariaDB database to use.
</ConfigProperty>

<ConfigProperty name="username" value="root" type="string">
Database user.
</ConfigProperty>

<ConfigProperty name="password" value="" type="string">
Password for that user.
</ConfigProperty>

<ConfigProperty name="pool-size" value="10" type="number">
Largest number of database connections the plugin may open at once. The defaults suit most servers. SQLite runs in WAL mode, so reads are not blocked while a save runs.
</ConfigProperty>

<ConfigProperty name="server-name" value="server1" type="string">
Unique server name used for cross-server MySQL setups.
</ConfigProperty>

<ConfigProperty name="sync-across-servers" value="false" type="boolean">
Shows a server selection page in <code>/ss list</code> to view spawners from all servers sharing a MySQL database. Only available in <code>MYSQL</code> mode.
</ConfigProperty>

<ConfigProperty name="migrate-from-local" value="true" type="boolean">
Automatically migrates local data on startup when switching database modes. Migrated files are renamed with a <code>.migrated</code> suffix so nothing is imported twice.
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
