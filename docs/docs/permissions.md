# Permissions

SmartSpawner uses a layered permission system. Click any permission node to copy it to your clipboard.

## Default Values

| Value | Meaning |
|-------|---------|
| `op` | Only server operators have this permission by default |
| `true` | All players have this permission by default |
| `false` | No players have this permission by default; must be explicitly granted |

## Command Permissions

::: info
To use any command, players need **both** `smartspawner.command.use` (base access) **and** the specific command permission listed below.
:::

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="smartspawner.command.use" defaultVal="op">
Base permission required for all SmartSpawner commands. Without this, no commands are accessible.
</PermRow>

<PermRow permission="smartspawner.command.reload" defaultVal="op">
Allows reloading SmartSpawner configuration files with <code>/ss reload</code>.
</PermRow>

<PermRow permission="smartspawner.command.give" defaultVal="op">
Allows giving spawners to players with <code>/ss give</code>.
</PermRow>

<PermRow permission="smartspawner.command.list" defaultVal="op">
Allows viewing the admin spawner list with <code>/ss list</code>.
</PermRow>

<PermRow permission="smartspawner.command.hologram" defaultVal="op">
Allows toggling holograms globally with <code>/ss hologram</code>.
</PermRow>

<PermRow permission="smartspawner.command.prices" defaultVal="op">
Allows viewing the item prices GUI with <code>/ss prices</code>.
</PermRow>

<PermRow permission="smartspawner.command.clear" defaultVal="op">
Allows clearing holograms and ghost spawners with <code>/ss clear</code>.
</PermRow>

<PermRow permission="smartspawner.command.near" defaultVal="op">
Allows scanning and highlighting nearby spawners with <code>/ss near</code>.
</PermRow>

<PermRow permission="smartspawner.command.set" defaultVal="op">
Allows setting spawner properties (stack size, range, delay) with <code>/ss set</code>.
</PermRow>

<PermRow permission="smartspawner.command.language" defaultVal="op">
Allows changing the active language with <code>/ss language</code>.
</PermRow>

<PermRow permission="smartspawner.command.gui_layout" defaultVal="op">
Allows changing the active GUI layout with <code>/ss gui_layout</code>.
</PermRow>

</BaseTable>

## Feature Permissions

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="smartspawner.changetype" defaultVal="op">
Allows players to change a spawner's entity type by right-clicking it with a spawn egg.
</PermRow>

<PermRow permission="smartspawner.stack" defaultVal="true">
Allows players to stack spawners by hand (right-click) or through the stacker GUI.
</PermRow>

<PermRow permission="smartspawner.break" defaultVal="true">
Allows players to break and collect Smart Spawners (subject to tool and Silk Touch requirements in config).
</PermRow>

<PermRow permission="smartspawner.break.bypassdropchance" defaultVal="op">
Players with this permission always receive the spawner item when breaking, regardless of the <code>drop_chance</code> setting. Also allows sneak stack breaking and stacker GUI access for spawners that have <code>drop_chance</code> configured.
</PermRow>

<PermRow permission="smartspawner.sellall" defaultVal="true">
Allows players to sell items from the spawner storage GUI.
</PermRow>

</BaseTable>
